/**
 * "Humor" de cada horário: o que você costuma ouvir naquela hora do dia.
 *
 * Cada escuta conta numa janela de ~4 horas em volta da hora em que aconteceu (a hora exata
 * pesa mais), então quem ouve jazz das 5h às 8h e trap a partir das 9h tem um perfil de jazz
 * às 6h e de trap às 10h. Escutas recentes valem mais, e músicas puladas naquele horário
 * contam contra o gênero e o artista.
 *
 * Com pouco histórico no horário, entra uma "energia esperada" por hora (calmo cedo, mais
 * pesado à tarde e à noite) junto do seu gosto geral, até o histórico do horário ganhar peso.
 *
 * O Android usa a mesma conta (android/.../insights/MoodProfile.kt).
 */
import { normalizeText } from '@shared/text';
import type { Song } from '@shared/types';

import type { ListenEntry } from './listening-db';
import { DAY_MS, type Daypart } from './listening-stats';

/** Peso de uma escuta pela distância (horas no relógio) até o horário alvo. */
const HOUR_WEIGHTS = [1, 0.6, 0.25];
const HALF_LIFE_DAYS = 45;
/** Uma música pulada conta como 0,6 minuto contra. */
const SKIP_WEIGHT = -0.6;
/** Minutos (já com os pesos) para confiar metade no histórico do horário. */
const CONFIDENCE_MINUTES = 45;
/** Na daylist, escutas no mesmo dia da semana valem um pouco mais. */
const SAME_WEEKDAY_BONUS = 1.3;

/** Energia esperada (0 calmo a 1 pesado) em cada hora, para quando falta histórico. */
const EXPECTED_ENERGY = [
    0.35, 0.3, 0.25, 0.2, 0.2, 0.25, 0.3, 0.35, 0.45, 0.5, 0.55, 0.6,
    0.6, 0.6, 0.6, 0.65, 0.65, 0.7, 0.7, 0.7, 0.65, 0.6, 0.5, 0.4,
];

/** Energia típica por palavra do gênero (comparada sem acentos, em minúsculas). */
const GENRE_ENERGY: [string, number][] = [
    ['sleep', 0.05], ['meditat', 0.05], ['ambient', 0.1], ['new age', 0.1], ['classic', 0.15],
    ['classica', 0.15], ['erudit', 0.15], ['piano', 0.15], ['lo-fi', 0.2], ['lofi', 0.2],
    ['lounge', 0.2], ['chill', 0.2], ['acoustic', 0.25], ['acustic', 0.25], ['instrumental', 0.25],
    ['bossa', 0.25], ['jazz', 0.3], ['folk', 0.3], ['soundtrack', 0.3], ['trilha', 0.3],
    ['mpb', 0.35], ['blues', 0.35], ['soul', 0.4], ['gospel', 0.4], ['worship', 0.4],
    ['indie', 0.45], ['r&b', 0.45], ['rnb', 0.45], ['reggae', 0.45], ['country', 0.45],
    ['pop', 0.55], ['samba', 0.55], ['pagode', 0.55], ['sertanejo', 0.55], ['forro', 0.6],
    ['alternative', 0.6], ['alternativ', 0.6], ['rock', 0.65], ['hip hop', 0.65], ['hip-hop', 0.65],
    ['rap', 0.65], ['reggaeton', 0.7], ['latin', 0.65], ['axe', 0.7], ['disco', 0.7], ['emo', 0.7],
    ['electronic', 0.7], ['eletronic', 0.7], ['house', 0.75], ['dance', 0.75], ['funk', 0.75],
    ['trap', 0.75], ['grunge', 0.75], ['drill', 0.8], ['trance', 0.8], ['edm', 0.85], ['techno', 0.85],
    ['punk', 0.85], ['drum and bass', 0.9], ['dnb', 0.9], ['dubstep', 0.9], ['metal', 0.95],
    ['heavy', 0.95], ['hardcore', 0.95], ['thrash', 1], ['death', 1], ['grindcore', 1],
];

