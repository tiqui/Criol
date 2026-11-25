import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router';

import type { Song } from '@shared/types';

import { openAlbumMenu, openArtistMenu, openPlaylistMenu, songsMenuItems } from '@/components/actions';
import { Card, Carousel } from '@/components/cards';
import { openMenu } from '@/components/menu';
import { MixCover } from '@/components/mix-cover';
import { Hero, Page, Section } from '@/components/page';
import { SongList } from '@/components/song-list';
import { toast } from '@/components/toast';
import { Button, Empty, IconButton } from '@/components/ui';
import { useListening } from '@/insights/listening-db';
import { mixText } from '@/insights/mix-text';
import { regenerateMix, useMixes } from '@/insights/mixes';
import type { Album } from '@/library/library';
import { createPlaylist, toggleFavorite, updatePlaylist, useIsFavorite, useLibrary } from '@/library/store';
import { formatLongDuration } from '@/lib/format';
import { routes } from '@/lib/nav';
import { playSongs, startRadio } from '@/player/store';

import styles from './details.module.css';
import { AlbumGrid, FolderRows, playlistSongs } from './library';
import { LibraryGate } from './library-gate';

const Dot = () => <span className={styles.dot}>•</span>;

function BigPlay({ shuffle, songs }: { songs: Song[]; shuffle?: boolean }) {
    const { t } = useTranslation();
    return (
        <>
            <IconButton className={styles.bigPlay} disabled={!songs.length} filled icon="mediaPlay" iconSize={26} label={t('actions.play')} onClick={() => playSongs(songs, 0, { shuffle: Boolean(shuffle) })} size={56} variant="filled" />
            <IconButton disabled={!songs.length} icon="shuffle" iconSize={22} label={t('actions.shuffle')} onClick={() => playSongs(songs, 0, { shuffle: true })} size={44} />
        </>
    );
}

function NotFound() {
    const { t } = useTranslation();
    return (
        <Page>
            <Empty action={<Link to="/">{t('nav.home')}</Link>} icon="alert" title={t('common.notFound')} />
        </Page>
    );
}

// ---------------------------------------------------------------------------

function AlbumContent() {
    const { t } = useTranslation();
    const { id = '' } = useParams();
    const library = useLibrary((s) => s.library);
    const album = library.albumsById.get(id);
    const fav = useIsFavorite('album', id);
    if (!album) return <NotFound />;
    const artist = album.artistNames[0] ? library.artistByName(album.artistNames[0]) : null;
    const more = artist ? [...artist.albums, ...artist.appearsOn].filter((a) => a.id !== album.id) : [];
    const formats = [...new Set(album.songs.map((s) => s.format.toUpperCase()).filter(Boolean))].join(', ');

    return (
        <Page flush>
            <Hero
                actions={
                    <>
                        <BigPlay songs={album.songs} />
                        <IconButton active={fav} filled={fav} icon="favorite" iconSize={22} label={fav ? t('actions.unfavorite') : t('actions.favorite')} onClick={() => void toggleFavorite('album', album.id)} size={44} />
                        <IconButton icon="more" iconSize={22} label={t('common.more')} onClick={(e) => openAlbumMenu(e.currentTarget, album)} size={44} />
                    </>
                }
                coverId={album.coverId}
                label={t('library.album')}
                meta={
                    <>
                        {artist ? <Link className={styles.strong} to={routes.artist(artist.id)}>{album.artist}</Link> : <span className={styles.strong}>{album.artist}</span>}
                        {album.year > 0 && (
                            <>
                                <Dot />
                                <span>{album.year}</span>
                            </>
                        )}
                        <Dot />
                        <span>{t('common.songCount', { count: album.songs.length, formatted: album.songs.length })}</span>
                        <Dot />
                        <span>{formatLongDuration(album.durationMs)}</span>
                    </>
                }
                seed={album.id}
                title={album.name}
            />
            <SongList discs showAlbum={false} songs={album.songs} trackNumbers />
            <p className={styles.footnote}>
                {album.genres.length > 0 && album.genres.map((g, i) => {
                    const genre = library.genres.find((x) => x.name === g);
                    return (
                        <span key={g}>
                            {i > 0 && ', '}
                            {genre ? <Link to={routes.genre(genre.id)}>{g}</Link> : g}
                        </span>
                    );
                })}
                {formats && <span className={styles.format}>{formats}</span>}
            </p>
            {more.length > 0 && artist && (
                <Carousel action={<Link className={styles.seeAll} to={routes.artist(artist.id)}>{t('common.seeAll')}</Link>} title={t('album.moreFrom', { artist: artist.name })}>
                    {more.slice(0, 16).map((a) => (
                        <Card coverId={a.coverId} key={a.id} onMenu={(at) => openAlbumMenu(at, a)} onPlay={() => playSongs(a.songs, 0, { shuffle: false })} seed={a.id} subtitle={a.year ? String(a.year) : a.artist} title={a.name} to={routes.album(a.id)} />
                    ))}
                </Carousel>
            )}
        </Page>
    );
}

