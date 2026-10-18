#!/usr/bin/env bash
#
# deploy.sh — instala um papinho.jar novo e reinicia o serviço, com verificação de saúde e rollback.
#
# Rodado pelo runner self-hosted (job "deploy" de .github/workflows/ci.yml) via sudo:
#   sudo /opt/papinho/deploy.sh /caminho/para/papinho.jar <versão-esperada>
#
# Passos: guarda o JAR atual -> instala o novo -> reinicia o serviço -> espera o /actuator/health
# responder UP e o /actuator/info trazer a versão esperada. Se não ficar saudável no prazo, restaura o
# JAR anterior, reinicia de novo e confere a saúde da versão restaurada (o job fica vermelho no GitHub e
# a versão antiga continua no ar).
#
# Este script roda como root: precisa ser de root e não pode ser gravável pelo usuário do runner, senão
# o runner poderia reescrever o que executa como root (deploy/PRODUCAO.md, "Deploy automático").

set -euo pipefail

DESTINO="/opt/papinho/papinho.jar"
BACKUP="/opt/papinho/papinho.jar.bak"
SERVICO="papinho"
URL_SAUDE="http://127.0.0.1:54326/actuator/health"
URL_INFO="http://127.0.0.1:54326/actuator/info"
# 90 x 2 s = até 3 min: o boot leva ~30 s na VPS, e uma migração Flyway pesada pode levar mais.
TENTATIVAS=90
INTERVALO=2

log() { echo "[deploy] $*"; }

aguardar_saude() {
  for ((i = 1; i <= TENTATIVAS; i++)); do
    if curl -fs --max-time 5 "$URL_SAUDE" 2>/dev/null | grep -q '"status":"UP"'; then
      log "saúde OK na tentativa $i/$TENTATIVAS."
      return 0
    fi
    sleep "$INTERVALO"
  done
  return 1
}

executar_rollback() {
  if [[ ! -f "$BACKUP" ]]; then
    log "sem backup para rollback. Investigue com: journalctl -u $SERVICO -n 100"
    return
  fi
  log "voltando para a versão anterior..."
  install -o root -g root -m 0644 "$BACKUP" "$DESTINO"
  systemctl restart "$SERVICO"
  if aguardar_saude; then
    log "rollback aplicado; a versão anterior está no ar. Investigue: journalctl -u $SERVICO -n 100"
  else
    log "ALERTA: rollback aplicado, mas o serviço não voltou saudável. journalctl -u $SERVICO -n 200"
  fi
}

JAR_ORIGEM="${1:-}"
VERSAO_ESPERADA="${2:-}"
if [[ -z "$JAR_ORIGEM" || ! -f "$JAR_ORIGEM" ]]; then
  log "ERRO: uso: $0 <caminho-do-jar> [versão-esperada]"
  exit 2
fi

if [[ -f "$DESTINO" ]]; then
  cp -f "$DESTINO" "$BACKUP"
  log "versão atual guardada em $BACKUP"
fi

# O JAR é de root e só leitura para o serviço (ProtectSystem=strict na unit).
install -o root -g root -m 0644 "$JAR_ORIGEM" "$DESTINO"
log "JAR novo instalado em $DESTINO"

systemctl restart "$SERVICO"
log "serviço '$SERVICO' reiniciado; aguardando $URL_SAUDE..."

if aguardar_saude; then
  versao_no_ar="$(curl -fs --max-time 5 "$URL_INFO" 2>/dev/null \
    | grep -oE '"version"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n1 \
    | sed -E 's/.*"([^"]+)"$/\1/')"
  if [[ -n "$VERSAO_ESPERADA" && "$versao_no_ar" != "$VERSAO_ESPERADA" ]]; then
    log "ERRO: versão no ar (${versao_no_ar:-desconhecida}) difere da esperada ($VERSAO_ESPERADA); revertendo."
    executar_rollback
    exit 1
  fi
  log "deploy concluído (versão no ar: ${versao_no_ar:-desconhecida})."
  exit 0
fi

log "ERRO: o serviço não ficou saudável em $((TENTATIVAS * INTERVALO)) s."
executar_rollback
exit 1
