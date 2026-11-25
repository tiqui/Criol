import { hash32 } from '@shared/text';

import { resolveIdbArt } from '@/sources/local-web';

/** Cor dominante de uma capa (para os cabeçalhos coloridos), com cache. */

const cache = new Map<string, Promise<null | string>>();
const STORAGE_KEY = 'criol_cover_colors';
let stored: Record<string, string> | null = null;

const loadStored = () => {
    if (stored) return stored;
    try {
        stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') as Record<string, string>;
    } catch {
        stored = {};
    }
    return stored;
};

let saveTimer: ReturnType<typeof setTimeout> | undefined;
const persist = () => {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
        const entries = Object.entries(loadStored());
        // Guarda só as 2000 mais recentes.
        const trimmed = Object.fromEntries(entries.slice(-2000));
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
        } catch {
            /* sem espaço */
        }
    }, 1000);
};

const rgbToHsl = (r: number, g: number, b: number) => {
    r /= 255;
    g /= 255;
    b /= 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const l = (max + min) / 2;
    if (max === min) return [0, 0, l];
    const d = max - min;
    const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    const h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [h * 60, s, l];
};

const extract = (url: string) =>
    new Promise<null | string>((resolve) => {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.decoding = 'async';
        img.onload = () => {
            try {
                const canvas = document.createElement('canvas');
                canvas.width = 24;
                canvas.height = 24;
                const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
                ctx.drawImage(img, 0, 0, 24, 24);
                const data = ctx.getImageData(0, 0, 24, 24).data;
                // Média ponderada pela saturação: cores vivas pesam mais que cinzas.
                let r = 0;
                let g = 0;
                let b = 0;
                let total = 0;
                for (let i = 0; i < data.length; i += 4) {
                    const [, s, l] = rgbToHsl(data[i], data[i + 1], data[i + 2]);
                    const w = 0.15 + s * (1 - Math.abs(l - 0.5) * 1.6);
                    if (w <= 0) continue;
                    r += data[i] * w;
                    g += data[i + 1] * w;
                    b += data[i + 2] * w;
                    total += w;
                }
                if (!total) return resolve(null);
                const [h, s] = rgbToHsl(r / total, g / total, b / total);
                // Escurece/clareia para servir de fundo com texto claro por cima.
                resolve(`hsl(${Math.round(h)} ${Math.round(Math.min(0.75, s) * 100)}% 34%)`);
            } catch {
                resolve(null);
            }
        };
        img.onerror = () => resolve(null);
        img.src = url;
    });

export const coverColor = (url: null | string): Promise<null | string> => {
    if (!url) return Promise.resolve(null);
    const key = hash32(url.replace(/([?&])(t|s|u|api_key)=[^&]*/g, ''));
    const known = loadStored()[key];
    if (known) return Promise.resolve(known);
    let promise = cache.get(key);
    if (!promise) {
        promise = (async () => {
            const real = url.startsWith('idb-art:') ? await resolveIdbArt(url) : url;
            const color = real ? await extract(real) : null;
            if (color) {
                loadStored()[key] = color;
                persist();
            }
            return color;
        })();
        cache.set(key, promise);
    }
    return promise;
};

/** Cor estável a partir de um texto (fundo de quem não tem capa). */
export const seedColor = (seed: string) => {
    const n = parseInt(hash32(seed || 'criol'), 16);
    return `hsl(${n % 360} 45% 32%)`;
};
