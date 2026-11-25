import react from '@vitejs/plugin-react';
import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const shared = { '@shared': resolve('src/shared') };
const version = JSON.stringify((JSON.parse(readFileSync('package.json', 'utf8')) as { version: string }).version);

export default defineConfig({
    main: {
        plugins: [externalizeDepsPlugin()],
        resolve: { alias: shared },
    },
    preload: {
        plugins: [externalizeDepsPlugin()],
        build: { rollupOptions: { output: { format: 'cjs', entryFileNames: '[name].cjs' } } },
        resolve: { alias: shared },
    },
    renderer: {
        root: 'src/renderer',
        define: { __CRIOL_VERSION__: version, __CRIOL_WEB__: 'false' },
        plugins: [react()],
        resolve: { alias: { ...shared, '@': resolve('src/renderer') } },
        css: { modules: { localsConvention: 'camelCaseOnly' } },
        build: { rollupOptions: { input: resolve('src/renderer/index.html') } },
    },
});
