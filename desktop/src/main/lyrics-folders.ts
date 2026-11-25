import { promises as fs } from 'node:fs';
import { join, resolve, sep } from 'node:path';

import { createLyricsEntry, isLyricFile, type LyricsIndexEntry } from '@shared/lyrics/folder-match';

/** Pastas só de letras (.lrc/.txt) escolhidas em Configurações > Letras. */

let allowedRoots: string[] = [];

async function readHeader(path: string) {
    const handle = await fs.open(path, 'r');
    try {
        const buffer = Buffer.alloc(2048);
        const { bytesRead } = await handle.read(buffer, 0, buffer.length, 0);
        return buffer.subarray(0, bytesRead).toString('utf8');
    } finally {
        await handle.close();
    }
}

export async function indexLyricsFolders(folders: string[]): Promise<LyricsIndexEntry[]> {
    allowedRoots = folders.map((folder) => resolve(folder));
    const out: LyricsIndexEntry[] = [];
    for (const folder of folders) {
        const stack = [folder];
        while (stack.length) {
            const dir = stack.pop()!;
            let entries: import('node:fs').Dirent[];
            try {
                entries = await fs.readdir(dir, { withFileTypes: true });
            } catch {
                continue;
            }
            for (const entry of entries) {
                const abs = join(dir, entry.name);
                if (entry.isDirectory()) stack.push(abs);
                else if (entry.isFile() && isLyricFile(entry.name)) {
                    try {
                        const st = await fs.stat(abs);
                        const header = entry.name.toLowerCase().endsWith('.txt') ? '' : await readHeader(abs);
                        out.push(createLyricsEntry(abs, folder, st.mtimeMs, header));
                    } catch {
                        /* ignore */
                    }
                }
            }
        }
    }
    return out;
}

/** Lê um arquivo de letra, só se estiver dentro de uma pasta de letras ou da biblioteca. */
export async function readLyricsFile(path: string, extraRoots: string[]): Promise<null | string> {
    const full = resolve(path);
    const roots = [...allowedRoots, ...extraRoots.map((root) => resolve(root))];
    if (!roots.some((root) => full === root || full.startsWith(root.endsWith(sep) ? root : root + sep))) return null;
    try {
        const raw = await fs.readFile(full);
        // UTF-8 com ou sem BOM; arquivos antigos em Latin-1 caem no fallback.
        const text = raw.toString('utf8').replace(/^\uFEFF/, '');
        return text.includes('\uFFFD') ? raw.toString('latin1') : text;
    } catch {
        return null;
    }
}
