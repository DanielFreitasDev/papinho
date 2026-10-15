# STATUS — progresso do Papinho

Fonte de verdade do progresso (PROMPT.md §3.1). `feito` exige evidência **executada**; código sem
verificação é `em andamento`. Última auditoria: 2026-10-15 (passada 2, Fase 3).

Ambiente da passada 1: sandbox de nuvem com JDK 25.0.4 (Temurin, baixado), Node 24.22.0, pnpm 10.34.6,
Docker 29 (daemon iniciado manualmente), Chromium do Playwright em `/opt/pw-browsers`, sem aparelhos reais.
A passada 1 cobriu as fases 0 e 1 e a maior parte da Fase 2 (conversas individuais em tempo real). Pendentes da
Fase 1: CT-04 (denúncia) e o e-mail opcional de ID-05.

Passada 2 (mesmo tipo de ambiente; JDK 25.0.4.1 baixado, Chromium 1194 em `/opt/pw-browsers` usado por
`PLAYWRIGHT_CHROMIUM_EXECUTABLE`): auditoria verde no início (backend 21 unitários + 52 integração, frontend 28);
CT-04 e o painel de dados do contato (UI-01); Fase 3 quase toda (upload tus, imagens, galeria, prévias com
SSRF, áudio, transcrição). Fim da passada: backend **74 unitários + 64 de integração**, frontend **29 Vitest**,
e2e **29 verdes** (desktop e celular; os 4 de `/setup` precisam de instância `lan` e rodam no CI), duas rodadas
seguidas contra instância nova.

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
| CT-02 | Solicitações de mensagem (aceitar, recusar, bloquear) | feito | `MessagingIT.shouldHandleMessageRequests` (vai para solicitações, leitura escondida até aceitar, recusar esconde); `RealtimeIT` presença só após aceitar; e2e aceitar pela UI | recusar e bloquear pela UI sem e2e (cobertos no servidor) |
| CT-03 | Bloquear e desbloquear | em andamento | `ContactIT` (foto/recado); `MessagingIT.shouldEnforceBlocksAndMembership` (envio recusado nos dois sentidos); presença filtrada por bloqueio; e2e `moderation.spec.ts` bloqueia pelo painel (denúncia com bloqueio) e o outro lado não envia | "não pode adicionar a grupos" vem com GR-01 |
| CT-04 | Denunciar usuário com as 5 últimas mensagens | feito | `ModerationIT` (copia as 5 últimas, bloqueia junto, IDOR 404, só admin vê e fecha); e2e `moderation.spec.ts` (denunciar pelo painel com bloqueio, admin vê as mensagens e resolve, axe) | módulo `moderation`; limite de 20 denúncias/hora por usuário |

## Fase 2 — Conversas individuais em tempo real

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| MS-01 | Formatação do WhatsApp, sanitizada | feito | `parse.test.ts` (10: marcadores, limites de palavra, mono literal, HTML vira texto, blocos, listas, citação, spoiler); e2e `chat.spec.ts` (negrito, spoiler que revela, mono) | render só monta elementos React; destaque de código por lowlight carregado sob demanda |
| MS-02 | Status por mensagem e "Dados da mensagem" | feito | `MessagingIT.shouldTrackReceiptsAndUnread` (ponteiros, horários de entrega/leitura, só o autor vê); e2e enviada → lida e diálogo Dados da mensagem | leitura escondida em solicitação (CT-02); configurações de PV-06 na Fase 5 |
| MS-03 | "digitando…" e "gravando áudio…" | feito | `RealtimeIT.shouldNotifyMessagesReceiptsAndTyping`; e2e `chat.spec.ts` ("digitando…") e `audio.spec.ts` ("gravando áudio…" no outro navegador durante a gravação) | |
| MS-04 | Responder, encaminhar, reagir, mencionar, fixar, favoritar, selecionar | em andamento | `MessagingIT.shouldReactPinStarAndForward` (até 3 fixadas, selo após 5 saltos, favorita privada); e2e responder com salto, reagir, fixar, favoritar, selecionar duas e encaminhar | menção @usuario é reconhecida e vira link (unit); @todos depende de grupos (Fase 4) |
| MS-14 | Marcador de não lidas, ir para a primeira, marcar como não lida | feito | `MessagingIT.shouldTrackReceiptsAndUnread`; e2e contador na lista, marcador "2 mensagens não lidas", botão leva à primeira, marcar como não lida | |
| RT-01 | `seq` monotônico por conversa e `afterSeq` | feito | `MessagingIT.shouldSyncBySeqAndDeduplicate`, `shouldPageHistoryBackwards`; `cache.test.ts` (6: cursor, merge, janela, pendentes); e2e reação e fixar chegam por afterSeq | seq atribuído com trava de linha (`SequenceAllocator`); reconexão real com rede caída não testada em e2e |
| RT-02 | `clientMessageId` idempotente | feito | `MessagingIT.shouldSyncBySeqAndDeduplicate` (reenvio devolve 200 com a mesma mensagem; outro chat dá 409); `cache.test.ts` pendente some ao confirmar | o botão "tentar de novo" reenvia com o mesmo id |
| RT-03 | Presença com heartbeat; aba em segundo plano não conta | feito | `RealtimeIT.shouldTrackPresence` (aba oculta não conta, offline com visto por último, terceiro recebe 404); e2e "online" no cabeçalho | por enquanto só entre conversas aceitas pelos dois lados; PV-04/PV-05 na Fase 5 |
| UI-01 | Duas colunas no desktop, pilha no celular | feito | lista + conversa no desktop e rota própria `/c/$id` com voltar no celular; painel "Dados do contato" como terceira coluna a partir de 1280 px e gaveta abaixo disso; e2e `moderation.spec.ts` e `media.spec.ts` usam o painel nos dois projetos (desktop 1280 px e Pixel 7) | |

