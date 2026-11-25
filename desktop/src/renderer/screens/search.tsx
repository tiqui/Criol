import { useDeferredValue, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router';

import { openAlbumMenu, openArtistMenu } from '@/components/actions';
import { Card, Carousel } from '@/components/cards';
import { Cover } from '@/components/cover';
import { Page, Section } from '@/components/page';
import { SongList } from '@/components/song-list';
import { Button, Empty, TextInput } from '@/components/ui';
import { useLibrary } from '@/library/store';
import { seedColor } from '@/lib/color';
import { useIsMobile } from '@/lib/hooks';
import { routes } from '@/lib/nav';
import { playSongs } from '@/player/store';

import { LibraryGate } from './library-gate';
import styles from './search.module.css';

function SearchContent() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const isMobile = useIsMobile();
    const query = params.get('q') || '';
    const deferred = useDeferredValue(query);
    const library = useLibrary((s) => s.library);
    const [allSongs, setAllSongs] = useState(false);
    const result = useMemo(() => library.search(deferred), [deferred, library]);

    const top = result.artists[0] && (!result.albums[0] || result.artists[0].name.toLowerCase().startsWith(deferred.toLowerCase())) ? { artist: result.artists[0] } : result.albums[0] ? { album: result.albums[0] } : null;
    const nothing = deferred && !result.songs.length && !result.albums.length && !result.artists.length && !result.genres.length;

    return (
        <Page>
            {isMobile && (
                <div className={styles.mobileInput}>
                    <h1>{t('nav.search')}</h1>
                    <TextInput
                        autoFocus={!query}
                        icon="search"
                        onChange={(e) => navigate(routes.search(e.target.value), { replace: true })}
                        placeholder={t('search.placeholder')}
                        type="search"
                        value={query}
                    />
                </div>
            )}

            {!deferred && (
                <Section title={t('search.browse')}>
                    <div className={styles.genres}>
                        {library.genres.slice(0, 48).map((g) => (
                            <Link className={styles.genre} key={g.id} style={{ background: seedColor(g.key) }} to={routes.genre(g.id)}>
                                <span>{g.name}</span>
                                <div className={styles.genreCover}>
                                    <Cover coverId={g.coverId} seed={g.id} size="sm" />
                                </div>
                            </Link>
                        ))}
                    </div>
                </Section>
            )}

            {nothing && <Empty icon="search" text={t('search.nothingText')} title={t('search.nothing', { query: deferred })} />}

            {deferred && !nothing && (
                <>
                    <div className={styles.topRow}>
                        {top && (
                            <Section title={t('search.topResult')}>
                                {'artist' in top && top.artist ? (
                                    <Link className={styles.top} onContextMenu={(e) => { e.preventDefault(); openArtistMenu({ x: e.clientX, y: e.clientY }, top.artist!); }} to={routes.artist(top.artist.id)}>
                                        <div className={styles.topCover}>
                                            <Cover coverId={top.artist.coverId} icon="user" round seed={top.artist.id} size="md" />
                                        </div>
                                        <strong className="truncate">{top.artist.name}</strong>
                                        <span>{t('library.artist')}</span>
                                    </Link>
                                ) : top.album ? (
                                    <Link className={styles.top} onContextMenu={(e) => { e.preventDefault(); openAlbumMenu({ x: e.clientX, y: e.clientY }, top.album!); }} to={routes.album(top.album.id)}>
                                        <div className={styles.topCover}>
                                            <Cover coverId={top.album.coverId} seed={top.album.id} size="md" />
                                        </div>
                                        <strong className="truncate">{top.album.name}</strong>
                                        <span>
                                            {t('library.album')} · {top.album.artist}
                                        </span>
                                    </Link>
                                ) : null}
                            </Section>
                        )}
                        {result.songs.length > 0 && (
                            <Section
                                action={
                                    result.songs.length > 5 && (
                                        <Button onClick={() => setAllSongs(!allSongs)} size="sm" variant="ghost">
                                            {allSongs ? t('common.showLess') : t('search.allSongs', { count: result.songs.length })}
                                        </Button>
                                    )
                                }
                                title={t('library.songs')}
                            >
                                <SongList onPlay={(i) => playSongs(result.songs, i)} songs={allSongs ? result.songs : result.songs.slice(0, 5)} />
                            </Section>
                        )}
                    </div>

                    {result.artists.length > 0 && (
                        <Carousel title={t('library.artists')}>
                            {result.artists.map((a) => (
                                <Card coverId={a.coverId} icon="user" key={a.id} onMenu={(at) => openArtistMenu(at, a)} onPlay={() => playSongs(a.songs, 0, { shuffle: true })} round seed={a.id} subtitle={t('library.artist')} title={a.name} to={routes.artist(a.id)} />
                            ))}
                        </Carousel>
                    )}
                    {result.albums.length > 0 && (
                        <Carousel title={t('library.albums')}>
                            {result.albums.map((a) => (
                                <Card coverId={a.coverId} key={a.id} onMenu={(at) => openAlbumMenu(at, a)} onPlay={() => playSongs(a.songs, 0, { shuffle: false })} seed={a.id} subtitle={a.artist} title={a.name} to={routes.album(a.id)} />
                            ))}
                        </Carousel>
                    )}
                    {result.genres.length > 0 && (
                        <Section title={t('library.genres')}>
                            <div className={styles.genreChips}>
                                {result.genres.map((g) => (
                                    <Link className={styles.genreChip} key={g.id} to={routes.genre(g.id)}>
                                        {g.name}
                                    </Link>
                                ))}
                            </div>
                        </Section>
                    )}
                </>
            )}
        </Page>
    );
}

export function SearchScreen() {
    return (
        <LibraryGate>
            <SearchContent />
        </LibraryGate>
    );
}
