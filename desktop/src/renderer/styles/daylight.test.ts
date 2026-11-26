import { describe, expect, it } from 'vitest';

import { blendPalettes, dayNightPair, nightAmount, resolvePalette } from './daylight';
import { luminance, PALETTES } from './themes';

const at = (h: number, m = 0) => nightAmount(h * 60 + m);
const contrast = (a: string, b: string) => {
    const x = luminance(a);
    const y = luminance(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};

describe('tema seguindo o dia', () => {
    it('claro de manhã, escuro à noite, metade às 16h', () => {
        expect(at(8)).toBe(0);
        expect(at(11, 59)).toBe(0);
        expect(at(21)).toBe(1);
        expect(at(3)).toBe(1);
        expect(at(16)).toBeCloseTo(0.5, 3);
        expect(at(6)).toBeCloseTo(0.5, 3);
    });

    it('escurece sempre durante a tarde', () => {
        for (let m = 12 * 60 + 1; m <= 20 * 60; m++) expect(nightAmount(m)).toBeGreaterThanOrEqual(nightAmount(m - 1));
    });

    it('o tema escolhido é uma das pontas', () => {
        expect(dayNightPair('nord').map((p) => p.id)).toEqual(['default-light', 'nord']);
        expect(dayNightPair('catppuccin-latte').map((p) => p.id)).toEqual(['catppuccin-latte', 'default-dark']);
        expect(resolvePalette('nord', false).id).toBe('nord');
        expect(resolvePalette('nord', true, new Date(2026, 10, 26, 9)).id).toBe('default-light');
        expect(resolvePalette('nord', true, new Date(2026, 10, 26, 23)).id).toBe('nord');
    });

    it('o texto continua legível em todo o caminho', () => {
        for (const theme of PALETTES) {
            const [day, night] = dayNightPair(theme.id);
            for (let step = 1; step < 100; step++) {
                const p = blendPalettes(day, night, step / 100);
                expect(contrast(p.foreground, p.background), `${theme.id} ${step}`).toBeGreaterThanOrEqual(4.5);
                expect(contrast(p.foregroundMuted, p.background), `${theme.id} ${step}`).toBeGreaterThanOrEqual(3);
            }
        }
    });
});
