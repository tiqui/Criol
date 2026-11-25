import { normalizeText } from '@shared/text';
import type { Song } from '@shared/types';

import type { ListenEntry } from '@/insights/listening-db';
import type { Library } from '@/library/library';
import type { UserData } from '@/library/store';

import { weightedOrder } from './auto-dj';

/**
 * "Rádio da música": a partir de uma música, monta uma fila de faixas parecidas que nunca acaba
 * (o player pede mais quando está terminando).
 *
 * A semelhança usa só o que existe na biblioteca e no histórico:
 * - gêneros, artista e época da música-semente;
 * - artistas relacionados: os que dividem faixas com o artista e os que você costuma ouvir na
 *   mesma sessão que ele;
 * - "ouvidas junto": músicas que você tocou perto (±45 min) de quando tocou a semente;
 * - favoritos e avaliações; o que tocou nas últimas horas perde peso.
 *
 * Variedade: no máximo 2 por álbum, poucas do mesmo artista, sem versões repetidas da mesma
 * música (ao vivo/remaster) e nunca o mesmo artista duas vezes seguidas. Quando o rádio continua,
 * os gêneros das últimas faixas entram com peso menor, então ele passeia devagar sem perder a
 * semente de vista.
 */

export const RADIO_START_SIZE = 50;
export const RADIO_REFILL_SIZE = 25;
export const RADIO_REFILL_AT = 5;

const MAX_PER_ALBUM = 2;
const MAX_PER_ARTIST = 3;
const SESSION_WINDOW_MS = 45 * 60 * 1000;
const RECENT_MS = 3 * 60 * 60 * 1000;
const VERSION_NOISE = /\s*[([][^)\]]*[)\]]\s*|\s+-\s+.*$/g;

const mainArtist = (s: Song) => normalizeText(s.artists[0] || s.artist);
const genresOf = (s: Song) => new Set(s.genres.map(normalizeText));
const namesOf = (s: Song) => [...s.artists, ...(s.albumArtist ? [s.albumArtist] : [])].map(normalizeText);
const yearGap = (a: Song, b: Song) => (a.year > 0 && b.year > 0 ? Math.abs(a.year - b.year) : 99);

/** "Música (Ao Vivo)" e "Música - Remaster 2011" contam como a mesma música. */
export const titleKey = (s: Song) => {
    const base = normalizeText(s.title.replace(VERSION_NOISE, ' ')) || normalizeText(s.title);
    return `${mainArtist(s)}|${base}`;
};

const normalizeWeights = (map: Map<string, number>) => {
    const top = Math.max(0, ...map.values());
    const out = new Map<string, number>();
    if (top <= 0) return out;
    for (const [k, v] of map) out.set(k, v / top);
    return out;
};

