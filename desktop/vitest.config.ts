import { resolve } from 'node:path';
import { defineConfig } from 'vitest/config';

export default defineConfig({
    define: { __CRIOL_VERSION__: JSON.stringify('teste'), __CRIOL_WEB__: 'false' },
    resolve: { alias: { '@': resolve('src/renderer'), '@shared': resolve('src/shared') } },
    test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
