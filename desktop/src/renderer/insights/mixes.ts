import { createStore, get, set, type UseStore } from 'idb-keyval';
import { create } from 'zustand';

import { normalizeText } from '@shared/text';
import type { Song } from '@shared/types';

import type { Library } from '@/library/library';
import type { UserData } from '@/library/store';
import { shuffled } from '@/player/auto-dj';

import type { ListenEntry } from './listening-db';
import { DAY_MS, type Daypart, getDaypart, scoreGenres, scoreSongs } from './listening-stats';

/**
 * "Feito para você": daylist, mixes do período do dia, No Repeat, De volta pro repeat,
 * Radar de descobertas e mixes por gênero. Sem histórico ainda, usa as contagens de reprodução
 * e a biblioteca. Cada mix só é refeita quando o período dela vence.
 */

export type MixKind = 'daylist' | 'daypart' | 'discover' | 'genre' | 'onRepeat' | 'repeatRewind';

export interface Mix {
    id: string;
    kind: MixKind;
    daypart?: Daypart;
    genre?: string;
    /** Até três gêneros que descrevem a mix (título da daylist). */
    tags: string[];
    songIds: string[];
    coverIds: string[];
    generatedAt: number;
    periodKey: string;
    /** 0 = domingo ... 6 = sábado. */
    weekday: number;
    personalized: boolean;
}

const MIX_SIZE = 40;
const MAX_PER_ARTIST = 3;
const ORDER = ['daylist', 'on-repeat', 'repeat-rewind', 'daypart-morning', 'daypart-afternoon', 'daypart-night', 'discover'];

export const sortMixes = (mixes: Mix[]) =>
    [...mixes].sort((a, b) => {
        const ia = ORDER.indexOf(a.id);
        const ib = ORDER.indexOf(b.id);
        return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
    });

const dayKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const weekKey = (d: Date) => {
    const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const day = t.getUTCDay() || 7;
    t.setUTCDate(t.getUTCDate() + 4 - day);
    const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
    return `${t.getUTCFullYear()}-w${Math.ceil(((t.getTime() - yearStart.getTime()) / DAY_MS + 1) / 7)}`;
};

export const mixPeriodKey = (kind: MixKind, now: Date, daypart?: Daypart) => {
    if (kind === 'daylist') return `${dayKey(now)}-${daypart || getDaypart(now)}`;
    if (kind === 'daypart' || kind === 'onRepeat') return dayKey(now);
    return weekKey(now);
};

export class MixGenerator {
    private readonly nowMs: number;
    private readonly entries: ListenEntry[];
    private readonly heard: Set<string>;
    private readonly genreIndex: Map<string, Library['genres'][number]>;
    private topPlayedCache: null | Song[] = null;

    constructor(
        private readonly library: Library,
        private readonly user: UserData,
        allEntries: ListenEntry[],
        sourceId: string,
        private readonly now = new Date(),
    ) {
        this.nowMs = now.getTime();
        // Só o histórico desta fonte: os ids das músicas são de cada servidor/pasta.
        this.entries = allEntries.filter((e) => e.playedAt >= this.nowMs - 400 * DAY_MS && (!e.serverId || e.serverId === sourceId));
        this.heard = new Set(this.entries.map((e) => e.songId));
        this.genreIndex = new Map(library.genres.map((g) => [g.key, g]));
    }

    private get topPlayed() {
        this.topPlayedCache ??= this.library.songs
            .filter((s) => (this.user.playCounts[s.id] || 0) > 0)
            .sort((a, b) => (this.user.playCounts[b.id] || 0) - (this.user.playCounts[a.id] || 0))
            .slice(0, 300);
        return this.topPlayedCache;
    }

    private resolve(ids: string[]) {
        return ids.map((id) => this.library.songsById.get(id)).filter((s): s is Song => Boolean(s));
    }

    private randomByGenre(name: string, limit: number, neverPlayed = false) {
        let list = this.genreIndex.get(normalizeText(name))?.songs || [];
        if (neverPlayed) list = list.filter((s) => !this.heard.has(s.id) && !(this.user.playCounts[s.id] > 0));
        return shuffled(list).slice(0, limit);
    }

