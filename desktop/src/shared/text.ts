/** Funções de texto usadas em todo o app. */

/** Minúsculas e sem acentos, para busca e comparação. */
export const normalizeText = (value: null | string | undefined) =>
    (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036F]/g, '')
        .toLowerCase()
        .trim();

// eslint-disable-next-line no-control-regex -- tags ID3v2.4 separam valores com NUL
const ARTIST_SEPARATORS = /\s*;\s*|\u0000|\s+\/\s+/;

/** "A; B" e "A / B" viram ["A", "B"]. "AC/DC" continua inteiro (sem espaços ao redor da barra). */
export const splitArtists = (value: null | string | undefined): string[] => {
    if (!value || !value.trim()) return [];
    const seen = new Set<string>();
    const out: string[] = [];
    for (const part of value.split(ARTIST_SEPARATORS)) {
        const name = part.trim();
        const key = normalizeText(name);
        if (name && !seen.has(key)) {
            seen.add(key);
            out.push(name);
        }
    }
    return out;
};

export const splitGenres = (value: null | string | string[] | undefined): string[] => {
    // eslint-disable-next-line no-control-regex -- tags ID3v2.4 separam valores com NUL
    const parts = Array.isArray(value) ? value : (value || '').split(/[;,\u0000]/);
    const seen = new Set<string>();
    const out: string[] = [];
    for (const part of parts) {
        const name = part.trim();
        const key = normalizeText(name);
        if (name && !seen.has(key)) {
            seen.add(key);
            out.push(name);
        }
    }
    return out;
};

/** Hash FNV-1a de 32 bits em hexadecimal. Rápido e estável entre plataformas. */
export const hash32 = (value: string) => {
    let h = 0x811c9dc5;
    for (let i = 0; i < value.length; i += 1) {
        h ^= value.charCodeAt(i);
        h = Math.imul(h, 0x01000193);
    }
    return (h >>> 0).toString(16).padStart(8, '0');
};

/** Id estável de 16 caracteres a partir de um texto. */
export const stableId = (value: string) => hash32(value) + hash32(`${value.length}:${value}`);

export const parseYear = (value: unknown): number => {
    if (typeof value === 'number') return value > 0 && value < 10000 ? Math.floor(value) : 0;
    const match = String(value || '').match(/\d{4}/);
    return match ? Number(match[0]) : 0;
};
