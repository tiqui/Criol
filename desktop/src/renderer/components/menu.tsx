import clsx from 'clsx';
import { type ReactNode, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { create } from 'zustand';

import { Icon, type IconName } from './icon';
import styles from './menu.module.css';

export interface MenuItem {
    label?: string;
    icon?: IconName;
    onSelect?: () => void;
    danger?: boolean;
    disabled?: boolean;
    checked?: boolean;
    /** Submenu (ex.: "Adicionar à playlist"). */
    children?: MenuItem[];
    divider?: boolean;
    /** Conteúdo livre (ex.: estrelas de avaliação). */
    custom?: ReactNode;
    /** Não fecha o menu ao clicar (ex.: favoritar, que muda na hora). */
    keepOpen?: boolean;
    /** Rótulo e estado lidos ao vivo (hook chamado pelo item), para o menu aberto acompanhar. */
    useLive?: () => { label?: string; filled?: boolean };
}

interface MenuHeader {
    title: string;
    subtitle?: string;
    media?: ReactNode;
}

interface MenuState {
    open: boolean;
    x: number;
    y: number;
    items: MenuItem[];
    header?: MenuHeader;
}

const useMenu = create<MenuState>()(() => ({ items: [], open: false, x: 0, y: 0 }));

export const closeMenu = () => useMenu.setState({ open: false });

/** Abre um menu no ponto (clique direito) ou abaixo de um elemento (botão "..."). */
export function openMenu(at: { x: number; y: number } | HTMLElement, items: MenuItem[], header?: MenuHeader) {
    let x: number;
    let y: number;
    if (at instanceof HTMLElement) {
        const rect = at.getBoundingClientRect();
        x = rect.right;
        y = rect.bottom + 4;
    } else {
        x = at.x;
        y = at.y;
    }
    useMenu.setState({ header, items: items.filter(Boolean), open: true, x, y });
}

const isMobile = () => window.matchMedia('(max-width: 768px)').matches;

/** Item cujo rótulo e ícone acompanham o estado (ex.: Favoritar / Remover dos favoritos). */
function LiveItem({ item, onHover }: { item: MenuItem; onHover: () => void }) {
    const live = item.useLive!();
    return (
        <button
            className={clsx(styles.item, item.danger && styles.danger)}
            disabled={item.disabled}
            onClick={() => {
                if (!item.keepOpen) closeMenu();
                item.onSelect?.();
            }}
            onMouseEnter={onHover}
            role="menuitem"
            type="button"
        >
            {item.icon ? <Icon filled={live.filled} name={item.icon} size={18} /> : <span className={styles.iconSpace} />}
            <span className={styles.label}>{live.label ?? item.label}</span>
        </button>
    );
}

function MenuList({ depth = 0, items, onBack }: { items: MenuItem[]; depth?: number; onBack?: () => void }) {
    const [sub, setSub] = useState<null | { items: MenuItem[]; top: number }>(null);
    const [mobileSub, setMobileSub] = useState<MenuItem[] | null>(null);
    const ref = useRef<HTMLDivElement>(null);

    if (mobileSub) return <MenuList depth={depth + 1} items={mobileSub} onBack={() => setMobileSub(null)} />;

    return (
        <div className={styles.list} ref={ref} role="menu">
            {onBack && (
                <button className={styles.item} onClick={onBack} role="menuitem" type="button">
                    <Icon name="back" size={18} />
                    <span className={styles.label}>Voltar</span>
                </button>
            )}
            {items.map((item, i) => {
                if (item.divider) return <div className={styles.divider} key={`d${i}`} />;
                if (item.custom) return <div className={styles.custom} key={`c${i}`}>{item.custom}</div>;
                const hasChildren = Boolean(item.children?.length);
                if (item.useLive) return <LiveItem item={item} key={`${item.label}${i}`} onHover={() => setSub(null)} />;
                return (
                    <button
                        className={clsx(styles.item, item.danger && styles.danger)}
                        disabled={item.disabled}
                        key={`${item.label}${i}`}
                        onClick={(e) => {
                            if (hasChildren) {
                                if (isMobile()) setMobileSub(item.children!);
                                else setSub(sub ? null : { items: item.children!, top: (e.currentTarget as HTMLElement).offsetTop });
                                return;
                            }
                            if (!item.keepOpen) closeMenu();
                            item.onSelect?.();
                        }}
                        onMouseEnter={(e) => {
                            if (isMobile()) return;
                            if (hasChildren) setSub({ items: item.children!, top: (e.currentTarget as HTMLElement).offsetTop });
                            else setSub(null);
                        }}
                        role="menuitem"
                        type="button"
                    >
                        {item.icon ? <Icon name={item.icon} size={18} /> : <span className={styles.iconSpace} />}
                        <span className={styles.label}>{item.label}</span>
                        {item.checked && <Icon className={styles.check} name="check" size={16} />}
                        {hasChildren && <Icon className={styles.chevron} name="chevronRight" size={16} />}
                    </button>
                );
            })}
            {sub && (
                <div className={styles.submenu} style={{ top: sub.top - 6 }}>
                    <MenuList depth={depth + 1} items={sub.items} />
                </div>
            )}
        </div>
    );
}

export function MenuHost() {
    const { header, items, open, x, y } = useMenu();
    const ref = useRef<HTMLDivElement>(null);
    const [pos, setPos] = useState({ left: x, top: y });
    const mobile = open && isMobile();

    useLayoutEffect(() => {
        if (!open || mobile || !ref.current) return;
        const rect = ref.current.getBoundingClientRect();
        const left = Math.max(8, Math.min(x - (x === rect.right ? rect.width : 0), window.innerWidth - rect.width - 8));
        const top = y + rect.height > window.innerHeight - 8 ? Math.max(8, y - rect.height) : y;
        setPos({ left: x + rect.width > window.innerWidth - 8 ? Math.max(8, x - rect.width) : left, top });
    }, [open, x, y, mobile, items]);

    useEffect(() => {
        if (!open) return;
        const onKey = (e: KeyboardEvent) => e.key === 'Escape' && closeMenu();
        const onScroll = () => !isMobile() && closeMenu();
        window.addEventListener('keydown', onKey);
        window.addEventListener('resize', closeMenu);
        window.addEventListener('wheel', onScroll, { passive: true });
        return () => {
            window.removeEventListener('keydown', onKey);
            window.removeEventListener('resize', closeMenu);
            window.removeEventListener('wheel', onScroll);
        };
    }, [open]);

    if (!open) return null;

    return createPortal(
        <div className={clsx(styles.backdrop, mobile && styles.backdropMobile)} onContextMenu={(e) => { e.preventDefault(); closeMenu(); }} onPointerDown={(e) => e.target === e.currentTarget && closeMenu()}>
            <div className={clsx(styles.menu, mobile && styles.sheet)} ref={ref} style={mobile ? undefined : { left: pos.left, top: pos.top }}>
                {mobile && <div className={styles.handle} />}
                {header && (
                    <div className={styles.header}>
                        {header.media}
                        <div className={styles.headerText}>
                            <strong className="truncate">{header.title}</strong>
                            {header.subtitle && <span className="truncate">{header.subtitle}</span>}
                        </div>
                    </div>
                )}
                <MenuList items={items} />
            </div>
        </div>,
        document.body,
    );
}