## Fase 3 — Mídia, áudio e links

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| MD-01 | Upload até 1 GB (tus), Tika, nome sanitizado, cota | feito | `MediaIT` (tus em dois pedaços, HEAD para retomar, 409 de deslocamento, 412 sem Tus-Resumable, 413 acima de 1 GB e da cota, cancelar devolve a cota, tipo pelo conteúdo, download como octet-stream); `MediaHelpersTest` (nomes, tipos); e2e `media.spec.ts` (progresso, cancelar upload preso, ordem de vários arquivos) | servidor tus próprio (ADR 0009); uploads expiram em 24 h; arquivos enviados e nunca anexados ainda contam na cota (limpeza na Fase 7) |
| MD-02 | Imagens: EXIF, miniaturas, thumbhash, legenda, visualizador | feito | `MediaHelpersTest` (orientação EXIF 1/3/6/8, JPEG sem EXIF, WebP sem EXIF/XMP), `ThumbHashTest` (bate com a referência JS), `MediaIT` (EXIF removido, miniatura, ThumbHash); e2e legenda, visualizador com zoom, navegação e Esc | HEIC/CMYK que o Java não decodifica são recusados como imagem |
| MD-03 | Galeria da conversa | feito | `MediaIT.shouldListGalleryByTab` (mídia, docs, áudios, links; 404 para quem é de fora); e2e abas Mídia e Docs e abrir foto da galeria | |
| MD-04 | Prévia de link com proteção contra SSRF | feito | `AddressPolicyTest` (33 endereços), `SafeFetcherTest` (redirecionamento para loopback, file:, laço, tamanho, porta, usuário na URL, DNS que resolve para endereço proibido), `LinkPreviewIT` (Open Graph, imagem recodificada, cache, 10.x/169.254.169.254/127.x/192.168.x sem prévia, 400 para file: e javascript:); e2e cartão do link | a conexão usa os endereços já checados (sem DNS rebinding) |
| MD-05 | YouTube nocookie clique-para-carregar; Vimeo/Spotify oEmbed | em andamento | `LinkPreviewIT.shouldEmbedYoutubeWithoutCookies`; e2e `links.spec.ts` (nenhuma requisição ao YouTube antes do clique, iframe nocookie com sandbox depois) | oEmbed de Vimeo/Spotify implementado (lista fechada, só o `src` em host permitido), mas sem rede no sandbox para testar contra os provedores |
| MD-06 | Antivírus ClamAV opcional | feito | `ClamAvScannerTest` (protocolo zINSTREAM contra clamd simulado: limpo, EICAR recusado, resposta estranha falha fechado); desligado por padrão (`PAPINHO_CLAMAV_ENABLED`) | não testado contra um clamd real |
| AU-01 | Gravação com MediaRecorder, onda, trava, pré-escuta | feito | e2e `audio.spec.ts` com microfone falso do Chromium (gravação travada, "Parar e ouvir" com pré-escuta, enviar direto, cancelar) | segurar para gravar e arrastar para travar (toque) e AAC/MP4 do Safari não testados em aparelho real |
| AU-02 | Velocidades, mini-player global, sequência | feito | e2e `audio.spec.ts` (1× → 1,5×, o segundo áudio assume a faixa ao fim do primeiro, mini-player fora da conversa e fechar) | |
| AU-03 | Transcrição sob demanda/automática com cache | em andamento | `TranscriptionIT` (provedor chamado uma vez, cache para os demais, 404 para quem é de fora, 409 para o navegador e texto do navegador em cache, falha 502 sem vazar a chave); e2e "Transcrever" com provedor simulado | automática para recebidos sem e2e; "visualização única nunca persistida" depende de MS-08 (Fase 4) |
| AU-04 | Whisper no navegador (Transformers.js, WebGPU/WASM) | bloqueado | worker com Transformers.js 4, runtime ONNX servido pelo JAR (`pnpm build` gera os `.wasm`), CSP com `wasm-unsafe-eval` e Hugging Face (`SecurityIT`) | sem acesso ao Hugging Face no sandbox para baixar o modelo e Chromium sem WebGPU: transcrição real não executada. Para desbloquear: rodar num navegador com internet e conferir download, progresso e texto |
| AU-05 | Provedores OpenAI-compatible no servidor | feito | `TranscriptionIT` contra provedor simulado (multipart com `model` e `file`, `Authorization: Bearer`, User-Agent `Papinho/<versão>`, chave cifrada e nunca devolvida nem logada, saúde do Whisper próprio); e2e cadastro de provedor no admin | Groq/OpenAI reais não testados (sem chave e sem rede) |

