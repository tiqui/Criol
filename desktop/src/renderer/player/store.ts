import { create } from 'zustand';

import type { Song } from '@shared/types';

import { toast } from '@/components/toast';
import i18n from '@/i18n';
import { randomId } from '@/lib/md5';
import { currentLibrary, libraryState } from '@/library/store';
import { useListening } from '@/insights/listening-db';
import { settings } from '@/settings/store';
import { getActiveSource } from '@/sources/store';

import { pickAlbums, pickSongs, shuffled } from './auto-dj';
import { engine } from './engine';
import { buildRadio, RADIO_REFILL_AT, RADIO_REFILL_SIZE, RADIO_START_SIZE } from './song-radio';

export interface QueueItem {
    /** Id único dentro da fila (a mesma música pode aparecer mais de uma vez). */
    key: string;
    song: Song;
    /** Entrou pelo Auto DJ. */
    auto?: boolean;
}

export type RepeatMode = 'all' | 'off' | 'one';
export type PlayerStatus = 'idle' | 'loading' | 'paused' | 'playing';

interface PlayerState {
    queue: QueueItem[];
    index: number;
    /** Ordem antes de ligar o aleatório. */
    original: null | QueueItem[];
    shuffle: boolean;
    repeat: RepeatMode;
    status: PlayerStatus;
    volume: number;
    muted: boolean;
    speed: number;
    /** Música-semente do rádio, se a fila é um rádio. */
    radioSeed: null | Song;
    sleepAt: null | number;
    sleepEndOfSong: boolean;
    fullscreen: boolean;
}

export const usePlayer = create<PlayerState>()(() => ({
    fullscreen: false,
    index: -1,
    muted: false,
    original: null,
    queue: [],
    radioSeed: null,
    repeat: 'off',
    shuffle: false,
    sleepAt: null,
    sleepEndOfSong: false,
    speed: 1,
    status: 'idle',
    volume: 0.8,
}));

/** Posição separada: muda várias vezes por segundo e só poucos componentes precisam dela. */
export const usePosition = create<{ position: number; duration: number; buffered: number }>()(() => ({
    buffered: 0,
    duration: 0,
    position: 0,
}));

const state = () => usePlayer.getState();
const set = (patch: Partial<PlayerState>) => usePlayer.setState(patch);

export const currentItem = () => {
    const { index, queue } = state();
    return queue[index] || null;
};
export const useCurrentSong = () => usePlayer((s) => s.queue[s.index]?.song || null);

const toItems = (songs: Song[], auto = false): QueueItem[] => songs.map((song) => ({ auto: auto || undefined, key: randomId(10), song }));

// ---------------------------------------------------------------------------
// Carregamento e eventos do motor
// ---------------------------------------------------------------------------

type Listener = (song: null | Song, previous: null | Song) => void;
const trackListeners = new Set<Listener>();
/** Avisa quando a música atual muda (histórico, scrobble, sessão de mídia). */
export const onTrackChange = (listener: Listener) => {
    trackListeners.add(listener);
    return () => trackListeners.delete(listener);
};

let loadedKey: null | string = null;
let loadedSong: null | Song = null;
let errorStreak = 0;
/** Fonte a que a fila atual pertence (cada fonte guarda a sua fila). */
let queueSourceId: null | string = null;
let suppressSave = false;
const claimQueue = () => {
    queueSourceId = libraryState().sourceId;
};

async function loadCurrent(autoplay: boolean, startMs = 0) {
    const item = currentItem();
    const previous = loadedSong;
    if (!item) {
        engine.stop();
        loadedKey = null;
        loadedSong = null;
        set({ status: 'idle' });
        usePosition.setState({ buffered: 0, duration: 0, position: 0 });
        trackListeners.forEach((l) => l(null, previous));
        return;
    }
    const source = getActiveSource();
    if (!source) return;
    loadedKey = item.key;
    loadedSong = item.song;
    usePosition.setState({ buffered: 0, duration: item.song.durationMs, position: startMs });
    set({ status: autoplay ? 'loading' : 'paused' });
    trackListeners.forEach((l) => l(item.song, previous));
    try {
        const url = await source.streamUrl(item.song);
        if (loadedKey !== item.key) return;
        await engine.load(item.key, url, autoplay, startMs);
    } catch (error) {
        if (loadedKey === item.key) handleError(error instanceof Error ? error.message : 'load');
    }
    void refillQueue();
}

