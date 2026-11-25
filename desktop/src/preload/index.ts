import { contextBridge, ipcRenderer } from 'electron';

import type { CriolDesktopApi } from './api.d';

const api: CriolDesktopApi = {
    appInfo: () => ipcRenderer.invoke('app:info'),
    indexLyricsFolders: (folders) => ipcRenderer.invoke('lyrics:indexFolders', folders),
    loadLocal: (sourceId, root) => ipcRenderer.invoke('local:load', sourceId, root),
    localEmbeddedLyrics: (sourceId, songId) => ipcRenderer.invoke('local:embeddedLyrics', sourceId, songId),
    onLocalProgress: (callback) => {
        const listener = (_event: unknown, sourceId: string, progress: Parameters<typeof callback>[1]) =>
            callback(sourceId, progress);
        ipcRenderer.on('local:progress', listener);
        return () => ipcRenderer.removeListener('local:progress', listener);
    },
    onMaximized: (callback) => {
        const listener = (_event: unknown, value: boolean) => callback(value);
        ipcRenderer.on('window:maximized', listener);
        return () => ipcRenderer.removeListener('window:maximized', listener);
    },
    openExternal: (url) => ipcRenderer.invoke('app:openExternal', url),
    pickFolder: (title) => ipcRenderer.invoke('dialog:pickFolder', title),
    platform: process.platform,
    readLyricsFile: (path) => ipcRenderer.invoke('lyrics:readFile', path),
    removeLocal: (sourceId) => ipcRenderer.invoke('local:remove', sourceId),
    scanLocal: (sourceId, root) => ipcRenderer.invoke('local:scan', sourceId, root),
    setTitleBarColors: (color, symbolColor) => ipcRenderer.invoke('window:setTitleBarColors', color, symbolColor),
};

contextBridge.exposeInMainWorld('criolDesktop', api);
