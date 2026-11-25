import clsx from 'clsx';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router';

import type { Song } from '@shared/types';

import { openAlbumMenu, openArtistMenu, openPlaylistMenu } from '@/components/actions';
import { Card, CardGrid } from '@/components/cards';
import { Cover } from '@/components/cover';
import { Icon } from '@/components/icon';
import { prompt } from '@/components/modal';
import { Page, PageTitle } from '@/components/page';
import { SongList } from '@/components/song-list';
import { Button, Chips, Empty, IconButton, Segmented, Select } from '@/components/ui';
import { VirtualRows } from '@/components/virtual-rows';
import type { Album, Artist, FolderNode } from '@/library/library';
import { compareText } from '@/library/library';
import { createPlaylist, useLibrary } from '@/library/store';
import { formatDuration } from '@/lib/format';
import { routes } from '@/lib/nav';
import { shuffled } from '@/player/auto-dj';
import { playSongs } from '@/player/store';
import { type AlbumSort, type ArtistSort, type SongSort, useSettings } from '@/settings/store';
import type { Playlist } from '@/sources/types';

import styles from './library.module.css';
import { LibraryGate } from './library-gate';

type Tab = 'albums' | 'artists' | 'favorites' | 'folders' | 'genres' | 'playlists' | 'songs';
const TABS: Tab[] = ['songs', 'albums', 'artists', 'genres', 'folders', 'playlists', 'favorites'];

export function PlayButtons({ songs }: { songs: Song[] }) {
    const { t } = useTranslation();
    return (
        <div className={styles.playButtons}>
            <Button disabled={!songs.length} icon="mediaPlay" onClick={() => playSongs(songs, 0, { shuffle: false })} variant="primary">
                {t('actions.play')}
            </Button>
            <Button disabled={!songs.length} icon="shuffle" onClick={() => playSongs(songs, 0, { shuffle: true })}>
                {t('actions.shuffle')}
            </Button>
        </div>
    );
}

// ---------------------------------------------------------------------------

function SongsTab() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    const user = useLibrary((s) => s.user);
    const sort = useSettings((s) => s.songSort);
    const desc = useSettings((s) => s.songSortDesc);
    const set = useSettings((s) => s.set);

    const songs = useMemo(() => {
        const list = [...library.songs];
        const cmp: Record<SongSort, (a: Song, b: Song) => number> = {
            added: (a, b) => b.dateAdded - a.dateAdded,
            album: (a, b) => compareText(a.album, b.album) || a.disc - b.disc || a.track - b.track,
            artist: (a, b) => compareText(a.artist, b.artist) || compareText(a.album, b.album) || a.track - b.track,
            duration: (a, b) => a.durationMs - b.durationMs,
            plays: (a, b) => (user.playCounts[b.id] || 0) - (user.playCounts[a.id] || 0),
            title: (a, b) => compareText(a.title, b.title),
            year: (a, b) => b.year - a.year,
        };
        if (sort !== 'title') list.sort(cmp[sort]);
        return desc ? list.reverse() : list;
    }, [desc, library, sort, user]);

    return (
        <>
            <div className={styles.toolbar}>
                <PlayButtons songs={songs} />
                <div className={styles.sort}>
                    <Select
                        aria-label={t('library.sortBy')}
                        onChange={(value) => set({ songSort: value })}
                        options={(['title', 'artist', 'album', 'added', 'year', 'duration', 'plays'] as SongSort[]).map((v) => ({ label: t(`library.sort.${v}`), value: v }))}
                        value={sort}
                    />
                    <IconButton icon="sort" label={t('library.reverse')} onClick={() => set({ songSortDesc: !desc })} variant="surface" />
                </div>
            </div>
            <SongList songs={songs} />
        </>
    );
}

export function AlbumGrid({ albums }: { albums: Album[] }) {
    return (
        <CardGrid
            getKey={(a) => a.id}
            items={albums}
            render={(album) => (
                <Card
                    coverId={album.coverId}
                    onMenu={(at) => openAlbumMenu(at, album)}
                    onPlay={() => playSongs(album.songs, 0, { shuffle: false })}
                    seed={album.id}
                    subtitle={album.year ? `${album.year} · ${album.artist}` : album.artist}
                    title={album.name}
                    to={routes.album(album.id)}
                />
            )}
        />
    );
}

