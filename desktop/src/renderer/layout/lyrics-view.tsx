import clsx from 'clsx';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { activeLineIndex } from '@shared/lyrics/parse';
import type { Lyrics } from '@shared/types';

import { Icon } from '@/components/icon';
import { Spinner } from '@/components/ui';
import { getLyrics } from '@/lyrics/service';
import { seek, useCurrentSong, usePosition } from '@/player/store';
import { useSettings } from '@/settings/store';
import { getActiveSource } from '@/sources/store';

import styles from './lyrics-view.module.css';

const SOURCE_LABELS: Record<string, string> = { file: 'lyrics.sourceFile', folder: 'lyrics.sourceFolder', LRCLIB: 'LRCLIB', server: 'lyrics.sourceServer' };

/** Letra da música atual. Sincronizada: acompanha a música e pula ao tocar numa linha. */
export function LyricsView({ large }: { large?: boolean }) {
    const { t } = useTranslation();
    const song = useCurrentSong();
    const [state, setState] = useState<{ id: null | string; loading: boolean; lyrics: Lyrics | null }>({ id: null, loading: false, lyrics: null });
    const fontSize = useSettings((s) => s.lyricsFontSize);

    useEffect(() => {
        if (!song) return;
        const source = getActiveSource();
        if (!source) return;
        let alive = true;
        setState({ id: song.id, loading: true, lyrics: null });
        void getLyrics(source, song).then((lyrics) => alive && setState({ id: song.id, loading: false, lyrics }));
        return () => {
            alive = false;
        };
    }, [song]);

    if (!song) return null;
    if (state.loading || state.id !== song.id) {
        return (
            <div className={styles.center}>
                <Spinner />
            </div>
        );
    }
    if (!state.lyrics) {
        return (
            <div className={styles.center}>
                <Icon name="lyrics" size={32} />
                <p>{t('lyrics.unavailable')}</p>
            </div>
        );
    }
    const label = SOURCE_LABELS[state.lyrics.source] || state.lyrics.source;
    return (
        <div className={clsx(styles.lyrics, large && styles.large)} style={{ '--lyrics-size': `${large ? fontSize : Math.round(fontSize * 0.8)}px` } as React.CSSProperties}>
            {state.lyrics.synced ? <Synced lyrics={state.lyrics} /> : <Plain lyrics={state.lyrics} />}
            <p className={styles.source}>{t('lyrics.source', { source: label.includes('.') ? t(label) : label })}</p>
        </div>
    );
}

function Plain({ lyrics }: { lyrics: Lyrics }) {
    return (
        <div className={styles.plain}>
            {lyrics.lines.map((line, i) => (
                <p key={i}>{line.text || '\u00A0'}</p>
            ))}
        </div>
    );
}

function Synced({ lyrics }: { lyrics: Lyrics }) {
    const position = usePosition((s) => s.position);
    // Um pouco adiantado: a linha acende junto com a voz.
    const active = activeLineIndex(lyrics.lines, position + 250);
    const containerRef = useRef<HTMLDivElement>(null);
    const userScroll = useRef(0);

    useEffect(() => {
        const container = containerRef.current;
        if (!container || Date.now() - userScroll.current < 3000) return;
        const el = container.querySelector<HTMLElement>(`[data-line="${Math.max(0, active)}"]`);
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, [active]);

    return (
        <div className={styles.synced} onTouchMove={() => (userScroll.current = Date.now())} onWheel={() => (userScroll.current = Date.now())} ref={containerRef}>
            {lyrics.lines.map((line, i) => (
                <button
                    className={clsx(styles.line, i === active && styles.active, i < active && styles.past)}
                    data-line={i}
                    key={i}
                    onClick={() => {
                        userScroll.current = 0;
                        seek(line.time ?? 0);
                    }}
                    type="button"
                >
                    {line.text || '♪'}
                </button>
            ))}
        </div>
    );
}