    /** Gêneros pelo histórico; sem histórico, pelas mais tocadas; senão, os maiores. */
    private tasteGenres(filter?: (e: ListenEntry) => boolean): [string[], boolean] {
        const known = (names: string[]) => names.filter((n) => this.genreIndex.has(normalizeText(n)));
        const fromHistory = known(scoreGenres(this.entries, { filter, now: this.nowMs }).map((g) => g.name));
        if (fromHistory.length >= 2) return [fromHistory, true];
        const fallback = known(scoreGenres(this.entries, { now: this.nowMs }).map((g) => g.name));
        if (fallback.length >= 2) return [fallback, true];
        const counts = new Map<string, { name: string; n: number }>();
        for (const s of this.topPlayed) {
            for (const g of s.genres) {
                const key = normalizeText(g);
                const cur = counts.get(key) || { n: 0, name: g };
                cur.n += this.user.playCounts[s.id] || 1;
                counts.set(key, cur);
            }
        }
        let genres = known([...counts.values()].sort((a, b) => b.n - a.n).map((c) => c.name));
        if (!genres.length) genres = shuffled(this.library.genres.slice(0, 12)).map((g) => g.name);
        return [genres, false];
    }

    private interleave(lists: Song[][]) {
        const out: Song[] = [];
        const max = Math.max(0, ...lists.map((l) => l.length));
        for (let i = 0; i < max; i += 1) for (const list of lists) if (list[i]) out.push(list[i]);
        return out;
    }

    private finalize(songs: Song[], limit = MIX_SIZE, maxPerArtist = MAX_PER_ARTIST) {
        const seen = new Set<string>();
        const perArtist = new Map<string, number>();
        const out: Song[] = [];
        for (const s of songs) {
            if (seen.has(s.id)) continue;
            seen.add(s.id);
            const artist = normalizeText(s.artists[0] || s.albumArtist);
            const count = perArtist.get(artist) || 0;
            if (artist && count >= maxPerArtist) continue;
            perArtist.set(artist, count + 1);
            out.push(s);
            if (out.length >= limit) break;
        }
        return out;
    }

    private sampleTop(ids: string[], take: number) {
        const headSize = Math.max(1, Math.floor(take * 0.6));
        const head = ids.slice(0, headSize);
        const tail = shuffled(ids.slice(head.length, head.length + take * 3)).slice(0, take - head.length);
        return shuffled([...head, ...tail]);
    }

    private build(id: string, kind: MixKind, songs: Song[], personalized: boolean, extra: Partial<Mix> = {}): Mix {
        const covers: string[] = [];
        const seenAlbums = new Set<string>();
        for (const s of songs) {
            if (!s.coverId || seenAlbums.has(s.albumId)) continue;
            seenAlbums.add(s.albumId);
            covers.push(s.coverId);
            if (covers.length >= 4) break;
        }
        return {
            coverIds: covers,
            generatedAt: this.nowMs,
            id,
            kind,
            periodKey: mixPeriodKey(kind, this.now, extra.daypart),
            personalized,
            songIds: songs.map((s) => s.id),
            tags: [],
            weekday: this.now.getDay(),
            ...extra,
        };
    }

    daypartMix(daypart: Daypart, daylist: boolean): Mix {
        const weekday = this.now.getDay();
        const inDaypart = (e: ListenEntry) => getDaypart(e.playedAt) === daypart;
        // A daylist também puxa o que você costuma ouvir neste dia da semana.
        const weighted = daylist ? (e: ListenEntry) => inDaypart(e) || new Date(e.playedAt).getDay() === weekday : inDaypart;
        const [genres, personalized] = this.tasteGenres(weighted);
        const top = daylist ? genres.slice(0, 3) : shuffled(genres.slice(0, 5)).slice(0, 3);
        const favoriteIds = scoreSongs(this.entries, { filter: weighted, now: this.nowMs }).map((s) => s.entry.songId);
        const favorites = this.resolve(this.sampleTop(favoriteIds, 18));
        const discovered = this.interleave(top.map((g) => this.randomByGenre(g, 20)));
        let songs = this.finalize(this.interleave([favorites, discovered]));
        if (songs.length < 10) songs = this.finalize([...songs, ...shuffled(this.topPlayed)]);
        if (songs.length < 10) songs = this.finalize([...songs, ...shuffled(this.library.songs).slice(0, 300)]);
        return this.build(daylist ? 'daylist' : `daypart-${daypart}`, daylist ? 'daylist' : 'daypart', songs, personalized, { daypart, tags: top });
    }

