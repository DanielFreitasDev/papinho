---
status: aceita
date: 2026-10-15
---

# 0007 — PKI própria para HTTPS na rede local

## Contexto e problema

Na LAN não há domínio público nem CA pública, mas o navegador exige contexto seguro (HTTPS) para PWA,
service worker, notificações, microfone, passkeys e cookies `__Host-`. Certificado autoassinado gera
avisos e não funciona para PWA. Instalar uma CA comum nos aparelhos dos amigos seria perigoso: ela
poderia validar qualquer site.

## Decisão

- Subcomando **`java -jar papinho.jar pki init`** com **BouncyCastle**, sem Spring e sem banco.
- **CA**: ECDSA P-256, 10 anos, `pathLen=0`, **Name Constraints críticas** permitindo só o hostname
  configurado (e subdomínios) e a faixa de IP da LAN. Uma CA vazada ou mal usada não engana outros sites.
- **Servidor**: ECDSA P-256, 397 dias (abaixo do teto de 398 dos navegadores), SAN `DNS:<hostname>` e
  `IP:<ip da LAN>`, EKU `serverAuth`.
- Chaves em `~/.papinho/pki` (ou `PAPINHO_PKI_DIR`), diretório `0700` e chaves `0600`, **fora do
  repositório**. `pki init` é idempotente (reaproveita a CA); `pki renew` reemite só o servidor.
- Perfil **`lan`**: TLS pelo **SSL bundle PEM** do Spring com **HTTP/2**; uma porta HTTP auxiliar serve
  apenas `/setup/**` e redireciona o resto para HTTPS no hostname da PKI.
- Página **`/setup`** com downloads (DER, PEM, perfil iOS), impressão digital SHA-256, QR Code e
  instruções por sistema e navegador; **`/setup/install-ca.sh`** confere a impressão digital antes de
  instalar no sistema e nas bases NSS.
- **Produção**: PKI desligada (`app.pki.enabled=false`, padrão); o TLS vem do proxy ou de uma CA pública.

## Consequências

- Boas: HTTPS real na LAN, PWA instalável, passkeys funcionando com hostname.
- Ruins: cada aparelho instala a CA uma vez; iOS exige ligar a confiança manualmente; passkeys exigem
  hostname resolvível (WebAuthn não aceita IP como RP ID).
- Verificado: os PKIX do JDK e do BouncyCastle **ignoram** Name Constraints na âncora; NSS, OpenSSL,
  Chrome e Apple aplicam. A prova automatizada usa `openssl verify`.

## Alternativas consideradas

- mkcert: CA sem Name Constraints, gerada fora do produto.
- Let's Encrypt com DNS-01: exige domínio público e credenciais de DNS.
- HTTP puro na LAN: perde PWA, push, microfone e passkeys.
