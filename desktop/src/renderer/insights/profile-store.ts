import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import type { RankedItem } from './listening-stats';

/** Perfil (nome, bio, foto, cor) e resumos mensais. Mesmo formato do Crify 1.x ("crify_profile", migrado para "criol_profile"). */

export interface MonthlyRecap {
    activeDays: number;
    busiestDay: null | { day: string; ms: number };
    byDay: Record<string, number>;
    byHour: number[];
    favoriteDaypart: null | string;
    generatedAt: number;
    month: string;
    newArtists: number;
    plays: number;
    previousTotalMs: null | number;
    serverId: null | string;
    topAlbums: RankedItem[];
    topArtists: RankedItem[];
    topGenres: RankedItem[];
    topSongs: RankedItem[];
    totalMs: number;
    uniqueArtists: number;
    uniqueGenres: number;
    uniqueSongs: number;
}

export const PROFILE_ACCENTS = ['#7c5cff', '#1db954', '#ff4fb1', '#ff8a3d', '#2fb8ff', '#ffd23f', '#e2453c', '#14b8a6'];

interface ProfileState {
    accent: string;
    /** Foto em data URL (JPEG 512×512). */
    avatar: null | string;
    bio: string;
    createdAt: number;
    displayName: string;
    pinnedBadge: null | string;
    recaps: Record<string, MonthlyRecap>;
    seenRecaps: string[];
    update: (data: Partial<Omit<ProfileState, 'markRecapSeen' | 'recaps' | 'saveRecap' | 'seenRecaps' | 'update'>>) => void;
    saveRecap: (recap: MonthlyRecap) => void;
    markRecapSeen: (month: string) => void;
}

export const useProfile = create<ProfileState>()(
    persist(
        (set) => ({
            accent: PROFILE_ACCENTS[0],
            avatar: null,
            bio: '',
            createdAt: Date.now(),
            displayName: '',
            markRecapSeen: (month) => set((s) => (s.seenRecaps.includes(month) ? s : { seenRecaps: [...s.seenRecaps, month] })),
            pinnedBadge: null,
            recaps: {},
            saveRecap: (recap) => set((s) => ({ recaps: { ...s.recaps, [recap.month]: recap } })),
            seenRecaps: [],
            update: (data) => set(data),
        }),
        {
            name: 'criol_profile',
            partialize: ({ accent, avatar, bio, createdAt, displayName, pinnedBadge, recaps, seenRecaps }) => ({
                accent, avatar, bio, createdAt, displayName, pinnedBadge, recaps, seenRecaps,
            }),
            version: 1,
        },
    ),
);

/** Recorta a imagem no centro e reduz para 512×512 (data URL JPEG). */
export async function imageToAvatar(file: Blob) {
    const bitmap = await createImageBitmap(file);
    const side = Math.min(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    canvas.getContext('2d')!.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, 512, 512);
    return canvas.toDataURL('image/jpeg', 0.88);
}
