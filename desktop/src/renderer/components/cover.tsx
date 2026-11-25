import clsx from 'clsx';
import { memo, useEffect, useState } from 'react';

import { getPlaceholderGradient } from '@/lib/placeholder';
import { useCurrentBase } from '@/sources/endpoint';
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
    // Muda quando o app troca entre o endereço principal e o alternativo do servidor.
    const base = useCurrentBase(config);
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
    }, [base, config, coverId, size]);
    return url;
};

/** Capa com degradê de reserva (cada item tem sempre o mesmo degradê). */
export const Cover = memo(function Cover({ alt = '', className, coverId, icon = 'music', round, seed, size = 'md' }: CoverProps) {
    const url = useCoverUrl(coverId, size);
    const [failed, setFailed] = useState<null | string>(null);
    // Guarda qual endereço terminou de carregar, e não um "carregou" solto: com a imagem em cache
    // o onLoad pode chegar antes de um efeito que zerasse o estado, e a capa ficava invisível.
    const [loadedUrl, setLoadedUrl] = useState<null | string>(null);
    const showImage = url && failed !== url;
    const loaded = loadedUrl === url;

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
                    key={url}
                    loading="lazy"
                    onError={() => setFailed(url)}
                    onLoad={() => setLoadedUrl(url)}
                    ref={(img) => {
                        // Imagem que já veio pronta do cache antes do React ouvir o load.
                        if (img?.complete && img.naturalWidth > 0 && loadedUrl !== url) setLoadedUrl(url);
                    }}
                    src={url}
                />
            )}
        </div>
    );
});
