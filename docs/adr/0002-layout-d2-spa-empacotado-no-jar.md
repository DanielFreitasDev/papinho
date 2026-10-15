---
status: aceita
date: 2026-10-15
---

# 0002 — Layout D2: Maven multi-módulo com o SPA empacotado no JAR

## Contexto e problema

O Papinho é auto-hospedado: o dono inicia na própria máquina e usa com amigos pela LAN, e depois pode
levar para uma VPS. A operação precisa ser a mais simples possível. O `STRUCTURE.md` oferece D1
(frontend e backend como artefatos separados atrás de um proxy) e D2 (SPA dentro do JAR).

## Decisão

Usar o layout **D2** (`STRUCTURE.md` 5.6):

- `pom.xml` raiz (`packaging pom`) com os módulos `frontend` e `backend`;
- `frontend/pom.xml` usa o `frontend-maven-plugin` para instalar Node e pnpm em `target/`, rodar o
  build do Vite e empacotar `dist/` em `META-INF/resources`;
- o backend depende desse JAR em `runtime` e gera **um único `papinho.jar`** que serve o SPA, a API
  (`/api/**`) e o WebSocket (`/ws`) na mesma origem;
- `SpaConfig` devolve `index.html` para rotas do cliente e mantém 404 para `/api`, `/actuator`, `/ws`,
  `/setup` e arquivos inexistentes.

Lint, typecheck e testes do frontend rodam no job de frontend do CI, não no `./mvnw verify`.

## Consequências

- Boas: um JAR + PostgreSQL para subir; mesma origem elimina CORS e simplifica o cookie de sessão
  (ADR 0003) e o handshake do WebSocket; o PWA e a CSP ficam sob o mesmo servidor.
- Ruins: `./mvnw verify` fica mais lento (instala Node e roda `vite build`); o fallback do SPA é código
  nosso porque o Spring Boot não tem um nativo (spring-boot#27554).

## Alternativas consideradas

- **D1** (nginx + backend separados): duas imagens, dois pipelines e um proxy reverso obrigatório; caro
  demais para o caso de uso principal (rodar na própria máquina).
- Thymeleaf (layout C): não entrega a experiência de app, PWA e tempo real exigidos.
