import { createStore, get as idbGet, set as idbSet, del as idbDel, type UseStore } from 'idb-keyval';
import { create } from 'zustand';

import type { Song } from '@shared/types';

import { randomId } from '@/lib/md5';
import { getSource } from '@/sources/store';
import type { FavoriteKind, Playlist, SourceConfig, SyncProgress, SyncResult } from '@/sources/types';
import { toast } from '@/components/toast';
import i18n from '@/i18n';

import { Library } from './library';

/** Biblioteca da fonte ativa, favoritos, avaliações, contagens e playlists. */

export interface UserData {
    favoriteSongs: string[];
    favoriteAlbums: string[];
    /** Nomes normalizados. */
    favoriteArtists: string[];
    ratings: Record<string, number>;
    playCounts: Record<string, number>;
    lastPlayed: Record<string, number>;
    /** Playlists guardadas no app (fonte local). */
    playlists: Playlist[];
}

const EMPTY_USER_DATA: UserData = {
    favoriteAlbums: [],
    favoriteArtists: [],
    favoriteSongs: [],
    lastPlayed: {},
    playCounts: {},
    playlists: [],
    ratings: {},
};

interface CachedLibrary {
    songs: Song[];
    playlists?: Playlist[];
    syncedAt: number;
}

let libraryDb: null | UseStore = null;
let userDb: null | UseStore = null;
const libraryStore = () => (libraryDb ??= createStore('criol-library', 'libraries'));
const userStore = () => (userDb ??= createStore('criol-userdata', 'userdata'));

/** Servidores sincronizam sozinhos ao abrir se a última vez foi há mais disso. */
const AUTO_SYNC_AFTER_MS = 30 * 60 * 1000;

interface LibraryState {
    sourceId: null | string;
    library: Library;
    loaded: boolean;
    syncing: boolean;
    progress: null | SyncProgress;
    error: null | string;
    syncedAt: number;
    user: UserData;
    favoriteSongSet: Set<string>;
    favoriteAlbumSet: Set<string>;
    favoriteArtistSet: Set<string>;
    /** Playlists do servidor (ou do app, na pasta local). */
    playlists: Playlist[];
    /** Muda a cada alteração de favoritos/contagens (útil como dependência). */
    version: number;
}

export const useLibrary = create<LibraryState>()(() => ({
    error: null,
    favoriteAlbumSet: new Set(),
    favoriteArtistSet: new Set(),
    favoriteSongSet: new Set(),
    library: Library.EMPTY,
    loaded: false,
    playlists: [],
    progress: null,
    sourceId: null,
    syncedAt: 0,
    syncing: false,
    user: EMPTY_USER_DATA,
    version: 0,
}));

const setUser = (user: UserData) => {
    useLibrary.setState((state) => ({
        favoriteAlbumSet: new Set(user.favoriteAlbums),
        favoriteArtistSet: new Set(user.favoriteArtists),
        favoriteSongSet: new Set(user.favoriteSongs),
        user,
        version: state.version + 1,
    }));
};

let saveTimer: ReturnType<typeof setTimeout> | undefined;
const persistUser = () => {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
        const { sourceId, user } = useLibrary.getState();
        if (sourceId) void idbSet(sourceId, user, userStore());
    }, 400);
};

const updateUser = (fn: (user: UserData) => UserData) => {
    setUser(fn(useLibrary.getState().user));
    persistUser();
};

let activeConfig: null | SourceConfig = null;
let loadToken = 0;

/** Troca a fonte ativa: mostra o cache na hora e sincroniza em segundo plano. */
export async function activateSource(config: null | SourceConfig) {
    const token = ++loadToken;
    activeConfig = config;
    if (!config) {
        useLibrary.setState({ error: null, library: Library.EMPTY, loaded: true, playlists: [], sourceId: null, syncing: false });
        setUser(EMPTY_USER_DATA);
        return;
    }
    useLibrary.setState({ error: null, loaded: false, progress: null, sourceId: config.id, syncing: false });
    const source = getSource(config);

    const [user, cached] = await Promise.all([
        idbGet<UserData>(config.id, userStore()).catch(() => undefined),
        config.type === 'local' && source.loadCached
            ? source.loadCached().then((r) => (r ? ({ songs: r.songs, syncedAt: Date.now() } as CachedLibrary) : undefined))
            : idbGet<CachedLibrary>(config.id, libraryStore()).catch(() => undefined),
    ]);
    if (token !== loadToken) return;
    setUser({ ...EMPTY_USER_DATA, ...(user || {}) });
    useLibrary.setState({
        library: cached ? new Library(cached.songs) : Library.EMPTY,
        loaded: true,
        playlists: config.type === 'local' ? user?.playlists || [] : cached?.playlists || [],
        syncedAt: cached?.syncedAt || 0,
    });

    if (source.needsPermission && (await source.needsPermission())) {
        useLibrary.setState({ error: 'permission' });
        return;
    }
    const stale = !cached || (config.type !== 'local' && Date.now() - cached.syncedAt > AUTO_SYNC_AFTER_MS) || config.type === 'local';
    if (stale) void syncLibrary();
}

