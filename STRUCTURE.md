# Project Structure & Repository Layout — Rules for AI Coding Agents

## Applicability and interoperability — revision 1.1 (2026-09-13)

This guide is independently usable. Companion guides are optional references: apply only the ones actually present and relevant to the task. A missing companion never requires installing the whole set, inventing its contents, or stopping work covered by this guide. Paths and project names in examples are templates, not facts about the current repository.

Honor the agent runtime's permissions and higher-priority instructions. Within project guidance, follow the explicit task, then applicable repository instructions and established conventions, then documentation for the installed version, then this guide's defaults. Existing insecure behavior is not a convention to preserve: fix it when in scope and report other findings. New-project defaults never authorize a dependency upgrade, migration, or restructuring of an existing project.

When guides are combined, topic ownership is: `COMMITS.md` for commit messages and Git actions; `CONTEXT.md` for `AGENTS.md`, `CLAUDE.md`, and project learning; `STRUCTURE.md` for file placement and build wiring; `JAVA.md` for backend implementation; `UI.md` for shadcn composition and APIs; `RESPONSIVENESS.md` for shared responsive/accessibility/PWA criteria. Product requirements decide whether PWA/offline is needed. Each guide retains the essentials needed when its companions are absent.

Mandatory rules apply within the requested scope. Validate changed behavior and concrete risks; use a full application audit only for a new application, a cross-cutting change, or an explicitly requested audit. Stop when the applicable acceptance criteria pass, or report a concrete environment blocker and the pending checks. Never claim an unexecuted check passed.

Deliver one report: outcome, relevant verification, and material limitations. Include a tree only for structural work and a suggested commit message when useful or requested; neither must be the last line. If the project learning workflow is active, put its short `LEARNING:` status last. Writing a suggested message never authorizes a commit or push.

---

**Version:** 1.1 — reviewed 2026-09-13
**Scope:** where files and directories go, and how backend and frontend builds are wired together, for four project shapes: plain Java, Spring Boot API, Spring Boot + Thymeleaf, and Spring Boot + React/shadcn. Covers repository root, `src/` layouts, resources, tests, frontend project structure, build integration, tooling files, and where the AI-agent guides themselves live.
**Companion guides (independent, each usable alone):** `JAVA.md` (how to write the Java/Spring code inside the packages), `UI.md` (shadcn/ui components and CLI), `RESPONSIVENESS.md` (responsive, app-like and PWA behaviour), `COMMITS.md` (commit messages), `CONTEXT.md` (shared agent instructions and project learning). Each is optional. This guide covers placement and wiring and includes standalone essentials for agent entry files.
**Language policy:** identifiers, file names, directory names and configuration keys in English; comments inside code samples in Brazilian Portuguese (pt-BR); `README.md` and ADRs written in pt-BR.

> **How this file is organized.** Part 0 (how to use, decision table) and Part 1 (repository root) apply to every project. Parts 2–5 are one per project shape; load only the one that matches. Parts 6–8 (naming, agent files, anti-patterns) are cross-cutting. Part 9 is the checklist, Part 10 the sources. If your tool loads instructions on demand (Claude Code `.claude/rules/` with `paths:`, Cursor rules with globs), keep a short pointer in `AGENTS.md` and read Parts 0, 1, 6 and 7 when relevant; load Parts 2–5 only for the matching project shape.

---

## Identification sheet for this version of the guide

The table preserves the original author's research snapshot of 2026-09-12; it is not a claim that revision 1.1 re-executed every generator, command, or build. Installed manifests/lockfiles and current official compatibility documentation govern adoption. The revision adds the agent-entry and scope corrections described in Part 10; do not treat dated patch versions as perpetual requirements.

| Item | Verified value (2026-09-12) | Source |
| --- | --- | --- |
| Spring Boot project reported generated in the original research by Spring Initializr (`demo.zip`) | Spring Boot **4.1.1**, Java **25**, Maven wrapper **3.9.16** (`distributionType=only-script`, jar not committed); starters `spring-boot-starter-webmvc`, `-data-jpa`, `-flyway` + `flyway-database-postgresql`, `-security`, `-actuator`, and the per-technology `*-test` starters; `spring-boot-devtools`, `spring-boot-docker-compose`, `lombok`; `compose.yaml` with `postgres:latest`; pre-created `src/main/resources/{db/migration,static,templates}` and `application.properties`; `.gitignore` ignores `HELP.md`, `target/`, `.mvn/wrapper/maven-wrapper.jar`, IDE folders | `pom.xml`, `HELP.md`, `.gitignore` inside the zip |
| `spring-boot-starter-thymeleaf` / `spring-boot-starter-thymeleaf-test` | Both exist for Boot 4.1 (`starter/` directory of the `4.1.x` branch, commit `885720a`, 2026-09-11; the starter depends on `spring-boot-thymeleaf`) | Maven Central; spring-boot repository |
| Spring sources behind `SpaConfig` (Part 5.6) | Spring Framework `7.0.x` (commit `bcd78a2`, 2026-09-10): `PathResourceResolver.getResource(String, Resource)` is `protected @Nullable`, returns `null` when the file is missing or fails `checkResource`; `ResourceHttpRequestHandler` strips the leading slash (`ResourceHandlerUtils.normalizeInputPath`) and throws `NoResourceFoundException` (→ 404) when a resolver returns `null`; `ResourceHandlerRegistration.setCacheControl`/`resourceChain(boolean)`/`addResolver` and `CacheControl.maxAge(Duration)`, `noCache()`, `immutable()` exist. Spring Boot `4.1.x` (commit `885720a`): `WebMvcAutoConfiguration.addResourceHandler` skips a pattern when `registry.hasMappingForPattern(pattern)` is already true, so a custom `/**` handler replaces the default one | spring-framework and spring-boot repositories (sparse clones) |
| Node.js | Newest **24.x = 24.21.0** (2026-09-10); v24 "Krypton" is the Active LTS line until 2026-10-20 (then Maintenance, EOL 2028-04-30); v26 is Current and enters LTS in October 2026; from v27 (April 2027) Node ships one major per year, every release LTS. `@types/node` newest 24.x = 24.13.4 | npm `node` package mirror (`registry.npmjs.org/node`), `nodejs.org/en/about/previous-releases`, nodejs.org "Evolving the Node.js Release Schedule" |
| shadcn/ui repository | commit `2b3e6d4` (2026-09-12); CLI **4.21.0**; official templates `templates/vite-app`, `vite-monorepo`, `start-app`, `react-router-app`, `next-app` (+ monorepo variants). `init --template vite` clones `templates/vite-app` from GitHub and adds `components.json`, `src/lib/utils.ts`, `button` and `src/components/component-example.tsx` | `packages/shadcn/src/templates/{vite,create-template}.ts` |
| `templates/vite-app` (what `init -t vite` produces) | `vite ^8`, `react`/`react-dom ^19.2.8`, `typescript ~6`, `eslint ^10` + `typescript-eslint ^8` + `eslint-plugin-react-hooks ^7.1.1` + `eslint-plugin-react-refresh`, `@vitejs/plugin-react ^6`, `tailwindcss ^4` via `@tailwindcss/vite`, `prettier ^3.9.6` + `prettier-plugin-tailwindcss ^0.8.1`, `@types/node ^20`. Files: `index.html`, `vite.config.ts`, `tsconfig.json` + `tsconfig.app.json` + `tsconfig.node.json`, `eslint.config.js`, `.prettierrc`, `.gitignore`, `pnpm-workspace.yaml` (only `allowBuilds`), `public/`, `src/{main.tsx,App.tsx,index.css,assets/,components/theme-provider.tsx}`. Scripts: `dev`, `build` (`tsc -b && vite build`), `lint`, `format`, `typecheck`, `preview` | template files |
| `templates/vite-monorepo` | `pnpm@10.33.4`, `turbo ^2.9.18`, `engines.node >=20`, `apps/web` + `packages/ui`, aliases `ui: @workspace/ui/components` | template files |
| npm `latest` on the research date | `vite` 8.3.0 (Node `^20.19 ‖ >=22.12`), `react` 19.3.0, `typescript` 7.0.2, `eslint` 10.10.0, `vitest` 5.0.0 (Node `^22.12 ‖ ^24 ‖ >=26`), `@playwright/test` 1.63.0, `@vitejs/plugin-react` 6.1.1, `@tanstack/react-router` 1.170.35, `@tanstack/router-plugin` 1.168.37, `@tanstack/react-query` 5.102.8, `react-router` 8.3.1, `zod` 4.6.2, `react-hook-form` 7.88.0, `msw` 2.15.0, `openapi-typescript` 7.13.0, `openapi-fetch` 0.17.0, `orval` 8.32.0, `@biomejs/biome` 2.5.13, `pnpm` 12.4.1, `shadcn` 4.21.0 (Node `>=20.18.1`), `cn` 0.3.0, `tailwindcss` 4.3.3, `vite-plugin-pwa` 1.3.0, `@wim.deblauwe/vite-plugin-spring-boot` 1.1.0 | `registry.npmjs.org` |
| `com.github.eirslett:frontend-maven-plugin` | **2.0.2** (2026-07-29). Requires Maven 3.6 + Java 17; Maven 4 reported to work. Goals: `install-node-and-npm`, `install-node-and-pnpm`, `install-node-and-corepack`, `install-bun`, `npm`, `pnpm`, `corepack`, `bun`, `npx`. Options: `workingDirectory` (default: dir of `pom.xml`), `installDirectory`, skip flags (`-Dskip.installnodepnpm`, `-Dskip.pnpm`, …) | mvnrepository + project README |
| `io.github.wimdeblauwe:vite-spring-boot-thymeleaf` / `-jte` | **2.0.1** for Boot 4.x (1.0.0 for 3.5). Tags `<vite:client>`, `<vite:react-refresh>`, `<vite:vite>` + `<vite:entry>`; properties `vite.mode` (`dev`/`build`), `vite.manifest` (`classpath:/static/.vite/manifest.json`), `vite.vite-entries-prefix` (`static`) | project README |
| `io.github.wimdeblauwe:htmx-spring-boot` / `-thymeleaf` | **5.1.0** (2026-03-14) for Boot ≥ 4.0.3; 4.0.3 for Boot 3.4/3.5 | project README compatibility table; mvnrepository |
| `ttcli` (Taming Thymeleaf CLI, reference generator) | **1.12.4** (2026-06-28); repo commit `c70d83c` (2026-09-02). Generates `templates/index.html` + `templates/layout/main.html`, `application-local.properties` with template cache off, Vite config with `root: src/main/resources`, entry `/static/css/application.css`, `outDir: target/classes/static`, proxy to `:8080`; pins `frontend-maven-plugin` 1.15.4 | repository sources |
| Spring Boot SPA fallback (`index.html` for client routes) | **Not built in.** Issue spring-projects/spring-boot#27554 is still open (General Backlog) | GitHub |
| Claude Code memory system | `./CLAUDE.md` or `./.claude/CLAUDE.md`, target **< 200 lines**; `CLAUDE.local.md` (gitignored); `.claude/rules/*.md` discovered recursively, optional `paths:` frontmatter for path-scoped loading; `@path` imports load at launch (do not reduce context); skills load on demand; block-level HTML comments are stripped; nested `CLAUDE.md` in subdirectories load when Claude reads files there | `code.claude.com/docs/en/memory` (fetched 2026-09-12) |

Discrepancies and unverified items: Part 10.2.

---

## 0. How to use this guide

### 0.1 Rule classification

- **[REQUIRED]** — cannot be violated. If an exception is unavoidable, state it in one sentence in the final report.
- **[RECOMMENDED]** — the default. Replace it only for a concrete reason, which must be stated.
- **[CONDITIONAL]** — applies only when the stated condition holds (a multi-module build, a second frontend app, Thymeleaf with Vite, …).

### 0.2 Precedence (highest wins)