function AlbumRows({ albums }: { albums: Album[] }) {
    const { t } = useTranslation();
    return (
        <VirtualRows
            getKey={(a) => a.id}
            items={albums}
            render={(album) => (
                <Link className={styles.row} onContextMenu={(e) => { e.preventDefault(); openAlbumMenu({ x: e.clientX, y: e.clientY }, album); }} to={routes.album(album.id)}>
                    <Cover coverId={album.coverId} seed={album.id} size="sm" />
                    <div className={styles.rowText}>
                        <strong className="truncate">{album.name}</strong>
                        <span className="truncate">{album.artist}</span>
                    </div>
                    <span className={styles.rowMeta}>{album.year || ''}</span>
                    <span className={styles.rowMeta}>{t('common.songCount', { count: album.songs.length, formatted: album.songs.length })}</span>
                </Link>
            )}
            rowHeight={64}
        />
    );
}

function AlbumsTab() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    const user = useLibrary((s) => s.user);
    const sort = useSettings((s) => s.albumSort);
    const desc = useSettings((s) => s.albumSortDesc);
    const grid = useSettings((s) => s.albumsAsGrid);
    const set = useSettings((s) => s.set);

    const albums = useMemo(() => {
        if (sort === 'random') return shuffled(library.albums);
        const plays = (a: Album) => a.songs.reduce((n, s) => n + (user.playCounts[s.id] || 0), 0);
        const cmp: Record<Exclude<AlbumSort, 'random'>, (a: Album, b: Album) => number> = {
            added: (a, b) => b.dateAdded - a.dateAdded,
            artist: (a, b) => compareText(a.artist, b.artist) || a.year - b.year,
            name: (a, b) => compareText(a.name, b.name),
            plays: (a, b) => plays(b) - plays(a),
            year: (a, b) => b.year - a.year || compareText(a.name, b.name),
        };
        const list = sort === 'name' ? [...library.albums] : [...library.albums].sort(cmp[sort]);
        return desc ? list.reverse() : list;
    }, [desc, library, sort, user]);

    return (
        <>
            <div className={styles.toolbar}>
                <span className={styles.count}>{t('common.albumCount', { count: albums.length, formatted: albums.length.toLocaleString() })}</span>
                <div className={styles.sort}>
                    <Select
                        aria-label={t('library.sortBy')}
                        onChange={(value) => set({ albumSort: value })}
                        options={(['name', 'artist', 'year', 'added', 'plays', 'random'] as AlbumSort[]).map((v) => ({ label: t(`library.sort.${v}`), value: v }))}
                        value={sort}
                    />
                    <IconButton icon="sort" label={t('library.reverse')} onClick={() => set({ albumSortDesc: !desc })} variant="surface" />
                    <IconButton icon={grid ? 'list' : 'grid'} label={grid ? t('library.asList') : t('library.asGrid')} onClick={() => set({ albumsAsGrid: !grid })} variant="surface" />
                </div>
            </div>
            {grid ? <AlbumGrid albums={albums} /> : <AlbumRows albums={albums} />}
        </>
    );
}

export function ArtistGrid({ artists }: { artists: Artist[] }) {
    const { t } = useTranslation();
    return (
        <CardGrid
            getKey={(a) => a.id}
            items={artists}
            render={(artist) => (
                <Card
                    coverId={artist.coverId}
                    icon="user"
                    onMenu={(at) => openArtistMenu(at, artist)}
                    onPlay={() => playSongs(artist.songs, 0, { shuffle: true })}
                    round
                    seed={artist.id}
                    subtitle={artist.albums.length ? t('common.albumCount', { count: artist.albums.length, formatted: artist.albums.length }) : t('common.songCount', { count: artist.songs.length, formatted: artist.songs.length })}
                    title={artist.name}
                    to={routes.artist(artist.id)}
                />
            )}
        />
    );
}

