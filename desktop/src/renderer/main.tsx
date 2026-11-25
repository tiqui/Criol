// Primeiro import: renomeia os dados salvos com o nome antigo antes das stores lerem o localStorage.
import { migrateLegacyDatabases } from './app/legacy-storage';

import './styles/global.css';
import './i18n';

import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './app/app';
import { bootstrap } from './app/bootstrap';

void migrateLegacyDatabases().finally(() => {
    bootstrap();

    createRoot(document.getElementById('root')!).render(
        <StrictMode>
            <App />
        </StrictMode>,
    );
});