const average = (values: number[]) => values.reduce((a, b) => a + b, 0) / values.length;

export function hourWeight(hour: number, target: number) {
    const d = Math.abs(hour - target);
    return HOUR_WEIGHTS[Math.min(d, 24 - d)] ?? 0;
}

/** Horas do bloco de 4 horas da daylist (0–3, 4–7, 8–11, 12–15, 16–19, 20–23). */
export const slotHours = (hour: number) => {
    const start = Math.floor(hour / 4) * 4;
    return [start, start + 1, start + 2, start + 3];
};

const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => from + i);

export const daypartHours = (daypart: Daypart) =>
    ({ afternoon: range(12, 17), dawn: range(0, 4), morning: range(5, 11), night: range(18, 23) })[daypart];

export const expectedEnergy = (hours: number[]) => average(hours.map((h) => EXPECTED_ENERGY[((h % 24) + 24) % 24]));

/** Energia de uma música pelos gêneros (média das palavras reconhecidas), ou null. */
export function genreEnergy(genres: string[]): null | number {
    const found: number[] = [];
    for (const g of genres) {
        const key = normalizeText(g);
        const tokens = key.split(/[^a-z0-9]+/).filter(Boolean);
        // Palavra simples: começo de uma palavra do gênero ("classic" acha "classical", mas "rap"
        // não acha "trap"). Com espaço ou símbolo ("hip hop", "r&b"): o texto inteiro.
        for (const [word, energy] of GENRE_ENERGY) {
            const simple = /^[a-z0-9]+$/.test(word);
            if (simple ? tokens.some((t) => t.startsWith(word)) : key.includes(word)) found.push(energy);
        }
    }
    return found.length ? average(found) : null;
}

/** Quanto a energia da música combina com a esperada para o horário (0 a 1; 0,6 se não sabe). */
export function energyFit(genres: string[], hours: number[]) {
    const energy = genreEnergy(genres);
    if (energy === null) return 0.6;
    return 1 - Math.min(1, Math.abs(energy - expectedEnergy(hours)) * 1.6);
}

/** Gêneros e artistas de um horário, com pontos (negativos quando você costuma pular). */
export class MoodProfile {
    private readonly maxGenre: number;
    private readonly maxArtist: number;

    constructor(
        private readonly genres: Map<string, { name: string; score: number }>,
        private readonly artists: Map<string, number>,
        /** Minutos ouvidos (com os pesos) que formam o perfil. */
        readonly minutes: number,
    ) {
        this.maxGenre = Math.max(0, ...[...genres.values()].map((g) => g.score)) || 1;
        this.maxArtist = Math.max(0, ...artists.values()) || 1;
    }

    get isEmpty() {
        return this.minutes <= 0;
    }

    /** Quanto dá para confiar neste perfil (0 sem histórico, perto de 1 com muito). */
    get confidence() {
        return this.minutes / (this.minutes + CONFIDENCE_MINUTES);
    }

    topGenres(limit: number) {
        return [...this.genres.values()]
            .filter((g) => g.score > 0)
            .sort((a, b) => b.score - a.score)
            .slice(0, limit)
            .map((g) => g.name);
    }

    /** -1 (você pula) a 1 (o que mais toca). O artista também conta. */
    affinity(genreKeys: string[], artistKey: string) {
        const clamp = (v: number) => Math.max(-1, Math.min(1, v));
        const genreScores = genreKeys.map((k) => this.genres.get(k)?.score).filter((s): s is number => s !== undefined);
        const g = genreScores.length ? clamp(Math.max(...genreScores) / this.maxGenre) : null;
        const artist = this.artists.get(artistKey);
        const a = artist === undefined ? null : clamp(artist / this.maxArtist);
        if (g === null && a === null) return 0;
        if (g === null) return a! * 0.85;
        if (a === null) return g;
        if (g < 0 || a < 0) return Math.min(g, a);
        return Math.max(g, a * 0.85);
    }
}

