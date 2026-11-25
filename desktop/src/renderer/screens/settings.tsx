import clsx from 'clsx';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router';

import { Icon } from '@/components/icon';
import { confirm, openModal } from '@/components/modal';
import { Page, PageTitle } from '@/components/page';
import { toast } from '@/components/toast';
import { Button, Chips, IconButton, Select, Switch } from '@/components/ui';
import { LANGUAGES } from '@/i18n';
import { clearListeningHistory, exportListeningHistory, importListeningHistory, useListening } from '@/insights/listening-db';
import { forgetSourceData, syncLibrary, useLibrary } from '@/library/store';
import { isDesktopApp } from '@/lib/hooks';
import { randomId } from '@/lib/md5';
import { routes } from '@/lib/nav';
import { isWebLyricsFolder, lyricsFolderLabel, refreshLyricsFolders } from '@/lyrics/service';
import { type Language, useSettings } from '@/settings/store';
import { getSource, useSources } from '@/sources/store';
import { pickWebFolder, supportsFolderAccess } from '@/sources/local-web';
import { ACCENT_PRESETS, PALETTES } from '@/styles/themes';

import { AddSourceForm } from './add-source';
import styles from './settings.module.css';

type SectionId = 'about' | 'appearance' | 'history' | 'lyrics' | 'playback' | 'sources';
const SECTIONS: SectionId[] = ['sources', 'appearance', 'playback', 'lyrics', 'history', 'about'];

function Row({ children, description, title }: { title: string; description?: ReactNode; children?: ReactNode }) {
    return (
        <div className={styles.row}>
            <div className={styles.rowText}>
                <strong>{title}</strong>
                {description && <span>{description}</span>}
            </div>
            {children && <div className={styles.rowControl}>{children}</div>}
        </div>
    );
}

function Group({ children, title }: { title?: string; children: ReactNode }) {
    return (
        <section className={styles.group}>
            {title && <h2>{title}</h2>}
            <div className={styles.groupBody}>{children}</div>
        </section>
    );
}

// ---------------------------------------------------------------------------

function SourcesSection() {
    const { t } = useTranslation();
    const sources = useSources((s) => s.sources);
    const activeId = useSources((s) => s.activeId);
    const setActive = useSources((s) => s.setActive);
    const remove = useSources((s) => s.remove);
    const syncing = useLibrary((s) => s.syncing);
    const syncedAt = useLibrary((s) => s.syncedAt);
    const songs = useLibrary((s) => s.library.songs.length);

    return (
        <>
            <Group title={t('settings.sources.title')}>
                {sources.map((s) => (
                    <div className={clsx(styles.source, s.id === activeId && styles.sourceActive)} key={s.id}>
                        <div className={styles.sourceIcon}>
                            <Icon name={s.type === 'local' ? 'folder' : 'server'} size={20} />
                        </div>
                        <div className={styles.rowText}>
                            <strong className="truncate">{s.name}</strong>
                            <span className="truncate">
                                {t(`sources.types.${s.type}`)} · {s.path || s.folderName || s.url}
                                {s.username ? ` · ${s.username}` : ''}
                            </span>
                            {s.id === activeId && (
                                <span>
                                    {t('settings.sources.songs', { count: songs, formatted: songs.toLocaleString() })}
                                    {syncedAt ? ` · ${t('settings.sources.syncedAt', { date: new Date(syncedAt).toLocaleString() })}` : ''}
                                </span>
                            )}
                        </div>
                        <div className={styles.sourceActions}>
                            {s.id === activeId ? (
                                <Button icon="refresh" loading={syncing} onClick={() => void syncLibrary()} size="sm">
                                    {s.type === 'local' ? t('settings.sources.rescan') : t('library.sync')}
                                </Button>
                            ) : (
                                <Button onClick={() => setActive(s.id)} size="sm">
                                    {t('settings.sources.use')}
                                </Button>
                            )}
                            <IconButton
                                icon="trash"
                                label={t('settings.sources.remove')}
                                onClick={async () => {
                                    if (!(await confirm(t('settings.sources.remove'), t('settings.sources.removeConfirm', { name: s.name }), t('common.remove'), true))) return;
                                    await getSource(s).dispose?.();
                                    await forgetSourceData(s.id);
                                    remove(s.id);
                                }}
                                size={34}
                            />
                        </div>
                    </div>
                ))}
                <div className={styles.addRow}>
                    <Button icon="add" onClick={() => openModal({ render: (close) => <AddSourceForm onDone={close} />, title: t('sources.add') })} variant="primary">
                        {t('sources.add')}
                    </Button>
                </div>
            </Group>
        </>
    );
}

