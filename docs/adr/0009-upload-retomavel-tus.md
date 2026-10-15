---
status: aceita
date: 2026-10-15
---

# 0009 — Upload retomável com o protocolo tus

## Contexto e problema

Arquivos de até **1 GB** (MD-01) em Wi-Fi doméstico e celular: um upload multipart único falha e
recomeça do zero a cada queda.

## Decisão

- Protocolo **tus 1.0** (criação, `PATCH` com `Upload-Offset`, `HEAD` para retomar, término/cancelamento)
  implementado no módulo `media`, gravando pela porta de armazenamento (ADR 0006).
- **Uppy** com `@uppy/tus` no frontend: barra de progresso, pausa/retomada e cancelamento.
- Limite de **1 GB** por arquivo e cota por usuário checados na criação (`Upload-Length`); tipo detectado
  pelo conteúdo com Apache Tika ao final; nome sanitizado.
- Uploads incompletos expiram e são limpos por job.

## Consequências

- Boas: retomada automática; protocolo aberto e maduro; cliente pronto.
- Ruins: implementação de servidor nossa (as bibliotecas Java de tus não acompanham o Spring Boot 4) ou
  avaliação de biblioteca na Fase 3; endpoints próprios fora do multipart padrão.

## Alternativas consideradas

- Multipart simples: sem retomada.
- Upload direto para S3 com URLs pré-assinadas: não funciona no armazenamento em disco da LAN.
