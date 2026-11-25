import type { Song } from '@shared/types';

import { addListenEntry, entryFromSong, isCountedListen, MIN_STORED_MS, useListening, type ListenEntry } from '@/insights/listening-db';
import { libraryState, recordPlay } from '@/library/store';
import { settings } from '@/settings/store';
import { getActiveSource } from '@/sources/store';
import { resolveIdbArt } from '@/sources/local-web';

import { next, onTrackChange, pause, play, previous, seek, skipBy, usePlayer, usePosition } from './store';

/**
 * Registra o que foi ouvido (histórico, contagem de reproduções, scrobble no servidor) e
 * mantém os controles de mídia do sistema (teclas de mídia, tela de bloqueio, Windows/macOS).
 */

interface Session {
    song: Song;
    sourceId: string;
    entryId: string;
    playedAt: number;
    listened: number;
    lastPos: number;
    nowPlayingSent: boolean;
    counted: boolean;
}

const PENDING_KEY = 'criol_pending_listen';
let session: null | Session = null;

const finalize = () => {
    const s = session;
    session = null;
    try {
        localStorage.removeItem(PENDING_KEY);
    } catch {
        /* ignore */
    }
    if (!s || s.listened < MIN_STORED_MS || !settings().recordHistory) return;
    void addListenEntry(entryFromSong(s.song, s.sourceId, Math.round(s.listened), s.playedAt, s.entryId));
};

const savePending = () => {
    if (!session || session.listened < MIN_STORED_MS || !settings().recordHistory) return;
    try {
        localStorage.setItem(PENDING_KEY, JSON.stringify(entryFromSong(session.song, session.sourceId, Math.round(session.listened), session.playedAt, session.entryId)));
    } catch {
        /* ignore */
    }
};

/** Escuta que estava em andamento quando o app fechou. */
export const recoverPendingListen = async () => {
    try {
        const raw = localStorage.getItem(PENDING_KEY);
        if (!raw) return;
        localStorage.removeItem(PENDING_KEY);
        const entry = JSON.parse(raw) as ListenEntry;
        if (entry?.id && !useListening.getState().entries.some((e) => e.id === entry.id)) await addListenEntry(entry);
    } catch {
        /* ignore */
    }
};

const scrobble = (song: Song, submission: boolean, playedAt: number) => {
    const source = getActiveSource();
    if (!settings().scrobble || !source?.scrobble || !source.capabilities.scrobble) return;
    void source.scrobble(song, submission, playedAt).catch(() => undefined);
};

// ---------------------------------------------------------------------------
// Sessão de mídia do sistema
// ---------------------------------------------------------------------------

const media = typeof navigator !== 'undefined' && 'mediaSession' in navigator ? navigator.mediaSession : null;

const updateMetadata = async (song: null | Song) => {
    if (!media) return;
    if (!song) {
        media.metadata = null;
        return;
    }
    const source = getActiveSource();
    let artwork: MediaImage[] = [];
    if (song.coverId && source) {
        const url = source.coverUrl(song.coverId, 512);
        const resolved = url?.startsWith('idb-art:') ? await resolveIdbArt(url) : url;
        if (resolved) artwork = [{ sizes: '512x512', src: resolved, type: 'image/jpeg' }];
    }
    media.metadata = new MediaMetadata({ album: song.album, artist: song.artist, artwork, title: song.title });
};

let lastPositionUpdate = 0;

export function startTracking() {
    void recoverPendingListen();

    onTrackChange((song) => {
        finalize();
        void updateMetadata(song);
        if (!song) return;
        session = {
            counted: false,
            entryId: crypto.randomUUID ? crypto.randomUUID() : String(Date.now()),
            lastPos: usePosition.getState().position,
            listened: 0,
            nowPlayingSent: false,
            playedAt: Date.now(),
            song,
            sourceId: libraryState().sourceId || '',
        };
    });

    usePosition.subscribe(({ duration, position }) => {
        const s = session;
        if (s && usePlayer.getState().status === 'playing') {
            const delta = position - s.lastPos;
            if (delta > 0 && delta < 2500) {
                s.listened += delta;
                if (!s.nowPlayingSent && s.listened > 1500) {
                    s.nowPlayingSent = true;
                    scrobble(s.song, false, s.playedAt);
                }
                if (!s.counted && isCountedListen(s.song.durationMs || duration, s.listened)) {
                    s.counted = true;
                    recordPlay(s.song.id);
                    scrobble(s.song, true, s.playedAt);
                }
                if (Math.floor(s.listened / 10_000) !== Math.floor((s.listened - delta) / 10_000)) savePending();
            }
            s.lastPos = position;
        } else if (s) {
            s.lastPos = position;
        }
        const now = Date.now();
        if (media && duration > 0 && now - lastPositionUpdate > 1000) {
            lastPositionUpdate = now;
            try {
                media.setPositionState({ duration: duration / 1000, playbackRate: usePlayer.getState().speed, position: Math.min(position, duration) / 1000 });
            } catch {
                /* posição fora do intervalo durante a troca de música */
            }
        }
    });

    usePlayer.subscribe((s, prev) => {
        if (media && s.status !== prev.status) media.playbackState = s.status === 'playing' || s.status === 'loading' ? 'playing' : s.status === 'paused' ? 'paused' : 'none';
    });

    window.addEventListener('beforeunload', savePending);

    if (media) {
        const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
            ['play', () => void play()],
            ['pause', () => pause()],
            ['stop', () => pause()],
            ['nexttrack', () => void next(true)],
            ['previoustrack', () => previous()],
            ['seekbackward', (d) => skipBy(-(d.seekOffset || settings().skipBackSeconds))],
            ['seekforward', (d) => skipBy(d.seekOffset || settings().skipForwardSeconds)],
            ['seekto', (d) => d.seekTime !== undefined && seek(d.seekTime * 1000)],
        ];
        for (const [action, handler] of handlers) {
            try {
                media.setActionHandler(action, handler);
            } catch {
                /* ação sem suporte */
            }
        }
    }
}