let preloadTimer: ReturnType<typeof setTimeout> | undefined;
const schedulePreload = () => {
    clearTimeout(preloadTimer);
    preloadTimer = setTimeout(async () => {
        const next = nextIndex(false);
        const item = next === null ? null : state().queue[next];
        if (!item || item.key === loadedKey) return;
        const source = getActiveSource();
        if (!source) return;
        try {
            engine.preload(item.key, await source.streamUrl(item.song));
        } catch {
            /* tenta de novo ao tocar */
        }
    }, 4000);
};

function handleError(reason: string) {
    errorStreak += 1;
    const song = currentItem()?.song;
    toast.error(i18n.t('player.playbackError'), song ? `${song.title} — ${i18n.t(`player.errors.${reason}`, { defaultValue: reason })}` : undefined);
    // Pula para a próxima, mas não fica em loop se nada toca (servidor fora do ar, por exemplo).
    if (errorStreak < 3 && nextIndex(false) !== null) void next(false);
    else set({ status: 'paused' });
}

let attached = false;
export function attachEngine() {
    if (attached) return;
    attached = true;
    const { muted, volume } = loadVolume();
    set({ muted, volume });
    engine.setVolume(volume, muted);
    engine.attach({
        onEnded: () => {
            const { sleepEndOfSong } = state();
            if (sleepEndOfSong) {
                set({ sleepAt: null, sleepEndOfSong: false, status: 'paused' });
                return;
            }
            if (state().repeat === 'one') {
                engine.seek(0);
                void engine.play();
                trackListeners.forEach((l) => l(loadedSong, loadedSong));
                return;
            }
            void next(false);
        },
        onError: (reason) => handleError(reason),
        onState: (status) => {
            if (status === 'playing') errorStreak = 0;
            set({ status });
        },
        onTime: (position, duration, buffered) => {
            usePosition.setState({ buffered, duration: duration || currentItem()?.song.durationMs || 0, position });
            const { sleepAt } = state();
            if (sleepAt && Date.now() >= sleepAt) {
                set({ sleepAt: null });
                pause();
            }
        },
    });
}

// ---------------------------------------------------------------------------
// Ações
// ---------------------------------------------------------------------------

/** Substitui a fila e começa a tocar. */
export function playSongs(songs: Song[], startIndex = 0, options: { shuffle?: boolean; radioSeed?: null | Song } = {}) {
    if (!songs.length) return;
    let items = toItems(songs);
    let index = Math.min(Math.max(startIndex, 0), items.length - 1);
    let original: null | QueueItem[] = null;
    const shuffle = options.shuffle ?? state().shuffle;
    if (shuffle) {
        original = items;
        const first = options.shuffle && startIndex === 0 ? null : items[index];
        const rest = shuffled(first ? items.filter((i) => i !== first) : items);
        items = first ? [first, ...rest] : rest;
        index = 0;
    }
    claimQueue();
    set({ index, original, queue: items, radioSeed: options.radioSeed ?? null, shuffle });
    void loadCurrent(true);
}

export function playNext(songs: Song[]) {
    if (!songs.length) return;
    const { index, original, queue } = state();
    if (index < 0) return playSongs(songs);
    const items = toItems(songs);
    claimQueue();
    const nextQueue = [...queue.slice(0, index + 1), ...items, ...queue.slice(index + 1)];
    set({ original: original ? [...original, ...items] : null, queue: nextQueue });
    schedulePreload();
    toast.info(i18n.t('player.addedNext', { count: songs.length }));
}

