# STATUS — progresso do Papinho

Fonte de verdade do progresso (PROMPT.md §3.1). `feito` exige evidência **executada**; código sem
verificação é `em andamento`. Última auditoria: 2026-10-15 (passada 1, fim da Fase 1).

Ambiente da passada 1: sandbox de nuvem com JDK 25.0.4 (Temurin, baixado), Node 24.22.0, pnpm 10.34.6,
Docker 29 (daemon iniciado manualmente), Chromium do Playwright em `/opt/pw-browsers`, sem aparelhos reais.
A passada 1 cobriu a Fase 0 e a Fase 1 (exceto o que depende de mensagens: CT-02, CT-04, e-mail de ID-05).

## Fase 0 — Fundação

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| F0-GEN | Geradores oficiais (Initializr, shadcn init) | feito | `backend/pom.xml` do Initializr (Boot 4.1.1, Java 25); `frontend/components.json` do `shadcn init --template vite --base base` | |
| F0-D2 | Layout D2: SPA no JAR | feito | `./mvnw -B install -DskipTests` gera `backend/target/papinho.jar`; e2e `smoke.spec.ts` carrega o SPA do JAR | ADR 0002 |
| F0-CTX | AGENTS.md, CLAUDE.md, LEARNING.md, STATUS.md | feito | arquivos na raiz; `CLAUDE.md` importa `AGENTS.md` e `LEARNING.md` | descoberta em sessão nova não verificada |
| F0-ADR | ADRs 0001–0010 | feito | `docs/adr/` | |
| F0-COMPOSE | `compose.yaml` com PostgreSQL 18 | feito | `spring-boot:run` no perfil local subiu `papinho-postgres-1` e o health respondeu UP | |
| F0-SEC | Segurança base (cadeias, denyAll, CSRF spa, CSP, Problem Details, Argon2) | feito | `SecurityIT` (12 testes) e `LanSetupIT.shouldIssueStrictCsrfCookieOverTls`; e2e sem violação de CSP | `__Host-SESSION` provado em `IdentityIT` |
| QA-04 | CI no GitHub Actions: backend, frontend e e2e em jobs separados | feito | `.github/workflows/ci.yml`; runs 38757286112 e 38769083331 verdes nos três jobs | e2e sobe instância padrão e perfil lan |
| QA-05 | README pt-BR (dev, LAN, CA, backup, produção) | em andamento | `README.md` | backup depende de BK-03 (Fase 7) |
| PK-01 | `pki init` com BouncyCastle: CA P-256 10 anos com Name Constraints; servidor ≤398 dias; chave 0600 | feito | `CertificateAuthorityTest` (7) e `PkiCommandTest` (5); `openssl verify` e `certutil -V` recusam domínio/IP de fora | PKIX do JDK ignora NC da âncora (LEARNING) |
| PK-02 | TLS por SSL bundle com HTTP/2; porta HTTP só com `/setup/**` e redirecionamento | feito | `LanSetupIT` (HTTP/2 validado pela CA; 301 para HTTPS; POST recusado); `curl --http2` manual no perfil lan | |
| PK-03 | Página `/setup`: downloads, SHA-256, QR, instruções por sistema/navegador | feito | `LanSetupIT.shouldServeSetupPageOverHttp`, `shouldDownloadCaInDerAndPem`; e2e `setup.spec.ts` (axe, CSP, 320 px) | instruções não testadas em aparelhos reais (iOS, Android, Windows, macOS) |
| PK-04 | `install-ca.sh`: confere SHA-256, detecta distro, NSS, idempotente, desinstala | feito | `scripts/verify-install-ca.sh` passou em Debian (sistema + NSS Chrome/Firefox), Fedora 42 e Arch; impressão digital errada aborta sem instalar | |
| PK-05 | Documentação de hostname (mDNS/Avahi ou DNS do roteador) | feito | `README.md` seção "Hostname na rede" | |
| PK-06 | PKI desligada em produção | feito | `SecurityIT.shouldNotExposeSetupWhenPkiDisabled` | |