    onRepeat(): Mix {
        const since = this.nowMs - 30 * DAY_MS;
        const ranked = scoreSongs(this.entries, { filter: (e) => e.playedAt >= since, halfLifeDays: 14, now: this.nowMs }).filter((s) => s.plays >= 2);
        let songs = this.resolve(ranked.slice(0, 30).map((s) => s.entry.songId));
        const personalized = songs.length >= 8;
        if (!personalized) {
            const recentTop = this.topPlayed.filter((s) => (this.user.lastPlayed[s.id] || 0) >= since);
            songs = [...songs, ...recentTop, ...this.topPlayed];
        }
        return this.build('on-repeat', 'onRepeat', this.finalize(songs, 30, 5), personalized);
    }

    repeatRewind(): Mix {
        const recentCut = this.nowMs - 30 * DAY_MS;
        const oldCut = this.nowMs - 400 * DAY_MS;
        const recent = new Set(this.entries.filter((e) => e.playedAt >= recentCut).map((e) => e.songId));
        const ranked = scoreSongs(this.entries, {
            filter: (e) => e.playedAt >= oldCut && e.playedAt < recentCut && !recent.has(e.songId),
            halfLifeDays: 120,
            now: this.nowMs,
        }).filter((s) => s.plays >= 3);
        let songs = this.resolve(ranked.slice(0, 30).map((s) => s.entry.songId));
        const personalized = songs.length >= 8;
        if (!personalized) songs = [...songs, ...this.topPlayed.filter((s) => !recent.has(s.id) && (this.user.lastPlayed[s.id] || 0) < recentCut)];
        return this.build('repeat-rewind', 'repeatRewind', this.finalize(songs, 30, 4), personalized);
    }

    discover(): Mix {
        const [genres, personalized] = this.tasteGenres();
        let fresh = this.interleave(genres.slice(0, 5).map((g) => this.randomByGenre(g, 25, true)));
        if (fresh.length < 15) {
            fresh = [...fresh, ...shuffled(this.library.songs.filter((s) => !this.heard.has(s.id) && !(this.user.playCounts[s.id] > 0))).slice(0, 60)];
        }
        return this.build('discover', 'discover', this.finalize(fresh, 30, 2), personalized, { tags: genres.slice(0, 3) });
    }

    genreMix(genre: string, personalized: boolean): Mix {
        const key = normalizeText(genre);
        const favoriteIds = scoreSongs(this.entries, { filter: (e) => e.genres.some((g) => normalizeText(g) === key), halfLifeDays: 60, now: this.nowMs }).map((s) => s.entry.songId);
        const favorites = this.resolve(this.sampleTop(favoriteIds, 12));
        const others = this.randomByGenre(genre, 40);
        return this.build(`genre-${key.replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}`, 'genre', this.finalize(this.interleave([favorites, others])), personalized, { genre, tags: [genre] });
    }

    genreMixes() {
        const [genres, personalized] = this.tasteGenres();
        return genres.slice(0, 3).map((g) => this.genreMix(g, personalized));
    }

    private covers?: Set<string>;
    /** Capas que existem na biblioteca atual. */
    private coverIds() {
        return (this.covers ??= new Set(this.library.songs.map((s) => s.coverId).filter((id): id is string => Boolean(id))));
    }

    isFresh(mix: Mix) {
        const daypart = mix.kind === 'daylist' ? getDaypart(this.now) : mix.daypart;
        if (mix.periodKey !== mixPeriodKey(mix.kind, this.now, daypart)) return false;
        // A biblioteca mudou desde que a mix foi feita (gênero sumiu, músicas saíram): refaz.
        if (mix.kind === 'genre' && !this.library.genres.some((g) => g.key === normalizeText(mix.genre || ''))) return false;
        if (mix.songIds.length && mix.songIds.filter((id) => this.library.songsById.has(id)).length < mix.songIds.length * 0.8) return false;
        if (mix.coverIds.some((id) => !this.coverIds().has(id))) return false;
        // Mix que saiu vazia tenta de novo depois de uma hora (o histórico pode ter crescido).
        return mix.songIds.length > 0 || this.nowMs - mix.generatedAt < 60 * 60 * 1000;
    }