export function addToQueue(songs: Song[], auto = false) {
    if (!songs.length) return;
    const { index, original, queue } = state();
    if (index < 0 && !auto) return playSongs(songs);
    const items = toItems(songs, auto);
    claimQueue();
    set({ original: original ? [...original, ...items] : null, queue: [...queue, ...items] });
    schedulePreload();
    if (!auto) toast.info(i18n.t('player.addedToQueue', { count: songs.length }));
}

export function playAt(index: number) {
    const { queue } = state();
    if (index < 0 || index >= queue.length) return;
    set({ index });
    void loadCurrent(true);
}

const nextIndex = (userAction: boolean): null | number => {
    const { index, queue, repeat } = state();
    if (!queue.length) return null;
    if (repeat === 'one' && !userAction) return index;
    if (index + 1 < queue.length) return index + 1;
    return repeat === 'all' ? 0 : null;
};

export async function next(userAction = true) {
    await refillQueue();
    const target = nextIndex(userAction);
    if (target === null) {
        // Fim da fila: volta para o começo, pausado.
        engine.pause();
        set({ status: 'paused' });
        usePosition.setState({ position: 0 });
        engine.seek(0);
        return;
    }
    set({ index: target });
    await loadCurrent(true);
}

export function previous() {
    const { index, queue, repeat } = state();
    if (usePosition.getState().position > 4000 || index <= 0) {
        if (index <= 0 && repeat === 'all' && queue.length > 1 && usePosition.getState().position <= 4000) {
            set({ index: queue.length - 1 });
            void loadCurrent(true);
            return;
        }
        seek(0);
        if (state().status !== 'playing') void play();
        return;
    }
    set({ index: index - 1 });
    void loadCurrent(true);
}

export async function play() {
    if (!currentItem()) return;
    if (!engine.hasSource) {
        await loadCurrent(true, usePosition.getState().position);
        return;
    }
    await engine.play();
}

export function pause() {
    engine.pause();
}

export function togglePlay() {
    if (state().status === 'playing' || state().status === 'loading') pause();
    else void play();
}

export function seek(ms: number) {
    usePosition.setState({ position: ms });
    engine.seek(ms);
}

export function skipBy(seconds: number) {
    const { duration, position } = usePosition.getState();
    seek(Math.min(Math.max(0, position + seconds * 1000), Math.max(0, duration - 500)));
}

export function setShuffle(value: boolean) {
    const { index, original, queue } = state();
    const current = queue[index];
    if (value === state().shuffle) return;
    if (value) {
        const rest = shuffled(queue.filter((_, i) => i !== index));
        set({ index: current ? 0 : -1, original: queue, queue: current ? [current, ...rest] : rest, shuffle: true });
    } else {
        const restored = original?.filter((item) => queue.includes(item)) || queue;
        // Músicas que entraram depois de ligar o aleatório também ficam.
        for (const item of queue) if (!restored.includes(item)) restored.push(item);
        set({ index: current ? restored.indexOf(current) : -1, original: null, queue: restored, shuffle: false });
    }
    schedulePreload();
}

export const toggleShuffle = () => setShuffle(!state().shuffle);

export function cycleRepeat() {
    const order: RepeatMode[] = ['off', 'all', 'one'];
    set({ repeat: order[(order.indexOf(state().repeat) + 1) % order.length] });
    schedulePreload();
}

export function setVolume(volume: number) {
    const value = Math.min(1, Math.max(0, volume));
    set({ muted: false, volume: value });
    engine.setVolume(value, false);
    saveVolume();
}

export function toggleMute() {
    const muted = !state().muted;
    set({ muted });
    engine.setVolume(state().volume, muted);
    saveVolume();
}

export function setSpeed(speed: number) {
    set({ speed });
    engine.setSpeed(speed);
}

export function removeFromQueue(keys: string[]) {
    const { index, original, queue } = state();
    const current = queue[index];
    const drop = new Set(keys);
    const nextQueue = queue.filter((item) => !drop.has(item.key));
    const removingCurrent = current && drop.has(current.key);
    let nextIdx = current && !removingCurrent ? nextQueue.indexOf(current) : Math.min(index, nextQueue.length - 1);
    if (!nextQueue.length) nextIdx = -1;
    set({ index: nextIdx, original: original ? original.filter((item) => !drop.has(item.key)) : null, queue: nextQueue });
    if (removingCurrent) void loadCurrent(state().status === 'playing');
    else schedulePreload();
}

