import type { TFunction } from 'i18next';

import i18n from '@/i18n';

import type { Daypart } from './listening-stats';
import { getDaypart } from './listening-stats';
import type { Mix } from './mixes';

export interface MixText {
    title: string;
    description: string;
    coverLabel: string;
}

const capitalize = (value: string) => (value ? value[0].toLocaleUpperCase(i18n.language) + value.slice(1) : value);

export const weekdayName = (day: number) => {
    // 2023-01-01 foi um domingo.
    const date = new Date(2023, 0, 1 + day);
    return new Intl.DateTimeFormat(i18n.language, { weekday: 'long' }).format(date).toLocaleLowerCase(i18n.language);
};

/** Título, descrição e texto da capa de cada mix. */
export function mixText(mix: Mix, t: TFunction): MixText {
    const daypart: Daypart = mix.daypart || getDaypart(Date.now());
    switch (mix.kind) {
        case 'daylist': {
            const tags = mix.tags.map((tag) => tag.toLocaleLowerCase(i18n.language)).join(' ');
            return {
                coverLabel: 'daylist',
                description: t('mixes.daylistDescription'),
                title: `daylist • ${tags} ${weekdayName(mix.weekday)} ${t(`mixes.dayparts.${daypart}.phrase`)}`.replace(/\s+/g, ' ').trim(),
            };
        }
        case 'daypart': {
            const genres = mix.tags.slice(0, 2).join(', ');
            return {
                coverLabel: t(`mixes.dayparts.${daypart}.label`),
                description: genres ? t(`mixes.dayparts.${daypart}.description`, { genres }) : t('mixes.daypartFallback'),
                title: t('mixes.daypartTitle', { daypart: t(`mixes.dayparts.${daypart}.label`) }),
            };
        }
        case 'discover':
            return { coverLabel: t('mixes.discover'), description: t('mixes.discoverDescription'), title: t('mixes.discover') };
        case 'onRepeat':
            return { coverLabel: t('mixes.onRepeat'), description: t('mixes.onRepeatDescription'), title: t('mixes.onRepeat') };
        case 'repeatRewind':
            return { coverLabel: t('mixes.repeatRewind'), description: t('mixes.repeatRewindDescription'), title: t('mixes.repeatRewind') };
        default: {
            const genre = capitalize(mix.genre || '');
            return { coverLabel: genre, description: t('mixes.genreDescription', { genre: mix.genre }), title: t('mixes.genreTitle', { genre }) };
        }
    }
}

/** Cores da capa de cada tipo de mix. */
export const mixGradient = (mix: Mix) => {
    const daypart = mix.daypart || getDaypart(Date.now());
    switch (mix.kind) {
        case 'daylist':
            return daypart === 'morning'
                ? 'linear-gradient(135deg, #ffb36b, #ff5e8a)'
                : daypart === 'afternoon'
                  ? 'linear-gradient(135deg, #ffd15c, #ff7a3d)'
                  : daypart === 'night'
                    ? 'linear-gradient(135deg, #5a5cff, #c04bff)'
                    : 'linear-gradient(135deg, #1d2b64, #6a3fb5)';
        case 'daypart':
            return daypart === 'morning' ? 'linear-gradient(135deg, #f7b733, #fc4a1a)' : daypart === 'afternoon' ? 'linear-gradient(135deg, #2fb8ff, #1b6bd8)' : 'linear-gradient(135deg, #3a2d8f, #9d4bdb)';
        case 'onRepeat':
            return 'linear-gradient(135deg, #ff4fb1, #8e2de2)';
        case 'repeatRewind':
            return 'linear-gradient(135deg, #14b8a6, #0f5f8a)';
        case 'discover':
            return 'linear-gradient(135deg, #1db954, #0b6b3a)';
        default: {
            let h = 0;
            for (const c of mix.genre || '') h = (h * 31 + c.charCodeAt(0)) % 360;
            return `linear-gradient(135deg, hsl(${h} 70% 50%), hsl(${(h + 50) % 360} 65% 30%))`;
        }
    }
};
