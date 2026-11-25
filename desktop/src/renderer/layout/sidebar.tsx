import clsx from 'clsx';
import { useTranslation } from 'react-i18next';
import { NavLink, useNavigate } from 'react-router';

import { openPlaylistMenu } from '@/components/actions';
import { Icon, type IconName } from '@/components/icon';
import { openMenu } from '@/components/menu';
import { prompt } from '@/components/modal';
import i18n from '@/i18n';
import { IconButton } from '@/components/ui';
import { createPlaylist, useLibrary } from '@/library/store';
import { desktopPlatform, isDesktopApp } from '@/lib/hooks';
import { navigate as go, routes } from '@/lib/nav';
import { useSettings } from '@/settings/store';
import { useSources } from '@/sources/store';

import { ProfileAvatar, useProfileName } from './profile-avatar';
import styles from './sidebar.module.css';

function Item({ end, icon, label, to }: { to: string; icon: IconName; label: string; end?: boolean }) {
    return (
        <NavLink className={({ isActive }) => clsx(styles.item, isActive && styles.active)} end={end} title={label} to={to}>
            <Icon name={icon} size={20} />
            <span className={styles.label}>{label}</span>
        </NavLink>
    );
}

/** Troca de servidor/pasta. */
export function openSourceMenu(at: HTMLElement) {
    const { activeId, setActive, sources } = useSources.getState();
    openMenu(at, [
        ...sources.map((s) => ({
            checked: s.id === activeId,
            icon: (s.type === 'local' ? 'folder' : 'server') as IconName,
            label: s.name,
            onSelect: () => setActive(s.id),
        })),
        { divider: true },
        { icon: 'settings', label: i18n.t('sources.manage'), onSelect: () => go(routes.settings('sources')) },
    ]);
}

export function Sidebar() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const playlists = useLibrary((s) => s.playlists);
    const library = useLibrary((s) => s.library);
    const collapsed = useSettings((s) => s.sidebarCollapsed);
    const setSettings = useSettings((s) => s.set);
    const sources = useSources((s) => s.sources);
    const activeId = useSources((s) => s.activeId);
    const active = sources.find((s) => s.id === activeId);
    const name = useProfileName();
    const mac = isDesktopApp() && desktopPlatform() === 'darwin';

    return (
        <aside className={clsx(styles.sidebar, collapsed && styles.collapsed)}>
            <div className={clsx(styles.brand, isDesktopApp() && 'drag', mac && styles.mac)}>
                <img alt="" className={styles.logo} src="./favicon.svg" />
                <span className={styles.brandName}>Criol</span>
                <IconButton
                    className={clsx(styles.collapse, 'no-drag')}
                    icon="panelLeft"
                    label={collapsed ? t('nav.expand') : t('nav.collapse')}
                    onClick={() => setSettings({ sidebarCollapsed: !collapsed })}
                    size={30}
                />
            </div>

            {active && (
                <button className={styles.source} onClick={(e) => openSourceMenu(e.currentTarget)} title={active.name} type="button">
                    <Icon name={active.type === 'local' ? 'folder' : 'server'} size={18} />
                    <span className={styles.label}>{active.name}</span>
                    <Icon className={styles.label} name="chevronDown" size={16} />
                </button>
            )}

            <nav className={styles.nav}>
                <Item end icon="home" label={t('nav.home')} to="/" />
                <Item icon="search" label={t('nav.search')} to="/search" />
                <div className={styles.group}>{t('nav.library')}</div>
                <Item icon="music" label={t('library.songs')} to="/library/songs" />
                <Item icon="album" label={t('library.albums')} to="/library/albums" />
                <Item icon="artist" label={t('library.artists')} to="/library/artists" />
                <Item icon="genre" label={t('library.genres')} to="/library/genres" />
                {library.hasFolders && <Item icon="folder" label={t('library.folders')} to="/library/folders" />}
                <Item icon="favorite" label={t('library.favorites')} to="/library/favorites" />
            </nav>

            <div className={styles.playlistsHead}>
                <NavLink className={({ isActive }) => clsx(styles.group, styles.groupLink, isActive && styles.active)} to="/library/playlists">
                    <Icon className={styles.collapsedOnly} name="playlist" size={20} />
                    <span className={styles.label}>{t('library.playlists')}</span>
                </NavLink>
                <IconButton
                    className={styles.label}
                    icon="add"
                    label={t('playlist.new')}
                    onClick={async () => {
                        const value = await prompt(t('playlist.new'), t('playlist.name'));
                        if (value) {
                            const created = await createPlaylist(value);
                            if (created) navigate(routes.playlist(created.id));
                        }
                    }}
                    size={28}
                />
            </div>
            <div className={styles.playlists}>
                {playlists.map((p) => (
                    <NavLink
                        className={({ isActive }) => clsx(styles.playlist, isActive && styles.active)}
                        key={p.id}
                        onContextMenu={(e) => {
                            e.preventDefault();
                            openPlaylistMenu({ x: e.clientX, y: e.clientY }, p, p.songIds.map((id) => library.songsById.get(id)).filter((s) => s !== undefined));
                        }}
                        title={p.name}
                        to={routes.playlist(p.id)}
                    >
                        <span className={clsx(styles.label, 'truncate')}>{p.name}</span>
                    </NavLink>
                ))}
            </div>

            <div className={styles.footer}>
                <NavLink className={({ isActive }) => clsx(styles.item, styles.profile, isActive && styles.active)} title={t('nav.profile')} to="/profile">
                    <ProfileAvatar size={26} />
                    <span className={clsx(styles.label, 'truncate')}>{name || t('nav.profile')}</span>
                </NavLink>
                <NavLink className={({ isActive }) => clsx(styles.item, isActive && styles.active)} title={t('nav.settings')} to="/settings">
                    <Icon name="settings" size={20} />
                    <span className={styles.label}>{t('nav.settings')}</span>
                </NavLink>
            </div>
        </aside>
    );
}