function ArtistContent() {
    const { t } = useTranslation();
    const { id = '' } = useParams();
    const library = useLibrary((s) => s.library);
    const user = useLibrary((s) => s.user);
    const artist = library.artistsById.get(id);
    const fav = useIsFavorite('artist', artist?.key);
    const top = useMemo(() => {
        if (!artist) return [];
        const withPlays = [...artist.songs].sort((a, b) => (user.playCounts[b.id] || 0) - (user.playCounts[a.id] || 0) || (user.ratings[b.id] || 0) - (user.ratings[a.id] || 0));
        return withPlays.slice(0, 5);
    }, [artist, user]);
    if (!artist) return <NotFound />;

    return (
        <Page flush>
            <Hero
                actions={
                    <>
                        <BigPlay shuffle songs={artist.songs} />
                        <Button icon="radio" onClick={() => startRadio(top[0] || artist.songs[0])}>
                            {t('actions.radio')}
                        </Button>
                        <IconButton active={fav} filled={fav} icon="favorite" iconSize={22} label={fav ? t('actions.unfavorite') : t('actions.favorite')} onClick={() => void toggleFavorite('artist', artist.key)} size={44} />
                        <IconButton icon="more" iconSize={22} label={t('common.more')} onClick={(e) => openArtistMenu(e.currentTarget, artist)} size={44} />
                    </>
                }
                coverId={artist.coverId}
                icon="user"
                label={t('library.artist')}
                meta={
                    <>
                        {artist.albums.length > 0 && (
                            <>
                                <span>{t('common.albumCount', { count: artist.albums.length, formatted: artist.albums.length })}</span>
                                <Dot />
                            </>
                        )}
                        <span>{t('common.songCount', { count: artist.songs.length, formatted: artist.songs.length })}</span>
                    </>
                }
                round
                seed={artist.id}
                title={artist.name}
            />
            <Section title={t('artist.popular')}>
                <SongList onPlay={(index) => playSongs(top, index)} songs={top} />
            </Section>
            {artist.albums.length > 0 && (
                <Section title={t('library.albums')}>
                    <AlbumGrid albums={artist.albums} />
                </Section>
            )}
            {artist.appearsOn.length > 0 && (
                <Carousel title={t('artist.appearsOn')}>
                    {artist.appearsOn.map((a: Album) => (
                        <Card coverId={a.coverId} key={a.id} onMenu={(at) => openAlbumMenu(at, a)} onPlay={() => playSongs(a.songs, 0, { shuffle: false })} seed={a.id} subtitle={a.artist} title={a.name} to={routes.album(a.id)} />
                    ))}
                </Carousel>
            )}
            {artist.songs.length > top.length && (
                <Section title={t('artist.allSongs', { count: artist.songs.length })}>
                    <SongList songs={artist.songs} />
                </Section>
            )}
        </Page>
    );
}

function GenreContent() {
    const { t } = useTranslation();
    const { id = '' } = useParams();
    const library = useLibrary((s) => s.library);
    const genre = library.genresById.get(id);
    if (!genre) return <NotFound />;
    return (
        <Page flush>
            <Hero
                actions={<BigPlay shuffle songs={genre.songs} />}
                coverId={genre.coverId}
                icon="genre"
                label={t('library.genre')}
                meta={
                    <>
                        <span>{t('common.albumCount', { count: genre.albums.length, formatted: genre.albums.length })}</span>
                        <Dot />
                        <span>{t('common.songCount', { count: genre.songs.length, formatted: genre.songs.length.toLocaleString() })}</span>
                    </>
                }
                seed={genre.id}
                title={genre.name}
            />
            <Section title={t('library.albums')}>
                <AlbumGrid albums={genre.albums} />
            </Section>
            <Section title={t('library.songs')}>
                <SongList songs={genre.songs} />
            </Section>
        </Page>
    );
}

function FolderContent() {
    const { t } = useTranslation();
    const [params] = useSearchParams();
    const path = params.get('path') || '';
    const library = useLibrary((s) => s.library);
    const folder = library.foldersByPath.get(path);
    if (!folder) return <NotFound />;
    const parts = path.split('/').filter(Boolean);
    const allSongs = (node: typeof folder): Song[] => [...node.songs, ...node.children.flatMap(allSongs)];

    return (
        <Page>
            <nav aria-label={t('library.folders')} className={styles.crumbs}>
                <Link to={routes.library('folders')}>{t('library.folders')}</Link>
                {parts.map((part, i) => (
                    <span key={i}>
                        <span className={styles.crumbSep}>/</span>
                        {i === parts.length - 1 ? <strong>{part}</strong> : <Link to={routes.folder(parts.slice(0, i + 1).join('/'))}>{part}</Link>}
                    </span>
                ))}
            </nav>
            <div className={styles.folderHead}>
                <h1>{folder.name}</h1>
                <span>{t('common.songCount', { count: folder.totalSongs, formatted: folder.totalSongs.toLocaleString() })}</span>
            </div>
            <div className={styles.actionsRow}>
                <Button icon="mediaPlay" onClick={() => playSongs(allSongs(folder), 0, { shuffle: false })} variant="primary">
                    {t('actions.play')}
                </Button>
                <Button icon="shuffle" onClick={() => playSongs(allSongs(folder), 0, { shuffle: true })}>
                    {t('actions.shuffle')}
                </Button>
            </div>
            {folder.children.length > 0 && <FolderRows folders={folder.children} />}
            {folder.songs.length > 0 && <SongList songs={folder.songs} />}
        </Page>
    );
}

