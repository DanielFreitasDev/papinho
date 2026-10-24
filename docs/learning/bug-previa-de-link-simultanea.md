---
name: Prévia de link pedida ao mesmo tempo dava 500
description: save() com id atribuído faz SELECT e INSERT; duas buscas do mesmo link novo colidiam na chave primária do cache
type: project
category: bug
date: 2026-10-24
---
Cache gravado pelo `save()` do Spring Data com chave natural (hash, código) precisa aguentar duas gravações da mesma
chave ao mesmo tempo.

**Problema/Contexto:** `LinkPreviewService.preview` lê o cache, busca a página fora de transação (leva segundos) e
grava com `previews.save(preview)`. Com id atribuído e sem `@Version`, o `save` faz `merge`: um SELECT que não acha
nada e um INSERT. Quem envia e quem recebe um link novo pedem a prévia quase juntos; as duas buscas encontram o cache
vazio e quem grava por último recebe `duplicate key value violates unique constraint "pk_link_previews"`, ou seja,
500 na tela e "Failed to load resource" no console (o e2e falha com qualquer erro de console).
**Por que só às vezes:** só com o link fora do cache, isto é, na primeira rodada contra uma instância nova; nas
repetições o link já estava gravado e o spec passava.
**Correção:** a `DataIntegrityViolationException` do `save` devolve a prévia da própria busca (a outra gravou a mesma
página).
**Como aplicar:** em outra tabela de cache ou de "primeira vez" com chave natural, use `on conflict` (como os
repositórios com `JdbcClient` do projeto) ou trate a violação. Para provar, segure as respostas do site de teste até
todas as requisições chegarem (`CountDownLatch`): assim todas passam pela leitura vazia antes de gravar.

**Evidência:** log do e2e `spacing.spec.ts` contra instância nova (2026-10-24), com duas violações no mesmo
milissegundo; `LinkPreviewIT.shouldServeTheSameLinkFetchedAtOnce` (4 pedidos juntos: 3 respostas 500 sem a correção,
4 respostas 200 com ela).
