import { useVirtualizer } from '@tanstack/react-virtual';
import clsx from 'clsx';
import { createContext, memo, type RefObject, useContext, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import type { Song } from '@shared/types';

import { libraryState, toggleFavorite, useIsFavorite } from '@/library/store';
import { formatDuration } from '@/lib/format';
import { navigate, routes } from '@/lib/nav';
import { playSongs, togglePlay, usePlayer } from '@/player/store';

import { openSongMenu, type SongMenuContext } from './actions';
import { Cover } from './cover';
import { Icon } from './icon';
import styles from './song-list.module.css';

/** Elemento que rola a página (o conteúdo principal). */
export const ScrollContext = createContext<RefObject<HTMLElement | null>>({ current: null });

interface SongListProps {
    songs: Song[];
    /** Número da faixa em vez da capa (página do álbum). */
    trackNumbers?: boolean;
    showAlbum?: boolean;
    onPlay?: (index: number) => void;
    menuContext?: (song: Song, index: number) => SongMenuContext;
    /** Agrupa por disco (álbuns com mais de um disco). */
    discs?: boolean;
}

const VIRTUAL_AFTER = 80;

export const PlayingBars = ({ paused }: { paused?: boolean }) => (
    <span className={clsx(styles.bars, paused && styles.barsPaused)}>
        <i />
        <i />
        <i />
    </span>
);

const Row = memo(function Row({
    index,
    menuContext,
    onPlay,
    showAlbum,
    song,
    trackNumbers,
}: {
    song: Song;
    index: number;
    trackNumbers?: boolean;
    showAlbum?: boolean;
    onPlay: (index: number) => void;
    menuContext: (song: Song, index: number) => SongMenuContext;
}) {
    const { t } = useTranslation();
    const isCurrent = usePlayer((s) => s.queue[s.index]?.song.id === song.id);
    const paused = usePlayer((s) => s.status !== 'playing' && s.status !== 'loading');
    const fav = useIsFavorite('song', song.id);
    const artist = song.artists[0] || song.artist;

    return (
        <div
            aria-current={isCurrent || undefined}
            className={clsx(styles.row, isCurrent && styles.current)}
            onClick={() => (isCurrent ? togglePlay() : onPlay(index))}
            onContextMenu={(e) => {
                e.preventDefault();
                openSongMenu({ x: e.clientX, y: e.clientY }, song, menuContext(song, index));
            }}
            onKeyDown={(e) => {
                if (e.key === 'Enter') onPlay(index);
            }}
            role="button"
            tabIndex={0}
        >
            <div className={styles.lead}>
                {trackNumbers ? (
                    <span className={styles.number}>{isCurrent ? <PlayingBars paused={paused} /> : song.track || index + 1}</span>
                ) : (
                    <div className={styles.coverWrap}>
                        <Cover coverId={song.coverId} seed={song.albumId} size="sm" />
                        {isCurrent && (
                            <div className={styles.coverOverlay}>
                                <PlayingBars paused={paused} />
                            </div>
                        )}
                    </div>
                )}
            </div>
            <div className={styles.main}>
                <span className={clsx(styles.title, 'truncate')}>{song.title}</span>
                <span className={clsx(styles.sub, 'truncate')}>
                    <button
                        className={styles.link}
                        onClick={(e) => {
                            e.stopPropagation();
                            const a = libraryState().library.artistByName(artist);
                            if (a) navigate(routes.artist(a.id));
                        }}
                        type="button"
                    >
                        {song.artist}
                    </button>
                </span>
            </div>
            {showAlbum && (
                <div className={clsx(styles.album, 'truncate')}>
                    <button
                        className={styles.link}
                        onClick={(e) => {
                            e.stopPropagation();
                            navigate(routes.album(song.albumId));
                        }}
                        type="button"
                    >
                        {song.album}
                    </button>
                </div>
            )}
            <button
                aria-label={fav ? t('actions.unfavorite') : t('actions.favorite')}
                className={clsx(styles.fav, fav && styles.favOn)}
                onClick={(e) => {
                    e.stopPropagation();
                    void toggleFavorite('song', song.id);
                }}
                type="button"
            >
                <Icon filled={fav} name="favorite" size={17} />
            </button>
            <span className={styles.duration}>{formatDuration(song.durationMs)}</span>
            <button
                aria-label={t('common.more')}
                className={styles.more}
                onClick={(e) => {
                    e.stopPropagation();
                    openSongMenu(e.currentTarget, song, menuContext(song, index));
                }}
                type="button"
            >
                <Icon name="more" size={20} />
            </button>
        </div>
    );
});

export function SongList({ discs, menuContext, onPlay, showAlbum = true, songs, trackNumbers }: SongListProps) {
    const scrollRef = useContext(ScrollContext);
    const listRef = useRef<HTMLDivElement>(null);
    const [margin, setMargin] = useState(0);
    const play = onPlay || ((index: number) => playSongs(songs, index));
    const ctx = menuContext || ((_song: Song, index: number) => ({ index, list: songs }));
    const virtual = songs.length > VIRTUAL_AFTER;

    useLayoutEffect(() => {
        if (!virtual) return;
        const measure = () => {
            const scroller = scrollRef.current;
            const list = listRef.current;
            if (!scroller || !list) return;
            setMargin(list.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop);
        };
        measure();
        const observer = new ResizeObserver(measure);
        if (scrollRef.current?.firstElementChild) observer.observe(scrollRef.current.firstElementChild);
        return () => observer.disconnect();
    }, [scrollRef, virtual]);

    const rowHeight = window.matchMedia('(max-width: 768px)').matches ? 60 : 56;
    const virtualizer = useVirtualizer({
        count: virtual ? songs.length : 0,
        estimateSize: () => rowHeight,
        getScrollElement: () => scrollRef.current,
        overscan: 12,
        scrollMargin: margin,
    });

    const renderRow = (song: Song, index: number) => (
        <Row index={index} key={`${song.id}-${index}`} menuContext={ctx} onPlay={play} showAlbum={showAlbum} song={song} trackNumbers={trackNumbers} />
    );

    if (!virtual) {
        const multiDisc = discs && new Set(songs.map((s) => s.disc)).size > 1;
        return (
            <div className={clsx(styles.list, !showAlbum && styles.noAlbum)} ref={listRef}>
                {songs.map((song, index) => (
                    <div key={`${song.id}-${index}`}>
                        {multiDisc && (index === 0 || songs[index - 1].disc !== song.disc) && (
                            <div className={styles.disc}>
                                <Icon name="album" size={16} /> Disco {song.disc || 1}
                            </div>
                        )}
                        {renderRow(song, index)}
                    </div>
                ))}
            </div>
        );
    }

    return (
        <div className={clsx(styles.list, !showAlbum && styles.noAlbum)} ref={listRef} style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {virtualizer.getVirtualItems().map((item) => (
                <div
                    key={item.key}
                    style={{ height: item.size, left: 0, position: 'absolute', right: 0, top: 0, transform: `translateY(${item.start - margin}px)` }}
                >
                    {renderRow(songs[item.index], item.index)}
                </div>
            ))}
        </div>
    );
}
