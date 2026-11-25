import clsx from 'clsx';
import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { openSongMenu } from '@/components/actions';
import { Cover } from '@/components/cover';
import { Icon } from '@/components/icon';
import { PlayingBars } from '@/components/song-list';
import { Button, Empty, IconButton } from '@/components/ui';
import { formatDuration } from '@/lib/format';
import { clearUpcoming, moveInQueue, playAt, removeFromQueue, stopRadio, type QueueItem, usePlayer } from '@/player/store';

import styles from './queue-view.module.css';

const ROW = 56;
const MAX_SHOWN = 300;

function Row({ dragging, item, offset, onHandleDown, position, current }: { item: QueueItem; position: number; current?: boolean; dragging?: boolean; offset?: number; onHandleDown?: (e: React.PointerEvent) => void }) {
    const { t } = useTranslation();
    const paused = usePlayer((s) => s.status !== 'playing' && s.status !== 'loading');
    return (
        <div
            className={clsx(styles.row, current && styles.current, dragging && styles.dragging)}
            onClick={() => !current && playAt(position)}
            onContextMenu={(e) => {
                e.preventDefault();
                openSongMenu({ x: e.clientX, y: e.clientY }, item.song);
            }}
            style={offset ? { transform: `translateY(${offset}px)` } : undefined}
        >
            {onHandleDown ? (
                <span className={styles.handle} onClick={(e) => e.stopPropagation()} onPointerDown={onHandleDown} title={t('queue.drag')}>
                    <Icon name="drag" size={16} />
                </span>
            ) : (
                <span className={styles.handle} />
            )}
            <div className={styles.cover}>
                <Cover coverId={item.song.coverId} seed={item.song.albumId} size="sm" />
                {current && (
                    <div className={styles.overlay}>
                        <PlayingBars paused={paused} />
                    </div>
                )}
            </div>
            <div className={styles.text}>
                <span className={clsx(styles.title, 'truncate')}>{item.song.title}</span>
                <span className={clsx(styles.artist, 'truncate')}>
                    {item.auto && <Icon className={styles.auto} name="wand" size={12} />}
                    {item.song.artist}
                </span>
            </div>
            <span className={styles.duration}>{formatDuration(item.song.durationMs)}</span>
            {!current && (
                <IconButton
                    className={styles.remove}
                    icon="close"
                    label={t('queue.remove')}
                    onClick={(e) => {
                        e.stopPropagation();
                        removeFromQueue([item.key]);
                    }}
                    size={30}
                />
            )}
        </div>
    );
}

export function QueueView() {
    const { t } = useTranslation();
    const queue = usePlayer((s) => s.queue);
    const index = usePlayer((s) => s.index);
    const radio = usePlayer((s) => s.radioSeed);
    const [drag, setDrag] = useState<null | { from: number; startY: number; dy: number }>(null);
    const listRef = useRef<HTMLDivElement>(null);

    const current = queue[index];
    const upcoming = queue.slice(index + 1, index + 1 + MAX_SHOWN);
    const hidden = Math.max(0, queue.length - index - 1 - MAX_SHOWN);

    if (!current) return <Empty icon="queue" title={t('queue.empty')} />;

    const target = drag ? Math.max(0, Math.min(upcoming.length - 1, drag.from + Math.round(drag.dy / ROW))) : -1;

    return (
        <div className={styles.queue}>
            <div className={styles.label}>{t('queue.nowPlaying')}</div>
            <Row current item={current} position={index} />

            {radio && (
                <div className={styles.radio}>
                    <Icon name="radio" size={18} />
                    <span className="truncate">{t('queue.radioOf', { title: radio.title })}</span>
                    <Button onClick={stopRadio} size="sm" variant="ghost">
                        {t('queue.stopRadio')}
                    </Button>
                </div>
            )}

            {upcoming.length > 0 && (
                <>
                    <div className={styles.labelRow}>
                        <span className={styles.label}>{t('queue.next')}</span>
                        <Button icon="clear" onClick={clearUpcoming} size="sm" variant="ghost">
                            {t('queue.clear')}
                        </Button>
                    </div>
                    <div
                        className={styles.list}
                        onPointerMove={(e) => drag && setDrag({ ...drag, dy: e.clientY - drag.startY })}
                        onPointerUp={() => {
                            if (drag) {
                                moveInQueue(index + 1 + drag.from, index + 1 + target);
                                setDrag(null);
                            }
                        }}
                        ref={listRef}
                    >
                        {upcoming.map((item, i) => {
                            let offset = 0;
                            if (drag) {
                                if (i === drag.from) offset = drag.dy;
                                else if (drag.from < target && i > drag.from && i <= target) offset = -ROW;
                                else if (drag.from > target && i < drag.from && i >= target) offset = ROW;
                            }
                            return (
                                <Row
                                    dragging={drag?.from === i}
                                    item={item}
                                    key={item.key}
                                    offset={offset}
                                    onHandleDown={(e) => {
                                        e.preventDefault();
                                        listRef.current?.setPointerCapture(e.pointerId);
                                        setDrag({ dy: 0, from: i, startY: e.clientY });
                                    }}
                                    position={index + 1 + i}
                                />
                            );
                        })}
                        {hidden > 0 && <div className={styles.more}>{t('queue.andMore', { count: hidden })}</div>}
                    </div>
                </>
            )}
        </div>
    );
}
