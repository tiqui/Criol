import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';

// Versão web (Docker): mesmo código da interface, sem o processo do Electron.
export default defineConfig({
    root: 'src/renderer',
    base: './',
    define: { __CRIOL_WEB__: 'true' },
    plugins: [react()],
    css: { modules: { localsConvention: 'camelCaseOnly' } },
    publicDir: resolve('src/renderer/public'),
    resolve: { alias: { '@': resolve('src/renderer'), '@shared': resolve('src/shared') } },
    build: { outDir: resolve('out/web'), emptyOutDir: true },
    server: { port: 5180 },
});
