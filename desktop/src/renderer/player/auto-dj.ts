import { normalizeText } from '@shared/text';
import type { Song } from '@shared/types';

import type { Album, Library } from '@/library/library';
import type { UserData } from '@/library/store';

/**
 * Auto DJ: quando a fila está acabando, escolhe mais músicas ou álbuns.
 *
 * "Parecidas": pontuação local (gênero, artista, época, favoritos/avaliação) e sorteio com peso,
 * misturando ~20% de músicas aleatórias para variar. "Aleatória": qualquer coisa da biblioteca.
 */

export interface AutoDjOptions {
    mode: 'albums' | 'songs';
    strategy: 'random' | 'similar';
    count: number;
    allowDuplicates: boolean;
}

const MAX_SONGS = 150;
const MAX_ALBUMS = 20;
const MIN_SIMILAR = 3;
const RECENT_MS = 2 * 60 * 60 * 1000;

export const shuffled = <T,>(items: T[], random = Math.random) => {
    const out = [...items];
    for (let i = out.length - 1; i > 0; i -= 1) {
        const j = Math.floor(random() * (i + 1));
        [out[i], out[j]] = [out[j], out[i]];
    }
    return out;
};

/** Sorteio ponderado sem reposição (Efraimidis–Spirakis). */
export const weightedOrder = <T,>(items: [T, number][], random = Math.random) =>
    items
        .map(([item, w]) => [item, Math.log(Math.max(random(), 1e-12)) / w] as const)
        .sort((a, b) => b[1] - a[1])
        .map(([item]) => item);

interface Ctx {
    genres: Set<string>;
    artists: Set<string>;
    year: number;
}

const ctxFor = (song: Song): Ctx => ({
    artists: new Set([...song.artists, song.albumArtist].filter(Boolean).map(normalizeText)),
    genres: new Set(song.genres.map(normalizeText)),
    year: song.year,
});

const commonScore = (ctx: Ctx, genres: string[], artists: string[], year: number) => {
    let score = 0;
    const g = genres.filter((x) => ctx.genres.has(normalizeText(x))).length;
    if (g > 0) score += 3 + (g - 1);
    if (artists.some((a) => ctx.artists.has(normalizeText(a)))) score += 4;
    if (ctx.year > 0 && year > 0) {
        const d = Math.abs(ctx.year - year);
        score += d <= 3 ? 1.5 : d <= 8 ? 0.75 : 0;
    }
    return score;
};

export function pickSongs(library: Library, user: UserData, current: Song, queueIds: Set<string>, opt: AutoDjOptions, now = Date.now()) {
    const count = Math.min(Math.max(opt.count, 1), MAX_SONGS);
    const available = library.songs.filter((s) => s.id !== current.id && (opt.allowDuplicates || !queueIds.has(s.id)));
    if (!available.length) return [];
    if (opt.strategy === 'random') return shuffled(available).slice(0, count);

    const ctx = ctxFor(current);
    const favorites = new Set(user.favoriteSongs);
    const scored: [Song, number][] = [];
    for (const s of available) {
        let score = commonScore(ctx, s.genres, [...s.artists, s.albumArtist], s.year);
        if (score <= 0) continue;
        if (favorites.has(s.id)) score += 1;
        score += (user.ratings[s.id] || 0) * 0.3;
        const last = user.lastPlayed[s.id];
        if (last && now - last < RECENT_MS) score *= 0.25;
        if (score >= MIN_SIMILAR) scored.push([s, score]);
    }

    const selected = new Map<string, Song>();
    const perAlbum = new Map<string, number>();
    const spice = Math.floor(count * 0.2);
    for (const s of weightedOrder(scored)) {
        if (selected.size >= count - spice) break;
        if ((perAlbum.get(s.albumId) || 0) >= 2) continue;
        perAlbum.set(s.albumId, (perAlbum.get(s.albumId) || 0) + 1);
        selected.set(s.id, s);
    }
    for (const s of shuffled(available)) {
        if (selected.size >= count) break;
        if (!selected.has(s.id)) selected.set(s.id, s);
    }
    return [...selected.values()];
}

export function pickAlbums(library: Library, user: UserData, current: Song, queueAlbumIds: Set<string>, opt: AutoDjOptions): Album[] {
    const count = Math.min(Math.max(opt.count, 1), MAX_ALBUMS);
    const available = library.albums.filter((a) => a.id !== current.albumId && (opt.allowDuplicates || !queueAlbumIds.has(a.id)));
    if (!available.length) return [];
    if (opt.strategy === 'random') return shuffled(available).slice(0, count);

    const ctx = ctxFor(current);
    const favorites = new Set(user.favoriteAlbums);
    const scored: [Album, number][] = [];
    for (const a of available) {
        let score = commonScore(ctx, a.genres, a.artistNames, a.year);
        if (score <= 0) continue;
        if (favorites.has(a.id)) score += 1;
        if (score >= MIN_SIMILAR) scored.push([a, score]);
    }
    const spice = count < 3 ? 0 : Math.floor(count * 0.2);
    const selected = new Map<string, Album>();
    for (const a of weightedOrder(scored).slice(0, count - spice)) selected.set(a.id, a);
    for (const a of shuffled(available)) {
        if (selected.size >= count) break;
        if (!selected.has(a.id)) selected.set(a.id, a);
    }
    return [...selected.values()];
}
