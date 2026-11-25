import type { Song } from '@shared/types';

import i18n from '@/i18n';
import type { Album, Artist } from '@/library/library';
import { addToPlaylist, createPlaylist, deletePlaylist, isFavorite, libraryState, setFavorite, setRating, updatePlaylist, useIsFavorite, useLibrary } from '@/library/store';
import { navigate, routes } from '@/lib/nav';
import { addToQueue, playNext, playSongs, setFullscreen, startRadio, usePlayer } from '@/player/store';
import type { Playlist } from '@/sources/types';
import { settings } from '@/settings/store';

import { Cover } from './cover';
import type { MenuItem } from './menu';
import { openMenu } from './menu';
import { confirm, prompt } from './modal';
import { toast } from './toast';
import { Stars } from './ui';

/** Menus de contexto das músicas, álbuns, artistas e playlists. */

const t = (key: string, options?: Record<string, unknown>) => i18n.t(key, options);

function playlistSubmenu(songs: Song[]): MenuItem[] {
    const playlists = libraryState().playlists.filter((p) => !p.readonly);
    const items: MenuItem[] = [
        {
            icon: 'add',
            label: t('playlist.new'),
            onSelect: async () => {
                const name = await prompt(t('playlist.new'), t('playlist.name'));
                if (!name) return;
                const created = await createPlaylist(name, songs.map((s) => s.id));
                if (created) toast.success(t('playlist.created', { name }));
            },
        },
    ];
    if (playlists.length) items.push({ divider: true });
    for (const p of playlists) {
        items.push({
            icon: 'playlist',
            label: p.name,
            onSelect: async () => {
                const existing = new Set(p.songIds);
                const fresh = songs.filter((s) => !existing.has(s.id));
                if (!fresh.length) {
                    toast.info(t('playlist.alreadyThere', { name: p.name }));
                    return;
                }
                await addToPlaylist(p.id, fresh.map((s) => s.id));
                toast.success(t('playlist.added', { count: fresh.length, name: p.name }));
            },
        });
    }
    return items;
}

export interface SongMenuContext {
    /** Remover da playlist (na página da playlist). */
    playlist?: Playlist;
    playlistIndex?: number;
    /** Lista em que a música está, para "tocar a partir daqui". */
    list?: Song[];
    index?: number;
}

/** Vai para uma página saindo do player em tela cheia (senão ela abre escondida atrás dele). */
const goTo = (path: string) => {
    setFullscreen(false);
    navigate(path);
};

export function songMenuItems(song: Song, ctx: SongMenuContext = {}): MenuItem[] {
    const artist = libraryState().library.artistByName(song.artists[0] || song.albumArtist || song.artist);
    const { index, queue } = usePlayer.getState();
    const isPlaying = queue[index]?.song.id === song.id;
    const items: MenuItem[] = [];
    // "Tocar" não faz sentido para a música que já está tocando.
    if (!isPlaying) {
        items.push({ icon: 'mediaPlay', label: t('actions.play'), onSelect: () => (ctx.list ? playSongs(ctx.list, ctx.index ?? 0, { shuffle: false }) : playSongs([song], 0, { shuffle: false })) });
    }
    items.push(
        { icon: 'queue', label: t('actions.playNext'), onSelect: () => playNext([song]) },
        { icon: 'list', label: t('actions.addToQueue'), onSelect: () => addToQueue([song]) },
        { icon: 'radio', label: t('actions.startRadio'), onSelect: () => startRadio(song) },
        { divider: true },
        { children: playlistSubmenu([song]), icon: 'addToPlaylist', label: t('actions.addToPlaylist') },
        {
            icon: 'favorite',
            keepOpen: true,
            label: t('actions.favorite'),
            onSelect: () => void setFavorite('song', song.id, !isFavorite('song', song.id)),
            useLive: () => {
                const fav = useIsFavorite('song', song.id);
                return { filled: fav, label: fav ? t('actions.unfavorite') : t('actions.favorite') };
            },
        },
    );
    if (settings().showRatings) items.push({ custom: <RatingRow songId={song.id} /> });
    items.push({ divider: true });
    items.push({ icon: 'album', label: t('actions.goToAlbum'), onSelect: () => goTo(routes.album(song.albumId)) });
    if (artist) items.push({ icon: 'artist', label: t('actions.goToArtist'), onSelect: () => goTo(routes.artist(artist.id)) });
    if (ctx.playlist && !ctx.playlist.readonly && ctx.playlistIndex !== undefined) {
        const playlist = ctx.playlist;
        const index = ctx.playlistIndex;
        items.push({ divider: true });
        items.push({
            danger: true,
            icon: 'trash',
            label: t('playlist.removeSong'),
            onSelect: () => void updatePlaylist(playlist.id, { songIds: playlist.songIds.filter((_, i) => i !== index) }),
        });
    }
    return items;
}

