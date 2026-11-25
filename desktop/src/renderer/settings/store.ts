import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type SongSort = 'added' | 'album' | 'artist' | 'duration' | 'plays' | 'title' | 'year';
export type AlbumSort = 'added' | 'artist' | 'name' | 'plays' | 'random' | 'year';
export type ArtistSort = 'albums' | 'name' | 'plays' | 'songs';
export type Language = 'en' | 'pt-BR' | 'system';

export interface Settings {
    themeId: string;
    /** Cor de destaque personalizada; null = cor do tema. */
    accent: null | string;
    language: Language;
    /** Fundo do player em tela cheia com a capa desfocada. */
    playerBlur: boolean;
    /** Cabeçalhos de álbum/artista na cor da capa. */
    coverColors: boolean;
    skipBackSeconds: number;
    skipForwardSeconds: number;
    showRatings: boolean;
    resumeQueue: boolean;
    albumsAsGrid: boolean;
    songSort: SongSort;
    songSortDesc: boolean;
    albumSort: AlbumSort;
    albumSortDesc: boolean;
    artistSort: ArtistSort;
    // Auto DJ
    autoDjEnabled: boolean;
    autoDjMode: 'albums' | 'songs';
    autoDjStrategy: 'random' | 'similar';
    autoDjCount: number;
    autoDjTiming: number;
    autoDjAllowDuplicates: boolean;
    // Letras
    lyricsFolders: string[];
    lyricsOnline: boolean;
    lyricsFontSize: number;
    // Histórico
    recordHistory: boolean;
    scrobble: boolean;
    // Layout
    sidebarCollapsed: boolean;
    sidePanel: 'lyrics' | 'none' | 'queue';
    sidePanelWidth: number;
    showExplore: boolean;
}

export const DEFAULT_SETTINGS: Settings = {
    accent: null,
    albumSort: 'name',
    albumSortDesc: false,
    albumsAsGrid: true,
    artistSort: 'name',
    autoDjAllowDuplicates: false,
    autoDjCount: 5,
    autoDjEnabled: false,
    autoDjMode: 'songs',
    autoDjStrategy: 'similar',
    autoDjTiming: 1,
    coverColors: true,
    language: 'system',
    lyricsFolders: [],
    lyricsFontSize: 26,
    lyricsOnline: true,
    playerBlur: true,
    recordHistory: true,
    resumeQueue: true,
    scrobble: true,
    showExplore: true,
    showRatings: true,
    sidebarCollapsed: false,
    sidePanel: 'none',
    sidePanelWidth: 360,
    skipBackSeconds: 10,
    skipForwardSeconds: 30,
    songSort: 'title',
    songSortDesc: false,
    themeId: 'default-dark',
};

interface SettingsStore extends Settings {
    set: (patch: Partial<Settings>) => void;
    reset: () => void;
}

export const useSettings = create<SettingsStore>()(
    persist(
        (set) => ({
            ...DEFAULT_SETTINGS,
            reset: () => set({ ...DEFAULT_SETTINGS }),
            set: (patch) => set(patch),
        }),
        {
            merge: (persisted, current) => ({ ...current, ...(persisted as Partial<Settings>) }),
            name: 'criol_settings_v2',
            partialize: ({ reset: _r, set: _s, ...rest }) => rest,
            version: 1,
        },
    ),
);

export const settings = () => useSettings.getState();
