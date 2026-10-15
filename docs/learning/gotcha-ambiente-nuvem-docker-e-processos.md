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
  funcionando; para outras use espelhos (`public.ecr.aws/docker/library/<imagem>`, `quay.io/fedora/fedora`).
- O JDK do sistema é 21; o JDK 25 precisa ser baixado (Adoptium) e usado via `JAVA_HOME`/`PATH`.
- `pkill -f`/`pgrep -f` com um padrão que aparece no próprio comando mata o shell da ferramenta (exit 144).
  Guarde o PID em arquivo ao iniciar processos em segundo plano.
- Recompilar `backend/target/papinho.jar` com uma instância rodando a partir dele quebra essa instância.
- `curl` passa pelo proxy do agente: use `--noproxy '*'` para `localhost`/`papinho.lan`.

**Evidência:** ocorrências nesta passada (2026-10-15).
