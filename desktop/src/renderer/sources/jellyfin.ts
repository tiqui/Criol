import { normalizeText, parseYear, splitArtists } from '@shared/text';
import type { Song } from '@shared/types';

import { randomId } from '@/lib/md5';

import {
    type FavoriteKind,
    type MusicSource,
    type Playlist,
    type SourceConfig,
    SourceError,
    type SyncResult,
} from './types';

/** Cliente da API do Jellyfin. */

type Json = Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

const VERSION = '2.0.0';
const TICKS_PER_MS = 10_000;

const deviceId = () => {
    try {
        let id = localStorage.getItem('criol_device_id');
        if (!id) {
            id = randomId(20);
            localStorage.setItem('criol_device_id', id);
        }
        return id;
    } catch {
        return 'criol';
    }
};

const authHeader = (token?: string) => {
    const device = window.criolDesktop ? 'Criol Desktop' : 'Criol Web';
    const parts = [`Client="Criol"`, `Device="${device}"`, `DeviceId="${deviceId()}"`, `Version="${VERSION}"`];
    if (token) parts.push(`Token="${token}"`);
    return `MediaBrowser ${parts.join(', ')}`;
};

async function request(url: string, token: string | undefined, init: RequestInit = {}): Promise<Response> {
    let response: Response;
    try {
        response = await fetch(url, {
            ...init,
            headers: { Authorization: authHeader(token), 'Content-Type': 'application/json', ...(init.headers || {}) },
        });
    } catch {
        throw new SourceError('Não foi possível conectar ao servidor.', 'network');
    }
    if (response.status === 401 || response.status === 403) throw new SourceError('Usuário ou senha incorretos.', 'auth');
    if (response.status === 404) throw new SourceError('Não encontrado.', 'notFound');
    if (!response.ok) throw new SourceError(`O servidor respondeu ${response.status}.`, 'server');
    return response;
}

export async function loginJellyfin(url: string, username: string, password: string) {
    const response = await request(`${url}/Users/AuthenticateByName`, undefined, {
        body: JSON.stringify({ Pw: password, Username: username }),
        method: 'POST',
    });
    const body = (await response.json()) as Json;
    if (!body.AccessToken || !body.User?.Id) throw new SourceError('Resposta inválida do servidor.', 'server');
    return { token: String(body.AccessToken), userId: String(body.User.Id) };
}

const toMs = (value: unknown) => {
    const t = Date.parse(String(value || ''));
    return Number.isNaN(t) ? 0 : t;
};

function toSong(raw: Json): Song {
    const artistItems: Json[] = raw.ArtistItems || [];
    const albumArtistItems: Json[] = raw.AlbumArtists || [];
    const artists: string[] = raw.Artists?.length ? raw.Artists : artistItems.map((a) => a.Name);
    const albumArtist = raw.AlbumArtist || albumArtistItems.map((a) => a.Name).join('; ') || '';
    const refs: Record<string, string> = {};
    for (const a of [...artistItems, ...albumArtistItems]) if (a?.Id && a?.Name) refs[normalizeText(a.Name)] = String(a.Id);
    const source: Json = raw.MediaSources?.[0] || {};
    const stream: Json = (source.MediaStreams || []).find((s: Json) => s.Type === 'Audio') || {};
    const path = String(raw.Path || source.Path || '').replace(/\\/g, '/');
    const coverId = raw.AlbumId && raw.AlbumPrimaryImageTag ? String(raw.AlbumId) : raw.ImageTags?.Primary ? String(raw.Id) : null;

    return {
        album: raw.Album || '',
        albumArtist,
        albumId: raw.AlbumId ? String(raw.AlbumId) : `noalbum-${raw.ParentId || raw.Id}`,
        artist: artists.join('; ') || albumArtist || 'Artista desconhecido',
        artistRefs: Object.keys(refs).length ? refs : undefined,
        artists: artists.length ? artists : splitArtists(albumArtist),
        bitrate: Math.round((Number(source.Bitrate || stream.BitRate) || 0) / 1000),
        coverId,
        dateAdded: toMs(raw.DateCreated),
        disc: Number(raw.ParentIndexNumber) || 0,
        durationMs: Math.round((Number(raw.RunTimeTicks) || 0) / TICKS_PER_MS),
        format: String(source.Container || path.split('.').pop() || '').toLowerCase(),
        genres: raw.Genres || [],
        id: String(raw.Id),
        path,
        sampleRate: Number(stream.SampleRate) || 0,
        size: Number(source.Size) || 0,
        title: raw.Name || '',
        track: Number(raw.IndexNumber) || 0,
        year: parseYear(raw.ProductionYear || raw.PremiereDate),
    };
}