function PlaylistContent() {
    const { t } = useTranslation();
    const { id = '' } = useParams();
    const library = useLibrary((s) => s.library);
    const playlist = useLibrary((s) => s.playlists.find((p) => p.id === id));
    const songs = useMemo(() => (playlist ? playlistSongs(playlist, library.songsById) : []), [library, playlist]);
    if (!playlist) return <NotFound />;
    const duration = songs.reduce((n, s) => n + s.durationMs, 0);
    const cover = songs.find((s) => s.coverId)?.coverId || null;

    return (
        <Page flush>
            <Hero
                actions={
                    <>
                        <BigPlay songs={songs} />
                        <IconButton icon="more" iconSize={22} label={t('common.more')} onClick={(e) => openPlaylistMenu(e.currentTarget, playlist, songs)} size={44} />
                    </>
                }
                coverId={cover}
                description={playlist.comment}
                icon="playlist"
                label={t('library.playlist')}
                meta={
                    <>
                        <span>{t('common.songCount', { count: songs.length, formatted: songs.length })}</span>
                        {duration > 0 && (
                            <>
                                <Dot />
                                <span>{formatLongDuration(duration)}</span>
                            </>
                        )}
                    </>
                }
                seed={playlist.id}
                title={playlist.name}
            />
            {songs.length === 0 ? (
                <Empty icon="playlist" text={t('playlist.emptyText')} title={t('playlist.empty')} />
            ) : (
                <SongList
                    menuContext={(_song, index) => ({ index, list: songs, playlist, playlistIndex: playlist.songIds.indexOf(songs[index].id) })}
                    songs={songs}
                />
            )}
        </Page>
    );
}

function MixContent() {
    const { t } = useTranslation();
    const { id = '' } = useParams();
    const library = useLibrary((s) => s.library);
    const user = useLibrary((s) => s.user);
    const sourceId = useLibrary((s) => s.sourceId);
    const mix = useMixes((s) => s.mixes.find((m) => m.id === id));
    const songs = useMemo(() => (mix ? mix.songIds.map((sid) => library.songsById.get(sid)).filter((s): s is Song => Boolean(s)) : []), [library, mix]);
    if (!mix) return <NotFound />;
    const text = mixText(mix, t);

    return (
        <Page flush>
            <Hero
                actions={
                    <>
                        <BigPlay songs={songs} />
                        <Button
                            icon="refresh"
                            onClick={() => sourceId && void regenerateMix(mix.id, library, user, useListening.getState().entries, sourceId)}
                        >
                            {t('mixes.regenerate')}
                        </Button>
                        <IconButton
                            icon="more"
                            iconSize={22}
                            label={t('common.more')}
                            onClick={(e) =>
                                openMenu(e.currentTarget, [
                                    ...songsMenuItems(songs),
                                    { divider: true },
                                    {
                                        icon: 'playlist',
                                        label: t('mixes.saveAsPlaylist'),
                                        onSelect: async () => {
                                            const created = await createPlaylist(text.title, mix.songIds);
                                            if (created) toast.success(t('playlist.created', { name: text.title }));
                                        },
                                    },
                                ])
                            }
                            size={44}
                        />
                    </>
                }
                description={text.description}
                label={t('home.madeForYou')}
                media={<MixCover label={text.coverLabel} mix={mix} />}
                meta={
                    <>
                        <span>{t('common.songCount', { count: songs.length, formatted: songs.length })}</span>
                        <Dot />
                        <span>{formatLongDuration(songs.reduce((n, s) => n + s.durationMs, 0))}</span>
                        {!mix.personalized && (
                            <>
                                <Dot />
                                <span>{t('mixes.notPersonalized')}</span>
                            </>
                        )}
                    </>
                }
                seed={mix.id}
                title={text.title}
            />
            {songs.length ? <SongList songs={songs} /> : <Empty icon="sparkles" title={t('mixes.empty')} />}
        </Page>
    );
}

export const AlbumScreen = () => <LibraryGate><AlbumContent /></LibraryGate>;
export const ArtistScreen = () => <LibraryGate><ArtistContent /></LibraryGate>;
export const GenreScreen = () => <LibraryGate><GenreContent /></LibraryGate>;
export const FolderScreen = () => <LibraryGate><FolderContent /></LibraryGate>;
export const PlaylistScreen = () => <LibraryGate><PlaylistContent /></LibraryGate>;
export const MixScreen = () => <LibraryGate><MixContent /></LibraryGate>;

/** Reordenar faixas da playlist (usado no menu de contexto futuramente). */
export const movePlaylistSong = (playlistId: string, songIds: string[], from: number, to: number) => {
    const next = [...songIds];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    return updatePlaylist(playlistId, { songIds: next });
};
