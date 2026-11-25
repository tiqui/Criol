import { useTranslation } from 'react-i18next';

import { IconButton, Segmented } from '@/components/ui';
import { useSettings } from '@/settings/store';

import { LyricsView } from './lyrics-view';
import { QueueView } from './queue-view';
import styles from './side-panel.module.css';

/** Painel à direita (desktop) com a fila ou a letra. */
export function SidePanel() {
    const { t } = useTranslation();
    const panel = useSettings((s) => s.sidePanel);
    const set = useSettings((s) => s.set);
    return (
        <aside className={styles.panel}>
            <div className={styles.head}>
                <Segmented
                    onChange={(value) => set({ sidePanel: value })}
                    options={[
                        { icon: 'queue', label: t('player.queue'), value: 'queue' },
                        { icon: 'lyrics', label: t('player.lyrics'), value: 'lyrics' },
                    ]}
                    value={panel === 'lyrics' ? 'lyrics' : 'queue'}
                />
                <IconButton icon="close" label={t('common.close')} onClick={() => set({ sidePanel: 'none' })} size={30} />
            </div>
            <div className={styles.body}>{panel === 'lyrics' ? <LyricsView /> : <QueueView />}</div>
        </aside>
    );
}
