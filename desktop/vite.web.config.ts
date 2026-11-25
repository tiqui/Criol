import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';

const version = JSON.stringify((JSON.parse(readFileSync('package.json', 'utf8')) as { version: string }).version);

// Versão web (Docker): mesmo código da interface, sem o processo do Electron.
export default defineConfig({
    root: 'src/renderer',
    base: './',
    define: { __CRIOL_VERSION__: version, __CRIOL_WEB__: 'true' },
    plugins: [react()],
    css: { modules: { localsConvention: 'camelCaseOnly' } },
    publicDir: resolve('src/renderer/public'),
    resolve: { alias: { '@': resolve('src/renderer'), '@shared': resolve('src/shared') } },
    build: { outDir: resolve('out/web'), emptyOutDir: true },
    server: { port: 5180 },
});
