import { describe, expect, it } from 'vitest';

import type { Song } from '@shared/types';

import { Library } from '@/library/library';
import type { UserData } from '@/library/store';

import { entryFromSong, type ListenEntry } from './listening-db';
import { getDaypart } from './listening-stats';
import { MixGenerator } from './mixes';
import { energyFit, genreEnergy, moodProfile } from './mood';

let n = 0;
const song = (artist: string, genre: string): Song => {
    n += 1;
    return {
        album: `${artist} LP`,
        albumArtist: artist,
        albumId: `${artist}|LP`,
        artist,
        artists: [artist],
        bitrate: 0,
        coverId: null,
        dateAdded: 0,
        disc: 1,
        durationMs: 200_000,
        format: 'mp3',
        genres: [genre],
        id: `s${n}`,
        path: '',
        sampleRate: 0,
        size: 0,
        title: `Faixa ${n}`,
        track: n,
        year: 2000,
    };
};

const GENRES: Record<string, string[]> = {
    'Heavy Metal': ['Metallica', 'Slayer', 'Iron Maiden', 'Sepultura'],
    Jazz: ['Miles Davis', 'Chet Baker', 'Bill Evans', 'Nina Simone'],
    MPB: ['Gal Costa', 'Caetano', 'Milton', 'Elis'],
    Trap: ['Travis Scott', 'Future', 'Matuê', 'Teto'],
};
const songs = Object.entries(GENRES).flatMap(([genre, artists]) => artists.flatMap((a) => Array.from({ length: 6 }, () => song(a, genre))));
const lib = new Library(songs);
const of = (genre: string) => songs.filter((s) => s.genres[0] === genre);
const user: UserData = { favoriteAlbums: [], favoriteArtists: [], favoriteSongs: [], lastPlayed: {}, playCounts: {}, playlists: [], ratings: {} };

const today = new Date(2026, 10, 27, 0, 0);
const at = (daysAgo: number, hour: number, minute = 0) => new Date(2026, 10, 27 - daysAgo, hour, minute).getTime();

/** Escutas de `genre` nas `hours` dos últimos `days` dias (`listened` < 30 s = pulada). */
const history = (genre: string, hours: number[], days = 14, listened = 180_000): ListenEntry[] => {
    const out: ListenEntry[] = [];
    for (let d = 1; d <= days; d += 1) {
        for (const h of hours) {
            of(genre)
                .filter((_, i) => (i + d + h) % 8 < 3)
                .forEach((s, i) => out.push(entryFromSong(s, 'local', listened, at(d, h, i * 7), `e${d}-${h}-${s.id}`)));
        }
    }
    return out;
};

const daylistAt = (hour: number, entries: ListenEntry[]) => {
    const now = new Date(today);
    now.setHours(hour, 15);
    const mix = new MixGenerator(lib, user, entries, 'local', now).daypartMix(getDaypart(now), true);
    const count: Record<string, number> = {};
    for (const id of mix.songIds) {
        const genre = lib.songsById.get(id)!.genres[0];
        count[genre] = (count[genre] || 0) + 1;
    }
    return { count, mix };
};

describe('humor por horário', () => {
    it('sem histórico não toca metal de manhã', () => {
        const { count, mix } = daylistAt(7, []);
        expect(count['Heavy Metal'] || 0).toBe(0);
        expect(mix.songIds.length).toBeGreaterThanOrEqual(20);
        expect(mix.moodMatch).toBeDefined();
        expect(mix.tags).not.toContain('Heavy Metal');
    });

    it('quem ouve metal toda manhã recebe metal de manhã', () => {
        const { count, mix } = daylistAt(7, history('Heavy Metal', [6, 7, 8]));
        expect(mix.tags[0]).toBe('Heavy Metal');
        expect(count['Heavy Metal']).toBeGreaterThan(mix.songIds.length / 2);
        expect(mix.moodMatch).toBeGreaterThanOrEqual(70);
    });

    it('jazz cedo e trap depois das 9h', () => {
        const entries = [...history('Jazz', [5, 6, 7]), ...history('Trap', [9, 10, 11])];
        expect(moodProfile(entries, [6], today.getTime()).topGenres(1)).toEqual(['Jazz']);
        expect(moodProfile(entries, [10], today.getTime()).topGenres(1)).toEqual(['Trap']);
        const early = daylistAt(5, entries);
        const late = daylistAt(9, entries);
        expect(early.mix.tags[0]).toBe('Jazz');
        expect(late.mix.tags[0]).toBe('Trap');
        expect(early.count.Jazz || 0).toBeGreaterThan(early.count.Trap || 0);
        expect(late.count.Trap || 0).toBeGreaterThan(late.count.Jazz || 0);
    });

    it('puladas de manhã saem da manhã mas ficam à noite', () => {
        const entries = [...history('Heavy Metal', [21, 22]), ...history('Heavy Metal', [8, 9], 14, 15_000), ...history('MPB', [8, 9])];
        expect(daylistAt(9, entries).count['Heavy Metal'] || 0).toBe(0);
        expect(daylistAt(21, entries).count['Heavy Metal'] || 0).toBeGreaterThan(10);
    });

    it('energia por gênero', () => {
        expect(genreEnergy(['Heavy Metal'])).toBeGreaterThanOrEqual(0.9);
        expect(genreEnergy(['Bossa Nova'])).toBeLessThanOrEqual(0.3);
        expect(genreEnergy(['Trap'])).toBeCloseTo(0.75, 3); // não confunde com "rap"
        expect(genreEnergy(['Desconhecido'])).toBeNull();
        expect(energyFit(['Jazz'], [7])).toBeGreaterThan(energyFit(['Metal'], [7]));
    });

    it('a daylist muda a cada 4 horas', () => {
        const key = (h: number) => new MixGenerator(lib, user, [], 'local', new Date(2026, 10, 27, h, 30)).daypartMix('morning', true).periodKey;
        expect(key(8)).toBe(key(11));
        expect(key(8)).not.toBe(key(12));
    });
});
