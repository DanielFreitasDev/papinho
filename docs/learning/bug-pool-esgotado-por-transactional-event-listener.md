---
name: Pool de conexões esgotado por @TransactionalEventListener com o registro do Modulith
description: envios simultâneos travavam 30 s; cada listener after-commit pedia uma 2ª conexão para concluir a publicação
type: project
category: bug
date: 2026-10-16
---
**Sintoma:** 25+ envios ao mesmo tempo na mesma conversa travavam; o log mostrava
`HikariPool-1 - Connection is not available, request timed out after 30000ms (total=10, active=10, waiting=…)`.

**Causa:** o registro de eventos do Spring Modulith (`spring-modulith-starter-jpa`) grava uma publicação para
**todo** `@TransactionalEventListener`, não só para `@ApplicationModuleListener`. O listener roda no after-commit
ainda segurando a conexão da requisição e, ao terminar, conclui a publicação numa nova transação, que pede outra
conexão. Com mais requisições do que o pool (10), todas ficam esperando uma conexão que nenhuma libera.

**Correção:** avisos efêmeros do WebSocket (`MessagingNotifier`) usam `@EventListener` + `TransactionSynchronization.afterCommit`
(fora do registro, sem banco); ouvintes entre módulos que consultam o banco (`PresenceService`,
`PrivacyReactions`, `NotificationDispatcher`) são `@ApplicationModuleListener` (assíncronos, transação própria).
Não use `@TransactionalEventListener` síncrono que toque o banco.

**Evidência:** `MessagingIT.shouldSendConcurrentlyWithoutExhaustingConnections` (30 envios simultâneos) falha por
timeout com o código antigo e passa com o novo; rajada de 100 envios pela API: 100 × 201 em 2 s (antes travava),
2026-10-16.