/** Busca a biblioteca de novo (servidor) ou reescaneia a pasta. */
export async function syncLibrary() {
    const config = activeConfig;
    if (!config || useLibrary.getState().syncing) return;
    const token = loadToken;
    const source = getSource(config);
    useLibrary.setState({ error: null, progress: null, syncing: true });
    try {
        const result: SyncResult = await source.sync((progress) => {
            if (token === loadToken) useLibrary.setState({ progress });
        });
        if (token !== loadToken) return;
        const syncedAt = Date.now();
        if (config.type !== 'local') {
            await idbSet(config.id, { playlists: result.playlists || [], songs: result.songs, syncedAt } satisfies CachedLibrary, libraryStore());
        }
        const current = useLibrary.getState().user;
        if (result.favorites) {
            updateUser((u) => ({
                ...u,
                favoriteAlbums: result.favorites!.albums,
                favoriteArtists: result.favorites!.artists,
                favoriteSongs: result.favorites!.songs,
                lastPlayed: { ...u.lastPlayed, ...(result.lastPlayed || {}) },
                // O servidor sabe das reproduções feitas em outros apps; mantém o maior número.
                playCounts: mergeMax(current.playCounts, result.playCounts || {}),
                ratings: source.capabilities.serverRatings ? result.ratings || {} : u.ratings,
            }));
        }
        useLibrary.setState({
            library: new Library(result.songs),
            playlists: config.type === 'local' ? useLibrary.getState().user.playlists : result.playlists || [],
            progress: null,
            syncedAt,
            syncing: false,
        });
    } catch (error) {
        if (token !== loadToken) return;
        const message = error instanceof Error ? error.message : String(error);
        useLibrary.setState({ error: message, progress: null, syncing: false });
        toast.error(i18n.t('library.syncFailed'), message);
    }
}

const mergeMax = (a: Record<string, number>, b: Record<string, number>) => {
    const out = { ...a };
    for (const [k, v] of Object.entries(b)) out[k] = Math.max(out[k] || 0, v);
    return out;
};

export async function forgetSourceData(sourceId: string) {
    await Promise.all([idbDel(sourceId, libraryStore()), idbDel(sourceId, userStore())]);
}

// ---------------------------------------------------------------------------
// Favoritos, avaliações, contagens
// ---------------------------------------------------------------------------

const listFor = (kind: FavoriteKind) => (kind === 'song' ? 'favoriteSongs' : kind === 'album' ? 'favoriteAlbums' : 'favoriteArtists');

export const isFavorite = (kind: FavoriteKind, id: string) => {
    const s = useLibrary.getState();
    return (kind === 'song' ? s.favoriteSongSet : kind === 'album' ? s.favoriteAlbumSet : s.favoriteArtistSet).has(id);
};

export const useIsFavorite = (kind: FavoriteKind, id: null | string | undefined) =>
    useLibrary((s) => (id ? (kind === 'song' ? s.favoriteSongSet : kind === 'album' ? s.favoriteAlbumSet : s.favoriteArtistSet).has(id) : false));

/** Favorita/desfavorita. Para artistas, o id é o nome normalizado. */
export async function setFavorite(kind: FavoriteKind, id: string, value: boolean) {
    const key = listFor(kind);
    updateUser((u) => ({ ...u, [key]: value ? [...new Set([...u[key], id])] : u[key].filter((x) => x !== id) }));
    const source = activeConfig ? getSource(activeConfig) : null;
    if (!source?.setFavorite || !source.capabilities.serverFavorites) return;
    let serverId: null | string | undefined = id;
    if (kind === 'artist') {
        const artist = useLibrary.getState().library.artistsByKey.get(id);
        serverId = artist?.songs.map((s) => s.artistRefs?.[id]).find(Boolean);
        if (!serverId) return;
    }
    try {
        await source.setFavorite(kind, serverId, value);
    } catch (error) {
        toast.error(i18n.t('library.serverActionFailed'), error instanceof Error ? error.message : undefined);
    }
}

