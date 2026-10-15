---
name: STOMP com Spring Security 7 — CSRF cru, heartbeat e sessão HTTP
description: o CONNECT exige token mascarado por padrão, o heartbeat precisa de agendador próprio e o WebSocket sobrevive ao logout
type: project
category: gotcha
date: 2026-10-15
---
- `@EnableWebSocketSecurity` usa `XorCsrfChannelInterceptor`, que espera o token **mascarado**; o SPA (com
  `csrf.spa()`) só tem o valor cru do cookie. Um bean chamado `csrfChannelInterceptor` substitui o padrão:
  `new CsrfChannelInterceptor()` compara o cabeçalho `X-XSRF-TOKEN` do CONNECT com o token cru.
- Sem token válido o servidor só fecha a conexão (`ConnectionLostException: Connection closed` no cliente),
  sem log de erro — prove com teste, não com log.
- `enableSimpleBroker().setHeartbeatValue(...)` exige `setTaskScheduler`; injetar `TaskScheduler` fica ambíguo com o
  `messageBrokerTaskScheduler`. Use um `ThreadPoolTaskScheduler` próprio, fora do contexto.
- O handshake `/ws` precisa estar na cadeia de segurança da API; senão cai no `denyAll`.
- O WebSocket não cai sozinho no logout/revogação: `RealtimeSessions` guarda o id da sessão HTTP do handshake e
  fecha (código 4401) quando ela some do Spring Session.

**Evidência:** `RealtimeIT` (4 testes, servidor real e `WebSocketStompClient`) em 2026-10-15.
