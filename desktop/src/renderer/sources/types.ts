import type { Song, SourceType } from '@shared/types';

/** Configuração salva de uma fonte de música. */
export interface SourceConfig {
    id: string;
    type: SourceType;
    name: string;
    /** Servidores: endereço base (sem "/rest"). */
    url?: string;
    username?: string;
    /** Subsonic: token = md5(senha + salt). */
    subsonic?: { salt: string; token: string };
    /** Jellyfin */
    jellyfin?: { token: string; userId: string };
    /** Pasta local no desktop: caminho absoluto. */
    path?: string;
    /** Pasta local na versão web: nome da pasta escolhida (o acesso fica no IndexedDB). */
    folderName?: string;
    createdAt: number;
}

export interface Playlist {
    id: string;
    name: string;
    songIds: string[];
    created: number;
    updated: number;
    /** Playlist de outro usuário do servidor (só leitura). */
    readonly?: boolean;
    comment?: string;
}

export type FavoriteKind = 'album' | 'artist' | 'song';

/** O que a sincronização traz do servidor além das músicas. */
export interface SyncResult {
    songs: Song[];
    favorites?: { albums: string[]; artists: string[]; songs: string[] };
    ratings?: Record<string, number>;
    playCounts?: Record<string, number>;
    lastPlayed?: Record<string, number>;
    playlists?: Playlist[];
}

export interface SyncProgress {
    /** Texto curto do que está acontecendo. */
    label: string;
    done: number;
    total: number;
}

export interface SourceCapabilities {
    /** Playlists ficam no servidor. */
    serverPlaylists: boolean;
    /** Avaliações (estrelas) ficam no servidor. */
    serverRatings: boolean;
    serverFavorites: boolean;
    scrobble: boolean;
}

export interface MusicSource {
    readonly config: SourceConfig;
    readonly capabilities: SourceCapabilities;
    /** Biblioteca salva do último escaneamento (pasta local). Servidores usam o cache do app. */
    loadCached?: () => Promise<null | SyncResult>;
    /** Busca a biblioteca inteira. */
    sync: (onProgress: (progress: SyncProgress) => void) => Promise<SyncResult>;
    /** Endereço para tocar a música. */
    streamUrl: (song: Song) => Promise<string>;
    /** Endereço da capa (síncrono; "idb-art:" indica capa guardada no navegador). */
    coverUrl: (coverId: string, size: number) => null | string;
    /** Letra crua (LRC ou texto) do servidor ou do arquivo. */
    lyrics: (song: Song) => Promise<null | string>;
    setFavorite?: (kind: FavoriteKind, id: string, value: boolean) => Promise<void>;
    setRating?: (songId: string, rating: number) => Promise<void>;
    scrobble?: (song: Song, submission: boolean, playedAt: number) => Promise<void>;
    createPlaylist?: (name: string, songIds: string[]) => Promise<Playlist>;
    updatePlaylist?: (playlist: Playlist, patch: { name?: string; songIds?: string[] }) => Promise<void>;
    deletePlaylist?: (playlist: Playlist) => Promise<void>;
    /** Pasta local na web: o navegador pede permissão de novo a cada sessão. */
    needsPermission?: () => Promise<boolean>;
    requestPermission?: () => Promise<boolean>;
    /** Limpeza ao remover a fonte. */
    dispose?: () => Promise<void>;
}

export class SourceError extends Error {
    constructor(
        message: string,
        readonly code: 'auth' | 'network' | 'notFound' | 'server' | 'unsupported' = 'server',
    ) {
        super(message);
    }
}