## Fase 1 — Contas e contatos

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| ID-01 | Cadastro com `@usuario` único normalizado, reservados, UUID v7 | feito | `IdentityIT` (unicidade sem caixa, reservados, formato, UUID v7); e2e `accounts.spec.ts` cadastro com checagem de disponibilidade | |
| ID-02 | Nome de exibição, foto (recorte, EXIF, miniaturas), recado | feito | `ProfileIT` (EXIF removido, 512 e 96 px, não-imagem 422); e2e `profile.spec.ts` (recorte, salvar foto, recado) | HEIC depende do navegador decodificar |
| ID-03 | Troca de `@usuario` com cooldown e reserva de 30 dias | feito | `IdentityIT.shouldReserveOldUsername`; e2e `profile.spec.ts` (troca e aviso do cooldown) | cooldown configurável pelo admin |
| ID-04 | Login por senha (Argon2, ≥12, vazada opcional) e passkey; Bucket4j; erros uniformes | feito | `IdentityIT` (erro uniforme, 429 com Retry-After, senha <12), `SecurityIT` (Argon2), `PasswordPolicyTest` (vazada só quando ligada); e2e senha errada e passkey com autenticador virtual | passkey em aparelho real não testada; WebAuthn exige hostname (não IP) |
| ID-05 | Códigos de recuperação, TOTP opcional, e-mail opcional | em andamento | `IdentityIT` (TOTP exigido depois de ativado; código de recuperação redefine senha, desliga TOTP, uso único); e2e TOTP e diálogo dos 10 códigos | falta e-mail opcional de recuperação (precisa de SMTP configurável) |
| ID-06 | Sessões ativas: listar, revogar, encerrar outras | feito | `IdentityIT.shouldManageSessions`; e2e `profile.spec.ts` (encerrar as outras derruba a sessão do outro navegador) | |
| ID-07 | Modos de cadastro; convites por link/QR com expiração e usos | feito | `IdentityIT` (aberto/convite/fechado; revogado, esgotado e vencido recusados; QR em SVG); e2e convite usado em outro contexto | |
| ID-08 | Primeiro usuário vira admin | feito | `IdentityIT.shouldMakeFirstUserAdmin`; e2e `first-admin.setup.ts` | |
| CT-01 | Adicionar contato por @usuario, link ou QR; apelido local | feito | `ContactIT` (por @usuario com apelido; link próprio e cartão); e2e adiciona por @usuario em maiúsculas e pelo link `/add/@usuario` | QR lido por câmera real não testado |
| CT-02 | Solicitações de mensagem (aceitar, recusar, bloquear) | pendente | | depende das conversas (Fase 2) |
| CT-03 | Bloquear e desbloquear | em andamento | `ContactIT` (bloqueado não vê foto/recado; desbloquear devolve) | UI pronta (lista e Configurações > Bloqueados) sem e2e; efeito em mensagens vem com a Fase 2 |
| CT-04 | Denunciar usuário com as 5 últimas mensagens | pendente | | depende das mensagens (Fase 2) |

## Fase 2 — Conversas individuais em tempo real

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| MS-01 | Formatação do WhatsApp, sanitizada | pendente | | |
| MS-02 | Status por mensagem e "Dados da mensagem" | pendente | | |
| MS-03 | "digitando…" e "gravando áudio…" | pendente | | |
| MS-04 | Responder, encaminhar, reagir, mencionar, fixar, favoritar, selecionar | pendente | | |
| MS-14 | Marcador de não lidas, ir para a primeira, marcar como não lida | pendente | | |
| RT-01 | `seq` monotônico por conversa e `afterSeq` | pendente | | |
| RT-02 | `clientMessageId` idempotente | pendente | | |
| RT-03 | Presença com heartbeat; aba em segundo plano não conta | pendente | | |
| UI-01 | Duas colunas no desktop, pilha no celular | em andamento | shell `app-shell.tsx`; e2e roda os fluxos em desktop e Pixel 7 | falta a coluna da conversa (Fase 2) |

## Fase 3 — Mídia, áudio e links

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| MD-01 | Upload até 1 GB (tus), Tika, nome sanitizado, cota | pendente | | |
| MD-02 | Imagens: EXIF, miniaturas, thumbhash, legenda, visualizador | pendente | | |
| MD-03 | Galeria da conversa | pendente | | |
| MD-04 | Prévia de link com proteção contra SSRF | pendente | | |
| MD-05 | YouTube nocookie clique-para-carregar; Vimeo/Spotify oEmbed | pendente | | |
| MD-06 | Antivírus ClamAV opcional | pendente | | |
| AU-01 | Gravação com MediaRecorder, onda, trava, pré-escuta | pendente | | |
| AU-02 | Velocidades, mini-player global, sequência | pendente | | |
| AU-03 | Transcrição sob demanda/automática com cache | pendente | | |
| AU-04 | Whisper no navegador (Transformers.js, WebGPU/WASM) | pendente | | |
| AU-05 | Provedores OpenAI-compatible no servidor | pendente | | |

## Fase 4 — Grupos e mensagens avançadas

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| GR-01 | Grupos: dados, admins, permissões, sair silenciosamente | pendente | | |
| GR-02 | Convite de grupo por link/QR com aprovação | pendente | | |
| GR-03 | Lista de transmissão | pendente | | |
| GR-04 | Lista de transmissão temporária | pendente | | |
| MS-05 | Editar com janela configurável | pendente | | |
| MS-06 | Apagar para mim e para todos | pendente | | |
| MS-07 | Mensagens temporárias com job (ShedLock) | pendente | | |
| MS-08 | Visualização única | pendente | | |
| MS-09 | Enquetes | pendente | | |
| MS-10 | Emojis, figurinhas, GIFs opcionais | pendente | | |
| MS-11 | Agendadas, silenciosas, rascunho sincronizado | pendente | | |
| MS-12 | Mensagens salvas | pendente | | |
| MS-13 | Atalhos de teclado e lista com `?` | pendente | | |

