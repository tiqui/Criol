import { normalizeText, parseYear, splitArtists, splitGenres } from '@shared/text';
import type { Song } from '@shared/types';

import { md5, randomId } from '@/lib/md5';

import { currentBase, ensureBase, failover } from './endpoint';
import {
    type FavoriteKind,
    type MusicSource,
    type Playlist,
    type SourceConfig,
    SourceError,
    type SyncProgress,
    type SyncResult,
} from './types';

/** Cliente da API Subsonic/OpenSubsonic (Navidrome, Gonic, Airsonic, Ampache...). */

const API_VERSION = '1.16.1';
const CLIENT = 'Criol';

type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

export const normalizeServerUrl = (url: string) => {
    let clean = url.trim().replace(/\/+$/, '');
    if (!/^https?:\/\//i.test(clean)) clean = `http://${clean}`;
    return clean.replace(/\/rest$/i, '');
};

export const subsonicAuth = (password: string) => {
    const salt = randomId(12);
    return { salt, token: md5(password + salt) };
};

const authParams = (config: SourceConfig) => {
    const params = new URLSearchParams({ c: CLIENT, f: 'json', u: config.username || '', v: API_VERSION });
    if (config.subsonic) {
        params.set('t', config.subsonic.token);
        params.set('s', config.subsonic.salt);
    }
    return params;
};

export const buildUrl = (config: SourceConfig, method: string, extra: Record<string, number | string | string[] | undefined> = {}) => {
    const params = authParams(config);
    for (const [key, value] of Object.entries(extra)) {
        if (value === undefined) continue;
        if (Array.isArray(value)) value.forEach((v) => params.append(key, v));
        else params.set(key, String(value));
    }
    return `${currentBase(config)}/rest/${method}?${params.toString()}`;
};

async function call(config: SourceConfig, method: string, extra: Record<string, number | string | string[] | undefined> = {}): Promise<Json> {
    let response: Response;
    try {
        response = await fetch(buildUrl(config, method, extra));
    } catch {
        // Sem resposta: se o outro endereço do servidor estiver no ar, tenta por ele.
        if (!(await failover(config))) throw new SourceError('Não foi possível conectar ao servidor.', 'network');
        try {
            response = await fetch(buildUrl(config, method, extra));
        } catch {
            throw new SourceError('Não foi possível conectar ao servidor.', 'network');
        }
    }
    if (!response.ok) throw new SourceError(`O servidor respondeu ${response.status}.`, response.status === 401 ? 'auth' : 'server');
    const body = (await response.json().catch(() => null)) as Json | null;
    const root = body?.['subsonic-response'];
    if (!root) throw new SourceError('Resposta inválida do servidor.', 'server');
    if (root.status !== 'ok') {
        const code = root.error?.code;
        throw new SourceError(root.error?.message || 'Erro do servidor.', code === 40 || code === 41 ? 'auth' : code === 70 ? 'notFound' : 'server');
    }
    return root;
}

/** Testa a conexão. Retorna o tipo de servidor ("navidrome", "gonic"...) e a versão. */
export async function pingSubsonic(config: SourceConfig) {
    const root = await call(config, 'ping');
    return { openSubsonic: Boolean(root.openSubsonic), type: (root.type as string) || 'subsonic', version: (root.serverVersion as string) || root.version };
}

const toMs = (value: unknown) => {
    if (!value) return 0;
    const t = Date.parse(String(value));
    return Number.isNaN(t) ? 0 : t;
};

interface AlbumInfo {
    artist: string;
    artistId?: string;
    year: number;
    created: number;
    coverArt?: string;
    genre?: string;
}

function toSong(raw: Json, album?: AlbumInfo): Song {
    const artistList: Json[] = Array.isArray(raw.artists) ? raw.artists : [];
    const albumArtistList: Json[] = Array.isArray(raw.albumArtists) ? raw.albumArtists : [];
    const artist = (raw.displayArtist as string) || (raw.artist as string) || artistList.map((a) => a.name).join('; ') || '';
    const artists = artistList.length ? artistList.map((a) => String(a.name)) : splitArtists(artist);
    const albumArtist = (raw.displayAlbumArtist as string) || albumArtistList.map((a) => a.name).join('; ') || album?.artist || '';

    const refs: Record<string, string> = {};
    for (const a of [...artistList, ...albumArtistList]) if (a?.id && a?.name) refs[normalizeText(a.name)] = String(a.id);
    if (raw.artistId && artists.length === 1) refs[normalizeText(artists[0])] = String(raw.artistId);
    if (album?.artistId && albumArtist) refs[normalizeText(albumArtist)] ??= album.artistId;

    const genres = Array.isArray(raw.genres) && raw.genres.length ? raw.genres.map((g: Json) => String(g.name)) : splitGenres(raw.genre || album?.genre || '');
    const path = typeof raw.path === 'string' ? raw.path.replace(/\\/g, '/') : '';

    return {
        album: raw.album || '',
        albumArtist,
        albumId: raw.albumId ? String(raw.albumId) : `noalbum-${raw.parent || raw.id}`,
        artist: artist || albumArtist || 'Artista desconhecido',
        artistRefs: Object.keys(refs).length ? refs : undefined,
        artists: artists.length ? artists : splitArtists(albumArtist),
        bitrate: Number(raw.bitRate) || 0,
        composer: raw.displayComposer || undefined,
        coverId: raw.coverArt ? String(raw.coverArt) : album?.coverArt || null,
        dateAdded: toMs(raw.created) || album?.created || 0,
        disc: Number(raw.discNumber) || 0,
        durationMs: (Number(raw.duration) || 0) * 1000,
        format: raw.suffix || '',
        genres,
        id: String(raw.id),
        path,
        sampleRate: Number(raw.samplingRate) || 0,
        size: Number(raw.size) || 0,
        title: raw.title || raw.name || '',
        track: Number(raw.track) || 0,
        year: parseYear(raw.year) || album?.year || 0,
    };
}

const toPlaylist = (raw: Json, username?: string): Playlist => ({
    comment: raw.comment || undefined,
    created: toMs(raw.created),
    id: String(raw.id),
    name: raw.name || '',
    readonly: Boolean(raw.owner && username && raw.owner !== username),
    songIds: Array.isArray(raw.entry) ? raw.entry.map((e: Json) => String(e.id)) : [],
    updated: toMs(raw.changed) || toMs(raw.created),
});

export function createSubsonicSource(config: SourceConfig): MusicSource {
    const fetchAlbums = async (onProgress: (p: SyncProgress) => void) => {
        const albums = new Map<string, AlbumInfo>();
        const raw: Json[] = [];
        for (let offset = 0; ; offset += 500) {
            const root = await call(config, 'getAlbumList2', { offset, size: 500, type: 'alphabeticalByName' });
            const page: Json[] = root.albumList2?.album || [];
            for (const a of page) {
                raw.push(a);
                albums.set(String(a.id), {
                    artist: a.displayArtist || a.artist || '',
                    artistId: a.artistId ? String(a.artistId) : undefined,
                    coverArt: a.coverArt ? String(a.coverArt) : undefined,
                    created: toMs(a.created),
                    genre: a.genre,
                    year: parseYear(a.year),
                });
            }
            onProgress({ done: albums.size, label: 'albums', total: 0 });
            if (page.length < 500) break;
        }
        return { albums, raw };
    };

    const fetchSongsBySearch = async (albums: Map<string, AlbumInfo>, onProgress: (p: SyncProgress) => void) => {
        const songs: Song[] = [];
        const rawSongs: Json[] = [];
        for (let offset = 0; ; offset += 500) {
            const root = await call(config, 'search3', { albumCount: 0, artistCount: 0, query: '', songCount: 500, songOffset: offset });
            const page: Json[] = root.searchResult3?.song || [];
            for (const s of page) {
                rawSongs.push(s);
                songs.push(toSong(s, albums.get(String(s.albumId))));
            }
            onProgress({ done: songs.length, label: 'songs', total: 0 });
            if (page.length < 500) break;
        }
        return { rawSongs, songs };
    };

    const fetchSongsByAlbum = async (albumIds: string[], albums: Map<string, AlbumInfo>, onProgress: (p: SyncProgress) => void) => {
        const songs: Song[] = [];
        const rawSongs: Json[] = [];
        const queue = [...albumIds];
        let done = 0;
        const worker = async () => {
            while (queue.length) {
                const id = queue.shift()!;
                try {
                    const root = await call(config, 'getAlbum', { id });
                    for (const s of root.album?.song || []) {
                        rawSongs.push(s);
                        songs.push(toSong(s, albums.get(id)));
                    }
                } catch {
                    /* álbum com problema: segue com os outros */
                }
                done += 1;
                if (done % 10 === 0) onProgress({ done, label: 'albums', total: albumIds.length });
            }
        };
        await Promise.all(Array.from({ length: 6 }, worker));
        return { rawSongs, songs };
    };

    return {
        capabilities: { scrobble: true, serverFavorites: true, serverPlaylists: true, serverRatings: true },
        config,

        async sync(onProgress) {
            const { albums } = await fetchAlbums(onProgress);
            let result = await fetchSongsBySearch(albums, onProgress);
            // Servidores que não aceitam busca vazia: busca álbum por álbum.
            if (result.songs.length === 0 && albums.size > 0) result = await fetchSongsByAlbum([...albums.keys()], albums, onProgress);

            const unique = new Map<string, Song>();
            result.songs.forEach((song) => unique.set(song.id, song));

            const ratings: Record<string, number> = {};
            const playCounts: Record<string, number> = {};
            const lastPlayed: Record<string, number> = {};
            const favSongs: string[] = [];
            for (const raw of result.rawSongs) {
                const id = String(raw.id);
                if (raw.userRating) ratings[id] = Number(raw.userRating);
                if (raw.playCount) playCounts[id] = Number(raw.playCount);
                if (raw.played) lastPlayed[id] = toMs(raw.played);
                if (raw.starred) favSongs.push(id);
            }

            onProgress({ done: 0, label: 'favorites', total: 0 });
            const starred = await call(config, 'getStarred2').catch(() => null);
            const favAlbums = (starred?.starred2?.album || []).map((a: Json) => String(a.id));
            const favArtists = (starred?.starred2?.artist || []).map((a: Json) => normalizeText(a.name));
            for (const s of starred?.starred2?.song || []) if (!favSongs.includes(String(s.id))) favSongs.push(String(s.id));

            onProgress({ done: 0, label: 'playlists', total: 0 });
            const playlists: Playlist[] = [];
            const list = await call(config, 'getPlaylists').catch(() => null);
            for (const p of list?.playlists?.playlist || []) {
                const full = await call(config, 'getPlaylist', { id: p.id }).catch(() => null);
                playlists.push(toPlaylist(full?.playlist || p, config.username));
            }

            return {
                favorites: { albums: favAlbums, artists: favArtists, songs: favSongs },
                lastPlayed,
                playCounts,
                playlists,
                ratings,
                songs: [...unique.values()],
            } satisfies SyncResult;
        },

        async streamUrl(song) {
            await ensureBase(config);
            return buildUrl(config, 'stream', { id: song.id });
        },

        coverUrl(coverId, size) {
            return buildUrl(config, 'getCoverArt', { id: coverId, size });
        },

        async lyrics(song) {
            try {
                const root = await call(config, 'getLyricsBySongId', { id: song.id });
                const list: Json[] = root.lyricsList?.structuredLyrics || [];
                const best = list.find((l) => l.synced) || list[0];
                if (best?.line?.length) {
                    const offset = Number(best.offset) || 0;
                    return best.synced
                        ? best.line
                              .map((line: Json) => {
                                  const ms = Math.max(0, (Number(line.start) || 0) - offset);
                                  const mm = String(Math.floor(ms / 60000)).padStart(2, '0');
                                  const ss = ((ms % 60000) / 1000).toFixed(2).padStart(5, '0');
                                  return `[${mm}:${ss}]${line.value ?? ''}`;
                              })
                              .join('\n')
                        : best.line.map((line: Json) => line.value ?? '').join('\n');
                }
            } catch {
                /* servidor sem OpenSubsonic: tenta a API antiga */
            }
            try {
                const root = await call(config, 'getLyrics', { artist: song.artists[0] || song.artist, title: song.title });
                const value = root.lyrics?.value;
                return typeof value === 'string' && value.trim() ? value : null;
            } catch {
                return null;
            }
        },

        async setFavorite(kind: FavoriteKind, id: string, value: boolean) {
            const param = kind === 'song' ? 'id' : kind === 'album' ? 'albumId' : 'artistId';
            await call(config, value ? 'star' : 'unstar', { [param]: id });
        },

        async setRating(songId, rating) {
            await call(config, 'setRating', { id: songId, rating });
        },

        async scrobble(song, submission, playedAt) {
            await call(config, 'scrobble', { id: song.id, submission: String(submission), time: submission ? playedAt : undefined });
        },

        async createPlaylist(name, songIds) {
            const root = await call(config, 'createPlaylist', { name, songId: songIds });
            if (root.playlist) return toPlaylist(root.playlist, config.username);
            // Servidores antigos não devolvem a playlist criada.
            const list = await call(config, 'getPlaylists');
            const found = (list.playlists?.playlist || []).filter((p: Json) => p.name === name).pop();
            return { created: Date.now(), id: String(found?.id || randomId()), name, songIds, updated: Date.now() };
        },

        async updatePlaylist(playlist, patch) {
            if (patch.songIds) {
                // createPlaylist com playlistId substitui a lista inteira de músicas.
                await call(config, 'createPlaylist', { playlistId: playlist.id, songId: patch.songIds });
            }
            if (patch.name !== undefined && patch.name !== playlist.name) {
                await call(config, 'updatePlaylist', { name: patch.name, playlistId: playlist.id });
            }
        },

        async deletePlaylist(playlist) {
            await call(config, 'deletePlaylist', { id: playlist.id });
        },
    };
}
