import clsx from 'clsx';
import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';

import { BarStrip } from '@/components/bar-strip';
import { Cover } from '@/components/cover';
import { Icon } from '@/components/icon';
import { openModal } from '@/components/modal';
import { Page, Section } from '@/components/page';
import { toast } from '@/components/toast';
import { Button, Empty, IconButton, Segmented, TextInput } from '@/components/ui';
import { formatListenMinutes } from '@/insights/format-minutes';
import { monthKey, useListening, type ListenEntry } from '@/insights/listening-db';
import { DAY_MS, getStreaks, type RankedItem, summarize } from '@/insights/listening-stats';
import { formatMonth } from '@/insights/monthly-recap';
import { ARCHETYPE_ICON, getAchievements, getListenerDna } from '@/insights/profile-insights';
import { imageToAvatar, PROFILE_ACCENTS, useProfile } from '@/insights/profile-store';
import { useLibrary } from '@/library/store';
import { formatDate, formatNumber, relativeTime } from '@/lib/format';
import { useIsMobile } from '@/lib/hooks';
import { routes } from '@/lib/nav';
import { ProfileAvatar, useProfileName } from '@/layout/profile-avatar';

import styles from './profile.module.css';

type Range = '4w' | '6m' | 'all';
const RANGE_DAYS: Record<Range, number> = { '4w': 28, '6m': 182, all: Infinity };

function EditProfile({ close }: { close: () => void }) {
    const { t } = useTranslation();
    const profile = useProfile();
    const [name, setName] = useState(profile.displayName);
    const [bio, setBio] = useState(profile.bio);
    const [accent, setAccent] = useState(profile.accent);
    const fileRef = useRef<HTMLInputElement>(null);
    return (
        <form
            className={styles.edit}
            onSubmit={(e) => {
                e.preventDefault();
                profile.update({ accent, bio: bio.trim().slice(0, 160), displayName: name.trim().slice(0, 40) });
                close();
            }}
        >
            <div className={styles.editAvatar}>
                <ProfileAvatar size={88} />
                <div className={styles.editAvatarActions}>
                    <Button icon="image" onClick={() => fileRef.current?.click()} size="sm">
                        {t('profile.changePhoto')}
                    </Button>
                    {profile.avatar && (
                        <Button onClick={() => profile.update({ avatar: null })} size="sm" variant="ghost">
                            {t('profile.removePhoto')}
                        </Button>
                    )}
                </div>
                <input
                    accept="image/*"
                    hidden
                    onChange={async (e) => {
                        const file = e.target.files?.[0];
                        if (!file) return;
                        try {
                            profile.update({ avatar: await imageToAvatar(file) });
                        } catch {
                            toast.error(t('profile.photoFailed'));
                        }
                    }}
                    ref={fileRef}
                    type="file"
                />
            </div>
            <TextInput label={t('profile.name')} maxLength={40} onChange={(e) => setName(e.target.value)} value={name} />
            <TextInput label={t('profile.bio')} maxLength={160} onChange={(e) => setBio(e.target.value)} value={bio} />
            <div className={styles.field}>
                <span className={styles.fieldLabel}>{t('profile.color')}</span>
                <div className={styles.swatches}>
                    {PROFILE_ACCENTS.map((c) => (
                        <button aria-label={c} aria-pressed={c === accent} className={styles.swatch} key={c} onClick={() => setAccent(c)} style={{ background: c }} type="button" />
                    ))}
                </div>
            </div>
            <div className={styles.editActions}>
                <Button onClick={close} variant="ghost">
                    {t('common.cancel')}
                </Button>
                <Button type="submit" variant="primary">
                    {t('common.save')}
                </Button>
            </div>
        </form>
    );
}

function Ranked({ items, kind }: { items: RankedItem[]; kind: 'artist' | 'genre' | 'song' }) {
    const { t, i18n } = useTranslation();
    const library = useLibrary((s) => s.library);
    if (!items.length) return <p className={styles.muted}>{t('profile.nothingYet')}</p>;
    return (
        <ol className={styles.ranked}>
            {items.map((item, i) => {
                const artist = kind === 'artist' ? library.artistByName(item.name) : null;
                const genre = kind === 'genre' ? library.genres.find((g) => g.key === item.id || g.name.toLowerCase() === item.name.toLowerCase()) : null;
                const to = artist ? routes.artist(artist.id) : genre ? routes.genre(genre.id) : kind === 'song' && library.songsById.get(item.id) ? routes.album(library.songsById.get(item.id)!.albumId) : null;
                const content = (
                    <>
                        <span className={styles.rank}>{i + 1}</span>
                        {kind !== 'genre' && (
                            <Cover coverId={artist?.coverId || item.imageId} icon={kind === 'artist' ? 'user' : 'music'} round={kind === 'artist'} seed={item.id || item.name} size="sm" />
                        )}
                        <div className={styles.rankedText}>
                            <strong className="truncate">{item.name}</strong>
                            <span className="truncate">{[item.subtitle, t('profile.plays', { count: item.plays, formatted: formatNumber(item.plays) })].filter(Boolean).join(' · ')}</span>
                        </div>
                        <span className={styles.rankedMinutes}>{formatListenMinutes(t, item.listenedMs, i18n.language)}</span>
                    </>
                );
                return <li key={`${item.id}-${item.name}`}>{to ? <Link className={styles.rankedRow} to={to}>{content}</Link> : <div className={styles.rankedRow}>{content}</div>}</li>;
            })}
        </ol>
    );
}

