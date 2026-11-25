import type { LyricLine, Lyrics } from '../types';
import { cleanLyricsText } from './clean';

const TIME_TAG = /\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;
const META_TAG = /^\s*\[(ar|ti|al|au|by|length|offset|re|ve|#):[^\]]*\]\s*$/i;

/**
 * Lê uma letra em LRC (sincronizada) ou texto puro. Linhas com várias marcações de tempo
 * ("[00:10.00][01:20.00]refrão") viram uma linha para cada tempo. Retorna null quando não sobra
 * letra de verdade (só créditos, "instrumental" ou texto de exemplo).
 */
export const parseLyrics = (raw: null | string | undefined, source: string): Lyrics | null => {
    const text = cleanLyricsText(raw);
    if (!text) return null;

    const offsetMatch = text.match(/^\s*\[offset:\s*([+-]?\d+)\s*\]/im);
    const offset = offsetMatch ? Number(offsetMatch[1]) : 0;

    const timed: LyricLine[] = [];
    const plain: LyricLine[] = [];
    for (const rawLine of text.split(/\r?\n/)) {
        if (META_TAG.test(rawLine)) continue;
        const times: number[] = [];
        TIME_TAG.lastIndex = 0;
        let match: null | RegExpExecArray;
        while ((match = TIME_TAG.exec(rawLine))) {
            const frac = match[3] ? Number(match[3].padEnd(3, '0').slice(0, 3)) : 0;
            times.push(Number(match[1]) * 60_000 + Number(match[2]) * 1000 + frac);
        }
        // Marcações por palavra (<00:01.00>) de LRC estendido.
        const content = rawLine.replace(TIME_TAG, '').replace(/<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>/g, '').trim();
        if (times.length) {
            for (const time of times) timed.push({ text: content, time: Math.max(0, time - offset) });
        } else if (content || plain.length) {
            plain.push({ text: content, time: null });
        }
    }

    if (timed.length >= 2) {
        timed.sort((a, b) => (a.time ?? 0) - (b.time ?? 0));
        return { lines: timed, source, synced: true };
    }
    while (plain.length && !plain[plain.length - 1].text) plain.pop();
    if (!plain.some((line) => line.text)) return null;
    return { lines: plain, source, synced: false };
};

/** Índice da linha que está tocando agora (-1 antes da primeira). */
export const activeLineIndex = (lines: LyricLine[], positionMs: number) => {
    let low = 0;
    let high = lines.length - 1;
    let found = -1;
    while (low <= high) {
        const mid = (low + high) >> 1;
        if ((lines[mid].time ?? 0) <= positionMs) {
            found = mid;
            low = mid + 1;
        } else high = mid - 1;
    }
    return found;
};
