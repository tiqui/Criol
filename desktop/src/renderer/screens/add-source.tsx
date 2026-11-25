import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Icon, type IconName } from '@/components/icon';
import { toast } from '@/components/toast';
import { Button, TextInput } from '@/components/ui';
import { isDesktopApp } from '@/lib/hooks';
import { randomId } from '@/lib/md5';
import { loginJellyfin } from '@/sources/jellyfin';
import { pickWebFolder, supportsFolderAccess } from '@/sources/local-web';
import { useSources, webConfig } from '@/sources/store';
import { normalizeServerUrl, pingSubsonic, subsonicAuth } from '@/sources/subsonic';
import { SourceError } from '@/sources/types';
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
                if (type === 'subsonic') {
                    const config = { createdAt: Date.now(), id: '', name, subsonic: subsonicAuth(password), type, url: base, username: username.trim() } as const;
                    const info = await pingSubsonic(config);
                    add({ name: name.trim() || (info.type === 'navidrome' ? 'Navidrome' : 'Subsonic'), subsonic: config.subsonic, type, url: base, username: username.trim() });
                } else {
                    const auth = await loginJellyfin(base, username.trim(), password);
                    add({ jellyfin: auth, name: name.trim() || 'Jellyfin', type, url: base, username: username.trim() });
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
                    {!locked && <TextInput autoComplete="url" label={t('sources.url')} onChange={(e) => setUrl(e.target.value)} placeholder={type === 'jellyfin' ? 'https://jellyfin.exemplo.com' : 'https://navidrome.exemplo.com'} value={url} />}
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
