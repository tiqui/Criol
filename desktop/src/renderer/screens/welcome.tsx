import { useTranslation } from 'react-i18next';

import { AddSourceForm } from './add-source';
import styles from './welcome.module.css';

/** Primeira abertura: escolher de onde vêm as músicas. */
export function Welcome() {
    const { t } = useTranslation();
    return (
        <div className={styles.welcome}>
            <div className={styles.card}>
                <img alt="" className={styles.logo} src="./favicon.svg" />
                <h1>{t('welcome.title')}</h1>
                <p>{t('welcome.text')}</p>
                <AddSourceForm />
            </div>
        </div>
    );
}
