---
name: Testar S3 sem MinIO e com Range de verdade
description: imagens do MinIO sumiram dos registros; adobe/s3mock com espera pelo log; skip antes da leitura vira Range
type: project
category: gotcha
date: 2026-10-17
---
- `minio/minio` não baixa mais (Docker Hub nega, quay.io pede login, `mirror.gcr.io` não tem). O
  `adobe/s3mock:4.10.0` baixa por `mirror.gcr.io/adobe/s3mock` (retague para o nome original) e fala a API S3
  com `forcePathStyle(true)`; `COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS` cria o bucket.
- A espera HTTP do Testcontainers levou "Connection reset" até o tempo acabar (a porta abre antes do app):
  use `Wait.forLogMessage(".*Started S3MockApplication.*", 1)`.
- O Spring responde `Range` com `resource.getInputStream()` e `skip(start)`. Um stream que só abre o objeto na
  primeira leitura transforma esse `skip` em `GetObject` com `Range: bytes=start-`, sem baixar o começo.
- O S3 pede o tamanho no `PutObject`: um `InputStream` de tamanho desconhecido passa antes por arquivo temporário.

**Evidência:** `S3StorageIT` (upload, `Range` 206 com os bytes certos, objeto apagado); ignorar o `Range` ou não
apagar derrubam o teste (commit `a2c77cf`).
