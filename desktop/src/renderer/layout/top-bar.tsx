import clsx from 'clsx';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate, useSearchParams } from 'react-router';

import { Icon } from '@/components/icon';
import { IconButton, Spinner } from '@/components/ui';
import { syncLibrary, useLibrary } from '@/library/store';
import { desktopPlatform, isDesktopApp } from '@/lib/hooks';
import { routes } from '@/lib/nav';

import { ProfileAvatar } from './profile-avatar';
import styles from './top-bar.module.css';

export function TopBar() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const location = useLocation();
    const [params] = useSearchParams();
    const onSearch = location.pathname === '/search';
    const [query, setQuery] = useState(onSearch ? params.get('q') || '' : '');
    const inputRef = useRef<HTMLInputElement>(null);
    const syncing = useLibrary((s) => s.syncing);
    const progress = useLibrary((s) => s.progress);
    const platform = desktopPlatform();

    useEffect(() => {
        if (onSearch) setQuery(params.get('q') || '');
        else setQuery('');
    }, [onSearch, params]);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'f')) {
                e.preventDefault();
                inputRef.current?.focus();
                inputRef.current?.select();
            }
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, []);

    const canGoBack = (window.history.state?.idx ?? 0) > 0;

    return (
        <div className={clsx(styles.bar, isDesktopApp() && 'drag', platform === 'darwin' && styles.mac, isDesktopApp() && platform !== 'darwin' && styles.overlay)}>
            <div className={clsx(styles.nav, 'no-drag')}>
                <IconButton disabled={!canGoBack} icon="chevronLeft" label={t('nav.back')} onClick={() => navigate(-1)} size={32} variant="surface" />
                <IconButton icon="chevronRight" label={t('nav.forward')} onClick={() => navigate(1)} size={32} variant="surface" />
            </div>
            <form
                className={clsx(styles.search, 'no-drag')}
                onSubmit={(e) => {
                    e.preventDefault();
                    navigate(routes.search(query), { replace: onSearch });
                }}
            >
                <Icon className={styles.searchIcon} name="search" size={18} />
                <input
                    aria-label={t('nav.search')}
                    className={styles.input}
                    onChange={(e) => {
                        setQuery(e.target.value);
                        navigate(routes.search(e.target.value), { replace: onSearch });
                    }}
                    onFocus={() => !onSearch && navigate(routes.search(query))}
                    placeholder={t('search.placeholder')}
                    ref={inputRef}
                    value={query}
                />
                {query && (
                    <button aria-label={t('common.clear')} className={styles.clear} onClick={() => { setQuery(''); navigate(routes.search(''), { replace: true }); inputRef.current?.focus(); }} type="button">
                        <Icon name="close" size={16} />
                    </button>
                )}
            </form>
            <div className={clsx(styles.right, 'no-drag')}>
                {syncing && (
                    <button className={styles.sync} onClick={() => navigate(routes.settings('sources'))} title={t('library.syncing')} type="button">
                        <Spinner size={16} />
                        <span>
                            {t('library.syncing')}
                            {progress?.done ? ` · ${progress.done.toLocaleString()}${progress.total ? `/${progress.total.toLocaleString()}` : ''}` : ''}
                        </span>
                    </button>
                )}
                {!syncing && <IconButton icon="refresh" label={t('library.sync')} onClick={() => void syncLibrary()} size={32} />}
                <button aria-label={t('nav.profile')} className={styles.avatar} onClick={() => navigate(routes.profile())} title={t('nav.profile')} type="button">
                    <ProfileAvatar size={32} />
                </button>
            </div>
        </div>
    );
}
