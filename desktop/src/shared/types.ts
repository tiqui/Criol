/** Tipos usados tanto pela interface quanto pelo processo principal do Electron. */

export type SourceType = 'jellyfin' | 'local' | 'subsonic';

/** Uma faixa da biblioteca, com o mesmo formato para todas as fontes. */
export interface Song {
    /** id dentro da fonte (id do servidor ou hash do caminho do arquivo). */
    id: string;
    title: string;
    /** Artista como está na tag ("A; B" ou "A feat. B"). */
    artist: string;
    /** Artistas separados. */
    artists: string[];
    albumArtist: string;
    album: string;
    albumId: string;
    /** Gêneros separados. */
    genres: string[];
    year: number;
    track: number;
    disc: number;
    durationMs: number;
    /** Caminho relativo à raiz da biblioteca, com "/" ("Artista/Álbum/01 - Música.flac"). */
    path: string;
    size: number;
    /** Quando entrou na biblioteca (epoch ms). */
    dateAdded: number;
    /** kbps */
    bitrate: number;
    sampleRate: number;
    /** Extensão/formato: flac, mp3, opus... */
    format: string;
    /** Id da capa no servidor (ou do álbum, na pasta local). null = sem capa. */
    coverId: null | string;
    composer?: string;
    /** Ids dos artistas no servidor, pelo nome normalizado (para favoritar artistas). */
    artistRefs?: Record<string, string>;
    /** Fonte local: arquivo de letra ao lado da música. */
    lyricsPath?: null | string;
    /** Fonte local: a tag tem letra embutida. */
    hasEmbeddedLyrics?: boolean;
}

/** Resultado do escaneamento de uma pasta local (processo principal). */
export interface LocalScanResult {
    songs: Song[];
    /** albumId -> tem capa salva. */
    art: string[];
    scannedAt: number;
}

export interface ScanProgress {
    phase: 'art' | 'files' | 'tags';
    done: number;
    total: number;
}

export interface LyricLine {
    /** ms; null em letras sem sincronia. */
    time: null | number;
    text: string;
}

export interface Lyrics {
    synced: boolean;
    lines: LyricLine[];
    /** De onde veio: "Servidor", "Arquivo", "Pasta de letras", "LRCLIB"... */
    source: string;
}
