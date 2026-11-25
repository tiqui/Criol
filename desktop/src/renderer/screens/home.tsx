import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { openAlbumMenu, openArtistMenu } from '@/components/actions';
import { Card, Carousel } from '@/components/cards';
import { Cover } from '@/components/cover';
import { MixCover } from '@/components/mix-cover';
import { Page } from '@/components/page';
import { IconButton } from '@/components/ui';
import { getDaypart } from '@/insights/listening-stats';
import { mixText } from '@/insights/mix-text';
import { useMixes } from '@/insights/mixes';
import type { Album } from '@/library/library';
import { useLibrary } from '@/library/store';
import { routes } from '@/lib/nav';
import { shuffled } from '@/player/auto-dj';
import { playSongs } from '@/player/store';
import { useSettings } from '@/settings/store';

import { useProfileName } from '@/layout/profile-avatar';

import styles from './home.module.css';
import { LibraryGate } from './library-gate';

const LIMIT = 20;

function AlbumCard({ album }: { album: Album }) {
    return (
        <Card
            coverId={album.coverId}
            onMenu={(at) => openAlbumMenu(at, album)}
            onPlay={() => playSongs(album.songs, 0, { shuffle: false })}
            seed={album.id}
            subtitle={album.artist}
            title={album.name}
            to={routes.album(album.id)}
        />
    );
}

