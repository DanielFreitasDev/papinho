---
name: IP do cliente atrás de proxy — framework confia no que o cliente manda
description: forward-headers-strategy=framework usa o primeiro valor do X-Forwarded-For/Forwarded; native (RemoteIpValve) só confia em proxies internos
type: project
category: gotcha
date: 2026-10-16
---
Atrás de proxy reverso, use `server.forward-headers-strategy=native` (`PAPINHO_FORWARD_HEADERS=native`), não
`framework`: os limites por IP (login, cadastro, recuperação, convites) dependem disso.

**Problema/Contexto:** com `framework`, o `ForwardedHeaderFilter` do Spring tira o `getRemoteAddr()` do primeiro
valor do `Forwarded` (`for=`) ou do `X-Forwarded-For`, sem lista de proxies confiáveis. O nginx com
`$proxy_add_x_forwarded_for` só acrescenta o IP real no fim e não mexe no `Forwarded`, então o cliente escolhe o
próprio IP a cada pedido. Com `native`, o `RemoteIpValve` do Tomcat lê o `X-Forwarded-For` de trás para a frente, para
no primeiro endereço que não é proxy interno (loopback, 10/8, 172.16/12, 192.168/16…) e ignora o `Forwarded`.
**Por que importa:** sem isso, força bruta de senha só esbarra no limite por conta, o cadastro aberto não tem limite e
os 11 hashes Argon2 do cadastro viram negação de serviço.
**Como aplicar:** proxy em outra máquina com IP público precisa entrar em
`server.tomcat.remoteip.internal-proxies` (`SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`, expressão regular). Sem proxy
(LAN direta), fica `none`: ninguém deve mandar cabeçalhos de encaminhamento. O MockMvc não passa pelo Tomcat: só um
teste com o servidor real prova a estratégia.
**Evidência:** `ForwardedHeadersIT` (servidor real com `native`: 429 na 21ª tentativa com X-Forwarded-For e
Forwarded inventados; com `framework`, 30 tentativas sem limite), commit `159e5d3`, 2026-10-16.
