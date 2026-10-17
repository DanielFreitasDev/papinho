---
name: Busca externa precisa de prazo total e não pode segurar conexão do banco
description: timeouts do HttpClient são por leitura; gotejamento prende a requisição; @Transactional em volta esgota o Hikari
type: project
category: gotcha
date: 2026-10-17
---
- Os timeouts do Apache HttpClient 5 (`connect`, `socket`, `response`) valem **por leitura**: um site que manda
  1 byte a cada 3 s nunca dispara nenhum. O prazo de relógio é um `ScheduledExecutorService` que chama
  `HttpGet.cancel()` (`SafeFetcher`, achado A01).
- Cancelada ainda na fila do pool HTTP (`setMaxConnPerRoute`), a execução lança `IllegalStateException:
  Endpoint not acquired / already released`, não `IOException`: trate as duas.
- `@Transactional` em volta de uma chamada externa segura uma conexão do Hikari o tempo todo: 10 prévias lentas
  de um usuário comum esgotaram o pool (10) e derrubaram a API para todos. Leia e grave o cache nas transações
  curtas do repositório e busque fora delas.
- O `HttpServer` do JDK usado nos testes tem um executor de uma thread só por padrão: um handler que goteja trava
  os outros; use `setExecutor(Executors.newCachedThreadPool())`.
- Upload grande para outro serviço com o `java.net.http.HttpClient`: monte o multipart com
  `BodyPublishers.concat(ofByteArray(cabeçalho), fromPublisher(ofInputStream(() -> in), tamanho),
  ofByteArray(fim))`; com `ByteArrayOutputStream` um áudio de 1 GB derrubava o heap (achado A19,
  `OpenAiCompatibleClientTest` com 2,06 GiB gerados sob demanda, commit `da72bf3`).

**Evidência:** `LinkPreviewIT.shouldBoundSlowFetchesWithoutHoldingConnections` (12 prévias contra um site que
goteja; pool ativo ≤ 1 e menos de 15 s) e `SafeFetcherTest.shouldAbortDripAtTotalDeadline` (commit `144f05a`).
