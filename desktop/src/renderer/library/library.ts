import { normalizeText, stableId } from '@shared/text';
import type { Song } from '@shared/types';

export interface Album {
    id: string;
    name: string;
    /** Artista mostrado ("Vários artistas" em coletâneas). */
    artist: string;
    artistNames: string[];
    year: number;
    songs: Song[];
    durationMs: number;
    dateAdded: number;
    genres: string[];
    coverId: null | string;
    folder: string;
}

export interface Artist {
    id: string;
    /** Nome normalizado (chave de favoritos e do histórico). */
    key: string;
    name: string;
    /** Álbuns em que é artista do álbum. */
    albums: Album[];
    /** Álbuns em que só aparece em alguma faixa. */
    appearsOn: Album[];
    songs: Song[];
    coverId: null | string;
}

export interface Genre {
    id: string;
    key: string;
    name: string;
    songs: Song[];
    albums: Album[];
    coverId: null | string;
}

export interface FolderNode {
    path: string;
    name: string;
    children: FolderNode[];
    songs: Song[];
    coverId: null | string;
    totalSongs: number;
}

export const VARIOUS_ARTISTS = 'Vários artistas';

const collator = new Intl.Collator('pt-BR', { numeric: true, sensitivity: 'base' });
export const compareText = (a: string, b: string) => collator.compare(a, b);

const byTrack = (a: Song, b: Song) => a.disc - b.disc || a.track - b.track || compareText(a.path || a.title, b.path || b.title);

const folderOf = (song: Song) => (song.path.includes('/') ? song.path.slice(0, song.path.lastIndexOf('/')) : '');

export const artistKey = (name: string) => normalizeText(name);
export const artistIdFor = (name: string) => stableId(`artist:${artistKey(name)}`);
export const genreIdFor = (name: string) => stableId(`genre:${normalizeText(name)}`);

export interface SearchResult {
    songs: Song[];
    albums: Album[];
    artists: Artist[];
    genres: Genre[];
}

/** Índices em memória calculados a partir das faixas. */
export class Library {
    readonly songsById = new Map<string, Song>();
    readonly albumsById = new Map<string, Album>();
    readonly artistsById = new Map<string, Artist>();
    readonly artistsByKey = new Map<string, Artist>();
    readonly genresById = new Map<string, Genre>();
    readonly foldersByPath = new Map<string, FolderNode>();
    readonly albums: Album[];
    readonly artists: Artist[];
    readonly genres: Genre[];
    readonly rootFolders: FolderNode[];
    readonly hasFolders: boolean;
    private songKeys: string[] = [];
    private albumKeys: string[] = [];
    private artistKeys: string[] = [];

    static readonly EMPTY = new Library([]);

