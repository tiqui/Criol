import { app, nativeImage } from 'electron';
import { createReadStream, promises as fs } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { Readable } from 'node:stream';
import { parseFile } from 'music-metadata';

import { tagsFromPath } from '@shared/path-tags';
import { normalizeText, parseYear, splitArtists, splitGenres, stableId } from '@shared/text';
import type { LocalScanResult, ScanProgress, Song } from '@shared/types';

/**
 * Biblioteca de uma pasta do computador: lê as tags dos arquivos, guarda um índice em disco
 * (para os próximos escaneamentos só lerem o que mudou) e as capas dos álbuns em JPEG.
 */

export const AUDIO_EXTENSIONS = new Set([
    'aac', 'aif', 'aiff', 'flac', 'm4a', 'mp3', 'mp4', 'oga', 'ogg', 'opus', 'wav', 'weba', 'webm',
]);
const COVER_NAMES = ['cover', 'folder', 'front', 'album', 'albumart', 'artwork', 'capa'];
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp'];
const INDEX_VERSION = 3;

interface IndexedFile {
    mtimeMs: number;
    size: number;
    song: Song;
}

interface LibraryIndex {
    version: number;
    root: string;
    files: Record<string, IndexedFile>;
    art: string[];
    scannedAt: number;
}

const baseDir = (sourceId: string) => join(app.getPath('userData'), 'library', sourceId.replace(/[^\w-]/g, '_'));
const indexFile = (sourceId: string) => join(baseDir(sourceId), 'index.json');
export const artDir = (sourceId: string) => join(baseDir(sourceId), 'art');

/** caminho absoluto de cada música carregada, por fonte. */
const songPaths = new Map<string, Map<string, string>>();

const ext = (name: string) => {
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
};
const stem = (name: string) => {
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(0, dot) : name;
};

async function readIndex(sourceId: string): Promise<LibraryIndex | null> {
    try {
        const parsed = JSON.parse(await fs.readFile(indexFile(sourceId), 'utf8')) as LibraryIndex;
        return parsed.version === INDEX_VERSION ? parsed : null;
    } catch {
        return null;
    }
}

async function writeIndex(sourceId: string, index: LibraryIndex) {
    await fs.mkdir(baseDir(sourceId), { recursive: true });
    const target = indexFile(sourceId);
    await fs.writeFile(`${target}.tmp`, JSON.stringify(index));
    await fs.rename(`${target}.tmp`, target);
}

function remember(sourceId: string, index: LibraryIndex) {
    const map = new Map<string, string>();
    for (const [rel, file] of Object.entries(index.files)) map.set(file.song.id, join(index.root, rel));
    songPaths.set(sourceId, map);
}

function toResult(index: LibraryIndex): LocalScanResult {
    return { art: index.art, scannedAt: index.scannedAt, songs: Object.values(index.files).map((f) => f.song) };
}

/** Índice salvo (sem escanear). */
export async function loadLocalLibrary(sourceId: string, root: string): Promise<LocalScanResult | null> {
    const index = await readIndex(sourceId);
    if (!index || index.root !== root) return null;
    remember(sourceId, index);
    return toResult(index);
}

interface FoundFile {
    abs: string;
    rel: string;
    mtimeMs: number;
    size: number;
    birthtimeMs: number;
}

async function walk(root: string, onDir: (count: number) => void) {
    const files: FoundFile[] = [];
    const lyricFiles = new Set<string>();
    const images = new Map<string, string[]>();
    const stack = [root];
    while (stack.length) {
        const dir = stack.pop()!;
        let entries: import('node:fs').Dirent[];
        try {
            entries = await fs.readdir(dir, { withFileTypes: true });
        } catch {
            continue;
        }
        for (const entry of entries) {
            if (entry.name.startsWith('.')) continue;
            const abs = join(dir, entry.name);
            if (entry.isDirectory()) {
                stack.push(abs);
                continue;
            }
            if (!entry.isFile()) continue;
            const e = ext(entry.name);
            if (AUDIO_EXTENSIONS.has(e)) {
                try {
                    const st = await fs.stat(abs);
                    files.push({
                        abs,
                        birthtimeMs: st.birthtimeMs || st.mtimeMs,
                        mtimeMs: st.mtimeMs,
                        rel: relative(root, abs).split(sep).join('/'),
                        size: st.size,
                    });
                } catch {
                    /* arquivo sumiu no meio do escaneamento */
                }
            } else if (e === 'lrc' || e === 'txt') {
                lyricFiles.add(abs.toLowerCase());
            } else if (IMAGE_EXTENSIONS.includes(e) && COVER_NAMES.includes(stem(entry.name).toLowerCase())) {
                const list = images.get(dir) || [];
                list.push(abs);
                images.set(dir, list);
            }
        }
        onDir(files.length);
    }
    return { files, images, lyricFiles };
}

