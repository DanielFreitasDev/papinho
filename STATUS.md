# STATUS — progresso do Papinho

Fonte de verdade do progresso (PROMPT.md §3.1). `feito` exige evidência **executada**; código sem
verificação é `em andamento`. Última auditoria: 2026-10-16 (passada 6, auditoria de qualidade depois das Fases 0–7).

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

Passada 3 (mesmo tipo de ambiente; Postgres pelo espelho `mirror.gcr.io` porque o Docker Hub e o ECR negaram):
auditoria verde no início (backend 74 + 64, frontend 29, e2e 29). Fase 4 inteira: editar e apagar, grupos e
convites, transmissão, temporárias (ShedLock), visualização única, enquetes, emojis/figurinhas/GIFs, agendadas,
silenciosas e rascunho, mensagens salvas e atalhos. Fim da passada: backend **74 unitários + 87 de integração**
(`./mvnw -B -pl backend verify`), frontend **33 Vitest** com lint, typecheck e format, e2e **37 verdes** (desktop
e celular, 4 de `/setup` só no CI) em duas rodadas seguidas contra instância nova.

Passada 4 (mesmo tipo de ambiente; JDK 25.0.4.1 baixado, Maven Central pelo espelho do Google por 429,
Postgres por `mirror.gcr.io`): auditoria no início com backend 74 + 87 e frontend 33 verdes, mas o **último run do
CI da passada 3 (40) tinha falhado** no `RealtimeIT.shouldRequireSessionAndCsrf` (corrida no teste, corrigida). Fase 5
inteira no servidor (módulo `privacy`, ADR 0013) e na interface. Fim da passada: backend **74 unitários + 95 de
integração**, frontend **38 Vitest** com lint, typecheck e format, e2e **41 verdes** em duas rodadas seguidas contra instância
nova e **43 verdes** na rodada final com o teste de inatividade e passkey (desktop e celular; 4 de `/setup` só no CI).

Passada 5 (mesmo tipo de ambiente; JDK 25.0.4.1 baixado, Maven pelo espelho do Google, Postgres por
`mirror.gcr.io`): auditoria verde no início (backend 74 + 95, frontend 38, CI run 43 verde). Fase 6 inteira: organização
da lista (GR-05), busca com `unaccent`/`pg_trgm`/`portuguese` (GR-06), módulo `notification` com Web Push próprio
(RFC 8291/8292) e regras no servidor (NT-01..03, fecha PV-03, PV-08 e SG-04), PWA com `vite-plugin-pwa` (NT-04) e
offline com Dexie (NT-05), ADR 0014. Fim da Fase 6: backend **78 unitários + 109 de integração**, frontend **38
Vitest** com lint, typecheck e format, e2e **49 verdes** (desktop e celular; 4 de `/setup` só no CI) na suíte
completa contra instância nova. Correção: os runs 45 e 46 do CI falharam no `format:check` de um spec e2e, corrigido
em `76020f2`.

Fase 7 na mesma passada: exportar conversa (BK-01), dados da conta e exclusão (BK-02, módulo `backup`), scripts de
backup e restauração testados (BK-03), uso de disco e convite no painel (AD-01), tema escuro, 320 px e teclado com
axe (AR-01..03), aparência (UI-02) e medição com 2000 mensagens (UI-04). Correções encontradas na passada: o run 51 do
CI falhou no ArchUnit (`StorageUsage` sem sufixo `Controller`, `5e698b9`); envios simultâneos esgotavam o pool de
conexões porque o registro de eventos do Modulith rastreava o `@TransactionalEventListener` do aviso por WebSocket
(`774e101`, teste com 100 envios em paralelo); a presença gravava "visto por último" de conta já excluída (erro de
FK nos logs do CI, `a456787`); o run 52 falhou no e2e de notificações porque o headless shell do Playwright nega
`Notification.permission` (`88de604`, passa a usar o Chromium completo); a sincronização perdia a mensagem avisada
durante a primeira busca da conversa (falhou o e2e de privacidade nos dois projetos, `ffdf737`, com teste Vitest).

Passada 6 (mesmo tipo de ambiente; JDK 25.0.4.1 baixado, Maven pelo espelho do Google com `MVNW_REPOURL` porque o
Central respondeu 429 até à distribuição do wrapper, Postgres e Mailpit por `mirror.gcr.io`): auditoria verde no início
(backend 78 + 117, frontend 42 Vitest, CI run 55 verde). Feito: e-mail opcional de recuperação (fecha ID-05), estados de
carregando/erro/sem conexão/sem permissão com "Tentar de novo" (UI-03), teste do limite de upload e cabeçalho
`Retry-After` nas respostas 429 (SG-03), oEmbed de Vimeo/Spotify contra provedores simulados (MD-05), transcrição
automática no e2e (AU-03), rótulos do Spinner e do botão de rolagem traduzidos (IN-01). Correções de e2e: colisão de
`@usuario` entre specs, rascunho sincronizado (MS-11) vazando entre os projetos no spec de atalhos e espera frágil do
aviso no teste de inatividade (o mesmo que falhou sob carga na passada 5). Fim da passada: backend **78 unitários + 125
de integração** (com `OutsiderAccessIT` e `ProblemDetailsIT` da auditoria de IDOR), frontend **55 Vitest** com lint, typecheck, format e build, e2e **63 verdes** na suíte completa
contra instância nova com e-mail ligado (1 falha, a da inatividade no celular, corrigida e 3/3 depois; 4 de `/setup` e
`perf` pulados), CI runs 56, 58 e 59 (38892740074, `15a0c16`, com a auditoria de IDOR) verdes nos três jobs.

