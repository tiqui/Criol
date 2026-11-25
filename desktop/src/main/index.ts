import { app, BrowserWindow, dialog, ipcMain, net, protocol, screen, shell } from 'electron';
import { promises as fs } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

import { migrateLegacyUserData } from './legacy-data';
import { indexLyricsFolders, readLyricsFile } from './lyrics-folders';
import {
    artDir,
    fileResponse,
    loadLocalLibrary,
    localSongPath,
    readEmbeddedLyrics,
    removeLocalLibrary,
    scanLocalLibrary,
} from './local-library';

const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged;
if (isDev) app.setPath('userData', `${app.getPath('userData')}-dev`);
migrateLegacyUserData(app.getPath('userData'));

protocol.registerSchemesAsPrivileged([
    {
        privileges: { bypassCSP: true, corsEnabled: true, secure: true, standard: true, stream: true, supportFetchAPI: true },
        scheme: 'criol-media',
    },
]);

let mainWindow: BrowserWindow | null = null;
const localRoots = new Map<string, string>();

const stateFile = () => join(app.getPath('userData'), 'window.json');

interface WindowState {
    x?: number;
    y?: number;
    width: number;
    height: number;
    maximized?: boolean;
}

async function readWindowState(): Promise<WindowState> {
    try {
        const state = JSON.parse(await fs.readFile(stateFile(), 'utf8')) as WindowState;
        const visible = screen.getAllDisplays().some((d) => {
            const b = d.workArea;
            return state.x !== undefined && state.y !== undefined && state.x < b.x + b.width && state.x + 100 > b.x && state.y >= b.y - 20 && state.y < b.y + b.height;
        });
        return visible ? state : { height: state.height, width: state.width };
    } catch {
        return { height: 820, width: 1320 };
    }
}

async function createWindow() {
    const state = await readWindowState();
    const isMac = process.platform === 'darwin';
    mainWindow = new BrowserWindow({
        backgroundColor: '#0c0c0c',
        height: state.height,
        minHeight: 560,
        minWidth: 360,
        show: false,
        title: 'Criol',
        titleBarStyle: 'hidden',
        ...(isMac ? { trafficLightPosition: { x: 14, y: 12 } } : { titleBarOverlay: { color: '#00000000', height: 40, symbolColor: '#e1e1e1' } }),
        icon: join(__dirname, '../renderer/icon.png'),
        webPreferences: {
            contextIsolation: true,
            preload: join(__dirname, '../preload/index.cjs'),
            sandbox: true,
        },
        width: state.width,
        x: state.x,
        y: state.y,
    });
    if (state.maximized) mainWindow.maximize();

    // Mostra assim que a página pintar; se o evento não vier, mostra ao terminar de carregar.
    const reveal = () => {
        if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) mainWindow.show();
    };
    mainWindow.once('ready-to-show', reveal);
    mainWindow.webContents.once('did-finish-load', () => setTimeout(reveal, 150));
    setTimeout(reveal, 4000);

    let saveTimer: NodeJS.Timeout | undefined;
    const saveState = () => {
        clearTimeout(saveTimer);
        saveTimer = setTimeout(() => {
            if (!mainWindow || mainWindow.isDestroyed()) return;
            const bounds = mainWindow.getNormalBounds();
            void fs.writeFile(stateFile(), JSON.stringify({ ...bounds, maximized: mainWindow.isMaximized() }));
        }, 400);
    };
    mainWindow.on('resize', saveState);
    mainWindow.on('move', saveState);
    mainWindow.on('maximize', () => mainWindow?.webContents.send('window:maximized', true));
    mainWindow.on('unmaximize', () => mainWindow?.webContents.send('window:maximized', false));

    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        if (/^https?:\/\//.test(url)) void shell.openExternal(url);
        return { action: 'deny' };
    });

    if (isDev && process.env.ELECTRON_RENDERER_URL) {
        await mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
    } else {
        await mainWindow.loadFile(join(__dirname, '../renderer/index.html'));
    }
}

function registerMediaProtocol() {
    // criol-media://song/<fonte>/<música>  e  criol-media://art/<fonte>/<álbum>
    protocol.handle('criol-media', async (request) => {
        const url = new URL(request.url);
        const [sourceId, itemId] = url.pathname.split('/').filter(Boolean).map(decodeURIComponent);
        if (url.host === 'song') {
            const path = localSongPath(sourceId, itemId);
            if (!path) return new Response('Not found', { status: 404 });
            return fileResponse(path, request.headers.get('range'));
        }
        if (url.host === 'art') {
            const file = join(artDir(sourceId), `${itemId.replace(/[^\w-]/g, '')}.jpg`);
            const response = await net.fetch(pathToFileURL(file).toString()).catch(() => null);
            if (!response || !response.ok) return new Response('Not found', { status: 404 });
            return new Response(response.body, {
                headers: { 'Cache-Control': 'max-age=31536000', 'Content-Type': 'image/jpeg' },
            });
        }
        return new Response('Not found', { status: 404 });
    });
}

function registerIpc() {
    ipcMain.handle('dialog:pickFolder', async (_event, title?: string) => {
        if (!mainWindow) return null;
        const result = await dialog.showOpenDialog(mainWindow, { properties: ['openDirectory'], title });
        return result.canceled ? null : result.filePaths[0] || null;
    });

    ipcMain.handle('local:load', async (_event, sourceId: string, root: string) => {
        localRoots.set(sourceId, root);
        return loadLocalLibrary(sourceId, root);
    });

    ipcMain.handle('local:scan', async (event, sourceId: string, root: string) => {
        localRoots.set(sourceId, root);
        return scanLocalLibrary(sourceId, root, (progress) => event.sender.send('local:progress', sourceId, progress));
    });

    ipcMain.handle('local:remove', async (_event, sourceId: string) => {
        localRoots.delete(sourceId);
        await removeLocalLibrary(sourceId);
    });

    ipcMain.handle('local:embeddedLyrics', (_event, sourceId: string, songId: string) => readEmbeddedLyrics(sourceId, songId));

    ipcMain.handle('lyrics:indexFolders', (_event, folders: string[]) => indexLyricsFolders(folders));
    ipcMain.handle('lyrics:readFile', (_event, path: string) => readLyricsFile(path, [...localRoots.values()]));

    ipcMain.handle('window:setTitleBarColors', (_event, color: string, symbolColor: string) => {
        if (process.platform === 'darwin' || !mainWindow) return;
        try {
            mainWindow.setTitleBarOverlay({ color, height: 40, symbolColor });
        } catch {
            /* Linux sem suporte a overlay */
        }
        mainWindow.setBackgroundColor(color.length === 9 ? `#${color.slice(7)}${color.slice(1, 7)}` : color);
    });

    ipcMain.handle('app:info', () => ({ platform: process.platform, version: app.getVersion() }));
    ipcMain.handle('app:openExternal', (_event, url: string) => {
        if (/^https?:\/\//.test(url)) void shell.openExternal(url);
    });
}

if (!app.requestSingleInstanceLock()) {
    app.quit();
} else {
    app.on('second-instance', () => {
        if (!mainWindow) return;
        if (mainWindow.isMinimized()) mainWindow.restore();
        mainWindow.focus();
    });

    app.whenReady().then(async () => {
        app.setAppUserModelId('app.criol');
        registerMediaProtocol();
        registerIpc();
        await createWindow();
        app.on('activate', () => {
            if (BrowserWindow.getAllWindows().length === 0) void createWindow();
        });
    });

    app.on('window-all-closed', () => {
        if (process.platform !== 'darwin') app.quit();
    });
}
