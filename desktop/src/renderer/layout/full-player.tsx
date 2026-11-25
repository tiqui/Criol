import clsx from 'clsx';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { openSongMenu } from '@/components/actions';
import { Cover, useCoverUrl } from '@/components/cover';
import { openMenu } from '@/components/menu';
import { IconButton } from '@/components/ui';
import i18n from '@/i18n';
import { libraryState, toggleFavorite, useIsFavorite } from '@/library/store';
import { useIsMobile } from '@/lib/hooks';
import { navigate, routes } from '@/lib/nav';
import { getPlaceholderGradient } from '@/lib/placeholder';
import { next, previous, setFullscreen, setSleepTimer, setSpeed, skipBy, useCurrentSong, usePlayer } from '@/player/store';
import { useSettings } from '@/settings/store';

import styles from './full-player.module.css';
import { LyricsView } from './lyrics-view';
import { AutoDjButton, ProgressBar, Transport, Volume } from './player-bar';
import { QueueView } from './queue-view';

type Tab = 'lyrics' | 'none' | 'queue';

function openPlayerOptions(at: HTMLElement) {
    const { sleepAt, sleepEndOfSong, speed } = usePlayer.getState();
    const t = (k: string, o?: Record<string, unknown>) => i18n.t(k, o);
    const sleepOptions = [15, 30, 45, 60, 90];
    openMenu(at, [
        {
            children: [0.75, 0.9, 1, 1.1, 1.25, 1.5].map((value) => ({ checked: speed === value, label: `${value}×`, onSelect: () => setSpeed(value) })),
            icon: 'speed',
            label: t('player.speed', { speed }),
        },
        {
            children: [
                ...sleepOptions.map((m) => ({ label: t('player.sleepMinutes', { count: m }), onSelect: () => setSleepTimer(m) })),
                { checked: sleepEndOfSong, label: t('player.sleepEndOfSong'), onSelect: () => setSleepTimer(null, true) },
                ...(sleepAt || sleepEndOfSong ? [{ divider: true }, { danger: true, label: t('player.sleepOff'), onSelect: () => setSleepTimer(null) }] : []),
            ],
            icon: 'timer',
            label: sleepAt ? t('player.sleepUntil', { time: new Date(sleepAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }) : t('player.sleepTimer'),
        },
    ]);
}

/** Player em tela cheia: capa grande, controles, letra e fila. */
export function FullPlayer() {
    const { t } = useTranslation();
    const song = useCurrentSong();
    const isMobile = useIsMobile();
    const [tab, setTab] = useState<Tab>(() => (window.matchMedia('(max-width: 768px)').matches ? 'none' : 'lyrics'));
    const blur = useSettings((s) => s.playerBlur);
    const skipBack = useSettings((s) => s.skipBackSeconds);
    const skipForward = useSettings((s) => s.skipForwardSeconds);
    const fav = useIsFavorite('song', song?.id);
    const bg = useCoverUrl(song?.coverId, 'md');
    const drag = useRef<null | { x: number; y: number }>(null);
    const [offset, setOffset] = useState({ x: 0, y: 0 });

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setFullscreen(false);
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, []);

    useEffect(() => {
        if (!song) setFullscreen(false);
    }, [song]);

    if (!song) return null;
    const artist = libraryState().library.artistByName(song.artists[0] || song.artist);

    const goTo = (path: string) => {
        setFullscreen(false);
        navigate(path);
    };

    return (
        <div
            className={clsx(styles.full, isMobile && styles.mobile, tab !== 'none' && styles.withTab)}
            onTouchEnd={() => {
                const { x, y } = offset;
                drag.current = null;
                setOffset({ x: 0, y: 0 });
                if (y > 120) setFullscreen(false);
                else if (Math.abs(x) > 80) {
                    if (x < 0) void next(true);
                    else previous();
                }
            }}
            onTouchMove={(e) => {
                if (!drag.current) return;
                const dx = e.touches[0].clientX - drag.current.x;
                const dy = e.touches[0].clientY - drag.current.y;
                setOffset(Math.abs(dy) > Math.abs(dx) ? { x: 0, y: Math.max(0, dy) } : { x: dx, y: 0 });
            }}
            style={offset.y ? { opacity: 1 - offset.y / 600, transform: `translateY(${offset.y}px)` } : undefined}
        >
            <div className={styles.bg} style={{ background: getPlaceholderGradient(song.albumId) }}>
                {blur && bg && <img alt="" className={styles.bgImg} src={bg} />}
                <div className={styles.bgShade} />
            </div>

            <div className={styles.top}>
                <IconButton icon="chevronDown" label={t('common.close')} onClick={() => setFullscreen(false)} size={40} />
                <div className={styles.context}>
                    <span>{t('player.playingFrom')}</span>
                    <strong className="truncate">{song.album}</strong>
                </div>
                <IconButton icon="more" label={t('common.more')} onClick={(e) => openSongMenu(e.currentTarget, song)} size={40} />
            </div>

            <div className={styles.body}>
                <div className={styles.left}>
                    <div
                        className={styles.coverBox}
                        onTouchStart={(e) => {
                            drag.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
                        }}
                        style={offset.x ? { transform: `translateX(${offset.x}px) rotate(${offset.x / 40}deg)` } : undefined}
                    >
                        <Cover className={styles.cover} coverId={song.coverId} seed={song.albumId} size="lg" />
                    </div>

                    <div className={styles.info}>
                        <div className={styles.infoText}>
                            <button className={clsx(styles.title, 'truncate')} onClick={() => goTo(routes.album(song.albumId))} type="button">
                                {song.title}
                            </button>
                            <button className={clsx(styles.artist, 'truncate')} onClick={() => artist && goTo(routes.artist(artist.id))} type="button">
                                {song.artist}
                            </button>
                        </div>
                        <IconButton active={fav} filled={fav} icon="favorite" iconSize={24} label={fav ? t('actions.unfavorite') : t('actions.favorite')} onClick={() => void toggleFavorite('song', song.id)} size={44} />
                    </div>

                    <ProgressBar className={styles.progress} />
                    <Transport size="lg" />

                    <div className={styles.tools}>
                        <IconButton icon="rewind" label={t('player.skipBack', { seconds: skipBack })} onClick={() => skipBy(-skipBack)} size={40} />
                        <IconButton active={tab === 'lyrics'} icon="lyrics" label={t('player.lyrics')} onClick={() => setTab(tab === 'lyrics' ? 'none' : 'lyrics')} size={40} />
                        <IconButton active={tab === 'queue'} icon="queue" label={t('player.queue')} onClick={() => setTab(tab === 'queue' ? 'none' : 'queue')} size={40} />
                        <AutoDjButton size={40} />
                        <IconButton icon="sliders" label={t('player.options')} onClick={(e) => openPlayerOptions(e.currentTarget)} size={40} />
                        <IconButton icon="forward" label={t('player.skipForward', { seconds: skipForward })} onClick={() => skipBy(skipForward)} size={40} />
                    </div>
                    {!isMobile && (
                        <div className={styles.volume}>
                            <Volume />
                        </div>
                    )}
                </div>

                {tab !== 'none' && (
                    <div className={styles.right}>
                        {isMobile && (
                            <div className={styles.tabHead}>
                                <strong>{tab === 'lyrics' ? t('player.lyrics') : t('player.queue')}</strong>
                                <IconButton icon="close" label={t('common.close')} onClick={() => setTab('none')} size={34} />
                            </div>
                        )}
                        <div className={styles.tabBody}>{tab === 'lyrics' ? <LyricsView large /> : <QueueView />}</div>
                    </div>
                )}
            </div>
        </div>
    );
}