## Fase 5 — Privacidade

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| PV-01 | Desfoque configurável por item | pendente | | |
| PV-02 | Bloqueio de tela | pendente | | |
| PV-03 | Conversa trancada | pendente | | |
| PV-04 | Visibilidade de visto por último, online, foto, recado | pendente | | |
| PV-05 | Reciprocidade no servidor | pendente | | |
| PV-06 | Confirmação de leitura global/por conversa | pendente | | |
| PV-07 | Quem pode me adicionar a grupos | pendente | | |
| PV-08 | Notificações sem conteúdo | pendente | | |
| SG-04 | Regras PV-* no servidor com testes de integração | pendente | | |

## Fase 6 — Organização, busca, notificações e PWA

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| GR-05 | Arquivar, fixar, silenciar, favoritar, pastas, limpar/apagar | pendente | | |
| GR-06 | Busca e filtros (unaccent, pg_trgm, portuguese) | pendente | | |
| NT-01 | Notification API e Web Push (VAPID) | pendente | | |
| NT-02 | Avisos de push dependente de internet e iOS | pendente | | |
| NT-03 | Configuração de notificações e Badging API | pendente | | |
| NT-04 | PWA instalável com atualização controlada | pendente | | |
| NT-05 | Offline: IndexedDB por usuário, fila com clientMessageId | pendente | | |

## Fase 7 — Backup, admin, endurecimento e e2e completo

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| BK-01 | Exportar conversa em ZIP | pendente | | |
| BK-02 | Exportar dados da conta e excluir conta | pendente | | |
| BK-03 | Backup e restauração da instância | pendente | | |
| AD-01 | Painel do admin | em andamento | `/admin`: configurações da instância e usuários (desativar); `IdentityIT` desativação derruba sessões | faltam métricas, armazenamento, denúncias |
| AD-02 | Configurações da instância no banco | em andamento | `instance_settings` com modo de cadastro, cooldown, janelas, cota, senha vazada; `IdentityIT.shouldApplyRegistrationModes` | novas chaves entram com as fases |
| AR-01 | WCAG 2.2 AA; RESPONSIVENESS.md aplicado | em andamento | e2e com axe nas telas existentes | revisar a cada tela nova |
| AR-02 | 320 px, alvos de 44 px, safe areas, teclado virtual | em andamento | e2e 320 px em `/`, `/login`, `/register` e `/setup`; alvos de 44 px em `pointer: coarse` (`index.css`) | teclado virtual só em aparelho real |
| AR-03 | Fluxo só com teclado; axe sem críticas/sérias | em andamento | axe sem críticas/sérias em `/setup`, cadastro, admin com contatos e segurança | fluxo só com teclado não automatizado |
| QA-03 | E2E dos fluxos críticos | em andamento | 23 testes verdes (desktop e celular): primeiro admin, convite, contatos, TOTP, passkey, perfil, sessões, setup | faltam mensagens, mídia, grupos, offline |

## Requisitos transversais

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| SG-01 | IDOR, Problem Details, sem segredos em logs | em andamento | Problem Details em 401/403/400/500 (`SecurityIT`) | IDOR a cada endpoint novo |
| SG-02 | CSP estrita, cabeçalhos, HSTS em produção | feito | `SecurityIT.shouldServeSpaShellWithSecurityHeaders`; e2e falha em qualquer violação de CSP | HSTS por `PAPINHO_HSTS=true` |
| SG-03 | Rate limit em login, convites, desbloqueio, envio, upload | em andamento | `RateLimiter` (Bucket4j) em login por IP e conta, cadastro, recuperação, convites, ações sensíveis; `IdentityIT` 429 com Retry-After | faltam envio, upload e desbloqueio (fases 2, 3 e 5) |
| UI-02 | Temas, papel de parede, fonte, densidade | em andamento | Configurações > Aparência (claro/escuro/sistema) | papel de parede, fonte e densidade pendentes |
| UI-03 | Estados carregando, vazio, erro, offline, sem permissão | em andamento | contatos com carregando/vazio/erro; não encontrado (e2e `smoke.spec.ts`) | offline vem com NT-05 |
| UI-04 | Lista de mensagens performática | pendente | | |
| IN-01 | Textos externalizados; pt-BR completo, pronto para en | em andamento | `i18n.test.ts` garante as mesmas chaves em pt-BR e en; backend com `messages*.properties` | |
| QA-01 | Backend: unitários, slice, integração, ArchUnit, Modulith, `./mvnw verify` | em andamento | `./mvnw -B verify` na raiz: 21 unitários + 42 de integração verdes; job backend do CI verde | cresce a cada fase |
| QA-02 | Frontend: lint, typecheck, Vitest | feito | `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test` (12) locais e no job frontend do CI | |

## Dúvidas registradas para o dono

1. **groupId/pacote Java.** O `PROMPT.md` traz o marcador `io.github.SEU_USUARIO`. Foi usado
   `io.github.labdev2025cmd` (dono do repositório no GitHub, sem hífen, que não é válido em pacote Java).
   Confirmar ou indicar outro antes que haja muito código (renomear é mecânico, mas toca todos os arquivos).
