---
name: PKIX do JDK e do BouncyCastle ignoram Name Constraints da âncora
description: Validar a CA da LAN com CertPathValidator não prova as Name Constraints; use openssl verify ou NSS certutil
type: project
category: gotcha
date: 2026-10-15
---
O `CertPathValidator` PKIX do JDK e o do BouncyCastle aceitam certificados para qualquer domínio emitidos
pela CA da LAN, porque não aplicam as Name Constraints gravadas no próprio certificado da âncora. NSS
(Firefox, Chrome no Linux), OpenSSL, Chrome e Apple aplicam.

**Problema/Contexto:** testes de "CA não valida banco.example.com" passavam a validar o certificado forjado.
Passar as constraints em `new TrustAnchor(cert, bytes)` no JDK lança "name constraints in trust anchor not supported".
**Por que importa:** um teste ingênuo daria falsa segurança, ou falharia sem que a CA estivesse errada.
**Como aplicar:** provar a recusa com `openssl verify -CAfile ca.pem leaf.pem` ("permitted subtree violation")
ou `certutil -V`; o PKIX do Java serve só para provar a cadeia válida.
**Evidência:** `CertificateAuthorityTest` (usa openssl); verificação manual com `certutil -V` em base NSS
retornou "not permitted to issue a certificate with this name" para domínio e IP de fora (2026-10-15).
