---
name: Login por passkey do Spring Security não ficava salvo na sessão
description: WebAuthnAuthenticationFilter sai do configurer sem SecurityContextRepository nem estratégia de sessão
type: project
category: bug
date: 2026-10-15
---
Com `http.webAuthn(...)` no Spring Security 7.1, o `WebAuthnAuthenticationFilter`:

1. não recebe o `SecurityContextRepository` da cadeia → o login funcionava na resposta, mas a próxima
   requisição voltava sem sessão (`/auth/session` com `me: null`);
2. não troca o id da sessão nem renova o CSRF;
3. guarda como principal um `PublicKeyCredentialUserEntity`, não o `PapinhoUser` → `CurrentUser.id()` falhava;
4. exceções do webauthn4j (origem errada, assinatura inválida) sobem como 500.

**Correção** (`identity/PasskeySecurity.java`): `ObjectPostProcessor` define repositório de contexto e
`ChangeSessionId` + `CsrfAuthenticationStrategy`; o success handler recarrega o `PapinhoUser` pelo
`UserDetailsService` e salva o contexto; `PasskeyErrorFilter` converte erros do webauthn4j em 400
`passkey-invalid`. No SPA, depois do login por passkey, `fetchQuery({ ...me, staleTime: 0 })` antes de navegar,
senão o guard reaproveita o "sem sessão" do cache.
**Evidência:** e2e "passkey: cadastra com autenticador virtual e entra sem senha" verde em desktop e celular
(2026-10-15).
