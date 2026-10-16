#!/bin/sh
# Verifica backup e restauração de ponta a ponta (BK-03): sobe uma instância com dados (conta, mensagem,
# foto de perfil e chave VAPID cifrada), faz o backup, restaura num PostgreSQL novo e num diretório vazio,
# sobe outra instância e confere que tudo voltou (inclusive o que depende da chave de cifra).
#
# Pré-requisitos: Docker, curl, jq e o JAR construído (./mvnw -B install -DskipTests). Usa as portas 8090,
# 5440 e 5441. JAVA (padrão java) precisa ser o JDK 25.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JAR=${JAR:-$ROOT/backend/target/papinho.jar}
JAVA=${JAVA:-java}
IMAGE=${PG_IMAGE:-postgres:18-alpine}
WORK=$(mktemp -d)
PID=""
cleanup() {
  [ -n "$PID" ] && kill "$PID" 2>/dev/null || true
  docker rm -f papinho-backup-a papinho-backup-b >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT INT TERM

database() {
  docker run -d --name "$1" -e POSTGRES_DB=papinho -e POSTGRES_USER=papinho -e POSTGRES_PASSWORD=papinho \
    -p "127.0.0.1:$2:5432" "$IMAGE" >/dev/null
  until docker exec "$1" pg_isready -U papinho -d papinho >/dev/null 2>&1; do sleep 1; done
  sleep 2
}

start() {
  DATABASE_URL="jdbc:postgresql://localhost:$1/papinho" DATABASE_USERNAME=papinho DATABASE_PASSWORD=papinho \
    PAPINHO_PORT=8090 PAPINHO_DATA_DIR="$2" "$JAVA" -jar "$JAR" > "$WORK/app-$1.log" 2>&1 &
  PID=$!
  for _ in $(seq 1 120); do
    curl -fs --noproxy '*' http://localhost:8090/actuator/health 2>/dev/null | grep -q UP && return 0
    sleep 1
  done
  tail -40 "$WORK/app-$1.log"
  exit 1
}

stop() {
  kill "$PID"
  wait "$PID" 2>/dev/null || true
  PID=""
}

api() {
  method=$1
  path=$2
  shift 2
  token=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$WORK/cookies")
  curl -fsS --noproxy '*' -b "$WORK/cookies" -c "$WORK/cookies" -X "$method" -H "X-XSRF-TOKEN: $token" \
    "http://localhost:8090$path" "$@"
}

echo "== Instância A com dados"
database papinho-backup-a 5440
start 5440 "$WORK/data-a"
curl -fsS --noproxy '*' -c "$WORK/cookies" http://localhost:8090/api/v1/instance >/dev/null
api POST /api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"guarda","displayName":"Guarda Backup","password":"senha muito segura 123"}' >/dev/null
SAVED=$(api POST /api/v1/conversations/saved | jq -r .id)
api POST "/api/v1/conversations/$SAVED/messages" -H 'Content-Type: application/json' \
  -d '{"clientMessageId":"0198f0a0-0000-7000-8000-000000000001","body":"Mensagem antes do backup"}' >/dev/null
api PUT /api/v1/profile/photo -F "photo=@$ROOT/frontend/public/icons/icon-512.png;type=image/png" >/dev/null
USER=$(api GET /api/v1/me | jq -r .account.id)
api GET "/api/v1/users/$USER/photo" -o "$WORK/photo-a"
VAPID_A=$(api GET /api/v1/notifications/push | jq -r .publicKey)
stop

echo "== Backup"
PGPASSWORD=papinho PAPINHO_PG_CONTAINER=papinho-backup-a PAPINHO_DATA_DIR="$WORK/data-a" \
  "$ROOT/scripts/backup.sh" "$WORK/backups"
ARCHIVE=$(ls "$WORK"/backups/papinho-backup-*.tar)
docker rm -f papinho-backup-a >/dev/null
rm -rf "$WORK/data-a"

echo "== Restauração na instância B (banco e diretório novos)"
database papinho-backup-b 5441
PGPASSWORD=papinho PAPINHO_PG_CONTAINER=papinho-backup-b PAPINHO_DATA_DIR="$WORK/data-b" \
  "$ROOT/scripts/restore.sh" "$ARCHIVE"
if PGPASSWORD=papinho PAPINHO_PG_CONTAINER=papinho-backup-b PAPINHO_DATA_DIR="$WORK/data-b" \
  "$ROOT/scripts/restore.sh" "$ARCHIVE" 2>/dev/null; then
  echo "FALHOU: restaurou por cima de um banco com dados" >&2
  exit 1
fi
start 5441 "$WORK/data-b"
rm -f "$WORK/cookies"
curl -fsS --noproxy '*' -c "$WORK/cookies" http://localhost:8090/api/v1/instance >/dev/null
api POST /api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"guarda","password":"senha muito segura 123"}' >/dev/null
api GET "/api/v1/conversations/$SAVED/messages" | jq -e '.messages[] | select(.body == "Mensagem antes do backup")' \
  >/dev/null
api GET "/api/v1/users/$USER/photo" -o "$WORK/photo-b"
cmp -s "$WORK/photo-a" "$WORK/photo-b"
VAPID_B=$(api GET /api/v1/notifications/push | jq -r .publicKey)
[ "$VAPID_A" = "$VAPID_B" ]
stop
echo "OK: login, mensagem, foto (mesmos bytes) e chave VAPID cifrada voltaram depois da restauração"
