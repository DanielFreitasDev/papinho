#!/bin/sh
# Verifica o install-ca.sh de verdade em contêineres Debian, Fedora e Arch (PK-04).
#
# Pré-requisitos: Docker e um Papinho no perfil lan acessível pelos contêineres como papinho.lan,
# com a porta HTTP (setup) e a HTTPS informadas abaixo. Exemplo:
#   java -jar backend/target/papinho.jar pki init --ip 172.17.0.1 --network 172.16.0.0/12
#   java -jar backend/target/papinho.jar --spring.profiles.active=lan \
#     --app.pki.public-http-port=8080 --app.pki.public-https-port=8443
#   scripts/verify-install-ca.sh
#
# Para cada distribuição: instala, roda de novo (idempotência), confere que o curl do sistema valida o
# HTTPS do servidor, desinstala e confere que nada ficou para trás. No Debian também confere as bases NSS
# (Chrome e um perfil do Firefox). Imagens vêm de espelhos públicos para evitar o limite do Docker Hub.
set -eu

HTTP_PORT=${HTTP_PORT:-8080}
HTTPS_PORT=${HTTPS_PORT:-8443}
SCRIPT_URL="http://papinho.lan:$HTTP_PORT/setup/install-ca.sh"

run() {
  image=$1
  prepare=$2
  echo "===== $image"
  docker run --rm --add-host papinho.lan:host-gateway "$image" sh -c "
    set -e
    $prepare
    curl -fsSL $SCRIPT_URL -o /tmp/install-ca.sh
    sh /tmp/install-ca.sh --yes
    sh /tmp/install-ca.sh --yes >/dev/null
    curl -fsS -o /dev/null https://papinho.lan:$HTTPS_PORT/actuator/health && echo 'TLS validado pelo sistema: ok'
    if command -v certutil >/dev/null; then
      certutil -L -d sql:\$HOME/.pki/nssdb | grep -q 'Papinho LAN CA' && echo 'NSS (Chrome): ok'
      certutil -L -d sql:\$HOME/.mozilla/firefox/test.default | grep -q 'Papinho LAN CA' && echo 'NSS (Firefox): ok'
    fi
    sh /tmp/install-ca.sh --uninstall >/dev/null
    if curl -fsS -o /dev/null https://papinho.lan:$HTTPS_PORT/actuator/health 2>/dev/null; then
      echo 'ERRO: o TLS continua confiável depois de desinstalar'; exit 1
    fi
    echo 'desinstalação: ok'
  "
}

run public.ecr.aws/docker/library/debian:stable-slim \
  'export DEBIAN_FRONTEND=noninteractive; apt-get update -qq >/dev/null; apt-get install -y -qq ca-certificates curl libnss3-tools >/dev/null 2>&1; mkdir -p $HOME/.mozilla/firefox/test.default; certutil -N -d sql:$HOME/.mozilla/firefox/test.default --empty-password'
run quay.io/fedora/fedora:42 'true'
run public.ecr.aws/docker/library/archlinux:latest 'true'
echo "Todas as distribuições passaram."
