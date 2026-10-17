---
status: aceita
date: 2026-10-15
---

# 0005 — Monólito modular com Spring Modulith

## Contexto e problema

O produto tem muitas áreas (identidade, conversas, mídia, privacidade, moderação…) que precisam evoluir
sem virar um emaranhado, mas um sistema distribuído seria desproporcional para auto-hospedagem.

## Decisão

Um único deployable organizado em módulos do **Spring Modulith**, um pacote de primeiro nível por módulo:

`identity`, `profile`, `contact`, `messaging` (conversas e mensagens), `media`, `presence`, `notification`,
`transcription`, `linkpreview`, `privacy`, `moderation`, `backup`, `admin`, `pki`, mais `shared`
(módulo aberto e pequeno: segurança base, erros, SPA).

- Subpacotes de um módulo são internos; outros módulos usam só os tipos públicos do pacote raiz,
  DTOs e eventos.
- Comunicação entre módulos por **eventos** com o registro de publicação do Modulith (**outbox** na tabela
  `event_publication`, republicado no reinício).
- **`ModularityTests`** (`ApplicationModules.verify()`) e **`ArchitectureTest`** (ArchUnit) no build.
- Migrations Flyway em `db/migration/<módulo>/`.
- Módulos são criados quando a primeira funcionalidade deles é implementada.
- **`conversation` e `message` são um módulo só, `messaging`** (registrado na auditoria de 2026-10-17, achado A91).
  Conversa e mensagem dividem o mesmo contador por conversa (`seq` e `last_seq`, a trava da linha que ordena
  envio, edição, leitura e sincronização) e as mesmas transações: enviar muda a participação (aceitar a
  solicitação), a lista (última mensagem, apagadas, arquivadas) e as confirmações. Separados, um dependeria do
  outro nos dois sentidos, o que o Modulith recusa, ou o envio precisaria de eventos para o que hoje é uma
  transação. Grupos, listas de transmissão, enquetes, figurinhas e busca ficam no mesmo módulo pelo mesmo motivo.

## Consequências

- Boas: fronteiras verificadas no build; extrair um módulo para serviço próprio continua possível.
- Ruins: eventos assíncronos exigem listeners idempotentes.

## Alternativas consideradas

- Pacotes por camada: escondem acoplamento.
- Multi-módulo Maven por domínio: mais build, sem ganho sobre o Modulith.
- Microsserviços: operação incompatível com "um JAR + PostgreSQL".
