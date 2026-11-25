import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams } from 'react-router';

import { BarStrip } from '@/components/bar-strip';
import { Cover } from '@/components/cover';
import { Page, Section } from '@/components/page';
import { Empty } from '@/components/ui';
import { formatListenMinutes } from '@/insights/format-minutes';
import { monthKey, useListening } from '@/insights/listening-db';
import { getMonthRange, type RankedItem } from '@/insights/listening-stats';
import { buildMonthlyRecap, formatMonth } from '@/insights/monthly-recap';
import { useProfile } from '@/insights/profile-store';
import { useLibrary } from '@/library/store';
import { formatDate, formatNumber } from '@/lib/format';

import styles from './recap.module.css';

function List({ items, round }: { items: RankedItem[]; round?: boolean }) {
    const { t, i18n } = useTranslation();
    const library = useLibrary((s) => s.library);
    return (
        <ol className={styles.list}>
            {items.map((item, i) => (
                <li key={`${item.id}${item.name}`}>
                    <span className={styles.rank}>{i + 1}</span>
                    <Cover coverId={round ? library.artistByName(item.name)?.coverId || null : item.imageId} icon={round ? 'user' : 'music'} round={round} seed={item.id || item.name} size="sm" />
                    <div className={styles.text}>
                        <strong className="truncate">{item.name}</strong>
                        <span className="truncate">{[item.subtitle, formatListenMinutes(t, item.listenedMs, i18n.language)].filter(Boolean).join(' · ')}</span>
                    </div>
                </li>
            ))}
        </ol>
    );
}

export function RecapScreen() {
    const { t, i18n } = useTranslation();
    const { month = monthKey(new Date()) } = useParams();
    const entries = useListening((s) => s.entries);
    const saved = useProfile((s) => s.recaps[month]);
    const markSeen = useProfile((s) => s.markRecapSeen);
    const isCurrent = month === monthKey(new Date());
    const recap = useMemo(() => saved || buildMonthlyRecap(entries, month), [entries, month, saved]);

    useEffect(() => {
        if (saved) markSeen(month);
    }, [markSeen, month, saved]);

    if (!recap.totalMs) {
        return (
            <Page>
                <Empty icon="calendar" title={t('recap.empty')} />
            </Page>
        );
    }

    const { from } = getMonthRange(month);
    const days = new Date(new Date(from).getFullYear(), new Date(from).getMonth() + 1, 0).getDate();
    const dayKeys = Array.from({ length: days }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`);
    const diff = recap.previousTotalMs ? Math.round(((recap.totalMs - recap.previousTotalMs) / recap.previousTotalMs) * 100) : null;

    return (
        <Page className={styles.recap}>
            <header className={styles.hero}>
                <span className={styles.label}>{isCurrent ? t('recap.soFar') : t('recap.title')}</span>
                <h1>
                    {formatMonth(month)} <span>{month.slice(0, 4)}</span>
                </h1>
                <p className={styles.big}>{formatListenMinutes(t, recap.totalMs, i18n.language)}</p>
                {diff !== null && <p className={styles.compare}>{diff >= 0 ? t('recap.more', { percent: diff }) : t('recap.less', { percent: -diff })}</p>}
            </header>

            <div className={styles.stats}>
                <div>
                    <strong>{formatNumber(recap.plays)}</strong>
                    <span>{t('profile.playsLabel')}</span>
                </div>
                <div>
                    <strong>{recap.activeDays}</strong>
                    <span>{t('recap.activeDays')}</span>
                </div>
                <div>
                    <strong>{formatNumber(recap.uniqueArtists)}</strong>
                    <span>{t('recap.artists')}</span>
                </div>
                {recap.newArtists > 0 && (
                    <div>
                        <strong>{recap.newArtists}</strong>
                        <span>{t('recap.newArtists')}</span>
                    </div>
                )}
                {recap.busiestDay && (
                    <div>
                        <strong>{formatDate(new Date(`${recap.busiestDay.day}T12:00:00`).getTime(), { day: 'numeric', month: 'short' })}</strong>
                        <span>{t('recap.busiestDay', { minutes: formatListenMinutes(t, recap.busiestDay.ms, i18n.language) })}</span>
                    </div>
                )}
                {recap.favoriteDaypart && (
                    <div>
                        <strong>{t(`mixes.dayparts.${recap.favoriteDaypart}.label`)}</strong>
                        <span>{t('recap.favoriteDaypart')}</span>
                    </div>
                )}
            </div>

            <Section title={t('recap.perDay')}>
                <div className={styles.card}>
                    <BarStrip format={(v) => formatListenMinutes(t, v, i18n.language)} labelEvery={5} labels={dayKeys.map((d) => String(Number(d.slice(8))))} title={t('recap.perDay')} values={dayKeys.map((d) => recap.byDay[d] || 0)} />
                </div>
            </Section>

            {recap.topArtists[0] && (
                <div className={styles.spotlight}>
                    <Cover coverId={null} icon="user" round seed={recap.topArtists[0].name} size="md" />
                    <div>
                        <span className={styles.label}>{t('recap.artistOfMonth')}</span>
                        <h2>{recap.topArtists[0].name}</h2>
                        <p>{formatListenMinutes(t, recap.topArtists[0].listenedMs, i18n.language)}</p>
                    </div>
                </div>
            )}

            <div className={styles.cols}>
                <Section title={t('recap.topSongs')}>
                    <List items={recap.topSongs} />
                </Section>
                <Section title={t('recap.topArtists')}>
                    <List items={recap.topArtists} round />
                </Section>
            </div>
            <div className={styles.cols}>
                <Section title={t('recap.topAlbums')}>
                    <List items={recap.topAlbums} />
                </Section>
                <Section title={t('recap.topGenres')}>
                    <div className={styles.genres}>
                        {recap.topGenres.map((g, i) => (
                            <span key={g.name} style={{ fontSize: `${Math.max(14, 26 - i * 3)}px` }}>
                                {g.name}
                            </span>
                        ))}
                    </div>
                </Section>
            </div>
        </Page>
    );
}
