import { useVirtualizer } from '@tanstack/react-virtual';
import clsx from 'clsx';
import { type MouseEvent, type ReactNode, useContext, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { Cover } from './cover';
import styles from './cards.module.css';
import { Icon, type IconName } from './icon';
import { ScrollContext } from './song-list';

export interface CardProps {
    to: string;
    coverId: null | string;
    seed: string;
    title: string;
    subtitle?: ReactNode;
    round?: boolean;
    icon?: IconName;
    onPlay?: () => void;
    onMenu?: (at: { x: number; y: number } | HTMLElement) => void;
    /** Capa personalizada (mixes). */
    media?: ReactNode;
}

export function Card({ coverId, icon, media, onMenu, onPlay, round, seed, subtitle, title, to }: CardProps) {
    const { t } = useTranslation();
    return (
        <Link
            className={clsx(styles.card, round && styles.roundCard)}
            draggable={false}
            onContextMenu={
                onMenu
                    ? (e: MouseEvent) => {
                          e.preventDefault();
                          onMenu({ x: e.clientX, y: e.clientY });
                      }
                    : undefined
            }
            to={to}
        >
            <div className={styles.media}>
                {media || <Cover coverId={coverId} icon={icon} round={round} seed={seed} size="md" />}
                {onPlay && (
                    <button
                        aria-label={t('actions.play')}
                        className={styles.play}
                        onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            onPlay();
                        }}
                        type="button"
                    >
                        <Icon filled name="mediaPlay" size={20} />
                    </button>
                )}
            </div>
            <div className={styles.text}>
                <span className={clsx(styles.title, 'truncate')}>{title}</span>
                {subtitle && <span className={clsx(styles.subtitle, 'truncate')}>{subtitle}</span>}
            </div>
        </Link>
    );
}

/** Linha horizontal de cartões com título e "Ver tudo". */
export function Carousel({ action, children, subtitle, title }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; children: ReactNode }) {
    const ref = useRef<HTMLDivElement>(null);
    const [edges, setEdges] = useState({ end: false, start: true });
    const update = () => {
        const el = ref.current;
        if (!el) return;
        setEdges({ end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 4, start: el.scrollLeft <= 4 });
    };
    useLayoutEffect(update, [children]);
    const scroll = (dir: number) => ref.current?.scrollBy({ behavior: 'smooth', left: dir * ref.current.clientWidth * 0.85 });

    return (
        <section className={styles.carousel}>
            <div className={styles.carouselHead}>
                <div className={styles.carouselTitles}>
                    <h2>{title}</h2>
                    {subtitle && <p>{subtitle}</p>}
                </div>
                <div className={styles.carouselActions}>
                    {action}
                    <button aria-label="‹" className={styles.arrow} disabled={edges.start} onClick={() => scroll(-1)} type="button">
                        <Icon name="chevronLeft" size={18} />
                    </button>
                    <button aria-label="›" className={styles.arrow} disabled={edges.end} onClick={() => scroll(1)} type="button">
                        <Icon name="chevronRight" size={18} />
                    </button>
                </div>
            </div>
            <div className={styles.track} onScroll={update} ref={ref}>
                {children}
            </div>
        </section>
    );
}

/** Grade de cartões; virtualizada quando a lista é grande. */
export function CardGrid<T>({ items, render, getKey }: { items: T[]; render: (item: T) => ReactNode; getKey: (item: T) => string }) {
    const scrollRef = useContext(ScrollContext);
    const ref = useRef<HTMLDivElement>(null);
    const [layout, setLayout] = useState({ columns: 5, margin: 0, width: 0 });
    const virtual = items.length > 60;

    useLayoutEffect(() => {
        const measure = () => {
            const el = ref.current;
            const scroller = scrollRef.current;
            if (!el || !scroller) return;
            const width = el.clientWidth;
            const min = width < 520 ? 140 : 168;
            const columns = Math.max(2, Math.floor((width + 16) / (min + 16)));
            setLayout({ columns, margin: el.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop, width });
        };
        measure();
        const observer = new ResizeObserver(measure);
        if (ref.current) observer.observe(ref.current);
        return () => observer.disconnect();
    }, [scrollRef]);

    const gap = layout.width < 520 ? 12 : 16;
    const colWidth = layout.width ? (layout.width - gap * (layout.columns - 1)) / layout.columns : 160;
    const rows = Math.ceil(items.length / layout.columns);
    const virtualizer = useVirtualizer({
        count: virtual ? rows : 0,
        estimateSize: () => colWidth + 62,
        getScrollElement: () => scrollRef.current,
        overscan: 3,
        scrollMargin: layout.margin,
    });

    if (!virtual) {
        return (
            <div className={styles.grid} ref={ref} style={{ gridTemplateColumns: `repeat(${layout.columns}, minmax(0, 1fr))` }}>
                {items.map((item) => (
                    <div key={getKey(item)}>{render(item)}</div>
                ))}
            </div>
        );
    }

    return (
        <div ref={ref} style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {virtualizer.getVirtualItems().map((row) => (
                <div
                    className={styles.grid}
                    key={row.key}
                    style={{
                        gridTemplateColumns: `repeat(${layout.columns}, minmax(0, 1fr))`,
                        left: 0,
                        position: 'absolute',
                        right: 0,
                        top: 0,
                        transform: `translateY(${row.start - layout.margin}px)`,
                    }}
                >
                    {items.slice(row.index * layout.columns, row.index * layout.columns + layout.columns).map((item) => (
                        <div key={getKey(item)}>{render(item)}</div>
                    ))}
                </div>
            ))}
        </div>
    );
}
