import { createStore, get, set, type UseStore } from 'idb-keyval';

import { buildKeyIndex, createLyricsEntry, isLyricFile, pickLyricsEntry, type LyricsIndexEntry, type LyricsIndexLookup } from '@shared/lyrics/folder-match';
import { parseLyrics } from '@shared/lyrics/parse';
import type { Lyrics, Song } from '@shared/types';

import { settings, useSettings } from '@/settings/store';
import { getFolderHandle } from '@/sources/local-web';
import type { MusicSource } from '@/sources/types';

/**
 * De onde vem a letra, em ordem: arquivo ao lado da música / tag / servidor, pastas de letras
 * e LRCLIB (internet). Uma letra sincronizada sempre ganha de uma sem sincronia.
 */

let cacheDb: null | UseStore = null;
const cacheStore = () => (cacheDb ??= createStore('criol-lyrics', 'cache'));
const NOT_FOUND_TTL = 7 * 24 * 60 * 60 * 1000;

interface CachedLyrics {
    raw: null | string;
    source: string;
    at: number;
}

const memory = new Map<string, Lyrics | null>();

// ---------------------------------------------------------------------------
// Pastas de letras
// ---------------------------------------------------------------------------

let folderIndex: null | Promise<LyricsIndexLookup> = null;
let indexedFor = '';

/** Na web, cada pasta é guardada como "web:<chave>:<nome>". */
export const isWebLyricsFolder = (value: string) => value.startsWith('web:');
export const lyricsFolderLabel = (value: string) => (isWebLyricsFolder(value) ? value.split(':').slice(2).join(':') : value);

type DirHandle = FileSystemDirectoryHandle & { entries: () => AsyncIterableIterator<[string, FileSystemHandle]> };

async function indexWebFolder(value: string): Promise<LyricsIndexEntry[]> {
    const key = value.split(':')[1];
    const root = (await getFolderHandle(key)) as DirHandle | undefined;
    if (!root) return [];
    const out: LyricsIndexEntry[] = [];
    const stack: [DirHandle, string][] = [[root, root.name]];
    while (stack.length) {
        const [dir, prefix] = stack.pop()!;
        try {
            for await (const [name, entry] of dir.entries()) {
                const rel = `${prefix}/${name}`;
                if (entry.kind === 'directory') stack.push([entry as DirHandle, rel]);
                else if (isLyricFile(name)) {
                    const file = await (entry as FileSystemFileHandle).getFile();
                    const header = name.toLowerCase().endsWith('.txt') ? '' : await file.slice(0, 2048).text();
                    out.push(createLyricsEntry(`${value}|${rel}`, value, file.lastModified, header));
                }
            }
        } catch {
            /* sem permissão para a pasta nesta sessão */
        }
    }
    return out;
}

async function readWebLyricsFile(path: string) {
    const [folder, rel] = path.split('|');
    const root = await getFolderHandle(folder.split(':')[1]);
    if (!root) return null;
    const parts = rel.split('/').slice(1);
    let dir: FileSystemDirectoryHandle = root;
    for (const part of parts.slice(0, -1)) dir = await dir.getDirectoryHandle(part);
    return (await (await dir.getFileHandle(parts[parts.length - 1])).getFile()).text();
}

const loadFolderIndex = () => {
    const folders = settings().lyricsFolders;
    const signature = folders.join('\n');
    if (!folderIndex || signature !== indexedFor) {
        indexedFor = signature;
        folderIndex = (async () => {
            const entries: LyricsIndexEntry[] = [];
            const desktop = folders.filter((f) => !isWebLyricsFolder(f));
            if (desktop.length && window.criolDesktop) entries.push(...(await window.criolDesktop.indexLyricsFolders(desktop)));
            for (const web of folders.filter(isWebLyricsFolder)) entries.push(...(await indexWebFolder(web)));
            return { byKey: buildKeyIndex(entries), files: new Map(entries.map((e) => [e.path, e])) };
        })();
    }
    return folderIndex;
};

/** Recarrega o índice (depois de adicionar/remover pastas). */
export const refreshLyricsFolders = () => {
    folderIndex = null;
    memory.clear();
    return loadFolderIndex();
};

