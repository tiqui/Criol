import type { CSSProperties } from 'react';
import type { IconType } from 'react-icons';
import { MdAllInclusive, MdAutoAwesome, MdBedtime, MdExplore, MdHistory, MdNightsStay, MdWbSunny, MdWbTwilight } from 'react-icons/md';

import type { Daypart } from '@/insights/listening-stats';
import { getDaypart } from '@/insights/listening-stats';
import type { Mix } from '@/insights/mixes';

import { Cover } from './cover';
import styles from './mix-cover.module.css';

/** Cores de cada período do dia (fundo, chão, orbe, borda do orbe), as mesmas do Criol Android. */
const PALETTES: Record<Daypart, { bg: string; floor: string; orb: string; edge: string }> = {
    afternoon: { bg: '#0a3f73', edge: '#6ab7ff', floor: '#59c3ff', orb: '#c9ecff' },
    dawn: { bg: '#070b22', edge: '#3c2d8f', floor: '#2a1b5c', orb: '#8f7bff' },
    morning: { bg: '#1b2f66', edge: '#ff8f5a', floor: '#f48c5b', orb: '#ffd98a' },
    night: { bg: '#0b0a2a', edge: '#6d3fd6', floor: '#3b1a78', orb: '#c7b2ff' },
};

const DAYPART_ICONS: Record<Daypart, IconType> = {
    afternoon: MdWbSunny,
    dawn: MdBedtime,
    morning: MdWbTwilight,
    night: MdNightsStay,
};

/** Mesmo degradê diagonal do Android (quase vertical, levemente inclinado). */
const diagonal = (...colors: string[]) => `linear-gradient(158deg, ${colors.join(', ')})`;

/** Cor fixa de um gênero (mesma conta do Android: hashCode do Java em HSL). */
export const genreColor = (genre: string) => {
    let hash = 0;
    for (const char of genre.toLowerCase()) hash = (Math.imul(hash, 31) + char.charCodeAt(0)) | 0;
    return `hsl(${(hash & 0x7fffffff) % 360} 55% 40%)`;
};

/** Cor de fundo da mix (cabeçalho da página da mix). */
export const mixColor = (mix: Mix) => {
    switch (mix.kind) {
        case 'daylist':
        case 'daypart':
            return PALETTES[mix.daypart || getDaypart(Date.now())].floor;
        case 'onRepeat':
            return '#2b2a8c';
        case 'repeatRewind':
            return '#0e5a55';
        case 'discover':
            return '#b0384a';
        default:
            return genreColor(mix.genre || '');
    }
};

function Glyph({ background, color, icon: GlyphIcon }: { background: string; color: string; icon: IconType }) {
    return (
        <div className={styles.glyph} style={{ background }}>
            <GlyphIcon color={color} />
        </div>
    );
}

function Art({ mix }: { mix: Mix }) {
    const daypart = mix.daypart || getDaypart(Date.now());
    const p = PALETTES[daypart];
    switch (mix.kind) {
        case 'daylist':
            return (
                <div className={styles.daylist} style={{ background: `radial-gradient(85% 85% at 50% 110%, ${p.floor}, ${p.bg})` }}>
                    <div className={styles.orb} style={{ '--orb': p.orb, '--edge': p.edge } as CSSProperties} />
                </div>
            );
        case 'daypart':
            return <Glyph background={diagonal(p.floor, p.bg)} color={p.orb} icon={DAYPART_ICONS[daypart]} />;
        case 'onRepeat':
            return <Glyph background={diagonal('#2b2a8c', '#1b1f5e')} color="#ff4fb1" icon={MdAllInclusive} />;
        case 'repeatRewind':
            return <Glyph background={diagonal('#0e5a55', '#12324f')} color="#ffc04d" icon={MdHistory} />;
        case 'discover':
            return <Glyph background={diagonal('#6a1b6f', '#e0533d', '#f7b33b')} color="#fff3d6" icon={MdExplore} />;
        default: {
            const color = genreColor(mix.genre || '');
            const covers = mix.coverIds.slice(0, 4);
            return (
                <div className={styles.collage} style={{ background: color }}>
                    {covers.length > 0 &&
                        [0, 1, 2, 3].map((i) => {
                            const id = covers[i] || covers[0];
                            return <Cover className={styles.tile} coverId={id} key={i} seed={id} size="sm" />;
                        })}
                    <div className={styles.fade} style={{ background: `linear-gradient(transparent, ${color} 45%, ${color})` }} />
                </div>
            );
        }
    }
}

/** Capa das mixes do "Feito para você", igual à do Criol Android. */
export function MixCover({ label, mix }: { mix: Mix; label: string }) {
    return (
        <div className={styles.mix}>
            <Art mix={mix} />
            <span className={styles.badge}>
                <MdAutoAwesome />
            </span>
            <span className={styles.label}>{label}</span>
        </div>
    );
}