## Fase 4 — Grupos e mensagens avançadas

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| GR-01 | Grupos: dados, admins, permissões, sair silenciosamente | feito | `GroupIT` (criar só com contatos, avisos do sistema, leitura quando todos leram, só admins enviam/editam, promover, remover, sair sem aviso, membro mais antigo vira admin, bloqueio impede adicionar, foto só para membros, IDOR 404, responder em privado); e2e `groups.spec.ts` (desktop e celular: criar, avisos, nome de quem envia, painel com axe, só admins enviam, responder em privado) | quem sai perde o acesso ao histórico (diferente do WhatsApp); "quem pode me adicionar" vem com PV-07 |
| GR-02 | Convite de grupo por link/QR com aprovação | feito | `GroupIT.shouldJoinByInviteLink` (prévia, entrar sem não lidas antigas, aprovação de entrada, só admins geram, redefinir e desativar invalidam); e2e link com QR Code no painel; limite de 60 consultas/entradas por hora | rota `/g/<código>` no SPA |
| GR-03 | Lista de transmissão | pendente | | |
| GR-04 | Lista de transmissão temporária | pendente | | |
| MS-05 | Editar com janela configurável | feito | `MessagingIT.shouldEditAndDeleteForEveryone` (só o autor, janela do admin 409, edição chega por afterSeq, aviso não se edita); e2e `messages.spec.ts` (menu Editar, selo "editada" no outro navegador, ↑ edita a última, Esc cancela) | |
| MS-06 | Apagar para mim e para todos | feito | `MessagingIT` (para todos: só autor, janela, texto/anexo/reações/favoritas removidos, arquivo 404, resposta mostra apagada; para mim: some do histórico, do afterSeq e da prévia só de quem apagou, IDOR 404); e2e aviso nos dois navegadores e apagar para mim persistente após recarregar | apagar várias pela seleção |
| MS-07 | Mensagens temporárias com job (ShedLock) | feito | `DisappearingIT` (timer 24 h/7 d/90 d com aviso, valores fora da lista 400, padrão para conversas e grupos novos, permissão no grupo, job apaga vencidas e arquivos, citação fica nula); e2e timer pelo painel com aviso nos dois lados | ShedLock 7.10.1 (`shedlock`, hora do banco); cliente esconde vencidas antes do job |
| MS-08 | Visualização única | pendente | | |
| MS-09 | Enquetes | pendente | | |
| MS-10 | Emojis, figurinhas, GIFs opcionais | pendente | | |
| MS-11 | Agendadas, silenciosas, rascunho sincronizado | pendente | | |
| MS-12 | Mensagens salvas | feito | `GroupIT.shouldKeepSavedMessagesAndReplyPrivately` (uma por pessoa, idempotente, invisível aos outros); e2e menu "Mensagens salvas" envia e passa no axe | |
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
| AD-01 | Painel do admin | em andamento | `/admin`: configurações da instância, usuários (desativar), fila de denúncias (`ModerationIT`, e2e) e provedores de transcrição (`TranscriptionIT`, e2e); `IdentityIT` desativação derruba sessões | faltam uso de disco e gerar convite pela lista de usuários |
| AD-02 | Configurações da instância no banco | em andamento | `instance_settings` com modo de cadastro, cooldown, janelas, cota, senha vazada; `IdentityIT.shouldApplyRegistrationModes` | novas chaves entram com as fases |
| AR-01 | WCAG 2.2 AA; RESPONSIVENESS.md aplicado | em andamento | e2e com axe nas telas existentes (inclui painel, visualizador, galeria, gravação, admin); `muted-foreground` escurecido para 4,5:1 sobre fundos cinza; fechar de toasts empilhados fora da ordem de foco | revisar a cada tela nova; tema escuro sem axe |
| AR-02 | 320 px, alvos de 44 px, safe areas, teclado virtual | em andamento | e2e 320 px em `/`, `/login`, `/register` e `/setup`; alvos de 44 px em `pointer: coarse` (`index.css`) | teclado virtual só em aparelho real |
| AR-03 | Fluxo só com teclado; axe sem críticas/sérias | em andamento | axe sem críticas/sérias em `/setup`, cadastro, admin com contatos e segurança | fluxo só com teclado não automatizado |
| QA-03 | E2E dos fluxos críticos | em andamento | 29 verdes + 4 de `/setup` (desktop e celular): primeiro admin, convite, contatos, TOTP, passkey, perfil, sessões, setup, conversa entre dois navegadores, denúncia, upload (fotos, documento, cancelar), voz, prévia de link | faltam grupo, apagar para todos, privacidade recíproca, conversa trancada, bloqueio de tela |

