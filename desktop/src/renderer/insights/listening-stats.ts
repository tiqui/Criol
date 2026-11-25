import type { ListenEntry } from './listening-db';

export type Daypart = 'afternoon' | 'dawn' | 'morning' | 'night';

export const DAYPARTS: Daypart[] = ['morning', 'afternoon', 'night', 'dawn'];

export const getDaypart = (date: Date | number): Daypart => {
    const hour = (typeof date === 'number' ? new Date(date) : date).getHours();
    if (hour < 5) return 'dawn';
    if (hour < 12) return 'morning';
    if (hour < 18) return 'afternoon';
    return 'night';
};

export const DAY_MS = 24 * 60 * 60 * 1000;

export type ListeningSummary = {
    activeDays: number;
    byDay: Record<string, number>;
    byHour: number[];
    byWeekday: number[];
    firstListenAt: null | number;
    lastListenAt: null | number;
    plays: number;
    topAlbums: RankedItem[];
    topArtists: RankedItem[];
    topGenres: RankedItem[];
    topSongs: RankedItem[];
    totalMs: number;
    uniqueArtists: number;
    uniqueGenres: number;
    uniqueSongs: number;
};

export type RankedItem = {
    id: string;
    imageId: null | string;
    listenedMs: number;
    name: string;
    plays: number;
    serverId?: string;
    /** Extra label (artist name for songs/albums). */
    subtitle?: string;
};

