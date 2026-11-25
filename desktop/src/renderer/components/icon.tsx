import type { CSSProperties } from 'react';
import type { IconType } from 'react-icons';
import {
    LuArrowDownUp, LuArrowLeft, LuAudioLines, LuCalendar, LuChartColumn, LuCheck, LuChevronDown, LuChevronLeft,
    LuChevronRight, LuChevronUp, LuCircleAlert, LuCirclePlay, LuClock, LuCompass, LuDisc3, LuDownload, LuEllipsis,
    LuFlame, LuFolder, LuFolderOpen, LuGauge, LuGlobe, LuGripVertical, LuHardDrive, LuHeart, LuHistory, LuHouse,
    LuImage, LuInfinity, LuInfo, LuLayoutGrid, LuLibrary, LuList, LuListEnd, LuListMusic, LuListPlus, LuListX,
    LuLoaderCircle, LuMaximize2, LuMenu, LuMicVocal, LuMinimize2, LuMinus, LuMoon, LuMoonStar, LuMusic, LuPalette,
    LuPanelLeft, LuPanelRight, LuPause, LuPencil, LuPin, LuPlay, LuPlus, LuRadio, LuRefreshCw, LuRepeat, LuRepeat1, LuRotateCcw, LuRotateCw,
    LuSearch, LuServer, LuSettings, LuShieldCheck, LuShuffle, LuSkipBack, LuSkipForward, LuSlidersHorizontal,
    LuSparkles, LuSquare, LuStar, LuSun, LuSunrise, LuTags, LuTimer, LuTrash2, LuTrophy, LuUpload, LuUser, LuUserPen,
    LuUsers, LuVolume1, LuVolume2, LuVolumeX, LuWandSparkles, LuX,
} from 'react-icons/lu';

const ICONS = {
    add: LuPlus,
    addToPlaylist: LuListPlus,
    album: LuDisc3,
    alert: LuCircleAlert,
    artist: LuUsers,
    audio: LuAudioLines,
    back: LuArrowLeft,
    calendar: LuCalendar,
    chart: LuChartColumn,
    check: LuCheck,
    chevronDown: LuChevronDown,
    chevronLeft: LuChevronLeft,
    chevronRight: LuChevronRight,
    chevronUp: LuChevronUp,
    clear: LuListX,
    clock: LuClock,
    close: LuX,
    compass: LuCompass,
    download: LuDownload,
    drag: LuGripVertical,
    edit: LuPencil,
    editProfile: LuUserPen,
    favorite: LuHeart,
    flame: LuFlame,
    folder: LuFolder,
    folderOpen: LuFolderOpen,
    genre: LuTags,
    grid: LuLayoutGrid,
    history: LuHistory,
    home: LuHouse,
    image: LuImage,
    infinity: LuInfinity,
    info: LuInfo,
    library: LuLibrary,
    list: LuList,
    loader: LuLoaderCircle,
    lyrics: LuMicVocal,
    maximize: LuMaximize2,
    mediaNext: LuSkipForward,
    mediaPause: LuPause,
    mediaPlay: LuPlay,
    mediaPrevious: LuSkipBack,
    menu: LuMenu,
    minimize: LuMinimize2,
    minus: LuMinus,
    moon: LuMoon,
    moonStar: LuMoonStar,
    more: LuEllipsis,
    music: LuMusic,
    palette: LuPalette,
    panelLeft: LuPanelLeft,
    panelRight: LuPanelRight,
    pin: LuPin,
    playCircle: LuCirclePlay,
    playlist: LuListMusic,
    queue: LuListEnd,
    radio: LuRadio,
    refresh: LuRefreshCw,
    repeat: LuRepeat,
    repeatOne: LuRepeat1,
    rewind: LuRotateCcw,
    forward: LuRotateCw,
    search: LuSearch,
    server: LuServer,
    settings: LuSettings,
    shield: LuShieldCheck,
    shuffle: LuShuffle,
    sliders: LuSlidersHorizontal,
    sort: LuArrowDownUp,
    sparkles: LuSparkles,
    speed: LuGauge,
    square: LuSquare,
    star: LuStar,
    sun: LuSun,
    sunrise: LuSunrise,
    timer: LuTimer,
    trash: LuTrash2,
    trophy: LuTrophy,
    upload: LuUpload,
    user: LuUser,
    volume: LuVolume2,
    volumeLow: LuVolume1,
    volumeMute: LuVolumeX,
    wand: LuWandSparkles,
    web: LuGlobe,
    disk: LuHardDrive,
} satisfies Record<string, IconType>;

export type IconName = keyof typeof ICONS;

interface IconProps {
    name: IconName;
    size?: number;
    className?: string;
    style?: CSSProperties;
    /** Preenche o ícone (coração/estrela ativos). */
    filled?: boolean;
    strokeWidth?: number;
}

export function Icon({ className, filled, name, size = 20, strokeWidth = 2, style }: IconProps) {
    const Component = ICONS[name];
    return (
        <Component
            aria-hidden
            className={className}
            fill={filled ? 'currentColor' : 'none'}
            size={size}
            strokeWidth={strokeWidth}
            style={{ flexShrink: 0, ...style }}
        />
    );
}
