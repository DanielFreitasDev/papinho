#!/bin/sh
# Assunto do commit com no máximo 72 caracteres Unicode (COMMITS.md, achado A93 da auditoria de 2026-10-17).
#   scripts/check-commit-subject.sh <arquivo-da-mensagem>   hook commit-msg (.githooks/commit-msg)
#   scripts/check-commit-subject.sh --range <base>..<topo>  CI, em todos os commits do intervalo
set -eu
LIMIT=72

length() {
  printf '%s' "$1" | LC_ALL=C.UTF-8 wc -m | tr -d ' '
}

check() {
  size=$(length "$1")
  if [ "$size" -gt "$LIMIT" ]; then
    printf 'Assunto com %s caracteres (máximo %s): %s\n' "$size" "$LIMIT" "$1" >&2
    return 1
  fi
}

if [ "${1:-}" = "--range" ]; then
  failed=0
  subjects=$(git log --format=%s "$2")
  # Uma linha por commit; o IFS de quebra de linha preserva os espaços do assunto.
  old_ifs=$IFS
  IFS='
'
  for subject in $subjects; do
    check "$subject" || failed=1
  done
  IFS=$old_ifs
  exit $failed
fi

subject=$(grep -v '^#' "$1" | head -n 1)
check "$subject"