## Fase 0 — Fundação

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| F0-GEN | Geradores oficiais (Initializr, shadcn init) | feito | `backend/pom.xml` do Initializr (Boot 4.1.1, Java 25); `frontend/components.json` do `shadcn init --template vite --base base` | |
| F0-D2 | Layout D2: SPA no JAR | feito | `./mvnw -B install -DskipTests` gera `backend/target/papinho.jar`; e2e `smoke.spec.ts` carrega o SPA do JAR | ADR 0002 |
| F0-CTX | AGENTS.md, CLAUDE.md, LEARNING.md, STATUS.md | feito | arquivos na raiz; `CLAUDE.md` importa `AGENTS.md` e `LEARNING.md` | descoberta em sessão nova não verificada |
| F0-ADR | ADRs 0001–0010 | feito | `docs/adr/` | |
| F0-COMPOSE | `compose.yaml` com PostgreSQL 18 | feito | `spring-boot:run` no perfil local subiu `papinho-postgres-1` e o health respondeu UP | |
| F0-SEC | Segurança base (cadeias, denyAll, CSRF spa, CSP, Problem Details, Argon2) | feito | `SecurityIT` (12 testes) e `LanSetupIT.shouldIssueStrictCsrfCookieOverTls`; e2e sem violação de CSP | `__Host-SESSION` provado em `IdentityIT` |
| QA-04 | CI no GitHub Actions: backend, frontend e e2e em jobs separados | feito | `.github/workflows/ci.yml`; runs 38757286112, 38769083331, 38815227435 e, na passada 4, 38830633763 e 38832315052 (`828abb7`, com a Fase 5) verdes nos três jobs; na passada 5, run 38847160629 (54, `9868970`, com as Fases 6 e 7) verde nos três jobs; na passada 6, runs 56, 58 e 59 (38892740074, `15a0c16`) verdes, com o Mailpit no job e2e depois das falhas dos runs 45/46 (format), 51 (ArchUnit) e 52 (e2e de notificações) | e2e sobe instância padrão e perfil lan. Correção da passada 4: o run 40 (último da passada 3) falhou no backend por um teste instável do `RealtimeIT`, corrigido em `6a4c833` |
| QA-05 | README pt-BR (dev, LAN, CA, backup, produção) | feito | `README.md` com Desenvolvimento, Testes, Configuração (inclusive e-mail), Subir na rede local, Instalar a CA, Produção e Backup; os comandos de build, testes e e2e foram executados nesta passada e os de backup/restauração na passada 5 (BK-03) | instruções por aparelho (iOS, Android, Windows, macOS) não testadas em aparelho real |
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
| ID-05 | Códigos de recuperação, TOTP opcional, e-mail opcional | feito | `IdentityIT` (TOTP exigido depois de ativado; código de recuperação redefine senha, desliga TOTP, uso único); e2e TOTP e diálogo dos 10 códigos; e-mail: `EmailRecoveryIT` (4, SMTP real do GreenMail: só vale confirmado, 202 igual para qualquer @usuario, link de uso único e com validade, token só em hash, sessões caem, troca avisa o endereço antigo e invalida links, 3 pedidos/hora por conta, nada de endereço ou token nos logs); e2e `email.spec.ts` com Mailpit (confirmar, pedir pelo @usuario, redefinir, sessão antiga cai, axe) | desligado por padrão (`PAPINHO_MAIL_ENABLED`); redefinir por e-mail não desliga o TOTP; SMTP com provedor real não testado |
| ID-06 | Sessões ativas: listar, revogar, encerrar outras | feito | `IdentityIT.shouldManageSessions`; e2e `profile.spec.ts` (encerrar as outras derruba a sessão do outro navegador) | |
| ID-07 | Modos de cadastro; convites por link/QR com expiração e usos | feito | `IdentityIT` (aberto/convite/fechado; revogado, esgotado e vencido recusados; QR em SVG); e2e convite usado em outro contexto | |
| ID-08 | Primeiro usuário vira admin | feito | `IdentityIT.shouldMakeFirstUserAdmin`; e2e `first-admin.setup.ts` | |
| CT-01 | Adicionar contato por @usuario, link ou QR; apelido local | feito | `ContactIT` (por @usuario com apelido; link próprio e cartão); e2e adiciona por @usuario em maiúsculas e pelo link `/add/@usuario` | QR lido por câmera real não testado |
| CT-02 | Solicitações de mensagem (aceitar, recusar, bloquear) | feito | `MessagingIT.shouldHandleMessageRequests` (vai para solicitações, leitura escondida até aceitar, recusar esconde); `RealtimeIT` presença só após aceitar; e2e aceitar pela UI | recusar e bloquear pela UI sem e2e (cobertos no servidor) |
| CT-03 | Bloquear e desbloquear | feito | `ContactIT` (foto/recado); `MessagingIT.shouldEnforceBlocksAndMembership` (envio recusado nos dois sentidos); `GroupIT.shouldRespectBlocksAndProtectPhoto` (bloqueado não é adicionado ao grupo); `GroupIT` link de convite recusa quem um admin bloqueou; presença filtrada por bloqueio; e2e `moderation.spec.ts` bloqueia pelo painel e o outro lado não envia | |
| CT-04 | Denunciar usuário com as 5 últimas mensagens | feito | `ModerationIT` (copia as 5 últimas, bloqueia junto, IDOR 404, só admin vê e fecha); e2e `moderation.spec.ts` (denunciar pelo painel com bloqueio, admin vê as mensagens e resolve, axe) | módulo `moderation`; limite de 20 denúncias/hora por usuário |

