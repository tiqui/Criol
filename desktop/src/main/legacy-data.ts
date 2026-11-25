import { cpSync, existsSync, mkdirSync, readdirSync, renameSync, rmSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';

/** O que indica que a pasta do Criol já tem dados (e não precisa migrar). */
const DATA_MARKERS = ['IndexedDB', 'library', 'Local Storage', 'window.json'];

/** Pastas de cache e travas do Chromium que não precisam ir junto. */
const SKIP = new Set([
    'blob_storage',
    'Cache',
    'Code Cache',
    'Crashpad',
    'crify-library',
    'DawnGraphiteCache',
    'DawnWebGPUCache',
    'GPUCache',
    'lockfile',
    'Shared Dictionary',
    'SingletonCookie',
    'SingletonLock',
    'SingletonSocket',
]);

/**
 * Na primeira execução do Criol, copia a pasta de dados do Crify (nome antigo do app) para a
 * pasta nova, para a biblioteca, o histórico, o perfil e as configurações continuarem iguais.
 * A pasta antiga fica intocada. Precisa rodar antes do app ficar pronto.
 *
 * O Electron já cria a pasta padrão (vazia) ao iniciar, então "pasta nova" quer dizer sem
 * nenhum dado do app ainda, não pasta inexistente.
 */
export function migrateLegacyUserData(userData: string) {
    if (DATA_MARKERS.some((name) => existsSync(join(userData, name)))) return;
    const legacy = join(dirname(userData), basename(userData).replace(/^Criol/i, 'Crify'));
    if (legacy === userData || !existsSync(legacy)) return;
    const temp = `${userData}.migrando`;
    try {
        rmSync(temp, { force: true, recursive: true });
        cpSync(legacy, temp, { filter: (source) => !SKIP.has(basename(source)), recursive: true });
        mkdirSync(userData, { recursive: true });
        for (const entry of readdirSync(temp)) {
            const target = join(userData, entry);
            if (!existsSync(target)) renameSync(join(temp, entry), target);
        }
        rmSync(temp, { force: true, recursive: true });
    } catch (error) {
        console.error('Falha ao copiar os dados do Crify', error);
        rmSync(temp, { force: true, recursive: true });
    }
}
