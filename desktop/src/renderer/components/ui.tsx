import clsx from 'clsx';
import { type ButtonHTMLAttributes, forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';

import { Icon, type IconName } from './icon';
import styles from './ui.module.css';

type ButtonVariant = 'danger' | 'ghost' | 'primary' | 'secondary';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: ButtonVariant;
    size?: 'lg' | 'md' | 'sm';
    icon?: IconName;
    loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
    { children, className, disabled, icon, loading, size = 'md', type = 'button', variant = 'secondary', ...rest },
    ref,
) {
    return (
        <button
            className={clsx(styles.button, styles[variant], styles[size], className)}
            disabled={disabled || loading}
            ref={ref}
            type={type}
            {...rest}
        >
            {loading ? <Spinner size={size === 'sm' ? 14 : 16} /> : icon && <Icon name={icon} size={size === 'sm' ? 16 : 18} />}
            {children && <span>{children}</span>}
        </button>
    );
});

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    icon: IconName;
    label: string;
    size?: number;
    iconSize?: number;
    active?: boolean;
    filled?: boolean;
    variant?: 'filled' | 'ghost' | 'surface';
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
    { active, className, filled, icon, iconSize, label, size = 36, style, type = 'button', variant = 'ghost', ...rest },
    ref,
) {
    return (
        <button
            aria-label={label}
            aria-pressed={active}
            className={clsx(styles.iconButton, variant === 'filled' ? styles.iconFilled : variant === 'surface' ? styles.iconSurface : styles.iconGhost, active && styles.active, className)}
            ref={ref}
            style={{ height: size, width: size, ...style }}
            title={label}
            type={type}
            {...rest}
        >
            <Icon filled={filled} name={icon} size={iconSize || Math.round(size * 0.55)} />
        </button>
    );
});

export function Spinner({ className, size = 20 }: { className?: string; size?: number }) {
    return <Icon className={clsx(styles.spinner, className)} name="loader" size={size} />;
}

export function Empty({ action, icon = 'music', text, title }: { icon?: IconName; title: string; text?: ReactNode; action?: ReactNode }) {
    return (
        <div className={styles.empty}>
            <div className={styles.emptyIcon}>
                <Icon name={icon} size={30} />
            </div>
            <h3>{title}</h3>
            {text && <p>{text}</p>}
            {action}
        </div>
    );
}

export function Switch({ checked, disabled, label, onChange }: { checked: boolean; onChange: (value: boolean) => void; label?: string; disabled?: boolean }) {
    return (
        <button
            aria-checked={checked}
            aria-label={label}
            className={styles.switch}
            disabled={disabled}
            onClick={() => onChange(!checked)}
            role="switch"
            type="button"
        >
            <span className={styles.thumb} />
        </button>
    );
}

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { icon?: IconName; label?: string; error?: string }>(
    function TextInput({ className, error, icon, label, ...rest }, ref) {
        const input = (
            <div className={clsx(styles.inputWrap, error && styles.inputError, className)}>
                {icon && <Icon className={styles.inputIcon} name={icon} size={18} />}
                <input className={styles.input} ref={ref} {...rest} />
            </div>
        );
        if (!label) return input;
        return (
            <label className={styles.field}>
                <span className={styles.fieldLabel}>{label}</span>
                {input}
                {error && <span className={styles.fieldError}>{error}</span>}
            </label>
        );
    },
);

export function Select<T extends string>({
    className,
    onChange,
    options,
    value,
    ...rest
}: Omit<SelectHTMLAttributes<HTMLSelectElement>, 'onChange'> & { value: T; options: { label: string; value: T }[]; onChange: (value: T) => void }) {
    return (
        <div className={clsx(styles.selectWrap, className)}>
            <select className={styles.select} onChange={(e) => onChange(e.target.value as T)} value={value} {...rest}>
                {options.map((o) => (
                    <option key={o.value} value={o.value}>
                        {o.label}
                    </option>
                ))}
            </select>
            <Icon className={styles.selectIcon} name="chevronDown" size={16} />
        </div>
    );
}

export function Segmented<T extends string>({ onChange, options, value }: { value: T; options: { label: string; value: T; icon?: IconName }[]; onChange: (value: T) => void }) {
    return (
        <div className={styles.segmented} role="tablist">
            {options.map((o) => (
                <button aria-selected={o.value === value} className={styles.segment} key={o.value} onClick={() => onChange(o.value)} role="tab" type="button">
                    {o.icon && <Icon name={o.icon} size={16} />}
                    {o.label}
                </button>
            ))}
        </div>
    );
}

/** Abas em pílulas, roláveis no celular. */
export function Chips<T extends string>({ onChange, options, value }: { value: T; options: { label: string; value: T }[]; onChange: (value: T) => void }) {
    return (
        <div className={styles.chips} role="tablist">
            {options.map((o) => (
                <button aria-selected={o.value === value} className={styles.chip} key={o.value} onClick={() => onChange(o.value)} role="tab" type="button">
                    {o.label}
                </button>
            ))}
        </div>
    );
}

export function Stars({ onChange, size = 16, value }: { value: number; onChange?: (value: number) => void; size?: number }) {
    return (
        <div className={styles.stars} role={onChange ? 'radiogroup' : undefined}>
            {[1, 2, 3, 4, 5].map((n) => (
                <button
                    aria-label={`${n}`}
                    className={clsx(styles.star, n <= value && styles.starOn)}
                    disabled={!onChange}
                    key={n}
                    onClick={(e) => {
                        e.stopPropagation();
                        onChange?.(n === value ? 0 : n);
                    }}
                    type="button"
                >
                    <Icon filled={n <= value} name="star" size={size} />
                </button>
            ))}
        </div>
    );
}
