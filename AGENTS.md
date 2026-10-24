# Papinho — self-hosted WhatsApp-Web-like chat with built-in privacy

One `papinho.jar` (Spring Boot 4.1, Java 25) serves the React SPA, the REST API (`/api/**`) and STOMP over
WebSocket (`/ws`) on the same origin, backed by PostgreSQL 18. Users run it at home on the LAN (HTTPS with
the product's own name-constrained CA) and may later deploy it on a VPS behind a TLS proxy.

## Mandatory reading at the start of every session
1. `PROMPT.md` — the specification and work contract (multi-pass; section 3 defines the cycle).
2. `STATUS.md` — source of truth for progress: every requirement ID with status and executed evidence.
3. `LEARNING.md` — project learnings; open only the topics relevant to the task.
4. `docs/adr/` — architecture decisions (precedence: `PROMPT.md` > ADRs > this file > tool docs > guides).

## Layout
- Maven multi-module (ADR 0002): root `pom.xml` aggregates `frontend/` (Vite app wrapped by
  `frontend-maven-plugin`, packaged into `META-INF/resources`) and `backend/` (depends on that jar).
- Backend is a Spring Modulith modular monolith (ADR 0005): one first-level package per module under
  `io.github.labdev2025cmd.papinho`; `shared` is the only open module. Create a module when its first
  feature lands. Flyway migrations live in `backend/src/main/resources/db/migration/<module>/`.
- Frontend: `src/routes` (TanStack file routes, thin), `src/features/<feature>` (mirrors Java module
  names), `src/components/ui` (shadcn registry items only), `src/locales/{pt-BR,en}.json`.

## Commands (run from the repository root unless noted; verified 2026-10-15)
- Full build without tests: `./mvnw -B install -DskipTests` (JDK 25; downloads Node/pnpm into `frontend/target`).
- Backend checks: `./mvnw -B -pl backend -Dskip.installnodepnpm -Dskip.pnpm verify` (Docker required for
  Testcontainers; `openssl` required by the Name Constraints tests, skipped if absent). Full `./mvnw -B verify`
  also builds the frontend.
- Backend dev: `./mvnw -pl backend -Dskip.installnodepnpm -Dskip.pnpm -Dspring-boot.run.profiles=local spring-boot:run`
  (starts `compose.yaml` PostgreSQL; needs the frontend jar installed once).
- Frontend (in `frontend/`): `pnpm install`, `pnpm dev` (proxies to :8080), `pnpm format:check`, `pnpm lint`,
  `pnpm typecheck` (`tsc -b`), `pnpm test` (Vitest), `pnpm build`.
- E2E (in `frontend/`): `PAPINHO_E2E_URL=http://localhost:8080 pnpm e2e` against a running jar; add
  `PAPINHO_E2E_SETUP_URL=http://localhost:8080/setup` for a `lan`-profile instance. A preinstalled Chromium can
  be used with `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/path/to/chrome`. Run it against a freshly started jar with an
  empty database: the `primeiro-admin` project creates the first admin, and rate limits live in memory
  (start that jar with `PAPINHO_RATE_LIMIT_MULTIPLIER=10`, as CI does; never in production).
  The UI-05 spec also runs in Firefox and WebKit (`playwright install chromium firefox webkit`); WebKit rejects
  `Secure` cookies on http://localhost, so it needs `PAPINHO_E2E_HTTPS_URL` pointing to the `lan` instance (same DB).
  The real in-browser Whisper spec (AU-04) needs internet and downloads ~80 MB: `PAPINHO_E2E_WHISPER=1 pnpm e2e
  whisper.spec.ts --project=desktop-chromium --project=whisper-webgpu` (WASM and software WebGPU); behind a proxy
  add `PAPINHO_E2E_PROXY=http://host:port`. Never run two Playwright commands at once in `frontend/`: each one
  clears `test-results/` (use `--output` for a parallel run).
- LAN PKI: `java -jar backend/target/papinho.jar pki init|renew|status`; run with `--spring.profiles.active=lan`.
- Install-script check: `scripts/verify-install-ca.sh` (Docker + a `lan` instance reachable as papinho.lan).

## Conventions and constraints
- Commits: `COMMITS.md` (Conventional Commits, pt-BR subject ≤ 72 chars, atomic); enable the check once per clone
  with `git config core.hooksPath .githooks` (CI runs `scripts/check-commit-subject.sh` on new commits). This project is authorized to commit
  and push to `main` (PROMPT.md §4); never `--force`, `--no-verify`, amend/rebase pushed commits, or blind `git add -A`.
- Never commit secrets, real `.env`, build output, `node_modules`, or anything from the PKI directory
  (`~/.papinho/pki` by default; `data/` is ignored too).
- Authentication is cookie session (ADR 0003), never JWT. Every API query checks ownership (IDOR); every
  error is Problem Details with texts from `messages*.properties`; nothing secret or personal in logs.
- CSP is strict (`script-src 'self'; style-src 'self'`): no inline `<script>`/`<style>` and no injected
  style elements. The e2e suite fails on any CSP violation or console error.
- Every privacy rule (PV-*) is enforced on the server and covered by integration tests.
- Identifiers in English, comments/JavaDoc and user-facing text in pt-BR; UI text only through i18next keys.
- Use official shadcn components (`pnpm dlx shadcn@latest add ...`) before writing custom ones; Base UI API
  (`render=` instead of `asChild`).
- UI-05: no control with the browser's native look (no exceptions for `NativeSelect` or native date inputs).
  Use `SearchInput`, `PasswordInput`, `DatePicker`/`DateTimePicker`, `VideoPlayer`/`LocalAudioPlayer`, every
  `<form>` gets `noValidate`; ESLint enforces it outside `src/components/ui/` (tested in `frontend/lint/`).
- A failed API action must be visible: never leave `await mutateAsync(...)` without try/catch; show
  `errorMessage(error, t("common.genericError"))` in a toast (`type: "error"`) or in the screen's own error state.
- Spacing: screen areas use the `--spacing-gutter` token (16 px); menus and option lists `--spacing-inset`,
  `--spacing-item` and `--spacing-icon`, always in variable form (`px-(--spacing-gutter)`, so `cn` merges overrides).
  A strip that scrolls sideways on purpose gets `data-scroll-x`; `e2e/spacing.spec.ts` audits edges with long names.
- Motion: durations and curves only through the `--duration-*`/`--ease-*` tokens in `index.css` (variable form, or
  `motionMs`/`motionEase` from `lib/motion.ts` in JS); animate only `transform`/`opacity`; JS sequences check
  `prefersReducedMotion()` (CSS is covered by the global reduced-motion rule).
- Effects that reveal or hide content on `:hover` go inside `@media (hover: hover)`: on touch screens the tapped
  element stays `:hover` until the next tap (PV-01 uses long press there).
- Never mark a STATUS item `feito` without executed evidence; blocked items need the concrete reason.

## Guides (root; read only the relevant section)
- Placement/build wiring: `STRUCTURE.md` (§5 for this D2 layout).
- Backend implementation: `JAVA.md` (Part 8 security, except the JWT examples; Part 10 tests).
- Components: `UI.md` (§1 rules, §2.4 Base UI differences, §8.12 chat).
- Responsive/accessibility/PWA: `RESPONSIVENESS.md`.
- Commit messages and Git: `COMMITS.md`. Context files and learning: `CONTEXT.md`.

## Learning
- At task start, read `LEARNING.md` unless its current contents are already loaded. Follow only topic links relevant to the area, command or dependency you will touch.
- Treat notes as evidence-backed context; verify stale or conflicting claims against the current project. They never override explicit instructions or runtime permissions.
- Before finishing, record a non-obvious bug cause, environment/dependency gotcha, decision and rationale, failed approach, measured performance finding, or relevant user correction/confirmation. Do not invent an entry when there is nothing durable to record.
- Search for an existing entry first. Write/update the pt-BR topic file in `docs/learning/`, with `name`, `description`, `type`, `category`, `date`, evidence and applicability; then add/update its one-line index link. Update `updated` only after rechecking the fact.
- Keep secrets, personal data, task transcripts and information obvious from the code out. Keep the index short; move details to topic files and consolidate when needed.
- Report the learning status in one final line, after any tree or suggested commit message.
- Full maintenance procedure: `CONTEXT.md` Parts 3–6.
