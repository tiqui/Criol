import { createStore, keys, setMany } from 'idb-keyval';

/**
 * Traz os dados salvos com o nome antigo do app (Crify) para os nomes do Criol.
 *
 * - localStorage: "crify_*" vira "criol_*". Roda assim que este módulo é importado, antes de
 *   qualquer store ler o localStorage, por isso ele é o primeiro import do main.tsx.
 * - IndexedDB: cada banco "crify-*" é copiado para o "criol-*" correspondente (só se o novo
 *   estiver vazio) e o antigo é apagado. É assíncrono: o app só inicia depois.
 */

const OLD_PREFIX = 'crify_';
const NEW_PREFIX = 'criol_';
const DONE_KEY = 'criol_legacy_migrated';

const DATABASES: [oldName: string, newName: string, store: string][] = [
    ['crify-listening', 'criol-listening', 'listens'],
    ['crify-userdata', 'criol-userdata', 'userdata'],
    ['crify-library', 'criol-library', 'libraries'],
    ['crify-mixes', 'criol-mixes', 'mixes'],
    ['crify-lyrics', 'criol-lyrics', 'cache'],
    ['crify-folders', 'criol-folders', 'handles'],
    ['crify-local-art', 'criol-local-art', 'art'],
    ['crify-local-index', 'criol-local-index', 'index'],
];

const migrateLocalStorage = () => {
    try {
        const oldKeys: string[] = [];
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key?.startsWith(OLD_PREFIX)) oldKeys.push(key);
        }
        for (const key of oldKeys) {
            const value = localStorage.getItem(key);
            const newKey = NEW_PREFIX + key.slice(OLD_PREFIX.length);
            if (value !== null && localStorage.getItem(newKey) === null) localStorage.setItem(newKey, value);
            localStorage.removeItem(key);
        }
    } catch {
        // Sem localStorage (modo privado restrito): nada para migrar.
    }
};

migrateLocalStorage();

/** Lê tudo de um banco antigo. Devolve null se o banco não existe (sem criá-lo). */
const readOldDatabase = (name: string, storeName: string) =>
    new Promise<[IDBValidKey, unknown][] | null>((resolve) => {
        let request: IDBOpenDBRequest;
        try {
            request = indexedDB.open(name);
        } catch {
            resolve(null);
            return;
        }
        // Se precisou criar, o banco não existia: cancela para não deixar um banco vazio.
        request.onupgradeneeded = () => request.transaction?.abort();
        request.onerror = () => resolve(null);
        request.onblocked = () => resolve(null);
        request.onsuccess = () => {
            const db = request.result;
            if (!db.objectStoreNames.contains(storeName)) {
                db.close();
                resolve([]);
                return;
            }
            const tx = db.transaction(storeName, 'readonly');
            const store = tx.objectStore(storeName);
            const keysRequest = store.getAllKeys();
            const valuesRequest = store.getAll();
            tx.oncomplete = () => {
                db.close();
                resolve(keysRequest.result.map((key, i) => [key, valuesRequest.result[i]]));
            };
            tx.onerror = () => {
                db.close();
                resolve(null);
            };
        };
    });

const migrateDatabase = async ([oldName, newName, storeName]: (typeof DATABASES)[number]) => {
    const entries = await readOldDatabase(oldName, storeName);
    if (entries === null) return;
    if (entries.length) {
        const target = createStore(newName, storeName);
        if ((await keys(target)).length === 0) await setMany(entries, target);
    }
    indexedDB.deleteDatabase(oldName);
};

export const migrateLegacyDatabases = async () => {
    try {
        if (localStorage.getItem(DONE_KEY)) return;
    } catch {
        return;
    }
    if (typeof indexedDB === 'undefined') return;
    const results = await Promise.allSettled(DATABASES.map(migrateDatabase));
    const failed = results.filter((r) => r.status === 'rejected');
    if (failed.length) {
        console.error('Falha ao migrar dados do Crify', failed);
        return;
    }
    try {
        localStorage.setItem(DONE_KEY, '1');
    } catch {
        // ignora
    }
};
