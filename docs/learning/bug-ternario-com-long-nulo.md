---
name: Ternário misturando long e Long nulo dá NullPointerException
description: cond ? row.lastSeq() : (outra ? row.peerReadSeq() : null) desembrulha o null e quebra a lista de conversas
type: project
category: bug
date: 2026-10-15
---
Em Java, `a ? long : (b ? long : null)` tem tipo `long` (o ramo primitivo vence): o `null` do ramo interno é
desembrulhado e lança `NullPointerException` em tempo de execução, sem aviso do compilador. Isso derrubou
`GET /api/v1/conversations` (500) ao calcular `peerReadSeq` para conversas, grupos e mensagens salvas.

**Como evitar:** com valores anuláveis (`@Nullable Long`), use `if/else` atribuindo a uma variável `Long`, ou
garanta que todos os ramos sejam `Long` (`Long.valueOf(...)`).

**Evidência:** `MessagingIT` com 6 falhas 500 ("Cannot invoke Long.longValue()") até trocar o ternário por
`if/else` em `ConversationService.toView` (2026-10-15).
