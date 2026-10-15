---
status: aceita
date: 2026-10-15
---

# 0003 — Autenticação por sessão em cookie, não JWT

## Contexto e problema

O único cliente é o próprio SPA, servido na mesma origem (ADR 0002). Precisamos de login por senha e por
passkey, revogação de sessões (ID-06), WebSocket autenticado e proteção contra CSRF, sem guardar tokens
em `localStorage`.

## Decisão

- **Spring Session JDBC** guarda as sessões no PostgreSQL (tabelas versionadas pelo Flyway).
- Cookie **`__Host-SESSION`** com `HttpOnly`, `Secure`, `SameSite=Lax` e `Path=/`, sem `Domain`.
- **`csrf.spa()`**: cookie `XSRF-TOKEN` legível pelo SPA (`Secure`, `SameSite=Strict`), enviado de volta
  no cabeçalho `X-XSRF-TOKEN`; um filtro materializa o token na primeira resposta.
- **`Argon2PasswordEncoder`** (`defaultsForSpringSecurity_v5_8`, Argon2id) para senhas, PINs e códigos.
- **Passkeys** com `spring-security-webauthn` e **TOTP** opcional.
- **WebSocket**: o handshake usa o mesmo cookie de sessão, valida o `Origin` e exige o token CSRF no frame
  `CONNECT` do STOMP.
- Cadeias de segurança separadas (API, Actuator, `/setup`, shell do SPA) e uma **cadeia final `denyAll`**.
- Erros 401/403 em Problem Details, sem desafio Basic e sem revelar se o usuário existe.

## Consequências

- Boas: tokens nunca ficam acessíveis ao JavaScript; revogar uma sessão é apagar uma linha; o WebSocket
  reaproveita a autenticação.
- Ruins: estado no servidor (uma consulta por requisição autenticada); exige CSRF em toda escrita.

## Alternativas consideradas

- JWT em resource server (exemplos do `JAVA.md`): não se aplica; revogação difícil e token exposto ao JS
  ou em cookie que ainda exigiria CSRF.
- Sessão em memória: perde todos os logins a cada reinício.
