#!/bin/sh
# Restaura um backup feito pelo scripts/backup.sh (BK-03) num banco vazio e num diretório de dados vazio.
#
# Uso: scripts/restore.sh papinho-backup-AAAAMMDD-HHMMSS.tar
#
# Pare o Papinho antes. O banco (PGDATABASE, padrão papinho) precisa existir e estar vazio: a restauração recusa
# um banco com tabelas para nunca misturar duas instâncias. Mesmo esquema de conexão e de PAPINHO_PG_CONTAINER
# do backup.sh. Os arquivos vão para PAPINHO_DATA_DIR (padrão ~/.papinho), que não pode ter files/ com conteúdo.
set -eu

ARCHIVE=${1:?informe o arquivo .tar do backup}
DATA_DIR=${PAPINHO_DATA_DIR:-$HOME/.papinho}
export PGDATABASE=${PGDATABASE:-papinho} PGUSER=${PGUSER:-papinho}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT INT TERM
umask 077

tar -C "$WORK" -xf "$ARCHIVE"
grep -q '^papinho-backup 1$' "$WORK/MANIFEST" || { echo "Não é um backup do Papinho" >&2; exit 1; }
(cd "$WORK" && sha256sum -c --quiet SHA256SUMS) || { echo "Backup corrompido (SHA-256 não confere)" >&2; exit 1; }

sql() {
  if [ -n "${PAPINHO_PG_CONTAINER:-}" ]; then
    docker exec -i -e PGPASSWORD="${PGPASSWORD:-}" "$PAPINHO_PG_CONTAINER" \
      psql -qAt --username="$PGUSER" --dbname="$PGDATABASE" -c "$1"
  else
    psql -qAt -c "$1"
  fi
}

restore() {
  if [ -n "${PAPINHO_PG_CONTAINER:-}" ]; then
    docker exec -i -e PGPASSWORD="${PGPASSWORD:-}" "$PAPINHO_PG_CONTAINER" \
      pg_restore --no-owner --exit-on-error --username="$PGUSER" --dbname="$PGDATABASE"
  else
    pg_restore --no-owner --exit-on-error --dbname="$PGDATABASE"
  fi
}

TABLES=$(sql "select count(*) from information_schema.tables where table_schema = 'public'")
if [ "$TABLES" != "0" ]; then
  echo "O banco $PGDATABASE não está vazio ($TABLES tabelas): crie um banco novo para restaurar." >&2
  exit 1
fi
if [ -d "$DATA_DIR/files" ] && [ -n "$(ls -A "$DATA_DIR/files" 2>/dev/null)" ]; then
  echo "$DATA_DIR/files já tem arquivos: restaure num diretório de dados vazio." >&2
  exit 1
fi

echo "Banco $PGDATABASE..."
restore < "$WORK/database.dump"
if [ -f "$WORK/data.tar.gz" ]; then
  echo "Arquivos em $DATA_DIR..."
  mkdir -p "$DATA_DIR"
  tar -C "$DATA_DIR" -xzf "$WORK/data.tar.gz"
fi
echo "Restaurado. Suba o Papinho com o mesmo PAPINHO_DATA_DIR (ou PAPINHO_CRYPTO_KEY) do backup."