useSettings.subscribe((s, prev) => {
    if (s.lyricsFolders !== prev.lyricsFolders) {
        folderIndex = null;
        memory.clear();
    }
});

async function fromFolders(song: Song): Promise<null | string> {
    if (!settings().lyricsFolders.length) return null;
    const lookup = await loadFolderIndex();
    const best = pickLyricsEntry(lookup, song);
    if (!best) return null;
    return isWebLyricsFolder(best.entry.path) ? readWebLyricsFile(best.entry.path) : (window.criolDesktop?.readLyricsFile(best.entry.path) ?? null);
}

// ---------------------------------------------------------------------------
// LRCLIB
// ---------------------------------------------------------------------------

interface LrclibRecord {
    syncedLyrics?: null | string;
    plainLyrics?: null | string;
    duration?: number;
    instrumental?: boolean;
}

async function fromLrclib(song: Song): Promise<null | string> {
    const artist = song.artists[0] || song.artist;
    const duration = Math.round(song.durationMs / 1000);
    const pick = (r: LrclibRecord | null) => (r && !r.instrumental ? r.syncedLyrics || r.plainLyrics || null : null);
    try {
        const params = new URLSearchParams({ album_name: song.album, artist_name: artist, track_name: song.title });
        if (duration) params.set('duration', String(duration));
        const response = await fetch(`https://lrclib.net/api/get?${params}`);
        if (response.ok) {
            const found = pick((await response.json()) as LrclibRecord);
            if (found) return found;
        }
        const search = await fetch(`https://lrclib.net/api/search?${new URLSearchParams({ artist_name: artist, track_name: song.title })}`);
        if (!search.ok) return null;
        const results = (await search.json()) as LrclibRecord[];
        const close = results
            .filter((r) => !duration || !r.duration || Math.abs(r.duration - duration) <= 3)
            .sort((a, b) => Number(Boolean(b.syncedLyrics)) - Number(Boolean(a.syncedLyrics)));
        for (const r of close) {
            const found = pick(r);
            if (found && parseLyrics(found, 'LRCLIB')) return found;
        }
    } catch {
        /* sem internet */
    }
    return null;
}

// ---------------------------------------------------------------------------

const cacheKey = (sourceId: string, song: Song) => `${sourceId}:${song.id}`;

/** Letra da música (ou null). */
export async function getLyrics(source: MusicSource, song: Song, force = false): Promise<Lyrics | null> {
    const key = cacheKey(source.config.id, song);
    if (!force && memory.has(key)) return memory.get(key)!;

    if (!force) {
        const cached = await get<CachedLyrics>(key, cacheStore()).catch(() => undefined);
        if (cached && (cached.raw || Date.now() - cached.at < NOT_FOUND_TTL)) {
            const parsed = parseLyrics(cached.raw, cached.source);
            // Letra local (arquivo/pasta) pode ter mudado: só confia no cache de servidor/internet.
            if (cached.source === 'LRCLIB' || cached.source === 'server' || !cached.raw) {
                memory.set(key, parsed);
                if (parsed || !cached.raw) return parsed;
            }
        }
    }

    let fallback: Lyrics | null = null;
    const attempts: [string, () => Promise<null | string>][] = [
        [source.config.type === 'local' ? 'file' : 'server', () => source.lyrics(song)],
        ['folder', () => fromFolders(song)],
    ];
    if (settings().lyricsOnline) attempts.push(['LRCLIB', () => fromLrclib(song)]);

    for (const [label, attempt] of attempts) {
        let raw: null | string = null;
        try {
            raw = await attempt();
        } catch {
            raw = null;
        }
        const parsed = parseLyrics(raw, label);
        if (!parsed) continue;
        if (parsed.synced) {
            memory.set(key, parsed);
            void set(key, { at: Date.now(), raw, source: label } satisfies CachedLyrics, cacheStore());
            return parsed;
        }
        if (!fallback) {
            fallback = parsed;
            void set(key, { at: Date.now(), raw, source: label } satisfies CachedLyrics, cacheStore());
        }
    }
    memory.set(key, fallback);
    if (!fallback) void set(key, { at: Date.now(), raw: null, source: '' } satisfies CachedLyrics, cacheStore());
    return fallback;
}
