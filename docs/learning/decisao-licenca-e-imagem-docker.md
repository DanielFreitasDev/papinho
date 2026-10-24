---
name: Licença AGPL-3.0 e imagem Docker no GHCR (Fase 5)
description: AGPL-3.0-or-later com permissão da seção 7 para o AspectJ; imagem Alpine com o JAR testado via build-context; release idempotente com imagem, JAR e sha256
type: project
category: decision
date: 2026-10-24
---
**Licença:** AGPL-3.0 ou posterior, escolhida pelo dono (2026-10-24): quem hospeda uma versão modificada precisa
oferecer o código às pessoas que a usam, o que combina com a promessa de privacidade. `LICENSE` tem só o texto
oficial (o GitHub reconhece a licença pelo arquivo); o aviso de copyright e a permissão adicional ficam em `NOTICE`,
que vai junto no JAR (`META-INF`, por `copy-resources` para não sobrescrever os `<resources>` do parent do Boot) e
na imagem (`/app`).

**Compatibilidade das dependências** (`pnpm licenses list --prod` e `license-maven-plugin:add-third-party` sem os
escopos de teste): frontend só com licenças permissivas no bundle; LGPL (`@img/sharp-libvips`, do
`@huggingface/transformers` para Node) e MPL (`lightningcss`) ficam só no build e não vão no JAR (conferido no
`dist/`: o único "sharp" é a etiqueta de um emoji). Backend quase todo Apache-2.0/MIT/BSD; Logback e as APIs Jakarta
são duplas (EPL/LGPL, EPL/GPL com Classpath Exception). O único conflito: `org.aspectj:aspectjweaver`, trazido pelo
`spring-aspects` do Spring Data JPA, é `EPL-2.0 AND BSD-3-Clause AND Apache-1.1` sem a GPL como licença secundária,
incompatível com a AGPL na leitura da FSF; a permissão adicional da seção 7 (modelo da FSF) cobre a combinação. O
`spring-session` diz "Broadcom Foundation License" no POM, mas o JAR traz a Apache-2.0.

**Imagem:** `eclipse-temurin:25-jre-alpine` (306 MB contra 480 MB da Ubuntu; a Ubuntu não tem `curl` nem `wget`). O
servidor só decodifica e redimensiona imagens com AWT, sem desenhar texto, então não precisa de fontes; o
`media.spec.ts` (miniaturas) passou contra o contêiner. Usuário 10001, `PAPINHO_DATA_DIR=/data` em volume,
`HEALTHCHECK` com o `wget` do busybox em `/actuator/health`. O estágio `jar` é trocável: o CI passa
`--build-context jar=dist` e a imagem leva o mesmo JAR testado da release (o estágio que compila do código nem roda);
sem isso, `docker build .` compila do código-fonte num JDK 25 na plataforma de quem constrói.

**CI:** o job da release, depois do deploy, decide o que publicar: versão nova no `pom.xml` gera a imagem
multiarquitetura (versão, `latest` e `sha-<commit>`) e depois a tag e a release com `papinho.jar`, `.sha256` e o
`docker pull` na descrição; versão já publicada não faz nada. A rodada manual (`workflow_dispatch`) num branch roda os
testes e publica só a imagem `sha-<commit>`, sem deploy nem release. O pacote nasce privado (o repositório é privado):
torná-lo público é manual, nas configurações do pacote.

**Evidência:** `docker buildx build --build-context jar=…` e o `compose.yaml` do README com a imagem local: saudável,
`smoke`, `chat`, `media`, `audio` e `animations` verdes contra o contêiner (2026-10-24).