    private tasks(): [string, () => Mix][] {
        return [
            ['daylist', () => this.daypartMix(getDaypart(this.now), true)],
            ['on-repeat', () => this.onRepeat()],
            ['repeat-rewind', () => this.repeatRewind()],
            ['daypart-morning', () => this.daypartMix('morning', false)],
            ['daypart-afternoon', () => this.daypartMix('afternoon', false)],
            ['daypart-night', () => this.daypartMix('night', false)],
            ['discover', () => this.discover()],
        ];
    }

    allFresh(stored: Mix[]) {
        const byId = new Map(stored.map((m) => [m.id, m]));
        const genre = stored.filter((m) => m.kind === 'genre');
        return this.tasks().every(([id]) => byId.has(id) && this.isFresh(byId.get(id)!)) && genre.length > 0 && genre.every((m) => this.isFresh(m));
    }

    /** Refaz só as mixes cujo período mudou. */
    refresh(stored: Mix[], force = false) {
        const byId = force ? new Map<string, Mix>() : new Map(stored.map((m) => [m.id, m]));
        const out: Mix[] = [];
        for (const [id, make] of this.tasks()) {
            const existing = byId.get(id);
            out.push(existing && this.isFresh(existing) ? existing : make());
        }
        const genre = force ? [] : stored.filter((m) => m.kind === 'genre');
        out.push(...(genre.length && genre.every((m) => this.isFresh(m)) ? genre : this.genreMixes()));
        return sortMixes(out);
    }

    rebuild(mix: Mix): Mix {
        switch (mix.kind) {
            case 'daylist':
                return this.daypartMix(getDaypart(this.now), true);
            case 'daypart':
                return this.daypartMix(mix.daypart || 'morning', false);
            case 'onRepeat':
                return this.onRepeat();
            case 'repeatRewind':
                return this.repeatRewind();
            case 'discover':
                return this.discover();
            default:
                return this.genreMix(mix.genre || '', mix.personalized);
        }
    }
}

// ---------------------------------------------------------------------------
// Armazenamento (por fonte)
// ---------------------------------------------------------------------------

let db: null | UseStore = null;
const store = () => (db ??= createStore('criol-mixes', 'mixes'));

interface MixState {
    sourceId: null | string;
    mixes: Mix[];
    busy: boolean;
}

export const useMixes = create<MixState>()(() => ({ busy: false, mixes: [], sourceId: null }));

let running: null | Promise<void> = null;

export async function ensureMixes(library: Library, user: UserData, entries: ListenEntry[], sourceId: string, force = false) {
    if (running) await running;
    running = (async () => {
        if (library.isEmpty) return;
        let mixes = useMixes.getState().sourceId === sourceId ? useMixes.getState().mixes : ((await get<Mix[]>(sourceId, store()).catch(() => undefined)) || []);
        useMixes.setState({ mixes, sourceId });
        const generator = new MixGenerator(library, user, entries, sourceId);
        if (!force && generator.allFresh(mixes)) return;
        useMixes.setState({ busy: true });
        try {
            mixes = generator.refresh(mixes, force);
            useMixes.setState({ mixes });
            await set(sourceId, mixes, store());
        } finally {
            useMixes.setState({ busy: false });
        }
    })();
    await running;
    running = null;
}

export async function regenerateMix(id: string, library: Library, user: UserData, entries: ListenEntry[], sourceId: string) {
    const current = useMixes.getState().mixes.find((m) => m.id === id);
    if (!current) return;
    const next = new MixGenerator(library, user, entries, sourceId).rebuild(current);
    const mixes = useMixes.getState().mixes.map((m) => (m.id === id ? next : m));
    useMixes.setState({ mixes });
    await set(sourceId, mixes, store());
}
