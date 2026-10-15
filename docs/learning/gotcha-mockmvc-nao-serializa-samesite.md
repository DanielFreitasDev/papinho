---
name: MockMvc não serializa SameSite de cookies
description: Set-Cookie no MockHttpServletResponse perde o atributo SameSite; verifique contra o Tomcat real
type: project
category: gotcha
date: 2026-10-15
---
O `CookieCsrfTokenRepository` grava o `SameSite` como atributo do `Cookie` (Servlet 6). O Tomcat real
renderiza o atributo; o `MockHttpServletResponse` do MockMvc não, então o teste de slice dizia que o
`XSRF-TOKEN` saía sem `SameSite=Strict`.

**Como aplicar:** atributos de cookie (`SameSite`, `__Host-`) são verificados em teste com servidor real
(`webEnvironment = RANDOM_PORT`), como `LanSetupIT.shouldIssueStrictCsrfCookieOverTls`.
**Evidência:** `SecurityIT` via MockMvc mostrava `XSRF-TOKEN=...; Path=/; Secure`; `LanSetupIT` sobre TLS
recebeu `SameSite=Strict` (2026-10-15).
