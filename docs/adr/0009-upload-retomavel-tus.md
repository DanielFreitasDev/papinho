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

## Notas de implementação (Fase 3, 2026-10-16)

- Servidor tus próprio no módulo `media` (`TusController`/`UploadService`): extensões `creation`,
  `termination` e `expiration`. Os pedaços ficam num diretório local (`PAPINHO_UPLOAD_DIR`) e o arquivo só
  vai para a porta `FileStorage` ao completar; assim a porta continua simples (gravar inteiro, ler com faixa).
- O id do upload vira o id da mídia; o último `PATCH` responde com `Papinho-Media-Id`.
- Escolhidas as linhas mais estabelecidas: **Uppy 5** (`@uppy/core` 5.2, `@uppy/tus` 5.1; a 6 saiu há
  poucas semanas) e **Apache Tika 3.3** (`tika-core`; a 4.x é recente). Pedaços de 50 MB no cliente.

## Atualização (2026-10-25)

- O `tika-core` subiu para a 4.1.0 (PR #5 do Dependabot). As quebras da linha 4 ficam no tika-server, no tika-app,
  nas pipes e na configuração. O upload só usa a detecção do `tika-core` (`Tika`, `TikaInputStream`), coberta pelos
  testes do backend, que passaram no CI do PR.