export function buildRadio(
    library: Library,
    user: UserData,
    history: ListenEntry[],
    seed: Song,
    size: number,
    exclude: Set<string> = new Set(),
    recent: Song[] = [],
    now = Date.now(),
    random = Math.random,
): Song[] {
    if (size <= 0) return [];
    const candidates = library.songs.filter((s) => s.id !== seed.id && !exclude.has(s.id));
    if (!candidates.length) return [];

    const seedArtists = new Set(namesOf(seed));
    const genres = new Map<string, number>();
    genresOf(seed).forEach((g) => genres.set(g, 1));
    for (const r of recent.slice(-5)) for (const g of genresOf(r)) genres.set(g, Math.min(1, (genres.get(g) || 0) + 0.35));
    const genreTotal = Math.max(1e-9, [...genres.values()].reduce((a, b) => a + b, 0));

    const rel = new Map<string, number>();
    for (const s of library.songs) {
        const names = new Set(namesOf(s));
        if (![...names].some((n) => seedArtists.has(n))) continue;
        for (const n of names) if (!seedArtists.has(n)) rel.set(n, (rel.get(n) || 0) + 1);
    }
    const together = new Map<string, number>();
    const sorted = history;
    const windowOf = (from: number, to: number) => {
        let lo = 0;
        let hi = sorted.length;
        while (lo < hi) {
            const mid = (lo + hi) >>> 1;
            if (sorted[mid].playedAt < from) lo = mid + 1;
            else hi = mid;
        }
        const out: ListenEntry[] = [];
        for (let i = lo; i < sorted.length && sorted[i].playedAt <= to; i += 1) out.push(sorted[i]);
        return out;
    };
    for (const anchor of sorted) {
        const strong = anchor.songId === seed.id;
        if (!strong && !anchor.artists.some((a) => seedArtists.has(normalizeText(a.name)))) continue;
        const w = strong ? 1 : 0.4;
        for (const e of windowOf(anchor.playedAt - SESSION_WINDOW_MS, anchor.playedAt + SESSION_WINDOW_MS)) {
            if (e.songId === anchor.songId) continue;
            together.set(e.songId, (together.get(e.songId) || 0) + w);
            for (const a of e.artists) {
                const k = normalizeText(a.name);
                if (!seedArtists.has(k)) rel.set(k, (rel.get(k) || 0) + 0.5 * w);
            }
        }
    }
    const related = normalizeWeights(rel);
    const heardTogether = normalizeWeights(together);
    const favorites = new Set(user.favoriteSongs);

    const score = (s: Song) => {
        let genre = 0;
        for (const g of genresOf(s)) genre += genres.get(g) || 0;
        genre /= genreTotal;
        const names = namesOf(s);
        const sameArtist = names.some((n) => seedArtists.has(n));
        const relatedScore = Math.max(0, ...names.map((n) => related.get(n) || 0));
        const tog = heardTogether.get(s.id) || 0;
        if (genre <= 0 && !sameArtist && relatedScore <= 0 && tog <= 0) return 0;
        let value = 4 * Math.min(1, genre) + (sameArtist ? 3 : 0) + 2.5 * relatedScore + 4.5 * tog;
        const gap = yearGap(seed, s);
        value += gap <= 3 ? 1.2 : gap <= 8 ? 0.6 : 0;
        if (favorites.has(s.id)) value += 0.6;
        value += (user.ratings[s.id] || 0) * 0.25;
        const last = user.lastPlayed[s.id];
        if (last && now - last < RECENT_MS) value *= 0.35;
        return value;
    };

    const scored: [Song, number][] = [];
    for (const s of candidates) {
        const value = score(s);
        if (value > 0) scored.push([s, value ** 1.6]);
    }
    const ordered = weightedOrder(scored, random);

    const picked: Song[] = [];
    const taken = new Set<string>();
    const perAlbum = new Map<string, number>();
    const perArtist = new Map<string, number>();
    const titles = new Set([titleKey(seed), ...recent.map(titleKey)]);
    const seedArtistCap = Math.max(3, Math.floor(size / 5));

    const tryAdd = (s: Song, caps: boolean) => {
        if (taken.has(s.id)) return;
        const tk = titleKey(s);
        if (titles.has(tk)) return;
        const artist = mainArtist(s);
        if (caps) {
            if ((perAlbum.get(s.albumId) || 0) >= MAX_PER_ALBUM) return;
            const cap = seedArtists.has(artist) ? seedArtistCap : MAX_PER_ARTIST;
            if ((perArtist.get(artist) || 0) >= cap) return;
        }
        picked.push(s);
        taken.add(s.id);
        titles.add(tk);
        perAlbum.set(s.albumId, (perAlbum.get(s.albumId) || 0) + 1);
        perArtist.set(artist, (perArtist.get(artist) || 0) + 1);
    };

    for (const s of ordered) {
        if (picked.length >= size) break;
        tryAdd(s, true);
    }
    for (const s of ordered) {
        if (picked.length >= size) break;
        tryAdd(s, false);
    }
    if (picked.length < size) {
        const rest = candidates
            .filter((s) => !taken.has(s.id))
            .map((s) => [s, yearGap(seed, s) + random() * 6] as const)
            .sort((a, b) => a[1] - b[1]);
        for (const [s] of rest) {
            if (picked.length >= size) break;
            tryAdd(s, false);
        }
    }

    // O mesmo artista não toca duas vezes seguidas (quando der).
    const pool = [...picked];
    const out: Song[] = [];
    let last = mainArtist(recent[recent.length - 1] || seed);
    while (pool.length) {
        // Se um artista tem tantas faixas sobrando que depois não daria para intercalar, ele vem
        // agora; senão, segue a ordem de relevância.
        const counts = new Map<string, number>();
        for (const s of pool) counts.set(mainArtist(s), (counts.get(mainArtist(s)) || 0) + 1);
        let crowded = '';
        for (const [artist, count] of counts) {
            if (artist !== last && count > Math.ceil((pool.length - 1) / 2)) crowded = artist;
        }
        let i = crowded ? pool.findIndex((s) => mainArtist(s) === crowded) : pool.findIndex((s) => mainArtist(s) !== last);
        if (i < 0) i = 0;
        const [s] = pool.splice(i, 1);
        out.push(s);
        last = mainArtist(s);
    }
    return out;
}
