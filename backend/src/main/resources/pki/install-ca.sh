#!/bin/sh
# Instala (ou remove) a CA da LAN do Papinho neste computador Linux.
#
# Uso:
#   curl -fsSL http://@@HOSTNAME_PORT@@/setup/install-ca.sh | sh
#   curl -fsSL http://@@HOSTNAME_PORT@@/setup/install-ca.sh | sh -s -- --uninstall
#   ... | sh -s -- --yes          (não pede confirmação da impressão digital)
#
# O que faz:
#   1. baixa a CA e confere a impressão digital SHA-256 abaixo antes de qualquer instalação;
#   2. instala no repositório do sistema (update-ca-certificates, update-ca-trust ou trust anchor);
#   3. adiciona às bases NSS (~/.pki/nssdb e perfis do Firefox) com certutil, quando disponível.
# Rodar de novo não duplica nada.
set -eu

CA_URL='@@CA_URL@@'
EXPECTED_FINGERPRINT='@@FINGERPRINT@@'
CA_NAME='Papinho LAN CA (@@HOSTNAME@@)'
FILE_NAME='papinho-@@HOSTNAME@@-ca'

ACTION=install
ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --uninstall) ACTION=uninstall ;;
    --yes|-y) ASSUME_YES=1 ;;
    -h|--help) sed -n '2,13p' "$0" 2>/dev/null || true; exit 0 ;;
    *) echo "Opção desconhecida: $arg" >&2; exit 2 ;;
  esac
done