function firstString(value: unknown): string {
    if (Array.isArray(value)) return value.filter(Boolean).join('; ');
    return typeof value === 'string' ? value : '';
}

async function readSong(file: FoundFile, previous: IndexedFile | undefined, lyricFiles: Set<string>): Promise<Song> {
    const name = file.rel.split('/').pop() || file.rel;
    const folder = file.rel.includes('/') ? file.rel.slice(0, file.rel.lastIndexOf('/')) : '';
    const folderName = folder.split('/').pop() || '';
    const sidecarBase = file.abs.slice(0, file.abs.length - ext(name).length - 1);
    const lyricsPath = ['.lrc', '.txt']
        .map((e) => sidecarBase + e)
        .find((candidate) => lyricFiles.has(candidate.toLowerCase()));

    let common: Awaited<ReturnType<typeof parseFile>>['common'] | null = null;
    let format: Awaited<ReturnType<typeof parseFile>>['format'] | null = null;
    try {
        const meta = await parseFile(file.abs, { duration: false, skipCovers: true });
        common = meta.common;
        format = meta.format;
    } catch {
        /* arquivo sem tags legíveis: usa o nome */
    }

    const guess = tagsFromPath(file.rel);
    const hasTags = Boolean(common?.title || common?.artist || common?.album);
    const albumArtist = firstString(common?.albumartist) || (hasTags ? '' : guess.artist);
    const artist = firstString(common?.artists?.length ? common.artists : common?.artist) || albumArtist || 'Artista desconhecido';
    const album = common?.album || folderName || 'Sem álbum';
    const albumKey = `${normalizeText(album)}|${normalizeText(albumArtist) || folder.toLowerCase()}`;
    const albumId = stableId(`album:${albumKey}`);
    const lyricsTags = (common?.lyrics || []) as unknown[];

    return {
        album,
        albumArtist,
        albumId,
        artist,
        artists: splitArtists(artist),
        bitrate: format?.bitrate ? Math.round(format.bitrate / 1000) : 0,
        composer: firstString(common?.composer) || undefined,
        coverId: null,
        dateAdded: previous?.song.dateAdded || Math.min(file.birthtimeMs, file.mtimeMs) || Date.now(),
        disc: common?.disk?.no || (hasTags ? 0 : guess.disc),
        durationMs: Math.round((format?.duration || 0) * 1000),
        format: ext(name),
        genres: splitGenres(common?.genre || []),
        hasEmbeddedLyrics: lyricsTags.length > 0,
        id: stableId(`song:${file.rel}`),
        lyricsPath: lyricsPath || null,
        path: file.rel,
        sampleRate: format?.sampleRate || 0,
        size: file.size,
        title: common?.title || (hasTags ? stem(name) : guess.title),
        track: common?.track?.no || (hasTags ? 0 : guess.track),
        year: parseYear(common?.year || common?.date || common?.originaldate),
    };
}

async function saveArt(target: string, data: Buffer | Uint8Array) {
    const image = nativeImage.createFromBuffer(Buffer.from(data));
    if (image.isEmpty()) return false;
    const { width, height } = image.getSize();
    const scaled = Math.max(width, height) > 800 ? image.resize(width >= height ? { width: 800 } : { height: 800 }) : image;
    await fs.writeFile(target, scaled.toJPEG(88));
    return true;
}

