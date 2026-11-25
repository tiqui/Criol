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

