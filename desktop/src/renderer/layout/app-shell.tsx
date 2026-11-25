import clsx from 'clsx';
import { useEffect, useRef } from 'react';
import { Outlet, useLocation, useNavigationType } from 'react-router';

import { ScrollContext } from '@/components/song-list';
import { useIsMobile } from '@/lib/hooks';
import { usePlayer } from '@/player/store';
import { useSettings } from '@/settings/store';

import styles from './app-shell.module.css';
import { FullPlayer } from './full-player';
import { MiniPlayer } from './mini-player';
import { MobileNav } from './mobile-nav';
import { PlayerBar } from './player-bar';
import { Sidebar } from './sidebar';
import { SidePanel } from './side-panel';
import { TopBar } from './top-bar';

/** Guarda a rolagem de cada página para voltar no mesmo lugar ao usar "Voltar". */
const scrollPositions = new Map<string, number>();

export function AppShell() {
    const scrollRef = useRef<HTMLElement>(null);
    const location = useLocation();
    const navType = useNavigationType();
    const isMobile = useIsMobile();
    const collapsed = useSettings((s) => s.sidebarCollapsed);
    const panel = useSettings((s) => s.sidePanel);
    const panelWidth = useSettings((s) => s.sidePanelWidth);
    const hasSong = usePlayer((s) => s.index >= 0);
    const fullscreen = usePlayer((s) => s.fullscreen);

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        const key = location.key;
        el.scrollTop = navType === 'POP' ? scrollPositions.get(key) || 0 : 0;
        return () => {
            scrollPositions.set(key, el.scrollTop);
        };
    }, [location.key, navType]);

    const showPanel = !isMobile && panel !== 'none' && hasSong;

    return (
        <ScrollContext.Provider value={scrollRef}>
            <div
                className={clsx(styles.shell, isMobile ? styles.mobile : styles.desktop, collapsed && styles.collapsed, showPanel && styles.withPanel)}
                style={{ '--panel-w': `${panelWidth}px` } as React.CSSProperties}
            >
                {!isMobile && <TopBar />}
                {!isMobile && <Sidebar />}
                <main className={styles.main} ref={scrollRef}>
                    <div className={styles.content}>
                        <Outlet />
                    </div>
                </main>
                {showPanel && <SidePanel />}
                {isMobile ? (
                    <div className={styles.bottom}>
                        <MiniPlayer />
                        <MobileNav />
                    </div>
                ) : (
                    <PlayerBar />
                )}
                {fullscreen && <FullPlayer />}
            </div>
        </ScrollContext.Provider>
    );
}
