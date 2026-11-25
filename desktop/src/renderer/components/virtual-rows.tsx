import { useVirtualizer } from '@tanstack/react-virtual';
import { type ReactNode, useContext, useLayoutEffect, useRef, useState } from 'react';

import { ScrollContext } from './song-list';

/** Lista genérica com altura fixa por linha, virtualizada quando é grande. */
export function VirtualRows<T>({ getKey, items, render, rowHeight }: { items: T[]; rowHeight: number; render: (item: T, index: number) => ReactNode; getKey: (item: T) => string }) {
    const scrollRef = useContext(ScrollContext);
    const ref = useRef<HTMLDivElement>(null);
    const [margin, setMargin] = useState(0);
    const virtual = items.length > 80;

    useLayoutEffect(() => {
        if (!virtual) return;
        const scroller = scrollRef.current;
        const el = ref.current;
        if (scroller && el) setMargin(el.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop);
    }, [scrollRef, virtual]);

    const virtualizer = useVirtualizer({
        count: virtual ? items.length : 0,
        estimateSize: () => rowHeight,
        getScrollElement: () => scrollRef.current,
        overscan: 10,
        scrollMargin: margin,
    });

    if (!virtual) {
        return (
            <div ref={ref}>
                {items.map((item, i) => (
                    <div key={getKey(item)}>{render(item, i)}</div>
                ))}
            </div>
        );
    }
    return (
        <div ref={ref} style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
            {virtualizer.getVirtualItems().map((row) => (
                <div key={row.key} style={{ height: row.size, left: 0, position: 'absolute', right: 0, top: 0, transform: `translateY(${row.start - margin}px)` }}>
                    {render(items[row.index], row.index)}
                </div>
            ))}
        </div>
    );
}
