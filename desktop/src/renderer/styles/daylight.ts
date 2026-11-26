/**
 * Tema que segue o dia: claro de manhã, vai escurecendo durante a tarde até o tema escuro
 * à noite, e volta a clarear no amanhecer. O Android usa a mesma conta
 * (android/.../ui/theme/Daylight.kt), para as duas versões mudarem juntas.
 */
import { hexToRgb, luminance, type Palette, paletteFor } from './themes';

const DAWN_START = 5 * 60;
const DAWN_END = 7 * 60;
const DUSK_START = 12 * 60;
const DUSK_END = 20 * 60;

/** Fundo em que o texto troca de escuro para claro: aí preto e branco têm o mesmo contraste (~4,6:1). */
const TEXT_FLIP = 0.18;

const smooth = (x: number) => {
    const t = Math.min(1, Math.max(0, x));
    return t * t * (3 - 2 * t);
};

/** Quanto de "noite" no minuto do dia (0 a 1439): 0 = tema claro, 1 = tema escuro. */
export function nightAmount(minuteOfDay: number): number {
    const m = ((minuteOfDay % 1440) + 1440) % 1440;
    if (m < DAWN_START) return 1;
    if (m < DAWN_END) return 1 - smooth((m - DAWN_START) / (DAWN_END - DAWN_START));
    if (m < DUSK_START) return 0;
    if (m < DUSK_END) return smooth((m - DUSK_START) / (DUSK_END - DUSK_START));
    return 1;
}

/** As duas pontas: o tema escolhido é uma delas (escuro = noite; claro = dia). */
export function dayNightPair(themeId: string): [day: Palette, night: Palette] {
    const chosen = paletteFor(themeId);
    return chosen.dark ? [paletteFor('default-light'), chosen] : [chosen, paletteFor('default-dark')];
}

const toHex = (rgb: number[]) => `#${rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`;

/** Mistura em sRGB (a mesma conta do Android). */
const mix = (a: string, b: string, t: number) => {
    const x = hexToRgb(a);
    const y = hexToRgb(b);
    return toHex(x.map((v, i) => v + (y[i] - v) * t));
};

const contrast = (a: string, b: string) => {
    const x = luminance(a);
    const y = luminance(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};

/** Leva o texto para o preto/branco só o necessário para ter o contraste mínimo. */
const readable = (fg: string, bg: string, min: number, toward: string) => {
    let color = fg;
    for (let step = 1; step <= 20 && contrast(color, bg) < min; step++) color = mix(fg, toward, step / 20);
    return color;
};

export function blendPalettes(day: Palette, night: Palette, t: number): Palette {
    if (t <= 0) return day;
    if (t >= 1) return night;
    const background = mix(day.background, night.background, t);
    const dark = luminance(background) < TEXT_FLIP;
    const text = dark ? night : day;
    const extreme = dark ? '#ffffff' : '#000000';
    return {
        background,
        backgroundAlt: mix(day.backgroundAlt, night.backgroundAlt, t),
        dark,
        foreground: readable(text.foreground, background, 4.5, extreme),
        foregroundMuted: readable(text.foregroundMuted, background, 3, extreme),
        id: text.id,
        name: text.name,
        primary: mix(day.primary, night.primary, t),
        surface: mix(day.surface, night.surface, t),
    };
}

export const minuteOfDay = (date = new Date()) => date.getHours() * 60 + date.getMinutes();

/** Paleta em uso agora: a do tema, ou a mistura do momento quando segue o dia. */
export function resolvePalette(themeId: string, followDay: boolean, date = new Date()): Palette {
    if (!followDay) return paletteFor(themeId);
    const [day, night] = dayNightPair(themeId);
    return blendPalettes(day, night, nightAmount(minuteOfDay(date)));
}
