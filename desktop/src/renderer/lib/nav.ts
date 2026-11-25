/** Navegação fora de componentes (menus, atalhos). O App registra o navigate do router. */

type Navigate = (to: string) => void;
let navigateImpl: Navigate = (to) => {
    window.location.hash = to;
};

export const setNavigate = (fn: Navigate) => {
    navigateImpl = fn;
};

export const navigate = (to: string) => navigateImpl(to);

export const routes = {
    album: (id: string) => `/album/${encodeURIComponent(id)}`,
    artist: (id: string) => `/artist/${encodeURIComponent(id)}`,
    folder: (path: string) => `/folder?path=${encodeURIComponent(path)}`,
    genre: (id: string) => `/genre/${encodeURIComponent(id)}`,
    home: () => '/',
    library: (tab = 'songs') => `/library/${tab}`,
    mix: (id: string) => `/mix/${encodeURIComponent(id)}`,
    playlist: (id: string) => `/playlist/${encodeURIComponent(id)}`,
    profile: () => '/profile',
    recap: (month: string) => `/recap/${month}`,
    search: (q = '') => (q ? `/search?q=${encodeURIComponent(q)}` : '/search'),
    settings: (section = '') => (section ? `/settings/${section}` : '/settings'),
};
