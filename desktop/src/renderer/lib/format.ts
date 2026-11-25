import i18n from '@/i18n';

export const formatDuration = (ms: number) => {
    const total = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
};

/** "1 h 20 min", "45 min". */
export const formatLongDuration = (ms: number) => {
    const totalMin = Math.round(ms / 60000);
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    if (h > 0 && m > 0) return `${h} h ${m} min`;
    if (h > 0) return `${h} h`;
    return `${m} min`;
};

export const formatNumber = (n: number) => n.toLocaleString(i18n.language);

export const songCount = (n: number) => i18n.t('common.songCount', { count: n, formatted: formatNumber(n) });
export const albumCount = (n: number) => i18n.t('common.albumCount', { count: n, formatted: formatNumber(n) });

export const formatDate = (ms: number, opts: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' }) =>
    new Intl.DateTimeFormat(i18n.language, opts).format(new Date(ms));

export const relativeTime = (ms: number) => {
    const diff = Date.now() - ms;
    const rtf = new Intl.RelativeTimeFormat(i18n.language, { numeric: 'auto' });
    const min = Math.round(diff / 60000);
    if (min < 1) return rtf.format(0, 'minute');
    if (min < 60) return rtf.format(-min, 'minute');
    const h = Math.round(min / 60);
    if (h < 24) return rtf.format(-h, 'hour');
    const d = Math.round(h / 24);
    if (d < 30) return rtf.format(-d, 'day');
    return formatDate(ms);
};