1. An explicit instruction from the user in the current task.
2. Repository instructions (`AGENTS.md` or the agent's entry file) and conventions already present in the repository (existing directory names, build tool, package manager, generator output). **Consistency with the existing tree beats this guide.**
3. Output of the official generators (Spring Initializr, `npx shadcn@latest init`, `ttcli init`). This guide describes what to *add* and *rename* after generation, never a hand-made replacement for the generator.
4. Documentation for the installed version of each tool.
5. This guide.
6. General community practice.

Where this guide and a companion guide touch the same subject, the companion guide owns the *content* and this guide owns the *location*: `JAVA.md` Part 5.1 decides how classes are grouped inside a feature package; this guide decides where `src/main/java` sits relative to `frontend/`.

### 0.3 Agent working rules (non-negotiable)

- **Never restructure an existing project unasked.** Moving files, renaming directories, switching Maven ↔ Gradle, npm ↔ pnpm, or `src/main/frontend` ↔ `frontend/` is a task in itself. If the layout deviates from this guide, say so in one sentence and keep working inside the existing layout.
- **Generate first, then adapt.** For a new project run the official generator (Part 3.1 / 4.1 / 5.2), read what it produced, then apply this guide. Do not hand-write `pom.xml`, `vite.config.ts` or `components.json` from memory.
- **Never invent plugin, tool or Node versions.** Use the ones in the identification sheet or read them from the project (`package.json`, `pom.xml`, `.nvmrc`). If a version is unknown, say so and look it up.
- **Never commit generated output** (`target/`, `build/`, `dist/`, `node/`, `node_modules/`, `.vite/`, `routeTree.gen.ts` is the one exception — Part 5.4).
- **Never put source files where a build tool would treat them as output** (no hand-written files under `target/classes/static`, no Vite `outDir` pointing into `src/`).
- **Report the tree.** When creating or noticeably extending a project, include the relevant resulting tree in the single delivery report (`tree -a -L 3 -I 'node_modules|target|dist|.git'`), so the human can see the layout without opening the IDE.
- **One secret policy.** `.env`, `*.local`, `application-local.yaml` with real credentials, keystores and `settings.xml` with tokens never enter the repository; `.env.example` and `compose.yaml` with throw-away dev credentials do.

### 0.4 Which layout? Decision table

| Project shape | Use when | Root layout (summary) | Part |
| --- | --- | --- | --- |
| **A. Plain Java** (library, CLI, batch, no Spring) | No web layer; published artifact or standalone jar | Maven/Gradle standard layout, single module; `module-info.java` only for libraries | 2 |
| **B. Spring Boot API** (REST only, consumer is a separate client) | Mobile app, third-party clients, or the frontend lives in another repository | Initializr layout + `compose.yaml`, `Dockerfile`, `.github/`, `docs/` | 3 |
| **C. Spring Boot + Thymeleaf** (server-rendered pages, optionally htmx/Tailwind) | Internal tools, CRUD back-offices, SEO pages, small teams without a frontend specialist | Layout B + `templates/`, `static/`; Vite at the root only in Vite mode | 4 |
| **D. Spring Boot + React + shadcn** (SPA) | Rich interactive UI, PWA, app-like experience (`RESPONSIVENESS.md`) | `backend/` + `frontend/` siblings (D1, separate deployables) or a Maven multi-module that bundles the SPA into the jar (D2) | 5 |
| **E. Monorepo with several frontends** | More than one web app sharing the design system | pnpm workspace `apps/*` + `packages/ui` (official shadcn monorepo template) beside `backend/` | 5.8 |

Ask one precise question before generating when the shape is ambiguous ("Will the UI be React or Thymeleaf?", "Single deployable jar or separate frontend deploy?"). Defaults if the user has no preference: **B** for an API, **C** for a small internal tool, **D1** for anything that needs the app-like experience of `RESPONSIVENESS.md`.

### 0.5 Existing project: audit before touching anything

1. `git ls-files | head -100` and `tree -a -L 2 -I 'node_modules|target|dist|.git'` — learn the real layout.
2. Detect the build tool (`pom.xml`/`mvnw` vs `build.gradle.kts`/`gradlew`) and the package manager from the lockfile (`pnpm-lock.yaml`, `package-lock.json`, `yarn.lock`, `bun.lock`). Never introduce a second one.
3. Detect generators: `components.json` (shadcn), `vite.config.*`, `react-router.config.ts`, `src/routeTree.gen.ts` (TanStack Router), `vite.mode` in properties (vite-spring-boot), `templates/layout/main.html` (ttcli), `<modules>` in `pom.xml`.
4. Read applicable `AGENTS.md`, `CLAUDE.md`, `LEARNING.md`, `README.md`, `CONTRIBUTING.md`, `docs/adr/` — existing decisions win (0.2).
5. Only then place new files, following the pattern already used for their siblings.

---

## 1. Repository root

### 1.1 Files every repository has [REQUIRED]

| File | Rule |
| --- | --- |
| `README.md` | pt-BR. Sections: what the system is, stack (with versions), prerequisites (JDK, Docker, Node/pnpm), how to run backend/frontend/database locally, how to run tests, deploy overview, links to the guides in `docs/guides/`. Keep it current — it is the human entry point; `AGENTS.md` is the shared agent entry point and `CLAUDE.md` is the Claude bridge. |
| `.gitignore` | Union of the Initializr file, the shadcn template file and Part 1.4. |
| `.gitattributes` | `* text=auto eol=lf` plus the exceptions in Part 1.4. Prevents CRLF churn between Windows and Linux. |
| `.editorconfig` | Part 1.4. Java 4 spaces / 120 columns (`JAVA.md` 2.7); everything web 2 spaces. |
| `AGENTS.md` | Shared project instructions, present even when starting with Claude Code only (Part 7.2). |
| `CLAUDE.md` | Short Claude bridge importing `AGENTS.md` and `LEARNING.md` (Part 7.3). |
| `LEARNING.md` | Short project learning index; detailed topics are created only when useful (Part 7.4). |
| Build wrapper | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` **or** `gradlew`, `gradlew.bat`, `gradle/wrapper/`. Committed; never require a globally installed Maven/Gradle. In layout D1 the wrapper lives in `backend/`. |
| `LICENSE` | Only if the repository is public or shared outside the organisation. |

### 1.2 Optional root files, by need [CONDITIONAL]

| File / dir | Add when |
| --- | --- |
| `compose.yaml` | The app needs infrastructure locally (PostgreSQL, Redis, Kafka, Keycloak, OTel collector). Canonical name is `compose.yaml` (Compose Specification); never `docker-compose.yml`. `spring-boot-docker-compose` looks for it in the working directory — in layout D1 set `spring.docker.compose.file=../compose.yaml` in `backend/src/main/resources/application-local.yaml` so the file can stay at the root and serve both halves. |
| `Dockerfile`, `.dockerignore` | The backend is shipped as an image built from a Dockerfile (`JAVA.md` 15.7) instead of Buildpacks. In layout D1 each half has its own `Dockerfile`. |
| `.env.example` | Any variable read from the environment. Committed, with placeholder values and a comment per variable. The real `.env` is ignored. |
| `CONTRIBUTING.md` | More than one contributor. Points to `docs/guides/` and the PR process. |
| `CHANGELOG.md` | Versioned releases (Keep a Changelog format, pt-BR). |
| `SECURITY.md` | Public repository. |
| `renovate.json` or `.github/dependabot.yml` | Always for anything that will live longer than a prototype (`JAVA.md` 11.2 asks for automated updates). One of the two, not both. |
| `.sdkmanrc` / `mise.toml` / `.tool-versions` | Pin the JDK for humans (`java=25-tem` style). Optional; the Maven `<java.version>`/toolchain is the source of truth for the build. |
| `.nvmrc` or `.node-version` | Any frontend. Contains the exact Node version (`24.21.0` — newest 24.x LTS on the research date; bump within the 24 line freely, move to 26 only after it enters LTS in October 2026). Must agree with `package.json#engines.node`, the `node:` image tag in the frontend `Dockerfile` and, in layout D2, with the Maven property `node.version`. |
| `lombok.config` | Any Java module that uses Lombok (`JAVA.md` 4.3). Lives next to the `pom.xml` that compiles the Java code (`backend/lombok.config` in D layouts). |
| `docs/` | Part 1.5. Always present when the guides are used. |
| `.github/` | CI (`workflows/`), `CODEOWNERS`, `pull_request_template.md`, `dependabot.yml`. Part 1.6. |
| `scripts/` | Repeatable developer scripts. Prefer Java 25 compact source files (`scripts/ImportSeed.java`, run with `java scripts/ImportSeed.java`) over shell when the logic is not trivial; shell for one-liners. |
| `config/` | **Do not create.** Spring Boot loads `./config/application*.yaml` from the working directory at runtime, so a `config/` folder at the root silently changes behaviour when the app is started from there. Use `deploy/` for infrastructure config. |
| `deploy/` | Kubernetes manifests, Helm chart, nginx/Caddy config for the frontend image, Terraform. Never application secrets. |

### 1.3 Never committed [REQUIRED]

`target/`, `build/`, `dist/`, `out/`, `node/` (frontend-maven-plugin install dir), `node_modules/`, `.vite/`, `*.log`, `.env`, `.env.*.local`, `*.local` (Vite convention), `CLAUDE.local.md`, `.claude/settings.local.json`, `.idea/`, `*.iml`, `.vscode/` (except `extensions.json`), `.DS_Store`, keystores (`*.p12`, `*.jks`), `HELP.md` (Initializr boilerplate; its `.gitignore` already excludes it), `.mvn/wrapper/maven-wrapper.jar` (the `only-script` wrapper downloads it), coverage and test reports, Playwright `test-results/` and `playwright-report/`.

### 1.4 Reference contents

```ini
# path: .editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 2

[*.java]
indent_size = 4
max_line_length = 120

[*.{kt,kts}]
indent_size = 4

[*.md]
trim_trailing_whitespace = false

[*.{cmd,bat}]
end_of_line = crlf

[Makefile]
indent_style = tab
```

```gitattributes
# path: .gitattributes
* text=auto eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
mvnw text eol=lf
gradlew text eol=lf
*.png binary
*.jpg binary
*.woff2 binary
*.jar binary
# Arquivos gerados: não aparecem em diffs de review no GitHub
pnpm-lock.yaml linguist-generated=true
**/routeTree.gen.ts linguist-generated=true
```

```gitignore
# path: .gitignore  (acrescentar ao arquivo gerado pelo Initializr e ao do template do shadcn)
# Build
target/
build/
dist/
out/
!**/src/main/**/target/
!**/src/test/**/target/
# Node
node/
node_modules/
.vite/
*.tsbuildinfo
# Ambiente e segredos
.env
.env.*.local
*.local
*.p12
*.jks
# Agentes
CLAUDE.local.md
.claude/settings.local.json
# Testes e relatórios
test-results/
playwright-report/
coverage/
# IDE e SO
.idea/
*.iml
.vscode/*
!.vscode/extensions.json
.DS_Store
```

### 1.5 `docs/` layout [RECOMMENDED]

```text
docs/
├── guides/                    # os guias para agentes, versionados junto com o código
│   ├── JAVA.md
│   ├── UI.md
│   ├── RESPONSIVENESS.md
│   ├── COMMITS.md
│   ├── STRUCTURE.md           # este arquivo
│   └── CONTEXT.md             # entrada dos agentes e aprendizado; incluir apenas guias selecionados
├── adr/                       # Architecture Decision Records (formato MADR, pt-BR)
│   ├── 0001-registrar-decisoes-de-arquitetura.md
│   ├── 0002-spa-react-em-vez-de-thymeleaf.md
│   └── 0003-deploy-do-frontend-separado-do-backend.md
├── api/                       # [CONDITIONAL] contrato OpenAPI exportado (Part 5.7)
│   └── openapi.json
└── images/                    # diagramas referenciados pelo README/ADRs
```

- ADR file name: `NNNN-<slug-em-kebab-case>.md`, sequential, never renumbered. Content in pt-BR: contexto, decisão, consequências, alternativas consideradas. Every choice this guide leaves open (D1 vs D2, Thymeleaf vs React, Modulith vs multi-module) gets an ADR.
- The guides are copied, not symlinked, into `docs/guides/` so the repository is self-contained. When a guide is updated centrally, copy the new version and mention it in the commit (`docs(guides): atualiza JAVA.md para a versão 1.1`).

### 1.6 `.github/` layout [CONDITIONAL — GitHub]

```text
.github/
├── workflows/
│   ├── backend.yml            # ./mvnw -B verify (Testcontainers precisa de Docker no runner)
│   ├── frontend.yml           # pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build
│   └── release.yml            # [CONDITIONAL] build de imagem, tag, publicação
├── dependabot.yml             # ou renovate.json na raiz — nunca os dois
├── CODEOWNERS
└── pull_request_template.md   # pt-BR: o que mudou, como testar, checklist dos guias
```

Use `on.push.paths` / `on.pull_request.paths` filters (`backend/**`, `frontend/**`) so a frontend-only change does not run the Maven build. Pipeline contents for the backend: `JAVA.md` 11.5. For the frontend the minimum is lint + typecheck + unit tests + build; Playwright (`RESPONSIVENESS.md` §59) runs in its own job when the project has an `e2e/` directory.

---

## 2. Layout A — Plain Java (library, CLI, batch; no Spring)

### 2.1 Maven, single module [RECOMMENDED default]

The Maven Standard Directory Layout is the only layout used; build tools, IDEs and every companion guide assume it.

```text
acme-cpf-validator/
├── .editorconfig  .gitattributes  .gitignore  README.md  AGENTS.md  CLAUDE.md  LEARNING.md  LICENSE
├── mvnw  mvnw.cmd  .mvn/wrapper/maven-wrapper.properties
├── lombok.config                                # [CONDITIONAL] se usa Lombok
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── module-info.java                 # [CONDITIONAL] só bibliotecas (2.3)
    │   │   └── com/acme/cpfvalidator/
    │   │       ├── package-info.java            # @NullMarked + JavaDoc do pacote (JAVA.md 15.1)
    │   │       ├── CpfValidator.java            # API pública
    │   │       └── internal/                    # detalhes; package-private ou não exportado no module-info
    │   └── resources/
    │       └── META-INF/                        # services/ (ServiceLoader), MANIFEST.MF só se necessário
    └── test/
        ├── java/com/acme/cpfvalidator/          # espelha o pacote; <Classe>Test (JAVA.md 2.2)
        │   ├── CpfValidatorTest.java
        │   └── support/                         # builders e dados de teste (JAVA.md 10.2)
        └── resources/                           # fixtures; nada de código
```

Rules:
- The root package equals `groupId + artifactId` without hyphens (`com.acme` + `cpf-validator` → `com.acme.cpfvalidator`). One root package per module; everything else is a sub-package of it.
- Integration tests stay in `src/test/java` named `*IT` and run by Failsafe (`JAVA.md` 10.2); do not create `src/it`, `src/integration-test` or a second test source set in Maven.
- Executable jars (CLI) use `maven-shade-plugin` or `maven-assembly-plugin` with `Main-Class` in the manifest; the entry class is `com.acme.<app>.Main` (a class named `Main` in the root package is the accepted exception to the "noun" naming rule of `JAVA.md` 2.2).
- Fill `<name>`, `<description>` and `<url>` in `pom.xml`. If the parent is `spring-boot-starter-parent`, keep the empty `<licenses/>`, `<developers/>`, `<scm/>` overrides the Initializr generates (they stop unwanted inheritance from the parent — `HELP.md` explains this) or fill them with real values; never delete them blindly.

### 2.2 Gradle (Kotlin DSL) equivalent [CONDITIONAL — the team already uses Gradle]

```text
acme-cpf-validator/
├── settings.gradle.kts          # rootProject.name, include(...) para multi-projeto
├── build.gradle.kts             # plugins { java; `java-library` }, toolchain Java 25
├── gradle.properties            # org.gradle.caching=true, org.gradle.configuration-cache=true
├── gradle/
│   ├── libs.versions.toml       # version catalog — única fonte de versões
│   └── wrapper/gradle-wrapper.{jar,properties}
├── gradlew  gradlew.bat
└── src/main|test/…              # idêntico ao Maven
```

- Gradle 9 with the Kotlin DSL and a **version catalog** (`gradle/libs.versions.toml`); versions never appear inline in `build.gradle.kts`.
- `buildSrc/` or an included `build-logic/` project for convention plugins once there are two or more modules with the same configuration; never copy-paste plugin blocks between modules.
- Same source layout as Maven; Spring Boot support via `id("org.springframework.boot")` + `id("io.spring.dependency-management")` or the `platform()` BOM (`JAVA.md` 11.2).

### 2.3 `module-info.java` (JPMS) [CONDITIONAL]

- **Libraries** published for others: add `module-info.java` at `src/main/java`, export only the API packages, `requires` the minimum. Name the module like the root package (`com.acme.cpfvalidator`).
- **Applications** (CLI, batch, every Spring Boot app): stay on the classpath; do not add `module-info.java`. Spring Boot fat jars and most Spring ecosystem libraries are not modular, and the module path adds friction without benefit here.
- Tests of a modular library live in the same `src/test/java` package tree; Surefire patches the module automatically. Do not add a test `module-info.java`.

### 2.4 Multi-module Maven [CONDITIONAL — real need only]

Create a multi-module build only when at least one of these is true: (a) more than one deployable is produced from one repository, (b) a library is published separately from the application, (c) a frontend must be bundled into the backend jar (layout D2, Part 5.6). Splitting one Spring Boot application into `domain`/`application`/`infrastructure` jars is **not** a reason — `JAVA.md` 5.1/5.3 achieve that separation with packages and ArchUnit, and Spring Modulith formalises it without extra jars.

```text
acme-billing/
├── pom.xml                      # <packaging>pom</packaging>, <modules>, dependencyManagement compartilhado
├── mvnw  .mvn/  .editorconfig  .gitattributes  .gitignore  README.md  AGENTS.md  CLAUDE.md  LEARNING.md  compose.yaml
├── billing-api/                 # contratos públicos (DTOs, eventos) publicados para outros serviços
│   └── pom.xml  src/…
├── billing-app/                 # a aplicação Spring Boot (única com spring-boot-maven-plugin)
│   └── pom.xml  lombok.config  src/…
└── billing-batch/               # segundo deployable, se existir
    └── pom.xml  src/…
```

Rules:
- Flat module directories (no nesting); directory name = artifactId; artifactIds prefixed with the product name (`billing-app`), root packages `com.acme.billing.<module>`.
- Versions managed once in the root `<dependencyManagement>`/`<properties>` (or a `billing-bom` module when third parties consume the artifacts). Child poms declare dependencies without versions.
- Only the deployable module applies `spring-boot-maven-plugin`; library modules never have `repackage`.
- Each module has its own `src/test`; cross-module test utilities go in a `billing-test-support` module with `<scope>test</scope>` consumers, never in `src/main` of a production module.

---

## 3. Layout B — Spring Boot backend (base for C and D)

### 3.1 Generate, then adjust

For a new project using the selected Boot 4 baseline, generate with Spring Initializr (`start.spring.io` or `curl https://start.spring.io/starter.zip …`) selecting Boot 4.1.x, Java 25, Maven, packaging jar, and the necessary starters (`JAVA.md` 1.3 when present; otherwise select actual requirements in Initializr). Then, **in this order**:

1. Rename `application.properties` → `application.yaml` (the whole project uses YAML, `JAVA.md` 11.1); one format only.
2. Delete unused generated directories: `src/main/resources/templates/` and `static/` for a pure API (Git does not track empty directories anyway; `.gitkeep` only where the directory is meaningful, e.g. `db/migration/` before the first migration).
3. Fill `<name>` and `<description>` in `pom.xml`; keep the empty overrides (2.1).
4. Add the applicable root files, `AGENTS.md`, `CLAUDE.md`, `LEARNING.md`, and the selected guides (Parts 1 and 7).
5. Pin the PostgreSQL image in `compose.yaml` (`postgres:18-alpine`, `JAVA.md` 15.7) — the Initializr writes `postgres:latest`, which is not reproducible.
6. Create the base package structure of `JAVA.md` 5.1 (`shared/` + first feature) and `TestcontainersConfiguration` (`JAVA.md` 10.4).

### 3.2 Reference tree (Boot 4.1, single module)

```text
billing-service/
├── .editorconfig  .gitattributes  .gitignore  README.md  AGENTS.md  CLAUDE.md  LEARNING.md
├── .claude/                                   # Part 7
├── .github/workflows/                         # Part 1.6
├── docs/                                      # Part 1.5
├── mvnw  mvnw.cmd  .mvn/wrapper/maven-wrapper.properties
├── pom.xml
├── lombok.config
├── compose.yaml                               # PostgreSQL etc. para desenvolvimento (JAVA.md 15.7)
├── Dockerfile  .dockerignore                  # [CONDITIONAL] (JAVA.md 11.4/15.7)
└── src/
    ├── main/
    │   ├── java/com/acme/billing/
    │   │   ├── BillingApplication.java        # única classe no pacote raiz além de package-info
    │   │   ├── package-info.java
    │   │   ├── shared/                        # config/, error/, security/, persistence/, web/ (JAVA.md 5.1)
    │   │   ├── customer/                      # uma pasta por feature; conteúdo definido em JAVA.md 5.1–5.2
    │   │   ├── invoice/
    │   │   └── payment/
    │   └── resources/
    │       ├── application.yaml               # defaults + estrutura; sem segredos
    │       ├── application-local.yaml         # máquina do dev: compose, logs legíveis, devtools
    │       ├── db/migration/                  # V<yyyyMMddHHmm>__<snake>.sql, R__<snake>.sql (JAVA.md 7.7)
    │       ├── messages.properties            # mensagens ao usuário; pt-BR default (JAVA.md 2.4)
    │       ├── messages_pt_BR.properties
    │       ├── messages_en.properties
    │       ├── ValidationMessages.properties  # [CONDITIONAL] mensagens de Bean Validation customizadas
    │       ├── META-INF/
    │       │   └── additional-spring-configuration-metadata.json   # [CONDITIONAL] metadata manual de propriedades
    │       └── static/  templates/            # só nos layouts C/D2 — apagar no layout B
    └── test/
        ├── java/com/acme/billing/
        │   ├── TestcontainersConfiguration.java   # container compartilhado (JAVA.md 10.4)
        │   ├── TestBillingApplication.java        # main de dev com Testcontainers (JAVA.md 10.4)
        │   ├── ArchitectureTest.java              # ArchUnit (JAVA.md 10.6)
        │   ├── support/                           # builders, object mothers, JsonAssertions
        │   ├── customer/                          # espelha main: CustomerServiceTest, CustomerControllerTest, CustomerApiIT
        │   └── invoice/
        └── resources/
            ├── application-test.yaml              # [CONDITIONAL] só se os testes precisam de overrides
            └── sql/                               # scripts para @Sql, nomeados <feature>/<cenario>.sql
```

### 3.3 `src/main/resources` rules

| Path | Rule |
| --- | --- |
| `application.yaml` | Base configuration for every environment. Structure and defaults only; environment values via `${ENV_VAR}` placeholders. Profiles as documents separated by `---` with `spring.config.activate.on-profile` are allowed for *small* differences; a separate `application-<profile>.yaml` when the profile has more than ~10 keys. |
| `application-local.yaml` | Committed. Developer machine: Docker Compose wiring, human-readable logs, `spring.jpa.show-sql` if wanted, devtools. No real secrets — throw-away dev credentials matching `compose.yaml` only. |
| `application-prod.yaml` | **Do not create** (`JAVA.md` 11.1): production values come from the platform (env vars, mounted secrets, config server). |
| `db/migration/` | Flyway only. Flat directory; file names per `JAVA.md` 2.2. Never `schema.sql`/`data.sql`. Spring Modulith: `db/migration/<module>/` with `spring.flyway.locations` listing each module. |
| `static/` | Layout B: absent. Layout C: hand-written assets or Vite output (Part 4.3). Layout D2: never (the SPA arrives through the frontend jar, Part 5.6). Spring also serves `public/`, `resources/`, `META-INF/resources/` — do not create those directories in `src/main/resources`; `static/` is the only one used. |
| `templates/` | Layout C only. |
| `messages*.properties` | UTF-8 (Boot reads them as UTF-8). Keys namespaced by feature or error slug (`error.invoice.not-found.title`). |
| `logback-spring.xml` | Only when structured logging via `logging.structured.format.*` (`JAVA.md` 11.1) is not enough. Prefer properties. |
| `banner.txt` | Not used (`spring.main.banner-mode: off`). |
| `META-INF/spring/…AutoConfiguration.imports` | Only in a library module that ships auto-configuration; never in an application. |
| Certificates, keystores | Never on the classpath. Mounted at runtime and referenced by path or `spring.ssl.bundle.*`. |

### 3.4 `src/test` rules

- Test packages mirror `src/main/java` exactly, so package-private members are reachable and `ArchUnit` slices align.
- Shared infrastructure lives in the root test package: `TestcontainersConfiguration`, `TestBillingApplication`, `ArchitectureTest`.
- Test data builders in `…/support/` (`JAVA.md` 10.2); `@Sql` scripts under `src/test/resources/sql/<feature>/`.
- `application-test.yaml` only when a test needs an override that cannot be expressed with `@TestPropertySource`/`@DynamicPropertySource`; otherwise tests run with the same `application.yaml` as production plus the `@ServiceConnection` container.
- No test-only code in `src/main`; no `@Profile("test")` beans in production classes.

### 3.5 Package placement (summary; content rules in `JAVA.md` 5)

- Base package = package of the `@SpringBootApplication` class = `groupId + product` (`com.acme.billing`). Everything scanned is below it; nothing above it.
- One directory per feature at the first level below the base package; `shared/` for cross-cutting code (small!); no `controller/`, `service/`, `repository/`, `model/`, `util/`, `common/` at the first level.
- `internal/` sub-packages for infrastructure adapters of a feature; `dto/` and `event/` sub-packages for the public contract of a feature.
- Spring Modulith [CONDITIONAL]: identical tree — each first-level package is a module; add `package-info.java` with `@ApplicationModule`, `ModularityTests` in the root test package, and per-module migration directories (3.3).

### 3.6 Container and CI files

- `Dockerfile` at the module root (next to `pom.xml`) following `JAVA.md` 15.7; `.dockerignore` with `target/`, `.git/`, `docs/`, `*.md`, `.claude/`, `node_modules/`.
- Buildpacks alternative needs no file: `./mvnw spring-boot:build-image`.
- `compose.yaml` at the repository root; services named after the technology (`postgres`, `redis`), not after the app.
- CI files in `.github/workflows/` (Part 1.6) or the platform equivalent (`.gitlab-ci.yml` at the root).

---

## 4. Layout C — Spring Boot + Thymeleaf (server-rendered)

Layout B plus a view layer. Everything in Part 3 still applies.

### 4.1 Dependencies and generation

- Starters: `spring-boot-starter-thymeleaf` and, for tests, `spring-boot-starter-thymeleaf-test` (both present in the Boot 4.1.x `starter/` directory; `spring-boot-starter-webmvc-test` + `MockMvcTester` render views, the Thymeleaf test starter adds the Thymeleaf-specific test support).
- Hypermedia: `io.github.wimdeblauwe:htmx-spring-boot-thymeleaf` **5.1.0** (Boot ≥ 4.0.3) when the pages use htmx. It brings `@HxRequest`, `HtmxResponse`, and the `hx:` Thymeleaf dialect. Never 4.0.x on Boot 4.
- htmx / Alpine / Bootstrap without npm: WebJars (`org.webjars.npm:htmx.org`, …) served at `/webjars/**`; `org.webjars:webjars-locator-lite` for version-agnostic URLs [CONDITIONAL].
- Layouts: Thymeleaf 3.1 native fragment layouts (`th:replace="~{layout/main :: layout(...)}"`) [RECOMMENDED] — this is what `ttcli` generates. The Layout Dialect (`nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect`) only when the project already uses it (it is no longer part of the starter).
- Generator: `ttcli init` (Thymeleaf, Boot 4.1, live reload = *Vite* when Tailwind or any npm tooling is wanted, *DevTools* otherwise) is the reference scaffold; Spring Initializr with the Thymeleaf starter is fine for the DevTools-only variant. JTE (`src/main/jte`) is the supported alternative engine in `ttcli`; not covered here.

### 4.2 Templates tree

```text
src/main/resources/templates/
├── layout/
│   └── main.html                 # th:fragment="layout(content)": <head>, nav, <vite:client>, entradas CSS/JS, <main th:replace="${content}">
├── fragments/                    # blocos reutilizados por várias páginas; um arquivo por tema
│   ├── nav.html                  # th:fragment="nav"
│   ├── forms.html                # th:fragment="text-field(field, label)" etc. — inputs com label, erro, aria
│   ├── pagination.html
│   └── flash.html                # mensagens de sucesso/erro (toast/alert)
├── error/                        # resolvidos pelo DefaultErrorViewResolver do Boot
│   ├── 403.html
│   ├── 404.html
│   └── 5xx.html                  # cobre 500–599; nunca mostrar stack trace
├── index.html                    # página inicial (view "index")
└── invoice/                      # UMA PASTA POR FEATURE, mesmo nome do pacote Java com o controller
    ├── list.html                 # view "invoice/list"
    ├── detail.html               # view "invoice/detail"
    ├── form.html                 # view "invoice/form" (criar e editar)
    └── _rows.html                # partial htmx: só fragmentos (th:fragment="rows"), nunca uma página completa
```

Rules:
- View name = path under `templates/` without `.html` (`return "invoice/list"`). Controller and template directories share the feature name, so a template is found by knowing the package.
- File names kebab-case, singular for the feature (matching the package), descriptive for the page (`list`, `detail`, `form`, `confirm-delete`).
- Partials returned to htmx requests start with `_` and contain only fragments; full pages never start with `_`. An `@HxRequest` handler returns `"invoice/_rows :: rows"`.
- Each page declares exactly one layout (`layout/main`) and passes named sections (`content`, `scriptContent`, `title`) — the `ttcli` pattern. No `th:include`, no copy-pasted `<head>`.
- `fragments/` files never contain page-specific markup; a fragment used by one page belongs to that page's file.
- Messages via `#{key}` from `messages*.properties` (`JAVA.md` 2.4); no hard-coded pt-BR strings in templates beyond trivial labels.
- Error templates receive Boot's error attributes; `server.error.include-*` stay `never` in production (`JAVA.md` 11.1).

### 4.3 Static assets: two modes

**Mode 1 — no Node toolchain [RECOMMENDED for small tools].** Hand-written CSS/JS and WebJars.

```text
src/main/resources/static/
├── css/application.css
├── js/application.js             # init do htmx/Alpine, pequenos helpers; sem bundler
├── img/
└── favicon.svg
```

Enable content-hash cache busting with `spring.web.resources.chain.strategy.content.enabled: true` and `paths: /**`; Thymeleaf's `@{/css/application.css}` then emits the hashed URL. `spring.thymeleaf.cache: false` and `spring.web.resources.chain.cache: false` in `application-local.yaml` only. Do not hand-roll a Tailwind pipeline in this mode; if Tailwind is wanted, use Mode 2.

**Mode 2 — Vite (Tailwind v4, TypeScript, SCSS, React islands) [CONDITIONAL].** The `ttcli` "Vite" setup, backed by `vite-spring-boot-thymeleaf` 2.0.1 + `@wim.deblauwe/vite-plugin-spring-boot`.

```text
billing-admin/
├── package.json                  # scripts: dev = vite, build = vite build; devDependencies: vite, @wim.deblauwe/vite-plugin-spring-boot, tailwindcss, @tailwindcss/vite
├── pnpm-lock.yaml
├── vite.config.js                # root: src/main/resources; input: /static/css/application.css (+ /static/js/application.js); outDir: target/classes/static; manifest: true; proxy → :8080
├── pom.xml                       # frontend-maven-plugin (install-node-and-pnpm, pnpm install, pnpm run build) + <resources> com excludes de *.html/*.css/*.js/*.svg
└── src/main/resources/
    ├── static/
    │   ├── css/application.css   # @import "tailwindcss"; entradas do Vite ficam aqui, na árvore de fontes
    │   └── js/application.js
    ├── templates/layout/main.html   # <vite:client>, <vite:vite><vite:entry value="/css/application.css"/></vite:vite>
    ├── application.yaml          # vite.mode: build
    └── application-local.yaml    # vite.mode: dev, spring.thymeleaf.cache: false, spring.web.resources.chain.cache: false
```

How the pieces connect (do not "fix" any of them):
- `vite.config.js` uses `src/main/resources` as `root`; entries are declared once in `rollupOptions.input` and once in the layout (`<vite:entry>`). Every new CSS/JS entry goes in both places.
- Production: `vite build` (run by `frontend-maven-plugin` in `generate-resources`) writes hashed bundles and `.vite/manifest.json` into `target/classes/static/`; the Vite plugin copies `**/*.html` and `**/*.svg` (default `fullCopyFilePaths`) into `target/classes/`. Because of that, the `pom.xml` `<resources>` block **excludes** `*.html`, `*.css`, `*.js`, `*.svg` from Maven's own copy — removing those excludes produces stale duplicates.
- Development: `pnpm dev` starts Vite (HMR on `:5173`, proxying everything that is not an asset to `:8080`) and the app runs with the `local` profile (`vite.mode=dev` makes `<vite:client>` render the HMR client). Open `http://localhost:8080` (or the Vite URL).
- `node/` (plugin install dir) and `node_modules/` are gitignored; `target/classes/static` is never committed. Tailwind v4 detects sources automatically from the working directory; add `@source "../../templates";` (relative to `application.css`) only if classes used in templates are missing from the build.
- `frontend-maven-plugin` version: **2.0.2** (identification sheet); `ttcli` still pins 1.15.4 — bump to 2.0.2 after generation, the goals are unchanged.

### 4.4 Java side of a page

- Page controllers live in the feature package next to the domain, named `<Feature>PageController` (`@Controller`, returns view names) so they are distinguishable from `<Feature>Controller` (`@RestController`) when both exist. Same package rules as `JAVA.md` 5.2: no business logic, one service call, no repositories.
- Form objects and view models are the feature's `dto/` records/classes; entities never reach a template (`JAVA.md` 5.6).
- htmx endpoints are ordinary handler methods with `@HxRequest`; they return partials (`_*.html`). Out-of-band swaps and multiple templates via `HtmxResponse`.
- Tests: `@WebMvcTest(InvoicePageController.class)` + `MockMvcTester` asserting the model and the rendered HTML (`hasViewName("invoice/list")`, body contains); full-page behaviour with Playwright when the project has an `e2e/` directory (`RESPONSIVENESS.md` §59 applies to Thymeleaf apps too).

---

## 5. Layout D — Spring Boot + React + shadcn/ui (SPA)

### 5.1 Two sub-layouts; pick one and record it in an ADR

| | **D1 — separate deployables [RECOMMENDED default]** | **D2 — single jar (SPA bundled)** [CONDITIONAL] |
| --- | --- | --- |
| Tree | `backend/` (Maven project, wrapper inside) + `frontend/` (Vite project); no build tool at the root | Root `pom.xml` (packaging `pom`) with modules `frontend` (thin Maven wrapper around Vite) and `backend` (depends on the frontend jar) |
| Production | Frontend static files behind nginx/Caddy/CDN with `try_files … /index.html`; backend as its own image; same origin via reverse proxy (`/api/` → backend) | One Spring Boot jar/image serves `index.html`, `assets/**` and the API; needs the SPA fallback config (5.6) |
| Choose when | Different release cadences, CDN, PWA at the edge, more than one client of the API, frontend team autonomy | Internal tools, single-instance deploys, ops constrained to "one artifact", no CDN |
| Cost | Two pipelines, two images, reverse proxy config | Slower `./mvnw verify` (Node install + `vite build`), SPA fallback code in the backend, CSRF/session care |

Both sub-layouts share the same `frontend/` internal structure (5.3) and the same dev workflow (5.5).

### 5.2 Bootstrapping a new project [REQUIRED order]

```bash
# 1. Backend (layout B) — gera em ./backend
#    Spring Initializr → Boot 4.1.x, Java 25, Maven, starters de JAVA.md 1.3; descompactar como backend/
#    Aplicar Part 3.1 (renomear para application.yaml, apagar static/ e templates/, lombok.config, ...)

# 2. Frontend — gera em ./frontend a partir do template oficial (UI.md §4.2). Responder "pnpm" ao prompt de package manager.
npx shadcn@latest init --name frontend --template vite --preset <preset-code-ou-nome>

# 3. Dependências de aplicação (versões resolvidas pelo pnpm; não pinar à mão)
cd frontend
pnpm add @tanstack/react-router @tanstack/react-query openapi-fetch zod react-hook-form @hookform/resolvers
pnpm add -D @tanstack/router-plugin @tanstack/react-router-devtools vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event msw openapi-typescript @playwright/test @types/node@^24
#    (@types/node@^24 substitui o ^20 do template para casar com o Node 24 do .nvmrc)

# 4. Componentes conforme a tela pedir (UI.md §1.1 passo 4 e §4.5); nunca add --all
npx shadcn@latest add button card field input select dialog sonner sidebar
```

- `--name frontend` makes the CLI create the `frontend/` directory (the template's `defaultProjectName` is `vite-app`; `--name` overrides it). Do not run `init` inside `backend/`.
- Router [RECOMMENDED]: **TanStack Router** (file-based, type-safe search params, loaders integrated with TanStack Query) for a SPA talking to a Java API. React Router 8 (framework mode) and TanStack Start bring a Node server for SSR — only when SSR is a requirement, and then the "frontend" is itself a server (out of scope here; the shadcn `react-router-app`/`start-app` templates cover the file layout).
- Keep the template's toolchain: ESLint 10 flat config + Prettier with `prettier-plugin-tailwindcss`. Biome [CONDITIONAL] only if the team standardises on it, and then remove ESLint/Prettier — never both.
- TypeScript: the template pins `~6`. `typescript` 7.x is on npm; do not upgrade as a side effect of another task (10.2).

### 5.3 Frontend tree

```text
frontend/
├── .gitignore  .prettierrc  .prettierignore  eslint.config.js      # do template — manter
├── .nvmrc                            # 24.21.0 — igual a engines.node, à tag node: do Dockerfile e, em D2, a node.version do Maven
├── .env.example                      # apenas VITE_*; nunca segredos (tudo aqui vai para o browser)
├── index.html                        # lang="pt-BR", viewport e theme-color (RESPONSIVENESS.md §10, §38)
├── package.json                      # packageManager, engines, scripts (5.4)
├── pnpm-lock.yaml  pnpm-workspace.yaml   # o workspace file só carrega allowBuilds (pnpm ≥ 10 bloqueia scripts de install)
├── components.json                   # shadcn — style/baseColor/cssVariables nunca editados à mão (UI.md §3.3)
├── vite.config.ts                    # plugins: tanstackRouter → react → tailwindcss; alias @; server.proxy; test (Vitest)
├── tsconfig.json  tsconfig.app.json  tsconfig.node.json
├── playwright.config.ts              # [CONDITIONAL] e2e
├── Dockerfile  nginx.conf            # [CONDITIONAL] D1 (5.6)
├── pom.xml                           # [CONDITIONAL] D2 (5.6) — wrapper Maven; devs de frontend o ignoram
├── public/                           # copiado sem processar: favicon.svg, icons/ (PWA), robots.txt
├── e2e/                              # [CONDITIONAL] specs Playwright: <fluxo>.spec.ts (RESPONSIVENESS.md §59)
└── src/
    ├── main.tsx                      # createRoot + <StrictMode><Providers><RouterProvider/></Providers>
    ├── index.css                     # tailwindCssFile do shadcn: tokens, @theme inline, :root, .dark — ÚNICO CSS global
    ├── vite-env.d.ts
    ├── routeTree.gen.ts              # GERADO pelo @tanstack/router-plugin; commitado; nunca editado (marcado linguist-generated)
    ├── app/
    │   ├── providers.tsx             # QueryClientProvider, ThemeProvider, TooltipProvider, Toaster/Sonner
    │   └── router.tsx                # createRouter({ routeTree, context: { queryClient }, defaultPreload: "intent" })
    ├── routes/                       # file-based routes — espelham as URLs (plural, como a API: JAVA.md 6.1)
    │   ├── __root.tsx                # shell: <Outlet/>, devtools em dev, notFoundComponent
    │   ├── index.tsx                 # /
    │   ├── login.tsx                 # /login
    │   ├── _authenticated.tsx        # layout sem segmento de URL: guard de autenticação (beforeLoad)
    │   └── _authenticated/
    │       ├── orders.tsx            # /orders — layout da seção + <Outlet/>
    │       ├── orders.index.tsx      # /orders — lista (validateSearch com zod; loader = ensureQueryData)
    │       └── orders.$orderId.tsx   # /orders/:orderId — detalhe
    ├── features/                     # uma pasta por feature; MESMO NOME do pacote Java (singular, kebab-case)
    │   └── order/
    │       ├── api.ts                # queryOptions()/mutation hooks sobre lib/api-client (nada de fetch solto)
    │       ├── schemas.ts            # zod: formulários e search params
    │       ├── types.ts              # type aliases sobre components["schemas"] do OpenAPI — nunca redigitados
    │       ├── components/           # order-table.tsx, order-form.tsx, order-status-badge.tsx (+ *.test.tsx ao lado)
    │       ├── hooks/                # use-order-filters.ts
    │       └── index.ts              # o que outras features podem importar; o resto é privado
    ├── components/
    │   ├── ui/                       # SOMENTE itens do registry shadcn (UI.md §4.4); nunca componentes de negócio
    │   ├── layout/                   # app-shell.tsx, app-sidebar.tsx, site-header.tsx, page-header.tsx
    │   ├── theme-provider.tsx        # vem do template
    │   ├── data-table/               # composições reutilizadas por ≥ 2 features (UI.md §8.5)
    │   ├── confirm-dialog.tsx        # (UI.md §8.11)
    │   └── empty-state.tsx
    ├── hooks/                        # hooks genéricos: use-mobile.ts (instalado pelo sidebar), use-debounce.ts
    ├── lib/
    │   ├── utils.ts                  # export { cn } from "cn" (gerado pelo init)
    │   ├── api-client.ts             # createClient<paths>() do openapi-fetch + middleware (Authorization, Problem Details → erro tipado)
    │   ├── query-client.ts           # QueryClient com staleTime/retry padrão
    │   ├── env.ts                    # import.meta.env validado com zod; único lugar que lê import.meta.env
    │   └── format.ts                 # Intl.DateTimeFormat/NumberFormat pt-BR
    ├── api/
    │   └── schema.d.ts               # GERADO por openapi-typescript (pnpm gen:api); nunca editado
    ├── assets/                       # imagens e fontes importadas pelo código (recebem hash no build)
    └── test/
        ├── setup.ts                  # import "@testing-library/jest-dom/vitest"; ciclo de vida do servidor msw
        ├── test-utils.tsx            # render() com providers e um router de teste
        └── msw/
            ├── handlers.ts           # handlers por feature, tipados pelo schema
            └── server.ts
```

### 5.4 What goes where (frontend)

| Concern | Location | Rule |
| --- | --- | --- |
| Registry components | `src/components/ui/` | Only `npx shadcn@latest add`. Customisations are edited in place with a `// custom:` header (`UI.md` §4.6). Nothing else is ever created here. |
| Product/shared components | `src/components/<group>/` | Used by two or more features or by the shell. Kebab-case files, PascalCase exports. |
| Feature code | `src/features/<feature>/` | Everything specific to one business capability. A feature imports another only through its `index.ts`. Mirror the backend feature package names so `order/` in Java and `order/` in TS are the same thing. |
| Routes | `src/routes/` | Thin: `validateSearch`, `beforeLoad` (auth), `loader` (`queryClient.ensureQueryData(orderQueries.detail(id))`) and a component that renders feature components. Business UI never lives in a route file. TanStack conventions: `__root.tsx`, `index.tsx`, `$param`, `_pathless` layouts, `.` flattening (`orders.$orderId.tsx`), `-` prefix for ignored helper files/folders. |
| Generated files | `src/routeTree.gen.ts`, `src/api/schema.d.ts` | Committed (so `pnpm typecheck` works without a generation step in CI and reviewers see contract changes), marked `linguist-generated` in `.gitattributes`, never edited; regenerated by `pnpm dev`/`build` (route tree) and `pnpm gen:api` (schema). |
| Server state | `features/<f>/api.ts` | TanStack Query `queryOptions` factories and mutation hooks. No `useEffect` + `fetch`. |
| Client/UI state | Component state, URL search params (router), `context` only for cross-cutting UI (theme, sidebar). A store library (Zustand) [CONDITIONAL] lives in `features/<f>/store.ts` or `src/stores/`; add it only with a stated reason. |
| Forms | `features/<f>/components/*-form.tsx` + `schemas.ts` | `react-hook-form` + `zod` resolver, `Field*` components (`UI.md` §8.2). |
| Environment | `src/lib/env.ts` | Only `VITE_*` variables (Vite exposes nothing else). `.env.example` committed; `.env.local` ignored. |
| Styling | `src/index.css` | Tokens and `@theme inline` only here (`UI.md` §3.3, §5). No second global stylesheet; component-scoped styles are Tailwind classes. |
| Unit/component tests | Next to the file: `order-table.test.tsx` | Vitest + Testing Library + msw. No `__tests__/` directories. |
| E2E tests | `e2e/` | Playwright; one spec per user flow; viewport matrix from `RESPONSIVENESS.md` §61. |
| PWA, only when selected | `vite.config.ts` (`vite-plugin-pwa`), `public/icons/`, optional `src/sw.ts` (injectManifest) | Manifest generated from config, icons in `public/`, never a hand-written `sw.js` in `public/` (`RESPONSIVENESS.md` §32–47). |
| Static files | `public/` | Only files that must keep their name and path (favicon, `robots.txt`, icons, `.well-known/`). Everything imported by code goes to `src/assets/`. |

`package.json` additions to the template (keep the template scripts as they are):

```jsonc
// path: frontend/package.json (trecho)
{
  "packageManager": "pnpm@10.33.4",           // a versão que o template do shadcn usa; alinhar com o time e com o Maven (D2)
  "engines": { "node": ">=24.21.0" },
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "e2e": "playwright test",
    "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts"
  }
}
```

```ts
// path: frontend/vite.config.ts  (o do template + router, proxy e Vitest)
import path from "path"
import tailwindcss from "@tailwindcss/vite"
import { tanstackRouter } from "@tanstack/router-plugin/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vitest/config"

export default defineConfig({
  plugins: [
    // O plugin do router precisa vir antes do plugin do React.
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    tailwindcss(),
  ],
  resolve: { alias: { "@": path.resolve(__dirname, "./src") } },
  server: {
    port: 5173,
    // Mesma origem em desenvolvimento: sem CORS, cookies funcionam.
    proxy: { "/api": { target: "http://localhost:8080", changeOrigin: true } },
  },
  build: { outDir: "dist", sourcemap: true },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
})
```

### 5.5 Development wiring (D1 and D2)

- Two processes: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (starts `compose.yaml` via `spring-boot-docker-compose`; in D1 with `spring.docker.compose.file: ../compose.yaml`) and `cd frontend && pnpm dev`. The developer opens `http://localhost:5173`; `/api/**` is proxied to `:8080`.
- Because of the proxy, the backend needs **no CORS configuration for local development**, and cookie-based sessions work. Add CORS (`JAVA.md` 6.15) only if production serves the SPA from a different origin than the API (D1 without a reverse proxy).
- The API base URL in the frontend is relative (`/api`) by default; `VITE_API_URL` overrides it only for the different-origin case.
- `pnpm gen:api` regenerates `src/api/schema.d.ts` from the running backend after any DTO/controller change; the commit that changes a contract carries both the Java and the generated TS (`COMMITS.md`: still one logical change).
- Commands are written in `README.md` (pt-BR) and shared `AGENTS.md` (Part 7.2); do not add a root `Makefile`/`justfile` unless the team already uses one.

### 5.6 Production wiring

**D1 — separate deploy.** The frontend image is a static server; the reverse proxy (or the same nginx) forwards `/api/` to the backend so the origin is shared.

```dockerfile
# path: frontend/Dockerfile
FROM node:24.21.0-alpine AS build
WORKDIR /app
# As imagens node:24 ainda trazem o Corepack; se a imagem não tiver, use: npm install -g pnpm@<versão do packageManager>
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
```

```nginx
# path: frontend/nginx.conf
server {
    listen 8080;
    root /usr/share/nginx/html;

    # Assets com hash no nome: cache imutável de um ano
    location /assets/ {
        add_header Cache-Control "public, max-age=31536000, immutable";
        try_files $uri =404;
    }

    # API na mesma origem (nome do serviço no compose/Kubernetes)
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Rotas do SPA: arquivo se existir, senão index.html (nunca cacheado)
    location / {
        add_header Cache-Control "no-cache";
        try_files $uri $uri/ /index.html;
    }
}
```

Backend: `server.forward-headers-strategy: framework` so redirects and `X-Forwarded-*` behave behind the proxy (`JAVA.md` 8.5). The frontend `Dockerfile` needs a `.dockerignore` with `node_modules`, `dist`, `e2e`, `test-results`.

**D2 — SPA bundled into the jar.** Maven multi-module; the frontend module packages `dist/` under `META-INF/resources/`, which Spring Boot serves as static content; the backend depends on it and adds the fallback.

```xml
<!-- path: pom.xml (raiz) -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
  </parent>
  <groupId>com.acme</groupId>
  <artifactId>billing</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <modules>
    <module>frontend</module>
    <module>backend</module>
  </modules>
  <properties>
    <java.version>25</java.version>
    <frontend-maven-plugin.version>2.0.2</frontend-maven-plugin.version>
    <!-- Exatamente o conteúdo de frontend/.nvmrc (com o prefixo v) e de packageManager em frontend/package.json -->
    <node.version>v24.21.0</node.version>
    <pnpm.version>10.33.4</pnpm.version>
  </properties>
</project>
```

```xml
<!-- path: frontend/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.acme</groupId>
    <artifactId>billing</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>billing-frontend</artifactId>
  <name>billing-frontend</name>
  <description>SPA empacotado como recursos estáticos (META-INF/resources)</description>

  <build>
    <resources>
      <!-- dist/ (saída do vite build) entra no jar onde o Boot serve conteúdo estático -->
      <resource>
        <directory>${project.basedir}/dist</directory>
        <targetPath>META-INF/resources</targetPath>
      </resource>
    </resources>
    <plugins>
      <plugin>
        <groupId>com.github.eirslett</groupId>
        <artifactId>frontend-maven-plugin</artifactId>
        <version>${frontend-maven-plugin.version}</version>
        <configuration>
          <!-- node/ e pnpm ficam em target/, nunca na raiz do módulo -->
          <installDirectory>${project.build.directory}</installDirectory>
        </configuration>
        <executions>
          <execution>
            <id>install-node-and-pnpm</id>
            <goals><goal>install-node-and-pnpm</goal></goals>
            <configuration>
              <nodeVersion>${node.version}</nodeVersion>
              <pnpmVersion>${pnpm.version}</pnpmVersion>
            </configuration>
          </execution>
          <execution>
            <id>pnpm-install</id>
            <goals><goal>pnpm</goal></goals>
            <configuration><arguments>install --frozen-lockfile</arguments></configuration>
          </execution>
          <execution>
            <id>pnpm-build</id>
            <goals><goal>pnpm</goal></goals>
            <configuration><arguments>run build</arguments></configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

```xml
<!-- path: backend/pom.xml (trecho) -->
<dependency>
  <groupId>com.acme</groupId>
  <artifactId>billing-frontend</artifactId>
  <version>${project.version}</version>
  <scope>runtime</scope>
</dependency>
```

- The default plugin phase is `generate-resources`, which runs before `process-resources`, so `dist/` exists when Maven copies it. `./mvnw -B verify` at the root builds both; `./mvnw -pl backend -am` still builds the frontend (dependency); `-Dskip.installnodepnpm -Dskip.pnpm` skips it when the jar is already in the local repository.
- Frontend lint/typecheck/unit tests are **not** run by Maven (keeps `verify` fast); they run in the frontend CI job (Part 1.6).
- `backend/src/main/resources/static/` stays absent — the SPA arrives through the jar.

Fallback so client-side routes (`/orders/42` on refresh) return `index.html`, while unknown API paths still return 404:

```java
// path: backend/src/main/java/com/acme/billing/shared/web/SpaConfig.java
package com.acme.billing.shared.web;

import java.io.IOException;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serve o SPA empacotado em META-INF/resources e devolve index.html para rotas do lado do cliente.
 * O Spring Boot não tem esse fallback nativo (spring-boot#27554), por isso o resolver customizado.
 * Registrar "/**" aqui substitui o handler padrão do Boot (WebMvcAutoConfiguration só o cria se o padrão não existe).
 */
@Configuration(proxyBeanMethods = false)
class SpaConfig implements WebMvcConfigurer {

    private static final String[] LOCATIONS = {"classpath:/META-INF/resources/", "classpath:/static/"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite gera assets/[nome]-[hash].*: cache imutável de um ano.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(LOCATIONS)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());

        // Todo o resto: o arquivo pedido ou index.html. O index nunca é cacheado.
        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATIONS)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** Mantém a checagem de segurança do PathResourceResolver e só então aplica o fallback. */
    static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected @Nullable Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            // API, actuator e arquivos com extensão inexistentes continuam sendo 404 de verdade.
            boolean notSpaRoute = resourcePath.startsWith("api/")
                    || resourcePath.startsWith("actuator/")
                    || resourcePath.contains(".");
            return notSpaRoute ? null : super.getResource("index.html", location);
        }
    }
}
```

What was verified and how (identification sheet): the resolver contract, the leading-slash stripping, the 404 path and the "custom `/**` replaces Boot's handler" behaviour were read in the Spring Framework `7.0.x` and Spring Boot `4.1.x` sources on the research date. The class could not be executed while this guide was written (no Maven Central access in that environment), so the test below is part of the deliverable: it runs in seconds in the web slice and must be green before D2 goes live.

Known edge cases: `/` is served by Boot's `WelcomePageHandlerMapping` (higher precedence than resource handlers; it finds `index.html` because `classpath:/META-INF/resources/` is one of the default `spring.web.resources.static-locations`, which this config does not change). Client routes that contain a dot (`/users/john.doe`) are treated as files and return 404 — if the SPA has such routes, replace `resourcePath.contains(".")` with an explicit extension check.

```java
// path: backend/src/test/java/com/acme/billing/shared/web/SpaConfigTest.java
package com.acme.billing.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Prova o fallback do SPA na fatia web, sem banco nem contexto completo. Os arquivos servidos vêm de
 * src/test/resources/META-INF/resources (index.html e assets/app-abc123.js). Filtros de segurança
 * desligados de propósito: aqui se testa o roteamento de recursos, não a autorização (JAVA.md 8.7).
 */
