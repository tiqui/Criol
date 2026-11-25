import { TFunction } from 'i18next';

import { msToMinutes } from './listening-stats';

/** "12 minutes", "1 minute" or "< 1 minute" (never "0 minutes" for something that was played). */
export const formatListenMinutes = (t: TFunction, ms: number, language?: string) => {
    const minutes = msToMinutes(ms);
    if (minutes === 0 && ms > 0) return t('recap.underOneMinute');
    return t('recap.minutesValue', {
        count: minutes,
        minutes: minutes.toLocaleString(language),
    });
};