const entryArtistKey = (e: ListenEntry) => normalizeText(e.artists[0]?.name || e.artist);
export const songArtistKey = (s: Song) => normalizeText(s.artists[0] || s.artist);

/**
 * Perfil das escutas em volta das `hours` (null = o dia inteiro, o gosto geral).
 * `weekday` (0 = domingo ... 6 = sábado) dá o bônus do mesmo dia da semana.
 */
export function moodProfile(entries: ListenEntry[], hours: null | number[], nowMs: number, weekday?: number) {
    const genres = new Map<string, { name: string; score: number }>();
    const artists = new Map<string, number>();
    let mass = 0;
    for (const e of entries) {
        const date = new Date(e.playedAt);
        const hourW = hours ? hours.reduce((sum, h) => sum + hourWeight(date.getHours(), h), 0) / hours.length : 1;
        if (hourW <= 0) continue;
        const recency = Math.pow(0.5, Math.max(0, nowMs - e.playedAt) / DAY_MS / HALF_LIFE_DAYS);
        const dayW = weekday !== undefined && date.getDay() === weekday ? SAME_WEEKDAY_BONUS : 1;
        const base = hourW * recency * dayW;
        const w = e.counted ? base * Math.min(e.listenedMs / 60_000, 8) : base * SKIP_WEIGHT;
        if (w > 0) mass += w;
        for (const g of e.genres) {
            const key = normalizeText(g);
            if (!key) continue;
            const cur = genres.get(key) || { name: g, score: 0 };
            cur.score += w;
            genres.set(key, cur);
        }
        const artist = entryArtistKey(e);
        if (artist) artists.set(artist, (artists.get(artist) || 0) + w);
    }
    return new MoodProfile(genres, artists, mass);
}

/** Gosto geral sem histórico de escuta: gêneros e artistas das mais tocadas (contagens). */
export function profileFromPlayCounts(songs: Song[], playCounts: Record<string, number>) {
    const genres = new Map<string, { name: string; score: number }>();
    const artists = new Map<string, number>();
    let mass = 0;
    for (const s of songs) {
        const n = playCounts[s.id] || 0;
        if (n <= 0) continue;
        mass += n;
        for (const g of s.genres) {
            const key = normalizeText(g);
            const cur = genres.get(key) || { name: g, score: 0 };
            cur.score += n;
            genres.set(key, cur);
        }
        const artist = songArtistKey(s);
        if (artist) artists.set(artist, (artists.get(artist) || 0) + n);
    }
    return new MoodProfile(genres, artists, mass);
}

/** Nota de encaixe de cada música num horário (0 a 1), juntando o perfil, o gosto geral e a energia. */
export class MoodScorer {
    private readonly cache = new Map<string, number>();

    constructor(
        private readonly hourProfile: MoodProfile,
        private readonly general: MoodProfile,
        private readonly hours: number[],
    ) {}

    fit(song: Song) {
        const cached = this.cache.get(song.id);
        if (cached !== undefined) return cached;
        const genreKeys = song.genres.map(normalizeText);
        const artist = songArtistKey(song);
        const c = this.hourProfile.confidence;
        const personal = this.hourProfile.affinity(genreKeys, artist);
        const taste = this.general.isEmpty ? 0.5 : (this.general.affinity(genreKeys, artist) + 1) / 2;
        const energy = energyFit(song.genres, this.hours);
        const value = Math.max(0, Math.min(1, c * personal + (1 - c) * (0.55 * energy + 0.45 * taste)));
        this.cache.set(song.id, value);
        return value;
    }

    /** Encaixe médio da playlist com o horário, em porcentagem. */
    matchPercent(songs: Song[]) {
        if (!songs.length) return undefined;
        return Math.max(0, Math.min(100, Math.floor((songs.reduce((sum, s) => sum + this.fit(s), 0) / songs.length) * 100)));
    }
}
