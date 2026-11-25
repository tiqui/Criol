import type { Song } from '../types';

/**
 * Matching of lyric files (.lrc / .txt) to songs, shared by the desktop app (lyric folders
 * on disk, main process) and the web version (folders imported in the browser). Pure functions
 * only, so both sides recognize files in exactly the same way.
 */

export const LYRIC_EXTENSIONS = new Set(['.elrc', '.lrc', '.txt']);

export type LyricsIndexEntry = {
    album?: string;
    artist?: string;
    /** Normalized file name without extension. */
    base: string;
    folder: string;
    lengthSec?: number;
    mtimeMs: number;
    /** Normalized name of the folder that contains the file. */
    parent: string;
    path: string;
    title?: string;
};

export type LyricsIndexLookup = {
    byKey: Map<string, Set<string>>;
    files: Map<string, LyricsIndexEntry>;
};

const splitPath = (value: string) => value.split(/[\\/]+/).filter(Boolean);

export const fileExt = (value: string) => {
    const name = splitPath(value).pop() || '';
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(dot).toLowerCase() : '';
};

export const fileStem = (value: string) => {
    const name = splitPath(value).pop() || '';
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(0, dot) : name;
};

export const parentName = (value: string) => {
    const parts = splitPath(value);
    return parts.length > 1 ? parts[parts.length - 2] : '';
};

export const isLyricFile = (value: string) => LYRIC_EXTENSIONS.has(fileExt(value));

export const isSyncedLyrics = (text: string) => /\[\d{1,3}:\d{2}/.test(text);

export const normalize = (value?: null | string) =>
    (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036F]/g, '')
        .toLowerCase()
        .replace(
            /\s*[([{].*?(feat|ft\.|with|remaster|live|version|edit|mix|mono|stereo|explicit|clean).*?[)\]}]/g,
            ' ',
        )
        .replace(/&/g, ' and ')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();

export const stripTrackNumber = (value: string) => value.replace(/^(\d{1,3}\s+)+/, '').trim();

/** Reads [ar:], [ti:], [al:] and [length:] from the top of an LRC file. */
export const parseLyricTags = (header: string) => {
    const tag = (name: string) => {
        const match = header.match(new RegExp(`^\\[${name}:([^\\]]*)\\]`, 'im'));
        return match ? match[1].trim() : undefined;
    };
    const length = tag('length');
    let lengthSec: number | undefined;
    if (length) {
        const [min, sec] = length.split(':').map(Number);
        lengthSec = sec !== undefined ? min * 60 + sec : min;
        if (Number.isNaN(lengthSec)) lengthSec = undefined;
    }
    return { album: tag('al'), artist: tag('ar'), lengthSec, title: tag('ti') };
};

/** Builds the index entry for a lyric file from its path and (for .lrc) its first bytes. */
export const createLyricsEntry = (
    filePath: string,
    folder: string,
    mtimeMs: number,
    header: string,
): LyricsIndexEntry => ({
    ...(fileExt(filePath) === '.txt' ? {} : parseLyricTags(header)),
    base: normalize(fileStem(filePath)),
    folder,
    mtimeMs,
    parent: normalize(parentName(filePath)),
    path: filePath,
});

/** Every lookup key a file answers to: its name, "Artist - Title" parts, and its tags. */
export const lyricKeysFor = (entry: LyricsIndexEntry) => {
    const keys = new Set<string>();
    keys.add(entry.base);
    keys.add(stripTrackNumber(entry.base));
    // "Artist - Title" / "Title - Artist" / "01 - Artist - Title"
    for (const part of fileStem(entry.path).split(/\s+[-–—_]\s+|\s*-\s*/)) {
        const normalized = stripTrackNumber(normalize(part));
        if (normalized.length > 1) keys.add(normalized);
    }
    if (entry.title) {
        keys.add(normalize(entry.title));
        if (entry.artist) keys.add(normalize(`${entry.artist} ${entry.title}`));
    }
    keys.delete('');
    return [...keys];
};

