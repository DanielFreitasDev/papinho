---
status: aceita
date: 2026-10-15
---

# 0006 — Armazenamento de arquivos atrás de uma porta

## Contexto e problema

Fotos, vídeos, documentos e áudios (até 1 GB cada) precisam de armazenamento simples na LAN e escalável
em produção.

## Decisão

O módulo `media` define a porta `FileStorage` (gravar por streaming, ler com faixa de bytes, apagar,
tamanho). Duas implementações selecionadas por configuração:

- **sistema de arquivos** (padrão): diretório configurável (`~/.papinho/files`), chaves opacas
  (UUID v7), nunca o nome original do usuário no caminho;
- **S3-compatível** (produção): AWS S3, MinIO, Cloudflare R2 etc.

Metadados (dono, tipo detectado pelo Tika, tamanho, hash, miniaturas) ficam no PostgreSQL. A cota por
usuário é checada antes de aceitar o upload. Apagar a mensagem apaga o arquivo pela porta.

## Consequências

- Boas: auto-hospedagem sem dependências; produção sem refatorar o domínio.
- Ruins: duas implementações para manter e testar (a de S3 com Testcontainers + MinIO).

## Alternativas consideradas

- Arquivos no PostgreSQL (`bytea`/large objects): backup e banco incham, streaming de 1 GB é ruim.
- Só S3: obriga a subir MinIO na LAN.
