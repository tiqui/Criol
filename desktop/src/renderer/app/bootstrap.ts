import i18n, { resolveLanguage } from '@/i18n';
import { loadListeningHistory, useListening } from '@/insights/listening-db';
import { ensureMixes } from '@/insights/mixes';
import { activateSource, useLibrary } from '@/library/store';
import { attachEngine, next, previous, resetForSource, restoreQueue, setVolume, skipBy, startQueuePersistence, togglePlay, usePlayer } from '@/player/store';
import { startTracking } from '@/player/tracker';
import { settings, useSettings } from '@/settings/store';
import { ensureBase, selectBase } from '@/sources/endpoint';
import { getActiveConfig, migrateOldSources, useSources } from '@/sources/store';
import { resolvePalette } from '@/styles/daylight';
import { applyTheme } from '@/styles/themes';

/** Liga tudo o que roda fora dos componentes: tema, idioma, player, histórico, biblioteca. */
export function bootstrap() {
    migrateOldSources();

    // Tema e idioma acompanham as configurações.
    const applyLook = () => {
        const s = settings();
        applyTheme(resolvePalette(s.themeId, s.themeFollowsDay), s.accent);
        const lang = resolveLanguage(s.language);
        if (i18n.language !== lang) void i18n.changeLanguage(lang);
        document.documentElement.lang = lang;
    };
    applyLook();
    useSettings.subscribe((s, prev) => {
        if (s.themeId !== prev.themeId || s.accent !== prev.accent || s.language !== prev.language || s.themeFollowsDay !== prev.themeFollowsDay) {
            applyLook();
        }
    });
    // Tema seguindo o dia: acompanha o relógio a cada minuto (e ao voltar para a janela).
    const followClock = () => settings().themeFollowsDay && applyTheme(resolvePalette(settings().themeId, true), settings().accent);
    setInterval(followClock, 60 * 1000);
    document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && followClock());

    attachEngine();
    startTracking();
    startQueuePersistence();
    void loadListeningHistory();

    // Fonte ativa: carrega a biblioteca e restaura a fila daquela fonte.
    let current: ReturnType<typeof getActiveConfig> | undefined;
    let queueRestored = false;
    const activate = () => {
        const config = getActiveConfig();
        if (config === current) return;
        const changedSource = config?.id !== current?.id;
        current = config;
        if (changedSource) {
            queueRestored = false;
            resetForSource();
        }
        void activateSource(config);
    };
    activate();
    useSources.subscribe(activate);

    // Servidor com dois endereços (ex.: rede local e Tailscale): testa de novo quando a rede muda
    // e de tempos em tempos, para voltar ao principal quando ele estiver de volta.
    const recheckServer = (force: boolean) => {
        const config = getActiveConfig();
        if (!config || config.type === 'local' || !config.fallbackUrl) return;
        void (force ? selectBase(config) : ensureBase(config));
    };
    recheckServer(true);
    window.addEventListener('online', () => recheckServer(true));
    (navigator as Navigator & { connection?: EventTarget }).connection?.addEventListener('change', () => recheckServer(true));
    setInterval(() => recheckServer(false), 2 * 60 * 1000);

    const refreshMixes = (force = false) => {
        const { library, loaded, sourceId, user } = useLibrary.getState();
        if (!loaded || !sourceId || library.isEmpty || !useListening.getState().loaded) return;
        void ensureMixes(library, user, useListening.getState().entries, sourceId, force);
    };

    useLibrary.subscribe((s, prev) => {
        if (s.loaded && s.sourceId && !queueRestored && !s.library.isEmpty) {
            queueRestored = true;
            restoreQueue(s.sourceId);
        }
        if (s.library !== prev.library) refreshMixes();
    });
    useListening.subscribe((s, prev) => {
        if (s.loaded && !prev.loaded) refreshMixes();
    });
    // A daylist muda ao longo do dia.
    setInterval(() => refreshMixes(), 10 * 60 * 1000);

    // Atalhos de teclado.
    window.addEventListener('keydown', (e) => {
        const target = e.target as HTMLElement;
        if (target.closest('input, textarea, select, [contenteditable="true"]')) return;
        const mod = e.ctrlKey || e.metaKey;
        if (e.code === 'Space' && !mod) {
            if (target.closest('button, [role="button"], [role="slider"]')) return;
            e.preventDefault();
            togglePlay();
        } else if (mod && e.key === 'ArrowRight') {
            e.preventDefault();
            void next(true);
        } else if (mod && e.key === 'ArrowLeft') {
            e.preventDefault();
            previous();
        } else if (mod && e.key === 'ArrowUp') {
            e.preventDefault();
            setVolume(usePlayer.getState().volume + 0.05);
        } else if (mod && e.key === 'ArrowDown') {
            e.preventDefault();
            setVolume(usePlayer.getState().volume - 0.05);
        } else if (e.shiftKey && e.key === 'ArrowRight') {
            skipBy(settings().skipForwardSeconds);
        } else if (e.shiftKey && e.key === 'ArrowLeft') {
            skipBy(-settings().skipBackSeconds);
        }
    });
}
