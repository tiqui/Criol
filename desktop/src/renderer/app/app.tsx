import { useEffect } from 'react';
import { createHashRouter, RouterProvider, useNavigate, useRouteError } from 'react-router';

import { MenuHost } from '@/components/menu';
import { ModalHost } from '@/components/modal';
import { Toaster } from '@/components/toast';
import { Button, Empty } from '@/components/ui';
import i18n from '@/i18n';
import { useMonthlyRecapGenerator } from '@/insights/monthly-recap';
import { AppShell } from '@/layout/app-shell';
import { setNavigate } from '@/lib/nav';
import { AlbumScreen, ArtistScreen, FolderScreen, GenreScreen, MixScreen, PlaylistScreen } from '@/screens/details';
import { HomeScreen } from '@/screens/home';
import { LibraryScreen } from '@/screens/library';
import { ProfileScreen } from '@/screens/profile';
import { RecapScreen } from '@/screens/recap';
import { SearchScreen } from '@/screens/search';
import { SettingsScreen } from '@/screens/settings';

function Root() {
    const navigate = useNavigate();
    useEffect(() => setNavigate((to) => navigate(to)), [navigate]);
    useMonthlyRecapGenerator();
    return (
        <>
            <AppShell />
            <MenuHost />
            <ModalHost />
            <Toaster />
        </>
    );
}

function ErrorScreen() {
    const error = useRouteError();
    return (
        <Empty
            action={<Button onClick={() => window.location.reload()}>{i18n.t('common.reload')}</Button>}
            icon="alert"
            text={error instanceof Error ? error.message : String(error)}
            title={i18n.t('common.error')}
        />
    );
}

const router = createHashRouter([
    {
        children: [
            { element: <HomeScreen />, index: true },
            { element: <SearchScreen />, path: 'search' },
            { element: <LibraryScreen />, path: 'library/:tab?' },
            { element: <AlbumScreen />, path: 'album/:id' },
            { element: <ArtistScreen />, path: 'artist/:id' },
            { element: <GenreScreen />, path: 'genre/:id' },
            { element: <FolderScreen />, path: 'folder' },
            { element: <PlaylistScreen />, path: 'playlist/:id' },
            { element: <MixScreen />, path: 'mix/:id' },
            { element: <ProfileScreen />, path: 'profile' },
            { element: <RecapScreen />, path: 'recap/:month' },
            { element: <SettingsScreen />, path: 'settings/:section?' },
        ],
        element: <Root />,
        errorElement: <ErrorScreen />,
        path: '/',
    },
]);

export function App() {
    return <RouterProvider router={router} />;
}
