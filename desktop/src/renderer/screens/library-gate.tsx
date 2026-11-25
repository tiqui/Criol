import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { Page } from '@/components/page';
import { Button, Empty, Spinner } from '@/components/ui';
import { activateSource, syncLibrary, useLibrary } from '@/library/store';
import { routes } from '@/lib/nav';
import { getSource, useActiveConfig } from '@/sources/store';

import styles from './library-gate.module.css';
import { Welcome } from './welcome';

/** Mostra a tela de boas-vindas, o carregamento ou os avisos antes do conteúdo da biblioteca. */
export function LibraryGate({ children }: { children: ReactNode }) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const config = useActiveConfig();
    const loaded = useLibrary((s) => s.loaded);
    const empty = useLibrary((s) => s.library.isEmpty);
    const syncing = useLibrary((s) => s.syncing);
    const progress = useLibrary((s) => s.progress);
    const error = useLibrary((s) => s.error);

    if (!config) return <Welcome />;

    if (error === 'permission') {
        return (
            <Page>
                <Empty
                    action={
                        <Button
                            icon="folderOpen"
                            onClick={async () => {
                                const source = getSource(config);
                                if (await source.requestPermission?.()) void activateSource(config);
                            }}
                            variant="primary"
                        >
                            {t('sources.allowAccess')}
                        </Button>
                    }
                    icon="folder"
                    text={t('sources.allowAccessText', { name: config.name })}
                    title={t('sources.allowAccessTitle')}
                />
            </Page>
        );
    }

    if (!loaded || (empty && syncing)) {
        return (
            <div className={styles.loading}>
                <Spinner size={28} />
                <strong>{t('library.loading')}</strong>
                {progress && (
                    <span>
                        {t(`library.progress.${progress.label}`, { defaultValue: progress.label })}
                        {progress.done ? ` · ${progress.done.toLocaleString()}${progress.total ? ` / ${progress.total.toLocaleString()}` : ''}` : ''}
                    </span>
                )}
            </div>
        );
    }

    if (empty) {
        return (
            <Page>
                <Empty
                    action={
                        <div className={styles.actions}>
                            <Button icon="refresh" onClick={() => void syncLibrary()} variant="primary">
                                {t('library.syncAgain')}
                            </Button>
                            <Button onClick={() => navigate(routes.settings('sources'))}>{t('sources.manage')}</Button>
                        </div>
                    }
                    icon="music"
                    text={error ? error : t('library.emptyText')}
                    title={t('library.emptyTitle')}
                />
            </Page>
        );
    }

    return <>{children}</>;
}
