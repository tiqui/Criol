import { describe, expect, it } from 'vitest';

import { isPlaceholderLyrics } from './lyrics/clean';
import { activeLineIndex, parseLyrics } from './lyrics/parse';
import { tagsFromPath } from './path-tags';
import { normalizeText, splitArtists, splitGenres, stableId } from './text';

describe('texto', () => {
    it('normaliza acentos e caixa', () => {
        expect(normalizeText('  Ação Ç É  ')).toBe('acao c e');
    });

    it('separa artistas sem quebrar AC/DC', () => {
        expect(splitArtists('AC/DC')).toEqual(['AC/DC']);
        expect(splitArtists('Anitta; J Balvin / Ozuna')).toEqual(['Anitta', 'J Balvin', 'Ozuna']);
        expect(splitArtists('A;a; ')).toEqual(['A']);
    });

    it('separa gêneros', () => {
        expect(splitGenres('Rock, Pop;MPB')).toEqual(['Rock', 'Pop', 'MPB']);
        expect(splitGenres(['Jazz', 'jazz'])).toEqual(['Jazz']);
    });

    it('gera ids estáveis', () => {
        expect(stableId('abc')).toBe(stableId('abc'));
        expect(stableId('abc')).not.toBe(stableId('abd'));
        expect(stableId('abc')).toHaveLength(16);
    });
});

describe('tags pelo caminho', () => {
    it('lê artista, álbum, faixa e título', () => {
        expect(tagsFromPath('Aurora Lane/Neon Tides/01 - Midnight Drive.wav')).toEqual({ album: 'Neon Tides', artist: 'Aurora Lane', disc: 0, title: 'Midnight Drive', track: 1 });
    });

    it('lê disco-faixa e nomes sem número', () => {
        expect(tagsFromPath('Banda/Álbum/2-05 Canção.flac')).toMatchObject({ disc: 2, title: 'Canção', track: 5 });
        expect(tagsFromPath('solta.mp3')).toMatchObject({ album: '', artist: '', title: 'solta', track: 0 });
    });
});

describe('letras', () => {
    it('lê LRC com várias marcações e offset', () => {
        const lyrics = parseLyrics('[ar:Alguém]\n[offset:500]\n[00:10.00][00:30.00]Refrão\n[00:20.50]Verso', 'teste');
        expect(lyrics?.synced).toBe(true);
        expect(lyrics?.lines.map((l) => [l.time, l.text])).toEqual([
            [9500, 'Refrão'],
            [20000, 'Verso'],
            [29500, 'Refrão'],
        ]);
        expect(activeLineIndex(lyrics!.lines, 21000)).toBe(1);
        expect(activeLineIndex(lyrics!.lines, 100)).toBe(-1);
    });

    it('lê texto sem sincronia', () => {
        const lyrics = parseLyrics('Linha um\n\nLinha dois\n\n', 'teste');
        expect(lyrics?.synced).toBe(false);
        expect(lyrics?.lines.map((l) => l.text)).toEqual(['Linha um', '', 'Linha dois']);
    });

    it('ignora letras só com créditos ou texto inventado', () => {
        expect(parseLyrics('[00:00.00]作词 : Fulano\n[00:01.00]作曲 : Ciclano', 'x')).toBeNull();
        const fake = 'Wob gopini den vudob qazaqo kimi\nVuqew qazaqo kimi tobale nupi\nGopini vudob sepa qanu dovi lika\nMoqe fiqa bopu reqi nuka';
        expect(isPlaceholderLyrics(fake.split('\n'))).toBe(true);
        expect(isPlaceholderLyrics(['Eu sei que vou te amar', 'por toda a minha vida eu vou te amar', 'em cada despedida eu vou te amar', 'desesperadamente'])).toBe(false);
    });
});
