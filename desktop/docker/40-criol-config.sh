#!/bin/sh
# Gera o config.js da versão web a partir das variáveis de ambiente do contêiner.
# Aceita também os nomes antigos (CRIFY_SERVER_* e SERVER_*).
set -eu

url="${CRIOL_SERVER_URL:-${CRIFY_SERVER_URL:-${SERVER_URL:-}}}"
type="${CRIOL_SERVER_TYPE:-${CRIFY_SERVER_TYPE:-${SERVER_TYPE:-}}}"
name="${CRIOL_SERVER_NAME:-${CRIFY_SERVER_NAME:-${SERVER_NAME:-}}}"
lock="${CRIOL_SERVER_LOCK:-${CRIFY_SERVER_LOCK:-${SERVER_LOCK:-false}}}"

case "$type" in
    navidrome | subsonic | Navidrome | Subsonic) type="subsonic" ;;
    jellyfin | Jellyfin) type="jellyfin" ;;
    *) type="" ;;
esac

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

if [ "$lock" = "true" ]; then lock_value=true; else lock_value=false; fi

cat > /usr/share/nginx/html/config.js <<CONFIG
window.CRIOL_CONFIG = {
    serverUrl: "$(json_escape "$url")",
    serverType: "$type",
    serverName: "$(json_escape "$name")",
    serverLock: $lock_value,
};
CONFIG
