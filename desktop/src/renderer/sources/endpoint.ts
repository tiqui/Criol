import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import type { SourceConfig } from './types';

/**
 * Endereço redundante do servidor (como no Immich): cada servidor pode ter um endereço principal
 * e um alternativo (por exemplo, o IP da rede local e o do Tailscale). O app testa os dois com um
 * tempo curto, usa o principal quando ele responde e passa para o alternativo quando não.
 *
 * O endereço em uso fica guardado por fonte, então o app já abre usando o último que funcionou.
 */

const PROBE_TIMEOUT_MS = 3500;
/** Depois de testado, o endereço vale por este tempo antes de testar de novo (e voltar ao principal). */
const FRESH_MS = 60_000;

interface EndpointsState {
    /** Endereço em uso de cada fonte (id → url). */
    bases: Record<string, string>;
}

export const useEndpoints = create<EndpointsState>()(persist(() => ({ bases: {} }), { name: 'criol_endpoints', version: 1 }));

/** Endereços da fonte em ordem de preferência, sem repetidos. */
export const serverUrls = (config: SourceConfig) => [...new Set([config.url, config.fallbackUrl].filter((u): u is string => Boolean(u)))];

/** Endereço em uso agora (o principal se nada foi escolhido ainda). */
export const currentBase = (config: SourceConfig) => {
    const chosen = useEndpoints.getState().bases[config.id];
    return chosen && serverUrls(config).includes(chosen) ? chosen : config.url || '';
};

/** Endereço em uso, para componentes que montam links (capas) e precisam se atualizar na troca. */
export const useCurrentBase = (config: null | SourceConfig) =>
    useEndpoints((s) => {
        if (!config) return '';
        const chosen = s.bases[config.id];
        return chosen && serverUrls(config).includes(chosen) ? chosen : config.url || '';
    });

/** Responde em pouco tempo? Qualquer resposta HTTP do servidor certo conta como "no ar". */
export async function probeServer(type: SourceConfig['type'], base: string, timeoutMs = PROBE_TIMEOUT_MS) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const path = type === 'jellyfin' ? '/System/Info/Public' : '/rest/ping?f=json&v=1.16.1&c=Criol';
        const response = await fetch(`${base}${path}`, { cache: 'no-store', signal: controller.signal });
        return response.ok;
    } catch {
        return false;
    } finally {
        clearTimeout(timer);
    }
}

const checkedAt = new Map<string, number>();
const inflight = new Map<string, Promise<string>>();

const setBase = (config: SourceConfig, base: string) => {
    if (useEndpoints.getState().bases[config.id] === base) return;
    useEndpoints.setState((s) => ({ bases: { ...s.bases, [config.id]: base } }));
};

/**
 * Testa os endereços ao mesmo tempo e fica com o primeiro da ordem de preferência que respondeu.
 * Se nenhum responder, mantém o atual. Devolve o endereço escolhido.
 */
export function selectBase(config: SourceConfig): Promise<string> {
    const urls = serverUrls(config);
    if (urls.length < 2) {
        checkedAt.set(config.id, Date.now());
        return Promise.resolve(currentBase(config));
    }
    const running = inflight.get(config.id);
    if (running) return running;
    const task = (async () => {
        const results = await Promise.all(urls.map((url) => probeServer(config.type, url)));
        const winner = urls.find((_, i) => results[i]);
        checkedAt.set(config.id, Date.now());
        if (winner) setBase(config, winner);
        return currentBase(config);
    })().finally(() => inflight.delete(config.id));
    inflight.set(config.id, task);
    return task;
}

/** Garante um endereço testado há pouco (barato quando já está fresco). */
export async function ensureBase(config: SourceConfig) {
    if (serverUrls(config).length < 2) return currentBase(config);
    const last = checkedAt.get(config.id) || 0;
    if (Date.now() - last < FRESH_MS) return currentBase(config);
    return selectBase(config);
}

/**
 * Depois de uma falha de rede: testa de novo e diz se o endereço mudou (vale tentar de novo).
 */
export async function failover(config: SourceConfig) {
    if (serverUrls(config).length < 2) return false;
    const before = currentBase(config);
    checkedAt.delete(config.id);
    const after = await selectBase(config);
    return after !== before;
}

/** Esquece o endereço escolhido (quando a fonte é removida ou os endereços mudam). */
export function forgetBase(sourceId: string) {
    checkedAt.delete(sourceId);
    useEndpoints.setState((s) => {
        const bases = { ...s.bases };
        delete bases[sourceId];
        return { bases };
    });
}