export function createJellyfinSource(config: SourceConfig): MusicSource {
    const base = config.url!;
    const token = config.jellyfin?.token;
    const userId = config.jellyfin?.userId || '';

    const get = async (path: string, params: Record<string, number | string> = {}) => {
        const query = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]));
        const response = await request(`${base}${path}${query.size ? `?${query}` : ''}`, token);
        return (await response.json()) as Json;
    };
    const send = async (method: string, path: string, body?: unknown) => {
        await request(`${base}${path}`, token, { body: body === undefined ? undefined : JSON.stringify(body), method });
    };

    const fetchPlaylists = async () => {
        const list = await get('/Items', { IncludeItemTypes: 'Playlist', Recursive: 'true', userId });
        const playlists: Playlist[] = [];
        for (const p of list.Items || []) {
            if (p.MediaType && p.MediaType !== 'Audio') continue;
            const items = await get(`/Playlists/${p.Id}/Items`, { Fields: 'DateCreated', userId }).catch(() => ({ Items: [] }));
            playlists.push({
                created: toMs(p.DateCreated),
                id: String(p.Id),
                name: p.Name || '',
                songIds: (items.Items || []).map((i: Json) => String(i.Id)),
                updated: toMs(p.DateLastSaved || p.DateCreated),
            });
        }
        return playlists;
    };

    return {
        capabilities: { scrobble: true, serverFavorites: true, serverPlaylists: true, serverRatings: false },
        config,

        async sync(onProgress) {
            const songs: Song[] = [];
            const favSongs: string[] = [];
            const playCounts: Record<string, number> = {};
            const lastPlayed: Record<string, number> = {};
            const pageSize = 1000;
            for (let start = 0; ; start += pageSize) {
                const page = await get('/Items', {
                    EnableImageTypes: 'Primary',
                    EnableUserData: 'true',
                    Fields: 'Genres,DateCreated,MediaSources,Path,ParentId,ProductionYear,PremiereDate',
                    IncludeItemTypes: 'Audio',
                    Limit: pageSize,
                    Recursive: 'true',
                    SortBy: 'SortName',
                    StartIndex: start,
                    userId,
                });
                const items: Json[] = page.Items || [];
                for (const raw of items) {
                    const song = toSong(raw);
                    songs.push(song);
                    if (raw.UserData?.IsFavorite) favSongs.push(song.id);
                    if (raw.UserData?.PlayCount) playCounts[song.id] = Number(raw.UserData.PlayCount);
                    if (raw.UserData?.LastPlayedDate) lastPlayed[song.id] = toMs(raw.UserData.LastPlayedDate);
                }
                onProgress({ done: songs.length, label: 'songs', total: Number(page.TotalRecordCount) || 0 });
                if (items.length < pageSize) break;
            }

            onProgress({ done: 0, label: 'favorites', total: 0 });
            const favAlbums = await get('/Items', { Filters: 'IsFavorite', IncludeItemTypes: 'MusicAlbum', Recursive: 'true', userId }).catch(() => ({ Items: [] }));
            const favArtists = await get('/Artists', { Filters: 'IsFavorite', userId }).catch(() => ({ Items: [] }));

            onProgress({ done: 0, label: 'playlists', total: 0 });
            const playlists = await fetchPlaylists().catch(() => []);

            return {
                favorites: {
                    albums: (favAlbums.Items || []).map((a: Json) => String(a.Id)),
                    artists: (favArtists.Items || []).map((a: Json) => normalizeText(a.Name)),
                    songs: favSongs,
                },
                lastPlayed,
                playCounts,
                playlists,
                songs,
            } satisfies SyncResult;
        },

        async streamUrl(song) {
            const params = new URLSearchParams({
                AudioCodec: 'aac',
                Container: 'opus,webm|opus,mp3,aac,m4a|aac,m4b|aac,flac,webma,webm|webma,wav,ogg',
                DeviceId: deviceId(),
                MaxStreamingBitrate: '999999999',
                TranscodingContainer: 'mp4',
                TranscodingProtocol: 'http',
                UserId: userId,
                api_key: token || '',
            });
            return `${base}/Audio/${song.id}/universal?${params}`;
        },

        coverUrl(coverId, size) {
            return `${base}/Items/${coverId}/Images/Primary?fillHeight=${size}&fillWidth=${size}&quality=90`;
        },

        async lyrics(song) {
            try {
                const body = await get(`/Audio/${song.id}/Lyrics`);
                const lines: Json[] = body.Lyrics || [];
                if (!lines.length) return null;
                const synced = lines.some((l) => l.Start !== undefined && l.Start !== null);
                return lines
                    .map((l) => {
                        if (!synced) return l.Text ?? '';
                        const ms = Math.round((Number(l.Start) || 0) / TICKS_PER_MS);
                        const mm = String(Math.floor(ms / 60000)).padStart(2, '0');
                        const ss = ((ms % 60000) / 1000).toFixed(2).padStart(5, '0');
                        return `[${mm}:${ss}]${l.Text ?? ''}`;
                    })
                    .join('\n');
            } catch {
                return null;
            }
        },

        async setFavorite(_kind: FavoriteKind, id: string, value: boolean) {
            const method = value ? 'POST' : 'DELETE';
            try {
                await send(method, `/UserFavoriteItems/${id}?userId=${userId}`);
            } catch {
                await send(method, `/Users/${userId}/FavoriteItems/${id}`);
            }
        },

        async scrobble(song, submission) {
            if (submission) {
                await send('POST', `/Sessions/Playing/Stopped`, { ItemId: song.id, PositionTicks: song.durationMs * TICKS_PER_MS });
                await send('POST', `/UserPlayedItems/${song.id}?userId=${userId}`).catch(() => send('POST', `/Users/${userId}/PlayedItems/${song.id}`));
            } else {
                await send('POST', `/Sessions/Playing`, { CanSeek: true, ItemId: song.id, PlayMethod: 'DirectStream' });
            }
        },

        async createPlaylist(name, songIds) {
            const response = await request(`${base}/Playlists`, token, {
                body: JSON.stringify({ Ids: songIds, MediaType: 'Audio', Name: name, UserId: userId }),
                method: 'POST',
            });
            const body = (await response.json()) as Json;
            return { created: Date.now(), id: String(body.Id), name, songIds, updated: Date.now() };
        },

        async updatePlaylist(playlist, patch) {
            if (patch.name !== undefined && patch.name !== playlist.name) {
                await send('POST', `/Playlists/${playlist.id}`, { Name: patch.name });
            }
            if (patch.songIds) {
                const current = await get(`/Playlists/${playlist.id}/Items`, { userId });
                const entryIds = (current.Items || []).map((i: Json) => i.PlaylistItemId).filter(Boolean);
                if (entryIds.length) await send('DELETE', `/Playlists/${playlist.id}/Items?entryIds=${entryIds.join(',')}`);
                for (let i = 0; i < patch.songIds.length; i += 100) {
                    const ids = patch.songIds.slice(i, i + 100).join(',');
                    await send('POST', `/Playlists/${playlist.id}/Items?ids=${ids}&userId=${userId}`);
                }
            }
        },

        async deletePlaylist(playlist) {
            await send('DELETE', `/Items/${playlist.id}`);
        },
    };
}
