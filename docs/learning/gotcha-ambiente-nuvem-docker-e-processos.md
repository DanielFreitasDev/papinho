---
name: Ambiente de nuvem do agente — Docker, imagens e processos
description: dockerd precisa ser iniciado; Docker Hub dá 429; pkill/pgrep -f podem matar o próprio shell
type: project
category: gotcha
date: 2026-10-15
---
No sandbox de nuvem usado nas passadas:

- O Docker está instalado mas o daemon não roda: `dockerd > log 2>&1 &` antes dos testes com Testcontainers.
- O Docker Hub responde `429 Too Many Requests` depois de alguns pulls. Imagens já baixadas continuam
  funcionando; para outras use espelhos (`mirror.gcr.io/library/<imagem>`, `public.ecr.aws/docker/library/<imagem>`,
  `quay.io/fedora/fedora`) e `docker tag` para o nome original (`postgres:18-alpine` do Testcontainers). Na
  passada 3 o ECR respondeu "Data limit exceeded" e só o `mirror.gcr.io` funcionou.
- O JDK do sistema é 21; o JDK 25 precisa ser baixado (Adoptium) e usado via `JAVA_HOME`/`PATH`.
- `pkill -f`/`pgrep -f` com um padrão que aparece no próprio comando mata o shell da ferramenta (exit 144).
  Guarde o PID em arquivo ao iniciar processos em segundo plano.
- Recompilar `backend/target/papinho.jar` com uma instância rodando a partir dele quebra essa instância.
- `curl` passa pelo proxy do agente: use `--noproxy '*'` para `localhost`/`papinho.lan`.
- O Maven Central (`repo.maven.apache.org` e `repo1`) responde 429: o `mvnw` não baixa nem a distribuição do Maven.
  Use `MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2` e o mesmo endereço como
  `<mirror>` de `central` em `~/.m2/settings.xml` (passadas 4 a 6).

**Evidência:** ocorrências nas passadas 1 a 3 (2026-10-15).