export const getDayKey = (date: Date | number) => {
    const d = typeof date === 'number' ? new Date(date) : date;
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
        d.getDate(),
    ).padStart(2, '0')}`;
};

export const filterByRange = (entries: ListenEntry[], from: number, to = Infinity) =>
    entries.filter((entry) => entry.playedAt >= from && entry.playedAt < to);

export const getMonthRange = (monthKey: string) => {
    const [year, month] = monthKey.split('-').map(Number);
    const from = new Date(year, month - 1, 1).getTime();
    const to = new Date(year, month, 1).getTime();
    return { from, to };
};

const bump = (
    map: Map<string, RankedItem>,
    key: string,
    base: Omit<RankedItem, 'listenedMs' | 'plays' | 'serverId'>,
    entry: ListenEntry,
) => {
    const item = map.get(key) || { ...base, listenedMs: 0, plays: 0, serverId: entry.serverId };
    item.listenedMs += entry.listenedMs;
    if (entry.counted) item.plays += 1;
    if (!item.imageId && base.imageId) item.imageId = base.imageId;
    map.set(key, item);
};

const rank = (map: Map<string, RankedItem>, limit: number) =>
    [...map.values()]
        .filter((item) => item.plays > 0 || item.listenedMs > 0)
        .sort((a, b) => b.plays - a.plays || b.listenedMs - a.listenedMs)
        .slice(0, limit);

export const summarize = (entries: ListenEntry[], limit = 10): ListeningSummary => {
    const songs = new Map<string, RankedItem>();
    const artists = new Map<string, RankedItem>();
    const albums = new Map<string, RankedItem>();
    const genres = new Map<string, RankedItem>();
    const byHour = new Array(24).fill(0);
    const byWeekday = new Array(7).fill(0);
    const byDay: Record<string, number> = {};

    let totalMs = 0;
    let plays = 0;
    let first: null | number = null;
    let last: null | number = null;

    for (const entry of entries) {
        totalMs += entry.listenedMs;
        if (entry.counted) plays += 1;
        first = first === null ? entry.playedAt : Math.min(first, entry.playedAt);
        last = last === null ? entry.playedAt : Math.max(last, entry.playedAt);

        const date = new Date(entry.playedAt);
        byHour[date.getHours()] += entry.listenedMs;
        byWeekday[date.getDay()] += entry.listenedMs;
        const dayKey = getDayKey(date);
        byDay[dayKey] = (byDay[dayKey] || 0) + entry.listenedMs;

        bump(
            songs,
            entry.songId,
            { id: entry.songId, imageId: entry.imageId, name: entry.title, subtitle: entry.artist },
            entry,
        );

        if (entry.artist) {
            bump(
                artists,
                entry.artistId || entry.artist.toLowerCase(),
                { id: entry.artistId || '', imageId: null, name: entry.artist },
                entry,
            );
        }

        if (entry.albumId) {
            bump(
                albums,
                entry.albumId,
                {
                    id: entry.albumId,
                    imageId: entry.imageId,
                    name: entry.album,
                    subtitle: entry.artist,
                },
                entry,
            );
        }

        for (const genre of entry.genres) {
            bump(
                genres,
                genre.toLowerCase(),
                { id: genre.toLowerCase(), imageId: null, name: genre },
                entry,
            );
        }
    }

    return {
        activeDays: Object.keys(byDay).length,
        byDay,
        byHour,
        byWeekday,
        firstListenAt: first,
        lastListenAt: last,
        plays,
        topAlbums: rank(albums, limit),
        topArtists: rank(artists, limit),
        topGenres: rank(genres, limit),
        topSongs: rank(songs, limit),
        totalMs,
        uniqueArtists: artists.size,
        uniqueGenres: genres.size,
        uniqueSongs: songs.size,
    };
};

/** Consecutive days (ending today or yesterday) with at least one listen, plus the record. */
export const getStreaks = (entries: ListenEntry[]) => {
    const days = new Set(entries.map((entry) => getDayKey(entry.playedAt)));
    if (days.size === 0) return { current: 0, longest: 0 };

    const sorted = [...days].sort();
    let longest = 1;
    let run = 1;
    for (let i = 1; i < sorted.length; i += 1) {
        const prev = new Date(`${sorted[i - 1]}T12:00:00`).getTime();
        const cur = new Date(`${sorted[i]}T12:00:00`).getTime();
        run = Math.round((cur - prev) / DAY_MS) === 1 ? run + 1 : 1;
        longest = Math.max(longest, run);
    }

    let current = 0;
    const cursor = new Date();
    if (!days.has(getDayKey(cursor))) cursor.setDate(cursor.getDate() - 1);
    while (days.has(getDayKey(cursor))) {
        current += 1;
        cursor.setDate(cursor.getDate() - 1);
    }

    return { current, longest };
};

export const getFavoriteDaypart = (byHour: number[]): Daypart | null => {
    const totals: Record<Daypart, number> = { afternoon: 0, dawn: 0, morning: 0, night: 0 };
    byHour.forEach((ms, hour) => {
        totals[getDaypart(new Date(2000, 0, 1, hour))] += ms;
    });
    const best = (Object.entries(totals) as [Daypart, number][]).sort((a, b) => b[1] - a[1])[0];
    return best && best[1] > 0 ? best[0] : null;
};

/**
 * Score songs by plays with an exponential recency decay so recent favourites rank higher.
 * Optional filter lets callers restrict to a daypart, weekday, etc.
 */
export const scoreSongs = (
    entries: ListenEntry[],
    options: { filter?: (entry: ListenEntry) => boolean; halfLifeDays?: number; now?: number } = {},
) => {
    const { filter, halfLifeDays = 30, now = Date.now() } = options;
    const scores = new Map<string, { entry: ListenEntry; plays: number; score: number }>();

    for (const entry of entries) {
        if (!entry.counted) continue;
        if (filter && !filter(entry)) continue;
        const ageDays = (now - entry.playedAt) / DAY_MS;
        const weight = Math.pow(0.5, ageDays / halfLifeDays);
        const current = scores.get(entry.songId);
        if (current) {
            current.score += weight;
            current.plays += 1;
            current.entry = entry;
        } else {
            scores.set(entry.songId, { entry, plays: 1, score: weight });
        }
    }

    return [...scores.values()].sort((a, b) => b.score - a.score);
};

export const scoreGenres = (
    entries: ListenEntry[],
    options: { filter?: (entry: ListenEntry) => boolean; halfLifeDays?: number; now?: number } = {},
) => {
    const { filter, halfLifeDays = 45, now = Date.now() } = options;
    const scores = new Map<string, { name: string; score: number }>();

    for (const entry of entries) {
        if (filter && !filter(entry)) continue;
        const ageDays = (now - entry.playedAt) / DAY_MS;
        const weight = Math.pow(0.5, ageDays / halfLifeDays) * (entry.listenedMs / 60_000);
        for (const genre of entry.genres) {
            const key = genre.toLowerCase();
            const current = scores.get(key) || { name: genre, score: 0 };
            current.score += weight;
            scores.set(key, current);
        }
    }

    return [...scores.values()].sort((a, b) => b.score - a.score);
};

export const msToMinutes = (ms: number) => Math.round(ms / 60_000);
