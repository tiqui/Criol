import clsx from 'clsx';
import { useRef, useState } from 'react';

import styles from './slider.module.css';

interface SliderProps {
    value: number;
    max: number;
    /** Durante o arraste. */
    onChange?: (value: number) => void;
    /** Ao soltar. */
    onCommit: (value: number) => void;
    buffered?: number;
    label: string;
    className?: string;
    /** Texto mostrado acima do ponteiro (ex.: tempo). */
    formatTooltip?: (value: number) => string;
    step?: number;
}

/** Barra deslizante para progresso e volume (mouse, toque e teclado). */
export function Slider({ buffered, className, formatTooltip, label, max, onChange, onCommit, step, value }: SliderProps) {
    const ref = useRef<HTMLDivElement>(null);
    const [drag, setDrag] = useState<null | number>(null);
    const [hover, setHover] = useState<null | number>(null);
    const shown = drag ?? value;
    const pct = max > 0 ? Math.min(100, Math.max(0, (shown / max) * 100)) : 0;

    const valueAt = (clientX: number) => {
        const rect = ref.current!.getBoundingClientRect();
        const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
        return ratio * max;
    };

    return (
        <div
            aria-label={label}
            aria-valuemax={max}
            aria-valuemin={0}
            aria-valuenow={Math.round(shown)}
            className={clsx(styles.slider, drag !== null && styles.dragging, className)}
            onKeyDown={(e) => {
                const delta = step ?? max / 20;
                if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
                    e.preventDefault();
                    onCommit(Math.min(max, value + delta));
                } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
                    e.preventDefault();
                    onCommit(Math.max(0, value - delta));
                }
            }}
            onPointerDown={(e) => {
                if (max <= 0) return;
                e.currentTarget.setPointerCapture(e.pointerId);
                const v = valueAt(e.clientX);
                setDrag(v);
                onChange?.(v);
            }}
            onPointerLeave={() => setHover(null)}
            onPointerMove={(e) => {
                if (max <= 0) return;
                const v = valueAt(e.clientX);
                if (e.pointerType === 'mouse') setHover(v);
                if (drag !== null) {
                    setDrag(v);
                    onChange?.(v);
                }
            }}
            onPointerUp={(e) => {
                if (drag === null) return;
                const v = valueAt(e.clientX);
                setDrag(null);
                onCommit(v);
            }}
            ref={ref}
            role="slider"
            tabIndex={0}
        >
            <div className={styles.track}>
                {buffered !== undefined && max > 0 && <div className={styles.buffered} style={{ width: `${Math.min(100, (buffered / max) * 100)}%` }} />}
                <div className={styles.fill} style={{ width: `${pct}%` }} />
            </div>
            <div className={styles.thumb} style={{ left: `${pct}%` }} />
            {formatTooltip && (drag ?? hover) !== null && (
                <div className={styles.tooltip} style={{ left: `${Math.min(100, Math.max(0, (((drag ?? hover) as number) / max) * 100))}%` }}>
                    {formatTooltip((drag ?? hover) as number)}
                </div>
            )}
        </div>
    );
}
