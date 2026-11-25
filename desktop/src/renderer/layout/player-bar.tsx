import clsx from 'clsx';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { openSongMenu } from '@/components/actions';
import { Cover } from '@/components/cover';
import { Icon } from '@/components/icon';
import { Slider } from '@/components/slider';
import { IconButton } from '@/components/ui';
import { libraryState, toggleFavorite, useIsFavorite } from '@/library/store';
import { formatDuration } from '@/lib/format';
import { routes } from '@/lib/nav';
import {
    cycleRepeat,
    next,
    previous,
    seek,
    setFullscreen,
    setVolume,
    toggleAutoDj,
    toggleMute,
    togglePlay,
    toggleShuffle,
    useCurrentSong,
    usePlayer,
    usePosition,
} from '@/player/store';
import { useSettings } from '@/settings/store';

import styles from './player-bar.module.css';

export function ProgressBar({ className, compact }: { className?: string; compact?: boolean }) {
    const { t } = useTranslation();
    const position = usePosition((s) => s.position);
    const duration = usePosition((s) => s.duration);
    const buffered = usePosition((s) => s.buffered);
    return (
        <div className={clsx(styles.progress, compact && styles.progressCompact, className)}>
            <span className={styles.time}>{formatDuration(position)}</span>
            <Slider buffered={buffered} formatTooltip={formatDuration} label={t('player.progress')} max={duration} onCommit={seek} step={5000} value={position} />
            <span className={styles.time}>{formatDuration(duration)}</span>
        </div>
    );
}

export function Transport({ size = 'md' }: { size?: 'lg' | 'md' }) {
    const { t } = useTranslation();
    const status = usePlayer((s) => s.status);
    const shuffle = usePlayer((s) => s.shuffle);
    const repeat = usePlayer((s) => s.repeat);
    const hasSong = usePlayer((s) => s.index >= 0);
    const playing = status === 'playing' || status === 'loading';
    const big = size === 'lg';
    return (
        <div className={clsx(styles.transport, big && styles.transportLarge)}>
            <IconButton active={shuffle} icon="shuffle" label={t('player.shuffle')} onClick={toggleShuffle} size={big ? 44 : 34} />
            <IconButton disabled={!hasSong} icon="mediaPrevious" label={t('player.previous')} onClick={previous} size={big ? 52 : 36} />
            <IconButton
                className={styles.play}
                disabled={!hasSong}
                filled
                icon={playing ? 'mediaPause' : 'mediaPlay'}
                iconSize={big ? 28 : 18}
                label={playing ? t('player.pause') : t('player.play')}
                onClick={togglePlay}
                size={big ? 68 : 38}
                variant="filled"
            />
            <IconButton disabled={!hasSong} icon="mediaNext" label={t('player.next')} onClick={() => void next(true)} size={big ? 52 : 36} />
            <IconButton
                active={repeat !== 'off'}
                icon={repeat === 'one' ? 'repeatOne' : 'repeat'}
                label={repeat === 'off' ? t('player.repeatOff') : repeat === 'all' ? t('player.repeatAll') : t('player.repeatOne')}
                onClick={cycleRepeat}
                size={big ? 44 : 34}
            />
        </div>
    );
}

export function Volume() {
    const { t } = useTranslation();
    const volume = usePlayer((s) => s.volume);
    const muted = usePlayer((s) => s.muted);
    return (
        <div className={styles.volume}>
            <IconButton
                icon={muted || volume === 0 ? 'volumeMute' : volume < 0.5 ? 'volumeLow' : 'volume'}
                label={muted ? t('player.unmute') : t('player.mute')}
                onClick={toggleMute}
                size={32}
            />
            <Slider label={t('player.volume')} max={1} onChange={setVolume} onCommit={setVolume} step={0.05} value={muted ? 0 : volume} />
        </div>
    );
}

/** Botão do Auto DJ (barra e player em tela cheia). */
export function AutoDjButton({ size = 34 }: { size?: number }) {
    const { t } = useTranslation();
    const enabled = useSettings((s) => s.autoDjEnabled);
    return <IconButton active={enabled} icon="wand" label={enabled ? t('player.autoDjDisable') : t('player.autoDjEnable')} onClick={toggleAutoDj} size={size} />;
}

export function PlayerBar() {
    const { t } = useTranslation();
    const song = useCurrentSong();
    const fav = useIsFavorite('song', song?.id);
    const panel = useSettings((s) => s.sidePanel);
    const setSettings = useSettings((s) => s.set);
    const radio = usePlayer((s) => s.radioSeed);
    const artist = song ? libraryState().library.artistByName(song.artists[0] || song.artist) : null;

    const togglePanel = (value: 'lyrics' | 'queue') => setSettings({ sidePanel: panel === value ? 'none' : value });

    return (
        <footer className={styles.bar}>
            <div className={styles.now}>
                {song ? (
                    <>
                        <button aria-label={t('player.openFullscreen')} className={styles.cover} onClick={() => setFullscreen(true)} type="button">
                            <Cover coverId={song.coverId} seed={song.albumId} size="sm" />
                            <span className={styles.expand}>
                                <Icon name="chevronUp" size={14} />
                            </span>
                        </button>
                        <div className={styles.meta} onContextMenu={(e) => { e.preventDefault(); openSongMenu({ x: e.clientX, y: e.clientY }, song); }}>
                            <Link className={clsx(styles.title, 'truncate')} to={routes.album(song.albumId)}>
                                {song.title}
                            </Link>
                            <span className={clsx(styles.artist, 'truncate')}>
                                {artist ? <Link to={routes.artist(artist.id)}>{song.artist}</Link> : song.artist}
                                {radio && <span className={styles.badge}>{t('player.radio')}</span>}
                            </span>
                        </div>
                        <IconButton active={fav} filled={fav} icon="favorite" label={fav ? t('actions.unfavorite') : t('actions.favorite')} onClick={() => void toggleFavorite('song', song.id)} size={34} />
                    </>
                ) : (
                    <span className={styles.idle}>{t('player.nothingPlaying')}</span>
                )}
            </div>
            <div className={styles.center}>
                <Transport />
                <ProgressBar />
            </div>
            <div className={styles.tools}>
                <AutoDjButton />
                <IconButton active={panel === 'lyrics'} disabled={!song} icon="lyrics" label={t('player.lyrics')} onClick={() => togglePanel('lyrics')} size={34} />
                <IconButton active={panel === 'queue'} disabled={!song} icon="queue" label={t('player.queue')} onClick={() => togglePanel('queue')} size={34} />
                <Volume />
                <IconButton disabled={!song} icon="maximize" label={t('player.openFullscreen')} onClick={() => setFullscreen(true)} size={34} />
            </div>
        </footer>
    );
}
