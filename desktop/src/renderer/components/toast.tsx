import { create } from 'zustand';

import { Icon } from './icon';
import styles from './toast.module.css';

type Kind = 'error' | 'info' | 'success';

interface Toast {
    id: number;
    kind: Kind;
    title: string;
    message?: string;
}

const useToasts = create<{ items: Toast[] }>()(() => ({ items: [] }));
let counter = 0;

const push = (kind: Kind, title: string, message?: string) => {
    const id = ++counter;
    useToasts.setState((s) => ({ items: [...s.items.filter((t) => t.title !== title || t.message !== message).slice(-3), { id, kind, message, title }] }));
    setTimeout(() => dismiss(id), kind === 'error' ? 6000 : 3500);
};

const dismiss = (id: number) => useToasts.setState((s) => ({ items: s.items.filter((t) => t.id !== id) }));

export const toast = {
    error: (title: string, message?: string) => push('error', title, message),
    info: (title: string, message?: string) => push('info', title, message),
    success: (title: string, message?: string) => push('success', title, message),
};

export function Toaster() {
    const items = useToasts((s) => s.items);
    return (
        <div aria-live="polite" className={styles.stack}>
            {items.map((t) => (
                <div className={styles.toast} data-kind={t.kind} key={t.id} onClick={() => dismiss(t.id)} role="status">
                    <Icon className={styles.icon} name={t.kind === 'error' ? 'alert' : t.kind === 'success' ? 'check' : 'info'} size={18} />
                    <div className={styles.text}>
                        <strong>{t.title}</strong>
                        {t.message && <span>{t.message}</span>}
                    </div>
                </div>
            ))}
        </div>
    );
}