## Requisitos transversais

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| SG-01 | IDOR, Problem Details, sem segredos em logs | em andamento | Problem Details em 401/403/400/500 (`SecurityIT`); IDOR de conversa, mensagem, reação e encaminhar (`MessagingIT`), denúncia (`ModerationIT`), upload, mídia e galeria (`MediaIT`), transcrição (`TranscriptionIT`, inclusive chave fora dos logs); WebSocket só assina a própria fila (`WebSocketSecurityConfig`) | IDOR a cada endpoint novo |
| SG-02 | CSP estrita, cabeçalhos, HSTS em produção | feito | `SecurityIT.shouldServeSpaShellWithSecurityHeaders`; e2e falha em qualquer violação de CSP | HSTS por `PAPINHO_HSTS=true` |
| SG-03 | Rate limit em login, convites, desbloqueio, envio, upload | em andamento | `RateLimiter` (Bucket4j) em login por IP e conta, cadastro, recuperação, convites, ações sensíveis, criação de upload (200/h), denúncias, prévias de link e transcrição; `IdentityIT` 429 com Retry-After; "digitando" limitado por frequência | falta limite de envio de mensagens e de desbloqueio (PV-02/PV-03); `rate-limit-multiplier` só para e2e |
| UI-02 | Temas, papel de parede, fonte, densidade | em andamento | Configurações > Aparência (claro/escuro/sistema) | papel de parede, fonte e densidade pendentes |
| UI-03 | Estados carregando, vazio, erro, offline, sem permissão | em andamento | contatos com carregando/vazio/erro; não encontrado (e2e `smoke.spec.ts`) | offline vem com NT-05 |
| UI-04 | Lista de mensagens performática | em andamento | `message-scroller` do shadcn com `content-visibility`, páginas de 50 e paginação para trás (`beforeSeq`) | sem medição com milhares de mensagens |
| IN-01 | Textos externalizados; pt-BR completo, pronto para en | em andamento | `i18n.test.ts` garante as mesmas chaves em pt-BR e en; backend com `messages*.properties` | |
| QA-01 | Backend: unitários, slice, integração, ArchUnit, Modulith, `./mvnw verify` | em andamento | `./mvnw -B -pl backend verify`: 74 unitários + 64 de integração verdes (inclui `MediaIT`, `LinkPreviewIT`, `TranscriptionIT`, `ModerationIT`, `ModularityTests` com os módulos novos) | cresce a cada fase |
| QA-02 | Frontend: lint, typecheck, Vitest | feito | `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test` (29), `pnpm build` locais e no job frontend do CI | |

## Dúvidas registradas para o dono

1. **groupId/pacote Java.** O `PROMPT.md` traz o marcador `io.github.SEU_USUARIO`. Foi usado
   `io.github.labdev2025cmd` (dono do repositório no GitHub, sem hífen, que não é válido em pacote Java).
   Confirmar ou indicar outro antes que haja muito código (renomear é mecânico, mas toca todos os arquivos).
