# Criol para desktop e web

App Electron (Windows, Linux, macOS) e versão web do Criol, com a mesma interface. Toca músicas
de pastas do computador, de servidores Navidrome/Subsonic e de servidores Jellyfin. Para baixar
e usar, veja o [README principal](../README.md).

## Desenvolvimento

Precisa do Node.js 24 e do pnpm 11. Os comandos rodam dentro desta pasta (`desktop/`).

```bash
pnpm install
pnpm dev            # app desktop com recarga automática
pnpm dev:web        # versão web em http://localhost:5180
pnpm lint           # tipos + ESLint
pnpm test           # testes
pnpm build          # desktop e web
pnpm package        # instaladores do sistema atual (pasta release/)
```

A imagem Docker é gerada a partir desta pasta (`docker build -t criol .`).

## Estrutura

- `src/main`: processo principal do Electron (janela, leitura das pastas, capas e streaming
  dos arquivos locais).
- `src/renderer`: a interface (React), igual no desktop e na web.
  - `sources`: clientes de cada fonte (Subsonic, Jellyfin, pasta no desktop, pasta no navegador).
  - `library`: índice da biblioteca, favoritos e playlists.
  - `player`: fila, motor de áudio, rádio e Auto DJ.
  - `insights`: histórico, estatísticas, perfil, mixes e resumos.
- `src/shared`: código usado pelos dois lados (letras, texto, tipos).

## Onde ficam os dados

Tudo fica no aparelho: a biblioteca em cache, o histórico de escuta, o perfil e as mixes. Os
arquivos de música só são lidos, nunca alterados. Para servidores, o Criol guarda só um token de
acesso, não a senha. O histórico pode ser exportado e importado em **Configurações → Perfil e
histórico**; aceita arquivos do Criol (Android, desktop e web) e do Crify.

Ao abrir pela primeira vez, os dados do Crify (nome antigo desta versão) são trazidos sozinhos:
no desktop, a pasta `Crify` dos dados do app é copiada para `Criol` (`src/main/legacy-data.ts`);
no navegador e no desktop, as chaves `crify_*` e os bancos `crify-*` são renomeados para
`criol_*`/`criol-*` (`src/renderer/app/legacy-storage.ts`).
