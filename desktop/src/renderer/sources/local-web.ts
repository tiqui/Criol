import { createStore, del, get, set, type UseStore } from 'idb-keyval';

import { tagsFromPath } from '@shared/path-tags';
import { normalizeText, parseYear, splitArtists, splitGenres, stableId } from '@shared/text';
import type { Song } from '@shared/types';

import type { MusicSource, SourceConfig, SyncProgress } from './types';

/**
 * Pasta local na versão web (Chrome/Edge): o navegador dá acesso à pasta escolhida pela
 * File System Access API. As tags são lidas aqui mesmo e as capas ficam no IndexedDB.
 */

/** A leitura de tags só é carregada quando uma pasta do navegador é usada. */
const loadParser = () => import('music-metadata').then((m) => m.parseBlob);

const AUDIO = new Set(['aac', 'flac', 'm4a', 'mp3', 'mp4', 'oga', 'ogg', 'opus', 'wav', 'weba', 'webm']);
const COVER_NAMES = ['cover', 'folder', 'front', 'album', 'albumart', 'artwork', 'capa'];

let handlesDb: null | UseStore = null;
let artDb: null | UseStore = null;
let indexDb: null | UseStore = null;
const handles = () => (handlesDb ??= createStore('criol-folders', 'handles'));
const arts = () => (artDb ??= createStore('criol-local-art', 'art'));
const indexes = () => (indexDb ??= createStore('criol-local-index', 'index'));

type DirHandle = FileSystemDirectoryHandle & {
    entries: () => AsyncIterableIterator<[string, FileSystemHandle]>;
    queryPermission: (opts: { mode: 'read' }) => Promise<PermissionState>;
    requestPermission: (opts: { mode: 'read' }) => Promise<PermissionState>;
};

export const supportsFolderAccess = () => typeof window !== 'undefined' && 'showDirectoryPicker' in window;

/** Abre o seletor de pasta do navegador e guarda o acesso. */
export async function pickWebFolder(key: string) {
    const picker = (window as unknown as { showDirectoryPicker: (o: object) => Promise<DirHandle> }).showDirectoryPicker;
    const handle = await picker({ id: 'criol-music', mode: 'read' });
    await set(key, handle, handles());
    return handle.name;
}

export const getFolderHandle = (key: string) => get<DirHandle>(key, handles());
export const forgetFolderHandle = (key: string) => del(key, handles());

const ext = (name: string) => (name.includes('.') ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '');
const stem = (name: string) => (name.includes('.') ? name.slice(0, name.lastIndexOf('.')) : name);

interface Found {
    rel: string;
    handle: FileSystemFileHandle;
}

async function walk(root: DirHandle, onCount: (n: number) => void) {
    const audio: Found[] = [];
    const lyrics = new Set<string>();
    const covers = new Map<string, FileSystemFileHandle>();
    const stack: [DirHandle, string][] = [[root, '']];
    while (stack.length) {
        const [dir, prefix] = stack.pop()!;
        for await (const [name, entry] of dir.entries()) {
            if (name.startsWith('.')) continue;
            const rel = prefix ? `${prefix}/${name}` : name;
            if (entry.kind === 'directory') stack.push([entry as DirHandle, rel]);
            else {
                const e = ext(name);
                if (AUDIO.has(e)) audio.push({ handle: entry as FileSystemFileHandle, rel });
                else if (e === 'lrc' || e === 'txt') lyrics.add(rel.toLowerCase());
                else if (['jpg', 'jpeg', 'png', 'webp'].includes(e) && COVER_NAMES.includes(stem(name).toLowerCase()) && !covers.has(prefix)) {
                    covers.set(prefix, entry as FileSystemFileHandle);
                }
            }
        }
        onCount(audio.length);
    }
    return { audio, covers, lyrics };
}

/** Reduz a imagem para no máximo 512 px e devolve JPEG. */
async function shrink(blob: Blob): Promise<Blob | null> {
    try {
        const bitmap = await createImageBitmap(blob);
        const scale = Math.min(1, 512 / Math.max(bitmap.width, bitmap.height));
        const canvas = new OffscreenCanvas(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale));
        canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        return await canvas.convertToBlob({ quality: 0.86, type: 'image/jpeg' });
    } catch {
        return null;
    }
}