say() { printf '%s\n' "$*"; }
fail() { printf 'Erro: %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

if [ "$(id -u)" -eq 0 ]; then
  SUDO=''
elif have sudo; then
  SUDO='sudo'
else
  fail "é preciso ser root ou ter sudo para mexer no repositório de certificados do sistema"
fi

detect_store() {
  if have update-ca-certificates && [ -d /usr/local/share/ca-certificates ]; then
    STORE=debian
  elif have update-ca-trust && [ -d /etc/pki/ca-trust/source/anchors ]; then
    STORE=fedora
  elif have trust; then
    STORE=p11kit
  else
    STORE=none
  fi
}

nss_databases() {
  [ -d "$HOME/.pki/nssdb" ] && printf '%s\n' "$HOME/.pki/nssdb"
  for profile in "$HOME"/.mozilla/firefox/*/ "$HOME"/snap/firefox/common/.mozilla/firefox/*/ \
                 "$HOME"/.var/app/org.mozilla.firefox/.mozilla/firefox/*/; do
    [ -f "${profile}cert9.db" ] && printf '%s\n' "${profile%/}"
  done
  return 0
}

nss_remove() {
  have certutil || return 0
  nss_databases | while IFS= read -r db; do
    while certutil -d "sql:$db" -L -n "$CA_NAME" >/dev/null 2>&1; do
      certutil -d "sql:$db" -D -n "$CA_NAME" || break
    done
  done
}

nss_add() {
  if ! have certutil; then
    say "certutil não encontrado: Chrome/Chromium e Firefox podem não confiar na CA."
    say "  Debian/Ubuntu: sudo apt install libnss3-tools | Fedora: sudo dnf install nss-tools | Arch: sudo pacman -S nss"
    return 0
  fi
  nss_remove
  nss_databases | while IFS= read -r db; do
    certutil -d "sql:$db" -A -t "C,," -n "$CA_NAME" -i "$1" && say "  NSS: $db"
  done
  if [ ! -d "$HOME/.pki/nssdb" ]; then
    mkdir -p "$HOME/.pki/nssdb"
    certutil -d "sql:$HOME/.pki/nssdb" -N --empty-password
    certutil -d "sql:$HOME/.pki/nssdb" -A -t "C,," -n "$CA_NAME" -i "$1" && say "  NSS: $HOME/.pki/nssdb"
  fi
}

system_remove() {
  case "$STORE" in
    debian)
      if [ -f "/usr/local/share/ca-certificates/$FILE_NAME.crt" ]; then
        $SUDO rm -f "/usr/local/share/ca-certificates/$FILE_NAME.crt"
        $SUDO update-ca-certificates --fresh >/dev/null
      fi ;;
    fedora)
      if [ -f "/etc/pki/ca-trust/source/anchors/$FILE_NAME.pem" ]; then
        $SUDO rm -f "/etc/pki/ca-trust/source/anchors/$FILE_NAME.pem"
        $SUDO update-ca-trust extract
      fi ;;
    p11kit)
      if [ -f "/etc/ca-certificates/trust-source/anchors/$FILE_NAME.pem" ]; then
        $SUDO rm -f "/etc/ca-certificates/trust-source/anchors/$FILE_NAME.pem"
        $SUDO trust extract-compat
      fi ;;
  esac
}

system_add() {
  case "$STORE" in
    debian)
      $SUDO install -m 0644 "$1" "/usr/local/share/ca-certificates/$FILE_NAME.crt"
      $SUDO update-ca-certificates >/dev/null
      say "  Sistema: /usr/local/share/ca-certificates/$FILE_NAME.crt" ;;
    fedora)
      $SUDO install -m 0644 "$1" "/etc/pki/ca-trust/source/anchors/$FILE_NAME.pem"
      $SUDO update-ca-trust extract
      say "  Sistema: /etc/pki/ca-trust/source/anchors/$FILE_NAME.pem" ;;
    p11kit)
      $SUDO install -D -m 0644 "$1" "/etc/ca-certificates/trust-source/anchors/$FILE_NAME.pem"
      $SUDO trust extract-compat
      say "  Sistema: /etc/ca-certificates/trust-source/anchors/$FILE_NAME.pem" ;;
    none)
      say "Nenhum repositório de certificados conhecido (update-ca-certificates, update-ca-trust, trust)." ;;
  esac
}

sha256_of() {
  if have sha256sum; then sha256sum "$1" | cut -d' ' -f1
  elif have shasum; then shasum -a 256 "$1" | cut -d' ' -f1
  elif have openssl; then openssl dgst -sha256 -r "$1" | cut -d' ' -f1
  else fail "nenhuma ferramenta de SHA-256 encontrada (sha256sum, shasum ou openssl)"
  fi
}

detect_store

if [ "$ACTION" = uninstall ]; then
  say "Removendo $CA_NAME…"
  system_remove
  nss_remove
  say "Pronto. A CA foi removida."
  exit 0
fi

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT INT TERM
PEM="$WORK_DIR/ca.pem"
DER="$WORK_DIR/ca.der"

say "Baixando a CA de $CA_URL…"
if have curl; then curl -fsSL -o "$PEM" "$CA_URL"
elif have wget; then wget -q -O "$PEM" "$CA_URL"
else fail "curl ou wget são necessários"
fi

grep -q -- '-----BEGIN CERTIFICATE-----' "$PEM" || fail "o arquivo baixado não é um certificado PEM"
sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' "$PEM" | sed '1d;$d' | tr -d '\r\n ' \
  | base64 -d > "$DER" 2>/dev/null || fail "não foi possível decodificar o certificado"

ACTUAL_FINGERPRINT=$(sha256_of "$DER" | tr 'A-F' 'a-f')
if [ "$ACTUAL_FINGERPRINT" != "$EXPECTED_FINGERPRINT" ]; then
  fail "a impressão digital não confere! esperada $EXPECTED_FINGERPRINT, recebida $ACTUAL_FINGERPRINT. Nada foi instalado."
fi
say "Impressão digital SHA-256 conferida: $ACTUAL_FINGERPRINT"

if [ "$ASSUME_YES" -ne 1 ] && [ -r /dev/tty ] && { : < /dev/tty; } 2>/dev/null; then
  say "Compare com a impressão digital mostrada pelo dono do servidor."
  printf 'Instalar a CA? [s/N] '
  read -r answer < /dev/tty || answer=''
  case "$answer" in
    s|S|sim|SIM|y|Y|yes) ;;
    *) say "Cancelado. Nada foi instalado."; exit 1 ;;
  esac
fi

say "Instalando $CA_NAME…"
system_remove
system_add "$PEM"
nss_add "$PEM"
say "Pronto. Feche e abra o navegador e acesse https://@@HOSTNAME_HTTPS@@/"
