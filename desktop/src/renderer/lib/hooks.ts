import { useEffect, useState } from 'react';

export const useMediaQuery = (query: string) => {
    const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
    useEffect(() => {
        const mql = window.matchMedia(query);
        const onChange = () => setMatches(mql.matches);
        mql.addEventListener('change', onChange);
        onChange();
        return () => mql.removeEventListener('change', onChange);
    }, [query]);
    return matches;
};

export const useIsMobile = () => useMediaQuery('(max-width: 768px)');

export const isDesktopApp = () => Boolean(window.criolDesktop);
export const desktopPlatform = () => window.criolDesktop?.platform || 'web';
