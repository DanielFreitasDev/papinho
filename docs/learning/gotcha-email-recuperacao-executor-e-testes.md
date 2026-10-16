---
name: E-mail de recuperação — executor do Spring, ArchUnit e testes com SMTP
description: applicationTaskExecutor some com o WebSocket; Timestamp barrado pelo ArchUnit; GreenMail e Mailpit nos testes
type: project
category: gotcha
date: 2026-10-16
---
- O Spring Boot só cria o `applicationTaskExecutor` quando não há outro `Executor` no contexto. O WebSocket/STOMP
  registra os seus (`clientInboundChannelExecutor` etc.), então injetar `TaskExecutor` (com ou sem
  `@Qualifier("applicationTaskExecutor")`) derruba o contexto. O `RecoveryMailer` cria o próprio
  `SimpleAsyncTaskExecutor` com threads virtuais.
- O envio de e-mail com token não usa `@ApplicationModuleListener`: o registro de eventos do Modulith grava o
  evento (com o link) na tabela `event_publication`. Use `TransactionSynchronization.afterCommit` + executor.
- O `ArchitectureTest.noLegacyDates` barra `java.sql.Timestamp`; no `JdbcTemplate`, passe
  `instant.atOffset(ZoneOffset.UTC)` (o driver do PostgreSQL aceita `OffsetDateTime`).
- `@DefaultValue`/variável vazia (`${PAPINHO_PUBLIC_URL:}`) vira `URI` vazia, não `null`: valide como `String`.
- `CapturedOutput` inclui o log do GreenMail (que imprime o endereço) e a impressão das requisições do MockMvc;
  para provar "nada de e-mail nos logs", filtre só as linhas de log do servidor.
- E2E: o Mailpit (`axllent/mailpit`, imagem pelo `mirror.gcr.io`) recebe o SMTP na 1025 e expõe
  `/api/v1/search?query=to:"..."` e `/api/v1/message/{ID}` (campo `Text`) na 8025.

**Evidência:** `EmailRecoveryIT` (4) e `e2e/email.spec.ts` verdes na passada 6 (2026-10-16); a falha de contexto
"required a bean of type TaskExecutor" apareceu ao injetar o executor.

**Aplicabilidade:** qualquer tarefa assíncrona nova fora do Modulith e qualquer teste que confira logs.
