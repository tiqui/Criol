import type { Mix } from '@/insights/mixes';
import { mixGradient } from '@/insights/mix-text';

import { Cover } from './cover';
import { Icon } from './icon';
import styles from './mix-cover.module.css';

/** Capa das mixes: cor do tipo, até três capas de álbuns e o nome. */
export function MixCover({ label, mix }: { mix: Mix; label: string }) {
    const icon = mix.kind === 'daylist' ? 'sunrise' : mix.kind === 'onRepeat' ? 'infinity' : mix.kind === 'repeatRewind' ? 'history' : mix.kind === 'discover' ? 'compass' : mix.kind === 'daypart' ? 'sun' : 'genre';
    return (
        <div className={styles.mix} style={{ background: mixGradient(mix) }}>
            <div className={styles.covers}>
                {mix.coverIds.slice(0, 3).map((id, i) => (
                    <div className={styles.thumb} key={id} style={{ zIndex: 3 - i }}>
                        <Cover coverId={id} seed={id} size="sm" />
                    </div>
                ))}
            </div>
            <div className={styles.label}>
                <Icon name={icon} size={16} />
                <span>{label}</span>
            </div>
        </div>
    );
}