function RatingRow({ songId }: { songId: string }) {
    // Lê a nota da store: muda na hora ao clicar nas estrelas, com o menu aberto.
    const rating = useLibrary((s) => s.user.ratings[songId] || 0);
    return (
        <div style={{ alignItems: 'center', display: 'flex', gap: 12, justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--fg-muted)', fontSize: 13 }}>{t('actions.rating')}</span>
            <Stars onChange={(value) => void setRating(songId, value)} value={rating} />
        </div>
    );
}

export function openSongMenu(at: { x: number; y: number } | HTMLElement, song: Song, ctx?: SongMenuContext) {
    openMenu(at, songMenuItems(song, ctx), {
        media: <Cover coverId={song.coverId} seed={song.albumId} size="sm" />,
        subtitle: song.artist,
        title: song.title,
    });
}

/** Menu para várias músicas (álbum, playlist, mix). */
export function songsMenuItems(songs: Song[]): MenuItem[] {
    return [
        { icon: 'mediaPlay', label: t('actions.play'), onSelect: () => playSongs(songs, 0, { shuffle: false }) },
        { icon: 'shuffle', label: t('actions.shuffle'), onSelect: () => playSongs(songs, 0, { shuffle: true }) },
        { icon: 'queue', label: t('actions.playNext'), onSelect: () => playNext(songs) },
        { icon: 'list', label: t('actions.addToQueue'), onSelect: () => addToQueue(songs) },
        { divider: true },
        { children: playlistSubmenu(songs), icon: 'addToPlaylist', label: t('actions.addToPlaylist') },
    ];
}

export function openAlbumMenu(at: { x: number; y: number } | HTMLElement, album: Album) {
    const fav = isFavorite('album', album.id);
    const artist = album.artistNames[0] ? libraryState().library.artistByName(album.artistNames[0]) : null;
    const items: MenuItem[] = [
        ...songsMenuItems(album.songs),
        { icon: 'favorite', label: fav ? t('actions.unfavorite') : t('actions.favorite'), onSelect: () => setFavorite('album', album.id, !fav) },
        { divider: true },
        { icon: 'album', label: t('actions.openAlbum'), onSelect: () => goTo(routes.album(album.id)) },
    ];
    if (artist) items.push({ icon: 'artist', label: t('actions.goToArtist'), onSelect: () => goTo(routes.artist(artist.id)) });
    openMenu(at, items, { media: <Cover coverId={album.coverId} seed={album.id} size="sm" />, subtitle: album.artist, title: album.name });
}

export function openArtistMenu(at: { x: number; y: number } | HTMLElement, artist: Artist) {
    const fav = isFavorite('artist', artist.key);
    const items: MenuItem[] = [
        ...songsMenuItems(artist.songs),
        { icon: 'radio', label: t('actions.startRadio'), onSelect: () => artist.songs[0] && startRadio(artist.songs[Math.floor(Math.random() * artist.songs.length)]) },
        { icon: 'favorite', label: fav ? t('actions.unfavorite') : t('actions.favorite'), onSelect: () => setFavorite('artist', artist.key, !fav) },
        { divider: true },
        { icon: 'artist', label: t('actions.openArtist'), onSelect: () => goTo(routes.artist(artist.id)) },
    ];
    openMenu(at, items, { media: <Cover coverId={artist.coverId} icon="user" round seed={artist.id} size="sm" />, subtitle: t('library.artist'), title: artist.name });
}

export function openPlaylistMenu(at: { x: number; y: number } | HTMLElement, playlist: Playlist, songs: Song[]) {
    const items: MenuItem[] = [...songsMenuItems(songs).slice(0, 4)];
    if (!playlist.readonly) {
        items.push({ divider: true });
        items.push({
            icon: 'edit',
            label: t('playlist.rename'),
            onSelect: async () => {
                const name = await prompt(t('playlist.rename'), t('playlist.name'), playlist.name);
                if (name && name !== playlist.name) await updatePlaylist(playlist.id, { name });
            },
        });
        items.push({
            danger: true,
            icon: 'trash',
            label: t('playlist.delete'),
            onSelect: async () => {
                if (await confirm(t('playlist.delete'), t('playlist.deleteConfirm', { name: playlist.name }), t('common.delete'), true)) {
                    await deletePlaylist(playlist.id);
                    navigate(routes.library('playlists'));
                }
            },
        });
    }
    openMenu(at, items, { subtitle: t('common.songCount', { count: songs.length, formatted: songs.length }), title: playlist.name });
}