export const buildKeyIndex = (files: Iterable<LyricsIndexEntry>) => {
    const byKey = new Map<string, Set<string>>();
    for (const entry of files) addToKeyIndex(byKey, entry);
    return byKey;
};

export const addToKeyIndex = (byKey: Map<string, Set<string>>, entry: LyricsIndexEntry) => {
    for (const key of lyricKeysFor(entry)) {
        if (!byKey.has(key)) byKey.set(key, new Set());
        byKey.get(key)!.add(entry.path);
    }
};

/** "Other Artist - Title.lrc": the part that is not the title names an artist. */
const fileNameNamesAnotherArtist = (entry: LyricsIndexEntry, title: string) => {
    const parts = fileStem(entry.path)
        .split(/\s+[-–—]\s+/)
        .map((part) => stripTrackNumber(normalize(part)))
        .filter(Boolean);
    if (parts.length < 2) return false;
    return parts.some((part) => part !== title && !/^\d+$/.test(part));
};

export const scoreLyricEntry = (entry: LyricsIndexEntry, song: Song) => {
    const title = normalize(song.title);
    const artists = [...song.artists, song.artist, song.albumArtist]
        .map((name) => normalize(name))
        .filter(Boolean);
    const album = normalize(song.album);
    const haystack = `${entry.base} ${entry.parent}`;

    let score = 0;
    const tagTitle = normalize(entry.title);
    const baseNoTrack = stripTrackNumber(entry.base);

    if (tagTitle && tagTitle === title) score += 50;
    else if (
        baseNoTrack === title ||
        baseNoTrack.endsWith(` ${title}`) ||
        baseNoTrack.startsWith(`${title} `)
    )
        score += 45;
    else if (title.length > 3 && entry.base.includes(title)) score += 30;

    const tagArtist = normalize(entry.artist);
    const artistMatches = artists.some(
        (artist) =>
            (tagArtist && (tagArtist === artist || tagArtist.includes(artist))) ||
            (artist.length > 1 && haystack.includes(artist)),
    );

    if (artistMatches) {
        score += 30;
    } else if (tagArtist || fileNameNamesAnotherArtist(entry, title)) {
        // The file clearly belongs to someone else: wrong lyrics are worse than none.
        score -= 60;
    }

    if (album && (normalize(entry.album) === album || entry.parent.includes(album))) score += 10;

    if (entry.lengthSec && song.durationMs) {
        const diff = Math.abs(entry.lengthSec - song.durationMs / 1000);
        if (diff > 8) score -= 40;
        else score += 5;
    }

    if (fileExt(entry.path) !== '.txt') score += 2;

    return score;
};

/**
 * Picks the best lyric file for a song, or null. A file named like the audio file
 * ("01 - Song.flac" and "01 - Song.lrc") wins outright; otherwise title/artist keys are scored.
 */
export const pickLyricsEntry = (
    { byKey, files }: LyricsIndexLookup,
    song: Song,
): null | { entry: LyricsIndexEntry; score: number } => {
    if (files.size === 0) return null;

    if (song.path) {
        const audioBase = normalize(fileStem(song.path));
        for (const filePath of byKey.get(audioBase) || []) {
            const entry = files.get(filePath);
            if (entry && entry.base === audioBase) {
                const score = 70 + scoreLyricEntry(entry, song);
                if (score >= 70) return { entry, score };
            }
        }
    }

    const title = normalize(song.title);
    const artist = normalize(song.artists[0] || song.artist || song.albumArtist);
    const candidates = new Set<string>();
    for (const key of [
        title,
        `${artist} ${title}`,
        `${title} ${artist}`,
        stripTrackNumber(title),
    ]) {
        for (const filePath of byKey.get(key) || []) candidates.add(filePath);
    }

    let best: null | { entry: LyricsIndexEntry; score: number } = null;
    for (const filePath of candidates) {
        const entry = files.get(filePath);
        if (!entry) continue;
        const score = scoreLyricEntry(entry, song);
        if (!best || score > best.score) best = { entry, score };
    }

    const threshold = candidates.size === 1 ? 45 : 70;
    return best && best.score >= threshold ? best : null;
};