function AppearanceSection() {
    const { t } = useTranslation();
    const s = useSettings();
    return (
        <>
            <Group title={t('settings.appearance.theme')}>
                <div className={styles.themes}>
                    {PALETTES.map((p) => (
                        <button aria-pressed={s.themeId === p.id} className={styles.theme} key={p.id} onClick={() => s.set({ themeId: p.id })} style={{ background: p.background, color: p.foreground }} type="button">
                            <span className={styles.themePreview}>
                                <i style={{ background: p.surface }} />
                                <i style={{ background: s.accent || p.primary }} />
                            </span>
                            <span className={styles.themeName}>{p.name}</span>
                        </button>
                    ))}
                </div>
            </Group>
            <Group title={t('settings.appearance.accent')}>
                <div className={styles.swatches}>
                    <button aria-pressed={!s.accent} className={clsx(styles.swatch, styles.swatchAuto)} onClick={() => s.set({ accent: null })} title={t('settings.appearance.themeColor')} type="button">
                        A
                    </button>
                    {ACCENT_PRESETS.map((c) => (
                        <button aria-label={c} aria-pressed={s.accent === c} className={styles.swatch} key={c} onClick={() => s.set({ accent: c })} style={{ background: c }} type="button" />
                    ))}
                    <label className={styles.swatch} style={{ background: 'conic-gradient(red, yellow, lime, aqua, blue, magenta, red)' }} title={t('settings.appearance.custom')}>
                        <input onChange={(e) => s.set({ accent: e.target.value })} type="color" value={s.accent || '#3574fc'} />
                    </label>
                </div>
            </Group>
            <Group>
                <Row title={t('settings.appearance.language')}>
                    <Select onChange={(v) => s.set({ language: v as Language })} options={[{ label: t('settings.appearance.system'), value: 'system' }, ...LANGUAGES.map((l) => ({ label: l.label, value: l.value }))]} value={s.language} />
                </Row>
                <Row description={t('settings.appearance.coverColorsHint')} title={t('settings.appearance.coverColors')}>
                    <Switch checked={s.coverColors} onChange={(v) => s.set({ coverColors: v })} />
                </Row>
                <Row description={t('settings.appearance.playerBlurHint')} title={t('settings.appearance.playerBlur')}>
                    <Switch checked={s.playerBlur} onChange={(v) => s.set({ playerBlur: v })} />
                </Row>
                <Row title={t('settings.appearance.showExplore')}>
                    <Switch checked={s.showExplore} onChange={(v) => s.set({ showExplore: v })} />
                </Row>
            </Group>
        </>
    );
}