function ArtistsTab() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    const user = useLibrary((s) => s.user);
    const sort = useSettings((s) => s.artistSort);
    const set = useSettings((s) => s.set);
    const [onlyAlbumArtists, setOnlyAlbumArtists] = useState(true);

    const artists = useMemo(() => {
        let list = onlyAlbumArtists ? library.artists.filter((a) => a.albums.length > 0) : [...library.artists];
        if (sort === 'songs') list = list.sort((a, b) => b.songs.length - a.songs.length);
        else if (sort === 'albums') list = list.sort((a, b) => b.albums.length - a.albums.length);
        else if (sort === 'plays') {
            const plays = new Map(list.map((a) => [a.id, a.songs.reduce((n, s) => n + (user.playCounts[s.id] || 0), 0)]));
            list = list.sort((a, b) => (plays.get(b.id) || 0) - (plays.get(a.id) || 0));
        }
        return list;
    }, [library, onlyAlbumArtists, sort, user]);

    return (
        <>
            <div className={styles.toolbar}>
                <Segmented
                    onChange={(v) => setOnlyAlbumArtists(v === 'album')}
                    options={[
                        { label: t('library.albumArtists'), value: 'album' },
                        { label: t('library.allArtists'), value: 'all' },
                    ]}
                    value={onlyAlbumArtists ? 'album' : 'all'}
                />
                <Select
                    aria-label={t('library.sortBy')}
                    onChange={(value) => set({ artistSort: value })}
                    options={(['name', 'songs', 'albums', 'plays'] as ArtistSort[]).map((v) => ({ label: t(`library.sort.${v}`), value: v }))}
                    value={sort}
                />
            </div>
            <ArtistGrid artists={artists} />
        </>
    );
}

function GenresTab() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    return (
        <CardGrid
            getKey={(g) => g.id}
            items={library.genres}
            render={(g) => (
                <Card coverId={g.coverId} icon="genre" onPlay={() => playSongs(g.songs, 0, { shuffle: true })} seed={g.id} subtitle={t('common.songCount', { count: g.songs.length, formatted: g.songs.length.toLocaleString() })} title={g.name} to={routes.genre(g.id)} />
            )}
        />
    );
}

export function FolderRows({ folders }: { folders: FolderNode[] }) {
    const { t } = useTranslation();
    return (
        <div>
            {folders.map((f) => (
                <Link className={styles.row} key={f.path} to={routes.folder(f.path)}>
                    <div className={styles.folderIcon}>{f.coverId ? <Cover coverId={f.coverId} seed={f.path} size="sm" /> : <Icon name="folder" size={22} />}</div>
                    <div className={styles.rowText}>
                        <strong className="truncate">{f.name}</strong>
                        <span className="truncate">{t('common.songCount', { count: f.totalSongs, formatted: f.totalSongs.toLocaleString() })}</span>
                    </div>
                    <Icon className={styles.chev} name="chevronRight" size={18} />
                </Link>
            ))}
        </div>
    );
}

function FoldersTab() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    const root = library.foldersByPath.get('');
    if (!library.hasFolders) return <Empty icon="folder" text={t('library.noFoldersText')} title={t('library.noFolders')} />;
    return (
        <>
            <FolderRows folders={library.rootFolders} />
            {root && root.songs.length > 0 && <SongList songs={root.songs} />}
        </>
    );
}

export const playlistSongs = (playlist: Playlist, songsById: Map<string, Song>) => playlist.songIds.map((id) => songsById.get(id)).filter((s): s is Song => Boolean(s));

