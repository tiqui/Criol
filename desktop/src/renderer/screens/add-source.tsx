import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Icon, type IconName } from '@/components/icon';
import { toast } from '@/components/toast';
import { Button, TextInput } from '@/components/ui';
import { isDesktopApp } from '@/lib/hooks';
import { randomId } from '@/lib/md5';
import { forgetBase, probeServer } from '@/sources/endpoint';
import { loginJellyfin } from '@/sources/jellyfin';
import { pickWebFolder, supportsFolderAccess } from '@/sources/local-web';
import { useSources, webConfig } from '@/sources/store';
import { normalizeServerUrl, pingSubsonic, subsonicAuth } from '@/sources/subsonic';
import { type SourceConfig, SourceError } from '@/sources/types';
import type { SourceType } from '@shared/types';

import styles from './add-source.module.css';

const TYPES: { type: SourceType; icon: IconName }[] = [
    { icon: 'server', type: 'subsonic' },
    { icon: 'server', type: 'jellyfin' },
    { icon: 'folder', type: 'local' },
];

/** Formulário para adicionar um servidor (Navidrome/Subsonic, Jellyfin) ou uma pasta. */
export function AddSourceForm({ onDone }: { onDone?: () => void }) {
    const { t } = useTranslation();
    const preset = webConfig();
    const locked = Boolean(preset.serverLock && preset.serverUrl);
    const canLocal = isDesktopApp() || supportsFolderAccess();
    const [type, setType] = useState<SourceType>(preset.serverType === 'jellyfin' ? 'jellyfin' : 'subsonic');
    const [name, setName] = useState(preset.serverName || '');
    const [url, setUrl] = useState(preset.serverUrl || '');
    const [fallback, setFallback] = useState(preset.serverFallbackUrl || '');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [folder, setFolder] = useState<null | { id: string; label: string; path?: string }>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<null | string>(null);
    const add = useSources((s) => s.add);

    const pickFolder = async () => {
        setError(null);
        try {
            if (window.criolDesktop) {
                const path = await window.criolDesktop.pickFolder(t('sources.pickFolder'));
                if (path) {
                    const label = path.split(/[\\/]/).filter(Boolean).pop() || path;
                    setFolder({ id: randomId(12), label, path });
                    if (!name) setName(label);
                }
            } else {
                const id = randomId(12);
                const label = await pickWebFolder(id);
                setFolder({ id, label });
                if (!name) setName(label);
            }
        } catch (e) {
            if ((e as DOMException)?.name !== 'AbortError') setError(String(e));
        }
    };

    const submit = async () => {
        setError(null);
        setBusy(true);
        try {
            if (type === 'local') {
                if (!folder) throw new SourceError(t('sources.folderRequired'));
                add({ folderName: folder.path ? undefined : folder.label, id: folder.id, name: name.trim() || folder.label, path: folder.path, type: 'local' });
            } else {
                const base = normalizeServerUrl(url);
                if (!url.trim()) throw new SourceError(t('sources.urlRequired'));
                const fallbackUrl = fallback.trim() ? normalizeServerUrl(fallback) : undefined;
                if (type === 'subsonic') {
                    const config = { createdAt: Date.now(), fallbackUrl, id: '', name, subsonic: subsonicAuth(password), type, url: base, username: username.trim() } as const;
                    // Com endereço alternativo, o teste passa para ele se o principal não responder.
                    const info = await pingSubsonic(config).finally(() => forgetBase(''));
                    add({ fallbackUrl, name: name.trim() || (info.type === 'navidrome' ? 'Navidrome' : 'Subsonic'), subsonic: config.subsonic, type, url: base, username: username.trim() });
                } else {
                    let auth;
                    try {
                        auth = await loginJellyfin(base, username.trim(), password);
                    } catch (e) {
                        if (!(fallbackUrl && e instanceof SourceError && e.code === 'network')) throw e;
                        auth = await loginJellyfin(fallbackUrl, username.trim(), password);
                    }
                    add({ fallbackUrl, jellyfin: auth, name: name.trim() || 'Jellyfin', type, url: base, username: username.trim() });
                }
            }
            toast.success(t('sources.added'));
            onDone?.();
        } catch (e) {
            const message = e instanceof SourceError ? (e.code === 'auth' ? t('sources.authFailed') : e.code === 'network' ? t('sources.networkFailed') : e.message) : String(e);
            setError(message);
        } finally {
            setBusy(false);
        }
    };

    return (
        <form
            className={styles.form}
            onSubmit={(e) => {
                e.preventDefault();
                void submit();
            }}
        >
            {!locked && (
                <div className={styles.types} role="radiogroup">
                    {TYPES.filter((o) => o.type !== 'local' || canLocal).map((o) => (
                        <button aria-checked={type === o.type} className={styles.type} key={o.type} onClick={() => setType(o.type)} role="radio" type="button">
                            <Icon name={o.icon} size={22} />
                            <strong>{t(`sources.types.${o.type}`)}</strong>
                            <span>{t(`sources.typeHints.${o.type}`)}</span>
                        </button>
                    ))}
                </div>
            )}

            {type === 'local' ? (
                <div className={styles.folder}>
                    <Button icon="folderOpen" onClick={() => void pickFolder()}>
                        {folder ? t('sources.changeFolder') : t('sources.pickFolder')}
                    </Button>
                    {folder && <span className="truncate">{folder.path || folder.label}</span>}
                </div>
            ) : (
                <>
                    {!locked && (
                        <>
                            <TextInput autoComplete="url" label={t('sources.url')} onChange={(e) => setUrl(e.target.value)} placeholder={type === 'jellyfin' ? 'https://jellyfin.exemplo.com' : 'https://navidrome.exemplo.com'} value={url} />
                            <TextInput autoComplete="off" label={t('sources.fallbackUrl')} onChange={(e) => setFallback(e.target.value)} placeholder="http://100.64.0.1:4533" value={fallback} />
                            <p className={styles.hint}>{t('sources.fallbackHint')}</p>
                        </>
                    )}
                    <TextInput autoComplete="username" label={t('sources.username')} onChange={(e) => setUsername(e.target.value)} value={username} />
                    <TextInput autoComplete="current-password" label={t('sources.password')} onChange={(e) => setPassword(e.target.value)} type="password" value={password} />
                </>
            )}
            {!locked && <TextInput label={t('sources.name')} onChange={(e) => setName(e.target.value)} placeholder={t('sources.namePlaceholder')} value={name} />}

            {error && (
                <div className={styles.error} role="alert">
                    <Icon name="alert" size={18} />
                    <span>{error}</span>
                </div>
            )}

            <Button loading={busy} size="lg" type="submit" variant="primary">
                {type === 'local' ? t('sources.addFolder') : t('sources.connect')}
            </Button>
            {type !== 'local' && <p className={styles.note}>{t('sources.passwordNote')}</p>}
        </form>
    );
}

