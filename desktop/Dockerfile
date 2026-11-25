# Criol (versão web) — imagem com o app compilado servido pelo nginx.

FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@11.5.2 --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# A versão web não precisa do Electron.
ENV ELECTRON_SKIP_BINARY_DOWNLOAD=1
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build:web

FROM nginxinc/nginx-unprivileged:alpine-slim
LABEL org.opencontainers.image.title="Criol" \
      org.opencontainers.image.description="Player de música para Navidrome/Subsonic e Jellyfin" \
      org.opencontainers.image.source="https://github.com/tiqui/Criol"

COPY --chown=nginx:nginx --from=build /app/out/web /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 docker/40-criol-config.sh /docker-entrypoint.d/40-criol-config.sh

# Servidor pré-configurado (opcional).
ENV CRIOL_SERVER_URL="" CRIOL_SERVER_TYPE="" CRIOL_SERVER_NAME="" CRIOL_SERVER_LOCK="false"

EXPOSE 9180
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:9180/ || exit 1