    constructor(readonly songs: Song[]) {
        const sorted = [...songs].sort((a, b) => compareText(a.title, b.title));
        this.songs = sorted;
        for (const song of sorted) this.songsById.set(song.id, song);

        // Álbuns
        const grouped = new Map<string, Song[]>();
        for (const song of sorted) {
            const list = grouped.get(song.albumId);
            if (list) list.push(song);
            else grouped.set(song.albumId, [song]);
        }
        const albums: Album[] = [];
        for (const [id, list] of grouped) {
            const tracks = list.sort(byTrack);
            const withAlbumArtist = tracks.find((t) => t.albumArtist.trim());
            const distinctArtists = new Set(tracks.map((t) => normalizeText(t.artist)));
            const display = withAlbumArtist ? withAlbumArtist.albumArtist : distinctArtists.size === 1 ? tracks[0].artist : VARIOUS_ARTISTS;
            const names = new Map<string, string>();
            if (display !== VARIOUS_ARTISTS) {
                for (const t of tracks) {
                    const source = t.albumArtist.trim() ? t.albumArtist.split(/\s*;\s*|\s+\/\s+/) : t.artists;
                    for (const n of source) if (n.trim()) names.set(artistKey(n), n.trim());
                }
            }
            const genres = new Map<string, string>();
            tracks.forEach((t) => t.genres.forEach((g) => genres.set(normalizeText(g), g)));
            albums.push({
                artist: display,
                artistNames: display === VARIOUS_ARTISTS ? [VARIOUS_ARTISTS] : [...names.values()],
                coverId: tracks.find((t) => t.coverId)?.coverId || null,
                dateAdded: Math.max(...tracks.map((t) => t.dateAdded || 0)),
                durationMs: tracks.reduce((acc, t) => acc + t.durationMs, 0),
                folder: folderOf(tracks[0]),
                genres: [...genres.values()],
                id,
                name: tracks[0].album || 'Sem álbum',
                songs: tracks,
                year: Math.max(...tracks.map((t) => t.year || 0)),
            });
        }
        albums.sort((a, b) => compareText(a.name, b.name));
        this.albums = albums;
        for (const album of albums) this.albumsById.set(album.id, album);

        // Artistas
        interface Acc {
            name: string;
            albums: Map<string, Album>;
            appears: Map<string, Album>;
            songs: Map<string, Song>;
        }
        const acc = new Map<string, Acc>();
        const accFor = (name: string) => {
            const key = artistKey(name);
            let a = acc.get(key);
            if (!a) {
                a = { albums: new Map(), appears: new Map(), name, songs: new Map() };
                acc.set(key, a);
            }
            return a;
        };
        for (const album of albums) {
            for (const name of album.artistNames) if (name !== VARIOUS_ARTISTS) accFor(name).albums.set(album.id, album);
            for (const song of album.songs) {
                const names = new Map<string, string>();
                for (const n of song.artists) names.set(artistKey(n), n);
                for (const n of song.albumArtist ? song.albumArtist.split(/\s*;\s*|\s+\/\s+/) : []) if (n.trim()) names.set(artistKey(n), n.trim());
                for (const name of names.values()) {
                    const a = accFor(name);
                    a.songs.set(song.id, song);
                    if (!a.albums.has(album.id)) a.appears.set(album.id, album);
                }
            }
        }
        const rank = new Map(sorted.map((s, i) => [s.id, i]));
        const byYearDesc = (a: Album, b: Album) => b.year - a.year || compareText(a.name, b.name);
        this.artists = [...acc.entries()]
            .filter(([key]) => key)
            .map(([key, a]) => {
                const all = [...a.albums.values(), ...a.appears.values()];
                return {
                    albums: [...a.albums.values()].sort(byYearDesc),
                    appearsOn: [...a.appears.values()].filter((al) => !a.albums.has(al.id)).sort(byYearDesc),
                    coverId: all.find((al) => al.coverId)?.coverId || null,
                    id: stableId(`artist:${key}`),
                    key,
                    name: a.name,
                    songs: [...a.songs.values()].sort((x, y) => (rank.get(x.id) || 0) - (rank.get(y.id) || 0)),
                };
            })
            .sort((a, b) => compareText(a.name, b.name));
        for (const artist of this.artists) {
            this.artistsById.set(artist.id, artist);
            this.artistsByKey.set(artist.key, artist);
        }

        // Gêneros
        const genreAcc = new Map<string, { name: string; songs: Song[] }>();
        for (const song of sorted) {
            for (const g of song.genres) {
                const key = normalizeText(g);
                if (!key) continue;
                const entry = genreAcc.get(key) || { name: g, songs: [] };
                entry.songs.push(song);
                genreAcc.set(key, entry);
            }
        }
        this.genres = [...genreAcc.entries()]
            .map(([key, entry]) => {
                const genreAlbums = [...new Set(entry.songs.map((s) => s.albumId))].map((id) => this.albumsById.get(id)!).filter(Boolean);
                return {
                    albums: genreAlbums,
                    coverId: genreAlbums.find((a) => a.coverId)?.coverId || null,
                    id: stableId(`genre:${key}`),
                    key,
                    name: entry.name,
                    songs: entry.songs,
                };
            })
            .sort((a, b) => b.songs.length - a.songs.length);
        for (const genre of this.genres) this.genresById.set(genre.id, genre);

        // Pastas
        const folderSongs = new Map<string, Song[]>();
        for (const song of sorted) {
            if (!song.path) continue;
            const folder = folderOf(song);
            const list = folderSongs.get(folder) || [];
            list.push(song);
            folderSongs.set(folder, list);
        }
        this.hasFolders = folderSongs.size > 0;
        const allPaths = new Set<string>();
        for (const path of folderSongs.keys()) {
            let p = path;
            while (p && !allPaths.has(p)) {
                allPaths.add(p);
                const idx = p.lastIndexOf('/');
                if (idx <= 0) break;
                p = p.slice(0, idx);
            }
        }
        const childrenOf = new Map<string, string[]>();
        for (const p of allPaths) {
            const idx = p.lastIndexOf('/');
            const parent = idx <= 0 ? '' : p.slice(0, idx);
            childrenOf.set(parent, [...(childrenOf.get(parent) || []), p]);
        }
        const build = (path: string): FolderNode => {
            const existing = this.foldersByPath.get(path);
            if (existing) return existing;
            const children = (childrenOf.get(path) || []).map(build).sort((a, b) => compareText(a.name, b.name));
            const own = [...(folderSongs.get(path) || [])].sort(byTrack);
            const node: FolderNode = {
                children,
                coverId: own.find((s) => s.coverId)?.coverId || children.find((c) => c.coverId)?.coverId || null,
                name: path.slice(path.lastIndexOf('/') + 1) || path,
                path,
                songs: own,
                totalSongs: own.length + children.reduce((n, c) => n + c.totalSongs, 0),
            };
            this.foldersByPath.set(path, node);
            return node;
        };
        const rootSongs = folderSongs.get('') || [];
        this.rootFolders = (childrenOf.get('') || []).map(build).sort((a, b) => compareText(a.name, b.name));
        if (rootSongs.length) {
            this.foldersByPath.set('', { children: this.rootFolders, coverId: null, name: '', path: '', songs: rootSongs.sort(byTrack), totalSongs: sorted.length });
        }
    }

