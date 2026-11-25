/**
 * Motor de áudio: dois elementos <audio> que se revezam. A próxima música é carregada no
 * elemento livre um pouco antes da atual acabar, então a troca é praticamente sem pausa.
 */

export interface EngineEvents {
    onTime: (positionMs: number, durationMs: number, bufferedMs: number) => void;
    onState: (state: 'loading' | 'paused' | 'playing') => void;
    onEnded: () => void;
    onError: (message: string) => void;
}

const createAudio = () => {
    const audio = new Audio();
    audio.preload = 'auto';
    return audio;
};

/** Volume perceptual: o controle linear vira uma curva quadrática. */
const curve = (v: number) => Math.max(0, Math.min(1, v)) ** 2;

class AudioEngine {
    private current = createAudio();
    private spare = createAudio();
    private spareKey: null | string = null;
    private events: EngineEvents | null = null;
    private volume = 1;
    private muted = false;
    private speed = 1;
    private loadId = 0;

    attach(events: EngineEvents) {
        this.events = events;
        for (const audio of [this.current, this.spare]) this.bind(audio);
    }

    private bind(audio: HTMLAudioElement) {
        const isCurrent = () => audio === this.current;
        const emitTime = () => {
            if (!isCurrent() || !this.events) return;
            const buffered = audio.buffered.length ? audio.buffered.end(audio.buffered.length - 1) : 0;
            this.events.onTime(audio.currentTime * 1000, (Number.isFinite(audio.duration) ? audio.duration : 0) * 1000, buffered * 1000);
        };
        audio.addEventListener('timeupdate', emitTime);
        audio.addEventListener('durationchange', emitTime);
        audio.addEventListener('progress', emitTime);
        audio.addEventListener('playing', () => isCurrent() && this.events?.onState('playing'));
        audio.addEventListener('pause', () => isCurrent() && !audio.ended && this.events?.onState('paused'));
        audio.addEventListener('waiting', () => isCurrent() && this.events?.onState('loading'));
        audio.addEventListener('ended', () => isCurrent() && this.events?.onEnded());
        audio.addEventListener('error', () => {
            if (!isCurrent() || !audio.src) return;
            const code = audio.error?.code;
            this.events?.onError(code === 4 ? 'format' : code === 2 ? 'network' : 'decode');
        });
    }

    private apply(audio: HTMLAudioElement) {
        audio.volume = this.muted ? 0 : curve(this.volume);
        audio.playbackRate = this.speed;
        audio.preservesPitch = true;
    }

    /** Carrega uma música (key = id único na fila) e começa a tocar se autoplay. */
    async load(key: string, url: string, autoplay: boolean, startMs = 0) {
        const id = ++this.loadId;
        const old = this.current;
        old.pause();
        if (this.spareKey === key && this.spare.src) {
            // Já estava pré-carregada: só troca os elementos e libera o antigo.
            this.current = this.spare;
            this.spare = old;
            old.removeAttribute('src');
            old.load();
        } else {
            this.clearPreload();
            old.src = url;
        }
        this.spareKey = null;
        const audio = this.current;
        this.apply(audio);
        if (startMs > 0) {
            const seek = () => {
                audio.currentTime = startMs / 1000;
            };
            if (audio.readyState >= 1) seek();
            else audio.addEventListener('loadedmetadata', seek, { once: true });
        } else if (audio.currentTime > 0) {
            audio.currentTime = 0;
        }
        this.events?.onState('loading');
        if (!autoplay) {
            this.events?.onState('paused');
            return;
        }
        try {
            await audio.play();
        } catch (error) {
            if (id !== this.loadId) return;
            if ((error as DOMException)?.name === 'NotAllowedError') this.events?.onState('paused');
            else if ((error as DOMException)?.name !== 'AbortError') this.events?.onError('decode');
        }
    }

    /** Pré-carrega a próxima música no elemento livre. */
    preload(key: string, url: string) {
        if (this.spareKey === key) return;
        this.spareKey = key;
        this.spare.src = url;
        this.apply(this.spare);
        this.spare.load();
    }

    clearPreload() {
        this.spareKey = null;
        if (this.spare.src) {
            this.spare.removeAttribute('src');
            this.spare.load();
        }
    }

    async play() {
        if (!this.current.src) return false;
        try {
            await this.current.play();
            return true;
        } catch {
            return false;
        }
    }

    pause() {
        this.current.pause();
    }

    stop() {
        this.current.pause();
        this.current.removeAttribute('src');
        this.current.load();
        this.clearPreload();
    }

    seek(ms: number) {
        if (this.current.src) this.current.currentTime = Math.max(0, ms / 1000);
    }

    get positionMs() {
        return this.current.currentTime * 1000;
    }

    get durationMs() {
        return Number.isFinite(this.current.duration) ? this.current.duration * 1000 : 0;
    }

    get hasSource() {
        return Boolean(this.current.src);
    }

    setVolume(volume: number, muted: boolean) {
        this.volume = volume;
        this.muted = muted;
        this.apply(this.current);
        this.apply(this.spare);
    }

    setSpeed(speed: number) {
        this.speed = speed;
        this.apply(this.current);
        this.apply(this.spare);
    }
}

export const engine = new AudioEngine();
