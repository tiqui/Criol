import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { randomId } from '@/lib/md5';

import { createJellyfinSource } from './jellyfin';
import { createDesktopLocalSource } from './local-desktop';
import { createWebLocalSource } from './local-web';
import { createSubsonicSource } from './subsonic';
import type { MusicSource, SourceConfig } from './types';

interface SourcesState {
    sources: SourceConfig[];
    activeId: null | string;
    add: (config: Omit<SourceConfig, 'createdAt' | 'id'> & { id?: string }) => SourceConfig;
    update: (id: string, patch: Partial<SourceConfig>) => void;
    remove: (id: string) => void;
    setActive: (id: null | string) => void;
}

export const useSources = create<SourcesState>()(
    persist(
        (set, get) => ({
            activeId: null,
            add: (input) => {
                const config: SourceConfig = { ...input, createdAt: Date.now(), id: input.id || randomId(12) };
                set({ activeId: config.id, sources: [...get().sources, config] });
                return config;
            },
            remove: (id) => {
                const sources = get().sources.filter((s) => s.id !== id);
                set({ activeId: get().activeId === id ? sources[0]?.id || null : get().activeId, sources });
            },
            setActive: (id) => set({ activeId: id }),
            sources: [],
            update: (id, patch) => set({ sources: get().sources.map((s) => (s.id === id ? { ...s, ...patch } : s)) }),
        }),
        { name: 'criol_sources', partialize: ({ activeId, sources }) => ({ activeId, sources }), version: 1 },
    ),
);

const instances = new Map<string, { config: SourceConfig; source: MusicSource }>();

/** Cliente da fonte (recriado quando a configuração muda). */
export const getSource = (config: SourceConfig): MusicSource => {
    const cached = instances.get(config.id);
    if (cached && cached.config === config) return cached.source;
    const source =
        config.type === 'subsonic'
            ? createSubsonicSource(config)
            : config.type === 'jellyfin'
              ? createJellyfinSource(config)
              : window.criolDesktop
                ? createDesktopLocalSource(config)
                : createWebLocalSource(config);
    instances.set(config.id, { config, source });
    return source;
};

export const getActiveConfig = () => {
    const { activeId, sources } = useSources.getState();
    return sources.find((s) => s.id === activeId) || null;
};

export const getActiveSource = () => {
    const config = getActiveConfig();
    return config ? getSource(config) : null;
};

export const useActiveConfig = () => useSources((state) => state.sources.find((s) => s.id === state.activeId) || null);

/**
 * Traz os servidores e pastas configurados no Crify 1.x (mesmo perfil do app), uma única vez.
 * Os ids são mantidos para o histórico de escuta continuar ligado às mesmas músicas.
 */
export const migrateOldSources = () => {
    try {
        if (localStorage.getItem('criol_sources_migrated')) return;
        localStorage.setItem('criol_sources_migrated', '1');
        if (useSources.getState().sources.length) return;
        const raw = localStorage.getItem('store_authentication');
        if (!raw) return;
        const parsed = JSON.parse(raw) as { state?: { currentServer?: { id?: string }; serverList?: Record<string, Record<string, string>> } };
        const list = Object.values(parsed.state?.serverList || {});
        const migrated: SourceConfig[] = [];
        for (const old of list) {
            const base = { createdAt: Date.now(), id: old.id, name: old.name || 'Servidor' };
            if (old.type === 'local' && old.url && window.criolDesktop) {
                migrated.push({ ...base, path: old.url, type: 'local' });
            } else if (old.type === 'navidrome' || old.type === 'subsonic') {
                const creds = new URLSearchParams(old.type === 'navidrome' ? old.ndCredential || old.credential : old.credential);
                const token = creds.get('t');
                const salt = creds.get('s');
                if (!token || !salt) continue;
                migrated.push({ ...base, subsonic: { salt, token }, type: 'subsonic', url: old.url, username: old.username || creds.get('u') || '' });
            } else if (old.type === 'jellyfin' && old.credential && old.userId) {
                migrated.push({ ...base, jellyfin: { token: old.credential, userId: old.userId }, type: 'jellyfin', url: old.url, username: old.username });
            }
        }
        if (!migrated.length) return;
        const current = parsed.state?.currentServer?.id;
        useSources.setState({ activeId: migrated.find((s) => s.id === current)?.id || migrated[0].id, sources: migrated });
    } catch {
        /* nada para migrar */
    }
};

/** Servidor pré-configurado na versão web (variáveis do Docker). */
export interface WebConfig {
    serverUrl?: string;
    serverType?: string;
    serverName?: string;
    serverLock?: boolean;
}

export const webConfig = (): WebConfig => {
    const raw = (window as unknown as { CRIOL_CONFIG?: Record<string, unknown> }).CRIOL_CONFIG || {};
    const text = (key: string) => (typeof raw[key] === 'string' && (raw[key] as string).trim() ? (raw[key] as string).trim() : undefined);
    return {
        serverLock: raw.serverLock === true || raw.serverLock === 'true',
        serverName: text('serverName'),
        serverType: text('serverType'),
        serverUrl: text('serverUrl'),
    };
};