    get isEmpty() {
        return this.songs.length === 0;
    }

    artistByName(name: string) {
        return this.artistsByKey.get(artistKey(name)) || null;
    }

    search(query: string): SearchResult {
        const terms = normalizeText(query).split(/\s+/).filter(Boolean);
        if (!terms.length) return { albums: [], artists: [], genres: [], songs: [] };
        if (!this.songKeys.length) {
            this.songKeys = this.songs.map((s) => normalizeText(`${s.title} ${s.artist} ${s.album}`));
            this.albumKeys = this.albums.map((a) => normalizeText(`${a.name} ${a.artist}`));
            this.artistKeys = this.artists.map((a) => a.key);
        }
        const matches = (key: string) => terms.every((t) => key.includes(t));
        const pick = <T,>(items: T[], keys: string[], limit: number) => {
            const out: T[] = [];
            for (let i = 0; i < items.length && out.length < limit; i += 1) if (matches(keys[i])) out.push(items[i]);
            return out;
        };
        const q = terms.join(' ');
        // Quem começa com a busca vem primeiro.
        const score = (text: string) => (normalizeText(text).startsWith(q) ? 0 : 1);
        return {
            albums: pick(this.albums, this.albumKeys, 60).sort((a, b) => score(a.name) - score(b.name)),
            artists: pick(this.artists, this.artistKeys, 60).sort((a, b) => score(a.name) - score(b.name) || b.songs.length - a.songs.length),
            genres: this.genres.filter((g) => terms.every((t) => g.key.includes(t))).slice(0, 20),
            songs: pick(this.songs, this.songKeys, 300).sort((a, b) => score(a.title) - score(b.title)),
        };
    }
}