async function extractArt(
    sourceId: string,
    root: string,
    files: Record<string, IndexedFile>,
    images: Map<string, string[]>,
    previousArt: Set<string>,
    onProgress: (p: ScanProgress) => void,
) {
    const dir = artDir(sourceId);
    await fs.mkdir(dir, { recursive: true });
    const byAlbum = new Map<string, string[]>();
    for (const [rel, file] of Object.entries(files)) {
        const list = byAlbum.get(file.song.albumId) || [];
        list.push(rel);
        byAlbum.set(file.song.albumId, list);
    }

    const art = new Set<string>();
    const missing = [...byAlbum.keys()].filter((id) => !previousArt.has(id));
    for (const id of byAlbum.keys()) if (previousArt.has(id)) art.add(id);

    let done = 0;
    for (const albumId of missing) {
        const rels = byAlbum.get(albumId)!;
        const target = join(dir, `${albumId}.jpg`);
        let ok = false;
        // 1) imagem "cover.jpg"/"folder.jpg" na pasta do álbum
        for (const rel of rels) {
            const folderAbs = join(root, rel.includes('/') ? rel.slice(0, rel.lastIndexOf('/')) : '');
            const candidates = images.get(folderAbs);
            if (candidates?.length) {
                try {
                    ok = await saveArt(target, await fs.readFile(candidates[0]));
                } catch {
                    ok = false;
                }
                if (ok) break;
            }
        }
        // 2) capa embutida nas tags
        for (const rel of ok ? [] : rels.slice(0, 3)) {
            try {
                const meta = await parseFile(join(root, rel), { duration: false, skipCovers: false });
                const picture = meta.common.picture?.find((p) => /front|cover/i.test(p.type || '')) || meta.common.picture?.[0];
                if (picture) ok = await saveArt(target, picture.data);
            } catch {
                ok = false;
            }
            if (ok) break;
        }
        if (ok) art.add(albumId);
        done += 1;
        if (done % 10 === 0) onProgress({ done, phase: 'art', total: missing.length });
    }

    // Capas de álbuns que saíram da biblioteca.
    try {
        for (const name of await fs.readdir(dir)) {
            const id = stem(name);
            if (!byAlbum.has(id)) await fs.rm(join(dir, name), { force: true });
        }
    } catch {
        /* ignore */
    }
    return art;
}

/**
 * Faixas da mesma pasta com o mesmo álbum ficam juntas mesmo quando só algumas têm "artista do
 * álbum" na tag: as que não têm herdam o mais comum entre as outras.
 */
function mergeAlbums(files: Record<string, IndexedFile>, changedAlbums: Set<string>) {
    const groups = new Map<string, IndexedFile[]>();
    for (const [rel, file] of Object.entries(files)) {
        const folder = rel.includes('/') ? rel.slice(0, rel.lastIndexOf('/')) : '';
        const key = `${folder.toLowerCase()}|${normalizeText(file.song.album)}`;
        const group = groups.get(key) || [];
        group.push(file);
        groups.set(key, group);
    }
    for (const group of groups.values()) {
        const counts = new Map<string, number>();
        for (const file of group) if (file.song.albumArtist) counts.set(file.song.albumArtist, (counts.get(file.song.albumArtist) || 0) + 1);
        const missing = group.filter((file) => !file.song.albumArtist);
        if (!counts.size || !missing.length) continue;
        const albumArtist = [...counts].sort((a, b) => b[1] - a[1])[0][0];
        for (const file of missing) {
            const albumId = stableId(`album:${normalizeText(file.song.album)}|${normalizeText(albumArtist)}`);
            changedAlbums.add(albumId);
            file.song = { ...file.song, albumArtist, albumId };
        }
    }
}

