import { describe, expect, it } from 'vitest';

import type { Song } from '@shared/types';

import { Library, VARIOUS_ARTISTS } from './library/library';
import type { UserData } from './library/store';
import { md5 } from './lib/md5';
import { pickSongs } from './player/auto-dj';
import { buildRadio, titleKey } from './player/song-radio';

const song = (id: string, patch: Partial<Song> = {}): Song => ({
    album: 'Álbum',
    albumArtist: '',
    albumId: 'al1',
    artist: 'Artista',
    artists: ['Artista'],
    bitrate: 0,
    coverId: null,
    dateAdded: 0,
    disc: 1,
    durationMs: 200_000,
    format: 'mp3',
    genres: ['Rock'],
    id,
    path: '',
    sampleRate: 0,
    size: 0,
    title: `Música ${id}`,
    track: Number(id.replace(/\D/g, '')) || 0,
    year: 2000,
    ...patch,
});

const user: UserData = { favoriteAlbums: [], favoriteArtists: [], favoriteSongs: [], lastPlayed: {}, playCounts: {}, playlists: [], ratings: {} };

describe('md5', () => {
    it('confere com valores conhecidos', () => {
        expect(md5('')).toBe('d41d8cd98f00b204e9800998ecf8427e');
        expect(md5('sesame')).toBe('c8dae1c50e092f3d877192fc555b1dcf');
        expect(md5('ação senhas4lt')).toBe('56df47c57ee8ff64032463b2be0e8923');
    });
});

describe('biblioteca', () => {
    const lib = new Library([
        song('s2', { track: 2 }),
        song('s1', { track: 1 }),
        song('c1', { album: 'Coletânea', albumId: 'al2', artist: 'X', artists: ['X'], genres: ['Pop'] }),
        song('c2', { album: 'Coletânea', albumId: 'al2', artist: 'Y feat', artists: ['Y'], genres: ['Pop'] }),
        song('p1', { album: 'Pastas', albumId: 'al3', path: 'A/B/01 - p1.mp3' }),
    ]);

    it('monta álbuns em ordem de faixa', () => {
        expect(lib.albumsById.get('al1')!.songs.map((s) => s.id)).toEqual(['s1', 's2']);
        expect(lib.albumsById.get('al2')!.artist).toBe(VARIOUS_ARTISTS);
    });

    it('monta artistas, gêneros e pastas', () => {
        expect(lib.artistByName('artista')?.songs).toHaveLength(3);
        expect(lib.artistByName('X')?.appearsOn).toHaveLength(1);
        expect(lib.genres[0].name).toBe('Rock');
        expect(lib.foldersByPath.get('A')?.totalSongs).toBe(1);
        expect(lib.hasFolders).toBe(true);
    });

    it('busca ignorando acentos', () => {
        expect(lib.search('coletanea').albums.map((a) => a.id)).toEqual(['al2']);
        expect(lib.search('musica s1').songs.map((s) => s.id)).toEqual(['s1']);
    });
});

describe('rádio e Auto DJ', () => {
    const songs = Array.from({ length: 60 }, (_, i) =>
        song(`r${i}`, {
            album: `Álbum ${i % 12}`,
            albumId: `a${i % 12}`,
            artist: `Artista ${i % 6}`,
            artists: [`Artista ${i % 6}`],
            genres: [i % 2 ? 'Rock' : 'Jazz'],
            title: `Faixa ${i}`,
        }),
    );
    const lib = new Library(songs);

    it('o rádio não repete a semente nem o mesmo artista em sequência', () => {
        const seed = songs[0];
        const radio = buildRadio(lib, user, [], seed, 20);
        expect(radio).toHaveLength(20);
        expect(radio.some((s) => s.id === seed.id)).toBe(false);
        for (let i = 1; i < radio.length; i += 1) expect(radio[i].artists[0]).not.toBe(radio[i - 1].artists[0]);
        expect(new Set(radio.map(titleKey)).size).toBe(radio.length);
    });

    it('o Auto DJ respeita a quantidade e evita o que já está na fila', () => {
        const picks = pickSongs(lib, user, songs[1], new Set(['r3', 'r5']), { allowDuplicates: false, count: 5, mode: 'songs', strategy: 'similar' });
        expect(picks).toHaveLength(5);
        expect(picks.some((s) => s.id === 'r3' || s.id === 'r5' || s.id === 'r1')).toBe(false);
    });
});

describe('capas dos gêneros', () => {
    it('uma capa por artista, começando por quem tem mais músicas no gênero', () => {
        const songs = [
            song('g1', { albumId: 'a1', artist: 'Pouco', artists: ['Pouco'], coverId: 'c-pouco', genres: ['Hip Hop'] }),
            song('g2', { albumId: 'a2', artist: 'Muito', artists: ['Muito'], coverId: 'c-muito', genres: ['Hip Hop'] }),
            song('g3', { albumId: 'a2', artist: 'Muito', artists: ['Muito'], coverId: 'c-muito', genres: ['Hip Hop'] }),
            song('g4', { albumId: 'a3', artist: 'Muito', artists: ['Muito'], coverId: 'c-muito-2', genres: ['Hip Hop'] }),
            song('g5', { albumId: 'a4', artist: 'SemCapa', artists: ['SemCapa'], coverId: null, genres: ['Hip Hop'] }),
        ];
        const genre = new Library(songs).genres.find((g) => g.name === 'Hip Hop')!;
        expect(genre.coverIds).toEqual(['c-muito', 'c-pouco']);
        expect(genre.coverId).toBe('c-muito');
    });

    it('gênero sem nenhuma capa fica sem imagem', () => {
        const genre = new Library([song('h1', { genres: ['Jazz'] })]).genres[0];
        expect(genre.coverIds).toEqual([]);
        expect(genre.coverId).toBeNull();
    });
});