function Stat({ label, value }: { label: string; value: string }) {
    return (
        <div className={styles.stat}>
            <strong>{value}</strong>
            <span>{label}</span>
        </div>
    );
}

function ProfileContent() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const isMobile = useIsMobile();
    const entries = useListening((s) => s.entries);
    const profile = useProfile();
    const name = useProfileName();
    const [range, setRange] = useState<Range>('4w');
    const [topKind, setTopKind] = useState<'artist' | 'genre' | 'song'>('artist');

    const data = useMemo(() => {
        const from = range === 'all' ? 0 : Date.now() - RANGE_DAYS[range] * DAY_MS;
        const filtered = entries.filter((e) => e.playedAt >= from);
        const summary = summarize(filtered, 10);
        const all = summarize(entries, 10);
        return {
            achievements: getAchievements(entries, all),
            dna: getListenerDna(entries, all),
            streaks: getStreaks(entries),
            summary,
        };
    }, [entries, range]);

    const months = useMemo(() => {
        const current = monthKey(new Date());
        const keys = new Set(Object.keys(profile.recaps));
        if (entries.some((e) => monthKey(e.playedAt) === current)) keys.add(current);
        return [...keys].sort().reverse();
    }, [entries, profile.recaps]);

    const recent = useMemo(() => [...entries].reverse().slice(0, 12), [entries]);
    const pinned = data.achievements.find((a) => a.id === profile.pinnedBadge && a.unlocked);
    const hours = Array.from({ length: 24 }, (_, h) => `${h}h`);
    const weekdays = Array.from({ length: 7 }, (_, d) => new Intl.DateTimeFormat(i18n.language, { weekday: 'short' }).format(new Date(2023, 0, 1 + d)));

    return (
        <Page className={styles.profile}>
            <header className={styles.hero} style={{ '--accent': profile.accent } as React.CSSProperties}>
                <div className={styles.heroMain}>
                    <ProfileAvatar size={isMobile ? 96 : 132} />
                    <div className={styles.heroText}>
                        <span className={styles.label}>{t('nav.profile')}</span>
                        <h1>{name || t('profile.anonymous')}</h1>
                        {profile.bio && <p>{profile.bio}</p>}
                        <div className={styles.heroMeta}>
                            {pinned && (
                                <span className={styles.pinned}>
                                    <Icon name={pinned.icon} size={14} />
                                    {t(`achievements.${pinned.id}.title`)}
                                </span>
                            )}
                            <span>{t('profile.since', { date: formatDate(profile.createdAt, { month: 'long', year: 'numeric' }) })}</span>
                        </div>
                    </div>
                </div>
                <div className={styles.heroActions}>
                    <Button icon="editProfile" onClick={() => openModal({ render: (close) => <EditProfile close={close} />, size: 'sm', title: t('profile.edit') })}>
                        {t('profile.edit')}
                    </Button>
                    {isMobile && <IconButton icon="settings" label={t('nav.settings')} onClick={() => navigate('/settings')} variant="surface" />}
                </div>
            </header>

            {entries.length === 0 ? (
                <Empty icon="chart" text={t('profile.emptyText')} title={t('profile.emptyTitle')} />
            ) : (
                <>
                    <div className={styles.rangeRow}>
                        <Segmented
                            onChange={setRange}
                            options={[
                                { label: t('profile.range.4w'), value: '4w' },
                                { label: t('profile.range.6m'), value: '6m' },
                                { label: t('profile.range.all'), value: 'all' },
                            ]}
                            value={range}
                        />
                    </div>

                    <div className={styles.stats}>
                        <Stat label={t('profile.minutesListened')} value={formatNumber(Math.round(data.summary.totalMs / 60000))} />
                        <Stat label={t('profile.playsLabel')} value={formatNumber(data.summary.plays)} />
                        <Stat label={t('profile.currentStreak')} value={t('profile.days', { count: data.streaks.current })} />
                        <Stat label={t('profile.longestStreak')} value={t('profile.days', { count: data.streaks.longest })} />
                    </div>

                    <div className={styles.twoCols}>
                        <Section title={t('profile.dna')}>
                            <div className={styles.dna}>
                                <div className={styles.archetype}>
                                    <div className={styles.archetypeIcon}>
                                        <Icon name={ARCHETYPE_ICON[data.dna.archetype]} size={26} />
                                    </div>
                                    <div>
                                        <strong>{t(`archetypes.${data.dna.archetype}.title`)}</strong>
                                        <p>{t(`archetypes.${data.dna.archetype}.description`)}</p>
                                    </div>
                                </div>
                                {(['variety', 'loyalty', 'novelty'] as const).map((trait) => (
                                    <div className={styles.trait} key={trait}>
                                        <span>{t(`profile.traits.${trait}`)}</span>
                                        <div className={styles.traitBar}>
                                            <div style={{ width: `${data.dna.traits[trait]}%` }} />
                                        </div>
                                        <span className={styles.traitValue}>{data.dna.traits[trait]}%</span>
                                    </div>
                                ))}
                            </div>
                        </Section>
                        <Section title={t('profile.clock')}>
                            <div className={styles.card}>
                                <BarStrip format={(v) => formatListenMinutes(t, v, i18n.language)} highlight={new Date().getHours()} labelEvery={isMobile ? 6 : 3} labels={hours} title={t('profile.byHour')} values={data.summary.byHour} />
                                <BarStrip format={(v) => formatListenMinutes(t, v, i18n.language)} height={70} highlight={new Date().getDay()} labels={weekdays} title={t('profile.byWeekday')} values={data.summary.byWeekday} />
                            </div>
                        </Section>
                    </div>

                    <Section
                        action={
                            <Segmented
                                onChange={setTopKind}
                                options={[
                                    { label: t('library.artists'), value: 'artist' },
                                    { label: t('library.songs'), value: 'song' },
                                    { label: t('library.genres'), value: 'genre' },
                                ]}
                                value={topKind}
                            />
                        }
                        title={t('profile.top')}
                    >
                        <Ranked items={topKind === 'artist' ? data.summary.topArtists : topKind === 'song' ? data.summary.topSongs : data.summary.topGenres} kind={topKind} />
                    </Section>
                </>
            )}

            {months.length > 0 && (
                <Section title={t('profile.recaps')}>
                    <div className={styles.recaps}>
                        {months.map((m) => {
                            const recap = profile.recaps[m];
                            const isNew = recap && !profile.seenRecaps.includes(m);
                            return (
                                <Link className={styles.recap} key={m} to={routes.recap(m)}>
                                    <span className={styles.recapYear}>{m.slice(0, 4)}</span>
                                    <strong>{formatMonth(m)}</strong>
                                    <span>{recap ? formatListenMinutes(t, recap.totalMs, i18n.language) : t('recap.soFar')}</span>
                                    {isNew && <span className={styles.newBadge}>{t('common.new')}</span>}
                                </Link>
                            );
                        })}
                    </div>
                </Section>
            )}

            <Section title={t('profile.achievements')}>
                <div className={styles.achievements}>
                    {data.achievements.map((a) => (
                        <button
                            className={clsx(styles.achievement, a.unlocked && styles.unlocked, profile.pinnedBadge === a.id && styles.isPinned)}
                            disabled={!a.unlocked}
                            key={a.id}
                            onClick={() => profile.update({ pinnedBadge: profile.pinnedBadge === a.id ? null : a.id })}
                            title={a.unlocked ? t('profile.pinHint') : undefined}
                            type="button"
                        >
                            <div className={styles.badge}>
                                <Icon name={a.icon} size={22} />
                            </div>
                            <strong>{t(`achievements.${a.id}.title`)}</strong>
                            <span>{t(`achievements.${a.id}.description`)}</span>
                            {!a.unlocked && (
                                <div className={styles.progress}>
                                    <div style={{ width: `${(a.current / a.target) * 100}%` }} />
                                </div>
                            )}
                            {profile.pinnedBadge === a.id && <Icon className={styles.pin} name="pin" size={14} />}
                        </button>
                    ))}
                </div>
            </Section>

            {recent.length > 0 && (
                <Section title={t('profile.recent')}>
                    <div className={styles.recent}>
                        {recent.map((e: ListenEntry) => (
                            <div className={styles.recentRow} key={e.id}>
                                <Cover coverId={e.imageId} seed={e.albumId || e.songId} size="sm" />
                                <div className={styles.rankedText}>
                                    <strong className="truncate">{e.title}</strong>
                                    <span className="truncate">{e.artist}</span>
                                </div>
                                <span className={styles.rankedMinutes}>{relativeTime(e.playedAt)}</span>
                            </div>
                        ))}
                    </div>
                </Section>
            )}
        </Page>
    );
}

export function ProfileScreen() {
    return <ProfileContent />;
}