interface IndexEntry {
    lastModified: number;
    size: number;
    song: Song;
}

async function fileAt(root: DirHandle, rel: string): Promise<File | null> {
    try {
        const parts = rel.split('/');
        let dir: FileSystemDirectoryHandle = root;
        for (const part of parts.slice(0, -1)) dir = await dir.getDirectoryHandle(part);
        return await (await dir.getFileHandle(parts[parts.length - 1])).getFile();
    } catch {
        return null;
    }
}

export function createWebLocalSource(config: SourceConfig): MusicSource {
    const key = config.id;
    const artKey = (albumId: string) => `${config.id}:${albumId}`;
    // Mantém as últimas URLs vivas: a música atual e a próxima (pré-carregada) usam ao mesmo tempo.
    const liveUrls: string[] = [];

    const rootHandle = async () => {
        const handle = await getFolderHandle(key);
        if (!handle) throw new Error('Pasta não encontrada. Adicione a pasta de novo em Configurações.');
        return handle;
    };

    return {
        capabilities: { scrobble: false, serverFavorites: false, serverPlaylists: false, serverRatings: false },
        config,

        async needsPermission() {
            const handle = await getFolderHandle(key);
            if (!handle) return false;
            return (await handle.queryPermission({ mode: 'read' })) !== 'granted';
        },

        async requestPermission() {
            const handle = await getFolderHandle(key);
            if (!handle) return false;
            return (await handle.requestPermission({ mode: 'read' })) === 'granted';
        },

        async loadCached() {
            const saved = await get<Record<string, IndexEntry>>(key, indexes());
            return saved ? { songs: Object.values(saved).map((e) => e.song) } : null;
        },

        async sync(onProgress: (p: SyncProgress) => void) {
            const root = await rootHandle();
            const previous = (await get<Record<string, IndexEntry>>(key, indexes())) || {};
            const parseBlob = await loadParser();
            const { audio, covers, lyrics } = await walk(root, (n) => onProgress({ done: n, label: 'files', total: 0 }));

            const next: Record<string, IndexEntry> = {};
            let done = 0;
            for (const found of audio) {
                const file = await found.handle.getFile();
                const prev = previous[found.rel];
                if (prev && prev.lastModified === file.lastModified && prev.size === file.size) {
                    next[found.rel] = prev;
                } else {
                    const folder = found.rel.includes('/') ? found.rel.slice(0, found.rel.lastIndexOf('/')) : '';
                    let common: Awaited<ReturnType<typeof parseBlob>>['common'] | null = null;
                    let format: Awaited<ReturnType<typeof parseBlob>>['format'] | null = null;
                    try {
                        const meta = await parseBlob(file, { duration: false, skipCovers: true });
                        common = meta.common;
                        format = meta.format;
                    } catch {
                        /* sem tags */
                    }
                    const guess = tagsFromPath(found.rel);
                    const hasTags = Boolean(common?.title || common?.artist || common?.album);
                    const albumArtist = (common?.albumartist || '').trim() || (hasTags ? '' : guess.artist);
                    const artist = (common?.artists?.length ? common.artists.join('; ') : common?.artist) || albumArtist || 'Artista desconhecido';
                    const album = common?.album || folder.split('/').pop() || 'Sem álbum';
                    const albumId = stableId(`album:${normalizeText(album)}|${normalizeText(albumArtist) || folder.toLowerCase()}`);
                    const sidecar = ['lrc', 'txt'].map((e) => `${stem(found.rel)}.${e}`).find((p) => lyrics.has(p.toLowerCase()));
                    next[found.rel] = {
                        lastModified: file.lastModified,
                        size: file.size,
                        song: {
                            album,
                            albumArtist,
                            albumId,
                            artist,
                            artists: splitArtists(artist),
                            bitrate: format?.bitrate ? Math.round(format.bitrate / 1000) : 0,
                            coverId: null,
                            dateAdded: prev?.song.dateAdded || file.lastModified || Date.now(),
                            disc: common?.disk?.no || (hasTags ? 0 : guess.disc),
                            durationMs: Math.round((format?.duration || 0) * 1000),
                            format: ext(found.rel),
                            genres: splitGenres(common?.genre || []),
                            hasEmbeddedLyrics: Boolean(common?.lyrics?.length),
                            id: stableId(`song:${found.rel}`),
                            lyricsPath: sidecar || null,
                            path: found.rel,
                            sampleRate: format?.sampleRate || 0,
                            size: file.size,
                            title: common?.title || (hasTags ? stem(found.rel.split('/').pop() || found.rel) : guess.title),
                            track: common?.track?.no || (hasTags ? 0 : guess.track),
                            year: parseYear(common?.year || common?.date),
                        },
                    };
                }
                done += 1;
                if (done % 20 === 0) onProgress({ done, label: 'tags', total: audio.length });
            }

            // Capas: imagem da pasta ou capa embutida do primeiro arquivo do álbum.
            const byAlbum = new Map<string, string[]>();
            for (const [rel, entry] of Object.entries(next)) byAlbum.set(entry.song.albumId, [...(byAlbum.get(entry.song.albumId) || []), rel]);
            let artDone = 0;
            for (const [albumId, rels] of byAlbum) {
                let has = Boolean(await get(artKey(albumId), arts()));
                if (!has) {
                    const folder = rels[0].includes('/') ? rels[0].slice(0, rels[0].lastIndexOf('/')) : '';
                    let blob: Blob | null = null;
                    const coverHandle = covers.get(folder);
                    if (coverHandle) blob = await shrink(await coverHandle.getFile());
                    if (!blob) {
                        const file = await fileAt(root, rels[0]);
                        if (file) {
                            try {
                                const meta = await parseBlob(file, { duration: false, skipCovers: false });
                                const picture = meta.common.picture?.[0];
                                if (picture) blob = await shrink(new Blob([new Uint8Array(picture.data)], { type: picture.format }));
                            } catch {
                                /* sem capa */
                            }
                        }
                    }
                    if (blob) {
                        await set(artKey(albumId), blob, arts());
                        has = true;
                    }
                }
                for (const rel of rels) next[rel].song.coverId = has ? albumId : null;
                artDone += 1;
                if (artDone % 10 === 0) onProgress({ done: artDone, label: 'art', total: byAlbum.size });
            }

            await set(key, next, indexes());
            return { songs: Object.values(next).map((e) => e.song) };
        },

        async streamUrl(song) {
            const file = await fileAt(await rootHandle(), song.path);
            if (!file) throw new Error('Arquivo não encontrado.');
            const url = URL.createObjectURL(file);
            liveUrls.push(url);
            while (liveUrls.length > 4) URL.revokeObjectURL(liveUrls.shift()!);
            return url;
        },

        coverUrl(coverId) {
            return `idb-art:${artKey(coverId)}`;
        },

        async lyrics(song) {
            const root = await rootHandle();
            if (song.lyricsPath) {
                const file = await fileAt(root, song.lyricsPath);
                const text = file ? await file.text() : null;
                if (text?.trim()) return text;
            }
            if (song.hasEmbeddedLyrics) {
                const file = await fileAt(root, song.path);
                if (!file) return null;
                const parseBlob = await loadParser();
                const meta = await parseBlob(file, { duration: false, skipCovers: true });
                for (const tag of (meta.common.lyrics || []) as unknown[]) {
                    if (typeof tag === 'string') return tag;
                    const t = tag as { syncText?: { text: string; timestamp?: number }[]; text?: string };
                    if (t.syncText?.length) {
                        return t.syncText
                            .map((l) => `[${String(Math.floor((l.timestamp || 0) / 60000)).padStart(2, '0')}:${(((l.timestamp || 0) % 60000) / 1000).toFixed(2).padStart(5, '0')}]${l.text}`)
                            .join('\n');
                    }
                    if (t.text) return t.text;
                }
            }
            return null;
        },

        async dispose() {
            await forgetFolderHandle(key);
            await del(key, indexes());
        },
    };
}

/** Capa guardada no IndexedDB ("idb-art:<fonte>:<álbum>") como object URL, com cache. */
const artUrls = new Map<string, Promise<null | string>>();
export const resolveIdbArt = (src: string) => {
    const k = src.slice('idb-art:'.length);
    let promise = artUrls.get(k);
    if (!promise) {
        promise = get<Blob>(k, arts()).then((blob) => (blob ? URL.createObjectURL(blob) : null)).catch(() => null);
        artUrls.set(k, promise);
    }
    return promise;
};
