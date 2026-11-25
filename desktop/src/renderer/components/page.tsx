import clsx from 'clsx';
import { type ReactNode, useEffect, useState } from 'react';

import { coverColor, seedColor } from '@/lib/color';
import { useSettings } from '@/settings/store';
import { getSource, useActiveConfig } from '@/sources/store';

import { Cover } from './cover';
import type { IconName } from './icon';
import styles from './page.module.css';

/** Cor da capa (ou da semente) para o fundo dos cabeçalhos. */
export const useHeaderColor = (coverId: null | string | undefined, seed: string) => {
    const config = useActiveConfig();
    const enabled = useSettings((s) => s.coverColors);
    const [color, setColor] = useState<null | string>(null);
    useEffect(() => {
        setColor(null);
        if (!enabled || !coverId || !config) return;
        let alive = true;
        void coverColor(getSource(config).coverUrl(coverId, 96)).then((c) => alive && setColor(c));
        return () => {
            alive = false;
        };
    }, [config, coverId, enabled]);
    if (!enabled) return null;
    return color || seedColor(seed);
};

interface HeroProps {
    label?: string;
    title: string;
    coverId?: null | string;
    seed: string;
    round?: boolean;
    icon?: IconName;
    media?: ReactNode;
    meta?: ReactNode;
    description?: ReactNode;
    actions?: ReactNode;
}

/** Cabeçalho das páginas de álbum, artista, playlist, mix... */
export function Hero({ actions, coverId, description, icon, label, media, meta, round, seed, title }: HeroProps) {
    const color = useHeaderColor(coverId, seed);
    return (
        <header className={styles.hero} style={color ? { '--hero-color': color } as React.CSSProperties : undefined}>
            <div className={styles.heroInner}>
                <div className={clsx(styles.heroMedia, round && styles.round)}>
                    {media || <Cover coverId={coverId} icon={icon} round={round} seed={seed} size="lg" />}
                </div>
                <div className={styles.heroText}>
                    {label && <span className={styles.label}>{label}</span>}
                    <h1 className={clsx(styles.title, title.length > 40 && styles.titleLong)}>{title}</h1>
                    {description && <p className={styles.description}>{description}</p>}
                    {meta && <div className={styles.meta}>{meta}</div>}
                </div>
            </div>
            {actions && <div className={styles.actions}>{actions}</div>}
        </header>
    );
}

/** Título simples de página (Biblioteca, Configurações...). */
export function PageTitle({ actions, children, subtitle }: { children: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
    return (
        <div className={styles.pageTitle}>
            <div className={styles.pageTitleText}>
                <h1>{children}</h1>
                {subtitle && <p>{subtitle}</p>}
            </div>
            {actions && <div className={styles.pageTitleActions}>{actions}</div>}
        </div>
    );
}

export function Page({ children, className, flush }: { children: ReactNode; className?: string; flush?: boolean }) {
    return <div className={clsx(styles.page, flush && styles.flush, className)}>{children}</div>;
}

export function Section({ action, children, title }: { title?: ReactNode; action?: ReactNode; children: ReactNode }) {
    return (
        <section className={styles.section}>
            {(title || action) && (
                <div className={styles.sectionHead}>
                    {title && <h2>{title}</h2>}
                    {action}
                </div>
            )}
            {children}
        </section>
    );
}
