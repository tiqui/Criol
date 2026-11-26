import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, type Plugin } from 'vite';

const rawVersion = (JSON.parse(readFileSync('package.json', 'utf8')) as { version: string }).version;
const version = JSON.stringify(rawVersion);

/**
 * Põe a versão no endereço do ícone e do manifesto (favicon.svg?v=2.1.1). O navegador guarda
 * o ícone da aba por muito tempo; com o endereço novo a cada versão, ele pega o ícone atual.
 */
const versionedIcons: Plugin = {
    name: 'criol-versioned-icons',
    transformIndexHtml: (html) =>
        html.replace(/href="\.\/(favicon\.svg|icon\.png|manifest\.webmanifest)"/g, (_, file: string) => `href="./${file}?v=${rawVersion}"`),
};

// Versão web (Docker): mesmo código da interface, sem o processo do Electron.
export default defineConfig({
    root: 'src/renderer',
    base: './',
    define: { __CRIOL_VERSION__: version, __CRIOL_WEB__: 'true' },
    plugins: [react(), versionedIcons],
    css: { modules: { localsConvention: 'camelCaseOnly' } },
    publicDir: resolve('src/renderer/public'),
    resolve: { alias: { '@': resolve('src/renderer'), '@shared': resolve('src/shared') } },
    build: { outDir: resolve('out/web'), emptyOutDir: true },
    server: { port: 5180 },
});
