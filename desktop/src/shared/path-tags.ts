/**
 * Quando o arquivo não tem tags, deduz pelo caminho no formato comum
 * "Artista/Álbum/01 - Título.ext": título e número pela nome do arquivo, álbum pela pasta e
 * artista pela pasta de cima.
 */
export function tagsFromPath(rel: string) {
    const parts = rel.split('/').filter(Boolean);
    const file = parts[parts.length - 1] || rel;
    const stem = file.includes('.') ? file.slice(0, file.lastIndexOf('.')) : file;
    const match = stem.match(/^(?:(\d{1,2})[-.])?(\d{1,3})\s*[-._)\s]\s*(.+)$/);
    const track = match ? Number(match[2]) : 0;
    const disc = match?.[1] ? Number(match[1]) : 0;
    const title = (match ? match[3] : stem).replace(/_/g, ' ').trim() || stem;
    const album = parts.length >= 2 ? parts[parts.length - 2] : '';
    const artist = parts.length >= 3 ? parts[parts.length - 3] : '';
    return { album, artist, disc, title, track };
}