type Reach = 'fail' | 'ok' | 'testing';

/** Edita os endereços de um servidor já adicionado (principal e alternativo). */
export function ServerAddressForm({ config, onDone }: { config: SourceConfig; onDone?: () => void }) {
    const { t } = useTranslation();
    const update = useSources((s) => s.update);
    const [url, setUrl] = useState(config.url || '');
    const [fallback, setFallback] = useState(config.fallbackUrl || '');
    const [reach, setReach] = useState<{ fallback?: Reach; primary?: Reach }>({});
    const [error, setError] = useState<null | string>(null);

    const test = async () => {
        const primary = url.trim() ? normalizeServerUrl(url) : '';
        const alt = fallback.trim() ? normalizeServerUrl(fallback) : '';
        setReach({ fallback: alt ? 'testing' : undefined, primary: primary ? 'testing' : undefined });
        const [a, b] = await Promise.all([primary ? probeServer(config.type, primary) : null, alt ? probeServer(config.type, alt) : null]);
        setReach({ fallback: b === null ? undefined : b ? 'ok' : 'fail', primary: a === null ? undefined : a ? 'ok' : 'fail' });
    };

    const save = () => {
        if (!url.trim()) {
            setError(t('sources.urlRequired'));
            return;
        }
        const primary = normalizeServerUrl(url);
        const alt = fallback.trim() ? normalizeServerUrl(fallback) : undefined;
        // Os endereços mudaram: o app testa de novo qual usar.
        forgetBase(config.id);
        update(config.id, { fallbackUrl: alt && alt !== primary ? alt : undefined, url: primary });
        toast.success(t('sources.addressesSaved'));
        onDone?.();
    };

    const status = (value?: Reach) =>
        value && <span className={value === 'ok' ? styles.reachOk : value === 'fail' ? styles.reachFail : styles.reachTesting}>{t(`sources.reach.${value}`)}</span>;

    return (
        <form
            className={styles.form}
            onSubmit={(e) => {
                e.preventDefault();
                save();
            }}
        >
            <div className={styles.address}>
                <TextInput autoComplete="url" label={t('sources.url')} onChange={(e) => setUrl(e.target.value)} value={url} />
                {status(reach.primary)}
            </div>
            <div className={styles.address}>
                <TextInput autoComplete="off" label={t('sources.fallbackUrl')} onChange={(e) => setFallback(e.target.value)} placeholder="http://100.64.0.1:4533" value={fallback} />
                {status(reach.fallback)}
            </div>
            <p className={styles.hint}>{t('sources.fallbackHint')}</p>
            {error && (
                <div className={styles.error} role="alert">
                    <Icon name="alert" size={18} />
                    <span>{error}</span>
                </div>
            )}
            <div className={styles.buttons}>
                <Button onClick={() => void test()}>{t('sources.test')}</Button>
                <Button type="submit" variant="primary">
                    {t('common.save')}
                </Button>
            </div>
        </form>
    );
}
