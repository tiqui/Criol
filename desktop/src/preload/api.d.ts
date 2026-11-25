import type { LyricsIndexEntry } from '../shared/lyrics/folder-match';
import type { LocalScanResult, ScanProgress } from '../shared/types';

/** API do app desktop exposta para a interface (window.criolDesktop). Ausente na versão web. */
export interface CriolDesktopApi {
    platform: string;
    appInfo: () => Promise<{ platform: string; version: string }>;
    pickFolder: (title?: string) => Promise<null | string>;
    loadLocal: (sourceId: string, root: string) => Promise<LocalScanResult | null>;
    scanLocal: (sourceId: string, root: string) => Promise<LocalScanResult>;
    removeLocal: (sourceId: string) => Promise<void>;
    onLocalProgress: (callback: (sourceId: string, progress: ScanProgress) => void) => () => void;
    localEmbeddedLyrics: (sourceId: string, songId: string) => Promise<null | string>;
    indexLyricsFolders: (folders: string[]) => Promise<LyricsIndexEntry[]>;
    readLyricsFile: (path: string) => Promise<null | string>;
    setTitleBarColors: (color: string, symbolColor: string) => Promise<void>;
    onMaximized: (callback: (maximized: boolean) => void) => () => void;
    openExternal: (url: string) => Promise<void>;
}

declare global {
    interface Window {
        criolDesktop?: CriolDesktopApi;
    }
    const __CRIOL_WEB__: boolean;
}
