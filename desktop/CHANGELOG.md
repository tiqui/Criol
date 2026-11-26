# Changelog

As mudanças de cada versão do Criol para desktop e web.

## [2.1.2] - 2026-11-26

### Adicionado

- Tema que segue o dia (Configurações → Aparência → Seguir o dia): a interface fica clara de
  manhã, vai escurecendo durante a tarde e fica escura à noite, clareando de novo ao amanhecer.
  O tema escolhido é uma das pontas (um tema escuro vira o da noite; um claro, o do dia). A conta
  é a mesma do Criol Android, então as duas versões mudam juntas.

### Corrigido

- Menus e submenus saíam da janela: agora viram para o outro lado ou sobem quando não cabem
  (ex.: "Adicionar à playlist" perto da borda direita, "Velocidade" e "Timer de sono" perto de
  baixo). Listas longas rolam dentro do menu.
- A aba do navegador continuava com o ícone antigo: o ícone agora é carregado com a versão no
  endereço e o servidor do Docker pede para o navegador sempre conferir.

## [2.1.1] - 2026-11-25

### Mudado

- Ícone novo, igual ao do Criol Android (app, instaladores, aba do navegador e versão web
  instalada).
- Gêneros quase sempre têm imagem: o card usa a capa de um dos artistas que mais aparecem no
  gênero e, se ela não carregar, tenta a de outro.

### Corrigido

- Menu da música no player em tela cheia: as estrelas e o "Favoritar" mudam na hora, sem fechar o
  menu; "Ir para o álbum/artista" sai da tela cheia; "Tocar" não aparece para a música que já
  está tocando.
- Vírgula solta entre os gêneros no rodapé da página do álbum.

## [2.1.0] - 2026-11-25

### Adicionado

- Endereço alternativo do servidor (Navidrome/Subsonic e Jellyfin): quando o principal não
  responde, o Criol passa sozinho para o alternativo e volta ao principal quando ele responde de
  novo. Dá para editar os dois em Configurações → Fontes. No Docker:
  `CRIOL_SERVER_FALLBACK_URL`.
- Botão do Auto DJ na barra do player e no player em tela cheia.

### Mudado

- Capas do "Feito para você" iguais às do Criol Android (orbe da daylist, ícones por período,
  mosaico nas mixes de gênero).

### Corrigido

- Capa da música às vezes não aparecia na barra do player.
- Botões ligados (letra, fila) não se destacavam no player em tela cheia.
- Pasta local: álbum dividido em dois quando só algumas faixas tinham "artista do álbum".
- Pasta local: gêneros como ", Rap/Hip Hop" quando a tag trazia o separador grudado.
- Mixes antigas continuavam com gêneros ou músicas que saíram da biblioteca.
- Rádio: o mesmo artista podia tocar duas vezes seguidas no fim da fila.

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
