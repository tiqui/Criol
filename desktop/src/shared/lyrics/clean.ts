/**
 * Some providers (NetEase in particular) answer with "lyrics" that only carry credits
 * ("作词 : ...", "作曲 : ...", "Composer: ...") or a fixed note such as "纯音乐，请欣赏"
 * ("instrumental, please enjoy"). Shown as lyrics that looks like random words, so credit and
 * placeholder lines are dropped, and lyrics with nothing left count as "not available".
 */

const CREDIT_LINE =
    /^(作词|作詞|作曲|编曲|編曲|制作人|製作人|制作|製作|词|詞|曲|填词|监制|監製|混音|母带|母帶|和声|和聲|录音|錄音|出品|发行|發行|企划|企劃|统筹|統籌|策划|策劃|演唱|原唱|翻唱|吉他|贝斯|貝斯|鼓|键盘|鍵盤|弦乐|弦樂|OP|SP|ISRC|lyricist|lyrics\s*by|composer|composed\s*by|music\s*by|producer|produced\s*by|arranger|arranged\s*by|written\s*by|mixed\s*by|mastered\s*by|recorded\s*by)\s*[:：]/i;

const PLACEHOLDER_LINE =
    /^(纯音乐[，,]?\s*请欣赏|此歌曲为没有填词的纯音乐[，,]?\s*请您欣赏|暂无歌词|暫無歌詞|[([]?\s*instrumental\s*[)\]]?)$/i;

/** Line text without leading [mm:ss.xx] / [ar:...] tags. */
const lineContent = (line: string) => line.replace(/^(\s*\[[^\]]*\])+/, '').trim();

/** A word made only of consonant+vowel pairs: "gopini", "qazaqo", "vudob". */
const CV_WORD = /^(?:[bcdfghjklmnpqrstvwxyz][aeiou])+[bcdfghjklmnpqrstvwxyz]?$/;
/** "q" not followed by "u" ("vuqew", "qanu"): almost never happens in real lyrics. */
const Q_WITHOUT_U = /q(?!u)/;

/**
 * Text that only imitates lyrics with made-up syllables ("Wob gopini den", "Vuqew qazaqo kimi"),
 * as written into music files by some tools as sample lyrics. Real lyrics, even in
 * syllable-based languages written in Latin letters, do not combine nearly every word built
 * from strict consonant+vowel pairs with "q" without "u" or almost no repeated words.
 */
export const isPlaceholderLyrics = (lines: string[]) => {
    const texts = lines.map(lineContent).filter(Boolean);
    // Generated files usually start with a "Title — Artist" header: ignore it.
    const body = texts.length > 1 && /\s[—–-]\s/.test(texts[0]) ? texts.slice(1) : texts;
    const words = body
        .flatMap((line) => line.toLowerCase().match(/[a-zà-ÿ']+/g) || [])
        .filter((word) => word.length >= 3);
    if (words.length < 12) return false;

    const cvRatio = words.filter((word) => CV_WORD.test(word)).length / words.length;
    if (cvRatio < 0.85) return false;

    const qWithoutU = words.filter((word) => Q_WITHOUT_U.test(word)).length;
    const uniqueRatio = new Set(words).size / words.length;
    return qWithoutU >= 2 || uniqueRatio >= 0.9;
};

export const isLyricsNoiseLine = (text: string) => {
    const content = lineContent(text);
    return CREDIT_LINE.test(content) || PLACEHOLDER_LINE.test(content);
};

/**
 * LRC or plain text without credit/placeholder lines, or null when no real lyric is left or the
 * text is made-up sample lyrics.
 */
export const cleanLyricsText = (text: null | string | undefined): null | string => {
    if (!text) return null;
    const kept = text.split(/\r?\n/).filter((line) => !isLyricsNoiseLine(line));
    if (!kept.some((line) => lineContent(line).length > 0)) return null;
    if (isPlaceholderLyrics(kept)) return null;
    return kept.join('\n').trim();
};