## Fase 2 — Conversas individuais em tempo real

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| MS-01 | Formatação do WhatsApp, sanitizada | feito | `parse.test.ts` (10: marcadores, limites de palavra, mono literal, HTML vira texto, blocos, listas, citação, spoiler); e2e `chat.spec.ts` (negrito, spoiler que revela, mono) | render só monta elementos React; destaque de código por lowlight carregado sob demanda |
| MS-02 | Status por mensagem e "Dados da mensagem" | feito | `MessagingIT.shouldTrackReceiptsAndUnread` (ponteiros, horários de entrega/leitura, só o autor vê); e2e enviada → lida e diálogo Dados da mensagem | leitura escondida em solicitação (CT-02); configurações de PV-06 na Fase 5 |
| MS-03 | "digitando…" e "gravando áudio…" | feito | `RealtimeIT.shouldNotifyMessagesReceiptsAndTyping`; e2e `chat.spec.ts` ("digitando…") e `audio.spec.ts` ("gravando áudio…" no outro navegador durante a gravação) | |
| MS-04 | Responder, encaminhar, reagir, mencionar, fixar, favoritar, selecionar | feito | `MessagingIT.shouldReactPinStarAndForward` (até 3 fixadas, selo após 5 saltos, favorita privada); e2e responder com salto, reagir (inclusive "Mais reações"), fixar, favoritar, selecionar duas e encaminhar; `@todos` em destaque sem link no grupo (`groups.spec.ts`) | menções avisam mesmo com a conversa silenciada, se a pessoa pedir (`NotificationIT`) |
| MS-14 | Marcador de não lidas, ir para a primeira, marcar como não lida | feito | `MessagingIT.shouldTrackReceiptsAndUnread`; e2e contador na lista, marcador "2 mensagens não lidas", botão leva à primeira, marcar como não lida | |
| RT-01 | `seq` monotônico por conversa e `afterSeq` | feito | `MessagingIT.shouldSyncBySeqAndDeduplicate`, `shouldPageHistoryBackwards`; `cache.test.ts` (6: cursor, merge, janela, pendentes); e2e reação e fixar chegam por afterSeq | seq atribuído com trava de linha (`SequenceAllocator`); passada 4: o cliente agora sincroniza também na primeira conexão do WebSocket (aviso se perdia entre carregar e assinar); reconexão real com rede caída não testada em e2e |
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
| MD-05 | YouTube nocookie clique-para-carregar; Vimeo/Spotify oEmbed | feito | `LinkPreviewIT.shouldEmbedYoutubeWithoutCookies`; `LinkPreviewIT.shouldEmbedVimeoAndSpotifyThroughOembed` contra oEmbed simulado (só o `src` do iframe em `player.vimeo.com`/`open.spotify.com`, HTML do provedor descartado, iframe de outro host ignorado); e2e `links.spec.ts` (nenhuma requisição ao YouTube antes do clique, iframe nocookie com sandbox depois) | Vimeo e Spotify reais não testados (sem rede no sandbox), como o Groq em AU-05 |
| MD-06 | Antivírus ClamAV opcional | feito | `ClamAvScannerTest` (protocolo zINSTREAM contra clamd simulado: limpo, EICAR recusado, resposta estranha falha fechado); desligado por padrão (`PAPINHO_CLAMAV_ENABLED`) | não testado contra um clamd real |
| AU-01 | Gravação com MediaRecorder, onda, trava, pré-escuta | feito | e2e `audio.spec.ts` com microfone falso do Chromium (gravação travada, "Parar e ouvir" com pré-escuta, enviar direto, cancelar) | segurar para gravar e arrastar para travar (toque) e AAC/MP4 do Safari não testados em aparelho real |
| AU-02 | Velocidades, mini-player global, sequência | feito | e2e `audio.spec.ts` (1× → 1,5×, o segundo áudio assume a faixa ao fim do primeiro, mini-player fora da conversa e fechar) | |
| AU-03 | Transcrição sob demanda/automática com cache | feito | `TranscriptionIT` (provedor chamado uma vez, cache para os demais, 404 para quem é de fora, 409 para o navegador e texto do navegador em cache, falha 502 sem vazar a chave); `ViewOnceIT` (áudio de visualização única nunca é transcrito, 404); e2e "Transcrever" com provedor simulado e, com a opção automática ligada, os recebidos transcritos sem clicar (`audio.spec.ts`, desktop e celular) | provedor real fica no `TranscriptionIT`/AU-05 |
| AU-04 | Whisper no navegador (Transformers.js, WebGPU/WASM) | bloqueado | worker com Transformers.js 4, runtime ONNX servido pelo JAR (`pnpm build` gera os `.wasm`), CSP com `wasm-unsafe-eval` e Hugging Face (`SecurityIT`) | sem acesso ao Hugging Face no sandbox para baixar o modelo e Chromium sem WebGPU: transcrição real não executada. Para desbloquear: rodar num navegador com internet e conferir download, progresso e texto |
| AU-05 | Provedores OpenAI-compatible no servidor | feito | `TranscriptionIT` contra provedor simulado (multipart com `model` e `file`, `Authorization: Bearer`, User-Agent `Papinho/<versão>`, chave cifrada e nunca devolvida nem logada, saúde do Whisper próprio); e2e cadastro de provedor no admin | Groq/OpenAI reais não testados (sem chave e sem rede) |

