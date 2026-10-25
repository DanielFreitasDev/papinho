---
name: Spring Modulith 2.1.2 põe proxy nas implementações internas da API
description: com o spring-modulith-observability, a classe interna que implementa um tipo da API do módulo vira proxy CGLIB a partir da 2.1.2; getClass() muda e um método final rodaria sem os campos
type: project
category: gotcha
date: 2026-10-25
---
**O que muda:** o `spring-modulith-observability-core` (runtime) envolve num proxy os beans que expõem a API de um
módulo, para observar as chamadas entre módulos. Até a 2.1.1, o proxy só pegava o bean cuja classe era um tipo
exposto. A 2.1.2 corrigiu o spring-modulith#1874 ("Methods declared in a module's API but implemented in non-exposed
types are not properly intercepted for observability") e passou a envolver também a implementação interna de uma
interface da API. No Papinho isso pega `FilesystemFileStorage` e `S3FileStorage` (de `FileStorage`) e o
`ClamAvScanner` (de `MalwareScanner`), todos em `media.internal`.

**Consequências:** `getClass()` devolve `S3FileStorage$$SpringCGLIB$$0`. O `S3StorageIT` comparava o nome da classe e
quebrou no PR do Dependabot; compare `AopUtils.getTargetClass(bean)`. O proxy é CGLIB: numa dessas implementações, um
método `final` rodaria na instância do proxy, com os campos nulos, e uma classe `final` nem subiria. Hoje o código
principal não tem método `final` de instância (conferido com grep).

**Evidência:** run 40202244902 (PR #15, 2.1.1 → 2.1.2) com `expected: "S3FileStorage" but was:
"S3FileStorage$$SpringCGLIB$$0"`. Com `AopUtils.getTargetClass`, o `S3StorageIT` passou localmente com
`-Dspring-modulith.version=2.1.1` e com `2.1.2`; no 2.1.2, upload, download com Range e exclusão passaram pelo proxy
(2026-10-25).
