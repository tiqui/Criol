import clsx from 'clsx';
import { useTranslation } from 'react-i18next';
import { NavLink, useLocation } from 'react-router';

import { Icon } from '@/components/icon';

import styles from './mobile-nav.module.css';
import { ProfileAvatar } from './profile-avatar';

const LIBRARY_PATHS = ['/library', '/album', '/artist', '/genre', '/folder', '/playlist'];

/** Navegação inferior no celular, com os mesmos destinos da barra lateral. */
export function MobileNav() {
    const { t } = useTranslation();
    const { pathname } = useLocation();
    const inLibrary = LIBRARY_PATHS.some((p) => pathname.startsWith(p));

    return (
        <nav aria-label={t('nav.main')} className={styles.nav}>
            <NavLink className={({ isActive }) => clsx(styles.item, isActive && styles.active)} end to="/">
                <Icon name="home" size={24} />
                <span>{t('nav.home')}</span>
            </NavLink>
            <NavLink className={({ isActive }) => clsx(styles.item, isActive && styles.active)} to="/search">
                <Icon name="search" size={24} />
                <span>{t('nav.search')}</span>
            </NavLink>
            <NavLink className={clsx(styles.item, inLibrary && styles.active)} to="/library">
                <Icon name="library" size={24} />
                <span>{t('nav.library')}</span>
            </NavLink>
            <NavLink className={({ isActive }) => clsx(styles.item, (isActive || pathname.startsWith('/recap') || pathname.startsWith('/settings')) && styles.active)} to="/profile">
                <ProfileAvatar size={26} />
                <span>{t('nav.profile')}</span>
            </NavLink>
        </nav>
    );
}
