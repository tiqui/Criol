import { afterEach, describe, expect, it, vi } from 'vitest';

import { currentBase, ensureBase, failover, forgetBase, selectBase, serverUrls } from './endpoint';
import type { SourceConfig } from './types';

const LOCAL = 'http://192.168.0.10:4533';
const TAILSCALE = 'http://100.64.0.1:4533';

const config = (id: string, extra: Partial<SourceConfig> = {}): SourceConfig => ({ createdAt: 0, fallbackUrl: TAILSCALE, id, name: 'Navidrome', type: 'subsonic', url: LOCAL, ...extra });

/** Simula quais endereços estão no ar. */
const network = (online: string[]) => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (online.some((base) => url.startsWith(base))) return new Response('{}', { status: 200 });
        throw new TypeError('Failed to fetch');
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
};

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('endereço redundante do servidor', () => {
    it('lista os endereços sem repetir', () => {
        expect(serverUrls(config('a'))).toEqual([LOCAL, TAILSCALE]);
        expect(serverUrls(config('b', { fallbackUrl: LOCAL }))).toEqual([LOCAL]);
        expect(serverUrls(config('c', { fallbackUrl: undefined }))).toEqual([LOCAL]);
    });

    it('prefere o principal quando os dois respondem', async () => {
        network([LOCAL, TAILSCALE]);
        expect(await selectBase(config('s1'))).toBe(LOCAL);
    });

    it('usa o alternativo quando o principal não responde', async () => {
        network([TAILSCALE]);
        const c = config('s2');
        expect(await selectBase(c)).toBe(TAILSCALE);
        expect(currentBase(c)).toBe(TAILSCALE);
    });

    it('mantém o endereço atual quando nenhum responde', async () => {
        const c = config('s3');
        network([TAILSCALE]);
        await selectBase(c);
        network([]);
        expect(await selectBase(c)).toBe(TAILSCALE);
    });

    it('volta ao principal quando ele responde de novo', async () => {
        const c = config('s4');
        network([TAILSCALE]);
        await selectBase(c);
        network([LOCAL, TAILSCALE]);
        expect(await failover(c)).toBe(true);
        expect(currentBase(c)).toBe(LOCAL);
    });

    it('depois de uma falha, só pede para repetir se o endereço mudou', async () => {
        const c = config('s5');
        network([LOCAL]);
        await selectBase(c);
        expect(await failover(c)).toBe(false);
        network([TAILSCALE]);
        expect(await failover(c)).toBe(true);
    });

    it('não testa de novo um endereço conferido há pouco', async () => {
        const c = config('s6');
        const fetchMock = network([LOCAL, TAILSCALE]);
        await selectBase(c);
        const calls = fetchMock.mock.calls.length;
        await ensureBase(c);
        expect(fetchMock.mock.calls.length).toBe(calls);
    });

    it('sem endereço alternativo, nem testa', async () => {
        const fetchMock = network([]);
        expect(await selectBase(config('s7', { fallbackUrl: undefined }))).toBe(LOCAL);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('esquecer volta ao principal', async () => {
        const c = config('s8');
        network([TAILSCALE]);
        await selectBase(c);
        forgetBase(c.id);
        expect(currentBase(c)).toBe(LOCAL);
    });
});
