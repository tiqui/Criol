# Criol

Player de música para Android que toca arquivos de pastas do próprio celular.

## Como compilar

1. No Android Studio: **File > Open** e escolha esta pasta (`Criol`).
2. Espere o Gradle sincronizar.
3. Conecte o celular (com depuração USB ligada) e clique em **Run**. Ou gere o APK em
   **Build > Generate App Bundles or APKs > Generate APKs**.

Use a variante **release** para o uso diário (Build Variants > app > release). Ela passa pelo R8,
fica com ~5 MB e roda bem mais fluida que a debug. Ela é assinada com a chave de debug, então
instala direto no celular.

## Primeiro uso

Abra o app e toque em **Adicionar pasta**, depois escolha a pasta onde estão suas músicas
(por exemplo, `Music`). Dá para adicionar mais de uma em **Configurações**.

## O que tem

- Início com carrosséis: tocados recentemente, adicionados recentemente, mais tocados, explorar,
  gêneros, álbuns favoritos e lançamentos.
- Biblioteca: músicas, álbuns (grade ou lista), artistas, gêneros, pastas e favoritos, com
  ordenação e rolagem rápida.
- Busca que ignora acentos.
- Páginas de álbum, artista, gênero e pasta, com cabeçalho na cor da capa.
- Player em tela cheia com fundo desfocado da capa, deslizar a capa para trocar de faixa,
  arrastar para baixo para fechar, botões de voltar/avançar alguns segundos, aleatório e repetir.
- Fila com reordenação por arraste. O aleatório é do próprio player: a música atual continua tocando,
  a fila mostra a ordem sorteada, "Tocar a seguir" entra logo depois da atual e desligar o
  aleatório volta à ordem original.
- Letras sincronizadas (`.lrc` ao lado da música, numa pasta de letras escolhida em Configurações
  — casadas pelo nome do arquivo, "Artista - Título" ou título —, ou embutidas nas tags de
  MP3, FLAC, OGG/Opus e M4A). Toque numa linha para pular até ela.
- Favoritos, avaliação de 0 a 5 estrelas, contagem de reproduções.
- Playlists (criar, renomear, excluir, reordenar) e playlists automáticas.
- Timer de sono, velocidade de reprodução, notificação com controles, tela de bloqueio,
  fones Bluetooth e pausa ao desconectar o fone.
- A fila e a posição são restauradas ao abrir o app.
- Auto DJ: quando a fila está acabando, adiciona músicas (ou álbuns) parecidos.
- **Rádio da música** (nos 3 pontinhos de qualquer música ou no menu do player): toca a música e
  uma fila de parecidas que nunca acaba. Usa gênero, artista, época, artistas que aparecem juntos
  nas faixas e o que você costuma ouvir na mesma sessão; evita repetir artista em sequência e
  versões ao vivo/remaster da mesma música. Dá para parar o rádio pela fila.
- Widgets 3×1, 3×3 e 4×4 com as cores do tema do app; o 3×3 e o 4×4 têm a capa desfocada de fundo, barra de progresso e tempos. Funcionam mesmo com o app fechado.
- 16 temas e cor de destaque personalizável.
- **Feito para você** (no Início): a **daylist**, que muda a cada período do dia (manhã, tarde, noite,
  madrugada) e leva no título os seus gêneros, o dia da semana e o período; mais **No Repeat**,
  **De volta pro repeat**, **Mix da Manhã/Tarde/Noite**, **Radar de descobertas** e mixes por gênero.
  Sem histórico ainda, elas usam as contagens de reprodução e a biblioteca. Dá para gerar de novo
  e salvar qualquer mix como playlist.
- **Perfil** (toque no avatar no Início): nome, bio, foto e cor; minutos ouvidos, reproduções,
  sequência de dias; DNA musical (Coruja, Explorador, Fiel...); relógio musical por hora e dia da
  semana; top artistas, músicas e gêneros (4 semanas, 6 meses ou sempre); conquistas (dá para fixar
  uma no perfil); ouvidas recentemente; exportar, importar e apagar o histórico.
- **Resumo mensal**: quando o mês fecha, o Criol gera um resumo (tempo ouvindo, artista e música do
  mês, minutos por dia, tops) e avisa. O mês atual aparece como "Até agora".
- O registro do histórico pode ser desligado em Configurações > Perfil e histórico.

## Leitura rápida

Com a permissão de ler músicas (o app pede ao escolher a primeira pasta; dá para ligar em
Configurações > Biblioteca > Leitura rápida), o Criol continua usando só as pastas escolhidas,
mas:
- pega as tags do índice de músicas do Android numa consulta só, em vez de abrir arquivo por
  arquivo (só para músicas novas ou alteradas; se o índice não bater com o arquivo, lê o arquivo);
- não varre as pastas ao abrir o app quando nada mudou no aparelho desde a última leitura
  (Android 11+, pastas do armazenamento do aparelho ou do cartão);
- percebe músicas novas sozinho enquanto está aberto.

Sem a permissão, tudo funciona como antes.

## Onde ficam os dados

Tudo fica no armazenamento interno do app:
- `criol.db` (SQLite): favoritos, avaliações, reproduções, playlists e histórico de escuta. Cada
  mudança grava só a linha que mudou;
- `library.json` (cache das tags), `queue.json` (fila; a posição fica nas preferências do app),
  a pasta `art/` (capas), `profile.json` (perfil e resumos mensais), `mixes.json` (mixes geradas)
  e a foto do perfil.

Ao atualizar de uma versão antiga, `userdata.json` e os arquivos de `listening/` são importados
para o banco uma vez; o original fica como `userdata.json.migrated` e em `listening/backup/`.
Seus arquivos de música só são lidos, nunca alterados.
