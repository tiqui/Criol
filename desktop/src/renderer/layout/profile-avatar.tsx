import { useProfile } from '@/insights/profile-store';
import { useActiveConfig } from '@/sources/store';

/** Nome do perfil, ou o usuário do servidor ativo. */
export const useProfileName = () => {
    const displayName = useProfile((s) => s.displayName);
    const config = useActiveConfig();
    return displayName.trim() || config?.username || '';
};

export function ProfileAvatar({ size = 32 }: { size?: number }) {
    const avatar = useProfile((s) => s.avatar);
    const accent = useProfile((s) => s.accent);
    const name = useProfileName();
    const style = { borderRadius: '50%', display: 'block', flexShrink: 0, height: size, width: size } as const;
    if (avatar) return <img alt="" src={avatar} style={{ ...style, objectFit: 'cover' }} />;
    return (
        <span
            style={{
                ...style,
                alignItems: 'center',
                background: accent,
                color: '#fff',
                display: 'grid',
                fontSize: size * 0.42,
                fontWeight: 700,
                placeItems: 'center',
            }}
        >
            {(name.trim()[0] || 'C').toUpperCase()}
        </span>
    );
}
