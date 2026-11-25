import clsx from 'clsx';
import { useRef } from 'react';
import { useTranslation } from 'react-i18next';

import { Cover } from '@/components/cover';
import { IconButton } from '@/components/ui';
import { next, previous, setFullscreen, togglePlay, useCurrentSong, usePlayer, usePosition } from '@/player/store';

import styles from './mini-player.module.css';

function MiniProgress() {
    const position = usePosition((s) => s.position);
    const duration = usePosition((s) => s.duration);
    return (
        <div className={styles.progress}>
            <div className={styles.fill} style={{ width: `${duration ? Math.min(100, (position / duration) * 100) : 0}%` }} />
        </div>
    );
}

/** Cartão do que está tocando, acima da navegação no celular. Deslize para trocar de música. */
export function MiniPlayer() {
    const { t } = useTranslation();
    const song = useCurrentSong();
    const status = usePlayer((s) => s.status);
    const playing = status === 'playing' || status === 'loading';
    const touch = useRef<null | { x: number; y: number }>(null);

    if (!song) return null;

    return (
        <div className={styles.wrap}>
            <div
                className={styles.card}
                onClick={() => setFullscreen(true)}
                onTouchEnd={(e) => {
                    const start = touch.current;
                    touch.current = null;
                    if (!start) return;
                    const dx = e.changedTouches[0].clientX - start.x;
                    const dy = e.changedTouches[0].clientY - start.y;
                    if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
                        if (dx < 0) void next(true);
                        else previous();
                    } else if (dy < -40 && Math.abs(dy) > Math.abs(dx)) {
                        setFullscreen(true);
                    }
                }}
                onTouchStart={(e) => {
                    touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
                }}
                role="button"
                tabIndex={0}
            >
                <Cover className={styles.cover} coverId={song.coverId} seed={song.albumId} size="sm" />
                <div className={styles.text}>
                    <span className={clsx(styles.title, 'truncate')}>{song.title}</span>
                    <span className={clsx(styles.artist, 'truncate')}>{song.artist}</span>
                </div>
                <div className={styles.controls} onClick={(e) => e.stopPropagation()}>
                    <IconButton filled icon={playing ? 'mediaPause' : 'mediaPlay'} iconSize={24} label={playing ? t('player.pause') : t('player.play')} onClick={togglePlay} size={44} />
                    <IconButton filled icon="mediaNext" iconSize={22} label={t('player.next')} onClick={() => void next(true)} size={44} />
                </div>
                <MiniProgress />
            </div>
        </div>
    );
}
