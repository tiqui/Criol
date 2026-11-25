# Changelog

As mudanças de cada versão do Criol para desktop e web.

## [2.0.0] - 2026-11-25

Primeira versão do Criol para desktop e web, com a mesma biblioteca, o mesmo perfil e as mesmas
mixes do app Android. Substitui o Crify.

### Adicionado

- Fontes: pastas do computador (desktop e navegador), Navidrome/Subsonic e Jellyfin, com troca
  rápida pela barra lateral. A biblioteca fica em cache no aparelho e abre na hora.
- Pasta local lê tags e capas dos arquivos; sem tags, usa o caminho "Artista/Álbum/01 - Título".
- Biblioteca com músicas, álbuns, artistas, gêneros, pastas, playlists e favoritos, ordenação
  e listas virtualizadas para bibliotecas grandes.
- Busca sem acentos com melhor resultado.
- Player com fila reordenável, aleatório, repetir, velocidade, timer de sono, teclas de mídia,
  controles do sistema e retomada da fila por fonte.
- Letras sincronizadas do arquivo, da tag, do servidor, de pastas de letras e do LRCLIB.
- Feito para você: daylist, mixes por período do dia, No Repeat, De volta pro repeat, Radar de
  descobertas e mixes por gênero.
- Rádio da música e Auto DJ.
- Perfil com estatísticas, DNA musical, relógio musical, tops, conquistas e resumo mensal.
- 16 temas, cor de destaque e cabeçalhos na cor da capa.
- Layout de celular com navegação inferior, mini player e player em tela cheia com gestos.
- Português e inglês.

### Vindo do Crify

- Na primeira vez que abre, traz sozinho os servidores, pastas, fila, histórico, perfil,
  playlists e configurações do Crify.
- A imagem Docker agora é `ghcr.io/tiqui/criol`; as variáveis `CRIFY_SERVER_*` continuam valendo.