export function moveInQueue(from: number, to: number) {
    const { index, queue } = state();
    if (from === to || from < 0 || from >= queue.length) return;
    const items = [...queue];
    const [moved] = items.splice(from, 1);
    items.splice(Math.min(Math.max(0, to), items.length), 0, moved);
    const current = queue[index];
    set({ index: current ? items.indexOf(current) : index, queue: items });
    schedulePreload();
}

/** Limpa a fila, mantendo a música atual. */
export function clearUpcoming() {
    const { index, queue } = state();
    const current = queue[index];
    set({ index: current ? 0 : -1, original: null, queue: current ? [current] : [], radioSeed: null });
    engine.clearPreload();
}

export function clearQueue() {
    set({ index: -1, original: null, queue: [], radioSeed: null });
    void loadCurrent(false);
}

export function startRadio(seed: Song) {
    const { entries } = useListening.getState();
    const picks = buildRadio(currentLibrary(), libraryState().user, entries, seed, RADIO_START_SIZE);
    set({ shuffle: false });
    playSongs([seed, ...picks], 0, { radioSeed: seed, shuffle: false });
    toast.info(i18n.t('player.radioStarted', { title: seed.title }));
}

export function stopRadio() {
    set({ radioSeed: null });
}

export function setSleepTimer(minutes: null | number, endOfSong = false) {
    set({ sleepAt: minutes ? Date.now() + minutes * 60_000 : null, sleepEndOfSong: endOfSong });
}

export const setFullscreen = (fullscreen: boolean) => set({ fullscreen });

// ---------------------------------------------------------------------------
// Rádio e Auto DJ: completam a fila quando ela está acabando
// ---------------------------------------------------------------------------

let refilling = false;
async function refillQueue() {
    if (refilling) return;
    const { index, queue, radioSeed, repeat } = state();
    const current = queue[index];
    if (!current || repeat !== 'off') return;
    const remaining = queue.length - index - 1;
    const library = currentLibrary();
    const user = libraryState().user;
    refilling = true;
    try {
        if (radioSeed) {
            if (remaining >= RADIO_REFILL_AT) return;
            const exclude = new Set(queue.map((i) => i.song.id));
            const recent = queue.slice(Math.max(0, index - 4), index + 1).map((i) => i.song);
            const picks = buildRadio(library, user, useListening.getState().entries, radioSeed, RADIO_REFILL_SIZE, exclude, recent);
            addToQueue(picks, true);
            return;
        }
        const s = settings();
        if (!s.autoDjEnabled || remaining >= s.autoDjTiming) return;
        const options = { allowDuplicates: s.autoDjAllowDuplicates, count: s.autoDjCount, mode: s.autoDjMode, strategy: s.autoDjStrategy } as const;
        if (s.autoDjMode === 'albums') {
            const albums = pickAlbums(library, user, current.song, new Set(queue.map((i) => i.song.albumId)), options);
            addToQueue(albums.flatMap((a) => a.songs), true);
        } else {
            addToQueue(pickSongs(library, user, current.song, new Set(queue.map((i) => i.song.id)), options), true);
        }
    } finally {
        refilling = false;
        schedulePreload();
    }
}

// ---------------------------------------------------------------------------
// Persistência: volume sempre; fila por fonte
// ---------------------------------------------------------------------------

function loadVolume() {
    try {
        const raw = JSON.parse(localStorage.getItem('criol_volume') || 'null') as null | { muted: boolean; volume: number };
        if (raw && typeof raw.volume === 'number') return raw;
    } catch {
        /* ignore */
    }
    return { muted: false, volume: 0.8 };
}