@WebMvcTest
@Import(SpaConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class SpaConfigTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("rota do lado do cliente devolve index.html sem cache")
    void shouldServeIndexForClientRoute() {
        assertThat(mvc.get().uri("/orders/42"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
                .bodyText().contains("<div id=\"root\">");
    }

    @Test
    @DisplayName("caminho de API inexistente continua 404")
    void shouldReturnNotFoundForUnknownApiPath() {
        assertThat(mvc.get().uri("/api/nothing")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("arquivo estático inexistente continua 404")
    void shouldReturnNotFoundForMissingFile() {
        assertThat(mvc.get().uri("/missing.js")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("asset com hash é servido com cache imutável de um ano")
    void shouldServeHashedAssetWithImmutableCache() {
        assertThat(mvc.get().uri("/assets/app-abc123.js"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=31536000, immutable");
    }
}
```

```text
backend/src/test/resources/META-INF/resources/     # fixtures do teste acima (não são o SPA real)
├── index.html                                     # <!doctype html><html><body><div id="root"></div></body></html>
└── assets/app-abc123.js                           # console.log("ok")
```

Security (D2, `JAVA.md` Part 8 for the rest): the API is protected, the SPA shell is public.

```java
// trecho de SecurityConfig: rotas do SPA e assets são públicos; a proteção fica em /api/**
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/**").authenticated()
        .requestMatchers("/actuator/health/**").permitAll()
        .anyRequest().permitAll());
```

### 5.7 API contract and generated types [RECOMMENDED]

- Source of truth: the Java controllers + `springdoc-openapi-starter-webmvc-ui` 3.x (`JAVA.md` 6.12), exposed at `/v3/api-docs`.
- `pnpm gen:api` → `src/api/schema.d.ts` (openapi-typescript); `src/lib/api-client.ts` builds `createClient<paths>({ baseUrl: env.apiUrl })` (openapi-fetch). Feature `types.ts` files alias `components["schemas"]["OrderResponse"]` — DTOs are never re-typed by hand.
- Optional: commit the exported spec to `docs/api/openapi.json` so CI can diff it against `main` and the frontend can regenerate without a running backend (`JAVA.md` 11.5 mentions the diff). Keep it refreshed by the same commit that changes the API.
- `orval` [CONDITIONAL] when the team wants generated TanStack Query hooks; output to `src/api/generated/`, also committed, also never edited.

### 5.8 Monorepo with several frontends (layout E) [CONDITIONAL]

Generated by `npx shadcn@latest init --template vite --monorepo` (official template `vite-monorepo`: pnpm workspace + Turborepo). The backend stays a sibling directory outside the pnpm workspace.

```text
acme-platform/
├── backend/                       # projeto Maven (layout B); não é um pacote pnpm
├── apps/
│   ├── web/                       # components.json (aliases ui → @workspace/ui/components), src/ como em 5.3
│   └── admin/
├── packages/
│   ├── ui/                        # src/components (itens do registry), src/hooks, src/lib, src/styles/globals.css, components.json
│   ├── eslint-config/             # [CONDITIONAL] configs compartilhadas (template next-monorepo)
│   └── typescript-config/
├── pnpm-workspace.yaml            # packages: apps/*, packages/*
├── turbo.json                     # tasks build/dev/lint/typecheck; outputs dist/**
├── package.json                   # packageManager pnpm@10.x; scripts turbo
└── compose.yaml  README.md  AGENTS.md  CLAUDE.md  LEARNING.md  docs/  .github/
```

- `npx shadcn@latest add` runs inside `apps/web` or with `-c apps/web`; registry components land in `packages/ui/src/components` (`UI.md` §4.4). `style`, `iconLibrary` and `baseColor` are identical in both `components.json` files.
- One `tailwindCssFile` (`packages/ui/src/styles/globals.css`) for every app.
- Turborepo caches `build`/`lint`/`typecheck`; `dev` is `persistent` and uncached (template `turbo.json`).

---

## 6. File and directory naming (cross-cutting)

| Element | Convention | Example |
| --- | --- | --- |
| Java packages, classes, tests | `JAVA.md` 2.2 (packages lowercase by feature; `PascalCase` classes; `*Test`, `*IT`) | `com.acme.billing.invoice.InvoiceService` |
| Java resources (templates, static, sql) | kebab-case directories and files; one directory per feature named like the package | `templates/invoice/confirm-delete.html`, `static/css/application.css` |
| Flyway migrations | `V<yyyyMMddHHmm>__<snake_description>.sql`, `R__<snake>.sql` (`JAVA.md` 7.7) | `V202609121030__create_invoices.sql` |
| YAML keys | kebab-case (Spring relaxed binding); project keys under one prefix | `app.payment.base-url` |
| Environment variables | `UPPER_SNAKE_CASE`; frontend ones prefixed `VITE_` | `DB_PASSWORD`, `VITE_API_URL` |
| Frontend files and directories | **kebab-case for everything**, including component files (shadcn convention: `login-form.tsx`, `app-sidebar.tsx`); no `PascalCase` file names, no `index.tsx` barrels except `features/<f>/index.ts` | `order-table.tsx`, `use-mobile.ts`, `data-table/` |
| TypeScript exports | `PascalCase` components and types, `camelCase` functions, hooks `useXxx`, `UPPER_SNAKE_CASE` constants | `export function OrderTable`, `useOrderFilters` |
| Route files | TanStack Router rules: `__root.tsx`, `index.tsx`, `$param`, `_pathless`, dot-flattening, `-` prefix ignored | `orders.$orderId.tsx` |
| Feature names | Singular, the same word in Java and TypeScript, kebab-case when composed | `invoice` ↔ `features/invoice/`, `order-item` |
| URLs | Plural, kebab-case (`JAVA.md` 6.1); route directories mirror URLs, not features | `/api/order-items` ↔ `routes/order-items.tsx` |
| Test files | Java `*Test.java`/`*IT.java`; TS `*.test.ts(x)` next to the source; Playwright `e2e/*.spec.ts` | `order-table.test.tsx`, `e2e/checkout.spec.ts` |
| Generated files | Committed, never edited, listed in `.gitattributes` as `linguist-generated` | `routeTree.gen.ts`, `api/schema.d.ts` |
| Docs | kebab-case `.md`; ADRs `NNNN-slug.md`; the agent guides keep their uppercase names | `docs/adr/0002-spa-react.md`, `docs/guides/JAVA.md` |
| CI workflows | kebab-case `.yml`, named after what they build | `.github/workflows/backend.yml` |
| Container files | `Dockerfile`, `.dockerignore`, `compose.yaml` (never `docker-compose.yml`) | |
| Maven modules | directory = artifactId, prefixed with the product | `billing-frontend/`, `billing-app/` |
| Scripts | kebab-case shell; Java single-file programs `PascalCase.java` | `scripts/reset-db.sh`, `scripts/ImportSeed.java` |

---

## 7. Where the guides and agent instructions live

### 7.1 Selected guides [RECOMMENDED]

Keep the guides selected for this repository in `docs/guides/`, or retain their established location. The set contains six independent references: `COMMITS.md`, `CONTEXT.md`, `STRUCTURE.md`, `JAVA.md`, `UI.md`, and `RESPONSIVENESS.md`. Any subset is valid. List only files that exist; none of these companions is a prerequisite for following this section.

These are large references. Read relevant sections on demand. Do not import all six into `AGENTS.md`, `CLAUDE.md`, or an always-loaded rule. No global agent configuration is required.

### 7.2 `AGENTS.md`: shared instructions [REQUIRED for agent setup]

Create a root `AGENTS.md` even if Claude Code is initially the only agent. It is the canonical home for repository-wide commands, actual layout, verified constraints, guide pointers, and the learning workflow. Keep it short; 60–140 lines is an editorial target, not a universal tool limit. `README.md` remains the human entry point.

When `CONTEXT.md` is present, its setup and migration procedure governs this work. Otherwise use this standalone procedure: inspect existing instruction files; create or minimally update `AGENTS.md`; consolidate shared rules without losing project decisions; create the Claude bridge below; create a small `LEARNING.md` index and topic files only for real durable findings. Existing nested instruction scopes must remain intact.

Example to adapt to the actual repository (commands shown as unverified until executed there):

```markdown
# Project instructions

## Project

Backend and frontend are independent deployables in `backend/` and `frontend/`.
Read their build files and lockfiles for installed versions. No incidental stack migrations.

## Commands

- Backend: from `backend/`, `./mvnw verify` — requires JDK and Docker; not verified.
- Frontend: from `frontend/`, use the scripts actually present in `package.json`; not verified.
- Do not invent a `test`, `typecheck`, `e2e`, or `gen:api` script if it is absent.

## Layout and constraints

- Backend feature packages: `backend/src/main/java/<actual-base-package>/<feature>/`.
- Frontend product features: `frontend/src/features/`; registry UI components follow the installed aliases.
- Never edit generated outputs; regenerate them with the repository command.
- Identifiers in English; code comments, README and ADRs in pt-BR.
- Respect existing authorization; a suggested commit message is not permission to commit or push.

## References

- Read only the relevant sections of guides actually present in `docs/guides/`.
- Backend implementation: `JAVA.md`; structure/build wiring: `STRUCTURE.md`.
- UI composition: `UI.md`; responsive/accessibility and conditional PWA: `RESPONSIVENESS.md`.
- Commit messages: `COMMITS.md`; context maintenance: `CONTEXT.md`.

## Project learning

Read `LEARNING.md` at task start, then only topic files relevant to the task.
After meaningful work, record a concise durable correction or decision with evidence.
Do not store secrets, speculative facts, full transcripts, or routine progress.
Add no entry when there is nothing new; finish the report with a short `LEARNING:` status.
```

Replace placeholders, remove missing guide names, and populate actual commands before calling setup complete. Shared rules must remain understandable to an agent that reads only `AGENTS.md`, without relying on `.claude/rules/`.

### 7.3 `CLAUDE.md`: Claude Code bridge [REQUIRED for agent setup]

At repository root, create or update `CLAUDE.md` with actual import lines outside code fences:

```markdown
@AGENTS.md
@LEARNING.md

# Claude Code

Shared instructions are maintained in `AGENTS.md`.
Read only learning topic files relevant to the task.
```

The fence is for this guide's example only; do not paste the fence into the real file. Claude Code documents this import pattern and does not automatically discover `AGENTS.md` by itself. Other agents' discovery rules differ; confirm the chosen tool/version rather than promising universal automatic loading.

If the established Claude entry is `.claude/CLAUDE.md`, keep it there and use `@../AGENTS.md` and `@../LEARNING.md`. Avoid circular imports and duplicate root/inner entry files for the same scope. Keep Claude-specific instructions here; do not maintain a second full copy of shared rules.

### 7.4 Learning files and optional path rules

`LEARNING.md` is a short index (create it with zero entries when appropriate):

```markdown
# Project learning

Read only the topics relevant to the current task.
No durable project learning has been recorded yet.
```

Topic files can live in `docs/learning/`; create them when there is useful content. Put dates, evidence, scope and consequences in the topic, then add a relative link to the index. Never list a topic file that does not exist.

Claude path rules are optional routing hints, not the only place holding a shared requirement. Example `.claude/rules/java.md` for a D1/D2 repository:

```markdown
---
paths:
  - "backend/**/*.java"
  - "backend/pom.xml"
  - "backend/src/main/resources/**"
---
# Backend reference

Follow the shared instructions in `AGENTS.md` and the installed Boot/JDK versions.
If present, read the relevant parts of `docs/guides/JAVA.md` and `docs/guides/STRUCTURE.md`.
Boot 4 defaults apply only to Boot 4 projects; this rule never authorizes a Boot 3 migration.
```

For a frontend rule, scope the real frontend paths and point to the present UI/responsiveness guides. Full browser matrices and PWA capabilities are conditional on the task/product. With no companion guide, follow the repository's existing tooling and applicable criteria stated here.

### 7.5 Settings, local files, MCP and skills [CONDITIONAL]

- Preserve the runtime's existing permission model. Adding instructions does not authorize broad command allowlists, credential access, installing an MCP server, or changing agent permissions.
- Team Claude settings live in `.claude/settings.json`; personal settings and `CLAUDE.local.md` are gitignored. Create only settings needed by an explicitly chosen workflow.
- Add shadcn MCP or other integrations only when selected for the project; keep secrets outside committed configuration and verify current official setup commands.
- Nested `AGENTS.md`/`CLAUDE.md` files are useful for real subproject differences. Keep scopes explicit; a nested Claude bridge imports that directory's shared rules without duplicating root policy.
- Repeatable procedures may become skills under the chosen tool's supported skill directory. Skills point to relevant guide sections; shared repository facts remain in `AGENTS.md`.

### 7.6 Migration and verification

| Existing state | Action |
| --- | --- |
| Only `CLAUDE.md` | Move shared facts into root `AGENTS.md`, preserve Claude-specific content, add the import bridge. |
| Only `AGENTS.md` | Keep it canonical; add a short `CLAUDE.md` bridge. |
| Both | Compare and consolidate differences deliberately; preserve local decisions and scopes. |
| Neither | Create both entry files and an initially empty learning index from repository evidence. |

Verify file existence, import resolution, no import cycles, and no full-guide eager imports. Confirm commands in the actual repository; distinguish static checks from tests and actual agent-loading checks. Include changed paths in the single delivery report. This section provides the essentials when `CONTEXT.md` is absent; when present, use its more detailed procedure.

---

## 8. Anti-patterns (reject on sight)

| Anti-pattern | Why it is wrong | Do instead |
| --- | --- | --- |
| `controller/`, `service/`, `repository/`, `model/` as first-level packages | Package by layer hides coupling and makes everything public (`JAVA.md` 5.1) | One package per feature + small `shared/` |
| Frontend source inside `src/main/resources/` (`resources/js/app.jsx`, `resources/frontend/`) | Mixes source with classpath resources; every build copies the whole tree into the jar; IDEs index it as Java resources | `frontend/` at the root (D1/D2); Thymeleaf Vite mode keeps only *entries* in `static/` |
| Vite `outDir` pointing into `src/main/resources/static` (or built bundles committed there) | Generated files in the source tree; stale bundles; noisy diffs | `outDir` = `dist` (SPA) or `target/classes/static` (Thymeleaf Vite mode) |
| `docker-compose.yml` | Legacy name; `compose.yaml` is the specification's canonical file | `compose.yaml` at the root |
| `config/` directory at the repository root | Spring Boot loads `./config/application*.yaml` at runtime — invisible behaviour change | `deploy/` for infra config |
| `application-prod.yaml` full of values or secrets | Configuration belongs to the platform; secrets in git | Env vars / mounted secrets (`JAVA.md` 11.1) |
| `.env` committed; `VITE_SECRET_*` variables | Everything `VITE_*` ships to the browser; secrets leak | `.env.example` only; secrets stay in the backend |
| Business components in `src/components/ui/` | `shadcn add --overwrite`/`apply` will delete them; registry directory is CLI-owned | `src/components/<group>/` or `src/features/<f>/components/` |
| Second global CSS file for tokens | Two sources of truth for the theme (`UI.md` §4.4) | Only `src/index.css` (`tailwindCssFile`) |
| `PascalCase` file names in the frontend (`OrderTable.tsx`) | Conflicts with every file the shadcn CLI generates; case-insensitive filesystems hide rename bugs | kebab-case files, PascalCase exports |
| `__tests__/` directories, `test/` tree mirroring `src/` in the frontend | Tests drift away from the code they cover | `*.test.tsx` next to the file; only `src/test/` for shared setup |
| Editing `routeTree.gen.ts` or `api/schema.d.ts` by hand | Overwritten on the next generation | Fix the routes / the Java API and regenerate |
| Two lockfiles (`package-lock.json` + `pnpm-lock.yaml`) or two Node package managers | Non-reproducible installs; CI and dev diverge | One package manager, declared in `packageManager` |
| `node/` or `node_modules/` inside the jar, `dist/` in git | Bloated artifacts, merge conflicts | `.gitignore` Part 1.4; `installDirectory` = `target` (D2) |
| SPA fallback that returns `index.html` for `/api/**` | Unknown API paths answer HTML with 200; clients misparse | Fallback excludes `api/`, `actuator/` and paths with an extension (5.6) |
| Hand-written Tailwind CLI pipeline for Thymeleaf | Fragile, duplicated with what Vite mode already does | Mode 1 without Tailwind, or Mode 2 (Vite) |
| Thymeleaf partials named like pages (`rows.html`) or pages returning fragments | Nobody can tell full pages from htmx partials | `_rows.html` for partials |
| Splitting one Boot app into `domain`/`application`/`infra` Maven modules | Ceremony without isolation gain | Packages + ArchUnit, or Spring Modulith (2.4) |
| `src/it/`, `src/integration-test/`, second test source set in Maven | Non-standard; tooling and guides assume `src/test` | `*IT` classes in `src/test`, Failsafe |
| Duplicated shared instructions or eager imports of entire guides | Divergence and excess startup context | Short canonical `AGENTS.md`, Claude import bridge, references read on demand (Part 7) |
| Guides only on someone's laptop | Agents in CI or other machines lose the rules | `docs/guides/` in the repository |
| `HELP.md`, `demo`, `com.example` left from the generator | Placeholder identity | Real `groupId`, `artifactId`, `<name>`, `<description>` before the first commit |

---

## 9. Checklists

### 9.1 New project

- [ ] Shape chosen (A–E) and recorded in `docs/adr/0002-…`; D1 vs D2 recorded if D.
- [ ] Generated with the official tool; `com.example`/`demo` replaced; `application.yaml` (not `.properties`); unused `static/`/`templates/` removed (B) or populated (C/D2).
- [ ] Root files: `README.md` (pt-BR), `.gitignore`, `.gitattributes`, `.editorconfig`, `AGENTS.md`, `CLAUDE.md`, `LEARNING.md`, wrapper, `compose.yaml` with pinned image, `.env.example` if needed.
- [ ] Only the selected guides are installed and referenced; shared rules are in `AGENTS.md`, with a valid Claude bridge and learning index. Optional agent-specific rules/settings are created only when applicable.
- [ ] Backend: `lombok.config`, `shared/` + first feature package, `db/migration/` with the first migration, `TestcontainersConfiguration`, `ArchitectureTest`.
- [ ] Thymeleaf: `templates/layout/main.html`, `fragments/`, `error/`, one feature directory; Mode 1 or Mode 2 decided; in Mode 2 `vite.mode` in both profiles and the Maven resource excludes untouched.
- [ ] SPA: `frontend/` from `init --template vite`, `.nvmrc`, `engines`, `packageManager`, scripts added, `vite.config.ts` with proxy/router/test, `src/{app,routes,features,components,hooks,lib,api,assets,test}` created, `gen:api` run once, `.gitattributes` marks generated files.
- [ ] D1: `frontend/Dockerfile` + `nginx.conf`; `spring.docker.compose.file: ../compose.yaml`. D2: root `pom.xml`, `frontend/pom.xml`, backend dependency, `SpaConfig` + `SpaConfigTest` green, security `permitAll` for the shell, `node.version`/`pnpm.version` equal to `.nvmrc`/`packageManager`.
- [ ] CI workflows with path filters; Dependabot or Renovate.
- [ ] `git status` shows no generated output; the delivery report includes the relevant tree for structural work and the checks actually run; active learning status goes last.

### 9.2 Existing project (before adding files)

- [ ] Audit done (Part 0.5); build tool, package manager and generators identified.
- [ ] New files placed next to their siblings, following the existing naming even when it differs from Part 6 (deviation stated once).
- [ ] No moves/renames without an explicit request; no second lockfile, formatter or linter introduced.
- [ ] Generated files regenerated, not edited; `.gitignore` extended only with entries from Part 1.3.

---

## 10. Sources, known limitations, and the procedure for updating this guide

### 10.1 Sources consulted on 2026-09-12

- `demo.zip` generated by Spring Initializr on 2026-09-12: `pom.xml` (Boot 4.1.1, Java 25, starters), `HELP.md` (parent-override explanation), `.gitignore`, `compose.yaml`, `.mvn/wrapper/maven-wrapper.properties` (Maven 3.9.16, `only-script`).
- `https://github.com/shadcn-ui/ui` at commit `2b3e6d4` (2026-09-12): `templates/vite-app/**`, `templates/vite-monorepo/**`, `templates/start-app/package.json`, `templates/react-router-app/package.json`, `packages/shadcn/src/templates/{vite,create-template,index}.ts`, `packages/shadcn/package.json` (CLI 4.21.0).
- `https://registry.npmjs.org/<package>/latest` for every version in the identification sheet; `https://registry.npmjs.org/@wim.deblauwe/vite-plugin-spring-boot` (README: `fullCopyFilePaths` default `['**/*.html','**/*.svg']`, `buildSystem`, copy target `target/classes`).
- `https://github.com/eirslett/frontend-maven-plugin` (README: requirements, goals, `workingDirectory`, `installDirectory`, skip flags) and `https://mvnrepository.com/artifact/com.github.eirslett/frontend-maven-plugin` (2.0.2, 2026-07-29).
- `https://github.com/wimdeblauwe/vite-spring-boot` (README: tags, properties, compatibility 2.0.1 ↔ Boot 4.x), `https://github.com/wimdeblauwe/ttcli` (README, releases 1.12.4) and its sources at commit `c70d83c` (`ViteLiveReloadInitService`, `ViteWithTailwindCssLiveReloadInitService`, `NpmHelper`, `files/templates/thymeleaf/**`), `https://github.com/wimdeblauwe/htmx-spring-boot` (compatibility table), `https://www.wimdeblauwe.com/blog/2026/08/03/writing-a-thymeleaf-component-library---part-2/` (Vite config with Thymeleaf).
- `https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-thymeleaf` (4.1.0), `https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide` (starter naming, test starter companions), `https://github.com/spring-projects/spring-boot/issues/27554` (SPA fallback: open).
- Sparse clones read on 2026-09-12: `https://github.com/spring-projects/spring-framework` branch `7.0.x` commit `bcd78a2` (`spring-webmvc/.../resource/{PathResourceResolver,ResourceHttpRequestHandler,ResourceHandlerUtils}.java`, `.../config/annotation/{ResourceHandlerRegistration,ResourceChainRegistration}.java`, `spring-web/.../http/CacheControl.java`, `spring-test/.../assertj/Abstract{Http,MockHttp}ServletResponseAssert.java`) and `https://github.com/spring-projects/spring-boot` branch `4.1.x` commit `885720a` (`module/spring-boot-webmvc/.../WebMvcAutoConfiguration.java`, `starter/` listing).
- Node.js: `https://registry.npmjs.org/node` (release mirror: 24.21.0 on 2026-09-10, 26.8.2 current), `https://nodejs.org/en/about/previous-releases` (v24 LTS, v26 Current), `https://nodejs.org/en/blog/announcements/evolving-the-nodejs-release-schedule` (v26 LTS in October 2026; one release per year from v27), `https://github.com/nodejs/LTS` (24.x maintenance 2026-10-20, EOL 2028-04-30).
- `https://code.claude.com/docs/en/memory` and `https://code.claude.com/docs/llms.txt` (CLAUDE.md scopes, size guidance, `@` imports, `.claude/rules/` with `paths`, `CLAUDE.local.md`, `AGENTS.md` pattern, skills as on-demand mechanism).
- Companion guides: `JAVA.md` 1.0 (Parts 0–2, 5, 7.7, 10, 11, 15.7), `UI.md` (§0–§4, §12), `RESPONSIVENESS.md` (§2, §59–§61), `COMMITS.md`.

### 10.2 Discrepancies and unconfirmed items

- **TypeScript**: npm `latest` is 7.0.2 while every shadcn template pins `~6`/`^6`. This guide follows the templates. Upgrade only as an explicit task after checking `typescript-eslint`, Prettier and the shadcn CLI against 7.
- **React**: templates pin `^19.2.8`; npm has 19.3.0. Fresh installs will get 19.3 — no incompatibility known, none verified.
- **`cn`**: `start-app` template uses `^0.2.6`; npm has 0.3.0 (`UI.md` §3.4 lists 0.2.x). Not verified against the registry components.
- **Node**: 24.21.0 is the newest 24.x on the research date and 24 is Active LTS until 2026-10-20. Node 26 enters LTS in October 2026; when the project moves to 26, bump `.nvmrc`, `engines.node`, the Dockerfile tag, `node.version` and `@types/node` together, after confirming the tools' `engines` fields (Vite 8 `^20.19 ‖ >=22.12`, Vitest 5 `^22.12 ‖ ^24 ‖ >=26`) still match.
- **`pnpm`**: shadcn monorepo template pins `pnpm@10.33.4`; npm `latest` reports 12.4.1. This guide uses the template's 10.x; `allowBuilds` in `pnpm-workspace.yaml` was observed in the template but its behaviour across pnpm majors was not tested.
- **`ttcli`** still pins `frontend-maven-plugin` 1.15.4; the goal names used by this guide (`install-node-and-pnpm`, `pnpm`) exist in 2.0.2's README. The 2.0.x changelog was not read; if 2.0.2 misbehaves, 1.15.4 is the known-good fallback.
- **Vitest 5**: the `defineConfig` import from `vitest/config` inside `vite.config.ts` was valid through Vitest 4 and is assumed unchanged in 5.0.0.
- **TanStack Router plugin option names** (`tanstackRouter({ target, autoCodeSplitting })`, plugin before `react()`) come from the router documentation as known to the author, not re-fetched today.
- **`SpaConfig`** (5.6): verified by reading the Spring Framework 7.0.x and Spring Boot 4.1.x sources listed in 10.1, not by running it — the sandbox used to write this guide has no Maven Central access. `SpaConfigTest` is provided so the first project that adopts D2 proves it; if the test fails, report it instead of patching the resolver blindly. `MockMvcTester` assertions used (`hasStatusOk`, `hasStatus(int/HttpStatus)`, `hasHeader`, `bodyText`) exist in `spring-test` 7.0.x.
- **Tailwind v4 source detection** with Vite `root` inside `src/main/resources` (Thymeleaf Mode 2) was not tested; the `@source` fallback is provided for that reason.
- **No command or file in this guide was executed** while it was produced. Treat trees and configuration as reviewed against sources, not as tested.
- Gradle equivalents for D2 (`com.github.node-gradle.node`) and for Thymeleaf Mode 2 are not covered. React Router 8 framework mode and TanStack Start (SSR) are out of scope.

### 10.3 Procedure for updating this guide

1. Record the date. Regenerate a Spring Initializr sample and compare `pom.xml`/`.gitignore`/`compose.yaml` with the identification sheet; update the Boot, Java and Maven wrapper versions.
2. `git clone --depth 1 --filter=blob:none --sparse https://github.com/shadcn-ui/ui.git && git sparse-checkout set templates packages/shadcn` → diff `templates/vite-app` and `templates/vite-monorepo` (`package.json`, `vite.config.ts`, `tsconfig*.json`, `eslint.config.js`, `.prettierrc`) against Part 5.3/5.4 and the identification sheet.
3. `npm view <pkg> version engines.node` for every package in the sheet; `npm view node versions --json` (newest 24.x/26.x) plus `https://nodejs.org/en/about/previous-releases` for the LTS status; update the sheet, `.nvmrc`, `engines`, the Dockerfile tag and `node.version`.
4. Check `frontend-maven-plugin`, `vite-spring-boot`, `htmx-spring-boot`, `ttcli` releases (GitHub releases / Maven Central); re-read the `ttcli` Vite init service for changes in the generated `vite.config.js`, properties and Maven excludes.
5. Re-check spring-projects/spring-boot#27554; if Spring Boot ships a native SPA fallback, replace `SpaConfig` in 5.6 with the property.
6. Re-read `https://code.claude.com/docs/en/memory` (and the `.claude` directory page) for changes to sizes, `paths` rules, imports and skills; update Part 7.
7. Re-read the companion guides' identification sheets and version tables; align every cross-reference (part and section numbers) in this file.
8. Review 10.2: remove resolved discrepancies, add new ones. Re-read the whole document for contradictions with `JAVA.md` Part 5/11 and `UI.md` §4.

### 10.4 Revision 1.1 review — 2026-09-13

The revision makes `AGENTS.md` canonical from initial setup, adds `CONTEXT.md` to the optional catalog, removes Claude-only shared policies, and aligns scope and reporting. [Claude Code memory](https://code.claude.com/docs/en/memory) documents the `@AGENTS.md` bridge; [AGENTS.md](https://agents.md/) describes the shared format. The existing D2 SPA resolver still requires its provided runtime tests in a consuming project; this revision did not execute them. Baseline tables are dated evidence, not permission to upgrade an existing repository.

Correction, 2026-09-14: §0.2 now lists repository instructions and installed-version documentation, matching the applicability contract at the top of this file.