function HomeContent() {
    const { t } = useTranslation();
    const library = useLibrary((s) => s.library);
    const user = useLibrary((s) => s.user);
    const favoriteAlbums = useLibrary((s) => s.favoriteAlbumSet);
    const mixes = useMixes((s) => s.mixes);
    const showExplore = useSettings((s) => s.showExplore);
    const name = useProfileName();
    const [exploreSeed, setExploreSeed] = useState(0);

    const sections = useMemo(() => {
        const albumPlays = new Map<string, number>();
        const albumLast = new Map<string, number>();
        for (const song of library.songs) {
            const plays = user.playCounts[song.id] || 0;
            if (plays) albumPlays.set(song.albumId, (albumPlays.get(song.albumId) || 0) + plays);
            const last = user.lastPlayed[song.id] || 0;
            if (last > (albumLast.get(song.albumId) || 0)) albumLast.set(song.albumId, last);
        }
        const byId = (id: string) => library.albumsById.get(id);
        const recentPlayed = [...albumLast.entries()].sort((a, b) => b[1] - a[1]).slice(0, LIMIT).map(([id]) => byId(id)).filter((a): a is Album => Boolean(a));
        const mostPlayed = [...albumPlays.entries()].sort((a, b) => b[1] - a[1]).slice(0, LIMIT).map(([id]) => byId(id)).filter((a): a is Album => Boolean(a));
        const recentAdded = [...library.albums].sort((a, b) => b.dateAdded - a.dateAdded).slice(0, LIMIT);
        const releases = [...library.albums].filter((a) => a.year > 0).sort((a, b) => b.year - a.year || b.dateAdded - a.dateAdded).slice(0, LIMIT);
        const favorites = library.albums.filter((a) => favoriteAlbums.has(a.id)).slice(0, LIMIT);
        const artistPlays = new Map<string, number>();
        for (const artist of library.artists) {
            let plays = 0;
            for (const s of artist.songs) plays += user.playCounts[s.id] || 0;
            if (plays) artistPlays.set(artist.id, plays);
        }
        const topArtists = [...artistPlays.entries()].sort((a, b) => b[1] - a[1]).slice(0, LIMIT).map(([id]) => library.artistsById.get(id)!);
        return { favorites, mostPlayed, recentAdded, recentPlayed, releases, topArtists };
    }, [favoriteAlbums, library, user]);

    // eslint-disable-next-line react-hooks/exhaustive-deps
    const explore = useMemo(() => shuffled(library.albums).slice(0, LIMIT), [library, exploreSeed]);

    const hour = new Date().getHours();
    const greeting = hour < 5 ? t('home.greetingNight') : hour < 12 ? t('home.greetingMorning') : hour < 18 ? t('home.greetingAfternoon') : t('home.greetingNight');
    const shortcuts = (sections.recentPlayed.length >= 4 ? sections.recentPlayed : [...sections.recentPlayed, ...sections.recentAdded]).filter((a, i, list) => list.indexOf(a) === i).slice(0, 8);
    const daypart = getDaypart(Date.now());

    return (
        <Page className={styles.home}>
            <div className={styles.greeting} data-daypart={daypart}>
                <h1>{name ? t('home.greetingName', { greeting, name }) : greeting}</h1>
            </div>

            {shortcuts.length > 0 && (
                <div className={styles.shortcuts}>
                    {shortcuts.map((album) => (
                        <Link className={styles.shortcut} key={album.id} onContextMenu={(e) => { e.preventDefault(); openAlbumMenu({ x: e.clientX, y: e.clientY }, album); }} to={routes.album(album.id)}>
                            <Cover coverId={album.coverId} seed={album.id} size="sm" />
                            <span className="truncate">{album.name}</span>
                            <IconButton
                                className={styles.shortcutPlay}
                                filled
                                icon="mediaPlay"
                                label={t('actions.play')}
                                onClick={(e) => {
                                    e.preventDefault();
                                    playSongs(album.songs, 0, { shuffle: false });
                                }}
                                size={34}
                                variant="filled"
                            />
                        </Link>
                    ))}
                </div>
            )}

            {mixes.length > 0 && (
                <Carousel subtitle={t('home.madeForYouSubtitle')} title={t('home.madeForYou')}>
                    {mixes
                        .filter((m) => m.songIds.length > 0)
                        .map((mix) => {
                            const text = mixText(mix, t);
                            return (
                                <Card
                                    coverId={null}
                                    key={mix.id}
                                    media={<MixCover label={text.coverLabel} mix={mix} />}
                                    onPlay={() => playSongs(mix.songIds.map((id) => library.songsById.get(id)!).filter(Boolean), 0, { shuffle: false })}
                                    seed={mix.id}
                                    subtitle={text.description}
                                    title={text.title}
                                    to={routes.mix(mix.id)}
                                />
                            );
                        })}
                </Carousel>
            )}

            {sections.recentPlayed.length > 0 && (
                <Carousel title={t('home.recentlyPlayed')}>
                    {sections.recentPlayed.map((a) => (
                        <AlbumCard album={a} key={a.id} />
                    ))}
                </Carousel>
            )}

            <Carousel action={<Link className={styles.more} to={routes.library('albums')}>{t('common.seeAll')}</Link>} title={t('home.recentlyAdded')}>
                {sections.recentAdded.map((a) => (
                    <AlbumCard album={a} key={a.id} />
                ))}
            </Carousel>

            {sections.mostPlayed.length > 0 && (
                <Carousel title={t('home.mostPlayed')}>
                    {sections.mostPlayed.map((a) => (
                        <AlbumCard album={a} key={a.id} />
                    ))}
                </Carousel>
            )}

            {sections.topArtists.length > 0 && (
                <Carousel action={<Link className={styles.more} to={routes.library('artists')}>{t('common.seeAll')}</Link>} title={t('home.yourArtists')}>
                    {sections.topArtists.map((artist) => (
                        <Card
                            coverId={artist.coverId}
                            icon="user"
                            key={artist.id}
                            onMenu={(at) => openArtistMenu(at, artist)}
                            onPlay={() => playSongs(artist.songs, 0, { shuffle: true })}
                            round
                            seed={artist.id}
                            subtitle={t('library.artist')}
                            title={artist.name}
                            to={routes.artist(artist.id)}
                        />
                    ))}
                </Carousel>
            )}

            {showExplore && library.albums.length > 6 && (
                <Carousel action={<IconButton icon="refresh" label={t('home.shuffleExplore')} onClick={() => setExploreSeed((n) => n + 1)} size={30} />} title={t('home.explore')}>
                    {explore.map((a) => (
                        <AlbumCard album={a} key={a.id} />
                    ))}
                </Carousel>
            )}

            {library.genres.length > 0 && (
                <Carousel action={<Link className={styles.more} to={routes.library('genres')}>{t('common.seeAll')}</Link>} title={t('library.genres')}>
                    {library.genres.slice(0, LIMIT).map((g) => (
                        <Card coverId={g.coverId} icon="genre" key={g.id} onPlay={() => playSongs(g.songs, 0, { shuffle: true })} seed={g.id} subtitle={t('common.songCount', { count: g.songs.length, formatted: g.songs.length })} title={g.name} to={routes.genre(g.id)} />
                    ))}
                </Carousel>
            )}

            {sections.favorites.length > 0 && (
                <Carousel action={<Link className={styles.more} to={routes.library('favorites')}>{t('common.seeAll')}</Link>} title={t('home.favoriteAlbums')}>
                    {sections.favorites.map((a) => (
                        <AlbumCard album={a} key={a.id} />
                    ))}
                </Carousel>
            )}

            {sections.releases.length > 0 && (
                <Carousel title={t('home.releases')}>
                    {sections.releases.map((a) => (
                        <AlbumCard album={a} key={a.id} />
                    ))}
                </Carousel>
            )}
        </Page>
    );
}

export function HomeScreen() {
    return (
        <LibraryGate>
            <HomeContent />
        </LibraryGate>
    );
}
