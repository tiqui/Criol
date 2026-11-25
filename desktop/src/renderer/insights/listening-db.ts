import { createStore, del, entries as idbEntries, update, type UseStore } from 'idb-keyval';
import { create } from 'zustand';

import type { Song } from '@shared/types';

import { randomId } from '@/lib/md5';

/**
 * Histórico de escuta, separado por mês ("AAAA-MM") no IndexedDB.
 *
 * Mesmo formato do Crify 1.x (banco "crify-listening", migrado para "criol-listening"), então o histórico antigo continua
 * valendo. Guarda uma cópia dos dados da música para o perfil e os resumos fazerem sentido
 * mesmo se o arquivo sair da biblioteca.
 */

export interface ListenEntry {
    id: string;
    songId: string;
    /** Fonte (servidor ou pasta) da música. */
    serverId: string;
    title: string;
    artist: string;
    artistId: null | string;
    artists: { id: string; name: string }[];
    album: string;
    albumId: string;
    genres: string[];
    /** Capa (coverId da fonte). */
    imageId: null | string;
    durationMs: number;
    listenedMs: number;
    playedAt: number;
    /** Longa o bastante para contar como reprodução (30 s ou metade da faixa). */
    counted: boolean;
}

/** Escutas mais curtas que isso são puladas e nem entram no histórico. */
export const MIN_STORED_MS = 10_000;
const PLAY_THRESHOLD_MS = 30_000;

export const isCountedListen = (durationMs: number, listenedMs: number) =>
    listenedMs >= (durationMs > 0 ? Math.min(PLAY_THRESHOLD_MS, durationMs / 2) : PLAY_THRESHOLD_MS);

export const entryFromSong = (song: Song, sourceId: string, listenedMs: number, playedAt: number, id = randomId(21)): ListenEntry => ({
    album: song.album,
    albumId: song.albumId,
    artist: song.artists[0] || song.albumArtist || song.artist,
    artistId: null,
    artists: song.artists.map((name) => ({ id: name.toLowerCase(), name })),
    counted: isCountedListen(song.durationMs, listenedMs),
    durationMs: song.durationMs,
    genres: song.genres,
    id,
    imageId: song.coverId,
    listenedMs,
    playedAt,
    serverId: sourceId,
    songId: song.id,
    title: song.title,
});

let db: null | UseStore = null;
const store = () => (db ??= createStore('criol-listening', 'listens'));

export const monthKey = (date: Date | number) => {
    const d = typeof date === 'number' ? new Date(date) : date;
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};

interface ListeningState {
    entries: ListenEntry[];
    loaded: boolean;
    version: number;
}

export const useListening = create<ListeningState>()(() => ({ entries: [], loaded: false, version: 0 }));

let loadPromise: null | Promise<void> = null;

export const loadListeningHistory = () => {
    loadPromise ??= (async () => {
        try {
            const buckets = await idbEntries<string, ListenEntry[]>(store());
            const all = buckets.flatMap(([, items]) => items || []).sort((a, b) => a.playedAt - b.playedAt);
            useListening.setState((s) => ({ entries: all, loaded: true, version: s.version + 1 }));
        } catch {
            useListening.setState({ loaded: true });
        }
    })();
    return loadPromise;
};

export const addListenEntry = async (entry: ListenEntry) => {
    useListening.setState((s) => ({ entries: [...s.entries, entry], version: s.version + 1 }));
    try {
        await update<ListenEntry[]>(monthKey(entry.playedAt), (current) => [...(current || []), entry], store());
    } catch {
        /* armazenamento cheio ou bloqueado */
    }
};

export const clearListeningHistory = async () => {
    const buckets = await idbEntries<string, ListenEntry[]>(store());
    await Promise.all(buckets.map(([key]) => del(key, store())));
    useListening.setState((s) => ({ entries: [], version: s.version + 1 }));
};

export const exportListeningHistory = () => JSON.stringify(useListening.getState().entries);

/**
 * Junta um histórico exportado ao atual (do Criol no Android, do Criol desktop/web ou do Crify).
 * Retorna quantos registros eram novos.
 */
export const importListeningHistory = async (raw: string, fallbackSourceId: string) => {
    const parsed = JSON.parse(raw) as Partial<ListenEntry & { artists: unknown }>[];
    if (!Array.isArray(parsed)) throw new Error('Arquivo de histórico inválido.');
    const existing = new Set(useListening.getState().entries.map((e) => e.id));
    const fresh: ListenEntry[] = [];
    for (const item of parsed) {
        if (!item || !item.id || !item.songId || !item.playedAt || existing.has(item.id)) continue;
        // O Criol guarda "artists" como lista de nomes.
        const artists = Array.isArray(item.artists)
            ? (item.artists as unknown[]).map((a) => (typeof a === 'string' ? { id: a.toLowerCase(), name: a } : (a as { id: string; name: string })))
            : [];
        fresh.push({
            album: item.album || '',
            albumId: item.albumId || '',
            artist: item.artist || artists[0]?.name || '',
            artistId: item.artistId || null,
            artists,
            counted: Boolean(item.counted),
            durationMs: item.durationMs || 0,
            genres: item.genres || [],
            id: item.id,
            imageId: item.imageId || null,
            listenedMs: item.listenedMs || 0,
            playedAt: item.playedAt,
            serverId: item.serverId || fallbackSourceId,
            songId: item.songId,
            title: item.title || '',
        });
        existing.add(item.id);
    }
    const byMonth = new Map<string, ListenEntry[]>();
    for (const entry of fresh) byMonth.set(monthKey(entry.playedAt), [...(byMonth.get(monthKey(entry.playedAt)) || []), entry]);
    for (const [key, items] of byMonth) await update<ListenEntry[]>(key, (current) => [...(current || []), ...items], store());
    useListening.setState((s) => ({
        entries: [...s.entries, ...fresh].sort((a, b) => a.playedAt - b.playedAt),
        version: s.version + 1,
    }));
    return fresh.length;
};
