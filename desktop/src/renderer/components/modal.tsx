import clsx from 'clsx';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { create } from 'zustand';

import i18n from '@/i18n';

import { IconButton, Button, TextInput } from './ui';
import styles from './modal.module.css';

interface ModalEntry {
    id: number;
    title?: string;
    size?: 'lg' | 'md' | 'sm';
    render: (close: () => void) => ReactNode;
    onClose?: () => void;
}

const useModals = create<{ stack: ModalEntry[] }>()(() => ({ stack: [] }));
let counter = 0;

export function openModal(entry: Omit<ModalEntry, 'id'>) {
    const id = ++counter;
    useModals.setState((s) => ({ stack: [...s.stack, { ...entry, id }] }));
    return () => closeModal(id);
}

export function closeModal(id: number) {
    const entry = useModals.getState().stack.find((m) => m.id === id);
    useModals.setState((s) => ({ stack: s.stack.filter((m) => m.id !== id) }));
    entry?.onClose?.();
}

function PromptBody({ close, initial, label, onDone, placeholder }: { close: () => void; initial: string; label: string; placeholder?: string; onDone: (value: null | string) => void }) {
    const [value, setValue] = useState(initial);
    const ref = useRef<HTMLInputElement>(null);
    useEffect(() => {
        ref.current?.focus();
        ref.current?.select();
    }, []);
    return (
        <form
            className={styles.form}
            onSubmit={(e) => {
                e.preventDefault();
                if (!value.trim()) return;
                onDone(value.trim());
                close();
            }}
        >
            <TextInput label={label} onChange={(e) => setValue(e.target.value)} placeholder={placeholder} ref={ref} value={value} />
            <div className={styles.actions}>
                <Button onClick={close} variant="ghost">
                    {i18n.t('common.cancel')}
                </Button>
                <Button disabled={!value.trim()} type="submit" variant="primary">
                    {i18n.t('common.save')}
                </Button>
            </div>
        </form>
    );
}

/** Pede um texto (nome de playlist etc.). */
export function prompt(title: string, label: string, initial = '', placeholder?: string) {
    return new Promise<null | string>((resolve) => {
        let done = false;
        openModal({
            onClose: () => !done && resolve(null),
            render: (close) => (
                <PromptBody
                    close={close}
                    initial={initial}
                    label={label}
                    onDone={(value) => {
                        done = true;
                        resolve(value);
                    }}
                    placeholder={placeholder}
                />
            ),
            size: 'sm',
            title,
        });
    });
}

/** Pede confirmação. */
export function confirm(title: string, message: ReactNode, confirmLabel = i18n.t('common.confirm'), danger = false) {
    return new Promise<boolean>((resolve) => {
        let done = false;
        openModal({
            onClose: () => !done && resolve(false),
            render: (close) => (
                <div className={styles.form}>
                    <div className={styles.message}>{message}</div>
                    <div className={styles.actions}>
                        <Button onClick={close} variant="ghost">
                            {i18n.t('common.cancel')}
                        </Button>
                        <Button
                            onClick={() => {
                                done = true;
                                resolve(true);
                                close();
                            }}
                            variant={danger ? 'danger' : 'primary'}
                        >
                            {confirmLabel}
                        </Button>
                    </div>
                </div>
            ),
            size: 'sm',
            title,
        });
    });
}

export function ModalHost() {
    const stack = useModals((s) => s.stack);
    useEffect(() => {
        if (!stack.length) return;
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') closeModal(stack[stack.length - 1].id);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [stack]);

    return createPortal(
        <>
            {stack.map((m) => (
                <div className={styles.backdrop} key={m.id} onPointerDown={(e) => e.target === e.currentTarget && closeModal(m.id)}>
                    <div aria-modal className={clsx(styles.modal, styles[m.size || 'md'])} role="dialog">
                        {m.title && (
                            <div className={styles.head}>
                                <h2>{m.title}</h2>
                                <IconButton icon="close" label={i18n.t('common.close')} onClick={() => closeModal(m.id)} size={32} />
                            </div>
                        )}
                        <div className={styles.body}>{m.render(() => closeModal(m.id))}</div>
                    </div>
                </div>
            ))}
        </>,
        document.body,
    );
}
