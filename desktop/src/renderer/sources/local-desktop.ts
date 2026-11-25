import type { MusicSource, SourceConfig } from './types';

/** Pasta do computador no app desktop: o processo principal lê os arquivos. */
export function createDesktopLocalSource(config: SourceConfig): MusicSource {
    const api = window.criolDesktop!;
    const root = config.path || '';

    return {
        capabilities: { scrobble: false, serverFavorites: false, serverPlaylists: false, serverRatings: false },
        config,

        async loadCached() {
            const result = await api.loadLocal(config.id, root);
            return result ? { songs: result.songs } : null;
        },

        async sync(onProgress) {
            const off = api.onLocalProgress((sourceId, progress) => {
                if (sourceId === config.id) onProgress({ done: progress.done, label: progress.phase, total: progress.total });
            });
            try {
                const result = await api.scanLocal(config.id, root);
                return { songs: result.songs };
            } finally {
                off();
            }
        },

        async streamUrl(song) {
            return `criol-media://song/${encodeURIComponent(config.id)}/${encodeURIComponent(song.id)}`;
        },

        coverUrl(coverId) {
            return `criol-media://art/${encodeURIComponent(config.id)}/${encodeURIComponent(coverId)}`;
        },

        async lyrics(song) {
            if (song.lyricsPath) {
                const text = await api.readLyricsFile(song.lyricsPath);
                if (text?.trim()) return text;
            }
            if (song.hasEmbeddedLyrics) return api.localEmbeddedLyrics(config.id, song.id);
            return null;
        },

        async dispose() {
            await api.removeLocal(config.id);
        },
    };
}
