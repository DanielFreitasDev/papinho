#!/bin/sh
# Backup da instância (BK-03): o banco (pg_dump em formato custom) e o diretório de dados do Papinho
# (arquivos enviados, chave de cifra e, se estiver lá dentro, a PKI da LAN) num único .tar.
#
# Uso: scripts/backup.sh [diretório-de-destino]
#
# Conexão pelas variáveis do PostgreSQL (PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE; padrão papinho em
# localhost). O pg_dump precisa ser da versão do servidor (18) ou mais nova; sem ele, use o do contêiner:
#   PAPINHO_PG_CONTAINER=papinho-postgres-1 scripts/backup.sh
# Diretório de dados: PAPINHO_DATA_DIR (padrão ~/.papinho). Uploads em andamento ficam de fora.
#
# O arquivo gerado contém a chave de cifra e a chave privada da CA: guarde-o com o mesmo cuidado (fica 0600).
set -eu

DEST=${1:-.}
DATA_DIR=${PAPINHO_DATA_DIR:-$HOME/.papinho}
export PGDATABASE=${PGDATABASE:-papinho} PGUSER=${PGUSER:-papinho}
STAMP=$(date -u +%Y%m%d-%H%M%S)
NAME="papinho-backup-$STAMP"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT INT TERM
umask 077

dump() {
  if [ -n "${PAPINHO_PG_CONTAINER:-}" ]; then
    docker exec -e PGPASSWORD="${PGPASSWORD:-}" "$PAPINHO_PG_CONTAINER" \
      pg_dump --format=custom --no-owner --username="$PGUSER" --dbname="$PGDATABASE"
  else
    pg_dump --format=custom --no-owner
  fi
}

echo "Banco $PGDATABASE..."
dump > "$WORK/database.dump"
[ -s "$WORK/database.dump" ] || { echo "pg_dump não gerou nada" >&2; exit 1; }

if [ -d "$DATA_DIR" ]; then
  echo "Arquivos de $DATA_DIR..."
  tar -C "$DATA_DIR" --exclude=./uploads -czf "$WORK/data.tar.gz" .
else
  echo "Aviso: $DATA_DIR não existe; o backup leva só o banco." >&2
fi

(cd "$WORK" && sha256sum database.dump $( [ -f data.tar.gz ] && echo data.tar.gz ) > SHA256SUMS)
printf 'papinho-backup 1\ncriado %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$WORK/MANIFEST"
mkdir -p "$DEST"
tar -C "$WORK" -cf "$DEST/$NAME.tar" .
chmod 600 "$DEST/$NAME.tar"
echo "Backup em $DEST/$NAME.tar"