function PlaybackSection() {
    const { t } = useTranslation();
    const s = useSettings();
    const seconds = [5, 10, 15, 30, 45, 60].map((n) => ({ label: `${n} s`, value: String(n) }));
    return (
        <>
            <Group>
                <Row description={t('settings.playback.resumeQueueHint')} title={t('settings.playback.resumeQueue')}>
                    <Switch checked={s.resumeQueue} onChange={(v) => s.set({ resumeQueue: v })} />
                </Row>
                <Row title={t('settings.playback.skipBack')}>
                    <Select onChange={(v) => s.set({ skipBackSeconds: Number(v) })} options={seconds} value={String(s.skipBackSeconds)} />
                </Row>
                <Row title={t('settings.playback.skipForward')}>
                    <Select onChange={(v) => s.set({ skipForwardSeconds: Number(v) })} options={seconds} value={String(s.skipForwardSeconds)} />
                </Row>
                <Row title={t('settings.playback.showRatings')}>
                    <Switch checked={s.showRatings} onChange={(v) => s.set({ showRatings: v })} />
                </Row>
                <Row description={t('settings.playback.scrobbleHint')} title={t('settings.playback.scrobble')}>
                    <Switch checked={s.scrobble} onChange={(v) => s.set({ scrobble: v })} />
                </Row>
            </Group>
            <Group title="Auto DJ">
                <Row description={t('settings.autoDj.hint')} title={t('settings.autoDj.enabled')}>
                    <Switch checked={s.autoDjEnabled} onChange={(v) => s.set({ autoDjEnabled: v })} />
                </Row>
                <Row title={t('settings.autoDj.mode')}>
                    <Select disabled={!s.autoDjEnabled} onChange={(v) => s.set({ autoDjMode: v })} options={[{ label: t('settings.autoDj.songs'), value: 'songs' }, { label: t('settings.autoDj.albums'), value: 'albums' }]} value={s.autoDjMode} />
                </Row>
                <Row title={t('settings.autoDj.strategy')}>
                    <Select disabled={!s.autoDjEnabled} onChange={(v) => s.set({ autoDjStrategy: v })} options={[{ label: t('settings.autoDj.similar'), value: 'similar' }, { label: t('settings.autoDj.random'), value: 'random' }]} value={s.autoDjStrategy} />
                </Row>
                <Row title={t('settings.autoDj.count')}>
                    <Select disabled={!s.autoDjEnabled} onChange={(v) => s.set({ autoDjCount: Number(v) })} options={[1, 3, 5, 10, 20, 50].map((n) => ({ label: String(n), value: String(n) }))} value={String(s.autoDjCount)} />
                </Row>
                <Row description={t('settings.autoDj.timingHint')} title={t('settings.autoDj.timing')}>
                    <Select disabled={!s.autoDjEnabled} onChange={(v) => s.set({ autoDjTiming: Number(v) })} options={[1, 2, 3, 5, 10].map((n) => ({ label: String(n), value: String(n) }))} value={String(s.autoDjTiming)} />
                </Row>
                <Row title={t('settings.autoDj.allowDuplicates')}>
                    <Switch checked={s.autoDjAllowDuplicates} disabled={!s.autoDjEnabled} onChange={(v) => s.set({ autoDjAllowDuplicates: v })} />
                </Row>
            </Group>
        </>
    );
}

export function LyricsSettings() {
    const { t } = useTranslation();
    const s = useSettings();
    const [count, setCount] = useState<null | number>(null);
    const canPick = isDesktopApp() || supportsFolderAccess();

    useEffect(() => {
        let alive = true;
        if (!s.lyricsFolders.length) {
            setCount(null);
            return;
        }
        void refreshLyricsFolders().then((index) => alive && setCount(index.files.size));
        return () => {
            alive = false;
        };
    }, [s.lyricsFolders]);

    const addFolder = async () => {
        try {
            if (window.criolDesktop) {
                const path = await window.criolDesktop.pickFolder(t('settings.lyrics.pick'));
                if (path && !s.lyricsFolders.includes(path)) s.set({ lyricsFolders: [...s.lyricsFolders, path] });
            } else {
                const key = `lyrics-${randomId(8)}`;
                const name = await pickWebFolder(key);
                s.set({ lyricsFolders: [...s.lyricsFolders, `web:${key}:${name}`] });
            }
        } catch (e) {
            if ((e as DOMException)?.name !== 'AbortError') toast.error(String(e));
        }
    };

    return (
        <>
            <Group title={t('settings.lyrics.folders')}>
                <p className={styles.help}>{t('settings.lyrics.foldersHint')}</p>
                {s.lyricsFolders.map((folder) => (
                    <div className={styles.source} key={folder}>
                        <div className={styles.sourceIcon}>
                            <Icon name="folder" size={20} />
                        </div>
                        <div className={styles.rowText}>
                            <strong className="truncate">{lyricsFolderLabel(folder)}</strong>
                            {isWebLyricsFolder(folder) && <span>{t('settings.lyrics.webFolder')}</span>}
                        </div>
                        <IconButton icon="trash" label={t('common.remove')} onClick={() => s.set({ lyricsFolders: s.lyricsFolders.filter((f) => f !== folder) })} size={34} />
                    </div>
                ))}
                <div className={styles.addRow}>
                    <Button disabled={!canPick} icon="add" onClick={() => void addFolder()}>
                        {t('settings.lyrics.add')}
                    </Button>
                    {count !== null && <span className={styles.help}>{t('settings.lyrics.found', { count })}</span>}
                    {!canPick && <span className={styles.help}>{t('settings.lyrics.unsupported')}</span>}
                </div>
            </Group>
            <Group>
                <Row description={t('settings.lyrics.onlineHint')} title={t('settings.lyrics.online')}>
                    <Switch checked={s.lyricsOnline} onChange={(v) => s.set({ lyricsOnline: v })} />
                </Row>
                <Row title={t('settings.lyrics.fontSize')}>
                    <Select onChange={(v) => s.set({ lyricsFontSize: Number(v) })} options={[20, 24, 26, 30, 34, 40].map((n) => ({ label: `${n} px`, value: String(n) }))} value={String(s.lyricsFontSize)} />
                </Row>
            </Group>
        </>
    );
}