function PlaylistsTab() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const playlists = useLibrary((s) => s.playlists);
    const library = useLibrary((s) => s.library);
    const sorted = useMemo(() => [...playlists].sort((a, b) => compareText(a.name, b.name)), [playlists]);
    const newPlaylist = async () => {
        const name = await prompt(t('playlist.new'), t('playlist.name'));
        if (!name) return;
        const created = await createPlaylist(name);
        if (created) navigate(routes.playlist(created.id));
    };
    return (
        <>
            <div className={styles.toolbar}>
                <Button icon="add" onClick={() => void newPlaylist()} variant="primary">
                    {t('playlist.new')}
                </Button>
            </div>
            {sorted.length === 0 ? (
                <Empty icon="playlist" text={t('playlist.emptyListText')} title={t('playlist.emptyList')} />
            ) : (
                <CardGrid
                    getKey={(p) => p.id}
                    items={sorted}
                    render={(p) => {
                        const songs = playlistSongs(p, library.songsById);
                        return (
                            <Card
                                coverId={songs.find((s) => s.coverId)?.coverId || null}
                                icon="playlist"
                                onMenu={(at) => openPlaylistMenu(at, p, songs)}
                                onPlay={songs.length ? () => playSongs(songs, 0, { shuffle: false }) : undefined}
                                seed={p.id}
                                subtitle={t('common.songCount', { count: songs.length, formatted: songs.length })}
                                title={p.name}
                                to={routes.playlist(p.id)}
                            />
                        );
                    }}
                />
            )}
        </>
    );
}

function FavoritesTab() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    const favSongs = useLibrary((s) => s.favoriteSongSet);
    const favAlbums = useLibrary((s) => s.favoriteAlbumSet);
    const favArtists = useLibrary((s) => s.favoriteArtistSet);
    const [kind, setKind] = useState<'albums' | 'artists' | 'songs'>('songs');
    const songs = useMemo(() => library.songs.filter((s) => favSongs.has(s.id)), [favSongs, library]);
    const albums = useMemo(() => library.albums.filter((a) => favAlbums.has(a.id)), [favAlbums, library]);
    const artists = useMemo(() => library.artists.filter((a) => favArtists.has(a.key)), [favArtists, library]);

    return (
        <>
            <div className={styles.toolbar}>
                <Segmented
                    onChange={setKind}
                    options={[
                        { label: `${t('library.songs')} · ${songs.length}`, value: 'songs' },
                        { label: `${t('library.albums')} · ${albums.length}`, value: 'albums' },
                        { label: `${t('library.artists')} · ${artists.length}`, value: 'artists' },
                    ]}
                    value={kind}
                />
                {kind === 'songs' && <PlayButtons songs={songs} />}
            </div>
            {kind === 'songs' && (songs.length ? <SongList songs={songs} /> : <Empty icon="favorite" text={t('library.noFavoritesText')} title={t('library.noFavorites')} />)}
            {kind === 'albums' && (albums.length ? <AlbumGrid albums={albums} /> : <Empty icon="album" title={t('library.noFavorites')} />)}
            {kind === 'artists' && (artists.length ? <ArtistGrid artists={artists} /> : <Empty icon="artist" title={t('library.noFavorites')} />)}
        </>
    );
}

function LibraryContent() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { tab: param } = useParams();
    const library = useLibrary((s) => s.library);
    const tab: Tab = TABS.includes(param as Tab) ? (param as Tab) : 'albums';
    const tabs = TABS.filter((x) => x !== 'folders' || library.hasFolders);

    return (
        <Page>
            <PageTitle subtitle={t('library.summary', { albums: library.albums.length.toLocaleString(), artists: library.artists.length.toLocaleString(), songs: library.songs.length.toLocaleString() })}>
                {t('nav.library')}
            </PageTitle>
            <Chips onChange={(v) => navigate(routes.library(v), { replace: true })} options={tabs.map((v) => ({ label: t(`library.${v}`), value: v }))} value={tab} />
            <div className={clsx(styles.tab)}>
                {tab === 'songs' && <SongsTab />}
                {tab === 'albums' && <AlbumsTab />}
                {tab === 'artists' && <ArtistsTab />}
                {tab === 'genres' && <GenresTab />}
                {tab === 'folders' && <FoldersTab />}
                {tab === 'playlists' && <PlaylistsTab />}
                {tab === 'favorites' && <FavoritesTab />}
            </div>
        </Page>
    );
}

export function LibraryScreen() {
    return (
        <LibraryGate>
            <LibraryContent />
        </LibraryGate>
    );
}

export const totalDuration = (songs: Song[]) => formatDuration(songs.reduce((n, s) => n + s.durationMs, 0));
