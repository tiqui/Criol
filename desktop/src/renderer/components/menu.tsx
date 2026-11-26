import clsx from 'clsx';
import { type ReactNode, type RefObject, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { create } from 'zustand';

import { Icon, type IconName } from './icon';
import styles from './menu.module.css';
import { type Box, placeMenu, type Placement, toBox } from './menu-position';

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
    placement: Placement;
    items: MenuItem[];
    header?: MenuHeader;
}

const useMenu = create<MenuState>()(() => ({ items: [], open: false, placement: { kind: 'point', x: 0, y: 0 } }));

export const closeMenu = () => useMenu.setState({ open: false });

/** Abre um menu no ponto (clique direito) ou abaixo de um elemento (botão "..."). */
export function openMenu(at: { x: number; y: number } | HTMLElement, items: MenuItem[], header?: MenuHeader) {
    const placement: Placement =
        at instanceof HTMLElement ? { anchor: toBox(at.getBoundingClientRect()), kind: 'below' } : { kind: 'point', x: at.x, y: at.y };
    useMenu.setState({ header, items: items.filter(Boolean), open: true, placement });
}

const isMobile = () => window.matchMedia('(max-width: 768px)').matches;

/** Põe o elemento na posição que cabe na janela, antes de ele aparecer na tela. */
function usePlacement(ref: RefObject<HTMLElement | null>, placement: Placement | null, deps: unknown[]) {
    useLayoutEffect(() => {
        const el = ref.current;
        if (!el || !placement) return;
        const { left, top } = placeMenu(
            { height: el.offsetHeight, width: el.offsetWidth },
            { height: window.innerHeight, width: window.innerWidth },
            placement,
        );
        el.style.left = `${left}px`;
        el.style.top = `${top}px`;
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);
}

/** Submenu em posição fixa, ao lado do item que o abriu (vira para a esquerda se não couber). */
function SubMenu({ anchor, depth, items }: { anchor: Box; depth: number; items: MenuItem[] }) {
    const ref = useRef<HTMLDivElement>(null);
    usePlacement(ref, { anchor, kind: 'side' }, [anchor, items]);
    return (
        <div className={styles.submenu} ref={ref}>
            <MenuList depth={depth} items={items} />
        </div>
    );
}

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
    const [sub, setSub] = useState<null | { items: MenuItem[]; anchor: Box }>(null);
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
                                else setSub(sub ? null : { anchor: toBox(e.currentTarget.getBoundingClientRect()), items: item.children! });
                                return;
                            }
                            if (!item.keepOpen) closeMenu();
                            item.onSelect?.();
                        }}
                        onMouseEnter={(e) => {
                            if (isMobile()) return;
                            if (hasChildren) setSub({ anchor: toBox(e.currentTarget.getBoundingClientRect()), items: item.children! });
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
            {sub && <SubMenu anchor={sub.anchor} depth={depth + 1} items={sub.items} />}
        </div>
    );
}

export function MenuHost() {
    const { header, items, open, placement } = useMenu();
    const ref = useRef<HTMLDivElement>(null);
    const mobile = open && isMobile();

    usePlacement(ref, open && !mobile ? placement : null, [open, placement, mobile, items, header]);

    useEffect(() => {
        if (!open) return;
        const onKey = (e: KeyboardEvent) => e.key === 'Escape' && closeMenu();
        // Rolar dentro do menu (lista longa) não fecha; rolar a página fecha.
        const onScroll = (e: WheelEvent) => !isMobile() && !ref.current?.contains(e.target as Node) && closeMenu();
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
            <div className={clsx(styles.menu, mobile && styles.sheet)} ref={ref}>
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