function HistorySection() {
    const { t } = useTranslation();
    const s = useSettings();
    const count = useListening((st) => st.entries.length);
    const sourceId = useLibrary((st) => st.sourceId);
    const fileRef = useRef<HTMLInputElement>(null);

    const exportHistory = () => {
        const blob = new Blob([exportListeningHistory()], { type: 'application/json' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `criol-historico-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    };

    return (
        <Group>
            <Row description={t('settings.history.recordHint')} title={t('settings.history.record')}>
                <Switch checked={s.recordHistory} onChange={(v) => s.set({ recordHistory: v })} />
            </Row>
            <Row description={t('settings.history.count', { count, formatted: count.toLocaleString() })} title={t('settings.history.export')}>
                <Button disabled={!count} icon="download" onClick={exportHistory} size="sm">
                    {t('settings.history.exportButton')}
                </Button>
            </Row>
            <Row description={t('settings.history.importHint')} title={t('settings.history.import')}>
                <Button icon="upload" onClick={() => fileRef.current?.click()} size="sm">
                    {t('settings.history.importButton')}
                </Button>
                <input
                    accept="application/json,.json"
                    hidden
                    onChange={async (e) => {
                        const file = e.target.files?.[0];
                        e.target.value = '';
                        if (!file) return;
                        try {
                            const added = await importListeningHistory(await file.text(), sourceId || '');
                            toast.success(t('settings.history.imported', { count: added }));
                        } catch (error) {
                            toast.error(t('settings.history.importFailed'), String(error));
                        }
                    }}
                    ref={fileRef}
                    type="file"
                />
            </Row>
            <Row description={t('settings.history.clearHint')} title={t('settings.history.clear')}>
                <Button
                    disabled={!count}
                    icon="trash"
                    onClick={async () => {
                        if (await confirm(t('settings.history.clear'), t('settings.history.clearConfirm'), t('common.delete'), true)) await clearListeningHistory();
                    }}
                    size="sm"
                    variant="danger"
                >
                    {t('common.delete')}
                </Button>
            </Row>
        </Group>
    );
}

function AboutSection() {
    const { t } = useTranslation();
    const [version, setVersion] = useState('2.0.0');
    useEffect(() => {
        void window.criolDesktop?.appInfo().then((info) => setVersion(info.version));
    }, []);
    return (
        <Group>
            <div className={styles.about}>
                <img alt="" src="./favicon.svg" />
                <div>
                    <strong>Criol {version}</strong>
                    <span>{isDesktopApp() ? t('settings.about.desktop') : t('settings.about.web')}</span>
                </div>
            </div>
            <p className={styles.help}>{t('settings.about.text')}</p>
            <div className={styles.addRow}>
                <Button icon="web" onClick={() => (window.criolDesktop ? void window.criolDesktop.openExternal('https://github.com/tiqui/Criol') : window.open('https://github.com/tiqui/Criol', '_blank'))} size="sm">
                    GitHub
                </Button>
            </div>
        </Group>
    );
}

export function SettingsScreen() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { section: param } = useParams();
    const section: SectionId = SECTIONS.includes(param as SectionId) ? (param as SectionId) : 'sources';
    return (
        <Page className={styles.settings}>
            <PageTitle>{t('nav.settings')}</PageTitle>
            <Chips onChange={(v) => navigate(routes.settings(v), { replace: true })} options={SECTIONS.map((v) => ({ label: t(`settings.sections.${v}`), value: v }))} value={section} />
            <div className={styles.body}>
                {section === 'sources' && <SourcesSection />}
                {section === 'appearance' && <AppearanceSection />}
                {section === 'playback' && <PlaybackSection />}
                {section === 'lyrics' && <LyricsSettings />}
                {section === 'history' && <HistorySection />}
                {section === 'about' && <AboutSection />}
            </div>
        </Page>
    );
}
