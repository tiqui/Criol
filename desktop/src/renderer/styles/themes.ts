/** Temas do app (os mesmos do Criol) e as variáveis CSS geradas a partir deles. */

export interface Palette {
    id: string;
    name: string;
    dark: boolean;
    background: string;
    backgroundAlt: string;
    surface: string;
    foreground: string;
    foregroundMuted: string;
    primary: string;
}

const p = (
    id: string,
    name: string,
    dark: boolean,
    background: string,
    backgroundAlt: string,
    surface: string,
    foreground: string,
    foregroundMuted: string,
    primary: string,
): Palette => ({ background, backgroundAlt, dark, foreground, foregroundMuted, id, name, primary, surface });

export const PALETTES: Palette[] = [
    p('default-dark', 'Padrão escuro', true, '#0c0c0c', '#080808', '#141414', '#e1e1e1', '#969696', '#3574fc'),
    p('default-light', 'Padrão claro', false, '#ebebeb', '#f0f0f0', '#e1e1e1', '#191919', '#505050', '#007aff'),
    p('nord', 'Nord', true, '#2e3440', '#252936', '#3b4252', '#eceff4', '#d8dee9', '#88c0d0'),
    p('dracula', 'Dracula', true, '#282a36', '#1e1f29', '#44475a', '#f8f8f2', '#bd93f9', '#bd93f9'),
    p('one-dark', 'One Dark', true, '#282c34', '#1e2128', '#373b46', '#abb2bf', '#98a1b2', '#61afef'),
    p('catppuccin-mocha', 'Catppuccin Mocha', true, '#181825', '#11111b', '#1e1e2e', '#cdd6f4', '#bac2de', '#89b4fa'),
    p('catppuccin-latte', 'Catppuccin Latte', false, '#eff1f5', '#e6e9ef', '#dce0e8', '#4c4f69', '#6c6f85', '#1e66f5'),
    p('tokyo-night', 'Tokyo Night', true, '#1a1b26', '#14151e', '#232433', '#c0caf5', '#a9b1d6', '#7dcfff'),
    p('gruvbox-dark', 'Gruvbox Dark', true, '#282828', '#1d2021', '#32302f', '#ebdbb2', '#bdae93', '#fabd2f'),
    p('rose-pine', 'Rosé Pine', true, '#191724', '#191724', '#1f1d2e', '#e0def4', '#908caa', '#ebbcba'),
    p('everforest-dark', 'Everforest Dark', true, '#232a2e', '#232a2e', '#343f38', '#d3c6aa', '#9da9a0', '#a7c080'),
    p('monokai', 'Monokai', true, '#272822', '#1e1f1c', '#32332d', '#f8f8f2', '#96927d', '#ae81ff'),
    p('night-owl', 'Night Owl', true, '#011627', '#00101c', '#0b2942', '#d6deeb', '#abb2bf', '#82aaff'),
    p('github-dark', 'GitHub Dark', true, '#0d1117', '#161b22', '#1e252e', '#c9d1d9', '#8b949e', '#58a6ff'),
    p('ayu-dark', 'Ayu Dark', true, '#1f2430', '#171b24', '#272e39', '#cbccc6', '#aaada6', '#73c0cb'),
    p('solarized-dark', 'Solarized Dark', true, '#002b36', '#073642', '#0e414e', '#93a1a1', '#657b83', '#268bd2'),
];

export const ACCENT_PRESETS = [
    '#3574fc', '#00a8ff', '#1db954', '#2ecc71', '#ffc107', '#ff8000',
    '#f03c3c', '#e91e63', '#ab47bc', '#7e57c2', '#00bcd4', '#ffffff',
];

export const paletteFor = (id: string) => PALETTES.find((palette) => palette.id === id) || PALETTES[0];

export const hexToRgb = (hex: string): [number, number, number] => {
    const clean = hex.replace('#', '');
    const full = clean.length === 3 ? clean.split('').map((c) => c + c).join('') : clean.slice(0, 6);
    const n = parseInt(full, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};

export const luminance = (hex: string) => {
    const [r, g, b] = hexToRgb(hex).map((v) => {
        const c = v / 255;
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const rgba = (hex: string, alpha: number) => {
    const [r, g, b] = hexToRgb(hex);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

/** Aplica o tema nas variáveis CSS do documento. */
export const applyTheme = (palette: Palette, accent: null | string) => {
    const primary = accent || palette.primary;
    const root = document.documentElement;
    const vars: Record<string, string> = {
        '--bg': palette.background,
        '--bg-alt': palette.backgroundAlt,
        '--border': rgba(palette.foreground, palette.dark ? 0.09 : 0.12),
        '--border-strong': rgba(palette.foreground, palette.dark ? 0.18 : 0.22),
        '--fg': palette.foreground,
        '--fg-muted': palette.foregroundMuted,
        '--fg-subtle': rgba(palette.foreground, 0.45),
        '--hover': rgba(palette.foreground, palette.dark ? 0.07 : 0.06),
        '--active': rgba(palette.foreground, palette.dark ? 0.12 : 0.1),
        '--on-primary': luminance(primary) > 0.5 ? '#111111' : '#ffffff',
        '--primary': primary,
        '--primary-soft': rgba(primary, 0.18),
        '--scrim': palette.dark ? 'rgba(0, 0, 0, 0.6)' : 'rgba(0, 0, 0, 0.35)',
        '--shadow': palette.dark ? '0 10px 30px rgba(0, 0, 0, 0.5)' : '0 10px 30px rgba(0, 0, 0, 0.16)',
        '--surface': palette.surface,
        '--surface-high': `color-mix(in srgb, ${palette.foreground} ${palette.dark ? 6 : 5}%, ${palette.surface})`,
    };
    for (const [key, value] of Object.entries(vars)) root.style.setProperty(key, value);
    root.dataset.theme = palette.dark ? 'dark' : 'light';
    root.style.colorScheme = palette.dark ? 'dark' : 'light';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', palette.background);
    void window.criolDesktop?.setTitleBarColors(`${palette.background}00`, palette.foreground);
};