/** Escaneia a pasta (só lê de novo os arquivos que mudaram desde o último escaneamento). */
export async function scanLocalLibrary(
    sourceId: string,
    root: string,
    onProgress: (p: ScanProgress) => void,
): Promise<LocalScanResult> {
    const previous = await readIndex(sourceId);
    const prevFiles = previous && previous.root === root ? previous.files : {};
    const prevArt = new Set(previous && previous.root === root ? previous.art : []);

    onProgress({ done: 0, phase: 'files', total: 0 });
    const { files, images, lyricFiles } = await walk(root, (count) => onProgress({ done: count, phase: 'files', total: 0 }));

    const next: Record<string, IndexedFile> = {};
    let done = 0;
    const queue = [...files];
    const changedAlbums = new Set<string>();
    const worker = async () => {
        while (queue.length) {
            const file = queue.pop()!;
            const prev = prevFiles[file.rel];
            if (prev && prev.mtimeMs === file.mtimeMs && prev.size === file.size) {
                next[file.rel] = prev;
            } else {
                const song = await readSong(file, prev, lyricFiles);
                next[file.rel] = { mtimeMs: file.mtimeMs, size: file.size, song };
                changedAlbums.add(song.albumId);
            }
            done += 1;
            if (done % 25 === 0 || done === files.length) onProgress({ done, phase: 'tags', total: files.length });
        }
    };
    await Promise.all(Array.from({ length: 6 }, worker));
    mergeAlbums(next, changedAlbums);

    // Um arquivo novo pode ter trazido a capa que faltava: procura de novo para esses álbuns.
    for (const id of changedAlbums) prevArt.delete(id);
    const art = await extractArt(sourceId, root, next, images, prevArt, onProgress);
    for (const file of Object.values(next)) file.song.coverId = art.has(file.song.albumId) ? file.song.albumId : null;

    const index: LibraryIndex = { art: [...art], files: next, root, scannedAt: Date.now(), version: INDEX_VERSION };
    await writeIndex(sourceId, index);
    remember(sourceId, index);
    return toResult(index);
}

export async function removeLocalLibrary(sourceId: string) {
    songPaths.delete(sourceId);
    await fs.rm(baseDir(sourceId), { force: true, recursive: true });
}

export const localSongPath = (sourceId: string, songId: string) => songPaths.get(sourceId)?.get(songId) || null;

/** Letra embutida nas tags do arquivo. */
export async function readEmbeddedLyrics(sourceId: string, songId: string): Promise<null | string> {
    const path = localSongPath(sourceId, songId);
    if (!path) return null;
    try {
        const meta = await parseFile(path, { duration: false, skipCovers: true });
        for (const tag of (meta.common.lyrics || []) as unknown[]) {
            if (typeof tag === 'string' && tag.trim()) return tag;
            const t = tag as { syncText?: { text: string; timestamp?: number }[]; text?: string };
            if (t.syncText?.length) {
                return t.syncText
                    .map((line) => {
                        const ms = line.timestamp || 0;
                        const mm = String(Math.floor(ms / 60000)).padStart(2, '0');
                        const ss = ((ms % 60000) / 1000).toFixed(2).padStart(5, '0');
                        return `[${mm}:${ss}]${line.text}`;
                    })
                    .join('\n');
            }
            if (t.text?.trim()) return t.text;
        }
    } catch {
        /* ignore */
    }
    return null;
}

const MIME: Record<string, string> = {
    aac: 'audio/aac', aif: 'audio/aiff', aiff: 'audio/aiff', flac: 'audio/flac', jpg: 'image/jpeg',
    m4a: 'audio/mp4', mp3: 'audio/mpeg', mp4: 'audio/mp4', oga: 'audio/ogg', ogg: 'audio/ogg',
    opus: 'audio/ogg', wav: 'audio/wav', weba: 'audio/webm', webm: 'audio/webm',
};

/** Resposta HTTP de um arquivo, com suporte a Range (necessário para avançar na música). */
export async function fileResponse(path: string, range: null | string): Promise<Response> {
    let size: number;
    try {
        size = (await fs.stat(path)).size;
    } catch {
        return new Response('Not found', { status: 404 });
    }
    const type = MIME[ext(path)] || 'application/octet-stream';
    const match = range?.match(/bytes=(\d*)-(\d*)/);
    if (match) {
        const start = match[1] ? Number(match[1]) : Math.max(0, size - Number(match[2]));
        const end = match[1] && match[2] ? Math.min(Number(match[2]), size - 1) : size - 1;
        if (start >= size) {
            return new Response(null, { headers: { 'Content-Range': `bytes */${size}` }, status: 416 });
        }
        const stream = Readable.toWeb(createReadStream(path, { end, start })) as ReadableStream;
        return new Response(stream, {
            headers: {
                'Accept-Ranges': 'bytes',
                'Content-Length': String(end - start + 1),
                'Content-Range': `bytes ${start}-${end}/${size}`,
                'Content-Type': type,
            },
            status: 206,
        });
    }
    const stream = Readable.toWeb(createReadStream(path)) as ReadableStream;
    return new Response(stream, {
        headers: { 'Accept-Ranges': 'bytes', 'Content-Length': String(size), 'Content-Type': type },
        status: 200,
    });
}