export const toggleFavorite = (kind: FavoriteKind, id: string) => setFavorite(kind, id, !isFavorite(kind, id));

export async function setRating(songId: string, rating: number) {
    updateUser((u) => {
        const ratings = { ...u.ratings };
        if (rating > 0) ratings[songId] = rating;
        else delete ratings[songId];
        return { ...u, ratings };
    });
    const source = activeConfig ? getSource(activeConfig) : null;
    if (source?.setRating && source.capabilities.serverRatings) {
        await source.setRating(songId, rating).catch(() => toast.error(i18n.t('library.serverActionFailed')));
    }
}

export function recordPlay(songId: string) {
    updateUser((u) => ({
        ...u,
        lastPlayed: { ...u.lastPlayed, [songId]: Date.now() },
        playCounts: { ...u.playCounts, [songId]: (u.playCounts[songId] || 0) + 1 },
    }));
}

// ---------------------------------------------------------------------------
// Playlists
// ---------------------------------------------------------------------------

const isLocalPlaylists = () => !activeConfig || !getSource(activeConfig).capabilities.serverPlaylists;

const savePlaylists = (playlists: Playlist[]) => {
    useLibrary.setState({ playlists });
    if (isLocalPlaylists()) updateUser((u) => ({ ...u, playlists }));
    else if (activeConfig) {
        const id = activeConfig.id;
        void idbGet<CachedLibrary>(id, libraryStore()).then((cached) => {
            if (cached) void idbSet(id, { ...cached, playlists }, libraryStore());
        });
    }
};

const serverCall = async (fn: () => Promise<unknown>) => {
    try {
        await fn();
        return true;
    } catch (error) {
        toast.error(i18n.t('library.serverActionFailed'), error instanceof Error ? error.message : undefined);
        return false;
    }
};

export async function createPlaylist(name: string, songIds: string[] = []) {
    const source = activeConfig ? getSource(activeConfig) : null;
    let playlist: Playlist = { created: Date.now(), id: randomId(12), name, songIds, updated: Date.now() };
    if (source?.capabilities.serverPlaylists && source.createPlaylist) {
        try {
            playlist = await source.createPlaylist(name, songIds);
        } catch (error) {
            toast.error(i18n.t('library.serverActionFailed'), error instanceof Error ? error.message : undefined);
            return null;
        }
    }
    savePlaylists([...useLibrary.getState().playlists, playlist]);
    return playlist;
}

export async function updatePlaylist(id: string, patch: { name?: string; songIds?: string[] }) {
    const playlist = useLibrary.getState().playlists.find((p) => p.id === id);
    if (!playlist) return;
    const source = activeConfig ? getSource(activeConfig) : null;
    const next = { ...playlist, ...patch, updated: Date.now() };
    savePlaylists(useLibrary.getState().playlists.map((p) => (p.id === id ? next : p)));
    if (source?.capabilities.serverPlaylists && source.updatePlaylist) {
        const ok = await serverCall(() => source.updatePlaylist!(playlist, patch));
        if (!ok) savePlaylists(useLibrary.getState().playlists.map((p) => (p.id === id ? playlist : p)));
    }
}

export const addToPlaylist = (id: string, songIds: string[]) => {
    const playlist = useLibrary.getState().playlists.find((p) => p.id === id);
    if (!playlist) return Promise.resolve();
    return updatePlaylist(id, { songIds: [...playlist.songIds, ...songIds] });
};

export async function deletePlaylist(id: string) {
    const playlist = useLibrary.getState().playlists.find((p) => p.id === id);
    if (!playlist) return;
    const source = activeConfig ? getSource(activeConfig) : null;
    if (source?.capabilities.serverPlaylists && source.deletePlaylist) {
        if (!(await serverCall(() => source.deletePlaylist!(playlist)))) return;
    }
    savePlaylists(useLibrary.getState().playlists.filter((p) => p.id !== id));
}

export const libraryState = () => useLibrary.getState();
export const currentLibrary = () => useLibrary.getState().library;
export const activeSourceConfig = () => activeConfig;
