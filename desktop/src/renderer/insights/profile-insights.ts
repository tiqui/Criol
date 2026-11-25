import type { ListenEntry } from './listening-db';
import {
    DAY_MS,
    getDayKey,
    getStreaks,
    ListeningSummary,
} from './listening-stats';
import type { IconName } from '@/components/icon';

export type Archetype =
    | 'balanced'
    | 'earlyBird'
    | 'explorer'
    | 'genreHopper'
    | 'loyal'
    | 'newcomer'
    | 'nightOwl'
    | 'repeater';

export const ARCHETYPE_ICON: Record<Archetype, IconName> = {
    balanced: 'sparkles',
    earlyBird: 'sunrise',
    explorer: 'compass',
    genreHopper: 'genre',
    loyal: 'favorite',
    newcomer: 'sparkles',
    nightOwl: 'moonStar',
    repeater: 'infinity',
};

const share = (part: number, total: number) => (total > 0 ? part / total : 0);

export const getListenerDna = (entries: ListenEntry[], summary: ListeningSummary) => {
    const total = summary.totalMs;
    const night = summary.byHour
        .map((ms, hour) => (hour >= 22 || hour < 5 ? ms : 0))
        .reduce((a, b) => a + b, 0);
    const early = summary.byHour
        .map((ms, hour) => (hour >= 5 && hour < 10 ? ms : 0))
        .reduce((a, b) => a + b, 0);

    const variety = Math.min(1, share(summary.uniqueArtists, Math.max(1, summary.plays)) * 2);
    const loyalty = share(
        summary.topArtists.slice(0, 5).reduce((acc, artist) => acc + artist.plays, 0),
        summary.plays,
    );

    const monthAgo = Date.now() - 30 * DAY_MS;
    const knownBefore = new Set(
        entries
            .filter((e) => e.playedAt < monthAgo)
            .map((e) => (e.artistId || e.artist).toLowerCase()),
    );
    const recent = entries.filter((e) => e.playedAt >= monthAgo);
    const recentArtists = new Set(recent.map((e) => (e.artistId || e.artist).toLowerCase()));
    const novelty = share(
        [...recentArtists].filter((artist) => !knownBefore.has(artist)).length,
        recentArtists.size,
    );

    const topSongShare = share(summary.topSongs[0]?.plays || 0, summary.plays);
    const topArtistShare = share(summary.topArtists[0]?.plays || 0, summary.plays);

    let archetype: Archetype = 'balanced';
    if (summary.plays < 25) archetype = 'newcomer';
    else if (share(night, total) > 0.4) archetype = 'nightOwl';
    else if (share(early, total) > 0.35) archetype = 'earlyBird';
    else if (topSongShare > 0.08) archetype = 'repeater';
    else if (topArtistShare > 0.25) archetype = 'loyal';
    else if (summary.uniqueGenres >= 15) archetype = 'genreHopper';
    else if (variety > 0.6) archetype = 'explorer';

    return {
        archetype,
        traits: {
            loyalty: Math.round(loyalty * 100),
            novelty: Math.round(novelty * 100),
            variety: Math.round(variety * 100),
        },
    };
};

export type Achievement = {
    current: number;
    icon: IconName;
    id: string;
    target: number;
    unlocked: boolean;
};

export const getAchievements = (
    entries: ListenEntry[],
    summary: ListeningSummary,
): Achievement[] => {
    const hours = summary.totalMs / 3_600_000;
    const maxDayMs = Math.max(0, ...Object.values(summary.byDay));
    const counted = entries.filter((entry) => entry.counted);
    const nightPlays = counted.filter((e) => new Date(e.playedAt).getHours() < 5).length;
    const earlyPlays = counted.filter((e) => {
        const hour = new Date(e.playedAt).getHours();
        return hour >= 5 && hour < 8;
    }).length;

    const songPerDay = new Map<string, number>();
    for (const entry of counted) {
        const key = `${getDayKey(entry.playedAt)}|${entry.songId}`;
        songPerDay.set(key, (songPerDay.get(key) || 0) + 1);
    }
    const maxSongInDay = Math.max(0, ...songPerDay.values());
    const { longest } = getStreaks(entries);

    const make = (
        id: string,
        icon: IconName,
        current: number,
        target: number,
    ): Achievement => ({
        current: Math.min(current, target),
        icon,
        id,
        target,
        unlocked: current >= target,
    });

    return [
        make('firstPlay', 'playCircle', summary.plays, 1),
        make('hours10', 'clock', Math.floor(hours), 10),
        make('hours100', 'trophy', Math.floor(hours), 100),
        make('marathon', 'flame', Math.floor(maxDayMs / 60_000), 180),
        make('nightOwl', 'moonStar', nightPlays, 50),
        make('earlyBird', 'sunrise', earlyPlays, 50),
        make('explorer', 'compass', summary.uniqueArtists, 100),
        make('genreHopper', 'genre', summary.uniqueGenres, 20),
        make('superfan', 'favorite', summary.topArtists[0]?.plays || 0, 100),
        make('brokenRecord', 'infinity', maxSongInDay, 10),
        make('streak7', 'calendar', longest, 7),
        make('streak30', 'calendar', longest, 30),
    ];
};
