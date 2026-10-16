---
name: Decisões da Fase 5 (privacidade) que não estão óbvias no código
description: reciprocidade por par, leitura no grupo, token de conversa trancada, bloqueio de tela no cliente
type: project
category: decision
date: 2026-10-16
---
- **Reciprocidade por par** (PV-05): A vê o visto por último/online de B só se as regras dos dois deixarem um ver
  o outro. Escondido volta como offline e sem horário (não revela que foi escondido). ADR 0013.
- **Leitura**: padrão global só nas conversas individuais; no grupo é sempre ligada, com sobreposição em
  `conversation_members.read_receipts`. No grupo quem desligou sai do "lida por todos". O e2e mostra que
  **quem envia avança a própria leitura** até a mensagem enviada (`peerReadSeq` 2 depois de responder).
- **Conversas trancadas**: token no cabeçalho `X-Papinho-Unlock`, só em memória (servidor e aba). Anexos
  continuam por id para membros, porque `<img>` não manda cabeçalho. Ao implementar NT-05 (IndexedDB), **não
  guardar conversas trancadas no cache offline**.
- **Bloqueio de tela**: o estado é do cliente (`localStorage` `papinho:screen-locked:<id>`); o fundo fica
  `inert` + desfocado (inclusive portais abertos depois, por `MutationObserver`). Passkey reutiliza o login.
- **Desfoque**: atributos em `<html>` (`data-blur`, `data-reveal-delay`) e regras fixas no `index.css`; nada de
  estilo inline por causa da CSP. Foco revela via `[data-private-scope]:focus-within`.
- **PV-08** só guarda a escolha; quem respeita é NT-01/NT-03 (Fase 6).

**Evidência:** `PrivacyIT` (7) e `RealtimeIT.shouldHideOnlineByPrivacy` verdes; e2e `privacy.spec.ts` verde em
desktop e celular (2026-10-16).