function saveVolume() {
    try {
        localStorage.setItem('criol_volume', JSON.stringify({ muted: state().muted, volume: state().volume }));
    } catch {
        /* ignore */
    }
}

interface QueueSnapshot {
    sourceId: string;
    ids: string[];
    originalIds: null | string[];
    index: number;
    positionMs: number;
    shuffle: boolean;
    repeat: RepeatMode;
    speed: number;
    radioSeedId: null | string;
}

const queueKey = (sourceId: string) => `criol_queue_${sourceId}`;

export function saveQueue() {
    const sourceId = queueSourceId;
    if (!sourceId || suppressSave) return;
    const { index, original, queue, radioSeed, repeat, shuffle, speed } = state();
    const snapshot: QueueSnapshot = {
        ids: queue.map((i) => i.song.id),
        index,
        originalIds: original ? original.map((i) => i.song.id) : null,
        positionMs: Math.floor(usePosition.getState().position),
        radioSeedId: radioSeed?.id || null,
        repeat,
        shuffle,
        sourceId,
        speed,
    };
    try {
        localStorage.setItem(queueKey(sourceId), JSON.stringify(snapshot));
    } catch {
        /* ignore */
    }
}

/** Guarda a fila da fonte atual e esvazia o player (troca de fonte). */
export function resetForSource() {
    saveQueue();
    suppressSave = true;
    engine.stop();
    loadedKey = null;
    const previous = loadedSong;
    loadedSong = null;
    set({ index: -1, original: null, queue: [], radioSeed: null, status: 'idle' });
    usePosition.setState({ buffered: 0, duration: 0, position: 0 });
    if (previous) trackListeners.forEach((l) => l(null, previous));
    queueSourceId = null;
    suppressSave = false;
}

/** Restaura a fila salva da fonte (pausada, na mesma posição). */
export function restoreQueue(sourceId: string) {
    let raw: null | QueueSnapshot = null;
    try {
        raw = JSON.parse(localStorage.getItem(queueKey(sourceId)) || 'null') as null | QueueSnapshot;
    } catch {
        raw = null;
    }
    resetForSource();
    queueSourceId = sourceId;
    if (!raw || !settings().resumeQueue) return;
    suppressSave = true;
    try {
        const library = currentLibrary();
        const songs = raw.ids.map((id) => library.songsById.get(id)).filter((s): s is Song => Boolean(s));
        if (!songs.length) return;
        const queue = toItems(songs);
        const currentId = raw.ids[raw.index];
        const index = Math.max(0, songs.findIndex((s) => s.id === currentId));
        let original: null | QueueItem[] = null;
        if (raw.shuffle && raw.originalIds) {
            const byId = new Map(queue.map((i) => [i.song.id, i]));
            original = raw.originalIds.map((id) => byId.get(id)).filter((i): i is QueueItem => Boolean(i));
        }
        set({
            index,
            original,
            queue,
            radioSeed: raw.radioSeedId ? library.songsById.get(raw.radioSeedId) || null : null,
            repeat: raw.repeat || 'off',
            shuffle: Boolean(raw.shuffle),
            speed: raw.speed || 1,
            status: 'paused',
        });
        engine.setSpeed(raw.speed || 1);
        const song = queue[index].song;
        loadedSong = song;
        usePosition.setState({ buffered: 0, duration: song.durationMs, position: raw.positionMs || 0 });
        trackListeners.forEach((l) => l(song, null));
    } catch {
        /* fila salva inválida */
    } finally {
        suppressSave = false;
    }
}

let saveTimer: ReturnType<typeof setInterval> | undefined;
export function startQueuePersistence() {
    usePlayer.subscribe((s, prev) => {
        if (s.queue !== prev.queue || s.index !== prev.index || s.shuffle !== prev.shuffle || s.repeat !== prev.repeat) saveQueue();
        if (s.index !== prev.index || s.queue !== prev.queue) schedulePreload();
    });
    clearInterval(saveTimer);
    saveTimer = setInterval(() => state().status === 'playing' && saveQueue(), 5000);
    window.addEventListener('beforeunload', saveQueue);
}