## Fase 4 — Grupos e mensagens avançadas

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| GR-01 | Grupos: dados, admins, permissões, sair silenciosamente | feito | `GroupIT` (criar só com contatos, avisos do sistema, leitura quando todos leram, só admins enviam/editam, promover, remover, sair sem aviso, membro mais antigo vira admin, bloqueio impede adicionar, foto só para membros, IDOR 404, responder em privado); e2e `groups.spec.ts` (desktop e celular: criar, avisos, nome de quem envia, painel com axe, só admins enviam, responder em privado) | quem sai perde o acesso ao histórico (diferente do WhatsApp); "quem pode me adicionar" vem com PV-07 |
| GR-02 | Convite de grupo por link/QR com aprovação | feito | `GroupIT.shouldJoinByInviteLink` (prévia, entrar sem não lidas antigas, aprovação de entrada, só admins geram, redefinir e desativar invalidam); e2e link com QR Code no painel; limite de 60 consultas/entradas por hora | rota `/g/<código>` no SPA |
| GR-03 | Lista de transmissão | feito | `BroadcastIT.shouldFanOutToContactsOnly` (só contatos como destinatários, chega como conversa individual só para quem tem o dono nos contatos, lista é só do dono 404, sem enquete); e2e `groups.spec.ts` (criar pela UI e a mensagem chega na conversa individual) | editar/apagar depois só afeta a cópia do dono |
| GR-04 | Lista de transmissão temporária | feito | `BroadcastIT.shouldHandleTemporaryLists` (timer da lista deixa a cópia temporária; lista vencida não envia, 403); diálogo com validade e timer | |
| MS-05 | Editar com janela configurável | feito | `MessagingIT.shouldEditAndDeleteForEveryone` (só o autor, janela do admin 409, edição chega por afterSeq, aviso não se edita); e2e `messages.spec.ts` (menu Editar, selo "editada" no outro navegador, ↑ edita a última, Esc cancela) | |
| MS-06 | Apagar para mim e para todos | feito | `MessagingIT` (para todos: só autor, janela, texto/anexo/reações/favoritas removidos, arquivo 404, resposta mostra apagada; para mim: some do histórico, do afterSeq e da prévia só de quem apagou, IDOR 404); e2e aviso nos dois navegadores e apagar para mim persistente após recarregar | apagar várias pela seleção |
| MS-07 | Mensagens temporárias com job (ShedLock) | feito | `DisappearingIT` (timer 24 h/7 d/90 d com aviso, valores fora da lista 400, padrão para conversas e grupos novos, permissão no grupo, job apaga vencidas e arquivos, citação fica nula); e2e timer pelo painel com aviso nos dois lados | ShedLock 7.10.1 (`shedlock`, hora do banco); cliente esconde vencidas antes do job |
| MS-08 | Visualização única | feito | `ViewOnceIT` (id do arquivo nunca vai ao cliente, rotas de mídia recusam até para quem enviou, token só para o destinatário, `no-store`, segunda abertura 410, token vencido 404, job tira o arquivo depois de aberto ou após 14 dias, áudio nunca transcrito); e2e `view-once.spec.ts` (abrir uma vez, cabeçalho `no-store`, marca d'água com o `@usuario`, aviso de captura, "Foto aberta" para os dois) | token vale 5 min para Range de vídeo (ADR 0012); o service worker não guarda nada da API, inclusive o conteúdo por token (NT-04); desfoque fora de foco sem teste automatizado |
| MS-09 | Enquetes | feito | `PollIT` (única troca o voto, múltipla, anônima esconde quem votou, encerrada 409, opções inválidas 400, resultados pelo afterSeq, IDOR 404, não edita nem encaminha); e2e voto da Hana aparece para o Gil com o nome dela | |
| MS-10 | Emojis, figurinhas, GIFs opcionais | feito | seletor próprio sobre emojibase-data (`data.test.ts`: busca sem acento e recentes por pessoa); `StickerIT` (pacotes da instância, só WebP até 1 MB, só o dono gerencia, favoritas por pessoa, mensagem de figurinha, exclusão lógica); `GifIT` contra Tenor simulado (proxy da busca sem URL nem chave, prévia, host fora da lista 502, GIF vira mídia) e GIFs desligados por padrão (503); e2e emoji pelo campo, "Mais reações", criar pacote, enviar WebP e mandar figurinha | Tenor real não testado (sem chave e sem rede no sandbox), mesmo critério de AU-05 e MD-05 (provedor atrás da porta provado contra simulação); seletor sem tons de pele (ADR 0011) |
| MS-11 | Agendadas, silenciosas, rascunho sincronizado | feito | `ScheduledIT` (agendada sai na hora como silenciosa, só quem agendou vê e cancela, passado 400, bloqueio marca falha; rascunho só do dono e apagado ao enviar); e2e rascunho sobrevive a recarregar, ícone de silenciosa no outro navegador, agendar e cancelar pela lista | silenciosa não notifica (`NotificationIT`, passada 5) |
| MS-12 | Mensagens salvas | feito | `GroupIT.shouldKeepSavedMessagesAndReplyPrivately` (uma por pessoa, idempotente, invisível aos outros); e2e menu "Mensagens salvas" envia e passa no axe | |
| MS-13 | Atalhos de teclado e lista com `?` | feito | e2e `shortcuts.spec.ts` (Ctrl+K abre a paleta, busca "salvas" e abre, "?" mostra a lista e Esc fecha, "?" dentro do campo é texto) e `messages.spec.ts` (↑ edita a última, Esc fecha o painel de contato); Ctrl+V cola arquivo e arrastar e soltar vêm da Fase 3 (`media.spec.ts`) | |

## Fase 5 — Privacidade

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| PV-01 | Desfoque configurável por item | feito | e2e `privacy.spec.ts` (Alt+Shift+P liga e desliga `data-blur`, mensagem com `filter: blur`, foco do teclado revela e sair desfoca); `privacy.test.ts` (itens e atrasos); `PrivacyIT` guarda as escolhas | desfoque em CSS sem estilo inline (CSP); `prefers-reduced-motion` respeitado; revelar com atraso do mouse e toque longo sem teste automatizado |
| PV-02 | Bloqueio de tela | feito | `PrivacyIT.shouldManageScreenLock` (Argon2, PIN só com dígitos, troca exige o atual, hash nunca vai ao cliente, 403 uniforme, 429); e2e `privacy.spec.ts` (bloquear pelo botão e pelo atalho, PIN errado, sobrevive a recarregar, bloqueia ao esconder a aba, inatividade de 1 min com relógio controlado, desbloqueio por passkey com autenticador virtual, fundo `inert`, axe); README documenta a proteção visual | tudo desligado por padrão; passkey reutiliza o login (ADR 0013) |
| PV-03 | Conversa trancada | feito | `PrivacyIT.shouldLockConversations` (sai da lista, 403 em conversa/mensagens/sync/galeria, pasta só com token, token de outra pessoa não vale, código compartilhado, destrancar exige token, fechar a pasta invalida, 429, IDOR 404); e2e (trancar pelo painel, recarregar pede o código, código errado, some da lista) | notificações sem conteúdo em `NotificationIT` (passada 5); anexos seguem por id para membros (ADR 0013); NT-05 não pode guardar trancadas no IndexedDB |
| PV-04 | Visibilidade de visto por último, online, foto, recado | feito | `PrivacyIT` (foto e recado com todos/contatos/exceto/ninguém, exceção só entre contatos 400; visto por último nas quatro opções); `RealtimeIT.shouldHideOnlineByPrivacy`; e2e online escondido some no outro navegador |  |
| PV-05 | Reciprocidade no servidor | feito | `PrivacyIT.shouldApplyPresenceReciprocity` e `shouldApplyReadReceiptRules`; `RealtimeIT.shouldHideOnlineByPrivacy`; e2e (online e leitura recíprocos entre dois navegadores) | reciprocidade por par (ADR 0013) |
| PV-06 | Confirmação de leitura global/por conversa | feito | `PrivacyIT.shouldApplyReadReceiptRules` (global, sobreposição por conversa, padrão do grupo ligado, quem desliga no grupo sai do "todos" e não vê, Dados da mensagem, IDOR 404); e2e ficam no "Entregue"; seleção por conversa nos painéis |  |
| PV-07 | Quem pode me adicionar a grupos | feito | `PrivacyIT.shouldRespectWhoCanAddToGroups` (criar e adicionar recusados com `group-add-not-allowed`, permitido depois de virar contato, link de convite segue disponível) | a UI mostra o detalhe do erro sugerindo o link |
| PV-08 | Notificações sem conteúdo | feito | `PrivacyIT` guarda "ocultar conteúdo"; `NotificationIT.shouldHideContent` (só "Papinho"/"Nova mensagem", nada do texto nem do nome no push) | |
| SG-04 | Regras PV-* no servidor com testes de integração | feito | `PrivacyIT` (7), `RealtimeIT.shouldHideOnlineByPrivacy` e `NotificationIT` (PV-03 e PV-08 nas notificações) | |

## Fase 6 — Organização, busca, notificações e PWA

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| GR-05 | Arquivar, fixar, silenciar, favoritar, pastas, limpar/apagar | feito | `ChatListIT` (5: arquivada sai com mensagem nova de outra pessoa salvo "manter arquivadas", fixar até 3 com 409, arquivar solta, silenciar 8 h/1 semana/sempre com menções, favorita e pastas só da pessoa, IDOR 404, limpar mantendo favoritas, apagar some até a próxima mensagem); e2e `organize.spec.ts` (desktop e celular, axe) | silenciar notificando menções aplicado em `NotificationIT`; apagar grupo mantém a pessoa como membro |
| GR-06 | Busca e filtros (unaccent, pg_trgm, portuguese) | feito | `SearchIT` (3: sem acento, radical em português, trecho por trigrama, curinga escapado, só conversas próprias, sem apagadas/escondidas/trancadas, filtros de remetente, dia e tipo com páginas, IDOR 404); e2e filtros todas/favoritas/grupos/lista/arquivadas, busca global e dentro da conversa | índices GIN de texto completo e trigrama (`V202610190200`); conversas e contatos filtrados no cliente |
| NT-01 | Notification API e Web Push (VAPID) | feito | `WebPushCryptoTest` (exemplo da RFC 8291 byte a byte, decifra no lado do navegador, ponto fora da curva recusado, JWT VAPID verificado); `NotificationIT` (assinatura só de serviço conhecido por HTTPS, chave VAPID cifrada, push para quem recebe e não para quem envia, 410 apaga); e2e `pwa.spec.ts` (notificação com o app aberto, permissão só pelo botão) | push real para FCM/Mozilla/Apple não testado (sem internet no sandbox; o Chromium do Playwright não tem Push API em contexto anônimo) |
| NT-02 | Avisos de push dependente de internet e iOS | feito | Configurações > Notificações com os dois avisos (e2e confere o de internet; o de iOS aparece só no iOS fora do app instalado); README | detecção do iOS não testada em aparelho real |
| NT-03 | Configuração de notificações e Badging API | feito | `NotificationIT` (individual desligada, silenciada, menção com silêncio, silenciosa MS-11, solicitação de estranho, som); e2e som desligado chega como notificação silenciosa; contador pela Badging API na página e no service worker | por conversa = silenciar (GR-05); Badging sem verificação automatizada (o Chromium headless não expõe o contador) |
| NT-04 | PWA instalável com atualização controlada | feito | `vite-plugin-pwa` 1.3.0 (`injectManifest`, ADR 0014); e2e `pwa.spec.ts` (manifesto `application/manifest+json`, `standalone`, ícone maskable, capturas; service worker controla a página; app abre sem rede) | aviso "Nova versão" com "Atualizar" (sem `skipWaiting` cego) sem e2e da troca de versão; instalação em aparelho real não testada |
| NT-05 | Offline: IndexedDB por usuário, fila com clientMessageId | feito | e2e `pwa.spec.ts` (sem rede: aviso visível, texto fica "Enviando", recarregar abre conversas do IndexedDB; rede volta: chega uma vez só; logout apaga o banco; Cache Storage sem nenhuma resposta de `/api`) | só texto entra na fila; trancadas nunca guardadas; outra conta no navegador apaga os bancos anteriores |

## Fase 7 — Backup, admin, endurecimento e e2e completo

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| BK-01 | Exportar conversa em ZIP | feito | `ExportIT` (2: formato `dd/MM/aaaa HH:mm - Nome: texto` no fuso de negócio com os nomes de quem exporta, apagada para todos como aviso, sem o que a pessoa escondeu, mídia no ZIP só quando pedida, IDOR 404, trancada sem token 403); e2e `data.spec.ts` baixa o ZIP pelo menu da conversa | escrito em streaming (`StreamingResponseBody`), 10 exportações por hora por pessoa; visualização única e vencidas ficam de fora |
| BK-02 | Exportar dados da conta e excluir conta | feito | `AccountDataIT` (3: ZIP com `dados.json` de cada módulo e os arquivos, sem hash de senha, TOTP, códigos ou chaves; excluir exige senha, último admin 409, sessões caem, mensagens ficam sem dono e a conversa como "Conta apagada" sem envio, admin do grupo passa adiante; promover outro admin antes de sair); `PresenceIT` (conexão que cai depois da exclusão não grava nada); e2e `data.spec.ts` (baixar dados, senha errada avisa, exclusão leva ao login, o outro lado vê "Conta apagada") | cada módulo contribui com sua seção (`PersonalDataSection`); evento `AccountDeleting` para os módulos limparem antes |
| BK-03 | Backup e restauração da instância | feito | `scripts/backup.sh` (pg_dump em formato custom + arquivos + manifesto com SHA-256), `scripts/restore.sh` e `scripts/verify-backup.sh`, executado ponta a ponta nesta passada: instância com dados, backup, restauração em banco e pasta novos e conferência de contas, mensagens e arquivos; README seção Backup | `PAPINHO_PG_CONTAINER` usa o `pg_dump` 18 do contêiner quando o do host é mais antigo |
| AD-01 | Painel do admin | feito | `/admin`: modo de cadastro, cotas e janelas de edição/exclusão (`IdentityIT.shouldApplyRegistrationModes`), usuários com desativar (derruba sessões), redefinir senha (`IdentityIT`), gerar convite e tornar/tirar admin (`AccountDataIT`), fila de denúncias (`ModerationIT`), provedores de transcrição (`TranscriptionIT`), uso de disco só para admin (`MediaIT`); e2e `data.spec.ts` (convite, admin, disco, axe) e `moderation.spec.ts` | |
| AD-02 | Configurações da instância no banco | feito | `instance_settings` (Flyway) com padrões seguros: cadastro só por convite, cooldown, janelas, cota, checagem de senha vazada; `IdentityIT.shouldApplyRegistrationModes` altera e aplica; provedores de transcrição em tabela própria com chave cifrada (`TranscriptionIT`) | |
| AR-01 | WCAG 2.2 AA; RESPONSIVENESS.md aplicado | feito | e2e com axe em todas as telas com fluxo (painel, visualizador, galeria, gravação, admin, exportar, excluir conta); `a11y.spec.ts` passa o axe no tema escuro em 9 telas principais e com papel de parede nos dois temas; `muted-foreground` com 4,5:1 | WCAG é referência: leitor de tela real não testado |
| AR-02 | 320 px, alvos de 44 px, safe areas, teclado virtual | bloqueado | e2e 320 px em `/`, `/login`, `/register`, `/setup` e nas 9 telas principais (`a11y.spec.ts`); alvos de 44 px em `pointer: coarse` (`index.css`); `viewport-fit=cover`, `h-dvh` e `env(safe-area-inset-*)` no shell, cabeçalho e aviso de conexão | bloqueado só no que exige aparelho real: teclado virtual e safe areas (notch) não existem no Chromium do sandbox. Para desbloquear: abrir no iPhone e num Android, conferir o campo de mensagem com o teclado aberto e as bordas em paisagem |
| AR-03 | Fluxo só com teclado; axe sem críticas/sérias | feito | `a11y.spec.ts` "fluxo completo só com o teclado": entrar com Tab/Enter, Ctrl+K abre a conversa, enviar com Enter, menu com setas e Esc devolvendo o foco, indicador de foco visível; axe sem críticas/sérias em todos os specs | |
| QA-03 | E2E dos fluxos críticos | feito | passada 6: suíte completa contra instância nova com e-mail ligado, 63 verdes e 1 falha (inatividade no celular, espera corrigida em `013b03e` e 3/3 depois), com os novos `email.spec.ts` e `states.spec.ts`; passada 5: suíte completa contra instância nova no fim da passada 5: 58 verdes e 2 falhas (privacidade nos dois projetos, corrida corrigida em `ffdf737`); depois da correção, `privacy`, `chat` e `pwa` 13/13 contra instância nova; cobre cadastro por convite, conversa em dois contextos, grupo, upload, apagar para todos, privacidade, trancada, bloqueio de tela, organização e busca, PWA offline, notificações, exportar, excluir conta, painel do admin, tema escuro, 320 px e teclado, todos com axe; 4 de `/setup` rodam no CI | o teste de inatividade com passkey falhou uma vez no celular sob carga e passou 3/3 isolado; `perf.spec.ts` é opcional |

## Requisitos transversais

| ID | Descrição curta | Status | Evidência | Observações |
|---|---|---|---|---|
| SG-01 | IDOR, Problem Details, sem segredos em logs | feito | Problem Details em 401/403/400/500 (`SecurityIT`) e, desde a passada 6, também nos erros do Spring MVC (JSON ilegível, id que não é UUID, 405, 415) com textos dos bundles (`ProblemDetailsIT`); IDOR por módulo (`MessagingIT`, `ModerationIT`, `MediaIT`, `TranscriptionIT`, `GroupIT`, `ChatListIT`, `PrivacyIT`, `SearchIT`, `ExportIT`) e `OutsiderAccessIT` (41 ações por id sobre recursos alheios, todas 404 e sem efeito); auditoria de todos os controllers na passada 6 sem IDOR confirmado; arquivo de figurinha tirada fechado para quem não recebeu (`StickerIT`); e-mail, token e chaves fora dos logs (`EmailRecoveryIT`, `TranscriptionIT`); WebSocket só assina a própria fila (`WebSocketSecurityConfig`) | riscos baixos aceitos: a primeira transcrição feita no navegador por um membro vale para todos (AU-03, ADR 0008); uma assinatura de push muda de dono se outra conta enviar o mesmo endpoint secreto; `/blocks/{id}` distingue UUID inexistente (UUID v7 não é adivinhável); a imagem de prévia de link é cache global por hash da URL |
| SG-02 | CSP estrita, cabeçalhos, HSTS em produção | feito | `SecurityIT.shouldServeSpaShellWithSecurityHeaders`; e2e falha em qualquer violação de CSP | HSTS por `PAPINHO_HSTS=true` |
| SG-03 | Rate limit em login, convites, desbloqueio, envio, upload | feito | `RateLimiter` (Bucket4j) em login por IP e conta, cadastro, recuperação, convites e links de grupo (60/h), ações sensíveis, criação de upload (200/h), exportações (10/h), denúncias, prévias de link, transcrição, GIFs, envio de mensagens/enquetes (120/min, `MessagingIT.shouldRateLimitSending`) e códigos de desbloqueio de tela e de conversas (10/15 min, `PrivacyIT` 429); `IdentityIT` 429 com `retryAfterSeconds` e o cabeçalho `Retry-After` (passada 6); `MediaIT.shouldRateLimitUploads` (200 criações por hora, depois 429 com `Retry-After`, outra pessoa segue); `EmailRecoveryIT` (3 links por hora por conta); "digitando" limitado por frequência | `rate-limit-multiplier` só para e2e; limites em memória (uma instância, ADR 0004) |
| UI-02 | Temas, papel de parede, fonte, densidade | feito | Configurações > Aparência: tema claro/escuro/sistema, papel de parede (nenhum, pontos, grade, ondas, tom), fonte (pequena a muito grande) e densidade, guardados no aparelho e aplicados por atributos no `<html>` (CSP sem estilo injetado); `display-prefs.test.ts`; e2e `a11y.spec.ts` confere atributos, 18 px, papel de parede depois de recarregar e axe nos dois temas | preferência por aparelho, não sincronizada entre aparelhos |
| UI-03 | Estados carregando, vazio, erro, offline, sem permissão | feito | `QueryState` compartilhado (carregando, vazio, erro com o detalhe do servidor e "Tentar de novo", sem conexão, 403 como "sem permissão" sem tentar de novo) aplicado em admin, transcrição, notificações, bloqueados, sessões, favoritas, figurinhas, membros do grupo e busca na conversa; `query-state.test.tsx` (6); e2e `states.spec.ts` (409 com tentar de novo que recupera, rede cortada, 403, axe, desktop e celular); não encontrado (`smoke.spec.ts`); offline e reconectando (`pwa.spec.ts`) | telas que já tinham estados próprios (lista, conversa, contatos, galeria, busca global) mantêm os seus |
| UI-04 | Lista de mensagens performática | feito | `message-scroller` do shadcn com `content-visibility`, páginas de 50, paginação para trás automática ao chegar no topo e linhas memorizadas; `perf.spec.ts` com 2000 mensagens: abrir em ~0,6 s, subir até a primeira em ~24 s, maior tarefa longa 1,15 s (era 2,7 s antes da memorização), ~38 mil nós no DOM | se conversas maiores pesarem em aparelho fraco, o próximo passo é virtualizar |
| IN-01 | Textos externalizados; pt-BR completo, pronto para en | feito | `i18n.test.ts` garante as mesmas chaves em pt-BR e en; varredura da passada 6: nenhum texto de interface fixo fora do i18next e as 747 chaves literais usadas existem nos dois idiomas; `Spinner` e botão de rolagem do shadcn traduzidos; backend com `messages*.properties` (inclusive os e-mails) | restam fixos só nomes de idioma, teclas e o selo "admin"; título e corpo das notificações vêm do servidor |
| QA-01 | Backend: unitários, slice, integração, ArchUnit, Modulith, `./mvnw verify` | feito | `./mvnw -B -pl backend verify`: 78 unitários + 125 de integração verdes no fim da passada 6 (com `EmailRecoveryIT`, `OutsiderAccessIT`, `ProblemDetailsIT`, `ArchitectureTest` e `ModularityTests`), job backend do CI verde | todas as fases implementadas; cresce com cada correção |
| QA-02 | Frontend: lint, typecheck, Vitest | feito | `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test` (55), `pnpm build` locais no fim da passada 6; job frontend do CI | o `tsc -b` do build inclui os specs e2e |

## Dúvidas registradas para o dono

Nenhuma dúvida aberta.

Respondidas:
1. **groupId/pacote Java** — `io.github.labdev2025cmd` (o marcador `io.github.SEU_USUARIO` do `PROMPT.md` com o dono
   do repositório sem hífen) confirmado pelo dono em 2026-10-16.
