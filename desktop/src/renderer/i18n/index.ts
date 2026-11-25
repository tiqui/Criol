import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import en from './locales/en.json';
import ptBR from './locales/pt-BR.json';

export const LANGUAGES = [
    { label: 'Português (Brasil)', value: 'pt-BR' },
    { label: 'English', value: 'en' },
] as const;

export const resolveLanguage = (setting: string) => {
    if (setting === 'pt-BR' || setting === 'en') return setting;
    const nav = (navigator.languages?.[0] || navigator.language || 'pt-BR').toLowerCase();
    return nav.startsWith('pt') ? 'pt-BR' : 'en';
};

void i18n.use(initReactI18next).init({
    fallbackLng: 'pt-BR',
    interpolation: { escapeValue: false },
    lng: 'pt-BR',
    resources: { en: { translation: en }, 'pt-BR': { translation: ptBR } },
    returnNull: false,
});

export default i18n;
