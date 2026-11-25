import { useEffect } from 'react';

import { toast } from '@/components/toast';
import i18n from '@/i18n';

import { loadListeningHistory, monthKey as getMonthKey, type ListenEntry, useListening } from './listening-db';
import { filterByRange, getFavoriteDaypart, getMonthRange, summarize } from './listening-stats';
import { type MonthlyRecap, useProfile } from './profile-store';

export const formatMonth = (monthKey: string, language = i18n.language) => {
    const { from } = getMonthRange(monthKey);
    return new Intl.DateTimeFormat(language, { month: 'long' }).format(new Date(from));
};

export const getPreviousMonthKey = (monthKey: string) => {
    const { from } = getMonthRange(monthKey);
    const date = new Date(from);
    date.setMonth(date.getMonth() - 1);
    return getMonthKey(date);
};

export const buildMonthlyRecap = (entries: ListenEntry[], monthKey: string): MonthlyRecap => {
    const { from, to } = getMonthRange(monthKey);
    const monthEntries = filterByRange(entries, from, to);
    const summary = summarize(monthEntries, 10);

    const previous = getMonthRange(getPreviousMonthKey(monthKey));
    const previousEntries = filterByRange(entries, previous.from, previous.to);

    const artistsBefore = new Set(
        entries
            .filter((entry) => entry.playedAt < from)
            .map((entry) => (entry.artistId || entry.artist).toLowerCase()),
    );
    const newArtists = new Set(
        monthEntries
            .map((entry) => (entry.artistId || entry.artist).toLowerCase())
            .filter((key) => key && !artistsBefore.has(key)),
    ).size;

    const busiest = Object.entries(summary.byDay).sort((a, b) => b[1] - a[1])[0];
    const serverCounts = new Map<string, number>();
    monthEntries.forEach((entry) =>
        serverCounts.set(entry.serverId, (serverCounts.get(entry.serverId) || 0) + 1),
    );

    return {
        activeDays: summary.activeDays,
        busiestDay: busiest ? { day: busiest[0], ms: busiest[1] } : null,
        byDay: summary.byDay,
        byHour: summary.byHour,
        favoriteDaypart: getFavoriteDaypart(summary.byHour),
        generatedAt: Date.now(),
        month: monthKey,
        newArtists: entries.some((entry) => entry.playedAt < from) ? newArtists : 0,
        plays: summary.plays,
        previousTotalMs: previousEntries.length
            ? previousEntries.reduce((acc, entry) => acc + entry.listenedMs, 0)
            : null,
        serverId: [...serverCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || null,
        topAlbums: summary.topAlbums.slice(0, 5),
        topArtists: summary.topArtists.slice(0, 5),
        topGenres: summary.topGenres.slice(0, 5),
        topSongs: summary.topSongs.slice(0, 10),
        totalMs: summary.totalMs,
        uniqueArtists: summary.uniqueArtists,
        uniqueGenres: summary.uniqueGenres,
        uniqueSongs: summary.uniqueSongs,
    };
};

/** Generates and saves recaps for every finished month that has history but no recap yet. */
export const generatePendingRecaps = (entries: ListenEntry[]) => {
    const currentMonth = getMonthKey(new Date());
    const { recaps, saveRecap } = useProfile.getState();
    const months = new Set(entries.map((entry) => getMonthKey(entry.playedAt)));
    const created: string[] = [];

    for (const month of [...months].sort()) {
        if (month >= currentMonth || recaps[month]) continue;
        const recap = buildMonthlyRecap(entries, month);
        if (recap.totalMs <= 0) continue;
        saveRecap(recap);
        created.push(month);
    }

    return created;
};

const CHECK_INTERVAL_MS = 1000 * 60 * 30;

/** Mounted once at app level: produces the monthly recap as soon as a month closes. */
export const useMonthlyRecapGenerator = () => {
    useEffect(() => {
        let cancelled = false;

        const run = async () => {
            await loadListeningHistory();
            if (cancelled) return;
            const created = generatePendingRecaps(useListening.getState().entries);
            const latest = created[created.length - 1];
            if (latest) {
                toast.info(i18n.t('recap.readyTitle'), i18n.t('recap.readyMessage', { month: formatMonth(latest) }));
            }
        };

        void run();
        const interval = setInterval(run, CHECK_INTERVAL_MS);

        return () => {
            cancelled = true;
            clearInterval(interval);
        };
    }, []);
};
