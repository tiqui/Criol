import clsx from 'clsx';
import { memo, useEffect, useState } from 'react';

import { getPlaceholderGradient } from '@/lib/placeholder';
import { useActiveConfig, getSource } from '@/sources/store';
import { resolveIdbArt } from '@/sources/local-web';

import styles from './cover.module.css';
import { Icon, type IconName } from './icon';

interface CoverProps {
    coverId: null | string | undefined;
    /** Semente do degradê quando não há capa (id do item). */
    seed: string;
    /** Tamanho pedido ao servidor. */
    size?: 'lg' | 'md' | 'sm';
    round?: boolean;
    className?: string;
    icon?: IconName;
    alt?: string;
}

const SIZES = { lg: 600, md: 300, sm: 96 };

/** Endereço da capa na fonte ativa. */
export const useCoverUrl = (coverId: null | string | undefined, size: 'lg' | 'md' | 'sm' = 'md') => {
    const config = useActiveConfig();
    const [url, setUrl] = useState<null | string>(null);
    useEffect(() => {
        if (!coverId || !config) {
            setUrl(null);
            return;
        }
        const raw = getSource(config).coverUrl(coverId, SIZES[size]);
        if (raw?.startsWith('idb-art:')) {
            let alive = true;
            void resolveIdbArt(raw).then((resolved) => alive && setUrl(resolved));
            return () => {
                alive = false;
            };
        }
        setUrl(raw);
    }, [config, coverId, size]);
    return url;
};

/** Capa com degradê de reserva (cada item tem sempre o mesmo degradê). */
export const Cover = memo(function Cover({ alt = '', className, coverId, icon = 'music', round, seed, size = 'md' }: CoverProps) {
    const url = useCoverUrl(coverId, size);
    const [failed, setFailed] = useState<null | string>(null);
    const [loaded, setLoaded] = useState(false);
    const showImage = url && failed !== url;

    useEffect(() => setLoaded(false), [url]);

    return (
        <div className={clsx(styles.cover, round && styles.round, className)} style={{ background: getPlaceholderGradient(seed) }}>
            {!showImage && (
                <div className={styles.fallback}>
                    <Icon name={icon} size={28} strokeWidth={1.6} />
                </div>
            )}
            {showImage && (
                <img
                    alt={alt}
                    className={clsx(styles.img, loaded && styles.loaded)}
                    decoding="async"
                    draggable={false}
                    loading="lazy"
                    onError={() => setFailed(url)}
                    onLoad={() => setLoaded(true)}
                    src={url}
                />
            )}
        </div>
    );
});
