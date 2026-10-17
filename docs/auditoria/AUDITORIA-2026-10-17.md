# Auditoria independente do PROMPT.md — 2026-10-17

- **Objeto:** `labdev2025-cmd/papinho`, commit `3f4cb6d` (HEAD do `main` em 2026-10-16, conferido com `git ls-remote`).
- **Método:** um workflow de 25 agentes.
  - 1 agente montou a linha de base.
  - 12 agentes auditaram uma área cada, cada um num git worktree próprio, com instância, Postgres e porta próprios.
  - 12 verificadores adversariais, um por área, foram instruídos a refutar.
  - Por fim, consolidação e revisão por amostra feitas pelo auditor principal.
- **Regras de evidência:**
  - Um item só é CUMPRIDO com evidência executada nesta sessão mais a leitura do código de produção.
  - As regras do servidor foram provadas no servidor, com curl, STOMP cru e testes de integração.
  - Os requisitos críticos passaram por checagem de mutação: a regra é removida, o teste tem de falhar, e a mudança é desfeita.
- **Árvore limpa:** todas as mutações foram desfeitas. Os 16 worktrees que sobraram tinham `git status` vazio e foram removidos. Nada foi corrigido.
- **Nota de método:** os worktrees criados pelo workflow nasceram no commit inicial `668c666`, que só tem os guias. Todos os agentes perceberam e fizeram `checkout --detach 3f4cb6d` antes de auditar; isso está registrado nas notas de cada um. Nenhum veredito vem do commit errado.

**Legenda dos vereditos**
- **CUMPRIDO:** provado por execução e por leitura do código.
- **PARCIAL:** falta parte do requisito ou da prova.
- **STATUS INCORRETO:** o `STATUS.md` diz `feito`, mas a realidade é PARCIAL. Nos 25 casos desta auditoria o veredito real é PARCIAL.
- **NÃO CUMPRIDO** e **NÃO VERIFICÁVEL:** nenhum requisito ficou com estes vereditos finais.

## 1. Resumo executivo

### Placar final (88 IDs; a contagem bate com `grep -oE '\b[A-Z]{2,3}-[0-9]{2}\b' PROMPT.md | sort -u` = 88, nenhum faltando, nenhum inventado, nenhum duplicado)

| Seção | CUMPRIDO | PARCIAL | STATUS INCORRETO | NÃO CUMPRIDO | NÃO VERIFICÁVEL | Total |
|---|---|---|---|---|---|---|
| ID | 5 | 2 | 1 | 0 | 0 | 8 |
| CT | 1 | 0 | 3 | 0 | 0 | 4 |
| MS | 8 | 6 | 0 | 0 | 0 | 14 |
| MD | 4 | 1 | 1 | 0 | 0 | 6 |
| AU | 2 | 2 | 1 | 0 | 0 | 5 |
| GR | 3 | 2 | 1 | 0 | 0 | 6 |
| PV | 1 | 2 | 5 | 0 | 0 | 8 |
| NT | 3 | 1 | 1 | 0 | 0 | 5 |
| BK | 2 | 1 | 0 | 0 | 0 | 3 |
| AD | 2 | 0 | 0 | 0 | 0 | 2 |
| SG | 0 | 1 | 3 | 0 | 0 | 4 |
| RT | 1 | 1 | 1 | 0 | 0 | 3 |
| UI | 2 | 0 | 3 | 0 | 0 | 5 |
| AR | 0 | 1 | 2 | 0 | 0 | 3 |
| IN | 0 | 0 | 1 | 0 | 0 | 1 |
| QA | 3 | 1 | 1 | 0 | 0 | 5 |
| PK | 5 | 0 | 1 | 0 | 0 | 6 |
| **Total** | **42** | **21** | **25** | **0** | **0** | **88** |
Em resumo:
- **42 de 88 (48%)** se sustentam por inteiro.
- **46 (52%)** são parciais.
- **25** desses 46 estão marcados `feito` no `STATUS.md` sem se sustentar por inteiro.
- **Nenhum** requisito ficou NÃO CUMPRIDO: toda funcionalidade exigida existe e funciona no caminho principal.
- A verificação adversarial **rebaixou 9 de 37** CUMPRIDOs que reexaminou (seção 8).

**Achados:** 96 depois de deduplicar. São 0 Críticos, 3 Altos, 39 Médios e 54 Baixos. Todos os Altos foram reproduzidos de forma independente por um segundo agente.

### Os 10 achados mais graves
1. **[Alta] RT-01: o sync por `afterSeq` perde mensagens com envios concorrentes.**
   - Em `MessageService.java:308-316`, o `lastSeq` é lido depois da busca, numa transação READ COMMITTED.
   - Se uma mensagem é confirmada entre as duas leituras, o cliente (`cache.ts:56-58`) avança o cursor para `lastSeq` e pula essa mensagem para sempre.
   - Medido: 11 de 300 mensagens perdidas. A suíte fica verde com o bug. (A04 na seção 5)
2. **[Alta] PV-04/PV-05: presença escondida vaza pelo STOMP.**
   - `PresenceService.refresh` (`PresenceService.java:130-150`) envia um evento `presence` aos pares a cada transição, mesmo quando o online e o visto por último estão como `NOBODY`.
   - O conteúdo vem filtrado, mas o momento de cada entrada e saída vaza.
   - Com `lastSeen=EVERYONE`, ao ficar online chega `lastSeenAt:null`, o que revela o online escondido.
   - Reproduzido por dois agentes, um de PV e outro de SG.
3. **[Alta] MD-04: prévia de link sem limite de tempo total.**
   - O `SafeFetcher` só tem timeout por leitura (6 s em `SafeFetcher.java:49-52`).
   - Um site que envia 1 byte a cada 3 s prende a requisição por 120 s.
   - Com 10 prévias em paralelo, um usuário comum esgota o Hikari (`total=10, active=10, waiting=3`) e os outros usuários recebem 500.
4. **[Média] PV-03: conversa trancada vaza sem token por caminhos laterais.**
   - Uma resposta que cita a mensagem devolve o texto citado.
   - As agendadas saem sem token.
   - A exportação da conta inclui as mensagens.
   - Os anexos podem ser lidos por id.
   - Parte disso foi reproduzida de forma independente pelo verificador de SG.
5. **[Média] NT-05/PV-03: conversa trancada depois de guardada offline continua com o texto no IndexedDB.**
6. **[Média] MS-08: o token de visualização única não é de uso único.** Vale por 5 minutos e pode ser reutilizado (`MessageService.java:48`, `ViewOnceOpens.messageOfToken`), contra "token de uso único".
7. **[Média] MD-02/privacidade: fotos acima de 50 MB e HEIC/HEIF são guardadas e entregues com EXIF/GPS intactos.** Elas viram DOCUMENT em `UploadService.java:202-204`.
8. **[Média] AU-03/MS-08: um áudio de visualização única pode ter transcrição persistida**, se o dono a transcreveu antes de enviá-lo como view-once.
9. **[Média] PK-04: a checagem da impressão digital do `install-ca.sh` pode ser contornada.**
   - O script confere só o primeiro bloco `BEGIN CERTIFICATE` (`install-ca.sh:161-168`), mas instala o PEM inteiro (`:183-186`).
   - Um bloco `TRUSTED CERTIFICATE` extra entra no repositório de confiança do sistema.
   - Reproduzido com um curl aceitando `banco.example.com`.
   - Ressalva de ameaça: o próprio script vem por HTTP.
10. **[Média, sistêmico] Regras críticas sem teste de regressão:** dezenas de mutações na regra de produção sobreviveram à suíte inteira.
    - Presença e leitura antes de aceitar a solicitação (CT-02) e com bloqueio (CT-03).
    - Autorização por destino STOMP e validação de Origin (SG-01).
    - Sanitização do render (MS-01).
    - Chave do provedor de transcrição cifrada em repouso (AU-05).
    - Constraint UNIQUE do @usuario (ID-01) e "só admins adicionam" (GR-01).
    - Expiração do heartbeat (RT-03), `CONTACTS_EXCEPT` do PV-07 e o handler de 500 e a higiene de logs (SG-01): um log da senha em claro no login não derruba nenhum teste.

    O comportamento de hoje está certo na maioria desses casos, mas nada impede que ele regrida.

Outros que pesam: limite de 3 fixadas contornável ao sair e voltar a um grupo (GR-05); URL de mensagem apagada para todos fica no cache `link_previews` (MS-06); alvos de toque de 28–32 px no celular (AR-02); lista de mensagens sem virtualização, com 5000 mensagens = 90 mil nós e 885 MB de heap (UI-04); IDs em UUID v4 em `media_uploads` e `scheduled_messages` (ID-01); não existem testes slice (QA-01); o armazenamento S3 do ADR 0006 não existe.

### Conclusão
**O PROMPT.md não foi cumprido por inteiro, mas foi cumprido em grande medida.**
- Toda funcionalidade das seções 7, 8 e 9 existe e funciona no caminho principal. O build e as suítes (78 unitários, 135 de integração e 86 Vitest) ficam verdes, e o CI tem os 3 jobs.
- Ainda assim, o contrato exige mais que o caminho principal. Pede privacidade aplicada no servidor sem exceção (PV, SG-04), `seq` sem perda (RT-01), proteção contra SSRF e abuso (MD-04), visualização única de uso único (MS-08), 44 px (AR-02) e cobertura por testes de integração de toda regra PV. Nisso, 46 requisitos ficam parciais.
- O `STATUS.md` superestima o progresso: 25 itens `feito` não se sustentam. As evidências citadas muitas vezes são testes que existem, mas não falham quando a regra é removida.
- O diferencial anunciado do produto, a privacidade, é justamente a seção com pior placar: 1 de 8 cumprido por inteiro depois da verificação adversarial.

## 2. Ambiente e comandos executados

### Linha de base (Fase 0) (2026-10-16, HEAD 3f4cb6d, sem worktree)

Relatório completo: /tmp/claude-0/-home-user-papinho/ff28e175-65d3-55ca-ae13-0ef9187a2f4b/scratchpad/auditoria/00-linha-de-base.md

### Ambiente preparado
- **Máquina:** 4 CPUs, 15 GB.
- **Java:** o `java` do PATH é o 21. O JDK 25 (Temurin 25.0.4.1) ficou em /root/jdk25.
- **Docker:** `dockerd` iniciado (PID em /root/run/dockerd.pid) e **continua rodando**.
- **Imagens:** baixadas por mirror.gcr.io e retagueadas com o nome original: postgres:18-alpine, testcontainers/ryuk:0.14.0 e axllent/mailpit:v1.31.3.
- **Maven:** `MVNW_REPOURL` e um mirror em ~/.m2/settings.xml apontam para maven-central.storage-download.googleapis.com. Nenhum 429.
- **Node/pnpm:** node 24.22.0 e pnpm 10.34.6 vêm de frontend/target/node (o node do PATH é o 22; o .nvmrc pede 24.22.0).
- **Ambiente para os outros agentes:** `source /root/run/env.sh`.
- **Navegadores:** Chromium 1194 em /opt/pw-browsers, usado com PLAYWRIGHT_CHROMIUM_EXECUTABLE. Firefox 1555 e WebKit 2370 instalados com `playwright install firefox webkit` e depois `install-deps` (apt), os dois com exit 0 e funcionando.

### Comandos
| Comando | Exit | Tempo | Resultado |
|---|---|---|---|
| `./mvnw -B install -DskipTests` | 0 | 70 s | ok |
| `./mvnw -B -pl backend -Dskip.installnodepnpm -Dskip.pnpm verify` | 0 | 192 s | surefire **78 / 0 falhas / 0 erros / 0 pulados**; failsafe **135 / 0 / 0 / 0** (41 classes) |
| `pnpm install --frozen-lockfile` | 0 | 1 s | ok |
| `pnpm format:check` | 0 | 6 s | ok |
| `pnpm lint` | 0 | 17 s | ok |
| `pnpm typecheck` | 0 | 10 s | ok |
| `pnpm test` | 0 | 10 s | **86 testes passaram, 15 arquivos** |
| `pnpm build` | 0 | 16 s | ok |

### E2E completo
Duas instâncias novas, padrão e lan, com o mesmo banco, Mailpit e rate limit x10, como no CI. Rodou em 697 s: **77 passaram, 2 falharam, 8 pularam**, 87 no total.

| Projeto | Passou | Falhou | Pulou |
|---|---|---|---|
| primeiro-admin | 1 | 0 | 0 |
| desktop-chromium | 38 | 1 | 3 |
| mobile-chromium | 36 | 1 | 5 |
| desktop-firefox | 1 | 0 | 0 |
| desktop-webkit (pela instância HTTPS lan) | 1 | 0 | 0 |

- `setup.spec.ts` (contra a lan) e `email.spec.ts` (Mailpit) passaram.
- **Falha:** `view-once.spec.ts:75`. A asserção esperava `filter` igual a "none" e recebeu `blur(32.994px)`.
- Repeti só esse spec com `--repeat-each 3` nos dois projetos e falhou **6 de 6**.
- Em `view-once-viewer.tsx:65-77` o app desfoca no evento `window.blur`. Com o Chromium 1194 a janela perde o foco antes da asserção, e a asserção não usa poll.
- No CI (Chromium 1248 por `channel`) o mesmo teste passou. É um teste dependente do ambiente, não um bug de produto provado.

### CI
- Run #65 (38949437219, 3f4cb6d) com sucesso nos 3 jobs.
- No e2e desse run: 78 passaram, **1 flaky** (desktop-firefox native-controls com "pageerror: The operation was aborted.", escondido por `retries: 1`), 8 pularam.
- No run #64: 79 passaram, 8 pularam.

### Receita
A receita de instância própria foi testada de ponta a ponta. Ao rodar, apareceram quatro pegadinhas:
- os nomes @usuario `admin` e `dono` são reservados;
- `cd && java &` guarda o PID do subshell, não o do java;
- uma instância antiga ainda morrendo responde ao health na mesma porta;
- recompilar o JAR derruba a instância que roda a partir dele.

### Estado final
- Ficam: dockerd rodando, `backend/target/papinho.jar` construído e o jar do frontend instalado em ~/.m2.
- Nenhum JAR rodando e nenhum contêiner.
- `git status --short` e `git diff` vazios.
- Aprendizado: nada gravado no repositório, porque a auditoria exige a árvore limpa.

### Números medidos × STATUS.md

| Item | STATUS.md (passada 8) | Medido nesta sessão |
|---|---|---|
| Backend | 78 unitários + 135 de integração | 78/0/0/0 e 135/0/0/0 (confere) |
| Vitest | 86 | 86 em 15 arquivos (confere) |
| lint, typecheck, format, build | verdes | verdes (confere) |
| E2E | "79 verdes e 0 falhas, 8 pulados" | 77 verdes, 2 falhas, 8 pulados (87 no total) |

**E2E não se reproduz neste ambiente.**
- As duas falhas são `view-once.spec.ts:75`, em desktop e em celular: a asserção recebeu `blur(...)` quando esperava "none".
- Isolado, o spec falhou 6 de 6. É dependente do ambiente (Chromium 1194 + perda de foco), e no CI passa.

**No CI:**
- O run #65 (último no main) teve 78 verdes e 1 flaky (desktop-firefox native-controls com "pageerror: The operation was aborted.", que passou no retry), mais 8 pulados.
- O run #64, que o STATUS cita, teve 79 verdes e 8 pulados.
- O "0 falhas" vale para o run 64. O run seguinte só ficou verde graças a `retries: 1`.

**Cobertura do STATUS:** o STATUS cita "OutsiderAccessIT (41 ações)". Na contagem de testes isso é 1 método.

### CI

O `.github/workflows/ci.yml` tem 3 jobs, nos eventos push e pull_request para main.

| Job | O que roda |
|---|---|
| backend | `./mvnw -B verify` na raiz, com JDK 25 Temurin; também constrói o frontend |
| frontend | `pnpm install --frozen-lockfile`, format:check, lint, typecheck, test e build |
| e2e | depende do job frontend; ver detalhes abaixo |

Detalhes do job e2e:
- Usa os serviços postgres:18-alpine e mailpit v1.31.3.
- Roda `./mvnw -q install -DskipTests` e `pki init --ip 127.0.0.1 --network 127.0.0.0/8`.
- Sobe a instância padrão em 8080 e a lan em 8443/8081, no mesmo banco, com `PAPINHO_RATE_LIMIT_MULTIPLIER=10` e o e-mail ligado.
- Roda `playwright install --with-deps chromium firefox webkit` e depois `pnpm e2e` com SETUP_URL, MAILPIT_URL e HTTPS_URL.
- Publica sempre o relatório do Playwright.

Cada job roda o que o STATUS (QA-04) alega. Ressalva: `retries: 1` no CI faz o job passar mesmo com um teste instável.

**Último run no main:** 38949437219 (#65, commit 3f4cb6d, 2026-10-16 18:42), conclusão **success** nos 3 jobs.
- Backend: "Tests run: 78, Failures: 0, Errors: 0, Skipped: 0" e "Tests run: 135, Failures: 0, Errors: 0, Skipped: 0", em 3:35 min.
- Frontend: Test Files 15 passed.
- E2E: "Running 87 tests using 2 workers", com "1 flaky" (desktop-firefox native-controls.spec.ts:224, `pageerror: The operation was aborted.`, passou no retry #1), "8 skipped" e "78 passed (13.7m)".

**Run anterior:** 38947703749 (#64, 7e542f7), success, com 86 Vitest, 78 + 135 no backend e e2e 79 passed, 8 skipped.

### Testes pulados ou vazios (linha de base)

- backend: CertificateAuthorityTest.java:138 Assumptions.assumeTrue (pula sem openssl); aqui o openssl existe e rodou 7/0/0
- backend: ModularityTests.shouldWriteModuleDocumentation (ModularityTests.java:19) não tem asserção, só gera a documentação PlantUML
- backend: OutsiderAccessIT conta como 1 teste no relatório; as '41 ações' do STATUS rodam dentro de um único método
- backend: nenhum @Disabled, @EnabledIf ou @Tag; os candidatos sem asserção em MediaHelpersTest e AddressPolicyTest eram falsos positivos do script (têm assertThat)
- e2e perf.spec.ts:12/13 pulado sem PAPINHO_E2E_PERF e no celular (2 pulos)
- e2e whisper.spec.ts:24/29 pulado sem PAPINHO_E2E_WHISPER (2 pulos)
- e2e a11y.spec.ts:79 celular deitado só no mobile; :102 teclado só no desktop; :244 breakpoints só no desktop-chromium (3 pulos)
- e2e native-controls.spec.ts:231 pulado no mobile (1 pulo); :235 pula o WebKit sem PAPINHO_E2E_HTTPS_URL
- e2e email.spec.ts:61 pula SEM AVISO sem PAPINHO_E2E_MAILPIT_URL; setup.spec.ts:15 pula SEM AVISO sem PAPINHO_E2E_SETUP_URL (a suíte fica verde sem eles)
- e2e first-admin.setup.ts:9 pula se a instância já tem admin
- e2e privacy.spec.ts:279 e accounts.spec.ts:141 pulam fora do Chromium (autenticador virtual)
- frontend: nenhum .only, it.skip, describe.skip ou fixme; nenhum test/it sem expect (script sobre 105 chamadas)
- total: 8 pulados por desenho no e2e completo, iguais aos do CI

### Como rodar testes isolados (medido)

Antes de tudo: `source /root/run/env.sh` (JDK 25, MVNW_REPOURL e node/pnpm do frontend/target), e rode a partir de /home/user/papinho.

- **Classe unitária (5-6 s):**
  ```
  ./mvnw -B -pl backend -Dskip.installnodepnpm -Dskip.pnpm test -Dtest=PasswordPolicyTest -Dsurefire.failIfNoSpecifiedTests=false
  ```
  Resultado: Tests run: 2, Failures: 0.
- **Classe IT com failsafe e Testcontainers (32 s, precisa do dockerd):**
  ```
  ./mvnw -B -pl backend -Dskip.installnodepnpm -Dskip.pnpm verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PrivacyIT
  ```
  Resultado: Tests run: 7, 0 falhas.
- **Um método IT (23 s):** acrescente `'-Dit.test=PrivacyIT#shouldStartWithWhatsAppDefaults'`. Resultado: Tests run: 1.
- **Atenção:** `verify` passa pelo package e regrava `backend/target/papinho.jar`. Faça isso num worktree próprio ou rode o JAR a partir de uma cópia, como a receita faz. A suíte backend completa leva 192 s.
- **Um arquivo Vitest (3 s):**
  ```
  cd frontend && pnpm exec vitest run src/features/privacy/privacy.test.ts
  ```
  Resultado: 1 arquivo, 5 testes passaram. A suíte inteira leva 10 s.
- **Um spec e2e:** veja como_subir_instancia, item 4 (cerca de 33 s para chat.spec.ts mais o setup).
- **Nunca rode dois Playwright em frontend/ sem `--output` próprio.**

### Comandos executados por área

**01-ID-AD**
- git checkout -q --detach 3f4cb6d (o worktree nasceu em 668c666, sem código): ok
- docker run audit-pg-01-ID-AD (15511) e start.sh com o jar de referência em 18110 (x10), 18111 (x1) e 18112 (banco vazio para o e2e): 'Started PapinhoApplication'
- python3 -I t1.py..t14.py (API real, cookie jar por usuário): saídas citadas nas linhas
- psql: versões dos UUIDs (uuidv.sql), constraint UNIQUE/CHECK, instance_settings, username_reservations, invites (expires_at forçado no passado)
- mvnit.sh base PasswordPolicyTest IdentityIT,SecurityIT: exit 0, 2/0 + 12/0 + 21/0
- mvnit.sh m1 (sem UNIQUE): exit 0, 24/0, sobreviveu
- mvnit.sh m2 (NFC): exit 1, shouldEnforceUsernameRules 'expected: 409'
- mvnit.sh m3 (UUID RANDOM): exit 1, 'expected: 7'
- mvnit.sh m4 (BCrypt, NotFound, sem balde por conta): exit 1, 3 falhas
- mvnit.sh m5a (sem expiração): exit 1; m5bcd (sem limite de usos, padrão OPEN, sem CLOSED): exit 1, só 2 falhas (o padrão OPEN não foi pego)
- mvnit.sh m6b (sem recontagem): exit 0, 24/0, sobreviveu; m6a (sempre USER): exit 1, 2 falhas
- mvnit.sh ok2 EmailRecoveryIT,ProfileIT,AccountDataIT: exit 0, 4/0 + 4/0 + 3/0
- e2e.sh accounts.spec.ts profile.spec.ts data.spec.ts moderation.spec.ts --project=desktop-chromium: '11 passed (1.4m)'
- kill dos 3 jars pelo PID e docker rm -f audit-pg-01-ID-AD; git status --short e git diff vazios

**02-CT-BK**
- git checkout -q --detach 3f4cb6d (o worktree estava em 668c666) -> HEAD 3f4cb6d
- it.sh base 'ModerationIT,MessagingIT,ContactIT,GroupIT,AccountDataIT,ExportIT,RealtimeIT' -> Tests run: 31, Failures: 0
- M1 (mutualPeers com REQUEST) RealtimeIT,PrivacyIT,MessagingIT,PresenceIT,NotificationIT -> 31/0, sobreviveu
- M2 (peerReadSeq sem peerState) MessagingIT -> falha em shouldHandleMessageRequests:126, morta
- M3+M4 (readSeq STOMP e readAt do info) MessagingIT,RealtimeIT,PrivacyIT -> 23/0, sobreviveram
- M5 (blockedWithSomeone=false) MessagingIT,ModerationIT -> 2 falhas, morta
- M6 (canSeePhoto=true) ContactIT -> falha em :87, morta
- M7+M8 (grupo sem bloqueio + presença sem bloqueio) 6 classes -> só GroupIT:141 falhou; M7 morta, M8 sobreviveu
- mutants.sh: jars m1, m34 e m78 vazam presença/leitura em caixa-preta; o jar de referência não vaza
- s1.sh CT-01 por curl; s13.sh + qrdecode.py: QR decodificado = http://localhost:18120/add/chefe
- s2.sh CT-02 REST + STOMP (stomp.mjs); s3.sh CT-03; s5.sh CT-04 (5 e depois 2 cópias); s6.sh BK-01; s7.sh BK-02 + scan.sql
- backup.sh -> tar; restore.sh: banco cheio exit 1, corrompido exit 1, banco novo exit 0; up-r.sh + s11.sh: instância restaurada íntegra
- e2e moderation.spec.ts data.spec.ts accounts.spec.ts --project=desktop-chromium --project=mobile-chromium -> 19 passed (2.2m), --output pw-02-CT-BK
- Limpeza: kill do PID do jar, docker rm -f audit-pg-02-CT-BK; git status --short e git diff --stat vazios (CLEAN)

**03-MS-a**
- git checkout -q --detach 3f4cb6d (worktree estava no commit inicial) -> HEAD 3f4cb6d
- it.sh base 'MessagingIT,DisappearingIT,RealtimeIT,PrivacyIT' -> exit 0; 3/0, 10/0, 7/0, 6/0
- receita.sh (AG=ms-a, PG 15513 audit-pg-03-MS-a, porta 18130, APP_MESSAGING_EXPIRY_CHECK=5s) -> instância com chefe/ana/bia
- t1.sh -> 403 para não autor, 404 para quem está fora, edição ok
- t2.sh -> janelas 14/16 min e 47/49 h (200/409), apagar com anexo (banco zerado, 404), encaminhar 6x (forwardedMany a partir de 5)
- t3.sh -> disco antes/depois, job das temporárias com shedlock, PV-06 no info, grupo 403
- node xss.mjs -> nada injetado, formatação ok
- node status.mjs -> Enviando/Enviada/Entregue/Lida
- node fwd.mjs -> selos 4 simples + 2 'várias vezes'
- node jump.mjs (2x) -> original fora da tela após o salto
- mut.py M1..M10 (allmut.sh) -> M1-M4, M6, M8-M10 falham; M5 só MediaIT; M7 passa
- pnpm install; mutação MS-01 + pnpm test -> 86 passed; eslint exit 0; revertida
- pnpm e2e chat/messages/audio --project=desktop-chromium (instância vazia 18131) -> 4 passed
- pnpm e2e groups.spec.ts (instância recriada) -> 2 passed
- kill dos jars, docker rm -f audit-pg-03-MS-a; git status --short e git diff --stat vazios

**04-MS-b**
- git checkout --detach 3f4cb6d (o worktree estava em 668c666, o commit inicial) -> HEAD 3f4cb6d
- it.sh it-base.log ViewOnceIT,PollIT,StickerIT,GifIT,ScheduledIT,NotificationIT,GroupIT,MessagingIT -> Tests run: 32, Failures: 0, EXIT 0
- Mutação A (view-once reemitido, voters na anônima, silent no dispatcher, clearDraft) com ViewOnceIT,PollIT,NotificationIT,ScheduledIT -> Tests run: 14, Failures: 4 (as 4 mutações derrubadas)
- Mutação B (noStore, touch no voto, filtro sender_id) com ViewOnceIT,PollIT,ScheduledIT -> Failures: 2, Errors: 1 (as 3 derrubadas)
- Mutação C (media.delete, prazo de 14 dias) com ViewOnceIT -> Failures: 2 (as 2 derrubadas)
- git checkout -- backend depois de cada mutação; git status --short e git diff vazios
- receita.sh AG=audit-04-MS-b PG 15514 APP 18140 -> chefe, ana, bia, conversa
- bb.sh, bb2.sh e bb3.sh (curl caixa-preta MS-08..MS-14) e purge-check.sh (job removeu os anexos e os arquivos do disco)
- receita.sh USERS=0 APP 18141 e e2e view-once, shortcuts, messages, groups e chat em desktop-chromium -> 5 passed, 1 failed (view-once.spec.ts:75)
- e2e temporário zz-audit-ms.spec.ts (apagado) -> 2 passed (cliente da visualização única, SW, Ctrl+V, drop, emojis e recentes, sem aba GIF)
- kill do PID dos jars e docker rm -f audit-pg-04-MS-b; git status --short vazio

**05-MD-AU**
- git checkout --detach 3f4cb6d no worktree (estava em 668c666 sem STATUS.md); no fim, voltei ao branch do worktree
- inst.sh: Postgres audit-pg-05-MD-AU:15515 + jar de referência :18150 com -Djdk.net.hosts.file -> UP
- ifalias.py (ioctl): aliases 1.2.3.4, 10.99.0.1, 169.254.169.254, 192.168.77.1 no lo; fake_web.py em :80 -> self-test 200 nos 5 IPs
- ssrf.sh com ~35 URLs (privados, DNS, redirects, esquemas, porta, usuário, enorme, stall, gotejamento, og:image) -> só o público teve HIT; gotejamento 120,04 s
- dos.sh: 10 prévias lentas + GET da bia -> 500 em 90 s, HikariPool esgotado
- md01.sh/md01b.sh/md01c.sh: tus em pedaços, exe renomeado, nome malicioso, 1 GB real, 1 GB+1, 412/409/404, cota -> conforme
- md02.sh/md02b.sh: JPEG com GPS e PNG com tEXt/eXIf -> metadados removidos, thumbhash, miniatura
- md03.sh: galeria por abas, 404 para quem é de fora, 400 para aba inválida
- md06.sh/md06b.sh: EICAR aceito com ClamAV desligado; ligado, 422 e log FOUND; clamd parado, 500
- au.sh/au2.sh com fake_stt.py :18152: UA, Bearer, multipart, hotwords, health, cache, 404, chave cifrada no banco, Groq 25 MB 413, 502 sem a chave no log
- vo.sh: view-once normal 404 e 0 linhas; dono transcreve antes -> linha persistida (furo)
- mvt.sh unit SafeFetcherTest,AddressPolicyTest com 3 mutações -> falhas; sem mutação 53/0
- mvt.sh it MediaIT com 4 mutações -> 5 falhas; TranscriptionIT com 3 mutações -> falhas; ViewOnceIT com 1 e com 2 barreiras removidas
- mvt.sh it MediaIT,LinkPreviewIT,TranscriptionIT,ViewOnceIT sem mutação -> 16/17 (LinkPreviewIT falhou pelo meu servidor em :80); LinkPreviewIT de novo sem o servidor -> 4/0
- e2e.sh media.spec.ts audio.spec.ts links.spec.ts desktop+mobile -> 7 passed (1.7m)
- e2e.sh whisper.spec.ts desktop-chromium com proxy -> passou (30 s); whisper-webgpu -> falhou por timeout e depois passou (6.2m)
- Limpeza: kill dos jars pelo PID (29067, 13255) e dos servidores falsos, docker rm -f audit-pg-05-MD-AU, aliases derrubados, backend/target e frontend/node_modules do worktree removidos; git status --short e git diff vazios

**06-GR**
- git checkout -q --detach 3f4cb6d (o worktree estava no commit inicial)
- up.sh: Postgres audit-pg-06-GR :15516 + jar de referência :18160 -> 'up pid 7701'
- s1-grupos.sh (curl): permissões 403, saída sem aviso (7->7), responder em privado 201
- s2-convite.sh: PV-07 403 e 200, aprovação pending e 404, reset e revogar 404
- QrDecode.java (zxing core 3.5.4): decoded=http://localhost:18160/g/0iA3j9y6Tty-TkgQrCeiwA
- s4/s5/s6/s10 (transmissão): só contatos recebem; lista vencida 403; job apagou a mensagem vencida; PUT com data passada 200
- s7-lista.sh: 4ª fixada 409 (também na corrida), silêncio 8h/7d/sempre, keepArchived, limpar e apagar
- s8-busca.sh + psql: extensões e índices GIN; acento e trigrama ok; acao/mensagem/reuniões não acham; trancada 403/[]
- it.sh m1m5 (GroupIT, BroadcastIT): 2 falhas esperadas (mutações mortas)
- it.sh m2m4pv7 (GroupIT, PrivacyIT, OutsiderAccessIT): joinApproval e PV-07 mortas; onlyAdminsAddMembers SOBREVIVEU
- it.sh m3 (GroupIT, BroadcastIT, ChatListIT, SearchIT): 4 mortas
- it.sh m6: canEdit, contatos da lista, TTL da cópia e keepArchived mortas; estado LEFT na busca SOBREVIVEU
- it.sh m7 (NotificationIT): exceção de menção morta (timeout :168)
- e2e groups.spec.ts + organize.spec.ts, desktop e mobile-chromium, instância vazia :18161 -> '5 passed (1.3m)'
- kill 7701 13242; docker rm -f audit-pg-06-GR; git status --short e git diff vazios -> CLEAN

**07-PV**
- git checkout --detach 3f4cb6d no worktree (estava em 668c666, sem STATUS.md) -> ok
- ./mvnw -B -q -pl backend -Dskip.installnodepnpm -Dskip.pnpm verify -Dtest=NONE -Dit.test=PrivacyIT,RealtimeIT,NotificationIT -> exit 0; 7/0, 6/0, 7/0
- receita-07-PV.sh (Postgres audit-pg-07-PV:15517, jar de referência :18170, RATE_LIMIT_MULTIPLIER=1) -> usuários chefe, ana e bia criados
- pv03-a/b/c/d.sh (curl): 403 em todas as rotas de conteúdo sem token, mas scheduled, replyTo e account/export vazam; 10x403 e depois 429; lock_codes $argon2id
- pv03-ttl.sh: às 23:05:50, token ocioso desde ~22:54 -> 403
- pv04-a.sh: recado e visto por último nas 4 opções; exceção de não contato 400; PV-07 CONTACTS_EXCEPT 403 e EVERYONE 201
- Teste temporário RealtimeIT#auditPresenceSideChannel (desfeito): eventos de presença chegam mesmo com tudo em NOBODY
- mut.py com 19 mutações no backend: 15 mortas; sobreviveram pv03-ttl, pv05-read-stomp, pv06-group-default e pv06-info; git diff vazio depois de cada uma
- pnpm e2e privacy.spec.ts desktop+mobile no jar de referência (instância vazia :18171) -> 7 passed (1.9m)
- e2e-mut.sh (vite build + vite preview :18172 -> :18171): controle 3 passed; sem focus-within 2 failed; hover e reduced-motion 2 failed
- vitest privacy.test.ts: 5 passed; com a mutação de blurAttributes, 2 falhas (desfeito)
- pv06-a.sh: com o global desligado, readAt=null e peerReadSeq=null; com a sobreposição, 1; IDOR 404
- kill do PID do jar; docker rm -f audit-pg-07-PV; git status --short e git diff vazios

**08-NT-RT**
- git checkout --detach 3f4cb6d (o worktree veio em 668c666, sem código); no fim git checkout worktree-wf_751b4ece-653-9 -> limpo
- receita-08.sh AG=a08 PG_PORT=15518 APP_PORT=18180 RLM=100 -> instância com chefe, ana, bia e conversa
- python3 rt08.py parallel <conv> 40 -> 40x201, seqs 2..41; psql -> 41\|41\|1\|41\|41
- python3 rt08.py samecid <conv> 20 -> 200:19, 201:1, 1 id; psql count=1
- python3 rt08.py crossconv <c1> <c2> 30 -> (201,500) x30
- python3 rt08.py syncrace <conv> 300 -> 11 perdidas; 200 -> 14 perdidas, SALTO afterSeq=502 lastSeq=503
- it08.sh mutA MessagingIT#sync+concurrent -> Errors 1 (uq_messages_conversation_seq)
- it08.sh mutB MessagingIT#shouldSyncBySeqAndDeduplicate -> Failures 1 (:63)
- it08.sh mutC idem -> Failures 1 (:55 expected 200)
- it08.sh mutD RealtimeIT#shouldTrackPresence -> Failures 1 (:160)
- it08.sh mutE RealtimeIT,PrivacyIT -> 13/13 passaram (mutação sobreviveu)
- it08.sh notif NotificationIT -> 7/7; mutF -> shouldHideContent:140 falhou
- ut08.sh WebPushCryptoTest -> 4/4
- curl manifest, ícones, screenshots, sw.js, endpoints de push (400/400/204/401)
- pnpm exec vitest run cache.test.ts sync.test.ts -> 10 passaram
- playwright pwa.spec.ts + audit08.spec.ts (desktop-chromium, instância 18180): pwa 3/3 passou; audit: permissão, Badging, presença oculta, iOS, IndexedDB e trancada, Cache Storage, troca de conta, heartbeat 50 s
- build08.sh n1 (sw guarda /api) + run08m.sh 18181 + pwa.spec offline -> falhou em :70
- build08.sh n2 (sem onLogout(clearOfflineData)) + run08m.sh 18182 + pwa.spec offline -> falhou em :114
- limpeza: jars 25328/27199/28471/28012 parados, docker rm -f audit-pg-08-NT-RT, spec temporário apagado, git status/diff vazios

**09-SG**
- git reset --hard 3f4cb6d (o worktree estava no commit inicial 668c666) → HEAD 3f4cb6d
- docker run audit-pg-09-SG:15519 + java -jar cópia do jar de referência na porta 18190 (rate limit x1) → Started PapinhoApplication
- setup.sh/setup2.sh/setup3.sh (curl): chefe admin, ana, bia, carol e os recursos da ana → ids.env
- idor.sh carol (cerca de 90 chamadas) + idor2.sh → 404 em todos os recursos alheios, 403 em admin e actuator, estado da ana intacto
- node stomp.mjs + hs.sh → handshake 401 sem cookie, 403 com Origin de outro site, 101 sem Origin; CONNECT sem CSRF dá ERROR; SUBSCRIBE alheio dá ERROR; SEND alheio sem efeito
- hdr.sh → cookies __Host-SESSION HttpOnly Secure SameSite=Lax; CSP e cabeçalhos; denyAll 403
- psql user_accounts → $argon2id$v=19$m=16384,t=2,p=1
- rl.sh + rl2.sh → 429 medidos em login, convites, invites/status, PIN, trancada, upload e envio (em paralelo)
- pd.sh + pd500.sh → Problem Details em 400/404/405/406/415/412/413/500; firewall e Tomcat fora do padrão; NUL gera 500
- grep no papinho.log → 0 ocorrências de nomes, corpos, senha ou tokens
- up.sh → nome sanitizado, download como attachment com octet-stream e nosniff, EXIF/GPS removido, 413 acima de 1 GB
- ssrf.sh + http.server na porta 80 → available:false em todos os internos, 0 acessos
- instância 18191 com PAPINHO_HSTS=true + X-Forwarded-Proto:https → Strict-Transport-Security max-age=31536000; includeSubDomains
- git log -p --all + sec.sh → nenhum .pem/.key/.p12/.env ou chave privada no histórico
- it.sh A (mutações de Origin, STOMP, CSP, Permissions-Policy, HSTS e digitando) → 36/36 passaram
- it.sh B (CSRF, STOMP CSRF, denyAll, BCrypt, SameSite, limites, 500) → 10 falhas esperadas; convites e 500 não pegos
- it.sh C (limite do bloqueio de tela, mídia legível) → PrivacyIT:260, MediaIT, StickerIT, TranscriptionIT e OutsiderAccessIT falharam
- it.sh D (requireMember) → OutsiderAccessIT, MessagingIT, ChatListIT e SearchIT falharam
- it.sh E (PV-03/04/05/06/07) → 6 falhas, uma por regra; it.sh F (PV-08) → NotificationIT.shouldHideContent falhou
- it.sh G (log da senha em claro) → 37/37 passaram
- kill do java (PIDs 25754 e 25919), docker rm -f audit-pg-09-SG, git checkout -- backend/src depois de cada lote; git status --short e git diff vazios

**10-UI**
- git reset -q --hard 3f4cb6d no worktree (criado no commit inicial 668c666) -> HEAD 3f4cb6d
- pnpm install --frozen-lockfile -> Done in 2.5s
- pnpm lint com arquivo plantado -> 21 errors, exit 1; arquivo removido
- vitest run lint/native-controls.test.ts -> 26 passed; com mutação no eslint.config.js -> 2 failed \| 24 passed; revertido
- ui-mutate-check.cjs (AST) -> 40 mutateAsync: 33 try/catch, 3 .catch/.then com toast, 2 logout .catch(()=>undefined)
- ui-up.sh (JAR de referência, padrão + lan) e ui-e2e.sh ref native-controls/states/a11y/smoke/setup --workers=3 -> 27 passed, 4 skipped, EXIT=0 (4.0m)
- ui-seed.sh 5000 + ui-probe.mjs perf -> openMs 496, scrollToFirst 81404 ms, 90183 nós, heap 885 MB, maior tarefa 1874 ms, CSP [] e console sem erros
- ui-states2.mjs -> lista e conversa com 403/offline/500 dão um texto genérico, retry 0
- ui-build.sh (package com as mutações: video controls, .dark color-scheme light, md->lg) -> BUILD EXIT=0; fontes revertidas
- ui-e2e.sh mut (desktop-chromium + firefox) -> 3 failed (a11y breakpoints, native-controls x2), 1 passed
- ui-silent.mjs (star 409) -> avisosVisiveis []
- ui-theme.mjs (JAR de referência) -> Escuro dark/dark, persistiu depois de recarregar, Claro light, Do sistema dark
- curl -sI /login -> Content-Security-Policy ... style-src 'self' ...
- kill pelos PIDs, docker rm -f audit-pg-10-UI; git status --short e git diff vazios

**11-AR-IN**
- git checkout --detach 3f4cb6d (o worktree estava no commit inicial 668c666) -> HEAD 3f4cb6d
- pnpm install --frozen-lockfile (worktree) -> ok
- bash up-11.sh (Postgres audit-pg-11-AR-IN:15521 + jar :18210) -> 'ok pid 6897'; depois reiniciado com a origem 18211 -> 'ok pid 13536'
- pnpm e2e a11y.spec.ts --project=desktop-chromium --project=mobile-chromium -> 10 passed, 3 skipped
- playwright test zz-audit-11.spec.ts (temporário; axe claro/escuro em 18 telas/modais, 320 px, alvos de toque, teclado com Tab real) -> 4 passed; resultados em JSON
- playwright test zz-audit-11b.spec.ts (320 px com texto longo e /admin) -> 1 passed; /admin MAIN 383>320
- node in01-keys.mjs -> 927/927 chaves, 0 diferenças, 40 plurais, 771 chaves literais e todas existem
- bash in01-curl.sh -> erros em pt-BR e en como Problem Details com textos dos bundles
- diff das chaves dos 3 messages*.properties -> IGUAIS; cmp messages.properties com messages_pt_BR -> idênticos
- checagem dos 60 slugs de ApiException no messages_en -> nenhum faltando
- build do CSS em dist: :hover fora de @media (hover:hover) só em background-color, nenhum que revele
- mut-11.sh base -> 3 passed; foco -> FALHOU; axe aria-label -> FALHOU; tabindex no login -> FALHOU; tabindex no composer -> PASSOU (teste frágil)
- mut-in01.sh -> chave removida do en: FALHOU; _one removido: FALHOU; texto fixo no JSX: 86/86, lint 0, tsc 0
- mut-in01c.sh -> chave removida dos dois idiomas: 86/86, lint 0, tsc 0
- kill do jar pelo PID; docker rm -f audit-pg-11-AR-IN; spec temporário removido; git status e git diff vazios

**12-PK-QA-geral**
- git reset --hard 3f4cb6d no worktree (ele estava no commit inicial 668c666) -> HEAD 3f4cb6d, igual ao main remoto (git ls-remote)
- java -jar papinho.jar pki init --dir .../pk12/pki --ip 192.168.10.20 --network 192.168.10.0/24 -> CA criada, SHA-256 41:0C:..., chaves 600
- openssl x509 -text (CA e servidor) -> P-256, 10 anos, NC críticas, servidor com 397 dias e SAN DNS+IP
- forge.sh: openssl verify de folhas forjadas -> 'permitted subtree violation' para evil.example.com, 10.0.0.5 e google.com
- mvn test CertificateAuthorityTest,PkiCommandTest com 2 mutações -> 12 testes, 4 falhas; revertido
- mvn verify -Dit.test=LanSetupIT com a mutação do filtro HTTP -> 1 falha (expected 301); revertido
- mvn verify -Dit.test=LanSetupIT com a checagem do fingerprint removida -> 7/7 verdes (mutação sobreviveu); revertido
- instância lan (18221/18220) + curl --http2 -v -> 'ALPN: server accepted h2', 'HTTP/2 200'; varredura da porta HTTP -> 301 em tudo fora de /setup
- Debian em contêiner (deb-test.sh, deb-mitm.sh) -> instalar/reinstalar/desinstalar OK; CA trocada recusada; bloco TRUSTED anexado aceito e curl para banco.example.com = 200
- verify-install-ca.sh (Fedora 42 e Arch) -> 'Todas as distribuições passaram.'
- jar sem perfil (18222) -> /setup/** 404 problem+json
- mvn test ModularityTests,ArchitectureTest com mutação de dependência proibida e @Autowired -> as 2 classes falham; revertido
- pnpm install/lint/typecheck/test no worktree -> exit 0, 86 testes
- e2e accounts/chat/groups/media/messages/privacy (desktop-chromium, banco vazio) -> 13 passed
- mvn package do jar mutado (deletedAt) + e2e messages.spec.ts -> 1 failed; revertido
- e2e setup.spec.ts contra a lan -> 4 passed
- gh run list/view -> run 38949437219 com os 3 jobs verdes; runs vermelhos reais no histórico
- mvn dependency:list + varredura do package.json -> só onnxruntime-web 1.31.0-dev
- psql information_schema -> 73 timestamptz, 3 timestamp sem fuso
- gh api commits paginado (226) + arquivos de cada commit (1539 entradas, 729 caminhos) -> nada proibido; 52 assuntos > 72
- comparação de IDs PROMPT x STATUS -> 88/88, 5 colunas
- limpeza: jars mortos pelo PID, contêineres pk12-deb e audit-pg-12-PK-QA-geral removidos; git status --short e git diff vazios


## 3. Matriz completa por ID

Contagem: 88 linhas, igual aos 88 IDs únicos do `PROMPT.md`.

Coluna "Verificação adversarial":
- `—`: não reexaminado;
- `mantido`: o verificador não conseguiu refutar;
- `refutado → X`: rebaixado.

| ID | Requisito (resumo) | Alegação do STATUS | Back-end | Front-end | Testes | Evidência executada | Mutação | Veredito da área | Verificação adversarial | **Veredito final** | Lacunas |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ID-01 | Cadastro com @usuario único, normalizado (NFKC, minúsculas, [a-z0-9._], 3–32), lista de reservados, unicidade por constraint, IDs internos em UUID v7. | feito; IdentityIT (unicidade sem caixa, reservados, formato, UUID v7); e2e accounts.spec.ts | identity/Username.java:36-47 (NFKC+toLowerCase(ROOT), regex); identity/ReservedUsernames.java:64-78; identity/IdentityService.java:342-354 (ensureAvailable); db/migration/identity/V202610151601__create_identity.sql:15-17 (UNIQUE + CHECK de formato); identity/UserAccount.java:26 (VERSION_7). IDs v4: media/UploadService.java:99, messaging/ScheduledMessageService.java:60, profile/ProfileService.java:93 (chave do arquivo) | frontend/src/routes/register.tsx (checagem de disponibilidade, coberta pelo e2e accounts.spec.ts) | IdentityIT.shouldEnforceUsernameRules (409 para ANA.SILVA e Ａｎａ.silva, reservados e formato) e shouldMakeFirstUserAdmin (versão 7 de user_accounts.id). Nenhum teste prova a constraint do banco: a M1 sobreviveu. | API: 'Ana.Silva' vira "ana.silva". 'ＡＮＡ.ｓｉｌｖａ' e '@ANA.SILVA' dão 409 username-unavailable. 'ab', 33×a, 'ana-silva', 'a..b', '.ana', 'ação' dão 400 username-invalid; 32×b dá 201. SQL direto: 'duplicate key value violates unique constraint "uq_user_accounts_username"' e 'violates check constraint "ck_user_accounts_username_format"' para 'ANA.SILVA'. Versões no banco: 'user_accounts.id versoes=7', 'media_uploads.id versoes=4', 'scheduled_messages.id versoes=4'. Corrida com o mesmo nome (6 em paralelo): '[(201,'ok'), (500,'urn:papinho:problem:internal') x5]'. | M1: tirei o UNIQUE da migration e rodei IdentityIT+AccountDataIT, 'Tests run: 24, Failures: 0', a mutação SOBREVIVEU. M2: troquei NFKC por NFC, shouldEnforceUsernameRules FALHOU ('expected: 409'). M3: UserAccount com Style.RANDOM, shouldMakeFirstUserAdmin FALHOU ('expected: 7'). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. (1) Os 'IDs internos em UUID v7' não valem para tudo: media_uploads e scheduled_messages usam UUID v4, e as chaves de arquivo de perfil também (o ADR 0006 pede v7). (2) A unicidade por constraint existe e funciona, mas nenhum teste a garante (a M1 sobrevive). (3) Na corrida pelo mesmo nome, quem perde recebe 500 'internal' em vez de 409, e o @usuario aparece no log ('Key (username)=(mesmo.nome0)'). |
| ID-02 | Nome de exibição, foto (recorte, sem EXIF, miniaturas) e recado, separados do @usuario. | feito; ProfileIT (EXIF removido, 512 e 96 px, não-imagem 422); e2e profile.spec.ts | profile/ProfileService.java:88-103 (squareJpeg 512 + thumbnail 96, recodifica); profile/ProfileController.java:46-75; recado limitado a 140 em ProfileController.java:79 | diálogo 'Ajustar foto' (recorte no cliente), exercitado em frontend/e2e/profile.spec.ts:26-48 | ProfileIT 4/0. O e2e profile.spec.ts passou (recorte, salvar foto, recado). | Upload de um JPEG 1200x900 com EXIF Make/Model/GPS. Saída: 'p512.jpg JPEG 512x512' e 'p96.jpg JPEG 96x96'. 'grep -c MarcaSecreta' deu 0 nos arquivos servidos e nos guardados em data/files/profiles. Um arquivo que não é imagem deu 422 image-invalid. DELETE /profile/photo deu hasPhoto:false e o diretório ficou vazio. PUT /profile devolveu displayName 'Bia Souza' e about 'Disponível'. O @usuario não mudou. ProfileIT: 'Tests run: 4, Failures: 0'. E2E: '11 passed (1.4m)' (inclui profile.spec.ts). | não feita: requisito não crítico | CUMPRIDO | mantido | **CUMPRIDO** | O recorte é só no cliente: o servidor recorta ao centro para quadrado. Fotos HEIC dependem do navegador. Não testei em aparelho real. |
| ID-03 | Troca de @usuario com cooldown configurável; o nome antigo fica reservado por 30 dias. | feito; IdentityIT.shouldReserveOldUsername; e2e profile.spec.ts | identity/IdentityService.java:184-209 (cooldown + reserva de 30 dias, USERNAME_RESERVATION em :41); identity/UsernameReservation.java:45-47 (blocks); admin/UpdateInstanceSettingsRequest.java:94 (cooldown 0..365) | configurações de perfil (troca e aviso do cooldown), em frontend/e2e/profile.spec.ts:52 | IdentityIT.shouldReserveOldUsername passou na linha de base (21/0). O e2e profile.spec.ts passou. | 'ana troca 200 ana.nova 2026-11-15T21:54:44Z'. 'bia pega nome antigo 409 username-unavailable', também com variação NFKC. No banco, 'ana.silva \| 29 days 23:59:59'. 'ana troca de novo (cooldown) 409 ... em 30 dia(s)'. Com reserved_until no passado, o nome ficou {'available': True} para a cris. Com cooldown 0, 'ana troca 200 ana.terceira'. Com cooldown 2, '409 ... em 2 dia(s)'. | não feita: requisito não crítico | CUMPRIDO | mantido | **CUMPRIDO** | A troca de @usuario não pede senha (o requisito não pede). Quando a conta é excluída, o ON DELETE CASCADE apaga a reserva, então os nomes ficam livres na hora; está fora do texto de ID-03. |
| ID-04 | Login por senha (Argon2, mínimo de 12, checagem opcional de senha vazada) e por passkey; Bucket4j; erros uniformes. | feito; IdentityIT (erro uniforme, 429 com Retry-After, senha <12), SecurityIT (Argon2), PasswordPolicyTest; e2e da passkey com autenticador virtual | shared/security/SecurityConfig.java:57-59 (Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8); identity/PasswordPolicy.java:16-35 (12..128, HIBP opcional); identity/IdentityService.java:137-166 (dummyHash, a mesma BadCredentials para inexistente, senha errada e conta desativada); shared/security/RateLimiter.java:27-28,71-82 (Bucket4j, login por IP 20/15min e por conta 10/15min); identity/WebAuthnConfig.java, PasskeyController.java | login/cadastro e passkey, em frontend/e2e/accounts.spec.ts:96 e :136 | SecurityIT.shouldHashPasswordsWithArgon2, IdentityIT.shouldUseUniformLoginErrors, shouldRateLimitLogin, shouldRejectShortPassword; PasswordPolicyTest ('curta demai' com 11 caracteres). O e2e accounts.spec.ts passou, inclusive a passkey. | No banco: '$argon2id$v=19$m=16384,t=2,p=1'. Senha de 11 caracteres: '400 password-weak'. Usuário inexistente e senha errada: os dois '401 {detail: Usuário ou senha incorretos., type: urn:papinho:problem:bad-credentials}', com 'corpos idênticos: True' e mediana de 32.0 ms contra 33.2 ms. Conta desativada com a senha certa: o mesmo 401. Com rate limit x1: 'bia [401 x10, 429]', 'Retry-After 89', e a senha certa depois disso também deu 429. Com a checagem de senha vazada ligada: 'password1234' deu '400 password-compromised'. E2E: '11 passed' (inclui 'passkey: cadastra com autenticador virtual e entra sem senha'). | M4 (três mutações, uma por teste): BCrypt no lugar de Argon2, shouldHashPasswordsWithArgon2 FALHOU. Inexistente lançando NotFound, shouldUseUniformLoginErrors FALHOU ('expected: 401'). Sem LOGIN_BY_ACCOUNT, shouldRateLimitLogin FALHOU ('expected: 429'). Desfeito. | CUMPRIDO | mantido | **CUMPRIDO** | A igualdade de tempo (dummyHash) não tem teste. A passkey não foi testada em aparelho real. O balde por conta deixa qualquer um, de qualquer IP, bloquear o login da vítima por até 15 minutos com 10 tentativas (DoS de bloqueio de conta, severidade baixa). |
| ID-05 | Códigos de recuperação no cadastro, TOTP opcional, e-mail opcional (desligado por padrão). | feito; IdentityIT (TOTP, código de recuperação); EmailRecoveryIT (GreenMail); e2e email.spec.ts | identity/IdentityService.java:114 e :361-382 (10 códigos em Argon2), :137-163 (TOTP de uso único), :228-256 (recover); identity/EmailRecoveryService.java; MailProperties (desligado por padrão) | diálogo dos 10 códigos e TOTP (e2e accounts.spec.ts:96) | IdentityIT.shouldRequireTotpAfterEnrollment e shouldRecoverWithRecoveryCode (linha de base 21/0). EmailRecoveryIT 4/0, rodado nesta sessão. | 'cadastro 201 códigos 10'. '/instance' mostra 'emailRecovery': False. 'login sem código totp-required', 'login com código 200', 'mesmo código de novo code-invalid'. Código errado e usuário inexistente: o mesmo '400 code-invalid'. 'recover ok 204', 'sessão antiga após recover 401', 'reuso do código 400', 'totpEnabled False'. Reset por e-mail: '503 email-disabled'. EmailRecoveryIT: 'Tests run: 4, Failures: 0'. | não feita: requisito não crítico | CUMPRIDO | — | **CUMPRIDO** | Não testei SMTP com provedor real, só GreenMail no IT. |
| ID-06 | Sessões ativas: dispositivo, data, IP aproximado, revogar uma, encerrar todas as outras. | feito; IdentityIT.shouldManageSessions; e2e profile.spec.ts | identity/SessionService.java:41-76 (findByPrincipalName, id público SHA-256, deleteById + fechar o WebSocket); identity/SessionController.java:124-140; identity/SessionLogin.java:58-62 (só o IP aproximado) | frontend/src/features/account/components/sessions-settings.tsx:44-110 (device, lastAccessedAt, approximateIp, revogar uma, encerrar as outras) | IdentityIT.shouldManageSessions (21/0). O e2e profile.spec.ts passou (encerrar as outras derruba o outro contexto). | A lista trouxe 'Chrome no Windows', 'Safari no iOS', 'approximateIp': '127.0.0.0/24' e current True. Revogação pela bia (IDOR): '404', e a ana seguiu com 200. Revogação da a2: '204', depois 'a2 /me 401' e 'a2 POST com cookie revogado 401', com a3 e a1 em 200. 'encerrar outras 200 {'revoked': 2}' e depois 'a3 /me 401 \| a1 200'. | não feita: requisito não crítico | CUMPRIDO | — | **CUMPRIDO** | O IP aproximado é um /24 (bom para a privacidade, pouco informativo). Não verifiquei o 'IP real atrás de proxy' nesta área. |
| ID-07 | Modos de cadastro aberto, somente convite (padrão) e fechado; convites por link e QR com expiração e limite de usos; botão 'Convidar amigos'. | feito; IdentityIT (aberto/convite/fechado; revogado, esgotado e vencido recusados; QR em SVG); e2e com o convite usado em outro contexto | identity/IdentityService.java:95-107 (modos); identity/InviteService.java:105-151 (1h..30d, 1..100, SHA-256 do token, lock de linha); identity/Invite.java:267-276; db/migration/admin/V202610151600__create_instance_settings.sql:19 (padrão INVITE_ONLY); identity/InviteController.java:253-281 (url + qrSvg) | diálogo 'Convidar amigos' (pt-BR.json:114/143, e2e data.spec.ts:107); frontend/src/routes/register.tsx:62-80 | IdentityIT.shouldRequireInviteAfterFirstUser, shouldRejectRevokedOrExhaustedInvite, shouldApplyRegistrationModes. O DatabaseCleaner.java:20 força INVITE_ONLY antes de cada teste, então o padrão real da migration não é testado. | Banco novo: 'INVITE_ONLY \| 30 \| 15 \| 48 \| 5368709120 \| f'. 'OPEN sem convite 201', 'CLOSED sem convite 403 registration-closed', 'INVITE_ONLY sem convite 403 invite-invalid', 'INVITE_ONLY com convite 201'. maxUses 2: '3o uso 403 invite-invalid'. 1 uso com 5 em paralelo: '[201, 403, 403, 403, 403]'. Revogado e vencido: 403, com status {'usable': False}. A bia revogando um convite do admin: 404. validHours 0/721 e maxUses 101: 400. qrSvg '<svg'. Achado: 'CLOSED admin cria convite 201', status {'usable': True}, mas 'CLOSED com convite do admin 403 registration-closed'. | M5a: usable() sem a expiração, shouldRejectRevokedOrExhaustedInvite FALHOU. M5c: sem uses<maxUses, FALHOU ('expected: 403'). M5d: sem o check de CLOSED, shouldApplyRegistrationModes FALHOU. M5b: padrão da migration trocado para OPEN, a mutação SOBREVIVEU (nenhum teste falhou). Desfeito. | PARCIAL | — | **PARCIAL** | O comportamento atende ao texto, mas o 'somente convite (padrão)' não tem teste que o guarde: a M5b sobrevive porque o DatabaseCleaner reescreve o modo. O padrão só foi provado aqui, consultando o banco. No modo fechado, o admin consegue criar convites, e o /invites/status diz usable:true, mas o cadastro com eles é recusado. Isso contradiz o JavaDoc de InviteService.java:99 ('o convite reabre a porta') e a tela register.tsx:65, que mostra o formulário inteiro antes de recusar. |
| ID-08 | O primeiro usuário cadastrado vira admin. | feito; IdentityIT.shouldMakeFirstUserAdmin; e2e first-admin.setup.ts; trava do primeiro usuário 'nos dois sentidos' | identity/IdentityService.java:95,111-112 (firstUser ? ADMIN : USER) e :124-130 (count, pg_advisory_xact_lock, recontagem) | setup do primeiro admin (e2e first-admin.setup.ts, projeto primeiro-admin) | shouldMakeFirstUserAdmin e shouldSerializeFirstRegistration. O DisplayName diz 'dois ao mesmo tempo não viram admin', mas o teste usa um cadastrante só, contra uma trava presa. | Instância vazia com 8 cadastros em paralelo, sem convite: 'corrida1 201' e 7 vezes '403 invite-invalid'. No banco, uma única linha 'corrida1 \| ADMIN'. O e2e primeiro-admin passou ('11 passed'). | M6a: sempre Role.USER, shouldMakeFirstUserAdmin e shouldSerializeFirstRegistration FALHARAM. M6b: 'return true' depois da trava, sem recontar (vários admins numa corrida), IdentityIT+AccountDataIT com 'Tests run: 24, Failures: 0', a mutação SOBREVIVEU. Desfeito. | PARCIAL | — | **PARCIAL** | O comportamento está correto, inclusive na corrida (provado só pela minha caixa-preta), mas a proteção contra a corrida, que é o ponto que o STATUS destaca, não tem teste que a guarde. O teste que o STATUS cita não põe dois cadastros em disputa. |
| AD-01 | Painel do admin: usuários (desativar, redefinir senha, gerar convite), fila de denúncias, modo de cadastro, cotas, janelas de edição e exclusão, provedores de transcrição, uso de disco. | feito; IdentityIT, AccountDataIT, ModerationIT, TranscriptionIT, MediaIT; e2e data.spec.ts e moderation.spec.ts | identity/AdminUserController.java:25-70 (@PreAuthorize ADMIN); identity/AdminUserService.java:137-192; admin/AdminSettingsController.java:13-32; shared/security/SecurityConfig.java:92 (/api/v1/admin/** com hasRole ADMIN); moderation/ReportController.java:39-55; media/StorageUsageController.java:15-26; transcription/TranscriptionController.java:64-99 | frontend/src/features/admin/components/admin-settings-form.tsx (modo, cooldown, janelas, cota, senha vazada), admin-users.tsx (desativar/reativar, redefinir senha, tornar admin), admin-storage.tsx; abas reports e transcription | O e2e data.spec.ts:99 ('painel do admin: convite, uso de disco e usuários') e o moderation.spec.ts passaram nesta sessão. IdentityIT.shouldDisableUser passou na linha de base. | Não-admin (bia) recebeu 403 forbidden em GET/PUT /admin/settings, /admin/users, disable, reset-password, PUT admin, /admin/reports, /admin/storage e /admin/transcription. Sem sessão, 401. Admin: reports, storage e transcription com 200. Desativar a cris: login com a senha certa deu 401. reset-password: 16 caracteres e o login com eles deu 200. Desativar a si mesmo deu 409 admin-self-action e tirar o próprio admin deu 409. E2E: '11 passed (1.4m)'. | não feita: requisito não crítico | CUMPRIDO | — | **CUMPRIDO** | Testei os provedores de transcrição só por GET e pelo 403 (não rodei o TranscriptionIT). O enable e o reset-password não chamam o requireActiveAdmin (o desativar e o tirar admin chamam), então um admin rebaixado na mesma janela ainda passaria pelo @PreAuthorize até a sessão cair. O reset de senha de outro admin é permitido. |
| AD-02 | Configurações da instância persistidas no banco, com padrões seguros. | feito; instance_settings (Flyway) com padrões seguros; IdentityIT.shouldApplyRegistrationModes | db/migration/admin/V202610151600__create_instance_settings.sql:2-19 (linha única, CHECK de modo, INVITE_ONLY/30/15/48/5 GiB/checagem off); admin/InstanceSettingsService.java:67-78; admin/UpdateInstanceSettingsRequest.java:92-98 (limites) | frontend/src/features/admin/components/admin-settings-form.tsx | shouldApplyRegistrationModes altera e aplica o modo. Nenhum teste lê os padrões da migration (o DatabaseCleaner os sobrescreve). | No banco: 'INVITE_ONLY \| 30 \| 15 \| 48 \| 5368709120 \| f'. Depois do PUT, 'version 7' e updated_at foram atualizados. Reiniciei o jar e 'apos reinicio {registrationMode: OPEN, usernameChangeCooldownDays: 7, messageEditWindowMinutes: 20, messageDeleteWindowHours: 24, userQuotaMegabytes: 1000}'. PUT parcial: 400. cooldown -1: 400. Modo inválido: 400. | não feita: requisito não crítico. A M5b, de ID-07, mostra que os padrões não têm teste. | CUMPRIDO | — | **CUMPRIDO** | Nenhum teste automatizado prova os padrões seguros: só a consulta ao banco desta sessão. A checagem de senha vazada desligada é uma escolha justificada (a instância pode estar offline). |
| CT-01 | Adicionar contato por @usuario, link ou QR Code, com apelido local | feito; ContactIT (por @usuario com apelido; link próprio e cartão); e2e adiciona por @usuario em maiúsculas e pelo link /add/@usuario; QR lido por câmera real não testado | contact/ContactController.java:221-251 (add, rename, people/{username}, my-link com QrCodes.svg); ContactService.java:47-58 (add/apelido), 72-77 (rename só do próprio contato) | features/contact/components/add-contact-dialog.tsx, my-link-dialog.tsx:19-42, person-card.tsx:29; routes/_app/add.$username.tsx | ContactIT 3/0 (baseline nesta sessão); e2e accounts.spec.ts desktop e mobile passou (apelido 'Chefe' e /add/${bruno}) | s1.sh: POST /contacts {"username":"@CAIO","nickname":"Caiozinho"} -> 201 label "Caiozinho"; PATCH -> "Caio C"; caio GET /contacts -> [] (apelido local); inexistente 404; si mesmo 400 contact-self; PATCH contato alheio 404; sem sessão 401. my-link -> url http://localhost:18120/add/bia. QR decodificado com zxing-cpp: ['http://localhost:18120/add/chefe']. e2e: 19 passed (accounts, data, moderation, desktop+mobile). | não feita: requisito não crítico | CUMPRIDO | mantido | **CUMPRIDO** | Não há leitor de QR dentro do app: o QR contém o link /add/usuario e depende da câmera do sistema; leitura por câmera real não testada. |
| CT-02 | Solicitações de mensagem (aceitar, recusar, bloquear); antes de aceitar o remetente não vê online, visto por último nem leitura | feito; MessagingIT.shouldHandleMessageRequests; 'RealtimeIT presença só após aceitar'; e2e aceitar, recusar e bloquear | ConversationService.java:107 (REQUEST); ConversationQueries.java:94-100 (mutualPeers exige os dois ACTIVE); PresenceService.java:91-95,104,146; ConversationService.java:398 (readSeq STOMP), 519-523 (peerReadSeq REST); MessageService.java:501-503 (readAt do info) | routes/_app/requests.tsx; e2e chat.spec.ts:89 (Aceitar), moderation.spec.ts:131 | MessagingIT.shouldHandleMessageRequests afirma só o REST peerReadSeq e a recusa. Não existe teste de presença antes de aceitar (RealtimeIT sempre faz o destinatário adicionar o remetente antes) nem de leitura escondida por STOMP ou em Dados da mensagem. | s2.sh (jar de referência + cliente STOMP): na REQUEST a ana recebe só {"type":"receipts",...,"deliveredSeq":1,"readSeq":0}; GET /presence/duda -> 404; peerReadSeq null; info readAt null. Depois de aceitar: {"type":"presence",...,"online":true}, peerReadSeq 1, readAt preenchido. e2e moderation.spec (recusar/bloquear) passou. | M1 mutualPeers aceitando REQUEST: RealtimeIT, PrivacyIT, MessagingIT, PresenceIT, NotificationIT 31/0, SOBREVIVEU (o jar mutante vazou presence online e GET /presence 200). M2 peerReadSeq sem peerState: MessagingIT.shouldHandleMessageRequests:126 falhou, MORTA. M3+M4 (readSeq do STOMP e readAt do info sem checar o estado): 23/0, SOBREVIVERAM (o jar mutante vazou "readSeq":1 e readAt). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | Comportamento correto no servidor (provado por caixa-preta), mas 3 de 4 caminhos da regra não têm teste: presença antes de aceitar (REST e STOMP), leitura por STOMP e readAt em Dados da mensagem. A evidência 'RealtimeIT presença só após aceitar' não existe. Veredito real: PARCIAL (não provado por testes). |
| CT-03 | Bloquear e desbloquear; bloqueado não envia, não vê presença nem foto e não adiciona a grupos | feito; ContactIT, MessagingIT.shouldEnforceBlocksAndMembership, GroupIT.shouldRespectBlocksAndProtectPhoto, link de convite, 'presença filtrada por bloqueio', e2e moderation | ConversationService.java:371-376 e 347-348 (403 message-blocked), 431-433 (mutualPeers sem bloqueados); ContactQueries.java:61-69; ProfileService.java:118,131,137; GroupService.java:422-430 (requireAddable), 344 (link) | bloqueio pelo painel e pela solicitação (e2e moderation.spec.ts) | Envio, foto e grupo têm testes que falham na mutação. Presença com bloqueio não tem teste. | s3.sh: depois de PUT /blocks, caio envia -> 403 message-blocked; bia envia 403; encaminhar 403; enquete 403; foto 404; people {"about":null,"hasPhoto":false}; presence 404; STOMP sem eventos; criar grupo com bia / adicionar -> 403 group-cannot-add; entrar pelo link do caio -> 404; desbloquear: foto 200, envio 201, presença 200. | M5 blockedWithSomeone=false: MessagingIT:238 e ModerationIT:58 falharam, MORTA. M6 canSeePhoto=true: ContactIT:87 falhou, MORTA. M7 requireAddable sem bloqueio: GroupIT:141 falhou, MORTA. M8 mutualPeers sem filtro de bloqueio: nenhum teste de presença falhou, SOBREVIVEU (o jar mutante entregou presence online/offline à bloqueada e GET /presence 200). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | 'Não vê presença' está implementado e foi provado por caixa-preta, mas sem teste de integração (M8 sobreviveu), embora o STATUS liste 'presença filtrada por bloqueio' como evidência. Veredito real: PARCIAL. |
| CT-04 | Denunciar usuário: as 5 últimas mensagens vão para a fila de moderação, com opção de bloquear | feito; ModerationIT (5 últimas, bloqueia junto, IDOR 404, só admin); e2e moderation.spec | moderation/ReportService.java:36,57-66; MessageService.java:514-527 (reportSnapshot); ReportController (@PreAuthorize ADMIN nas rotas /admin/reports) | features/moderation; e2e moderation.spec.ts:21 | ModerationIT 2/0, só com 7 textos seguidos; nenhum caso com aviso do sistema ou mensagem apagada entre as últimas | s5.sh caso A (7 textos): {"n":5,"bodies":["m3","m4","m5","m6","m7"]}. Caso B (m8, m9, timer ligado, timer desligado, m10 apagada, m11): {"n":2,"bodies":["m9","m11"]}; psql: 'de novo \| 2 \| m9,m11'. block:true -> blocks ["ana"], ana envia 403; IDOR 404; não admin na fila 403. e2e moderation desktop+mobile passou. | não feita: requisito não crítico | STATUS INCORRETO | — | **STATUS INCORRETO** | reportSnapshot pega as 5 últimas linhas e só depois tira os avisos do sistema e as apagadas: com avisos ou apagadas entre as últimas, a denúncia leva menos de 5 mensagens, embora existam mensagens mais antigas. Veredito real: PARCIAL. |
| BK-01 | Exportar conversa em ZIP (texto no estilo do WhatsApp + mídia opcional) | feito; ExportIT (2) formato, apagadas, escondidas, mídia opcional, IDOR 404, trancada 403; e2e data.spec | messaging/ConversationExport.java:67-163; MessagingController.java:50-60 | menu da conversa > Exportar (e2e data.spec.ts:30) | ExportIT 2/0 nesta sessão (mídia no ZIP com media=true, '<arquivo omitido>' sem ela, carla 404, trancada 403) | s6.sh: 200 application/zip, Cache-Control: no-store, filename 'Conversa do Papinho com Bia.zip'; Conversa.txt '16/10/2026 18:59 - Caio: antes'; a mensagem que o caio apagou para si não sai no ZIP dele e sai no da bia; caio exporta conversa alheia -> 404 conversation-not-found; sem sessão 401. e2e data.spec passou. | não feita: requisito não crítico | CUMPRIDO | mantido | **CUMPRIDO** | Fuso fixo America/Fortaleza no código (ConversationExport.java:43); a mídia no ZIP foi provada pela IT, não por caixa-preta. |
| BK-02 | Exportar todos os dados da conta (LGPD) e excluir a conta | feito; AccountDataIT (3) dados.json por módulo e arquivos, sem segredos; exclusão com senha, último admin 409, sessões caem; e2e data.spec | backup/AccountExport.java; AccountExportController.java:29-37; messaging/MessagingPersonalData.java (sender_id = userId); identity/AccountDeletionService.java; AccountController.java:43-52 | Configurações > Conta > Seus dados (e2e data.spec.ts:30) | AccountDataIT 3/0 nesta sessão; não verifica a foto de perfil no export | s7.sh: seções [arquivos,conta,contatos,denuncias,mensagens,notificacoes,perfil,privacidade,transcricao]; enviadas ["resposta *negrito*"]; textos de outros 0; nenhum hash. perfil {"temFoto":true}, mas `unzip -l` com files=true mostra só dados.json. Exclusão: senha errada 401, certa 204, cookie antigo 401, login 401, conversa do caio com canSend false e envio 403. scan.sql no banco: resta só 'conversations.direct_key (character varying): 1 linha(s)' com o UUID dela; a foto saiu do disco. | não feita: requisito não crítico | PARCIAL | — | **PARCIAL** | A foto de perfil (dado pessoal) não entra na exportação nem com files=true; o UUID da conta apagada continua em conversations.direct_key. As mensagens enviadas ficam com quem recebeu, sem dono (decisão documentada). |
| BK-03 | Backup e restauração da instância: pg_dump + volume, README e restauração testada | feito; scripts/backup.sh, restore.sh, verify-backup.sh executados de ponta a ponta; README seção Backup | scripts/backup.sh, scripts/restore.sh, scripts/verify-backup.sh | n/a | Sem teste automático no CI; verificação manual nesta sessão com os scripts reais | backup.sh com PAPINHO_PG_CONTAINER=audit-pg-02-CT-BK: tar 0600 com database.dump, data.tar.gz, SHA256SUMS, MANIFEST. restore.sh em banco cheio: 'não está vazio (57 tabelas)', exit 1; tar corrompido: 'Backup corrompido (SHA-256 não confere)', exit 1; banco novo papinho_r: exit 0, 'users=4 messages=20 reports=2 media=1 contacts=1 blocks=1' igual ao original, secret.key igual. Nova instância (porta 18121) no banco restaurado: logins 200, mídia com o mesmo SHA-256 31147a35..., denúncias [5,2], bloqueio mantido, envio novo 201. README.md:243-273 descreve o procedimento. | não feita: requisito não crítico | CUMPRIDO | — | **CUMPRIDO** | scripts/verify-backup.sh não foi rodado (portas fixas 8090/5440/5441); a PKI fora de PAPINHO_DATA_DIR não entra no backup (documentado); restaurei num banco novo dentro do mesmo contêiner, não num contêiner novo. |
| MS-01 | Formatação do WhatsApp com conteúdo sanitizado (nunca injetar HTML do usuário) | feito; parse.test.ts (10 casos, inclusive 'HTML vira texto') e e2e chat.spec (negrito, spoiler, mono) | n/a (corpo guardado como texto puro em messages.body) | frontend/src/features/chat/format/parse.ts:44 (LINK só http(s)), message-text.tsx:63-120 (renderInline monta só elementos React; texto como string), :125-145 Spoiler, :147-175 CodeBlock com lowlight | parse.test.ts testa só o parser (que não injeta nada por construção); nenhum teste de render; nenhum e2e envia HTML. chat.spec cobre negrito/spoiler/mono | xss.mjs no Chromium 1194 (bia lendo 10 payloads da ana): `INJETADO {"xss":null,"ids":[],"imgX":0,"scripts":0,"jsLinks":0,"iframes":0,"svgOnload":0}`, `TEXTO LITERAL VISÍVEL [true,true,true]`, `FMT {"strong":"negrito","em":"italico","s":"riscado","code":"mono","spoilerBtn":true,...}`, `SPOILER {"antes":"0","depois_botao":0,...}`, `CODE js {"hljsSpans":3}`, listas ul/ol e blockquote presentes, `PROBLEMAS []`. e2e chat.spec.ts: passed | message-text.tsx: case "text" -> <span dangerouslySetInnerHTML={{__html: node.value}}/>; `pnpm test` -> `Tests 86 passed (86)`; eslint no arquivo exit 0. NENHUM teste falhou. Desfeita (git checkout). | PARCIAL | — | **PARCIAL** | O comportamento está correto no navegador (provado), mas a sanitização não tem nenhum teste que a proteja: a mutação que injeta HTML do usuário passa em Vitest e lint, e nenhum e2e manda payload HTML. Pela regra de mutação, o requisito crítico fica NÃO PROVADO pela suíte. Falta um teste de render (Vitest + Testing Library ou e2e) com payload HTML/javascript: que confira o DOM. |
| MS-02 | Status enviando/enviada/entregue/lida e painel 'Dados da mensagem' respeitando a seção 7.7 | feito; MessagingIT.shouldTrackReceiptsAndUnread e e2e enviada->lida e diálogo | messaging/MessageService.java:478-509 (info: só o autor, :494-503 readAt só se ACTIVE && iShare && sharers.contains); ConversationService.java:259-273 (receiptSharers), :390-410 (publishReceipts filtra leitura), :515-527 (peerReadSeq null sem reciprocidade) | features/chat/components/conversation-view.tsx:1192-1203 (statusOf), message-row.tsx:640-662 (StatusIcon), message-info-dialog.tsx | MessagingIT.shouldTrackReceiptsAndUnread (só autor 404), PrivacyIT.shouldApplyReadReceiptRules (readAt nulo); e2e chat.spec (Enviada/Entregue -> Lida, diálogo Dados da mensagem) | status.mjs (POST atrasado 3 s): `1 durante o POST: Enviando` `2 depois do POST: Enviada` `3 depois de entregue: Entregue` `4 depois de lida: Lida`. t3.sh: ligada `{"username":"bia","d":true,"r":true}` peerReadSeq 18; bia desliga -> `{"d":true,"r":false}` `"peerReadSeq":null`; ana desliga a própria -> r:false; bia (não autora) e chefe (fora) -> HTTP=404. ITs base: MessagingIT 10/0, PrivacyIT 7/0; e2e chat.spec passed | M8 (readAt sem filtro PV-06): FALHOU PrivacyIT.java:159 'Expecting value to be true but was false'. M9 (info sem checar autor): FALHOU MessagingIT.shouldTrackReceiptsAndUnread 'expected: 404'. Desfeitas, diff vazio. | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | Nenhuma relevante. 'Enviando' só existe no cliente (otimista); provado com atraso artificial no POST. |
| MS-03 | Indicadores 'digitando…' e 'gravando áudio…' | feito; RealtimeIT.shouldNotifyMessagesReceiptsAndTyping; e2e chat.spec e audio.spec | messaging/TypingController.java:39-62 (/app/activity, só 'typing'/'recording', audiência via typingAudience, efêmero) | e2e confirma render em main: 'digitando…' e 'gravando áudio…' | RealtimeIT testa só 'typing' pelo STOMP; 'recording' só pelo e2e audio.spec.ts:26 | it.sh base: `Tests run: 6, Failures: 0 ... RealtimeIT`; e2e desktop-chromium: `✓ audio.spec.ts:8:1 ... (37.3s)`, `✓ chat.spec.ts:35:1 ... (41.1s)` (asserções chat.spec.ts:109 'digitando…', audio.spec.ts:26 'gravando áudio…') | não feita: requisito não crítico | CUMPRIDO | mantido | **CUMPRIDO** | 'recording' não tem teste de integração no servidor, só e2e. |
| MS-04 | Responder com salto, encaminhar (selo e 'várias vezes' após 5 saltos), reagir, mencionar @usuario/@todos, fixar, favoritar, selecionar várias | feito; MessagingIT.shouldReactPinStarAndForward; e2e responder com salto, reagir, fixar, favoritar, selecionar e encaminhar; @todos em groups.spec | MessageService.java:338-369 (forward, forwardCount+1), :904-905 (forwardedMany = forwardCount >= FORWARDED_MANY), :371 react, :398 pin, :455 star | conversation-view.tsx:332-352 (jumpTo -> scroller.scrollToMessage align center smooth), message-text.tsx:80-100 (menções) | MessagingIT.shouldReactPinStarAndForward (passou na base); e2e chat.spec só confere que o botão 'Ir para a mensagem respondida' está visível, não que o salto mostra a original | t2.sh, 6 encaminhamentos em cadeia: `{"forwardCount":4,"forwardedMany":false}` `{"forwardCount":5,"forwardedMany":true}`; fwd.mjs no navegador do chefe: `selos {"muitas":2,"simples":4}`. e2e chat.spec/groups.spec passed (`✓ groups.spec.ts:27:1 ... (35.3s)`). jump.mjs (original + 60 mensagens + resposta): `antes do salto: não renderizada`, `depois do salto +3000ms: fora da tela ORIGINAL-ALVO-DO-SALTO@-449 enchimento 0@-387` (reproduzido 2 vezes) | não feita: requisito não crítico | PARCIAL | — | **PARCIAL** | O salto para a original não funciona quando ela está longe no histórico: a lista rola, mas a mensagem fica 449 px acima da área visível (1280x720), mesmo 6 s depois. O e2e não pega isso porque só confere se o botão existe. As menções @usuario só foram vistas no parse.test (Vitest) e @todos no groups.spec; não provei o aviso de menção no servidor. |
| MS-05 | Editar: só o autor, janela configurável pelo admin (padrão 15 min), selo 'editada' | feito; MessagingIT.shouldEditAndDeleteForEveryone; e2e messages.spec | MessageService.java:535-550 (edit), :769-776 (requireAuthorWithin: autor, não apagada, não sistema; createdAt+janela); admin/InstanceSettingsEntity.java:31 (messageEditWindowMinutes = 15); Message.java edit() define editedAt | e2e messages.spec.ts:36-48 (menu Editar, selo 'editada' no outro navegador) | MessagingIT.shouldEditAndDeleteForEveryone (403 não autor, 409 com janela 0 definida pelo admin); e2e messages.spec | t1.sh: bia PATCH na msg da ana -> `"status":403 ... message-not-author`; chefe -> 404; ana -> `{"body":"oi bia editada","editedAt":"2026-10-16T22:22:03..."}`. t2.sh: settings `{"messageEditWindowMinutes":15,...}`; created_at -14 min -> HTTP=200; -16 min -> `"status":409 ... message-window-expired`; banco `editar aos 16min\|f`, `editada aos 14min\|t`. Grupo: ana (admin do grupo) edita msg da bia -> HTTP=403. e2e messages.spec passed | M1 (sem checar autor): FALHOU MessagingIT.java:272 'expected: 403 but was: 200'. M2 (janela de edição de 36500 dias): FALHOU :307 'expected: 409 but was: 200'. Desfeitas, diff vazio. | CUMPRIDO | mantido | **CUMPRIDO** | Nenhuma. |
| MS-06 | Apagar para mim; apagar para todos só pelo autor (individual e grupo), janela padrão 48 h, aviso, conteúdo e arquivos removidos do servidor | feito; MessagingIT (só autor, janela, texto/anexo/reações/favoritas removidos, arquivo 404; apagar para mim); e2e | MessageService.java:555-574 (deleteForEveryone: requireAuthorWithin com messageDeleteWindow, apaga reações/favoritas/enquete, media.delete se ninguém mais usa), :577-588 (hide); Message.java deleteForEveryone zera body/anexo/resposta/encaminhada; media/MediaService.java:114-135 (remove: linha + storage.delete do conteúdo e da miniatura após o commit); InstanceSettingsEntity.java:34 (48 h) | e2e messages.spec.ts:70,86 ('Esta mensagem foi apagada' nos dois navegadores) | MessagingIT.shouldEditAndDeleteForEveryone e shouldHideForMe; e2e messages.spec. O arquivo é conferido só por GET 404; o disco só é conferido pelo MediaIT (mesmo caminho remove()) | t2.sh: -47 h -> `{"deleted":true,"body":""}`; -49 h -> `"status":409`; bia apaga a msg da ana -> HTTP=403; ana apaga a com foto -> `{"deleted":true,"body":"","attachment":null}`; banco `msg\|\|\|t`, `media_files\|0`, `reacoes\|0`, `estrelas\|0`; content -> HTTP=404; pg_dump sem 'legenda secreta' (0). t3.sh: disco antes `67863625-...` e `67863625-...-thumb`, depois `(nenhum arquivo ...)`. Grupo: ana (admin do grupo) apaga a msg da bia -> HTTP=403; bia apaga a própria -> `{"deleted":true,"body":""}`. e2e messages.spec passed | M1 (sem autor): FALHOU :272. M3 (janela de apagar de 36500 dias): FALHOU MessagingIT.java:308 'expected: 409 but was: 200'. M4 (sem media.delete): FALHOU :299 'expected: 404 but was: 200'. M5 (sem storage.delete do conteúdo): MessagingIT e DisappearingIT PASSAM; só MediaIT.shouldPurgeFilesNeverUsed:319 falha ('Expecting an empty Optional but was containing value: file [/root/.papinho/files/media/...]'). Todas desfeitas, diff vazio. | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | O teste do MS-06 não confere o disco (só o 404 da API); a remoção física fica protegida de forma indireta pelo MediaIT, que usa o mesmo MediaService.remove. Apagar para mim não foi mutado (não crítico isoladamente; shouldHideForMe passou). |
| MS-07 | Temporárias por conversa (desligado/24 h/7 d/90 d), timer padrão para conversas novas, job com ShedLock que remove mensagens e arquivos | feito; DisappearingIT (timers, 400 fora da lista, padrão, permissão no grupo, job apaga vencidas e arquivos); e2e timer pelo painel | MessageService.java:659-684 (changeDisappearing, TIMERS), :696-707 (preferências), :713-733 (purgeExpired: deleteAll + media.delete); DisappearingMessagesJob.java:20-27 (@Scheduled + @SchedulerLock 'disappearing-messages'); shared/config/SchedulingConfig.java:21-26 (JdbcTemplateLockProvider); migração shared/V202610170300__create_shedlock.sql | e2e messages.spec.ts:90-110 (combobox 'Mensagens temporárias') | DisappearingIT (3) chama job.purge() direto; nenhum teste toca no ShedLock | t3.sh (jar com APP_MESSAGING_EXPIRY_CHECK=5s): timer 86400 -> HTTP=204, 3600 -> HTTP=400; `expires_at - created_at` = `1 day`; expires_at forçado no passado; 12 s depois `msg\|0`, `media\|0`, arquivo e miniatura fora do disco; shedlock `disappearing-messages\|t\|t\|vm` (locked_at no último minuto). Preferência padrão aceita 86400/604800/7776000/null. it.sh base: DisappearingIT 3/0; e2e messages.spec passed | M6 (purgeExpired sem apagar arquivos): FALHOU DisappearingIT.java:117 'expected: 404 but was: 200'. M10 (sem deleteAll): FALHOU. M7 (sem @SchedulerLock): DisappearingIT PASSA 3/0, então o ShedLock não tem teste. Desfeitas, diff vazio. | PARCIAL | — | **PARCIAL** | Na caixa-preta, a remoção de mensagens e arquivos e a trava do ShedLock funcionam. Mas tirar o @SchedulerLock não derruba nenhum teste (M7), então a parte 'com ShedLock' do requisito crítico não está provada pela suíte. Como no MS-06, o teste do job confere o arquivo só pela API, não pelo disco. |
| MS-08 | Visualização única de foto, vídeo e áudio. Servidor: token de uso único, no-store, nada no service worker, arquivo apagado após aberto ou após 14 dias. Cliente: sem baixar, copiar ou menu, desfoque, marca d'água com o @usuario e aviso de captura. | feito: ViewOnceIT (token só para o destinatário, no-store, segunda abertura 410, job remove o arquivo) e view-once.spec.ts. Nota do próprio STATUS: o token vale 5 min para Range (ADR 0012). | MessageService.java:198-217 (openViewOnce, token de 32 bytes, 410 na segunda vez); ViewOnceOpens.java:30-36 (on conflict do nothing) e :76-90 (purgeable, 14 dias ou todos abriram); MessagingController.java:266-277 (noStore, Pragma no-cache, inline); MessageService.java:238-250 (purge com media.delete); ViewOnceCleanupJob.java:21; sw.ts:17-31 (só precache, /api/ fora) | frontend/src/features/chat/components/view-once-viewer.tsx:41 (aviso), :65-77 (blur/focus/visibilitychange), :96-99 (select-none, onContextMenu/onCopy/onDragStart preventDefault), :122-152 (marca d'água SVG com @usuario) | ViewOnceIT (2 testes) afirma de verdade 410, no-store, token alheio 404, purge e arquivo removido. shouldValidateAndExpire, porém, só confere 410 depois de 14 dias, não a remoção do arquivo. view-once.spec.ts falha sempre neste ambiente (asserção sem poll na linha 75). | IT: 'Tests run: 32, Failures: 0' (8 classes). Curl contra o jar de referência: segunda abertura (POST) -> 410; outro login da bia -> 410; GET conteúdo 'Cache-Control: no-store / Pragma: no-cache / Content-Disposition: inline'; token pela ana -> 404; sem sessão -> 401; rota /media/{id}/content -> 404 para os dois; encaminhar -> 400. Depois do job: 'msg\|01a146d3-6dc1...\|t\|t' (sem anexo) e os arquivos 34d6.../9e06... sumiram do disco, inclusive o da mensagem de 15 dias. E2E temporário: 'MARCA: @hana.johpla', 'FILTRO sem foco: blur(7.00601px)', 'FILTRO aba escondida: blur(...)', 'PREVENIDOS: {"contextmenu":true,"copy":true,"dragstart":true}', 'SW: {..."caches":["workbox-precache-v2-..."],"api":[]}', 'AVISO no visualizador: ... o navegador não consegue impedir capturas de tela.' | Cinco mutações, e o teste falhou em todas. (1) MessageService:211 com '&& false', token reemitido: ViewOnceIT:76 'expected: 410 but was: 200'. (2) sem noStore em MessagingController:273: ViewOnceIT:74 'Expecting actual not to be null'. (3) sem media.delete em MessageService:246: ViewOnceIT:89 '[arquivo removido] expected: 0L but was: 1L'. (4) prazo de 14 dias desligado em ViewOnceOpens:80: ViewOnceIT:119 'expected: 410 but was: 200'. Tudo desfeito; git diff vazio. | PARCIAL | — | **PARCIAL** | (a) O token NÃO é de uso único. A segunda GET /api/v1/view-once/{token} com o mesmo token deu 200; ele serve várias vezes durante 5 min. É um desvio declarado no ADR 0012 e no STATUS, por causa do Range de vídeo, mas o PROMPT (que prevalece) pede uso único. Para imagem nem há essa justificativa. (b) O e2e do repositório (view-once.spec.ts:75) falha sempre neste ambiente, então a prova de desfoque no CI depende do Chromium 1248. (c) Não verifiquei no navegador o vídeo e o áudio de visualização única (sem download e sem PiP). Só a foto foi verificada. Se o desvio (a) for aceito, o resto está cumprido. |
| MS-09 | Enquetes: escolha única ou múltipla, opção anônima, data de encerramento, resultados em tempo real. | feito: PollIT (única troca o voto, múltipla, anônima esconde quem votou, encerrada 409, opções inválidas 400, resultados pelo afterSeq, IDOR 404) e e2e: o voto da Hana aparece para o Gil. | MessageService.java:632-652 (vote: encerrada 409, validação de única ou múltipla, touch para o tempo real); MessageService.java:967-984 (PollView.of: anônima -> voters List.of()); Polls.java:30-37 e :72-92; MessageService.java:818-826 (touch -> MessagesChanged pelo WebSocket) | e2e groups.spec.ts:27 (enquete entre dois navegadores); DateTimePicker próprio (não reli o componente) | PollIT (2 testes) afirma de verdade que a anônima tem voters vazio, o 409, a mudança pelo afterSeq e o 404 de quem está fora. groups.spec.ts passou e cobre o resultado em tempo real na interface. | IT PollIT 2/0 falhas. Curl: anônima múltipla vista pela ana '{"totalVoters":1,"options":[{"votes":1,"voters":[]},{"votes":1,"voters":[]},...]}'. O id da bia aparece 0 vezes na resposta crua de /messages. Não anônima: '"voters":["01a146d2-afe0-..."]'. Única com 2 opções -> 400; encerrada -> 409 'Enquete encerrada'; closesAt no passado -> 400; pessoa de fora -> 404. E2E groups.spec.ts passou (5 passed no lote). | (1) anônima devolvendo os votantes (MessageService:979): PollIT:95 '[anônima] Expecting empty but was: ["01a146cf-..."]'. (2) sem touch(message) no voto (MessageService:650), sem tempo real: PollIT:72 NullPointerException, a mudança não chega pelo afterSeq. As duas falharam como esperado e foram desfeitas. | CUMPRIDO | mantido | **CUMPRIDO** | Nenhuma relevante. O tempo real na interface se apoia no e2e groups.spec.ts (enquete não anônima). Não testei a anônima na interface. |
| MS-10 | Emojis (seletor com busca e recentes); figurinhas (pacotes dos usuários, WebP inclusive animado, favoritas); GIFs (provedor externo opcional, desligado por padrão, atrás de uma porta). | feito: data.test.ts, StickerIT, GifIT contra Tenor simulado, GIFs desligados 503, e2e de emoji e figurinha. | StickerService.java:82-93 (só image/webp até 1 MB, máx. 120 por pacote), :129-147 (favoritas por pessoa); GifProperties.java:20-26 (provider padrão 'none'; enabled só com tenor e apiKey); GifProvider.java (porta) e TenorGifProvider.java | frontend/src/features/emoji/emoji-picker.tsx:37-98 (busca e recentes); composer.tsx:317,338 (aba GIF só se gifStatus.enabled); sticker/sticker-panel.tsx e sticker-manager.tsx | StickerIT usa só um WebP estático de 1x1, sem caso animado. StickerIT.shouldKeepGifsDisabledByDefault e GifIT passaram. | Curl: WebP animado de 3 quadros como figurinha -> 201. A bia baixa '200 image/webp' e o PIL lê 'formato WEBP quadros 3 animada True'. PNG como figurinha -> 400; adicionar em pacote alheio -> 404. Favoritas: bia ["98c23ddd..."], ana []. Mensagem de figurinha '{"kind":"USER","sticker":{...}}'. GIF '{"enabled":false}', e a busca dá 503 'GIFs desligados'. E2E temporário: 'ABAS: [..."Emojis","Figurinhas","🕘",...]' (sem GIF); a busca 'pizza' põe '🍕' no campo, e ao reabrir aparece em Recentes. messages.spec.ts e groups.spec.ts passaram. | não feita: o requisito não é crítico nesta área. O comportamento foi provado por caixa-preta. | CUMPRIDO | mantido | **CUMPRIDO** | O Tenor real não foi testado (sem chave). Ele está atrás da porta e desligado por padrão, como o requisito pede. Figurinha animada sem teste automatizado; só a minha caixa-preta a prova. Recentes ficam no localStorage, por aparelho. |
| MS-11 | Mensagens agendadas, envio silencioso (sem notificação) e rascunho sincronizado entre dispositivos. | feito: ScheduledIT (agendada sai na hora, só quem agendou vê e cancela, passado 400, bloqueio marca falha, rascunho só do dono e apagado ao enviar); NotificationIT (silenciosa não notifica); e2e messages.spec.ts. | ScheduledMessageService.java:51-67 (agendar), :72-81 (lista só do sender_id), :85-90 (cancelar só o dono), :93-114 (dispatchDue manda pelo send com SendOptions silent); ScheduledMessagesJob.java:18 (15 s); NotificationDispatcher.java:76 ('\|\| recipient.silent()' -> não manda WS notify nem push); ConversationService.java:299-317 (rascunho por user_id); ConversationQueries.java:52; MessageService.java:186 (enviar apaga o rascunho) | e2e messages.spec.ts:133-171 (rascunho sobrevive a recarregar, silenciosa, agendar e cancelar) | ScheduledIT (3) e NotificationIT (7) afirmam de verdade: as mutações os derrubam. messages.spec.ts passou. | IT 'ScheduledIT Tests run: 3, Failures: 0', 'NotificationIT Tests run: 7, Failures: 0'. Curl: agendada para +20 s; antes da hora 0 mensagens, depois '[{"body":"agendada-xyz","silent":true,...}]'. A bia lista agendadas -> [] e cancelar dá 404; pessoa de fora -> 404; passado -> 400. Rascunho: o dispositivo 2 da ana vê '{"draft":"pensando..."}'; a bia vê '{"draft":null}'; pessoa de fora PUT -> 404; depois de enviar do dispositivo 2, o dispositivo 1 vê null. | (1) sem recipient.silent() em NotificationDispatcher:76: NotificationIT:130 '[silenciosa (MS-11)] Expecting empty but was: [Sent[..."body":"psiu"...]]'. (2) sem clearDraft em MessageService:186: ScheduledIT:100 '[enviar apaga o rascunho]'. (3) lista sem 'sender_id = :me': ScheduledIT:59 '[só quem agendou] Expecting empty but was: [...]'. Todas falharam e foram desfeitas. | CUMPRIDO | mantido | **CUMPRIDO** | Agendada só leva texto, sem anexo; o requisito não pede anexo. A ausência de push foi provada pelo IT com o RecordingPushGateway, não com push real. |
| MS-12 | Mensagens salvas: uma conversa consigo mesmo. | feito: GroupIT.shouldKeepSavedMessagesAndReplyPrivately e e2e no menu Mensagens salvas com axe. | MessagingController.java:87-90 (POST /conversations/saved); ConversationService.java:124 (openSelf) | e2e shortcuts.spec.ts abre 'Mensagens salvas' pela paleta (heading visível) | GroupIT (5) passou; shortcuts.spec.ts passou. | Curl: '{"id":"01a146d5-7457...","kind":"SELF"}'; repetir dá o mesmo id. A bia lendo a salva da ana -> 404; a bia enviando -> 404. A bia tem a própria salva (01a146d5-7546...). GroupIT 'Tests run: 5, Failures: 0'. | não feita: o requisito não é crítico. O isolamento foi provado por caixa-preta (404 para outra pessoa). | CUMPRIDO | mantido | **CUMPRIDO** | Nenhuma. |
| MS-13 | Atalhos: Ctrl/Cmd+K (paleta), ↑ edita a última, Esc fecha painéis, Ctrl+V cola imagem, arrastar e soltar; lista de atalhos com ?. | feito: shortcuts.spec.ts (Ctrl+K, ?, Esc) e messages.spec.ts (↑, Esc). 'Ctrl+V cola arquivo e arrastar e soltar vêm da Fase 3 (media.spec.ts)'. | n/a | frontend/src/components/layout/app-shell.tsx:115 (ctrlKey\|\|metaKey + k) e :124 ('?' fora de campo); composer.tsx:374-379 (onPaste com clipboardData.files) e :399-402 (ArrowUp com o campo vazio); conversation-view.tsx:643-657 (onDragOver/onDrop); shortcuts-dialog.tsx:31 | shortcuts.spec.ts e messages.spec.ts passaram. NÃO existe teste de Ctrl+V nem de arrastar e soltar: grep 'paste\|clipboard\|drop\|DataTransfer' em frontend/e2e não encontra nada, inclusive em media.spec.ts. | E2E do repositório: shortcuts.spec.ts e messages.spec.ts passaram (5 passed no lote). E2E temporário meu (apagado): 'COLAR abriu: Enviar 1 arquivo' (ClipboardEvent com File) e 'SOLTAR abriu: 1 menção a solta.png' (dragover+drop com DataTransfer). | não feita: o requisito não é crítico. | CUMPRIDO | — | **CUMPRIDO** | Funciona; provei Ctrl+V e soltar com o meu spec. Mas a evidência que o STATUS cita para Ctrl+V e arrastar (media.spec.ts) não existe: nenhum teste automatizado protege esses dois atalhos. Cmd+K só foi lido no código (metaKey), não executado. |
| MS-14 | Marcador de não lidas, botão 'ir para a primeira não lida', marcar conversa como não lida. | feito: MessagingIT.shouldTrackReceiptsAndUnread e e2e com contador, marcador '2 mensagens não lidas', botão para a primeira e marcar como não lida. | ConversationService.java:238-239 (markUnread com requireMember) e :496 (unreadCount, firstUnreadSeq); MessagingController.java:131-141 (read/unread) | conversation-view.tsx:633-637 (primeira não lida); e2e chat.spec.ts:164-181 | MessagingIT (10) passou; chat.spec.ts passou e cobre o marcador, o botão 'Ir para a primeira não lida' e 'Marcar como não lida'. | Curl: depois de 3 mensagens '{"state":"ACTIVE","unreadCount":3,"markedUnread":false,"firstUnreadSeq":21}'; depois de ler 'unreadCount":0'; marcar não lida -> 204 e 'markedUnread":true'; pessoa de fora marcando -> 404. E2E chat.spec.ts passou. | não feita: o requisito não é crítico. | CUMPRIDO | — | **CUMPRIDO** | Nenhuma. |
| MD-01 | Upload tus até 1 GB, retomável, Tika, nome sanitizado e cota por usuário funcionam no servidor real; as mutações foram pegas pelo MediaIT | feito; MediaIT (tus em dois pedaços, HEAD, 409, 412, 413, cota, cancelar), MediaHelpersTest, e2e media.spec.ts | media/UploadService.java:90-118 (create: 1 GB em :95, cota com advisory lock em :103-106, FileNames.sanitize em :109), :144-173 (append/offset), :195-201 (Tika detect + ContentTypes.resolve); media/TusController.java:45-110; media/FileNames.java:17-45; media/MediaController.java:203-211 (octet-stream + attachment) | e2e media.spec.ts:124-135 (progressbar 'Enviando azul.png', 'Cancelar envio') | MediaIT (9 testes), MediaHelpersTest (14), e2e media.spec.ts desktop+mobile. Asserem de verdade: cada mutação derrubou uma asserção | OPTIONS -> 'Tus-Max-Size: 1073741824'; .exe (MZ) como foto.png/image/png em 4 PATCH com HEAD 'Upload-Offset=100000/200000/300000' -> '"kind":"DOCUMENT","contentType":"application/x-msdownload"', download 'Content-Type: application/octet-stream' + attachment; nome '../../etc/<passwd>‮\|gpj.exe\x01:x?*.jpg' -> '_passwd___gpj.exe__x__.jpg'; 1 GB+1 -> 413 'O limite é de 1 GB por arquivo.'; upload REAL de 1 GB (4x256 MB) -> 'size:1073741824' em 9 s; sem Tus-Resumable 412; offset errado 409; cris HEAD/PATCH/DELETE 404/404/404; cota 2000 MB: '413 ... quota-exceeded availableBytes 1023085968', 900 MB -> 201; não admin PUT settings 403. e2e: '7 passed (1.7m)' (media/audio/links, desktop+mobile) | 4 mutações em UploadService/FileNames, MediaIT rodado: sem limite de 1 GB -> FALHA shouldEnforceLimitsAndQuota:241; sem cota -> FALHA shouldEnforceQuotaUnderConcurrentCreates:282; tipo declarado em vez do Tika -> FALHA shouldDetectTypeAndStripExif:156; sanitize devolvendo o nome cru -> FALHA shouldUploadInChunks:141. Revertido, git diff vazio | CUMPRIDO | mantido | **CUMPRIDO** | Baixa: o .exe fica com o nome 'foto.png'; o download é seguro (octet-stream + nosniff), mas a extensão não é corrigida. Quando o clamd está fora do ar o upload completo fica parado (ver MD-06). |
| MD-02 | EXIF/GPS e metadados de PNG removidos, orientação aplicada, miniatura e thumbhash gerados; legenda e visualizador com zoom e navegação provados no e2e | feito; MediaHelpersTest, ThumbHashTest, MediaIT, e2e legenda/visualizador | media/UploadService.java:218-228 (PhotoPipeline.process); media/PhotoPipeline.java; media/ImageProcessing.java; media/internal/ThumbHash.java | e2e media.spec.ts:35 (Legenda), :82-96 (visualizador: Próxima imagem, zoom, Esc) | MediaHelpersTest e ThumbHashTest passaram (unit, 53 testes no lote); MediaIT.shouldDetectTypeAndStripExif passou; e2e media.spec.ts verde | JPEG gerado com 'exif:GPSLatitude: 3/1,43/1,5/1', 'exif:Make: CameraAuditoria', Orientation RightTop, 800x600 -> do servidor: 'Geometry: 600x800', 'Orientation: Undefined', '(linhas com exif/GPS acima: 0)', strings Make/Model 0; '"thumbhash":"3+cNLQZ3iIV0d3coeXcHiIjgNvj6","hasThumbnail":true'; 'thumbnail 200 image/jpeg', miniatura 360x480. PNG com tEXt+eXIf: 'original: 2' -> 'do servidor: 0' | não feita: não é crítico. A remoção de EXIF foi provada em caixa-preta | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | WebP e HEIC não testados por mim (cobertos só pelo MediaHelpersTest). |
| MD-03 | Galeria por abas (mídia, links, docs, áudios) correta e fechada para quem é de fora | feito; MediaIT.shouldListGalleryByTab; e2e abas Mídia e Docs | messaging/MessagingController.java:211 (GET /conversations/{id}/gallery) | e2e media.spec.ts:143-165 ('Mídia, links e docs', tabs Mídia/Docs) | MediaIT.shouldListGalleryByTab passou; e2e media.spec.ts verde | bia: 'MEDIA: [{"k":"IMAGE","b":"foto legenda"}]', 'LINKS: [{"k":null,"b":"veja https://pub.test/og"}]', 'DOCUMENTS: [{"k":"DOCUMENT"...}]', 'AUDIO:' com 3 áudios (a visualização única ficou fora); 'cris: [HTTP 404]'; 'tab inválida: [HTTP 400]' | não feita: não é crítico | CUMPRIDO | mantido | **CUMPRIDO** | As abas Links e Áudios na interface não estão no e2e (só a API). |
| MD-04 | IPs privados, DNS, redirecionamentos, esquemas e tamanho estão bem bloqueados. Não há limite de tempo total: uma resposta em gotejamento prende a prévia sem fim e, sob @Transactional, esgota o pool de conexões da instância | feito; AddressPolicyTest (33), SafeFetcherTest, LinkPreviewIT (10.x/169.254/127.x/192.168 sem prévia, 400 para file:/javascript:) | linkpreview/internal/AddressPolicy.java:25-76; linkpreview/internal/SafeFetcher.java:121-133 (timeouts por leitura), :140-186 (redirect manual, MAX_REDIRECTS=3), :189-210 (esquema/porta/usuário), :240-263 (GuardedResolver); linkpreview/LinkPreviewService.java:91-107 (@Transactional envolvendo o fetch) | n/a (regra de servidor) | AddressPolicyTest e SafeFetcherTest asserem de verdade (mutações pegas). LinkPreviewIT.shouldRefusePrivateTargets é frágil quanto a 127.x: o IT libera loopback (test-allowed-ports). Nenhum teste de tempo total | Configuração de produção, DNS por -Djdk.net.hosts.file, aliases 1.2.3.4/10.99.0.1/169.254.169.254/192.168.77.1 no lo e servidor falso em :80. Controle 'http://pub.test/og' -> available:true + HIT. 127.0.0.1, 169.254.169.254, 10.99.0.1, 192.168.77.1, [::1], loop.test, priv10.test, meta.test, lan.test, mixed.test (público+privado), 2130706433, [::ffff:127.0.0.1], [::ffff:a9fe:a9fe], 0.0.0.0 -> available:false e nenhum HIT; redirect 302 para 127.0.0.1/loop.test/169.254.169.254/priv10.test/file:/gopher: -> 'grep -c path=/r[1-4] -> 0'; laço parou em /loop/3; file:/gopher:/ftp:/dict:/javascript: -> 400 'Só links http e https têm prévia.'; 200 MB -> 0,04 s; stall -> 6,02 s. GOTEJAMENTO: '[HTTP 200 120.043053s]' 'SLOW finished full 120s'. 10 prévias lentas da ana + GET da bia: '[HTTP 500 90.058641s]' (página HTML do Tomcat) e 'HikariPool-1 - Connection is not available ... (total=10, active=10, idle=0, waiting=3)' | GuardedResolver sem checagem -> FALHA SafeFetcherTest.shouldCheckAddressesAfterDnsResolution:126; sem link-local/169.254 -> FALHA AddressPolicyTest [169.254.169.254] e [fe80::1]; sem checagem de esquema -> FALHA shouldEnforceLimits:140. Revertido. Para o limite de tempo não há regra nem teste para mutar | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. Falta o limite de TEMPO TOTAL exigido em 'limitar redirecionamentos, tamanho da resposta e tempo': só existem timeouts por leitura (6 s), e um site que pinga 1 byte a cada 3 s segura a prévia sem fim (até 512 KB, dias). Como preview() é @Transactional e faz o fetch com a conexão do banco aberta, um único usuário (limite de 120/h) derruba a instância com 10 pedidos. Além disso, a asserção de 127.0.0.1 no LinkPreviewIT só passa se nada escutar em :80. |
| MD-05 | YouTube vira youtube-nocookie e o iframe só carrega depois do clique, com sandbox; Vimeo e Spotify pelo oEmbed só contra simulação | feito; LinkPreviewIT YouTube/Vimeo/Spotify simulados; e2e links.spec.ts | linkpreview/LinkPreviewService.java:49-54 (padrões), :119-137 (nocookie e oEmbed em lista fechada), :192-213 (só o src em https e no host permitido) | features/linkpreview/components/link-preview-card.tsx:39-49 (iframe com allow e sandbox depois do clique) | LinkPreviewIT (4 testes, passou sem o meu servidor em :80) incluindo shouldEmbedVimeoAndSpotifyThroughOembed; e2e links.spec.ts verde (desktop+mobile) | GET preview 'https://www.youtube.com/watch?v=dQw4w9WgXcQ' e 'https://youtu.be/dQw4w9WgXcQ' -> '"embedProvider":"YOUTUBE","embedSrc":"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"'; e2e links.spec.ts ✓ desktop-chromium e mobile-chromium (expect(youtubeRequests).toEqual([]) antes do clique); LinkPreviewIT 'Tests run: 4, Failures: 0' | não feita: não é crítico | CUMPRIDO | — | **CUMPRIDO** | Vimeo e Spotify reais não testados (o endpoint oEmbed é fixo e https, não deu para simular por fora); no e2e, a resposta de prévia é simulada por route.fulfill. |
| MD-06 | ClamAV desligado por padrão atrás da porta MalwareScanner; ligado, recusa o EICAR por zINSTREAM e falha fechado se o clamd cai | feito; ClamAvScannerTest contra clamd simulado; desligado por padrão | media/internal/MalwareScannerConfig.java (bean: ClamAvScanner só se enabled, senão file -> true); media/MediaProperties.java:331-333 (enabled false); application.yaml:120-123; media/internal/ClamAvScanner.java:36-64; UploadService.java:205-208 | n/a | ClamAvScannerTest passou (1 teste) | Padrão: EICAR aceito ('Papinho-Media-Id: 3e07cb13...'), clamd.log vazio. Com PAPINHO_CLAMAV_ENABLED=true contra clamd falso: EICAR -> 'PATCH off=0 HTTP/1.1 422', log 'Upload rejected by ClamAV: Win.Test.EICAR_HDB-1 FOUND', 'cmd=b'zINSTREAM\x00' bytes=68 found=True'; arquivo limpo -> 204. clamd fora do ar -> '"status":500,"title":"Erro interno"' e o arquivo não é aceito | não feita: não é crítico | CUMPRIDO | — | **CUMPRIDO** | Não testado contra um clamd real. Com o clamd fora do ar a resposta é um 500 genérico (urn:papinho:problem:internal), não uma mensagem própria; o upload completo fica pendente (HEAD 200) reservando a cota até expirar. |
| AU-01 | Gravação com MediaRecorder (webm/ogg/mp4), trava, pré-escuta, cancelamento e envio provados no Chromium com microfone falso; Safari, toque e forma de onda sem prova executada | feito; e2e audio.spec.ts com microfone falso; Safari e toque não testados (lacuna declarada) | media/ContentTypes.java:403-423 (webm/ogg/mp4 declarados como áudio viram audio/*) | features/chat/components/voice-recorder.tsx:18-20 (opus webm, opus ogg, audio/mp4), :83-112 (forma de onda no canvas por AnalyserNode), :127-158 (start), :269-343 (trava e arrastar) | e2e audio.spec.ts:16-40 (Gravação travada, Parar e ouvir, Pré-escuta do áudio, Cancelar gravação, Enviar áudio) | e2e '✓ [desktop-chromium] › e2e/audio.spec.ts:8:1 ... (41.1s)' e '✓ [mobile-chromium] › e2e/audio.spec.ts:8:1 ... (29.1s)' | não feita: não é crítico | PARCIAL | — | **PARCIAL** | Não verificados: AAC/MP4 no Safari (sem Safari aqui), segurar para gravar e arrastar para travar no toque, e a forma de onda (só lida no código; o e2e não a confere). |
| AU-02 | Velocidade, mini-player global e reprodução em sequência provados no e2e | feito; e2e audio.spec.ts (1× -> 1,5×, sequência, mini-player fora da conversa) | n/a (Range em media/MediaController.java) | features/media/player.ts:22 (RATES = [1, 1.5, 2]), :83 e :112 (playbackRate); features/media/components/mini-player.tsx | e2e audio.spec.ts:57-78 | e2e audio.spec.ts verde em desktop-chromium e mobile-chromium ('Velocidade 1,5×', 2º slider ativo no fim do 1º, região 'Áudio tocando' em 'Mensagens favoritas') | não feita: não é crítico | CUMPRIDO | — | **CUMPRIDO** | A velocidade 2× e a troca para OUTRA conversa (o e2e vai para Favoritas) não foram exercitadas; ficam só no código. |
| AU-03 | Transcrição sob demanda com cache para os demais e 404 para quem é de fora funcionam. A regra 'visualização única nunca tem transcrição persistida' é furada pelo dono: ele transcreve antes de enviar e o texto fica no banco | feito; TranscriptionIT (provedor uma vez, cache, 404, 409 navegador); ViewOnceIT (view-once nunca transcrito, 404); e2e automático | transcription/TranscriptionService.java:101-130 (cache em :103-106), :134-137 (texto do navegador), :217-236; media/MediaService.java:159-167 (restricted); messaging/MessageRepository.java:37-48; messaging/MessageService.java:162-165 (envio view-once não olha transcripts) | features/transcription/components/transcript-view.tsx, transcription-settings.tsx; e2e audio.spec.ts:46-96 (POST simulado por route.fulfill) | TranscriptionIT e ViewOnceIT passaram e pegam as mutações; nenhum teste cobre a transcrição feita antes de virar view-once | bia POST -> '"source":"WHISPER"'; ana GET e POST devolvem o cache; provedor 'grep -c POST -> 1'; cris GET/POST/PUT -> 404/404/404. View-once normal: bia POST/PUT e ana POST -> 404, 'transcripts no banco para V1: 0'. FURO: ana POST antes de enviar -> envio aceito '"viewOnce":{"kind":"AUDIO"...}', 'mensagem view_once com V2: t', 'transcripts no banco para V2: WHISPER \| Oi Bia, chego às oito na Fortaleza'; PUT do navegador antes -> 'transcripts no banco para V3: BROWSER \| segredo falado uma vez' | Sem o atalho do cache (TranscriptionService:104) -> FALHA TranscriptionIT:129 'provedor chamado uma vez só'. Só restricted removido -> ViewOnceIT falha em :61, mas a asserção de transcrição :112 continua passando por causa da 2ª barreira (attachmentVisibleTo); com as duas barreiras removidas -> FALHA :112. Revertido | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. Um áudio de visualização única pode ter transcrição persistida: o dono faz POST ou PUT /media/{id}/transcript no próprio upload antes de enviar com viewOnce:true; send() aceita e o texto fica na tabela transcripts até a mídia ser apagada (cascade). Ninguém lê pela API (404), mas o texto fica em repouso e nos backups. No e2e, a transcrição automática usa POST simulado (route.fulfill), então o cache no servidor não aparece no e2e. |
| AU-04 | Whisper no navegador de verdade (Transformers.js num Web Worker), com download sob demanda e progresso, provado em WASM e em WebGPU | feito; whisper.spec.ts WASM (~1 min) e WebGPU (~3,7 min) com internet | transcription/TranscriptionService.java:134-137 (guarda o texto do navegador) | features/transcription/whisper.worker.ts:20 (useBrowserCache), :27-31, :41-75 (webgpu com .catch para wasm); browser-whisper.ts:30 (new Worker); protocol.ts:23-25 (whisper-base/small) | e2e whisper.spec.ts (opcional, PAPINHO_E2E_WHISPER=1) | desktop-chromium: 'transcrição: ... And so my fellow Americans ask not what your country can do for you' '✓ ... (30.4s)'. whisper-webgpu: a 1ª tentativa falhou por tempo (8 min em 'Transcrevendo…', load average 14 em 4 CPUs); a 2ª passou: '✓ 2 [whisper-webgpu] ... (6.2m)', com a asserção dos arquivos q4/fp32 | não feita: não é crítico | CUMPRIDO | — | **CUMPRIDO** | O cache do modelo numa 2ª carga não foi exercitado (só useBrowserCache=true no código). O fallback em tempo de execução de WebGPU com falha para WASM também não; o WASM foi provado sem adaptador. O spec WebGPU depende da carga da máquina (falhou uma vez por timeout). GPU real e Safari não testados. |
| AU-05 | Proxy OpenAI-compatible provado ponta a ponta contra um provedor falso: UA Papinho/<versão>, Bearer, multipart, hotwords, saúde, chave cifrada (AES-GCM) fora do cliente e dos logs, limite de 25 MB do Groq, padrão do admin e escolha do usuário | feito; TranscriptionIT contra provedor simulado; Groq/OpenAI reais não testados | transcription/internal/OpenAiCompatibleClient.java:26-27 (sem redirect), :33 (UA), :47-78 (multipart, Bearer, text), :81-89 (GET {base}/health); transcription/ProviderKind.java:119-122; transcription/TranscriptionService.java:111-128 (limite, decrypt, hotwords só no WHISPER, log sem a chave), :154-168 (cifra), :271-273 (hasKey); shared/crypto/SecretCipher.java (AES-256-GCM, chave fora do banco) | features/transcription/components/admin-transcription.tsx:128-146, :224 (PasswordInput só para escrever, hasKey em :167) | TranscriptionIT (2) asserem UA, Bearer, multipart e que a chave não aparece na saída; as mutações foram pegas | provedor falso: '"path": "/v1/audio/transcriptions", "ua": "Papinho/0.1.0-SNAPSHOT", "auth": "Bearer sk-AUDITORIA-SEGREDO-7f3a9c", "ctype": "multipart/form-data", "fields": {"model": "whisper-1", ... "hotwords": "Papinho, Fortaleza", "file": {"filename": "voz.ogg"...}}'; saúde '"GET", "path": "/health"' -> '{"healthy":true}'; admin GET grep da chave -> 0; banco 'WHISPER\|v1:5PCc9bO1Ng2cgzZbN/VDTfQcr1pcYJyBVEBAG...\|79\|f', pg_dump grep SEGREDO -> 0; padrões GROQ 'https://api.groq.com/openai','whisper-large-v3-turbo','maxBytes":26214400'; wav de 26460078 B -> 413 'aceita áudios de até 25 MB', provedor chamado 0 vezes; 500 do provedor -> 502, log 'reason=Provider answered HTTP 500', grep da chave no papinho.log -> 0; options 'instanceDefault' = id definido pelo admin; preferência da bia = Groq 200 | UA 'OpenAI/Python <versão>' -> FALHA TranscriptionIT:121; log.info da chave decifrada -> FALHA TranscriptionIT:134 e :152 (doesNotContain SECRET). Revertido, git diff vazio | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | Groq e OpenAI reais não testados (sem chave). O e2e de cadastro de provedor no admin não foi rodado por mim. A URL base é livre, escolhida pelo admin, sem a política de SSRF (decisão de confiança no admin). |
| GR-01 | Grupos: nome, foto e descrição, admins, permissões (quem envia, quem edita os dados, quem adiciona), "só admins enviam", sair silenciosamente e responder em privado. O servidor aplica todas as regras. Uma das regras críticas não tem teste que a proteja. | feito: GroupIT cobre só admins enviam/editam, promover, remover, sair sem aviso, IDOR 404 e responder em privado; e2e groups.spec.ts | GroupService.java:118-136 (updateInfo) com requireCanEdit em :450-454; :162-178 (settings via requireAdmin :442); :190-194 (addMembers checa onlyAdminsAddMembers); :219-251 (remover e promover); :257-266 (leave sem appendSystem); ConversationService.java:334-341 (requireSendable/onlyAdminsSend), chamado em MessageService.java:160, :351, :618 e ScheduledMessageService.java:52; MessageService.java:166-171 (privateReply) | features/chat/components/group-details.tsx:367-403 (sair), :419-434 (interruptores de permissão), new-group-dialog.tsx; e2e/groups.spec.ts | GroupIT.shouldManagePermissionsAndMembers cobre enviar 403, editar 403, settings 403 e o lastSeq sem mudar ao sair. Nenhum teste chama POST /members como não-admin com onlyAdminsAddMembers=true (a linha 99 liga a opção, mas nada a exercita). OutsiderAccessIT só testa quem é de fora, que já recebe 404 de qualquer jeito. | curl (s1.out) com a bia como não-admin: enviar -> 403 "urn:papinho:problem:group-only-admins"; agendar 403; PATCH nome 403; foto 403; adicionar duda (contato) -> 403 group-only-admins; settings 403; promover 403; remover 403. Caio sai: "lastSeq visto pela bia antes=7 depois=7" e avisos ["group.created","group.added","group.settings","group.settings","group.renamed"]; caio depois de sair: GET 404, enviar 404. Responder em privado: {"body":"eu","replyTo":"<id do grupo>"}. e2e groups.spec.ts desktop e mobile: passed. | (1) requireSendable sem onlyAdminsSend: GroupIT:105 "expected: 403" FALHOU. (2) leave com appendSystem: GroupIT:124 "[sem aviso ao sair] expected: 7L but was: 8L" FALHOU. (3) requireCanEdit sem efeito: GroupIT:106 "expected: 403 but was: 200" FALHOU. (4) addMembers sem a checagem onlyAdminsAddMembers: GroupIT, PrivacyIT e OutsiderAccessIT PASSARAM; a mutação sobreviveu. Tudo foi desfeito. | PARCIAL | — | **PARCIAL** | O produto cumpre: a chamada pela API provou o 403 em todas as permissões. Pela regra de mutação, porém, a permissão "quem adiciona membros" não está provada: nenhum teste falha se ela for removida. Falta um teste de não-admin adicionando com onlyAdminsAddMembers=true. "Sair silenciosamente" aqui não deixa aviso nem para os admins (no WhatsApp os admins são avisados); não é exigido. |
| GR-02 | Convite por link e QR Code com aprovação de entrada opcional, respeitando "quem pode me adicionar a grupos" (PV-07). | feito: GroupIT.shouldJoinByInviteLink (prévia, entrar, aprovação, só admins geram, redefinir e revogar); e2e com o QR no painel | GroupService.java:291-312 (código só para admins), :316-325 (prévia), :331-365 (join, :351 aprovação -> PENDING), :369-392 (pedidos e decisão via requireAdmin), :422-431 (requireAddable, :427 privacy.canAddToGroups); PrivacyService.java:173-175 | routes/_app/g.$code.tsx, features/chat/components/join-group.tsx, group-details.tsx:463-513 (pedidos: aprovar e recusar) | GroupIT.shouldJoinByInviteLink testa de verdade o pending, o 404 do pendente e o 403 do não-admin. PrivacyIT.shouldRespectWhoCanAddToGroups testa o 403 group-add-not-allowed. O qrSvg só é testado com startsWith("<svg"). | curl (s2.out): groupsAdd=CONTACTS -> 403 "urn:papinho:problem:group-add-not-allowed"; depois que o edu adiciona a ana -> 200; NOBODY -> criar grupo 403. Com aprovação: join -> {"pending":true}; o pendente recebe GET 404, mensagens 404, enviar 404; não-admin: pedidos 403, aprovar 403; recusar 204 e depois 404; aprovar -> 200. O edu com NOBODY entra pelo link -> pending:false. Reset: link antigo 404; revogar: novo 404. QR decodificado com zxing: "decoded=http://localhost:18160/g/0iA3j9y6Tty-TkgQrCeiwA". | join sem joinApproval: GroupIT.shouldJoinByInviteLink:180 "Expecting value to be true but was false" FALHOU. requireAddable sem canAddToGroups: PrivacyIT:209 "expected: 403 but was: 201" FALHOU. Desfeitas. | CUMPRIDO | mantido | **CUMPRIDO** | decideJoin (GroupService.java:377-392) não confere MAX_MEMBERS ao aprovar (é um caso de borda com 1024 membros). Nenhum e2e entra pela rota /g/<código> nem aprova pela UI. |
| GR-03 | Lista de transmissão: cada destinatário recebe numa conversa individual, e só quem tem o remetente nos contatos recebe. | feito: BroadcastIT.shouldFanOutToContactsOnly; e2e groups.spec.ts | BroadcastService.java:93-107 (só contatos ativos e sem bloqueio), :86-90 (lista só do dono); MessageService.java:257-283 fanOut (:268 isContact(recipient, me) e bloqueio; :271 directWith); encaminhar :356-363 também espalha; enquete recusada :614-616 | features/chat/components/broadcast-dialog.tsx, broadcast-details.tsx; e2e/groups.spec.ts (criar lista e a mensagem chega na conversa individual) | BroadcastIT testa o 403 de não-contato, o 404 da lista para outros, a conversa DIRECT para quem tem o remetente e nada para quem não tem (nem em pedidos). | curl (s4.out): bia (tem a ana) -> [{"kind":"DIRECT","state":"ACTIVE","peer":"ana"}]; caio e duda (sem a ana) -> [] em conversas e em pedidos. No banco: "BROADCAST\|1 / DIRECT\|1". A bia não vê a lista (404 em conversa, destinatários e edição). Destinatário não-contato -> 403. Encaminhar para a lista também espalha (DIRECT\|2). | fanOut sem isContact: BroadcastIT:76 "Expecting empty but was: [{..."state":"REQUEST"...}]" FALHOU. validRecipients sem isContact: BroadcastIT:59 "expected: 403 but was: 201" FALHOU. Desfeitas. | CUMPRIDO | mantido | **CUMPRIDO** | Nenhuma relevante. |
| GR-04 | Lista de transmissão temporária: a lista expira numa data, e as mensagens enviadas por ela são temporárias. | feito: BroadcastIT.shouldHandleTemporaryLists; diálogo com validade e timer (DateTimePicker) | ConversationService.java:342-346 (lista vencida -> BroadcastExpiredException); Broadcasts.java:72-74 expired; MessageService.java:263-277 (expireAt no original e nas cópias); BroadcastService.java:109-121 validate (timer em MessagingPreferences.TIMERS, data no passado só no create); DisappearingMessagesJob | features/chat/components/broadcast-dialog.tsx:48-116 (expiresAt com DateTimePicker e messageTtlSeconds) | BroadcastIT testa o expiresAt da cópia e, depois de vencer a validade pelo banco, canSend=false e o 403. | curl (s4/s5/s6.out): expires_at - created_at = "1 day" no BROADCAST e no DIRECT; TTL 3600 -> 400; criar com data passada -> 400. Depois de 22:43:21: {"canSend":false,..."expired":true}; enviar -> 403 "urn:papinho:problem:broadcast-expired"; encaminhar -> 403. Com expires_at da mensagem forçado no passado, o job apagou a mensagem: a bia, depois do job, já não tem "traga-bebida"; count 0. | requireSendable sem expired: BroadcastIT:106 "expected: 403 but was: 201" FALHOU. Cópia sem TTL: BroadcastIT:98 "[cópia temporária] Expecting value to be false but was true" FALHOU. Desfeitas. | CUMPRIDO | mantido | **CUMPRIDO** | PUT /api/v1/broadcasts/{id} aceita expiresAt no passado (200) e reabre uma lista vencida, porque validate recebe now=null em BroadcastService.java:73. Antes do job, a mensagem vencida ainda aparece no histórico por até 60 s. |
| GR-05 | Lista: arquivar (com "manter arquivadas"), fixar até 3, silenciar 8 h, 1 semana ou sempre com menções, favoritar, pastas, limpar e apagar. | feito: ChatListIT (5 testes); e2e organize.spec.ts; menções em NotificationIT | ChatListService.java:29 MAX_PINNED=3, :62-81 pin (lockKey "pins:"+me e contagem), :94-103 mute + MuteDuration :246-255, :110-132 limpar e apagar, :147-205 pastas com requireMember; ConversationQueries.java:40-45 arquivada com keep_archived; NotificationDispatcher.java:80 | features/chat/components/conversation-menu.tsx, folder-settings.tsx, conversation-list.tsx; e2e/organize.spec.ts | ChatListIT testa o 409 da 4ª fixada, keepArchived, as durações do silêncio, IDOR 404 e limpar mantendo as favoritas. NotificationIT testa a menção com silêncio. | curl (s7.out): 3 fixadas -> 204; a 4ª -> 409 "urn:papinho:problem:conversation-pin-limit", também por outra sessão. Dois pedidos simultâneos -> "C1=204 C2=409" e 3 no banco. Silenciar EIGHT_HOURS -> 06:44:30 (agora 22:44:30); ONE_WEEK -> 2026-10-23; ALWAYS -> 9999-12-31; ONE_HOUR -> 400. Arquivar: depois de mensagem do caio archived=false; com keepArchived archived=true. Limpar: ana [] e caio vê as 3. Pasta e pin de outra pessoa -> 404. e2e organize.spec.ts desktop e mobile: passed. | MAX_PINNED=4: ChatListIT:84 "expected: 409 but was: 204" FALHOU. keep_archived falso: ChatListIT:60 [manter arquivadas] FALHOU. Dispatcher sem a exceção de menção: NotificationIT:168 ConditionTimeout FALHOU. Desfeitas. | CUMPRIDO | refutado → STATUS INCORRETO | **STATUS INCORRETO** | Nenhum e2e cobre apagar conversa nem silenciar com menções; o servidor está coberto por testes. |
| GR-06 | Filtros da lista; busca global de conversas, contatos e mensagens; busca dentro da conversa por texto, remetente, data e tipo, com navegação; tudo em PostgreSQL com unaccent, pg_trgm e portuguese. | feito: SearchIT (3); e2e com filtros todas/favoritas/grupos/lista/arquivadas, busca global e dentro da conversa; índices em V202610190200 | V202610190200__add_message_search.sql (extensões, papinho_portuguese, papinho_unaccent IMMUTABLE, índices GIN); MessageSearch.java:23-32, :41-50 (global com estado ACTIVE/REQUEST, sem trancadas), :56-90 (filtros); SearchService.java:34-56 (requireReadable -> 403 se trancada) | features/chat/organization.ts:221-243 (filtros no cliente, inclusive unread); conversation-search.tsx:24-211 (tipo, remetente, DatePicker, navegação :76-78); global-search.tsx | SearchIT testa acento, radical (conversas), trigrama, curinga, conversa alheia, apagada, escondida, trancada, filtros e IDOR. Não há teste de ex-membro (LEFT) na busca global. Não há teste (nem e2e nem Vitest) do filtro "Não lidas" nem dos filtros de remetente, data e tipo e da navegação pela UI. | psql: unaccent 1.1 e pg_trgm 1.6; cfgname papinho_portuguese; ix_messages_body_fts "USING gin (to_tsvector('papinho_portuguese'::regconfig, body))" e ix_messages_body_trgm "gin_trgm_ops". curl (s8.out): 'reuniao', 'ACOES', 'otimo' e 'Fortal' acham; duda e chefe (de fora) -> []; apagada não aparece; trancada sem token: global [] e dentro 403; ex-membro caio: global [] e dentro 404. Mas: "ana 'acao' -> []", "ana 'mensagem' -> []" (para "mensagens"), "ana 'reuniões' -> []". to_tsvector('papinho_portuguese','mensagens') @@ plainto_tsquery(...,'mensagem') = f. e2e organize.spec.ts: passed. | Global sem hiddenLocked: SearchIT:85 "[trancada sem token] Expecting empty" FALHOU. Global aceitando LEFT, PENDING e DECLINED: SearchIT PASSOU; a mutação sobreviveu. A busca do ex-membro só ficou provada pela chamada à API. Desfeitas. | PARCIAL | — | **PARCIAL** | No servidor a busca está correta e não vaza nada: conversas alheias, ex-membro, apagadas e trancadas ficam de fora. Faltam: (1) o filtro "Não lidas" e os filtros de remetente, data e tipo com navegação na UI não têm teste e não foram executados nesta sessão; (2) singular e plural não se juntam (ação/ações, reunião/reuniões, mensagem/mensagens), e o comentário da migração afirma o contrário; (3) a regra de estado da busca global não tem teste. |
| PV-01 | Desfoque com interruptor por item (mensagens, prévia, nomes, fotos, mídia). Revela com o mouse (na hora ou com atraso), com o foco do teclado e com toque longo. Respeita prefers-reduced-motion e tem o atalho global Alt+Shift+P. | feito: e2e privacy.spec.ts (atalho, foco, atraso de 3 s, toque longo pelo CDP, movimento reduzido), privacy.test.ts e PrivacyIT | PrivacyService.java:36-38 (padrões do Blur), :64-70 (atraso de 0 a 10000 ms) e :265 (record Blur). O servidor só guarda a escolha. | private-mode.ts:20-28 (atalho), :31-45 (atributos), :90-140 (toque longo de 450 ms); index.css:319-359 (regras de blur, hover dentro de @media (hover: hover), focus-within, data-revealed, reduced-motion); data-private em message-row.tsx:222, conversation-list.tsx:340/366, user-avatar.tsx:40, attachment-view.tsx:86 e video-player.tsx:87 | O e2e privacy.spec.ts:42 testa só o item 'messages'. Os itens previews, names, photos e media não são testados no navegador; só o vitest testa o mapeamento dos atributos. | `pnpm e2e privacy.spec.ts --project=desktop-chromium --project=mobile-chromium` contra o jar de referência: '7 passed (1.9m)'. vitest privacy.test.ts: '5 passed'. Chamada /api/v1/privacy pelo chefe: blur {"privateMode":false,"messages":true,"previews":true,"names":false,"photos":false,"media":true,"revealDelayMs":0} | (1) Tirei as regras :focus-within do index.css, gerei o build e servi com vite preview. O e2e falhou nos dois projetos (l.103 Expected "none" Received "blur(7.2px)"); o controle sem mutação deu 3 passed. (2) Pus o hover fora de @media (hover: hover) e o reduced-motion com 150ms. O mobile falhou na l.121 (filter "none") e o desktop na l.142 ("0.15s"). (3) No vitest, blurAttributes ignorando os interruptores: 2 falhas. Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL: os interruptores não são todos independentes. O anexo (data-private=media) fica dentro do BubbleContent data-private=messages (message-row.tsx:220-351). Com mensagens desfocadas e mídia não, a mídia fica desfocada mesmo assim, porque o filtro do pai vale para ela. O e2e não exercita prévia, nomes, fotos nem mídia. O servidor aceita atraso de até 10000 ms, mas o CSS para em 3000 ms (private-mode.ts:42). |
| PV-02 | Bloqueio de tela com PIN ou senha próprios, Argon2 no servidor e passkey. Gatilhos: inatividade, troca de aba e atalho. Tudo desligado por padrão. Fundo desfocado e inerte. | feito: PrivacyIT.shouldManageScreenLock; e2e (botão, atalho, PIN errado, recarregar, aba escondida, inatividade, passkey, inert, axe); README | ScreenLockService.java:310-312 (formato do PIN e do atalho), :332-334 (padrão desligado), :341-376 (troca exige o segredo atual), :393-400 (confere com Argon2 e limite de tentativas) | screen-lock.tsx:174 (atalho), :193-216 (inert e MutationObserver); index.css:362-367 (blur 1.5rem); screen-lock-settings.tsx:66-71 (shortcut "Alt+Shift+L" ligado ao criar o bloqueio) | PrivacyIT.shouldManageScreenLock confere $argon2, 403, 429 e que o hash não vai ao cliente. Os 3 testes do e2e passam. | Caixa-preta (multiplicador 1): padrão {"enabled":false,...,"lockOnHidden":false,"shortcut":null}. 10 PINs errados deram 403 e os seguintes 429. No banco, screen_locks = PIN\|$argon2id$v=19$m=16384,t=2,p=1. A Ana, sem bloqueio, recebe 404. O e2e passou (7 passed), incluindo 'body > [inert]'. | Com requireSecret sem o encoder.matches, o PrivacyIT falhou ('expected: 403 but was: 204'). Sem o rateLimiter.consume, também falhou ('expected: 429 but was: 403'). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. 'Tudo desativado por padrão' não vale na UI: ao ligar o bloqueio, o gatilho de atalho já vem ligado com Alt+Shift+L (screen-lock-settings.tsx:70), e o e2e confirma ('O atalho padrão também bloqueia'). O resto foi provado. |
| PV-03 | Conversa trancada: código Argon2 próprio ou compartilhado, pasta oculta, API só com token curto, notificação sem conteúdo e limite de tentativas. | feito: PrivacyIT.shouldLockConversations (403 em conversa, mensagens, sync e galeria; pasta; código compartilhado; 429; IDOR) e NotificationIT | ConversationLocks.java:45-46 (IDLE de 10 min e MAX_LIFETIME de 1 h), :121-135 (unlock com limite), :166-175 (hidden); ConversationService.java:140-143 (lista), :158-161 (busca), :247-253 (requireReadable); NotificationRecipients.java:80-81 | features/chat/api.ts:72-79 (pasta), offline.ts:64/83 (não guarda trancadas no IndexedDB) | PrivacyIT, SearchIT.shouldSkipHiddenDeletedAndLocked, ExportIT e NotificationIT.shouldHideContent. Nenhum teste cobre a validade do token, os agendamentos, a resposta citando outra mensagem nem a exportação da conta. | Sem token: conversa, mensagens, afterSeq=0, galeria, fixadas, busca na conversa, export, info, editar, votar e reagir dão 403; lista, busca global e favoritas dão []. Com token: 200. O token parou de valer depois de 10 min ocioso (403 às 23:05:50). Força bruta: 10x 403, depois 429 até com o código certo. lock_codes guarda $argon2id. VAZAMENTOS: GET /scheduled sem token devolve 200 com body "agendadasecreta"; POST de mensagem com replyToId devolve 201 com replyTo.body "respostasecreta" (texto da Bia); /account/export devolve 200 e o dados.json contém "segredoxyz https://exemplo.org/a" e "agendadasecreta". | requireReadable sem a tranca: PrivacyIT e SearchIT falharam (403 virou 200). Lista sem filtro: falhou. hiddenLocked vazio: SearchIT falhou. Sem rate limit: falhou (429 virou 403). Notificação sem a regra da tranca: NotificationIT falhou ("outro segredo"). IDLE e MAX_LIFETIME de 3650 dias: os testes PASSARAM (mutante sobreviveu). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. Há conteúdo da conversa trancada saindo sem token: agendadas (ScheduledMessageService.java:72-73 usa requireMember), citação em replyTo ao enviar (MessageService.java:136 e :168-174), exportação da conta (BK-02) e anexos por id (MessageMediaAccess.canRead, aceito na ADR 0013). Enviar mensagens para a trancada também funciona sem token. Nenhum teste prova que o token tem curta duração. |
| PV-04 | Visibilidade de visto por último, online, foto e recado: Todos, Meus contatos, Meus contatos exceto… ou Ninguém. O padrão é tudo visível. | feito: PrivacyIT (foto e recado nas 4 opções; visto por último); RealtimeIT.shouldHideOnlineByPrivacy | PrivacyService.java:36-38 (padrões EVERYONE), :125-142 (exceções só entre contatos), :146-157 (allows); ProfileService.java:118-138; PresenceService.java:91-96 e :115-119 | configurações de privacidade (não auditei a UI a fundo) | PrivacyIT.shouldApplyPhotoAndAboutVisibility, shouldApplyPresenceReciprocity e shouldStartWithWhatsAppDefaults; RealtimeIT. | Caixa-preta do recado: EVERYONE -> bia e chefe veem; CONTACTS -> só a bia; CONTACTS_EXCEPT[bia] -> null para os dois; NOBODY -> null; exceção com quem não é contato -> 400. Visto por último: CONTACTS_EXCEPT[bia] -> [false,false]; chefe sem conversa -> 404. Teste STOMP temporário: com Bruno online=NOBODY e lastSeen=NOBODY, a Ana recebe {"type":"presence",...,"online":false,"lastSeenAt":null} a cada vez que ele fica online ou offline. Com online=NOBODY e lastSeen=EVERYONE, o evento ao ficar online traz lastSeenAt:null enquanto o REST mostra lastSeenAt "2026-10-16T22:56:51Z". | CONTACTS_EXCEPT sem a exceção: falhou. CONTACTS -> true: falhou. Padrão online=CONTACTS: falhou (shouldStartWithWhatsAppDefaults e RealtimeIT). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. O online escondido vaza pelo STOMP: PresenceService.refresh (:130-150) manda um evento a cada transição para todos os pares, mesmo quando nada é visível. Isso entrega a hora exata de entrada e saída, e com o visto por último visível o lastSeenAt=null denuncia 'online agora'. A opção CONTACTS_EXCEPT do online não tem teste específico. |
| PV-05 | Reciprocidade no servidor: quem esconde o visto por último ou o online não vê o dos outros, e quem desliga a confirmação de leitura não vê a dos outros. | feito: PrivacyIT.shouldApplyPresenceReciprocity e shouldApplyReadReceiptRules; RealtimeIT | PrivacyService.java:163-169 (canSeePresence); ConversationService.java:518-525 (peerReadSeq), :393-410 (receipts pelo STOMP); MessageService.java:496-503 (info) | n/a (regra do servidor) | Presença e peerReadSeq são bem testados. A reciprocidade no 'Dados da mensagem' e nos eventos STOMP de leitura não tem teste que falhe. | Caixa-preta: com a bia em lastSeen NOBODY, bia->ana e ana->bia dão [false,false]. Leitura: com a Ana desligando o global, info readAt=null e peerReadSeq=null (antes eram true e 1). Os testes de base passaram. | canSeePresence sem o allows recíproco: PrivacyIT e RealtimeIT falharam. peerReadSeq sem iShare: falhou. publishReceipts mandando a leitura também para quem não compartilha: PASSOU (sobreviveu). info sem iShare: PASSOU (sobreviveu). Tudo desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. A reciprocidade de presença vaza pelo STOMP (o canal lateral descrito em PV-04). Os mutantes da leitura pelo STOMP e no info sobreviveram, então essas regras não estão provadas por teste; o info está certo hoje só pela caixa-preta. |
| PV-06 | Confirmação de leitura global, com sobreposição por conversa e por grupo. No grupo é ligada por padrão. Vale a reciprocidade no escopo desligado. | feito: PrivacyIT.shouldApplyReadReceiptRules (global, por conversa, grupo, Dados da mensagem, IDOR) | ConversationService.java:178-190 (changeReadReceipts), :259-273 (receiptSharers e shares), :561 (grupo) | seleção nos painéis (não auditei) | PrivacyIT.shouldApplyReadReceiptRules | Caixa-preta: ligar só nesta conversa com o global desligado devolve peerReadSeq=1. Outra pessoa (chefe) muda read-receipts da conversa -> 404. | Sem a sobreposição: falhou ('sobreposição por conversa liga de novo' esperava 2, veio 0). Grupo seguindo o global (shares: return global==null\|\|global): PASSOU (sobreviveu). Tudo desfeito. | PARCIAL | — | **PARCIAL** | O comportamento observado está certo. Mas 'no grupo é sempre enviada' não está provado nos caminhos Java (info e STOMP): o mutante que faz o grupo seguir o global sobreviveu, porque o 'lida por todos' vem do SQL. Falta um teste com o global desligado e o grupo sem sobreposição, conferindo info e eventos. |
| PV-07 | Quem pode me adicionar a grupos, com as mesmas quatro opções. | feito: PrivacyIT.shouldRespectWhoCanAddToGroups | PrivacyService.java:172-175; GroupService.java:422-431 | mostra o detalhe do erro (não auditei) | PrivacyIT.shouldRespectWhoCanAddToGroups (CONTACTS e NOBODY) | Caixa-preta: com a bia em CONTACTS_EXCEPT[ana], a Ana cria grupo com a bia e recebe {"status":403,"type":"urn:papinho:problem:group-add-not-allowed"}. Com EVERYONE, 201. | requireAddable sem a checagem: PrivacyIT falhou ('expected: 403 but was: 201'). Desfeito. | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | CONTACTS_EXCEPT só foi provado na caixa-preta; nenhum teste automatizado cobre essa opção em PV-07. |
| PV-08 | Opção de ocultar o conteúdo nas notificações: mostra só 'Nova mensagem'. | feito: NotificationIT.shouldHideContent | PrivacyService.java:192-195; NotificationDispatcher.java:62-90 (título 'Papinho' e corpo notification.hidden, tanto no STOMP quanto no Web Push) | o service worker mostra o payload pronto (o servidor decide) | NotificationIT.shouldHideContent confere título e corpo no push | Base NotificationIT 7/0 nesta sessão. O padrão hideNotificationContent=false foi visto na caixa-preta. | `if (hideContent \|\| recipient.hidden())` -> `if (recipient.hidden())`: NotificationIT falhou ('expected: "Papinho" but was: "Pessoa ana"'). Desfeito. | CUMPRIDO | mantido | **CUMPRIDO** | Não testei Web Push de verdade num navegador (ficou só o IT com o push capturado). |
| RT-01 | Cada conversa tem seq monotônico e a sincronização usa afterSeq. A sincronização perde mensagens quando há envios ao mesmo tempo: o lastSeq é lido depois da busca e o cliente pula o cursor por cima de mensagens que não recebeu. | feito; MessagingIT.shouldSyncBySeqAndDeduplicate e shouldPageHistoryBackwards, cache.test.ts e e2e por afterSeq | SequenceAllocator.java:25-34 (UPDATE ... RETURNING); MessageService.java:137 (lock); :309-316 sync (findChangedAfter e depois sequences.current); V202610151900__create_messaging.sql:66 uq (conversation_id, seq) | features/chat/api.ts:229-236 (afterSeq=cursor); cache.ts:56-58 (cursor = max(cursor, page.lastSeq)); cache.ts:21 (cursor inicial = lastSeq); use-realtime-sync.ts:86-91 | MessagingIT.shouldSyncBySeqAndDeduplicate e shouldSendConcurrentlyWithoutExhaustingConnections (30 envios em paralelo, só confere que não há duplicata, não confere buraco); cache.test.ts e sync.test.ts (10 passaram). Nenhum teste cobre envio concorrente durante o sync. | rt08.py parallel 40 -> 'status: [201] count 40', seqs 2..41; psql -> '41\|41\|1\|41\|41' (count, distinct, min, max, last_seq). rt08.py syncrace 300 (algoritmo do cache.ts) -> 'enviadas=300 syncs=694 perdidas_pelo_algoritmo_do_cliente=11'; outra rodada -> 'SALTO: afterSeq=502 veio changeSeq=[] mas lastSeq=503' | A: next() feito como leitura e depois escrita, sem lock -> shouldSendConcurrently...:88 FALHOU ('duplicate key ... uq_messages_conversation_seq'). B: afterSeq ignorado (0) -> shouldSyncBySeqAndDeduplicate:63 FALHOU. Revertidas, git diff vazio. | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. O seq é monotônico e não tem buracos nem duplicatas, mesmo com concorrência. Já 'recebe o que perdeu' falha: entre findChangedAfter e current() (comandos separados em READ COMMITTED) uma mensagem é confirmada, a resposta vem sem ela e com lastSeq maior, e o cliente pula o cursor. Perdeu 11 de 300 e 14 de 200. O history() tem o mesmo padrão. A reconexão com a rede caída não foi testada no e2e. |
| RT-02 | Reenviar com o mesmo clientMessageId devolve a mesma mensagem, também numa corrida de 20 envios simultâneos. Com o mesmo id em duas conversas ao mesmo tempo, o servidor responde 500 em vez de 409, mas não duplica. | feito; MessagingIT.shouldSyncBySeqAndDeduplicate (reenvio 200, outro chat 409); cache.test.ts | MessageService.java:137-144 (lock da conversa, findBySenderIdAndClientMessageId, 409 se for de outra conversa); MessagingController.java:157-161; V202610151900:67 uq (sender_id, client_message_id) | features/chat/offline.ts:122-160 (fila reenvia com item.clientMessageId); cache.ts:60-66 (pendente some ao confirmar) | MessagingIT.shouldSyncBySeqAndDeduplicate confere o reenvio sequencial (200 e mesmo id) e o 409 sequencial. Não há teste de corrida. | rt08.py samecid 20 -> 'status: Counter({200: 19, 201: 1})', 'ids: Counter({...: 20})'; psql count(client_message_id) -> 1. rt08.py crossconv 30 -> 'pares de status: Counter({(201, 500): 30})'; log: 'duplicate key value violates unique constraint "uq_messages_sender_client_message_id"' | C: remover a busca do existente -> shouldSyncBySeqAndDeduplicate:55 FALHOU ('expected: 200'). Revertida. | CUMPRIDO | mantido | **CUMPRIDO** | Nunca duplica (comprovado). Com o mesmo id em duas conversas ao mesmo tempo, o servidor responde 500 em vez de 409 (achado). A fila offline reenviar após uma resposta perdida, com o primeiro envio já gravado, não tem e2e. |
| RT-03 | Presença por heartbeat a cada 20 s, com a visibilidade da aba. O servidor tira o online depois de 45 s sem heartbeat ou quando a aba está oculta. | feito; RealtimeIT.shouldTrackPresence; e2e 'online' no cabeçalho | PresenceService.java:36 (STALE_AFTER 45 s), :74-78 heartbeat(active), :81-84 varredura a cada 15 s, :121-128 isOnline | lib/realtime.ts:52 (20 s), :101-107 (visibilitychange/focus/blur/interval), :152-156 (active = visibilityState==='visible') | RealtimeIT.shouldTrackPresence confere a aba oculta. A expiração do heartbeat não tem nenhum teste: a mutação E sobreviveu. | e2e próprio no Chromium (instância de referência): 'AUDIT presença com aba visível: online' / 'AUDIT presença com aba oculta: não online' / 'AUDIT presença de volta: online'; heartbeat bloqueado com o WS aberto -> 'AUDIT online sumiu sem heartbeat depois de 50 s; frames bloqueados: 2' | D: Beat(true, ...) ignorando active -> RealtimeIT.shouldTrackPresence:160 FALHOU ('aba em segundo plano não conta'). E: ignorar o tempo do heartbeat -> RealtimeIT+PrivacyIT 13/13 PASSARAM (sem cobertura). Revertidas. | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | O comportamento foi comprovado no navegador e no servidor. O teste do repositório não cobre a expiração do heartbeat (mutação E sobreviveu). A janela visível sem foco conta como online, porque só a visibilidade conta. |
| NT-01 | Notification API com o app aberto e Web Push com VAPID e aes128gcm. A permissão só é pedida pelo botão. A entrega real por um serviço de push não foi exercitada. | feito; WebPushCryptoTest, NotificationIT, e2e pwa.spec (notificação com o app aberto, permissão só pelo botão); push real não testado | notification/NotificationDispatcher.java:62-121; internal/WebPushCrypto.java, internal/Vapid.java; PushSubscriptions.java:99-102 (lista de serviços aceitos, só HTTPS) | features/notification/push.ts:33-60 (requestPermission só em enableNotifications); notification-settings.tsx:43-45 e botão 'Ativar notificações'; use-notifications.ts:30-52; sw.ts:52-92 | NotificationIT 7/7; WebPushCryptoTest 4/4. pwa.spec 'notificações...' usa grantPermissions, por isso não prova o pedido de permissão (a prova veio do meu e2e). | it08 NotificationIT -> 'Tests run: 7, Failures: 0'; ut08 WebPushCryptoTest -> 'Tests run: 4, Failures: 0'; curl: evil.example.com -> 400, http://fcm -> 400, https://fcm -> 204, sem sessão -> 401; psql push_vapid_keys private 'v1:W5dW...' (cifrada); e2e próprio: 'requestPermission antes do clique: null', depois do clique '1'; pwa.spec 3/3 passou | F (não exigida): 'ocultar conteúdo' desligado -> NotificationIT.shouldHideContent:140 FALHOU ('expected: "Papinho"'). Revertida. | PARCIAL | — | **PARCIAL** | Não verificável aqui: a entrega real de Web Push por FCM, Mozilla ou Apple com o app fechado (o Chromium do Playwright não tem Push API) e o comportamento no Firefox, Safari e Edge. O e2e do repositório não prova 'permissão só após ação explícita', porque concede a permissão antes. |
| NT-02 | Configurações > Notificações mostra o aviso de que o push depende de internet e, no iOS fora do app instalado, o aviso de instalar o app. | feito; e2e confere o aviso de internet; README | n/a | features/notification/push.ts:19-27 (iosNeedsInstall); notification-settings.tsx:96-111 | pwa.spec.ts:194 confere o aviso de internet. O do iOS não tinha teste. | e2e próprio com UA de iPhone: 'AUDIT alertas com UA de iPhone: ["O push depende de internet...","No iPhone e no iPad, instale o app..."]' | não feita: requisito não crítico, só texto na tela | CUMPRIDO | mantido | **CUMPRIDO** | A detecção do iOS só foi testada com o UA falsificado, não num aparelho real (iPadOS com UA de Mac depende de maxTouchPoints). |
| NT-03 | Preferências global, individual, grupos, menções e som no servidor. 'Por conversa' é o silenciar. O contador de não lidas passa pela Badging API na página e no service worker. | feito; NotificationIT; e2e de som desligado; Badging sem verificação automatizada | NotificationDispatcher.java:74-95 (decide); NotificationPreferences.java:30-59 | use-notifications.ts:54-63 (setAppBadge/clearAppBadge); sw.ts:63-69; notification-settings.tsx | NotificationIT.shouldRespectMuteAndPreferences, shouldNotifyRecipient e shouldHideContent; pwa.spec som desligado chega silent:true | NotificationIT 7/7; curl GET /notifications/preferences -> {'enabled':True,'direct':True,'groups':True,'mentions':True,'sound':True}; e2e próprio com espião em navigator.setAppBadge: 'AUDIT badges: ["clear",1]'; pwa.spec notificações passou | F (ver NT-01) detectada | CUMPRIDO | mantido | **CUMPRIDO** | O som é só global (não há som por conversa). O badge do service worker no push não foi verificado. Badging só foi verificado com espião, porque o headless não mostra o contador. |
| NT-04 | Manifesto completo (standalone, ícones 192 e 512, maskable 512, screenshots wide e narrow reais). O service worker com injectManifest só troca de versão pelo botão Atualizar. | feito; pwa.spec (manifesto, standalone, maskable, capturas, troca de versão) | servido como estático (Content-Type application/manifest+json) | vite.config.ts:18-73 (registerType prompt, manifest); src/sw.ts:34-38 (skipWaiting só na mensagem SKIP_WAITING); lib/pwa.ts:17-33 (toast 'Nova versão' e Atualizar) | pwa.spec.ts:31 (manifesto) e :124 (versão nova espera o Atualizar) | curl manifest -> 'Content-Type: application/manifest+json', display 'standalone', maskable-512 'maskable'; PNGs 192x192, 512x512, 512x512, 1280x800, 390x844; sw.js 'Cache-Control: no-cache'; pwa.spec 'versão nova do app espera o Atualizar para assumir' passou (4.9s) | não feita: não crítico. O e2e confere waiting:'installed' até o clique. | CUMPRIDO | — | **CUMPRIDO** | A instalação num aparelho real não foi testada. A zona segura do ícone maskable não foi medida. |
| NT-05 | IndexedDB por conta (Dexie), apagado no logout e na troca de conta. Fila de texto com clientMessageId, avisos de offline e reconexão, nenhuma resposta /api no Cache Storage. Mas uma conversa trancada depois de guardada continua no IndexedDB. | feito; e2e pwa.spec; lacuna diz 'trancadas nunca guardadas; outra conta no navegador apaga os bancos anteriores' | n/a (lado cliente); dedupe da fila em MessageService.java:137-144 | lib/offline-db.ts:41-64 (banco por usuário, apaga os outros), :89-102 (clearOfflineData), :116-118 (deleteSnapshot NUNCA usado); features/chat/offline.ts:22, :59-113 (persist), :122-167 (flushOutbox); sw.ts:17-32; components/layout/connection-banner.tsx | pwa.spec.ts:31 confere a fila, o banner, o reload offline, o Cache Storage sem /api e o logout apagando o banco. Não há teste de troca de conta nem de trancar depois de guardar. | pwa.spec offline passou (17.2s). e2e próprio: troca de conta sem logout -> '[ papinho-01a14716-9088... ]', o banco anterior foi apagado; 'AUDIT Cache Storage: 76 entradas; não-app: []'; depois de trancar: 'messages:01a14716-9c4e-...={"messages":[...' e "AUDIT texto 'Segredo guardado' ainda no IndexedDB: true" | N1: sw guarda GET /api no Cache Storage -> pwa.spec.ts:70 FALHOU (15 URLs /api). N2: sem onLogout(clearOfflineData) -> pwa.spec.ts:114 FALHOU ('papinho-01a1471b-...' continua). Jars mutados construídos no worktree e revertidos. | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. Os sub-itens literais funcionam, mas a alegação 'trancadas nunca guardadas' do STATUS (e o contrato do PV-03: 'NT-05 não pode guardar trancadas') é falsa: trancar uma conversa já guardada deixa as mensagens e o detalhe no IndexedDB. A troca de conta funciona, mas o repositório não tem teste. O reenvio da fila após uma resposta perdida não tem e2e. |
| SG-01 | No servidor, o IDOR está correto e os testes falham quando as checagens de dono saem. Logs limpos. Mas há erros fora do Problem Details (firewall do Spring Security e Tomcat). Os testes também não pegam a autorização por destino STOMP, a checagem de Origin, a checagem de membro no 'digitando', o handler de 500 nem um log com dado pessoal. | feito: Problem Details em 401/403/400/500 (SecurityIT) e erros do MVC (ProblemDetailsIT). IDOR por módulo e OutsiderAccessIT (41 ações). 'WebSocket só assina a própria fila (WebSocketSecurityConfig)'. E-mail, token e chaves fora dos logs. | messaging/ConversationService.java:276-278 (requireMember → 404); media/MediaService.java:159-168 (requireReadable); media/MediaService.java:52-55 (requireOwned para anexo); shared/realtime/WebSocketSecurityConfig.java:24-30 (subscribe só /user/queue/**, send /app/**, denyAll); shared/realtime/WebSocketConfig.java:45 (sem setAllowedOrigins); messaging/ConversationService.java:416-424 (typingAudience); shared/error/GlobalExceptionHandler.java:60-68 (500 genérico); shared/security/SecurityConfig.java:170-183 (cadeia final denyAll) | n/a | Pegaram as mutações: OutsiderAccessIT.shouldRefuseOutsiders, MessagingIT.shouldEnforceBlocksAndMembership e shouldHideForMe, ChatListIT, SearchIT, MediaIT.shouldShareAttachmentWithConversationOnly, StickerIT e TranscriptionIT. A cadeia denyAll só é pega por tabela, em SecurityIT.shouldProtectSensitiveActuatorEndpoints. Nenhum teste cobre Origin no handshake, destino STOMP, membro em /app/activity, conteúdo do 500 ou ausência de dado pessoal nos logs do login. | Varredura com curl como carol (idor-carol.txt): '404 GET /api/v1/conversations/<convAna>/messages', '404 GET /api/v1/media/<img>/content', '404 GET /api/v1/view-once/<token>', '404 HEAD/PATCH/DELETE /api/v1/uploads/<id> (carol)', '403 GET /api/v1/admin/users'. Corpo com id alheio: '400 ... Só é possível anexar arquivos que você enviou.', forward alheio '404'. STOMP (stomp.txt): 'carol SUBSCRIBE /user/<idAna>/queue/events CONNECTED -> ERROR', 'carol SEND /app/activity conv ana-bia: ana recebeu 0 eventos', controle com a bia: 1 evento. Handshake (hs.sh): '403 handshake cookie, origin evil', '401 handshake sem cookie'. Problem Details: '500 POST .../messages :: {"detail":"Não foi possível concluir a ação...' sem stack. Fora do padrão: '[firewall //] 400 application/json :: {"timestamp":...,"error":"Bad Request","path":"/api//v1/me"}', '[firewall %00] 400 text/html ... HTTP Status 400 – Bad Request'. Logs: busca de ana/bia/segredo/senha-bem/Pizza/4321/tokens → 0 ocorrências; só 'Account registered userId=<uuid>'. | D: requireMember usando o membro de outra pessoa → falharam OutsiderAccessIT:141, MessagingIT:248/348, ChatListIT:121/146 e SearchIT:113. C: requireReadable devolvendo o arquivo → falharam MediaIT:179, StickerIT:89, TranscriptionIT e OutsiderAccessIT. B: denyAll trocado por permitAll → SecurityIT:37 (esperava 401, veio 404). A: setAllowedOriginPatterns("*") + simpSubscribeDestMatchers("/**") + typingAudience sem requireMember → 36/36 passaram (NÃO PEGO). B: 500 com Arrays.toString(stackTrace) → ProblemDetailsIT e SecurityIT passaram (NÃO PEGO). G: log.info da senha, do usuário e do IP em claro → 37/37 passaram (NÃO PEGO). Tudo desfeito com git checkout. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. (1) 'Problem Details em todos os erros' não vale para o que o StrictHttpFirewall recusa (//, %2e%2e, ;), para os métodos FOO e TRACE (JSON padrão do Boot, sem CSP nem nosniff) nem para os 400 do Tomcat (%00, %zz, cabeçalho grande: página HTML). (2) A alegação 'Problem Details em ... 500 (SecurityIT)' é falsa: nenhum teste exercita o 500. (3) Os testes não provam a autorização por destino STOMP, a validação de Origin nem a checagem de membro no 'digitando' (o comportamento correto aparece só na caixa-preta). (4) Nenhum teste pega um log de senha ou usuário em claro. (5) Um NUL em campos de texto dá 500 em vez de 400. (6) Handshake sem Origin é aceito (101); só clientes que não são navegador fazem isso. |
| SG-02 | Na resposta real, CSP sem unsafe-inline, nosniff, XFO DENY, Referrer-Policy, Permissions-Policy, COOP e CORP estão presentes. O HSTS só sai com PAPINHO_HSTS=true e HTTPS. O teste confere pouco: as mutações de object-src, Permissions-Policy e HSTS ligado sobreviveram. | feito: SecurityIT.shouldServeSpaShellWithSecurityHeaders; o e2e falha em qualquer violação de CSP; HSTS por PAPINHO_HSTS=true | shared/security/SecurityProperties.java:33-49 (DEFAULT_CSP com 'wasm-unsafe-eval', DEFAULT_PERMISSIONS_POLICY); shared/security/SecurityConfig.java:185-199 (applyHeaders, HSTS 31536000 includeSubDomains quando hsts=true); resources/application.yaml:100 (hsts: ${PAPINHO_HSTS:false}) | n/a (CSP aplicada pelo servidor; o e2e não foi rodado nesta sessão) | SecurityIT:79-93 só confere contains("default-src 'self'"), contains("script-src 'self'"), frame-ancestors, ausência de unsafe-inline e unsafe-eval, e doesNotContainHeader(HSTS). Não confere object-src, base-uri, form-action, Permissions-Policy nem CORP, e não há teste com HSTS ligado. | curl -D - http://localhost:18190/ (hdr.txt): 'Content-Security-Policy: default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; ... object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'', 'X-Content-Type-Options: nosniff', 'X-Frame-Options: DENY', 'Referrer-Policy: same-origin', 'Permissions-Policy: camera=(self), microphone=(self), geolocation=()...', 'Cross-Origin-Resource-Policy: same-origin'. Instância 18191 com PAPINHO_HSTS=true e native: '-H X-Forwarded-Proto:https → Strict-Transport-Security: max-age=31536000 ; includeSubDomains'; sem o Proto, ausente. Na instância padrão, sem HSTS. | Lote A: remover "object-src 'none'" do DEFAULT_CSP, remover .permissionsPolicyHeader e trocar o HSTS ligado por disable → SecurityIT 12/12 e LanSetupIT 7/7 passaram (NÃO PEGO). Desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. O comportamento em produção está correto quando PAPINHO_HSTS=true, mas o HSTS depende de uma variável manual (padrão false) e nenhum teste prova que ele é enviado. Os testes deixam passar a perda de object-src, Permissions-Policy, base-uri e form-action. A CSP traz 'wasm-unsafe-eval' e connect-src para huggingface.co (aceitos no ADR 0008), o que diverge do 'script-src 'self'' citado no AGENTS.md. As respostas 400 do StrictHttpFirewall saem sem nenhum cabeçalho de segurança. |
| SG-03 | Medi os 429 no servidor real com multiplicador 1 em login (conta e IP), convites (criação e verificação), códigos de desbloqueio (tela e trancadas), envio de mensagens e upload. As mutações de login, envio, upload e desbloqueio foram pegas. O limite de convites não tem teste. | feito: RateLimiter (Bucket4j) em login, cadastro, recuperação, convites e links de grupo (60/h), upload 200/h, envio 120/min (MessagingIT.shouldRateLimitSending), códigos de desbloqueio 10/15 min (PrivacyIT), IdentityIT 429 com Retry-After, MediaIT.shouldRateLimitUploads | shared/security/RateLimiter.java:27-54 (políticas) e :71-82 (consume); identity/IdentityService.java:138,140 (login IP/conta); identity/InviteService.java:53 (criação) e :88 (verificação); privacy/ScreenLockService.java:115; privacy/ConversationLocks.java:83,123; messaging/MessageService.java:146,606; media/UploadService.java:91 | n/a | Pegaram as mutações: IdentityIT.shouldRateLimitLogin:327, MessagingIT.shouldRateLimitSending:323, MediaIT.shouldRateLimitUploads:103, PrivacyIT.shouldManageScreenLock:260 e PrivacyIT.shouldLockConversations:324. Não encontrei teste de 429 para a criação ou verificação de convites (grep '429' em *IT.java: só Forwarded, EmailRecovery, Identity, Media, Messaging e Privacy). | rl.sh (x1): login errado na bia '10 401  2 429', '429 ... Retry-After: 89'; convites '30 201  2 429'; invites/status '60 200  2 429'; PIN '10 403  1 429' e depois o PIN certo '429 ... Aguarde 89 segundos'; trancada '9 403  2 429'; upload '200 201  3 429', 'Retry-After: 12'. rl2.sh em paralelo (8 workers): '55 201  145 429' em 2,1 s; chefe segue '201'. Em série, 125 mensagens deram todas 201: a recarga greedy repõe cerca de 2 por segundo. | Lote B (comentei os consume): login (IdentityIT:327 FALHOU), envio (MessagingIT:323 FALHOU), upload (MediaIT:103 FALHOU), trancadas (PrivacyIT:324 FALHOU), convites InviteService:53 (nenhum teste falhou: NÃO PEGO). Lote C: bloqueio de tela (PrivacyIT:260 FALHOU). Desfeito. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL, por pouco: o comportamento existe e foi medido em todos os itens do requisito, mas o limite de convites (um dos itens do SG-03) não é provado por teste nenhum, e o STATUS o lista como evidência. Os limites ficam em memória, numa instância só (aceito no ADR 0004). O envio de 120/min tolera rajadas acima de 120 por causa da recarga greedy. |
| SG-04 | Toda regra PV que tem efeito no servidor (PV-02 a PV-08) é aplicada no servidor e tem teste de integração. Cada mutação da regra derrubou o teste. O PV-01 é desfoque visual; o servidor só guarda as escolhas. | feito: PrivacyIT (7), RealtimeIT.shouldHideOnlineByPrivacy e NotificationIT (PV-03 e PV-08 nas notificações) | privacy/PrivacyService.java:146-157 (allows), :164-169 (reciprocidade), :173-175 (grupos), :193-195 (ocultar conteúdo), :198-205 (foto e recado); privacy/ConversationLocks.java:166-175 (hidden/token) e :121-128; privacy/ScreenLockService.java:105-120 (Argon2 e limite); messaging/ConversationService.java:259-272 (receiptSharers/shares) | PV-01 só no cliente (não li a fundo; e2e privacy.spec.ts não rodado nesta sessão) | PrivacyIT: shouldStartWithWhatsAppDefaults (PV-01 guarda as escolhas, PV-04 padrões), shouldApplyPhotoAndAboutVisibility (PV-04), shouldApplyPresenceReciprocity (PV-04/05), shouldApplyReadReceiptRules (PV-05/06), shouldRespectWhoCanAddToGroups (PV-07), shouldManageScreenLock (PV-02), shouldLockConversations (PV-03); RealtimeIT.shouldHideOnlineByPrivacy (PV-04/05); NotificationIT.shouldHideContent (PV-08 e PV-03). PV-02 a PV-08 têm, cada um, pelo menos um IT que afirma o comportamento. | Mutações rodadas (mut-E.log, mut-F.log): 'PrivacyIT.shouldApplyPresenceReciprocity:126 [reciprocidade: Bruno também não vê o da Ana]', 'PrivacyIT.shouldApplyReadReceiptRules:157 [Bruno desligou: Ana não vê a leitura dele]', 'PrivacyIT.shouldRespectWhoCanAddToGroups:209', 'PrivacyIT.shouldApplyPhotoAndAboutVisibility:94', 'PrivacyIT.shouldLockConversations:287', 'RealtimeIT.shouldHideOnlineByPrivacy:198', 'NotificationIT.shouldHideContent:140'. PV-02: com BCrypt, PrivacyIT:239 (o hash precisa começar com $argon2); sem o limite, PrivacyIT:260. Na caixa-preta, o PIN errado leva 429 depois de 10 tentativas, e um token da view-once ou uma conversa trancada de outra pessoa levam 404. | E: canSeePresence sem o segundo allows, canAddToGroups=true, canSeePhoto=true, hidden=false, shares=true → 6 falhas, uma por regra. F: hidesNotificationContent=false → NotificationIT.shouldHideContent FALHOU. PV-02: BCrypt (lote B) e consume do bloqueio de tela removido (lote C) → PrivacyIT FALHOU. Desfeito. | CUMPRIDO | refutado → PARCIAL | **PARCIAL** | O PV-01 (desfoque) é só visual: no servidor há apenas a persistência das escolhas (PrivacyIT.shouldStartWithWhatsAppDefaults). Não existe regra de servidor para aplicar, o que é coerente. Não testei o desbloqueio por passkey do PV-02. |
| UI-01 | Duas colunas no desktop (lista + conversa, painel de detalhes opcional) e pilha no celular com rota própria /c/$id | feito; e2e a11y.spec com 767/768/769 e 1279/1280/1281; moderation/media usam o painel | n/a | frontend/src/components/layout/app-shell.tsx:154 (md:grid-cols-[minmax(18rem,26rem)_1fr]), :159 e :339 (hidden md:flex); conversation-view.tsx:233 useMediaQuery('(min-width: 80rem)'); rota routes/_app/c.$conversationId.tsx | a11y.spec.ts:240 'bordas dos breakpoints' afirma main oculto/visível, sidebar, gaveta x terceira coluna e ausência de rolagem horizontal; roda só em desktop-chromium | ui-e2e.sh ref: '✓ 7 [desktop-chromium] › e2e/a11y.spec.ts:240:1 › bordas dos breakpoints de colunas e do painel de detalhes (19.1s)'; mobile-chromium smoke/a11y 320 px também passaram | md: -> lg: em app-shell.tsx:154/159/339, JAR reconstruído: '✘ 2 [desktop-chromium] › e2e/a11y.spec.ts:240:1' com 'Expect "toBeVisible" getByRole('main')' em a11y.spec.ts:265; revertido (git diff vazio) | CUMPRIDO | mantido | **CUMPRIDO** | O teste de breakpoints só roda no desktop-chromium. Não testei aparelho real. |
| UI-02 | Temas claro/escuro/sistema, papel de parede, tamanho de fonte e densidade | feito; Configurações > Aparência; display-prefs.test.ts; e2e a11y confere atributos, 18 px, papel de parede e axe nos dois temas | n/a (preferência por aparelho) | frontend/src/components/theme-provider.tsx:5,89,94; features/account/components/appearance-settings.tsx:34-56; lib/display-prefs.ts (data-wallpaper/font-size/density); index.css:371-410 | a11y.spec.ts:181 afirma os atributos, 18px e radial-gradient depois de recarregar. Nenhum e2e escolhe o tema explicitamente: só emulam o tema do sistema. | e2e ref: '✓ 6 [desktop-chromium] › a11y.spec.ts:181:1 › aparência: papel de parede, fonte e densidade (16.2s)' e o mesmo no mobile; ui-theme.mjs (JAR de referência): 'Escuro {"cls":"dark","scheme":"dark"}', 'Escuro após recarregar {"cls":"dark","scheme":"dark"}', 'Claro com sistema escuro {"cls":"light","scheme":"light"}', 'Sistema (escuro) {"cls":"dark"...}' | não feita (não é crítico). Indiretamente, o JAR com .dark mutado deu 'Escuro {"cls":"dark","scheme":"light"}' na mesma sonda. | CUMPRIDO | mantido | **CUMPRIDO** | A preferência não sincroniza entre aparelhos (aceitável). A escolha explícita de tema não tem e2e no repositório. |
| UI-03 | Todos os estados: carregando, vazio, erro, offline, sem permissão | feito; QueryState em telas secundárias; states.spec (409, rede cortada, 403); lacuna admite que lista, conversa, contatos, galeria e busca 'mantêm os seus' | n/a | components/query-state.tsx (completo) só em admin, sessões, bloqueados, favoritas etc.; conversation-list.tsx:100-105 só mostra um Alert genérico; conversation-view.tsx:125-158 só 'Algo deu errado' + 'Ir para o início'; contact-list.tsx:94-99 só um Alert genérico | states.spec.ts cobre só /settings/blocked, /settings/sessions e /admin; query-state.test.tsx | e2e ref: '✓ 15 [desktop-chromium] › states.spec.ts:9:3' (passou). ui-states2.mjs com contexto novo e falha forçada: '{"target":"lista","kind":403,"text":"... Não foi possível concluir a ação. Tente de novo em instantes.","retry":0}', o mesmo para offline e 500; '{"target":"conversa","kind":"offline","text":"Algo deu errado Ir para o início","retry":0}', o mesmo para 403 e 500 | não feita (não é crítico); a sonda já mostra a falta | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. Nas duas telas principais (lista de conversas e conversa) e em contatos, o erro é genérico: não tem 'Tentar de novo' e não separa sem conexão nem sem permissão. O 403 na conversa vira 'Algo deu errado'. O requisito pede todos os estados. |
| UI-04 | Lista de mensagens performática com milhares de itens (virtualização ou message-scroller com paginação reversa) | feito; message-scroller, páginas de 50, perf.spec com 2000 msgs (abrir 0,6 s, subir 24 s, maior tarefa 1,15 s, 38 mil nós) | features/chat/api.ts:107 (messages?limit=PAGE, paginação para trás) | components/ui/message-scroller.tsx; conversation-view.tsx:164 MessageScrollerProvider; sem virtualização | perf.spec.ts é opcional (PAPINHO_E2E_PERF) e só afirma openMs<5000 e mensagens>=TOTAL: não falha com tarefas longas nem memória | 5000 mensagens inseridas por SQL (ui-seed.sh: '5000\|5000') e medidas com ui-probe.mjs no Chromium 1194: '{"openMs":496,"scrollToFirstMs":81404,"steps":50,"fps":12,"msgsInDom":5000,"domNodes":90183,"heapMB":885,"longTaskCount":249,"longestTaskMs":1874}', '{"typing18charsMs":666}'; numa corrida parcial, 31 páginas levaram 28,9 s (0,93 s/página) e 50 páginas levaram 81 s (1,6 s/página) | não feita (não é crítico) | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. A abertura é rápida (0,5 s) e a paginação reversa existe. Mas sem virtualização as 5000 ficam no DOM (90 mil nós, 885 MB de heap JS), há tarefas longas de até 1,87 s, e o custo por página cresce com o total, de 0,9 s para 1,6 s por página: 81 s para chegar à primeira. Com milhares de itens, isso não é 'performático'. O perf.spec não tem limite para tarefas longas. |
| UI-05 | Nenhum controle com aparência nativa; e2e com capturas em Chromium/Firefox/WebKit claro/escuro; lint que proíbe select/checkbox/radio/range/date/time/color/progress/controls fora de components/ui | feito; native-controls.spec em 3 motores e 2 temas (68/68/66 capturas); regra de lint + 26 testes; lacuna: celular/Safari real e WebKit sem vídeo | n/a (cabeçalho CSP: 'style-src 'self'' conferido por curl) | frontend/eslint.config.js:12-70 (NATIVE_CONTROLS) e no-alert; src/index.css:88 (color-scheme: light), :96 (.dark color-scheme: dark), :145-176 (scrollbars), :180 (:focus-visible), :185 (::selection), :193 (::placeholder), :207-218 (autofill), :226 (search-cancel), :235-242 (spinners do number); inputs file ocultos em composer.tsx:269, profile-settings.tsx:129, group-details.tsx:131, sticker-manager.tsx:89; nenhum asChild (grep vazio), 22 render= | lint/native-controls.test.ts (26) e e2e native-controls.spec.ts (verifica controles visíveis, [title], scrollbars, color-scheme e console/CSP pelo fixture) | Lint plantado: 'pnpm lint' -> '✖ 21 problems (21 errors)' / 'ELIFECYCLE Command failed with exit code 1'. e2e ref: '✓ 8 [desktop-chromium] native-controls (2.5m)', '✓ 30 [desktop-firefox] ... (2.1m)', '✓ 31 [desktop-webkit] ... (2.6m)', '27 passed (4.0m)' / 'EXIT=0'; capturas: 68/68/66 | (1) Lint: seletor de audio/video desativado -> 'Tests 2 failed \| 24 passed (26)'. (2) Produção: 'controls' no <video> (video-player.tsx:79) + color-scheme light em .dark, JAR reconstruído -> native-controls falhou no Chromium e no Firefox: '+   "controle nativo: <video controls=\"\" src=...' (a mutação do color-scheme não ficou isolada, porque o teste para na 1ª tela). Tudo revertido, git diff vazio. | CUMPRIDO | refutado → STATUS INCORRETO | **STATUS INCORRETO** | Riscos residuais: (a) o lint não pega type={"checkbox"} em expressão, tipo por spread, nem title em componentes com maiúscula (<Button title> gera a dica nativa); o e2e pega [title] em runtime só nas telas visitadas. (b) Celular fora da matriz do UI-05 (skip isMobile). (c) Autofill sem prova automatizada; type=search e as setas do number não entram na verificação em runtime. (d) WebKit sem vídeo. (e) No CI #65 o native-controls ficou flaky no Firefox ('pageerror: The operation was aborted.'), mascarado por retries:1. |
| AR-01 | WCAG 2.2 AA como referência; RESPONSIVENESS.md aplicado | feito: axe em todas as telas com fluxo; a11y.spec.ts passa o axe no tema escuro em 9 telas e com papel de parede; muted-foreground 4,5:1 | n/a | frontend/e2e/fixtures.ts:66-104 (axe wcag2a..wcag22aa); frontend/src/index.css:261-274 (alvos coarse), 328-345 (hover só em @media (hover: hover)); contact-list.tsx:135-150 (violação) | a11y.spec.ts:44 (axe escuro em 9 rotas fixas, sem a aba Contatos), :181 (aparência). Nenhum spec roda o axe na aba Contatos com contato | pnpm e2e a11y.spec.ts (desktop+mobile): '10 passed, 3 skipped'. Spec temporário de auditoria, 18 telas/modais x claro/escuro x 2 projetos: todas sem critical/serious, exceto a aba Contatos: 'serious label-content-name-mismatch x1: button[aria-label="Conversar com Hana Audit"]' (claro e escuro, desktop e celular). Varredura de Tab: div[role=tabpanel] tabindex=0 'outline-none' com 'none 0px \| none' (sem indicador de foco, WCAG 2.4.7). CSS compilado: nenhum :hover que revela fora de @media (hover:hover) | veja AR-03 (o axe falha quando se tira o aria-label: button-name) | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. Violação serious do axe (WCAG 2.5.3 Label in Name, nível A) no botão 'Conversar com' da aba Contatos; tabpanel focável sem foco visível (2.4.7); alvos de toque abaixo dos 44 px que o RESPONSIVENESS pede (ver AR-02); sem safe-area left/right. Leitor de tela real não testado |
| AR-02 | 320 px sem rolagem horizontal; alvos de 44 px; safe areas; teclado virtual não esconde o campo | bloqueado: e2e 320 px; alvos de 44 px em pointer: coarse (index.css); viewport-fit=cover, h-dvh, env(safe-area-inset-*); bloqueado só no que exige aparelho real | n/a | index.html:7 (viewport-fit=cover, interactive-widget=resizes-content); app-shell.tsx:151 (h-dvh); composer.tsx:220 e page-header.tsx:21 (safe-area top/bottom); index.css:261-274 (min 2.75rem só para [data-slot=button\|input\|...]); app-shell.tsx:184-210 (TooltipTrigger render=<Button> troca o data-slot) | a11y.spec.ts:60 (320 px) só mede documentElement.scrollWidth e não vê contêiner interno rolando; não há teste de alvo de toque nem de safe area | Pixel 7: matchMedia coarse=true. Área efetiva por elementFromPoint: '"Menu" caixa 32x32 efetiva 32x32', '"Ações da mensagem" caixa 28x28 efetiva 28x28', '"Voltar" 32x32', '"Emojis" 32x32', '"Perfil" caixa 54x28' (navegação de configurações), Fechar de diálogo 28x28. 320 px: doc=0 em todas as telas, mas /admin: 'MAIN[] 383>320', abas 'Transcrição' right=329 e 'Disco' right=380 (captura 320-admin.png). Texto longo sem espaço a 320: doc 0. Teclado simulado (altura 360): campo de mensagem visível (inViewport true). grep safe-area-inset-left/right: count=0 | não feita: não é crítico | PARCIAL | — | **PARCIAL** | Alvos de 44 px NÃO cumpridos em pointer: coarse (32 px nos botões de ícone com Tooltip/DropdownTrigger, 28 px em ações da mensagem, Fechar e links das configurações). Ao contrário do que o STATUS diz, o 'alvos de 44 px' não está feito. /admin rola na horizontal a 320 px. Não há safe-area left/right (paisagem com notch). Teclado virtual e notch reais: NÃO VERIFICÁVEL sem aparelho |
| AR-03 | Fluxo completo só com teclado; axe sem violações críticas ou sérias | feito: a11y.spec.ts 'fluxo completo só com o teclado' (Tab/Enter, Ctrl+K, Enter envia, menu com setas e Esc devolvendo o foco, foco visível); axe sem críticas/sérias em todos os specs | n/a | app-shell.tsx:113-130 (Ctrl+K); componentes Base UI (Dialog/Menu com trap e devolução de foco); contact-list.tsx:135-150 (violação do axe) | a11y.spec.ts:101 usa field.focus() e actions.focus() programáticos: não prova que o campo de mensagem e o botão de ações se alcançam por Tab; foco visível checado em 1 elemento só | Spec temporário só com Tab de verdade: cadastro por convite (Tab/Enter) ok; Adicionar contato ok; aba Contatos com seta+Enter; 'até campo Mensagem ok tabs 7'; 'mensagem enviada sent:true'; 'Ações da mensagem' por Shift+Tab, Enter abre, Esc fecha e o foco volta; diálogo 'Meu link' com 14 Tabs sempre 'DENTRO'; menu principal com Esc 'menuHidden:true', foco em 'Menu'. Axe: 'serious label-content-name-mismatch' na aba Contatos | (1) '*:focus-visible{outline:none;box-shadow:none}' em index.css: teste FALHOU ('Expected: true Received: false'). (2) Sem aria-label em 'Adicionar contato': axe escuro FALHOU ('button-name'). (3) tabIndex=-1 no @usuario: FALHOU (toBeFocused). (4) tabIndex=-1 no textarea do composer: o teste PASSOU ('1 passed'), ou seja, o teste não prova o fluxo. Tudo desfeito, git diff vazio | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. O axe acha violação serious (label-content-name-mismatch) na aba Contatos, o que contradiz 'sem críticas/sérias'. O fluxo por teclado funciona na prática (provado nesta sessão), mas o teste do projeto é frágil: focus() programático no composer e no menu, o que a mutação 4 mostrou. Foco vai para <body> ao abrir a conversa; tabpanel sem foco visível |
| IN-01 | Textos da interface externalizados; pt-BR completo; estrutura pronta para en | feito: i18n.test.ts com as mesmas chaves; varredura sem texto fixo; 747 chaves literais existem; backend com messages*.properties; restam fixos nomes de idioma, teclas e o selo 'admin' | backend/src/main/java/io/github/labdev2025cmd/papinho/shared/error/GlobalExceptionHandler.java:41-113 (title/detail de messageSource); messages.properties, messages_en.properties, messages_pt_BR.properties (175 chaves cada) | frontend/src/lib/i18n.ts:1-37; src/locales/pt-BR.json e en.json; appearance-settings.tsx:23-81 (troca de idioma); api-client.ts:56 (Accept-Language) | src/lib/i18n.test.ts compara só os conjuntos de chaves; não há regra de lint contra literais nem teste de paridade dos messages*.properties no backend | in01-keys.mjs: 'pt-BR keys 927 en keys 927', 'only pt []', 'only en []', 'plural keys 40', 'interpolation mismatch []', 'literal keys used 771 missing in pt []'; en sem acento do pt (0). Diff dos 3 .properties vazio ('IGUAIS'); os 60 slugs de ApiException têm title e detail. curl: en 'Incorrect username or password.' / pt 'Usuário ou senha incorretos.', 401/400/403 application/problem+json. Grep de JSX: só 'admin-users.tsx:89 <Badge>admin</Badge>' e 'recover-form.tsx:69 placeholder="xxxxx-xxxxx"' | Removi chat.startConversation do en.json: i18n.test FALHOU. Removi invite.hours_one do en: FALHOU. Texto fixo no JSX de contact-list.tsx: vitest 86/86, lint 0, typecheck 0, nada falhou. Removi a chave dos dois idiomas: 86/86, nada falhou. Tudo desfeito | STATUS INCORRETO | — | **STATUS INCORRETO** | Veredito real: PARCIAL. No estado atual a externalização está quase completa (provada pela minha varredura), mas a metade 'nenhum texto fora do i18next' não tem guarda: as mutações de texto fixo e de chave apagada nos dois idiomas passam em tudo. Mensagens de validação vêm do bundle do Hibernate Validator, não de messages*.properties. Fixos: selo 'admin', placeholder 'xxxxx-xxxxx'. Backend sem teste de paridade de chaves |
| PK-01 | pki init com BouncyCastle: CA P-256 de 10 anos com Name Constraints críticas, servidor de no máximo 398 dias com SAN DNS e IP, chave 0600 fora do repositório | feito; CertificateAuthorityTest (7), PkiCommandTest (5); openssl verify/certutil recusam domínio e IP de fora | backend/src/main/java/io/github/labdev2025cmd/papinho/pki/internal/CertificateAuthority.java:54-56,87-96,123-126; PkiStore.java:51,164-177; PkiCommand.java:97-129,198 | n/a | CertificateAuthorityTest afirma curva, 3650 dias, NC críticas, SAN, <=398 dias e openssl verify com 'permitted subtree violation'; PkiCommandTest afirma rw------- na chave e rwx------ no diretório | pki init em diretório temporário. openssl x509: 'NIST CURVE: P-256', 'Not After : Oct 14 00:36:46 2036', 'X509v3 Name Constraints: critical / Permitted: DNS:papinho.lan IP:192.168.10.0/255.255.255.0'. Servidor: 'Not After : Nov 18 00:36:46 2027' (397 d), 'DNS:papinho.lan, IP Address:192.168.10.20'. stat: '600 ca.key.pem', '600 server.key.pem', '700 pki'. Certificados forjados com a chave da CA para evil.example.com, IP 10.0.0.5 e papinho.lan+google.com: 'error 47 at 0 depth lookup: permitted subtree violation'. | Removi addExtension(nameConstraints) (CertificateAuthority.java:96) e troquei OWNER_ONLY_FILE para rw-r--r-- (PkiStore.java:51). Resultado: 'Tests run: 12, Failures: 4' (shouldConstrainCaToHostnameAndNetwork:59, shouldRejectCertificateForOtherDomain:98, shouldRejectCertificateForIpOutsideNetwork:106, shouldCreateFilesWithOwnerOnlyKeys:49). Mutação revertida. | CUMPRIDO | mantido | **CUMPRIDO** | As NC também permitem subdomínios (www.papinho.lan passou no openssl), o que é aceitável. Com locale POSIX, os acentos da saída do CLI viram '?'. Os testes com openssl são pulados se ele não estiver instalado. |
| PK-02 | TLS por SSL bundle com HTTP/2; na porta HTTP só /setup/** e o redirecionamento para HTTPS | feito; LanSetupIT (HTTP/2, 301, POST recusado) e curl --http2 manual | backend/src/main/resources/application-lan.yaml:6-17 (ssl.bundle papinho-lan); application.yaml:56-57 (http2.enabled); pki/HttpPortConfig.java:33-83 | n/a | LanSetupIT.shouldServeTlsWithHttp2 (version HTTP_2), shouldRedirectHttpToHttps (301 + Location), shouldRejectWritesOnSetup | Instância lan do jar de referência. curl --http2 --cacert: 'ALPN: server accepted h2', 'SSL certificate verify ok.', 'HTTP/2 200'. Porta HTTP: /api/v1/instance, /api/v1/conversations, /, /ws, /ws/info, /login e /actuator/health dão '301 https://papinho.lan:18221/...'. Upgrade WebSocket em /ws: 'HTTP/1.1 301'. '/setup/../api' e '%2e%2e' dão 400. /setup, ca.crt, ca.pem e install-ca.sh dão 200. POST /setup/ca.crt dá 403. | HttpPortConfig.java:67 trocado por 'if (true \|\| ...)'. LanSetupIT: 'Failures: 1' em shouldRedirectHttpToHttps:113 'expected: 301'. Mutação revertida. | CUMPRIDO | mantido | **CUMPRIDO** | '/setup/' com barra final dá 404 (detalhe). Não fiz mutação do http2.enabled; o h2 foi provado pelo curl real. |
| PK-03 | /setup com .crt e .pem, SHA-256 visível, QR Code e instruções por SO e navegador | feito; LanSetupIT e e2e setup.spec.ts (axe, CSP, 320 px) | pki/SetupController.java:37-92; SetupService.java:157-163 (QR com zxing); resources/pki/setup.html:25,90-92; resources/pki/setup.js:4-37 | n/a (página servida pelo backend) | LanSetupIT.shouldServeSetupPageOverHttp, shouldDownloadCaInDerAndPem; e2e setup.spec.ts confere #fingerprint, downloads, CSP, UI-05 e 320 px | Pela porta HTTP, /setup mostra 'BA:ED:86:...:60:28:83', igual ao 'openssl x509 -fingerprint -sha256' do ca.crt (DER) e do ca.pem baixados. A página tem '<svg ... aria-label="QR Code da página de instalação">', data-os linux/windows/macos/ios/android, browser-firefox e 'Ajustes de Confiança de Certificado'. papinho-ca.mobileconfig: '200 application/x-apple-aspen-config'. e2e setup.spec.ts contra a instância lan: '4 passed (6.9s)'. | não feita: requisito não crítico | CUMPRIDO | mantido | **CUMPRIDO** | As instruções não foram testadas em aparelhos reais (iOS, Android, Windows, macOS). |
| PK-04 | install-ca.sh confere o SHA-256 da CA antes de instalar, detecta a distro, cobre NSS, é idempotente e desinstala | feito; verify-install-ca.sh passou em Debian, Fedora e Arch; impressão digital errada aborta sem instalar | backend/src/main/resources/pki/install-ca.sh:46-56 (distro), 58-91 (NSS), 93-130, 161-168 (checagem), 183-186 (instalação de "$PEM") | n/a | LanSetupIT.shouldServeInstallScriptWithFingerprint só confere que o texto contém EXPECTED_FINGERPRINT, CA_URL e --uninstall. scripts/verify-install-ca.sh testa instalar, reinstalar, TLS e desinstalar, mas NÃO tem caso de impressão digital errada. | Debian em contêiner: 'TLS pelo sistema: 200'. Reinstalar mantém o bundle em 151→151 e uma entrada NSS em nssdb e no Firefox. --uninstall: 0 arquivos e 0 entradas. CA trocada: 'Erro: a impressão digital não confere! ... Nada foi instalado.' exit=1. PEM com a CA legítima seguida de '-----BEGIN TRUSTED CERTIFICATE-----' de outra CA: 'Impressão digital SHA-256 conferida: baed86...', '/etc/ssl/certs/ca-certificates.crt:2' blocos TRUSTED, e o curl do sistema para https://banco.example.com:18229 com a folha do atacante deu '200' (antes: 'curl: (60)'). verify-install-ca.sh sem o Debian: Fedora 42 e Arch 'TLS validado pelo sistema: ok', 'desinstalação: ok'. | Troquei a checagem da linha 166 por 'if false; then'. LanSetupIT: 'Tests run: 7, Failures: 0', BUILD SUCCESS. A mutação SOBREVIVEU, e nenhum teste automatizado prova a recusa. Mutação revertida. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL. (1) A conferência pode ser contornada: o script verifica só o bloco BEGIN CERTIFICATE extraído, mas instala o arquivo PEM inteiro, e um bloco TRUSTED CERTIFICATE anexado passa a ser confiável no sistema, com uma CA SEM Name Constraints. (2) Nenhum teste falha se a checagem da impressão digital for removida. (3) O Debian do verify-install-ca.sh original depende de apt sem proxy; rodei o equivalente manual. Idempotência, NSS e desinstalação estão OK. |
| PK-05 | Documentação de hostname (mDNS/Avahi, DNS do roteador, ressalvas; passkeys exigem nome) | feito; README seção 'Hostname na rede' | PkiCommand.java:201-208 (recusa IP como hostname); application-lan.yaml rp-id | n/a | PkiCommandTest.shouldRejectIpAsHostname | README.md:199-210 lido: DNS do roteador, mDNS/Avahi com .local, /etc/hosts, ressalvas e 'passkeys exigem um nome, porque o WebAuthn não aceita IP como RP ID'. PkiCommandTest rodou com shouldRejectIpAsHostname verde (o único que falhou foi o da mutação de permissão). | não feita: requisito de documentação | CUMPRIDO | mantido | **CUMPRIDO** | O procedimento mDNS não foi testado numa rede real. |
| PK-06 | Em produção a PKI fica desligada | feito; SecurityIT.shouldNotExposeSetupWhenPkiDisabled | application.yaml:103-104 (app.pki.enabled: false); @ConditionalOnBooleanProperty("app.pki.enabled") em SetupController.java:22, SetupService.java:38, HttpPortConfig.java:29 | n/a | SecurityIT.shouldNotExposeSetupWhenPkiDisabled (MockMvc 404 em /setup e /setup/install-ca.sh) | Jar sem perfil na porta 18222, com uma PKI válida em PAPINHO_PKI_DIR: /setup, /setup/ca.crt, ca.pem, install-ca.sh e qr.svg deram '404 application/problem+json'. Log: 'Tomcat started on port 18222 (http)', sem porta HTTPS. | não feita: requisito não crítico | CUMPRIDO | — | **CUMPRIDO** | Nenhuma relevante. |
| QA-01 | Backend: testes unitários, slice e de integração (Testcontainers), ArchUnit, Modulith, ./mvnw verify | feito; 78 unitários + 135 de integração, ArchitectureTest, ModularityTests | backend/src/test/java/io/github/labdev2025cmd/papinho/ModularityTests.java:11-17; ArchitectureTest.java:20-46; support/IntegrationTest.java (@SpringBootTest + Testcontainers); TestcontainersConfiguration.java:16 | n/a | ModularityTests.verify() e ArchUnit afirmam de verdade (ver mutação). Não há NENHUM teste slice: grep por @WebMvcTest/@DataJdbcTest/@DataJpaTest/@JsonTest/@JdbcTest/@ApplicationModuleTest não acha nada, e todos os ITs sobem o contexto completo. | LanSetupIT (Testcontainers) rodou verde nesta sessão ('Tests run: 7, Failures: 0'). CertificateAuthorityTest e PkiCommandTest rodaram. Suíte completa: linha de base da Fase 0 ('surefire 78 / failsafe 135', exit 0). | SetupMaterial passou a chamar media.internal.ThumbHash e PkiCommand ganhou um campo @Autowired. Resultado: "Module 'pki' depends on non-exposed type ...media.internal.ThumbHash within module 'media'!" e 'ArchitectureTest.noFieldInjection FAILURE'. Mutações revertidas. | STATUS INCORRETO | — | **STATUS INCORRETO** | O veredito real é PARCIAL: faltam os testes slice exigidos pelo QA-01. O STATUS e o nome do passo do CI ('unitários, slice, integração') alegam slice que não existe. Unitários, integração, ArchUnit, Modulith e verify existem e funcionam. |
| QA-02 | Frontend: lint, typecheck e Vitest | feito; lint, typecheck, format, test (86) e build | n/a | frontend/package.json (scripts lint/typecheck/test); frontend/lint/ (regra do UI-05) | 86 Vitest em 15 arquivos | No worktree: 'pnpm install --frozen-lockfile' exit=0; 'pnpm lint' exit=0; 'pnpm typecheck' (tsc -b --noEmit) exit=0; 'pnpm test': 'Test Files 15 passed (15)', 'Tests 86 passed (86)'. | não feita: requisito não crítico | CUMPRIDO | — | **CUMPRIDO** | Não reexecutei build nem format:check (a linha de base mostra exit 0). |
| QA-03 | E2E com Playwright e axe: convite, dois usuários em dois contextos, grupo, upload, apagar para todos, privacidade recíproca, trancada, bloqueio de tela | feito; suíte completa com 79 verdes na passada 8 | n/a | frontend/e2e/accounts.spec.ts:32, chat.spec.ts:35 (newContext x2), groups.spec.ts:27, media.spec.ts:45, messages.spec.ts:28,59-71, privacy.spec.ts:42,154,274; fixtures.ts:60-95 (axe critical/serious) | Todos os specs críticos chamam expectNoSeriousA11yViolations (de 3 a 7 vezes cada) e usam dois contextos de navegador | Jar de referência com banco vazio: accounts, chat, groups, media, messages e privacy em desktop-chromium deram '13 passed (2.2m)'. | Removi 'this.deletedAt = now' em Message.java:254 e empacotei o jar do worktree. Com banco vazio, messages.spec.ts deu '1 failed' em "waiting for getByRole('main').getByText('Você apagou esta mensagem')". Mutação revertida. | CUMPRIDO | mantido | **CUMPRIDO** | Nesta sessão rodei só o desktop-chromium. Na linha de base, view-once.spec.ts falhou 6/6 com o Chromium 1194 local (fora dos 8 fluxos) e o CI esconde um flaky com retries: 1. |
| QA-04 | CI no GitHub Actions com backend, frontend e e2e em jobs separados | feito; run 38949437219 e anteriores verdes nos três jobs | .github/workflows/ci.yml:17-30 (backend: ./mvnw -B verify), 40-60 (frontend), 64-139 (e2e: padrão + lan + Mailpit) | n/a | n/a | 'gh run view 38949437219': '✓ Frontend (lint, typecheck, Vitest, build)', '✓ Backend (./mvnw verify)', '✓ E2E (Playwright + axe)'. Também há runs vermelhos reais: 38842738988 ('X E2E') e 38836857890 ('X Frontend'), então os jobs falham quando há erro. | não feita: exigiria fazer push de um commit quebrado no remoto. A falha real dos jobs aparece no histórico (runs 38842738988 e 38836857890), e a falha dos testes do backend sob mutação foi provada localmente com o mesmo ./mvnw. | CUMPRIDO | mantido | **CUMPRIDO** | O e2e não depende do job backend (needs: [frontend]). 'retries: 1' esconde testes flaky. A falha ao vivo não foi provada por push. |
| QA-05 | README em pt-BR: desenvolvimento, LAN, instalar a CA, backup e produção | feito; README com as seções e os comandos executados | README.md:27 (Desenvolvimento), 43 (Testes), 165 (Subir na rede local), 199 (Hostname), 212 (Instalar a CA), 228 (Produção), 243 (Backup) | n/a | n/a | Rodei os passos de LAN do README (pki init, --spring.profiles.active=lan, /setup, install-ca.sh) e eles funcionaram. Rodei também o modo de produção sem perfil (/setup 404). | não feita: documentação | PARCIAL | — | **PARCIAL** | Os 5 tópicos existem. Mas README.md:220 garante que o script instala 'conferindo a impressão digital antes', o que pode ser contornado (PK-04). Não reexecutei os comandos de backup/restauração. |

## 4. Obrigações sem ID (§0–§6, §10, §11)

| Obrigação | Resumo | Evidência executada | Mutação | Veredito final | Lacunas |
|---|---|---|---|---|---|
| §0 parâmetros | groupId/pacote, hostname papinho.lan, pt-BR, fuso America/Fortaleza com persistência em UTC, branch main | 'git ls-remote origin': 'refs/heads/main' = 3f4cb6d. psql information_schema: 73 colunas 'timestamp with time zone' e 3 'without' (flyway_schema_history.installed_on, user_credentials.created, user_credentials.last_used). 'show timezone' = UTC. | não feita: não aplicável | **PARCIAL** | O groupId é razoável (o dono no GitHub é labdev2025-cmd, e hífen não vale em pacote Java). Porém user_credentials.created e last_used são timestamp sem fuso e a JVM não força UTC (não há TimeZone.setDefault): numa máquina em America/Fortaleza essas datas são gravadas em hora local, o que fere a regra 'persistência sempre em UTC'. |
| §1 missão (tecnologias maduras/estáveis) | Só versões estáveis; dúvidas registradas em ADR | 'mvn dependency:list' (233 linhas) filtrado por -M/-RC/beta/alpha/preview/SNAPSHOT: só o próprio papinho-frontend SNAPSHOT. Varredura do package.json: [["onnxruntime-web","1.31.0-dev.20260914-8d85527a0"]]. | não feita: não aplicável | **PARCIAL** | Uma dependência direta é um build dev/nightly do onnxruntime-web (é a mesma que @huggingface/transformers 4.3.1 fixa) e não há ADR registrando o motivo. Todo o resto é estável. |
| §2 documentos e precedência | Guias na raiz, nunca movidos | 'ls *.md' mostra os 6 guias na raiz e 'ls docs/guides' dá 'No such file'. No histórico completo (API do GitHub), cada guia aparece só como 'added', nunca como renamed ou removed. | não feita: não aplicável | **CUMPRIDO** | Nenhuma. |
| §3 STATUS.md e ciclo | STATUS.md com todos os IDs e os 5 campos; feito só com evidência executada | 88 IDs extraídos do PROMPT.md e 88 do STATUS.md, com 'comm -3' vazio e sem duplicatas. Todas as linhas têm 5 células e a evidência preenchida. Status: {"feito":87,"bloqueado":1}. | não feita: não aplicável | **PARCIAL** | A forma está completa, mas a regra 'feito exige evidência que sustente' não vale para tudo: nesta área PK-04 e QA-01 estão marcados como feito sem se sustentar. |
| §4 Git | Conventional Commits em pt-BR pelo COMMITS.md, commits atômicos, nada proibido versionado, push em main | Histórico completo de 226 commits pela API do GitHub (o clone local é shallow, com 50). Os 173 commits do Claude seguem o padrão; os 2 fora ('Commit inicial de criação do projeto', 'ajustado PROMPT.md') são do dono. Contagem Unicode: 52 assuntos passam de 72 caracteres, todos do Claude (por exemplo, 105: 'docs: registra o ADR 0014, a lista, busca, notificações e offline no README e os aprendizados da Fase 6'). Arquivos de todos os commits: 729 caminhos, nenhum casa com node_modules/target/dist/.env/.pem/.key/.crt/.jar/.log/data. git ls-files (723) está limpo e não há 'BEGIN PRIVATE KEY' versionado. | não feita: não aplicável | **PARCIAL** | 52 de 173 commits do agente violam o teto rígido de 72 caracteres do COMMITS.md:93. O uso de --force e de amend em commits já enviados não é verificável pelo repositório. |
| §5 ambiente | PostgreSQL 18 no compose.yaml e Testcontainers nos testes; não fingir verificações | LanSetupIT rodou com Testcontainers (7/7). compose.yaml lido: 'postgres:18-alpine'. | não feita: não aplicável | **CUMPRIDO** | Não subi o compose.yaml em si (o perfil local); usei um contêiner manual da mesma imagem. |
| §6 ADRs 0001-0010, stack do frontend, chat do shadcn, módulos Modulith | ADRs obrigatórios em MADR pt-BR batendo com o código; stack do frontend; componentes oficiais de chat; módulos listados | 'ls docs/adr' mostra 0001-0015, e os 10 primeiros têm status e as 4 seções. 'grep implements FileStorage' acha só FilesystemFileStorage, e não há S3/minio/awssdk no pom nem no código. ADR 0005:17 lista 'conversation', 'message', mas o código tem um único 'messaging'. A contagem de imports de 'components/ui/attachment' dá 0. O jar tem BOOT-INF/lib/papinho-frontend-0.1.0-SNAPSHOT.jar com META-INF/resources/index.html e sw.js (D2). Há uma pasta Flyway por módulo em db/migration/<módulo>. | não feita: não aplicável (a mutação de módulos foi feita no QA-01) | **PARCIAL** | (1) O ADR 0006 manda ter S3-compatível em produção, mas não existe implementação S3. (2) O ADR 0005 e o PROMPT §6 listam os módulos 'conversation' e 'message', e o código os juntou em 'messaging' sem ADR: o ADR não bate com o código. (3) O componente oficial 'attachment' do shadcn não é usado. A stack do frontend, os ADRs 0001-0004 e 0007-0010 (amostrados), o Flyway por módulo e o layout D2 estão OK. |
| §10 fases (AGENTS.md menciona prompt.md e STATUS.md) | AGENTS.md menciona PROMPT.md e STATUS.md como leitura obrigatória; ordem das fases | AGENTS.md lido (carregado pelo CLAUDE.md): os itens 1 e 2 da lista de leitura obrigatória são PROMPT.md e STATUS.md. CLAUDE.md e LEARNING.md existem. | não feita: não aplicável | **CUMPRIDO** | Pelo repositório não dá para verificar se a ordem das fases foi seguida. |
| §11 relatório | Relatório de fim de passada com IDs, correções, verificações, bloqueados, próximos passos, hash e linha LEARNING | Li o cabeçalho do STATUS.md: há o resumo das passadas 1-8. O relatório em si é saída de chat e não está no repositório. | não feita: não aplicável | **NÃO VERIFICÁVEL** | Faltariam as transcrições das sessões para conferir os 7 itens do relatório. |

O §11 (relatório de fim de passada) só se confere nas respostas das sessões anteriores, que não estão no repositório. As mensagens de commit e o `STATUS.md` registram hashes e verificações, mas isso não prova o relatório em 7 itens.


## 5. Achados

Os achados estão numerados por severidade. "Verificação" diz o que o verificador adversarial fez com o achado. Duplicatas encontradas por dois agentes foram fundidas e anotadas como reproduzidas de forma independente.

### A01. [Alta] Prévia de link sem limite de tempo total: gotejamento prende a requisição e esgota o pool do banco (DoS por um usuário comum)
- **IDs:** MD-04 · **Área:** 05-MD-AU (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/linkpreview/internal/SafeFetcher.java:126-130; backend/src/main/java/io/github/labdev2025cmd/papinho/linkpreview/LinkPreviewService.java:91-105`
- **Reproduzir:** Site público que responde 200 text/html e envia 1 byte a cada 3 s. Como usuário comum, disparar 10 GET /api/v1/link-previews?url=<site>?n=i em paralelo; depois qualquer outro usuário faz GET /api/v1/conversations
- **Evidência:** prévia única: '[HTTP 200 120.043053s]' 'SLOW finished full 120s'; com 10 em paralelo, a bia: '[HTTP 500 90.058641s]' e no log 'HikariPool-1 - Connection is not available, request timed out after 30002ms (total=10, active=10, idle=0, waiting=3)'
- **Correção sugerida:** Prazo total por busca (por exemplo, 10 s de relógio, abortando o HttpGet) e corpo lido com deadline; tirar o fetch de dentro do @Transactional (ler o cache numa transação curta, buscar fora e salvar noutra); limitar as prévias simultâneas por usuário e no total; teste com servidor que goteja.
- **Verificação:** Verificador: confirmado (Alta→Alta). Reproduzido de forma independente com um servidor local que envia 1 byte a cada 3 s por 120 s (porta liberada por APP_LINK_PREVIEW_TEST_ALLOWED_PORTS; o código do fetch é o mesmo da produção). A ana disparou 10 GET /api/v1/link-previews em paralelo; no t=15 s a bia fez 'bia conversations [HTTP 500 90.044501s]' (página HTML do Tomcat, não Problem Details); health '[HTTP 200 15.171320s]'; log '17' ocorrências de 'HikariPool-1 - Connection is not available, request timed out after 30001ms (total=10, active=10, idle=0, waiting=3)'. As prévias terminaram em 120, 180 e 240 s: setMaxConnPerRoute(4) (

### A02. [Alta] Presença escondida vaza pelo STOMP (hora de cada entrada e saída)
- **IDs:** PV-04, PV-05 · **Área:** 07-PV (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/presence/PresenceService.java:130-150`
- **Reproduzir:** Bruno com online=NOBODY e lastSeen=NOBODY. A Ana fica conectada em /user/queue/events. O Bruno conecta e manda /app/presence active=true, depois active=false.
- **Evidência:** Nas duas transições a Ana recebe {"type":"presence","userId":"...","online":false,"lastSeenAt":null}. Com lastSeen=EVERYONE, ao ficar online chega lastSeenAt:null enquanto o REST mostra "2026-10-16T22:56:51Z".
- **Correção sugerida:** Em refresh(), mandar o evento só a quem pode ver alguma coisa e só quando a visão filtrada mudar; ao ficar online, mandar lastSeenAt com o último valor conhecido em vez de null. Criar um IT com STOMP para isso.
- **Verificação:** Verificador: confirmado (Alta→Alta). Código: PresenceService.java:130-150. refresh() manda o PresenceEvent a TODOS os mutualPeers a cada transição online/offline, seja qual for a visibilidade, e com changedAt=null ao ficar online. Reproduzido com STOMP real (pv07v-presenca.log). Com o Bruno em online=NOBODY e lastSeen=NOBODY, a Ana recebeu {"type":"presence","userId":"01a14793-4071-...","online":false,"lastSeenAt":null} em 2.5s (active:true), 4.5s (active:false), 6.6s (active:true) e 8.6s (desconectou): um evento por transição, que dá a hora exata de cada entrada e saída. Com online=NOBODY e lastSeen=EVERYONE: o REST mostrava las Reproduzido de forma independente pelo verificador de 09-SG.

### A03. [Alta] Sync por afterSeq perde mensagens com envios concorrentes (lastSeq lido depois da busca)
- **IDs:** RT-01 · **Área:** 08-NT-RT (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:312-314; frontend/src/features/chat/cache.ts:56-58 (também history() :319-329 e cache.ts:21)`
- **Reproduzir:** Instância com rate limit x100. Um thread envia 300 mensagens (POST /messages). Outro faz GET ?afterSeq=<cursor> em laço, atualiza o cursor com max(changeSeq) e, quando hasMore=false, com lastSeq, como faz o cache.ts. No fim compare os ids vistos com os enviados (script rt08.py syncrace).
- **Evidência:** enviadas=300 syncs=694 perdidas_pelo_algoritmo_do_cliente=11 | SALTO: afterSeq=502 veio changeSeq=[] mas lastSeq=503
- **Correção sugerida:** Ler o lastSeq ANTES da consulta e limitar a busca a change_seq <= lastSeq, ou rodar sync e history em REPEATABLE READ. Acrescentar um teste de integração que intercale um envio entre as duas leituras.
- **Verificação:** Verificador: confirmado (Alta→Alta). Reprodução independente na minha instância (porta 18185, RLM=100) com `python3 -I rt.py syncrace <conv> 300`. Saída:
- 'SALTO: afterSeq=202 veio changeSeq=[] mas lastSeq=203' (mais 5 saltos)
- 'enviadas=300 syncs=726 perdidas_pelo_algoritmo_do_cliente=23'
- 'perdidas que existem no servidor: 23'

Causa no código:
- MessageService.java:312, findChangedAfter, e :314, sequences.current, rodam em comandos separados de uma transação readOnly em READ COMMITTED. Não há configuração de isolation em backend/src/main.
- O cliente faz `cursor = Math.max(cursor, page.lastSeq)` (cache.ts:56-58) e o próximo

### A04. [Média] Cadastro simultâneo com o mesmo @usuario devolve 500 e grava o @usuario no log
- **IDs:** ID-01 · **Área:** 01-ID-AD (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/IdentityService.java:109-112 (ensureAvailable seguido de saveAndFlush, sem tratar DataIntegrityViolationException)`
- **Reproduzir:** Pôr a instância em modo OPEN e disparar 6 POST /api/v1/auth/register em paralelo com 'mesmo.nome0' e 'MESMO.NOME0' (script t13.py).
- **Evidência:** mesmo.nome0 [(201, 'ok'), (500, 'urn:papinho:problem:internal') x5]; log: 'Detail: Key (username)=(mesmo.nome0) already exists.' + stack trace
- **Correção sugerida:** Traduzir a violação de uq_user_accounts_username (e de username_reservations) para UsernameUnavailableException (409), tanto no cadastro quanto na troca de @usuario. Não registrar o Detail do PostgreSQL no log.

### A05. [Média] IDs internos em UUID v4, contra 'IDs internos em UUID v7'
- **IDs:** ID-01 · **Área:** 01-ID-AD (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/media/UploadService.java:99; messaging/ScheduledMessageService.java:60; profile/ProfileService.java:93; também StickerService.java:75,100, Polls.java:42, ConversationLocks.java:85, TranscriptionService.java:150`
- **Reproduzir:** Criar um upload TUS e uma mensagem agendada pela API, depois consultar a versão dos UUIDs das PKs (uuidv.sql).
- **Evidência:** NOTICE:  media_uploads.id versoes=4 / NOTICE:  scheduled_messages.id versoes=4 (enquanto user_accounts.id versoes=7)
- **Correção sugerida:** Gerar todos os IDs com um gerador v7 (UuidGenerator VERSION_7 ou um utilitário compartilhado) e trocar UUID.randomUUID() nesses pontos, inclusive nas chaves de arquivo (o ADR 0006 pede v7).

### A06. [Média] No modo fechado, o admin cria convites que o status diz utilizáveis, mas o cadastro os recusa
- **IDs:** ID-07, AD-01 · **Área:** 01-ID-AD (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/IdentityService.java:98-100 contra InviteService.java:99-110; frontend/src/routes/register.tsx:65`
- **Reproduzir:** Pôr o modo CLOSED pelo PUT /api/v1/admin/settings. Como admin, chamar POST /api/v1/invites. Consultar GET /api/v1/invites/status?token=... e depois POST /api/v1/auth/register com o inviteToken.
- **Evidência:** CLOSED admin cria convite 201 / CLOSED status do convite do admin {'usable': True} / CLOSED com convite do admin 403 urn:papinho:problem:registration-closed
- **Correção sugerida:** Decidir a regra. Se o convite do admin deve reabrir a porta, aceitar o convite no modo CLOSED quando o criador é admin. Se não deve, recusar a criação no modo CLOSED (ou status usable:false) e ajustar o JavaDoc e a tela de cadastro.

### A07. [Média] Três regras críticas sem teste de regressão: as mutações sobrevivem
- **IDs:** ID-01, ID-07, ID-08, AD-02 · **Área:** 01-ID-AD (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/support/DatabaseCleaner.java:20; IdentityIT.java:79-93`
- **Reproduzir:** No worktree, aplicar cada mutação e rodar IdentityIT. (a) Remover a linha 'CONSTRAINT uq_user_accounts_username UNIQUE (username)'. (b) Trocar VALUES (1, 'INVITE_ONLY' por 'OPEN' na migration de instance_settings. (c) Trocar 'return users.count() == 0;' por 'return true;' depois do pg_advisory_xact_lock.
- **Evidência:** (a) e (c): 'Tests run: 24, Failures: 0, Errors: 0' / BUILD SUCCESS; (b): só falharam shouldRejectRevokedOrExhaustedInvite e shouldApplyRegistrationModes, por causa das outras mutações do mesmo lote, e shouldRequireInviteAfterFirstUser passou
- **Correção sugerida:** (a) Teste que insere um @usuario duplicado direto no banco, ou cadastro concorrente que espera 409. (b) Teste que lê o padrão de instance_settings sem passar pelo DatabaseCleaner, ou o DatabaseCleaner restaura a partir da migration. (c) Teste com dois cadastros reais em disputa na instância vazia, esperando exatamente um admin.

### A08. [Média] Presença e leitura antes de aceitar a solicitação não têm teste; mutações sobrevivem
- **IDs:** CT-02 · **Área:** 02-CT-BK (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/ConversationQueries.java:99; ConversationService.java:398; MessageService.java:501-503`
- **Reproduzir:** Troque o filtro de mutualPeers por `me.state in ('ACTIVE','REQUEST') and peer.state in ('ACTIVE','REQUEST')`, ou tire `member.state() == MemberState.ACTIVE` de publishReceipts e de info(); rode it.test=RealtimeIT,PrivacyIT,MessagingIT,PresenceIT
- **Evidência:** M1: 'Tests run: 31, Failures: 0'; M3+M4: 'Tests run: 23, Failures: 0'. Jar mutante: ana recebe {"type":"presence",...,"online":true} e {"readSeq":1}
- **Correção sugerida:** Em RealtimeIT, criar um caso com conversa REQUEST (sem o destinatário adicionar o remetente): GET /presence 404, nenhum evento de presença e receipts com readSeq 0. Em MessagingIT, readAt nulo em /messages/{id}/info antes de aceitar. Corrigir a evidência do STATUS.
- **Verificação:** Verificador: confirmado (Alta→Média). Reproduzi as duas mutações no meu worktree e desfiz.
- M1, mutualPeers com me.state/peer.state in ('ACTIVE','REQUEST') em ConversationQueries.java:98: RealtimeIT 6/0, PresenceIT 1/0, MessagingIT 10/0, PrivacyIT 7/0, 'Tests run: 24, Failures: 0', exit 0.
- M3+M4, sem a checagem de ACTIVE em ConversationService.java:398 (publishReceipts) e em MessageService.java:501 (info readAt): 'Tests run: 24, Failures: 0', exit 0.
- Já a mutação de peerReadSeq na view (ConversationService.java:523) morre em MessagingIT.shouldHandleMessageRequests:126. Ou seja, só o caminho REST da conversa está coberto; o ST

### A09. [Média] Bloqueado não ver presença não tem teste; mutação sobrevive
- **IDs:** CT-03 · **Área:** 02-CT-BK (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/ConversationService.java:431-433`
- **Reproduzir:** Tire o `.filter(peer -> !contacts.isBlockedEitherWay(userId, peer))` de mutualPeers e rode GroupIT,RealtimeIT,PrivacyIT,MessagingIT,ContactIT,PresenceIT
- **Evidência:** Só falhou 'GroupIT.shouldRespectBlocksAndProtectPhoto:141' (da mutação M7 aplicada junto); nenhum teste de presença falhou. Jar mutante: bloqueada recebe presence online/offline e GET /presence 200 com lastSeenAt
- **Correção sugerida:** Teste em RealtimeIT: par aceito, um bloqueia, o outro não recebe presença por STOMP e recebe 404 no REST.
- **Verificação:** Verificador: confirmado (Alta→Média): falta de teste, a regra de produção funciona hoje.

### A10. [Média] Denúncia leva menos de 5 mensagens quando há avisos do sistema ou apagadas entre as últimas
- **IDs:** CT-04 · **Área:** 02-CT-BK (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:522-526`
- **Reproduzir:** Conversa com m1..m9, ligar e desligar o timer, enviar m10 e apagá-la para todos, enviar m11, e então POST /api/v1/reports
- **Evidência:** {"reason":"de novo","n":2,"bodies":["m9","m11"]}; psql: 'de novo | 2 | m9,m11'
- **Correção sugerida:** Filtrar deleted e SYSTEM na consulta (findBefore só de mensagens de usuário não apagadas) antes de limitar a 5, e cobrir o caso no ModerationIT.

### A11. [Média] Salto 'Ir para a mensagem respondida' deixa a original fora da tela quando ela está longe no histórico
- **IDs:** MS-04 · **Área:** 03-MS-a (área)
- **Local:** `frontend/src/features/chat/components/conversation-view.tsx:332-352`
- **Reproduzir:** Instância com ana/bia; ana envia 'ORIGINAL-ALVO-DO-SALTO' e mais 60 mensagens; bia responde à original (replyToId). A ana abre /c/<conv> no Chromium em 1280x720 e clica em 'Ir para a mensagem respondida' (script ms-a/jump.mjs).
- **Evidência:** antes do salto: não renderizada
depois do salto +3000ms: fora da tela ORIGINAL-ALVO-DO-SALTO@-449 enchimento 0@-387 enchimento 1@-324
depois do salto +6000ms: fora da tela ORIGINAL-ALVO-DO-SALTO@-449
- **Correção sugerida:** Depois de scrollToMessage, quando a lista virtual medir as alturas reais, repetir o alinhamento (por exemplo, scroll sem 'smooth' ou nova chamada no fim da rolagem) até o item estar visível. No e2e, conferir que a original fica dentro da área visível (toBeInViewport), não só que o botão existe.

### A12. [Média] Nenhum teste protege a sanitização do render: injetar HTML do usuário passa em toda a suíte
- **IDs:** MS-01 · **Área:** 03-MS-a (área)
- **Local:** `frontend/src/features/chat/format/message-text.tsx:65-66`
- **Reproduzir:** Trocar `case "text": return node.value` por `return <span dangerouslySetInnerHTML={{ __html: node.value }} />` e rodar `pnpm test` e `pnpm exec eslint` no arquivo.
- **Evidência:** Tests  86 passed (86)
lint_exit=0
- **Correção sugerida:** Criar um teste de render (Vitest + Testing Library) do MessageText com payloads `<img onerror>`, `<script>`, `javascript:` e `<svg onload>` que confira que nenhum elemento é criado. Opcional: ligar react/no-danger no ESLint.

### A13. [Média] URL de mensagem apagada para todos permanece no cache link_previews
- **IDs:** MS-06 · **Área:** 03-MS-a (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/linkpreview/LinkPreviewService.java:92 (preview grava por URL; nenhum delete/purge); messaging/MessageService.java:555-574 (deleteForEveryone não toca nas prévias)`
- **Reproduzir:** 1) A ana envia 'olha https://link-secreto-da-ana.invalid/caminho-privado-123' para a bia. 2) O cliente (aqui a bia) chama GET /api/v1/link-previews?url=<URL>. 3) A ana faz DELETE /api/v1/messages/<id>. 4) select url, available, fetched_at from link_previews where url like '%caminho-privado-123%'.
- **Evidência:** {"deleted":true,"body":""} / mensagem: t|t / depois (link_previews com a URL): https://link-secreto-da-ana.invalid/caminho-privado-123|f|2026-10-17 01:43:48.763775+00 / pg_dump --data-only | grep -c caminho-privado-123 -> 1
- **Correção sugerida:** Ao apagar para todos (e no job de temporárias e na exclusão de conta), remover as entradas de link_previews das URLs do corpo que nenhuma outra mensagem não apagada ainda contém, ou dar ao cache um TTL com purge agendado. Cobrir com um IT que confere a tabela depois do delete.

### A14. [Média] Regras de leitura no STOMP, no painel 'Dados da mensagem' e no 'lida' do grupo sem teste que as proteja
- **IDs:** MS-02, PV-05, PV-06, CT-02 · **Área:** 03-MS-a (verificação)
- **Local:** `messaging/ConversationService.java:398-399; messaging/MessageService.java:502-503; messaging/ConversationQueries.java:58`
- **Reproduzir:** No worktree: (mA) trocar ConversationService.java:398-399 por `long readSeq = member.readSeq();` e rodar todas as ITs (verify -Dit.test='*IT'). (mC1) tirar `m.state() == MemberState.ACTIVE &&` de MessageService.java:502. (mC2) tirar `iShare &&` da mesma linha. (mD) trocar ConversationQueries.java:58 por `coalesce(min(o.read_seq), 0)`. Em cada caso, rodar PrivacyIT, Messag-ingIT, GroupIT e ChatListIT.
- **Evidência:** mA: `[INFO] Tests run: 135, Failures: 0, Errors: 0, Skipped: 0`; mC1: `Tests run: 25, Failures: 0`; mC2: `Tests run: 22, Failures: 0`; mD: `Tests run: 27, Failures: 0`. Hoje o produto se comporta certo pela caixa-preta: STOMP `"readSeq":0` com a confirmação desligada; info `"readAt":null` em REQUEST e com a própria desligada.
- **Correção sugerida:** Acrescentar ITs. 1) Recibo pelo STOMP: com quem lê ou quem envia desligado, e com quem lê em REQUEST, o evento 'receipts' traz readSeq 0. 2) GET /messages/{id}/info com readAt nulo quando o destinatário está em REQUEST e quando o próprio autor desligou a confirmação. 3) No grupo, peerReadSeq ignora quem desligou.

### A15. [Média] Token de visualização única reutilizável por 5 minutos (não é de uso único)
- **IDs:** MS-08 · **Área:** 04-MS-b (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/ViewOnceOpens.java:40-47 (messageOfToken só confere a validade); MessageService.java:222-231`
- **Reproduzir:** 1) A bia faz POST /api/v1/messages/{id}/view-once e recebe o token. 2) Faz GET /api/v1/view-once/{token} duas vezes.
- **Evidência:** conteúdo 1ª GET: HTTP/1.1 200 ... Cache-Control: no-store / conteúdo 2ª GET com o mesmo token -> 200
- **Correção sugerida:** Para IMAGE: invalidar o token na primeira GET. Para vídeo e áudio: amarrar o token a uma única resposta em streaming, ou aceitar só Range contínuo. Se o desvio for mantido, registrar no PROMPT como exceção aceita.

### A16. [Média] STATUS cita media.spec.ts como evidência de Ctrl+V e arrastar e soltar, mas nenhum teste os cobre
- **IDs:** MS-13 · **Área:** 04-MS-b (área)
- **Local:** `STATUS.md:182; frontend/e2e/media.spec.ts`
- **Reproduzir:** grep -rn -i 'paste|clipboard|drop|DataTransfer' frontend/e2e
- **Evidência:** o grep não acha nada em e2e/; só o código de produção: composer.tsx:374 onPaste, conversation-view.tsx:652 onDrop
- **Correção sugerida:** Acrescentar ao e2e o ClipboardEvent('paste') com File e o dispatchEvent('drop') com DataTransfer, que funcionaram no spec temporário da auditoria.

### A17. [Média] view-once.spec.ts falha sempre localmente: asserção sem poll logo após o blur da janela
- **IDs:** MS-08 · **Área:** 04-MS-b (área)
- **Local:** `frontend/e2e/view-once.spec.ts:75; frontend/src/features/chat/components/view-once-viewer.tsx:66-69`
- **Reproduzir:** Em instância vazia: e2e.sh 18141 view-once.spec.ts --project=desktop-chromium (Chromium 1194)
- **Evidência:** Expected: "none" Received: "blur(38.6881px)" at view-once.spec.ts:75:26
- **Correção sugerida:** Despachar 'focus' antes e usar expect.poll(filter).toBe('none'). Avaliar também iniciar 'hidden' com !document.hasFocus().

### A18. [Média] Áudio de visualização única pode ter transcrição persistida (o dono transcreve antes de enviar)
- **IDs:** AU-03, MS-08 · **Área:** 05-MD-AU (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:162-165; backend/src/main/java/io/github/labdev2025cmd/papinho/transcription/TranscriptionService.java:101-137`
- **Reproduzir:** ana faz o upload do áudio; POST /api/v1/media/{id}/transcript (provedor) ou PUT com {"text":...}; depois POST /conversations/{c}/messages {attachmentId:id, viewOnce:true}; consultar select * from transcripts where media_id=id
- **Evidência:** 'mensagem view_once com V2: t' e 'transcripts no banco para V2: WHISPER | Oi Bia, chego às oito na Fortaleza'; 'transcripts no banco para V3: BROWSER | segredo falado uma vez'
- **Correção sugerida:** Ao marcar viewOnce, recusar o anexo que já tem transcrição ou apagar a linha de transcripts na mesma transação (porta do módulo de transcrição); teste no ViewOnceIT que transcreve antes de enviar e confere que não sobra nada no banco.

### A19. [Média] Transcrição no servidor carrega o áudio inteiro na memória: áudio de 1 GB com o Whisper próprio derruba o heap (OutOfMemoryError)
- **IDs:** AU-05, AU-03 · **Área:** 05-MD-AU (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/transcription/internal/OpenAiCompatibleClient.java:50-64; backend/src/main/java/io/github/labdev2025cmd/papinho/transcription/ProviderKind.java (WHISPER maxBytes padrão 1073741824)`
- **Reproduzir:** Admin cria o provedor WHISPER (limite padrão de 1 GB) e o define como padrão. A ana envia um WAV de 998400078 B (ffmpeg anullsrc 5200 s) como mensagem para a bia. A bia e a ana fazem POST /api/v1/media/{id}/transcript ao mesmo tempo.
- **Evidência:** 'bia [HTTP 500 3.993358s]' 'ana [HTTP 200 5.474008s]'; 'RSS máximo (KB): 3946288'; 'OOM no log: 2'; 'jakarta.servlet.ServletException: Handler dispatch failed: java.lang.OutOfMemoryError: Java heap space'. O provedor recebeu len 998400496 (MaxHeapSize 3586129920).
- **Correção sugerida:** Enviar o multipart em streaming (BodyPublishers.ofInputStream ou concatenando os publishers) em vez de ByteArrayOutputStream; limitar o padrão do WHISPER (por exemplo, 25-100 MB) e as transcrições simultâneas; teste com áudio grande e heap pequeno.

### A20. [Média] Fotos acima de 50 MB e fotos HEIC/HEIF são guardadas com EXIF/GPS intactos
- **IDs:** MD-02, PV · **Área:** 05-MD-AU (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/media/UploadService.java:49,202-204; backend/src/main/java/io/github/labdev2025cmd/papinho/media/ContentTypes.java:12`
- **Reproduzir:** A ana envia, por tus, um JPEG de 56 MB com GPS no EXIF (Pillow, ruído 3700x3700, qualidade 100) para a bia; a bia faz GET /api/v1/media/{id}/content e roda identify -verbose. Repetir com um arquivo HEIC.
- **Evidência:** '{"k":"DOCUMENT","ct":"image/jpeg","thumb":false}' 'tamanho baixado: 56347981' 'exif:GPSLatitude: 3/1,43/1,5/1' 'exif:Make: CameraAuditoria'. HEIC: '{"kind":"DOCUMENT","contentType":"image/heic"...}' 'byte a byte igual ao original'. O JPEG pequeno sai '(sem GPS/Make)'.
- **Correção sugerida:** Acima do limite de recodificação, tirar os segmentos APP1/APPn sem decodificar (como o WebpMetadata faz no WebP) ou recusar; tratar HEIC/HEIF/AVIF (tirar o EXIF dos boxes ou converter para JPEG); teste de integração com foto grande e com HEIC.

### A21. [Média] Nenhum teste protege a permissão "só admins adicionam membros"
- **IDs:** GR-01 · **Área:** 06-GR (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/GroupService.java:192; backend/src/test/java/io/github/labdev2025cmd/papinho/messaging/GroupIT.java:99-108`
- **Reproduzir:** Troque a condição da linha 192 por `if (false && ...)` e rode GroupIT, PrivacyIT e OutsiderAccessIT com -Dit.test.
- **Evidência:** Tests run: 1, Failures: 0 (OutsiderAccessIT); só falharam GroupIT.shouldJoinByInviteLink e PrivacyIT, por causa das outras mutações aplicadas juntas; shouldManagePermissionsAndMembers passou
- **Correção sugerida:** Em shouldManagePermissionsAndMembers, com onlyAdminsAddMembers=true, faça um não-admin chamar POST /groups/{id}/members com um contato e afirme o 403 urn:papinho:problem:group-only-admins.

### A22. [Média] Limite de 3 conversas fixadas é ultrapassado ao sair e voltar a um grupo fixado
- **IDs:** GR-05 · **Área:** 06-GR (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/ChatListService.java:71-75; GroupService.java:258-266 (leave); ConversationMember.java:106-113 (rejoin)`
- **Reproduzir:** bia fixa o grupo G e mais 2 conversas (a 4ª dá 409). bia faz POST /api/v1/groups/G/leave e fixa uma 4ª conversa (204, porque o G em LEFT não conta). Depois volta ao G por POST /api/v1/groups/invites/<code>/join. GET /api/v1/conversations mostra 4 fixadas.
- **Evidência:** bia pin G: HTTP 204 G1: HTTP 204 G2: HTTP 204 / bia pin G3 (4a): HTTP 409 / bia sai de G: HTTP 204 / bia pin G3: HTTP 204 / bia volta pelo link: {"groupId":"01a14792-...","pending":false} / fixadas da bia na lista: 4 (banco: 4)
- **Correção sugerida:** Limpar pinned_at (e archived_seq, se for o caso) quando o membro sai ou é removido (leave, removeMember, conta apagada), ou zerar no rejoin. Acrescentar um caso ao ChatListIT.

### A23. [Média] Resposta citando mensagem devolve o texto da conversa trancada sem token
- **IDs:** PV-03 · **Área:** 07-PV (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:136,168-174`
- **Reproduzir:** Tranca a conversa. Sem o cabeçalho X-Papinho-Unlock, faz POST /api/v1/conversations/{id}/messages com replyToId apontando para uma mensagem da outra pessoa.
- **Evidência:** 201 ... "replyTo":{..."body":"respostasecreta"...}
- **Correção sugerida:** Usar requireReadable em send, createPoll e vote, ou ao menos quando houver replyToId.
- **Verificação:** Reproduzido de forma independente pelo verificador de 09-SG.

### A24. [Média] Agendadas da conversa trancada saem sem token
- **IDs:** PV-03 · **Área:** 07-PV (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/ScheduledMessageService.java:72-73`
- **Reproduzir:** Agenda 'agendadasecreta', tranca a conversa e faz GET /api/v1/conversations/{id}/scheduled sem token.
- **Evidência:** [{"id":"4b7547bb-...","body":"agendadasecreta",...}] com status 200
- **Correção sugerida:** Trocar requireMember por requireReadable em list e em schedule.
- **Verificação:** Reproduzido de forma independente pelo verificador de 09-SG.

### A25. [Média] Exportação da conta inclui mensagens de conversa trancada sem token
- **IDs:** PV-03 · **Área:** 07-PV (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessagingPersonalData.java (seção 'enviadas'); backup/AccountExportController.java:29`
- **Reproduzir:** Com a conversa trancada, faz GET /api/v1/account/export sem token.
- **Evidência:** 200, e o dados.json contém 'segredoxyz https://exemplo.org/a' e 'agendadasecreta'
- **Correção sugerida:** Exigir o token ou reautenticação, ou omitir o texto das trancadas que o token da requisição não cobre; documentar a decisão numa ADR.

### A26. [Média] Conversa trancada depois de guardada continua no IndexedDB com o texto
- **IDs:** NT-05, PV-03 · **Área:** 08-NT-RT (área)
- **Local:** `frontend/src/features/chat/offline.ts:59-113; frontend/src/lib/offline-db.ts:116 (deleteSnapshot não é chamado em lugar nenhum)`
- **Reproduzir:** Gil e Hana conversam, Gil envia 'Segredo guardado' e espera 2 s. Gil tranca a conversa pelo painel. Leia o store 'snapshots' do banco papinho-<gil>.
- **Evidência:** 'messages:01a14716-9c4e-7592-9ad0-acf0ad3d2776={"messages":[{"id":...' e AUDIT texto 'Segredo guardado' ainda no IndexedDB: true
- **Correção sugerida:** Ao trancar, e quando detail.locked aparecer, chamar deleteSnapshot de messages:<id> e detail:<id> e tirar os itens da fila. Cobrir com e2e.

### A27. [Média] Autorização STOMP (destino, Origin e membro no 'digitando') sem cobertura de teste
- **IDs:** SG-01 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/shared/realtime/WebSocketSecurityConfig.java:27; shared/realtime/WebSocketConfig.java:45; messaging/ConversationService.java:417`
- **Reproduzir:** No worktree: trocar simpSubscribeDestMatchers("/user/queue/**") por "/**", adicionar setAllowedOriginPatterns("*") no addEndpoint("/ws") e tirar requireMember de typingAudience. Depois rodar verify -Dit.test=SecurityIT,RealtimeIT,MessagingIT,OutsiderAccessIT,LanSetupIT.
- **Evidência:** [INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0 (mut-A.log). Na caixa-preta o código atual está correto: '403 handshake cookie, origin evil', 'carol SUBSCRIBE /user/<idAna>/queue/events CONNECTED -> ERROR'.
- **Correção sugerida:** Em RealtimeIT, adicionar testes para: handshake com Origin de outro site (403); SUBSCRIBE em /user/<outro>/queue/**, /queue/** e /topic/** (ERROR); e SEND em /app/activity com conversa alheia sem evento para os membros.

### A28. [Média] Handler de 500 e higiene de logs sem teste: vazamento de stack trace e log de senha em claro não falham a suíte
- **IDs:** SG-01 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/shared/error/GlobalExceptionHandler.java:60-68; identity/IdentityService.java:137-140`
- **Reproduzir:** (1) Trocar o detail do 500 por Arrays.toString(exception.getStackTrace()) e rodar ProblemDetailsIT e SecurityIT. (2) Inserir log.info("Login attempt username={} password={} ip={}", ...) em authenticate e rodar IdentityIT, EmailRecoveryIT e SecurityIT.
- **Evidência:** (1) ProblemDetailsIT 'Tests run: 1, Failures: 0' (mut-B.log). (2) '[INFO] Tests run: 37, Failures: 0, Errors: 0' (mut-G.log). O STATUS diz 'Problem Details em 401/403/400/500 (SecurityIT)', mas o SecurityIT não tem caso de 500.
- **Correção sugerida:** Incluir em ProblemDetailsIT um 500 forçado que confira type urn:papinho:problem:internal e a ausência de nomes de classe no corpo. Incluir um teste com CapturedOutput que faça login, cadastro e envio e confira que o usuário, a senha e o corpo da mensagem não aparecem no log.

### A29. [Média] Erros recusados pelo StrictHttpFirewall e pelo Tomcat fogem do Problem Details e saem sem cabeçalhos de segurança
- **IDs:** SG-01, SG-02 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/shared/security/SecurityConfig.java (nenhum RequestRejectedHandler); shared/error/GlobalExceptionHandler.java`
- **Reproduzir:** curl --path-as-is http://localhost:18190/api//v1/me ; curl --path-as-is 'http://localhost:18190/api/v1/me;jsessionid=x' ; curl -X FOO http://localhost:18190/api/v1/me ; curl --path-as-is 'http://localhost:18190/api/v1/me%00'
- **Evidência:** [firewall //] 400 application/json :: {"timestamp":"2026-10-17T00:50:14.346Z","status":400,"error":"Bad Request","path":"/api//v1/me"} ; [TRACE] 405 application/json :: {..."error":"Method Not Allowed"...} ; [firewall %00] 400 text/html;charset=utf-8 :: <title>HTTP Status 400 – Bad Request</title>. A resposta de '//' não traz Content-Security-Policy nem X-Content-Type-Options.
- **Correção sugerida:** Registrar um RequestRejectedHandler e um ErrorController (ou um ErrorAttributes próprio) que escrevam Problem Details pelo ProblemDetailsWriter, e configurar o ErrorReportValve do Tomcat para não mostrar a página HTML.

### A30. [Média] Várias mutações com .mutate() sem onError e sem MutationCache global: a falha da API fica silenciosa
- **IDs:** UI-03, AGENTS (ação com falha visível), MS-04 · **Área:** 10-UI (área)
- **Local:** `frontend/src/lib/query-client.ts:5-14; frontend/src/features/chat/components/conversation-view.tsx:574,577,970,1115; profile-settings.tsx:119; invite-dialog.tsx:221; admin-reports.tsx:140,147; blocked-settings.tsx:49; contact-list.tsx:227,235; schedule-dialogs.tsx:182; admin-transcription.tsx:63,109,283; group-details.tsx:493,506`
- **Reproduzir:** Login como usuário com conversa; page.route(/\/api\/v1\/messages\/[^/]+\/star$/) -> 409; abrir 'Ações da mensagem' > 'Favoritar' (script ui-silent.mjs)
- **Evidência:** {"requisicoesStar":["PUT"],"avisosVisiveis":[],"textoNaPagina":0}
- **Correção sugerida:** Adicionar onError com toast (errorMessage(error, t('common.genericError')), type 'error') em cada .mutate sem tratamento, ou um MutationCache({ onError }) global no query-client; estender a regra/Vitest para .mutate.

### A31. [Média] Lista sem virtualização: com 5000 mensagens são 90 mil nós, 885 MB de heap, tarefas longas de 1,87 s e custo por página crescente
- **IDs:** UI-04 · **Área:** 10-UI (área)
- **Local:** `frontend/src/features/chat/components/conversation-view.tsx:164; frontend/src/components/ui/message-scroller.tsx`
- **Reproduzir:** bash ui-seed.sh 5000 (SQL) e node ui-probe.mjs perf perfa <senha> <conv>: abre a conversa e rola até a primeira
- **Evidência:** {"openMs":496,"scrollToFirstMs":81404,"steps":50,"msgsInDom":5000,"domNodes":90183,"heapMB":885,"longTaskCount":249,"longestTaskMs":1874}
- **Correção sugerida:** Virtualizar (ex.: @tanstack/react-virtual) ou descartar páginas distantes; dar ao perf.spec limites para a maior tarefa longa e para os nós no DOM.

### A32. [Média] Lista de conversas, conversa e contatos sem 'Tentar de novo' e sem distinguir sem conexão e sem permissão
- **IDs:** UI-03 · **Área:** 10-UI (área)
- **Local:** `frontend/src/features/chat/components/conversation-list.tsx:100-105; conversation-view.tsx:125-158; frontend/src/features/contact/components/contact-list.tsx:94-99`
- **Reproduzir:** node ui-states2.mjs perfb <senha> <conv>: contexto novo, rota /api/v1/conversations (ou /conversations/<id>) forçada a 403, abort ou 500
- **Evidência:** {"target":"lista","kind":403,"text":"... Não foi possível concluir a ação. Tente de novo em instantes.","retry":0} / {"target":"conversa","kind":"offline","text":"Algo deu errado Ir para o início","retry":0}
- **Correção sugerida:** Usar QueryState (ou a mesma classificação queryProblem) nessas telas, com o botão de tentar de novo; cobrir no states.spec.

### A33. [Média] Erro de página intermitente 'The operation was aborted.' no Firefox reprova o native-controls (escondido por retries no CI)
- **IDs:** UI-05, QA-03 · **Área:** 10-UI (verificação)
- **Local:** `frontend/e2e/fixtures.ts:53 (detecção); origem no app não localizada`
- **Reproduzir:** Instância nova (JAR de referência, perfil padrão e lan). Rodar PAPINHO_E2E_URL=... PAPINHO_E2E_HTTPS_URL=... pnpm exec playwright test native-controls.spec.ts a11y.spec.ts --project=primeiro-admin --project=desktop-firefox ... --retries=0. Falhou em 1 de 2 rodadas completas aqui e ficou flaky no CI #65.
- **Evidência:** [desktop-firefox] native-controls.spec.ts:224:1 ... Error: erros de console ou de CSP ... + "pageerror: The operation was aborted. " (pwv-10-UI/ref.log)
- **Correção sugerida:** Capturar error.stack no fixture e achar a promessa rejeitada com AbortError sem tratamento (fetch cancelado ou mídia). Tratá-la no app, e não tolerá-la no teste.

### A34. [Média] Alvos de toque de 28-32 px no celular (regra de 44 px não pega botões com Tooltip/Dropdown)
- **IDs:** AR-02, AR-01 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/src/index.css:261-274; frontend/src/components/layout/app-shell.tsx:184-210`
- **Reproduzir:** Projeto mobile-chromium (Pixel 7, matchMedia('(pointer: coarse)')=true), abrir '/', a conversa e /settings/*. Medir a área efetiva por elementFromPoint
- **Evidência:** "Menu" caixa 32x32 efetiva 32x32; "Ações da mensagem" caixa 28x28 efetiva 28x28; "Voltar" 32x32; "Perfil" caixa 54x28; button[dialog-close] "Fechar" 28x28
- **Correção sugerida:** Aplicar o min-height/min-width de 2.75rem em pointer: coarse também a [data-slot=tooltip-trigger], [data-slot=dropdown-menu-trigger], [data-slot=dialog-close] e aos links de navegação (ou expandir a área com after:-inset). Criar um e2e que meça alvos >= 44 px
- **Verificação:** Verificador: confirmado (Alta→Média). Reproduzido no Chromium 1194 com o perfil Pixel 7 (matchMedia('(pointer: coarse)')=true, largura 412), na instância própria (porta 18215, jar de referência). Script: /tmp/claude-0/-home-user-papinho/ff28e175-65d3-55ca-ae13-0ef9187a2f4b/scratchpad/auditoria/v11-touch.cjs.

Saída em '/': button[slot=tooltip-trigger] "Adicionar contato" 32x32; "Meu link e QR Code" 32x32; button[slot=dropdown-menu-trigger] "Menu" 32x32; input[slot=input-group-control] "Buscar..." 361x32; "Ações da conversa: Bia" 32x32.

Na conversa: a "Voltar" 32x32; dropdown-menu-trigger "Ações da mensagem" 28x28; message-scrolle

### A35. [Média] Axe serious label-content-name-mismatch no botão 'Conversar com' da aba Contatos
- **IDs:** AR-01, AR-03 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/src/features/contact/components/contact-list.tsx:135-150`
- **Reproduzir:** Com um contato, abrir a aba Contatos e rodar o AxeBuilder com as tags wcag2a..wcag22aa (claro ou escuro)
- **Evidência:** serious label-content-name-mismatch x1: button[aria-label="Conversar com Hana Audit"]
- **Correção sugerida:** Fazer o nome acessível conter o texto visível (por exemplo, aria-label '<nome> @usuario, conversar' ou aria-describedby em vez de aria-label) e incluir a aba Contatos no a11y.spec.ts

### A36. [Média] /admin rola na horizontal a 320 px (abas não cabem)
- **IDs:** AR-02 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/src/features/admin (TabsList da página /admin); frontend/e2e/fixtures.ts:107-114 só mede documentElement`
- **Reproduzir:** Celular com viewport 320x640, entrar como admin e abrir /admin
- **Evidência:** "scrollers": ["MAIN[] 383>320"]; BUTTON "Transcrição" right=329; BUTTON "Disco" right=380
- **Correção sugerida:** Fazer a TabsList quebrar linha ou rolar dentro de si mesma (overflow-x-auto na lista, não no main). O expectNoHorizontalScroll deve checar também os contêineres internos

### A37. [Média] Teste 'fluxo completo só com o teclado' usa focus() programático e não prova o fluxo
- **IDs:** AR-03 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/e2e/a11y.spec.ts:140-141 e 151-154`
- **Reproduzir:** Pôr tabIndex={-1} no InputGroupTextarea de composer.tsx:363, fazer o build e rodar a11y.spec.ts -g teclado
- **Evidência:** [2/2] [desktop-chromium] › e2e/a11y.spec.ts:101:1 › fluxo completo só com o teclado ... 1 passed (4.6s)
- **Correção sugerida:** Chegar ao composer e ao botão de ações com Tab/Shift+Tab, como fez o spec de auditoria, e checar o foco visível em todas as paradas

### A38. [Média] Nenhuma guarda automática contra texto fixo na UI ou chave sumida nos dois idiomas
- **IDs:** IN-01 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/src/lib/i18n.test.ts:15-19; frontend/eslint.config (sem regra i18next/no-literal-string)`
- **Reproduzir:** Trocar t(...) por um literal em contact-list.tsx, ou apagar chat.startConversation de pt-BR.json e en.json, e rodar pnpm test, lint e typecheck
- **Evidência:** Test Files  15 passed (15) / Tests  86 passed (86) / lint exit=0 / typecheck exit=0
- **Correção sugerida:** Adicionar eslint-plugin-i18next (no-literal-string em JSX) e um teste que confira que toda chave literal usada no código existe no pt-BR

### A39. [Média] install-ca.sh confere um bloco mas instala o arquivo PEM inteiro: uma CA extra (TRUSTED CERTIFICATE) passa pela checagem da impressão digital
- **IDs:** PK-04, QA-05 · **Área:** 12-PK-QA-geral (área)
- **Local:** `backend/src/main/resources/pki/install-ca.sh:161-168 e 183-186`
- **Reproduzir:** Montar ca.pem = CA legítima + bloco '-----BEGIN TRUSTED CERTIFICATE-----' de outra CA (openssl x509 -trustout -addtrust serverAuth). Apontar o CA_URL do script servido para esse arquivo e rodar 'sh install-ca.sh --yes' num Debian. Subir um openssl s_server com uma folha para banco.example.com assinada pela CA do atacante e rodar curl.
- **Evidência:** 'Impressão digital SHA-256 conferida: baed86...'; '/etc/ssl/certs/ca-certificates.crt:2' (blocos TRUSTED); 'openssl verify -purpose sslserver ... /s/leaf.pem: OK'; curl https://banco.example.com:18229/ → '200' (antes: 'curl: (60) SSL certificate problem')
- **Correção sugerida:** Instalar só o DER verificado, reconvertido para PEM (openssl x509 -inform DER ou base64 com cabeçalho), nunca o arquivo baixado. Ou recusar qualquer arquivo com mais de um bloco PEM ou com texto fora do único bloco CERTIFICATE. Acrescentar ao verify-install-ca.sh casos de CA trocada e de bloco anexado.
- **Verificação:** Verificador: confirmado (Alta→Média). Reproduzi de forma independente no Fedora 42 (contêiner quay.io/fedora/fedora:42, caminho update-ca-trust), não no Debian.
- Usei o install-ca.sh servido pela minha instância lan, com CA_URL trocado para file:///w/tampered.pem: a CA legítima mais 'openssl x509 -trustout -addtrust serverAuth' de uma 'Evil CA'.
- Saída: 'Impressão digital SHA-256 conferida: 791223ae60c1...' e 'Sistema: /etc/pki/ca-trust/source/anchors/papinho-ca-papinho.lan.pem'.
- O arquivo instalado tem 2 blocos BEGIN, e 'trust list' mostra 'label: Evil CA / trust: anchor'.
- curl em https://banco.example.com:18227/ (openssl s

### A40. [Média] Nenhum teste automatizado prova que uma impressão digital errada aborta a instalação
- **IDs:** PK-04 · **Área:** 12-PK-QA-geral (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/pki/LanSetupIT.java:155-168; scripts/verify-install-ca.sh:24-40`
- **Reproduzir:** Trocar 'if [ "$ACTUAL_FINGERPRINT" != "$EXPECTED_FINGERPRINT" ]; then' por 'if false; then' e rodar LanSetupIT.
- **Evidência:** [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 ... BUILD SUCCESS
- **Correção sugerida:** Teste (shell em contêiner ou IT com ProcessBuilder sh) que serve uma CA diferente e afirma exit 1 com 'não confere' e nenhum arquivo instalado.

### A41. [Média] Não existem testes slice; STATUS e CI alegam que sim
- **IDs:** QA-01 · **Área:** 12-PK-QA-geral (área)
- **Local:** `backend/src/test (nenhum @WebMvcTest/@DataJdbcTest/@JsonTest/@ApplicationModuleTest); .github/workflows/ci.yml:29; STATUS.md:235`
- **Reproduzir:** grep -rnE '@(WebMvcTest|DataJdbcTest|DataJpaTest|JsonTest|JdbcTest|RestClientTest|ApplicationModuleTest)' backend/src/test
- **Evidência:** saída vazia; só support/IntegrationTest.java:22 '@AutoConfigureMockMvc' sobre @SpringBootTest
- **Correção sugerida:** Acrescentar testes slice (por exemplo @WebMvcTest dos controllers com as regras de segurança, @DataJdbcTest dos repositórios e @ApplicationModuleTest por módulo) ou corrigir STATUS e CI.

### A42. [Média] Armazenamento S3-compatível exigido pelo ADR 0006 não existe
- **IDs:** §6 (ADR 0006) · **Área:** 12-PK-QA-geral (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/media/FileStorage.java:11-13; media/internal/FilesystemFileStorage.java:24; docs/adr/0006-armazenamento-de-arquivos-atras-de-porta.md`
- **Reproduzir:** grep -rn 'implements FileStorage' backend/src/main/java; grep -rniE 's3|minio|awssdk' backend/pom.xml backend/src/main
- **Evidência:** só 'FilesystemFileStorage implements FileStorage'; a menção a S3 está apenas em comentários (FileStorage.java:11, application.yaml:115)
- **Correção sugerida:** Implementar o adaptador S3 (com Testcontainers MinIO, como o próprio ADR prevê) ou registrar num ADR novo que fica fora do escopo, e alinhar o STATUS.

### A43. [Baixa] O limite por conta permite bloquear o login de outra pessoa
- **IDs:** ID-04 · **Área:** 01-ID-AD (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/IdentityService.java:139-140; shared/security/RateLimiter.java:28`
- **Reproduzir:** Com rate limit x1, fazer 10 POST /api/v1/auth/login com o @usuario da vítima e senha errada (de qualquer IP). Depois, a vítima tenta com a senha certa.
- **Evidência:** bia [401, 401, 401, 401, 401, 401, 401, 401, 401, 401, 429] / bia senha certa depois do 429 429
- **Correção sugerida:** Usar o balde por par IP+conta ou exigir um passo extra (atraso progressivo, CAPTCHA, passkey) em vez de bloquear a conta toda. Ou aceitar o risco e registrá-lo num ADR.

### A44. [Baixa] /auth/recover revela pelo tempo de resposta se a conta existe
- **IDs:** ID-04, ID-05 · **Área:** 01-ID-AD (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/IdentityService.java:237-246`
- **Reproduzir:** Com rate limit x10, fazer POST /api/v1/auth/recover {username, recoveryCode:'aaaa-bbbb-cccc', newPassword:'senha-nova-bem-longa'} alternando entre 'naoexiste3' e 'ana', 6 vezes cada. Comparar o time_total do curl.
- **Evidência:** rinex 0.036669 0.037857 0.038188 0.040604 0.054129 0.055368 | rexist 0.277004 0.290428 0.291527 0.298954 0.304414 0.331073. As duas respostas são '400 urn:papinho:problem:code-invalid' com o mesmo corpo, mas a conta existente leva cerca de 8 vezes mais: percorre até 10 hashes Argon2 de códigos de recuperação, contra 1 dummyHash para a inexistente. O impacto é limitado, porque /usernames/availability já diz se o nome está ocupado (120 consultas a cada 15 min por IP) e o recover tem limite de 10 por hora por IP.
- **Correção sugerida:** Igualar o custo: para conta inexistente ou desativada, fazer tantos matches com o dummyHash quanto o número fixo de códigos (10). Ou sempre percorrer todos os candidatos sem sair no primeiro acerto, completando com dummies até 10. Acrescentar um teste que mutile o caminho do dummy.

### A45. [Baixa] Igualdade de tempo do login (dummyHash) sem nenhum teste
- **IDs:** ID-04 · **Área:** 01-ID-AD (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/IdentityService.java:144`
- **Reproduzir:** Comentar 'passwordEncoder.matches(password, dummyHash);' e rodar PasswordPolicyTest, IdentityIT e SecurityIT.
- **Evidência:** mutA.log: as únicas falhas foram as das outras mutações do mesmo lote (shouldDisableUser e shouldCheckBreachedPasswordsOnlyWhenEnabled). Nenhum teste falhou pela remoção do dummyHash. 'grep -rn dummyHash|timing backend/src/test' não encontrou nada.
- **Correção sugerida:** Teste unitário com um PasswordEncoder espião que conte as chamadas de matches para usuário inexistente, igual a 1.

### A46. [Baixa] Excluir a conta libera na hora o @usuario atual e os nomes antigos reservados
- **IDs:** ID-03 · **Área:** 01-ID-AD (verificação)
- **Local:** `identity/UsernameReservation (FK com ON DELETE CASCADE) e AccountDeletionService`
- **Reproduzir:** A ana troca para 'ana.velha' (assim 'ana' fica reservado). Ela faz DELETE /api/v1/account {password}. Logo depois, a bia faz PUT /api/v1/account/username {username:'ana'}.
- **Evidência:** Antes: 'ana|t' em username_reservations. Depois do DELETE (HTTP204), a reserva sumiu e a bia recebeu '"username":"ana" HTTP200'. Os contatos que conheciam @ana passam a ver outra pessoa com esse nome.
- **Correção sugerida:** Na exclusão, converter o @usuario atual e as reservas em reservas sem dono (user_id nulo) por 30 dias, em vez de apagar em cascata.

### A47. [Baixa] Deslocamento do recorte da foto não é limitado no estado e cria uma zona morta
- **IDs:** ID-02 · **Área:** 01-ID-AD (verificação)
- **Local:** `frontend/src/features/account/components/photo-cropper.tsx:74-76 (o limite só é aplicado no desenho, :58-61)`
- **Reproduzir:** No diálogo 'Ajustar foto', com uma imagem 2:1, apertar ArrowLeft 40 vezes (deslocamento de 640, o limite é 256) e depois ArrowRight: a imagem só se mexe a partir do 25º toque.
- **Evidência:** Leitura do código: setOffset acumula sem limite e só o desenho usa Math.min/Math.max. No script crop.mjs, 40x ArrowLeft já dava o enquadramento máximo (center=srgb(0,0,254)), igual ao de 16 toques. Não medi a zona morta por toque.
- **Correção sugerida:** Limitar o offset em move() com o mesmo maxX e maxY do desenho.

### A48. [Baixa] Foto de perfil fica fora da exportação de dados da conta
- **IDs:** BK-02 · **Área:** 02-CT-BK (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/backup/AccountExport.java (só media.ownedBy); profile/ProfilePersonalData.java`
- **Reproduzir:** Subir uma foto de perfil e chamar GET /api/v1/account/export?files=true
- **Evidência:** perfil {"temFoto":true,...}; unzip -l: '1752 ... dados.json' e '1 file'
- **Correção sugerida:** Incluir a foto (perfil/foto.jpg) quando files=true e testar no AccountDataIT.

### A49. [Baixa] UUID da conta apagada continua em conversations.direct_key
- **IDs:** BK-02 · **Área:** 02-CT-BK (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/AccountDeletionService.java:65-73`
- **Reproduzir:** Excluir a conta e varrer as colunas uuid/text do banco procurando o id (scan.sql)
- **Evidência:** NOTICE:  conversations.direct_key (character varying): 1 linha(s)
- **Correção sugerida:** No AccountDeleting, reescrever ou anular o direct_key das conversas individuais da conta.

### A50. [Baixa] ShedLock do job de temporárias sem nenhum teste
- **IDs:** MS-07 · **Área:** 03-MS-a (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/DisappearingMessagesJob.java:21`
- **Reproduzir:** Remover a linha @SchedulerLock(name = "disappearing-messages", ...) e rodar DisappearingIT.
- **Evidência:** === M7-sem-shedlock (DisappearingIT)
exit=0
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
- **Correção sugerida:** Teste de integração que rode o job agendado (ou o proxy do bean) e confira a linha 'disappearing-messages' na tabela shedlock, ou que duas execuções simultâneas não apagam em dobro.

### A51. [Baixa] Testes de apagar para todos e do job conferem o arquivo só pela API, não pelo disco
- **IDs:** MS-06, MS-07 · **Área:** 03-MS-a (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/messaging/MessagingIT.java:298; DisappearingIT.java:117`
- **Reproduzir:** Remover `storage.delete(MediaFile.contentKey(id));` em MediaService.remove e rodar MessagingIT#shouldEditAndDeleteForEveryone e DisappearingIT.
- **Evidência:** Tests run: 3, Failures: 0 ... DisappearingIT
Tests run: 1, Failures: 0 ... MessagingIT
(só MediaIT.shouldPurgeFilesNeverUsed:319 falha)
- **Correção sugerida:** Nos dois testes, conferir também que o arquivo não existe mais no storage (StorageService/diretório), como o MediaIT já faz.

### A52. [Baixa] MediaIT grava arquivos em /root/.papinho/files, o diretório real do usuário
- **IDs:** (higiene de testes) · **Área:** 03-MS-a (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/media/MediaIT.java:319`
- **Reproduzir:** Rodar MediaIT com a mutação M5 e ler a mensagem da asserção.
- **Evidência:** Expecting an empty Optional but was containing value: file [/root/.papinho/files/media/36d47b5d-1421-4c24-817a-ed7862316290]
- **Correção sugerida:** Nos testes, apontar app.storage.dir/PAPINHO_DATA_DIR para um @TempDir.

### A53. [Baixa] Editar mensagem própria já apagada devolve 403 'message-not-author'
- **IDs:** MS-05 · **Área:** 03-MS-a (verificação)
- **Local:** `messaging/MessageService.java:769-771 (requireAuthorWithin junta autor, apagada e sistema numa só exceção)`
- **Reproduzir:** A ana envia uma mensagem, faz DELETE /api/v1/messages/<id> e depois PATCH /api/v1/messages/<id> {"body":"ressuscita"}.
- **Evidência:** "status":403 "type":"urn:papinho:problem:message-not-author" HTTP=403 (a ana é a autora)
- **Correção sugerida:** Separar os casos: a mensagem apagada (ou de sistema) devolve um Problem próprio, por exemplo 409 message-deleted com o texto em messages*.properties, e o 403 not-author fica só para quem não é o autor.

### A54. [Baixa] ViewOnceIT não confere que o arquivo sai depois dos 14 dias, só o 410
- **IDs:** MS-08 · **Área:** 04-MS-b (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/messaging/ViewOnceIT.java:115-119`
- **Reproduzir:** Ler shouldValidateAndExpire: depois de job.purge() só há o assert hasStatus(410).
- **Evidência:** assertThat(bruno.post("/api/v1/messages/" + id + "/view-once", null)).hasStatus(410);
- **Correção sugerida:** Acrescentar a contagem em media_files para o anexo da mensagem de 15 dias. A caixa-preta da auditoria confirmou que o arquivo sai.

### A55. [Baixa] Figurinha WebP animada sem teste automatizado
- **IDs:** MS-10 · **Área:** 04-MS-b (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/messaging/StickerIT.java:27-28`
- **Reproduzir:** StickerIT só usa um WebP estático de 1x1 (VP8L).
- **Evidência:** private static final byte[] WEBP = Base64..."UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA=="
- **Correção sugerida:** Acrescentar um WebP animado (ANIM/ANMF) e conferir que o conteúdo devolvido continua animado. A caixa-preta da auditoria mostrou 'quadros 3 animada True'.

### A56. [Baixa] Contato bloqueado ainda vota em enquete e reage na conversa direta
- **IDs:** MS-09, MS-04, CT-03 · **Área:** 04-MS-b (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:634 (vote) e :371 (react) usam requireVisible/requireReadable, não requireSendable (ConversationService.java:334-353)`
- **Reproduzir:** 1. A ana cria uma enquete na conversa direta com a bia.
2. A ana chama PUT /api/v1/blocks/{bia}.
3. A bia chama POST /conversations/{C}/messages.
4. A bia chama PUT /api/v1/messages/{enquete}/vote.
5. A bia chama PUT /api/v1/messages/{enquete}/reaction com {"emoji":"👍"}.
- **Evidência:** --- ana bloqueia bia: HTTP 204
--- bia envia texto: HTTP 403
--- bia vota na enquete da ana (bloqueada): {"changeSeq":8,"opts":[{"text":"sim","votes":0},{"text":"nao","votes":1},...]}
--- bia reage (bloqueada): HTTP 200
- **Correção sugerida:** Em vote e react, recusar quando conversations.blockedWithSomeone(me, conversationId) numa conversa DIRECT (BlockedException, 403), ou usar uma checagem 'pode interagir' comum. Cobrir com um IT.

### A57. [Baixa] Nenhum teste cobre a recusa de enquete criada com prazo no passado
- **IDs:** MS-09 · **Área:** 04-MS-b (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:615-617`
- **Reproduzir:** 1. Trocar a condição 'closesAt != null && !closesAt.isAfter(clock.instant())' por false.
2. Rodar -Dit.test=PollIT: só falha a asserção do 409 de outra mutação aplicada junto.
3. grep closesAt em backend/src/test e frontend/e2e só acha PollIT, que cria com +3600 s ou altera o banco.
- **Evidência:** grep -rn 'closesAt|closes_at' backend/src/test frontend/e2e mostrou só usos de prazo futuro no PollIT. A caixa-preta confirma que hoje funciona: closesAt 2020-01-01 -> HTTP 400.
- **Correção sugerida:** Em PollIT, acrescentar uma asserção: criar com closesAt = Instant.now().minusSeconds(1) deve dar 400.

### A58. [Baixa] LinkPreviewIT.shouldRefusePrivateTargets passa por acaso em 127.0.0.1: o IT libera loopback
- **IDs:** MD-04 (teste) · **Área:** 05-MD-AU (área)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/linkpreview/LinkPreviewIT.java:93 e :139-147; backend/src/main/java/io/github/labdev2025cmd/papinho/linkpreview/LinkPreviewService.java:70-81`
- **Reproduzir:** Deixar qualquer servidor HTTP escutando em 127.0.0.1:80 e rodar -Dit.test=LinkPreviewIT
- **Evidência:** 'LinkPreviewIT.shouldRefusePrivateTargets:144 [http://127.0.0.1/admin] Expecting value to be false but was true'; sem o servidor: 'Tests run: 4, Failures: 0'
- **Correção sugerida:** Provar 127.x com a política de produção (como o SafeFetcherTest faz com PUBLIC_ONLY) ou tirar 127.0.0.1 dessa lista no IT; não usar loopback liberado para afirmar bloqueio.

### A59. [Baixa] Antivírus indisponível vira 500 genérico e deixa o upload completo pendente
- **IDs:** MD-06 · **Área:** 05-MD-AU (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/media/internal/ClamAvScanner.java:39; backend/src/main/java/io/github/labdev2025cmd/papinho/media/UploadService.java:205`
- **Reproduzir:** PAPINHO_CLAMAV_ENABLED=true com o clamd parado; enviar qualquer arquivo por tus
- **Evidência:** '"status":500,"title":"Erro interno","type":"urn:papinho:problem:internal"'; '[HEAD depois: 200]'; log 'java.net.ConnectException: Connection refused'
- **Correção sugerida:** Mapear a falha do scanner para um Problem Details próprio (503, 'antivírus indisponível') com texto em messages*.properties; decidir se o upload é descartado ou fica para nova tentativa.

### A60. [Baixa] Com o pool esgotado, a API responde a página HTML do Tomcat em vez de Problem Details
- **IDs:** SG-01 (visto no MD-04) · **Área:** 05-MD-AU (área)
- **Local:** `não localizado (falha antes do GlobalExceptionHandler, provavelmente no filtro de sessão JDBC)`
- **Reproduzir:** Esgotar o pool (achado acima) e fazer GET /api/v1/conversations
- **Evidência:** '<h1>HTTP Status 500 – Internal Server Error</h1></body></html> [HTTP 500 90.058641s]'
- **Correção sugerida:** Página de erro do servidor que emite application/problem+json para /api/**, ou tratar a falha de conexão no filtro.

### A61. [Baixa] Nenhum teste prova que a chave do provedor fica cifrada em repouso (mutação para texto puro sobrevive)
- **IDs:** AU-05 · **Área:** 05-MD-AU (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/transcription/TranscriptionService.java:115,165`
- **Reproduzir:** No worktree, trocar cipher.decrypt(provider.apiKeyEncrypted()) por provider.apiKeyEncrypted() e cipher.encrypt(apiKey.strip()) por apiKey.strip(); rodar -Dit.test=TranscriptionIT.
- **Evidência:** 'Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 ... BUILD SUCCESS'; grep 'api_key_encrypted|apiKeyEncrypted|SecretCipher' em backend/src/test: nada. Na caixa-preta, hoje o banco tem 'v1:...' e o pg_dump não tem a chave.
- **Correção sugerida:** No TranscriptionIT, ler transcription_providers.api_key_encrypted via JDBC e afirmar que não contém o SECRET e começa com 'v1:'.

### A62. [Baixa] Trocar a URL base do provedor mantém a chave guardada e a envia ao novo destino
- **IDs:** AU-05 · **Área:** 05-MD-AU (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/transcription/TranscriptionService.java:153-167`
- **Reproduzir:** Com um provedor que já tem chave, o admin faz PUT /api/v1/admin/transcription/providers/{id} com nova baseUrl (http://127.0.0.1:18157) sem o campo apiKey; um usuário pede uma transcrição.
- **Evidência:** '{"baseUrl":"http://127.0.0.1:18157","hasKey":true}'; o novo destino recebeu '"auth": "Bearer sk-VERIF05-SEGREDO-91c2"'.
- **Correção sugerida:** Ao mudar o host da baseUrl, apagar a chave ou exigir que ela seja informada de novo (o padrão de 'secret re-entry').

### A63. [Baixa] Exclusão da visualização única na galeria não tem teste (mutação sobrevive)
- **IDs:** MD-03, MS-08 · **Área:** 05-MD-AU (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageRepository.java:51-52`
- **Reproduzir:** No worktree, tirar 'and m.viewOnce = false' de findWithAttachments; rodar -Dit.test='MediaIT,ViewOnceIT'.
- **Evidência:** 'Tests run: 2, Failures: 0 ... in ...ViewOnceIT'; o MediaIT só falhou por outra mutação, aplicada junto (shouldUploadInChunks:127).
- **Correção sugerida:** No ViewOnceIT, afirmar que a mensagem de visualização única não aparece em gallery?tab=MEDIA/AUDIO.

### A64. [Baixa] Nenhum teste cobre a exclusão do ex-membro (LEFT) na busca global
- **IDs:** GR-06 · **Área:** 06-GR (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageSearch.java:44`
- **Reproduzir:** Troque o filtro por `cm.state in ('ACTIVE','REQUEST','LEFT','PENDING','DECLINED')` e rode SearchIT.
- **Evidência:** [INFO] Tests run: 3, Failures: 0, Errors: 0 -- in ...SearchIT
- **Correção sugerida:** Adicionar ao SearchIT: quem sai do grupo (e quem está com pedido pendente) não acha as mensagens dele na busca global.

### A65. [Baixa] A busca não junta singular e plural com -ão/-ões nem mensagem/mensagens, e a migração afirma o contrário
- **IDs:** GR-06 · **Área:** 06-GR (área)
- **Local:** `backend/src/main/resources/db/migration/messaging/V202610190200__add_message_search.sql:1-9`
- **Reproduzir:** Envie "A Reunião de Ações" e "Mandei as mensagens" e chame GET /api/v1/search/messages?q=acao, ?q=reuniões e ?q=mensagem.
- **Evidência:** ana 'acao' -> []; ana 'mensagem' -> []; ana 'reuniões' -> []; psql: to_tsvector('papinho_portuguese','mensagens') @@ plainto_tsquery('papinho_portuguese','mensagem') = f
- **Correção sugerida:** Corrigir o comentário da migração. Avaliar um dicionário (ispell/hunspell pt_BR, ou stem antes de unaccent) ou normalização extra para os plurais comuns.

### A66. [Baixa] Editar a lista temporária aceita validade no passado e reabre lista vencida
- **IDs:** GR-04 · **Área:** 06-GR (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/BroadcastService.java:73`
- **Reproduzir:** PUT /api/v1/broadcasts/{id} com expiresAt=2020-01-01T00:00:00Z, e depois com uma data futura numa lista já vencida.
- **Evidência:** PUT lista com expiresAt no passado -> 200; POST lista com expiresAt no passado -> 400; edição de lista vencida -> {"expiresAt":"2030-01-01T00:00:00Z","expired":false}
- **Correção sugerida:** Passar clock.instant() para validate também no update (ou recusar editar lista vencida).

### A67. [Baixa] Aprovar pedido de entrada não confere o limite de membros
- **IDs:** GR-02 · **Área:** 06-GR (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/GroupService.java:376-392`
- **Reproduzir:** Com o grupo em 1024 membros ativos e pedidos pendentes, aprovar um pedido (não executado: exigiria 1024 contas).
- **Evidência:** Leitura de código: decideJoin chama request.rejoin sem a checagem `> MAX_MEMBERS` que join (:348) e addMembers (:207) fazem
- **Correção sugerida:** Repetir a checagem de MAX_MEMBERS em decideJoin quando approve=true.
- **Verificação:** Reproduzido de forma independente pelo verificador de 06-GR.

### A68. [Baixa] Prévia do convite responde 200 a quem foi bloqueado por um admin, mas o join responde 'link inválido'
- **IDs:** GR-02 · **Área:** 06-GR (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/GroupService.java:316-325 (preview) vs :342-345 (join)`
- **Reproduzir:** edu bloqueia a ana (admin do grupo) com PUT /api/v1/blocks/{ana}. Depois faz GET /api/v1/groups/invites/<code> e POST /api/v1/groups/invites/<code>/join.
- **Evidência:** edu prévia: HTTP 200  join: {"detail":"Este link de convite não existe ou foi revogado.",..."status":404,..."type":"urn:papinho:problem:group-invite-not-found"} HTTP 404
- **Correção sugerida:** Aplicar na prévia a mesma checagem de bloqueio do join e responder 404, para não expor nome, descrição e contagem de membros e não contradizer o join.

### A69. [Baixa] Regras de produção sem teste: cinco mutações sobreviveram à suíte IT inteira
- **IDs:** GR-03, GR-04, GR-05, GR-02 · **Área:** 06-GR (verificação)
- **Local:** `MessageService.java:265 e :268; ChatListService.java:127-130 e :252; GroupService.java:343`
- **Reproduzir:** Aplicar ao mesmo tempo: ONE_WEEK -> 8 dias; fanOut sem isBlockedEitherWay; remove() sem apagar chat_folder_items; fanOut sem expireAt no original; join sem 'if (blocked)'. Rodar ./mvnw -pl backend verify -Dit.test='*IT'.
- **Evidência:** [INFO] Tests run: 135, Failures: 0, Errors: 0, Skipped: 0 (mutD.log). Pela caixa-preta, todas as cinco regras funcionam no jar de referência (s1/s2/s4.out).
- **Correção sugerida:** Acrescentar asserções: BroadcastIT, para bloqueio depois de criar a lista e para expiresAt do original; ChatListIT, para ONE_WEEK e para a pasta depois de apagar a conversa; GroupIT, para join de quem foi bloqueado por um admin.

### A70. [Baixa] Anexos de conversa trancada acessíveis por id sem token
- **IDs:** PV-03 · **Área:** 07-PV (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageMediaAccess.java:24-27`
- **Reproduzir:** Leitura de código: canRead não consulta ConversationLocks (a ADR 0013 admite isso em 'Ruins'). Não executei por HTTP.
- **Evidência:** return messages.attachmentVisibleTo(viewer, mediaId) || groups.photoVisibleTo(viewer, mediaId);
- **Correção sugerida:** Passar o token por cookie de curta duração ou por URL assinada para a mídia das trancadas.

### A71. [Baixa] Gatilho de atalho já vem ligado ao criar o bloqueio de tela
- **IDs:** PV-02 · **Área:** 07-PV (área)
- **Local:** `frontend/src/features/privacy/components/screen-lock-settings.tsx:66-71`
- **Reproduzir:** Configurações > Privacidade > liga o bloqueio com PIN e aperta Alt+Shift+L.
- **Evidência:** shortcut: "Alt+Shift+L" no estado inicial; o e2e privacy.spec.ts:244-246 'O atalho padrão também bloqueia'
- **Correção sugerida:** Começar com shortcut null, o que o requisito pede ('Tudo desativado por padrão').

### A72. [Baixa] Interruptor de mídia não é independente do de mensagens
- **IDs:** PV-01 · **Área:** 07-PV (área)
- **Local:** `frontend/src/features/chat/components/message-row.tsx:220-351; frontend/src/index.css:320-326`
- **Reproduzir:** Modo privado com mensagens ligado e mídia desligado: a foto dentro do balão fica desfocada, porque herda o filtro do BubbleContent.
- **Evidência:** <BubbleContent ... data-private="messages"> envolve <AttachmentView> (data-private="media")
- **Correção sugerida:** Aplicar o blur de 'messages' só ao texto, não ao balão inteiro, ou separar o anexo do bloco desfocado.

### A73. [Baixa] Mutantes sobreviventes: validade do token, leitura pelo STOMP, info e padrão do grupo
- **IDs:** PV-03, PV-05, PV-06 · **Área:** 07-PV (área)
- **Local:** `ConversationLocks.java:45-46; ConversationService.java:400, :272; MessageService.java:502`
- **Reproduzir:** python3 mut.py com IDLE e MAX_LIFETIME de 3650 dias; seeRead=new HashSet<>(others); info sem iShare; shares sem a regra do grupo.
- **Evidência:** [pv03-ttl] mvn exit=0; [pv05-read-stomp] mvn exit=0; [pv06-info] mvn exit=0; [pv06-group-default] mvn exit=0
- **Correção sugerida:** Criar testes com Clock controlado para a expiração do token, um IT STOMP com quem não compartilha, o info com quem leu desligado e um grupo com o global desligado.

### A74. [Baixa] Lacunas de teste: CONTACTS_EXCEPT em PV-07 e o evento STOMP 'notify' de PV-08 não têm teste automatizado
- **IDs:** PV-07, PV-08 · **Área:** 07-PV (verificação)
- **Local:** `backend/src/test/java/io/github/labdev2025cmd/papinho/privacy/PrivacyIT.java:204-225; backend/src/test/java/io/github/labdev2025cmd/papinho/notification/NotificationIT.java:135-152`
- **Reproduzir:** 1) Em PrivacyService.allows, troque o caso CONTACTS_EXCEPT por `contacts.isContact(owner, viewer) && (item == PrivacyItem.GROUPS_ADD || !isExcepted(owner, item, viewer))` e rode `-Dit.test='PrivacyIT,GroupIT'`. 2) Rode `grep -rn '"notify"' backend/src/test frontend/e2e`.
- **Evidência:** Com a mutação: 'Tests run: 7, Failures: 0 -- in ...PrivacyIT' e 'Tests run: 5, Failures: 0 -- in ...GroupIT' (BUILD SUCCESS). O grep por '"notify"' não devolveu nada.
- **Correção sugerida:** Em PrivacyIT.shouldRespectWhoCanAddToGroups, cobrir CONTACTS_EXCEPT com exceção (403) e sem exceção (201). Em NotificationIT, assinar /user/queue/events com WebSocketStompClient (como no RealtimeIT) e conferir title/body do 'notify' com hideNotificationContent=true.

### A75. [Baixa] Mesmo clientMessageId em duas conversas ao mesmo tempo dá 500 em vez de 409
- **IDs:** RT-02, SG-01 · **Área:** 08-NT-RT (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/MessageService.java:137-144`
- **Reproduzir:** Duas requisições simultâneas da ana com o mesmo clientMessageId, cada uma para uma conversa diferente (rt08.py crossconv, 30 rodadas).
- **Evidência:** pares de status: Counter({(201, 500): 30}); log: duplicate key value violates unique constraint "uq_messages_sender_client_message_id"
- **Correção sugerida:** Travar pela chave (sender, clientMessageId) com pg_advisory_xact_lock (lockKey já existe) ou traduzir DataIntegrityViolation dessa constraint para ClientMessageIdConflictException (409).

### A76. [Baixa] Nenhum teste cobre a expiração do heartbeat de presença
- **IDs:** RT-03 · **Área:** 08-NT-RT (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/presence/PresenceService.java:127`
- **Reproduzir:** Troque o predicado por 'beat -> beat.active()' e rode RealtimeIT,PrivacyIT.
- **Evidência:** Tests run: 13, Failures: 0 ... BUILD SUCCESS (mutação sobreviveu)
- **Correção sugerida:** Teste com Clock controlável que avança 46 s e chama sweep(), esperando presence offline.
- **Verificação:** Reproduzido de forma independente pelo verificador de 08-NT-RT.

### A77. [Baixa] Preferências global, grupos, menções e som sem teste de integração (mutações sobrevivem)
- **IDs:** NT-03 · **Área:** 08-NT-RT (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/notification/NotificationDispatcher.java:76,83,89,94; backend/src/test/java/io/github/labdev2025cmd/papinho/notification/NotificationIT.java:155-189`
- **Reproduzir:** No worktree em 3f4cb6d, rodar `python3 -I auditoria/v08/mut.py enabled groups mentions sound muted direct`. O script aplica cada mutação em decide(), roda `-Dit.test=NotificationIT` e reverte. As mutações:
- enabled: tira `!prefs.enabled() ||`.
- groups: `recipient.group() ? false : ...`.
- mentions: `!(mention)`.
- sound: silent=false fixo.
- **Evidência:** == MUT enabled -> Tests run: 7, Failures: 0
== MUT muted -> NotificationIT.shouldRespectMuteAndPreferences:161 [silenciada] FALHOU
== MUT groups -> Tests run: 7, Failures: 0
== MUT direct -> NotificationIT.shouldRespectMuteAndPreferences:177 [individuais desligadas] FALHOU
== MUT mentions -> Tests run: 7, Failures: 0
== MUT sound -> Tests run: 7, Failures: 0

O comportamento atual está correto, segundo a caixa-preta por STOMP (nt03.mjs).
- **Correção sugerida:** Em NotificationIT.shouldRespectMuteAndPreferences (ou num teste novo), acrescentar asserções para:
- enabled=false: nada;
- groups=false: mensagem de grupo não avisa;
- groups=false com mentions=true: só a menção avisa;
- groups=false com mentions=false: nem a menção avisa;
- sound=false: o payload do push sai com silent=true.

### A78. [Baixa] NUL (\u0000) em campos de texto vira 500 em vez de 400 e registra a exceção inteira
- **IDs:** SG-01 · **Área:** 09-SG (área)
- **Local:** `messaging/MessageService (envio), rascunho, StickerService.createPack, ChatListService (pastas), contato (nickname), SearchController`
- **Reproduzir:** POST /api/v1/conversations/{id}/messages com {"clientMessageId":"<uuid>","body":"a\u0000b"} (o mesmo vale para draft, stickers/packs, folders, PATCH contacts nickname e GET search?q=a%00b)
- **Evidência:** 500 POST /api/v1/conversations/.../messages :: {"detail":"Não foi possível concluir a ação..."} ; no log: 'PSQLException: ERROR: invalid byte sequence for encoding "UTF8": 0x00'
- **Correção sugerida:** Recusar caracteres de controle (pelo menos U+0000) na validação de entrada, com 400 Problem Details, ou removê-los ao normalizar o texto.

### A79. [Baixa] HSTS só por variável manual e sem teste com HSTS ligado; teste de cabeçalhos aceita a perda de object-src e Permissions-Policy
- **IDs:** SG-02 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/resources/application.yaml:100; shared/security/SecurityConfig.java:193-197; test SecurityIT.java:79-93`
- **Reproduzir:** Remover "object-src 'none'" de SecurityProperties.DEFAULT_CSP e .permissionsPolicyHeader de SecurityConfig, trocar o HSTS ligado por disable e rodar SecurityIT e LanSetupIT.
- **Evidência:** SecurityIT 'Tests run: 12, Failures: 0' e LanSetupIT 'Tests run: 7, Failures: 0' (mut-A.log). Na caixa-preta com PAPINHO_HSTS=true e X-Forwarded-Proto https: 'Strict-Transport-Security: max-age=31536000 ; includeSubDomains'. Na instância padrão, sem HSTS.
- **Correção sugerida:** Comparar a CSP e a Permissions-Policy inteiras no SecurityIT e criar um teste com app.security.hsts=true e requisição segura que confira o HSTS. Avaliar ligar o HSTS sempre que a requisição for HTTPS, exceto no perfil lan.

### A80. [Baixa] Limite de convites (criação 30/h e verificação 60/10 min) sem teste
- **IDs:** SG-03 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/identity/InviteService.java:53,88`
- **Reproduzir:** Comentar rateLimiter.consume(INVITE_CREATE_BY_USER) e rodar IdentityIT (não há teste de 429 para convites: grep '429' em *IT.java).
- **Evidência:** Na mutação do lote B nenhum teste de convite falhou. Na caixa-preta funciona: 'convites (30/h por usuário): 30 201  2 429', 'verificação de convite por IP: 60 200  2 429'.
- **Correção sugerida:** Adicionar ao IdentityIT um teste que crie 31 convites e consulte invites/status 61 vezes, esperando 429 com Retry-After.

### A81. [Baixa] Handshake do /ws sem cabeçalho Origin é aceito
- **IDs:** SG-01 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/shared/realtime/WebSocketConfig.java:45`
- **Reproduzir:** curl -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: ...' -H 'Cookie: __Host-SESSION=<válido>' http://localhost:18190/ws (sem Origin)
- **Evidência:** 101 handshake cookie, sem Origin
- **Correção sugerida:** Só clientes que não são navegador conseguem fazer isso (navegadores sempre mandam Origin), então o CSWSH não piora. Se for desejável, recusar o handshake sem Origin com um interceptor.

### A82. [Baixa] Range inválido responde 416 com o arquivo inteiro no corpo e sem Problem Details
- **IDs:** SG-01 · **Área:** 09-SG (área)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/media/MediaController.java:46-51`
- **Reproduzir:** curl -H 'Range: bytes=abc' -b <ana> http://localhost:18190/api/v1/media/<id>/content
- **Evidência:** HTTP/1.1 416 ; Content-Range: bytes */539 ; Content-Length: 539 (corpo JPEG)
- **Correção sugerida:** Devolver o 416 sem corpo (ou com Problem Details). Hoje só a própria pessoa autorizada recebe o arquivo, sem vazamento.

### A83. [Baixa] Regras PV no evento STOMP de leitura e na lista de favoritas sem teste de integração
- **IDs:** SG-04, PV-05, PV-06, PV-03 · **Área:** 09-SG (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/ConversationService.java:393-411 (publishReceipts); MessageService.java:469-477 (starred)`
- **Reproduzir:** Duas mutações:
- (a) Em publishReceipts, readSeq=member.readSeq(), seeRead=others e seeDelivered=Set.of(). Rodar PrivacyIT, RealtimeIT, MessagingIT, GroupIT e NotificationIT.
- (b) Em starred(), tirar isVisible do filtro. Rodar PrivacyIT e MessagingIT.
- **Evidência:** (a) Nenhuma falha ligada à mutação ('Tests run: 36, Failures: 4'; as 4 falhas são das outras mutações do lote: PrivacyIT:209/94/288 e NotificationIT:150).
(b) PrivacyIT e MessagingIT passaram com a mutação.
- **Correção sugerida:** Criar:
- um IT STOMP afirmando que quem desligou a confirmação (ou o par dela) recebe receipts com readSeq=0;
- um IT afirmando que GET /messages/starred omite as mensagens de conversa trancada sem token.

### A84. [Baixa] Regra de lint contornável: type em expressão ou spread e title em componente com maiúscula passam
- **IDs:** UI-05 · **Área:** 10-UI (área)
- **Local:** `frontend/eslint.config.js:12-70`
- **Reproduzir:** Plantar em src/features/x.tsx: <input type={"checkbox"} />, <input {...{ type: "checkbox" }} />, <Button title="dica">x</Button>; rodar pnpm exec eslint
- **Evidência:** Nenhum erro nas linhas 5, 31 e 32 do arquivo plantado; as outras 21 violações foram apontadas
- **Correção sugerida:** Ampliar os seletores para JSXExpressionContainer com Literal e title em qualquer JSXOpeningElement exceto iframe/Tooltip; ou uma regra própria.
- **Verificação:** Reproduzido de forma independente pelo verificador de 10-UI.

### A85. [Baixa] Matriz do UI-05 sem celular e sem prova de autofill, botão nativo do type=search e setas do number
- **IDs:** UI-05 · **Área:** 10-UI (área)
- **Local:** `frontend/e2e/native-controls.spec.ts:37-56,231-234`
- **Reproduzir:** Ler o seletor forbidden e o test.skip(isMobile)
- **Evidência:** test.skip(isMobile, "a matriz do UI-05 é Chromium, Firefox e WebKit no desktop")
- **Correção sugerida:** Rodar a verificação também no mobile-chromium; checar ::-webkit-search-cancel-button e os spin buttons do number por getComputedStyle.

### A86. [Baixa] perf.spec só afirma o tempo de abertura; não falharia com uma regressão de rolagem ou memória
- **IDs:** UI-04 · **Área:** 10-UI (área)
- **Local:** `frontend/e2e/perf.spec.ts:112-113`
- **Reproduzir:** Ler as asserções finais
- **Evidência:** expect(result.messages).toBeGreaterThanOrEqual(TOTAL)
  expect(openMs).toBeLessThan(5_000)
- **Correção sugerida:** Limitar longestTaskMs, domNodes e scrollToFirstMs.

### A87. [Baixa] Nenhum teste prova a remoção das setinhas do type=number (mutação sobrevive)
- **IDs:** UI-05 · **Área:** 10-UI (verificação)
- **Local:** `frontend/src/index.css:235-242; frontend/e2e/native-controls.spec.ts:20-110`
- **Reproduzir:** Remover `input[type="number"] { appearance: textfield; }` de index.css, construir o SPA e rodar native-controls.spec.ts --project=desktop-firefox.
- **Evidência:** Com a mutação: '1 passed (1.4m)'. A captura mut-m5/.../light-admin.png mostra as setinhas nativas nos 4 campos do admin.
- **Correção sugerida:** Em nativeAppearance, exigir appearance textfield ou none em input[type=number] e none em input[type=search] visíveis.

### A88. [Baixa] tabpanel focável sem indicador de foco visível
- **IDs:** AR-01 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/src/components/ui/tabs.tsx (TabsContent com outline-none, tabindex=0 do Base UI)`
- **Reproduzir:** Em '/', dar Tab até depois da aba 'Conversas'
- **Evidência:** div[tabpanel]:TodasNão lidas... [SEM FOCO VISÍVEL]; style "none 0px oklch(0.708 0 0) | none"
- **Correção sugerida:** Dar focus-visible:ring ao TabsContent ou tirar o painel da ordem de Tab quando ele tem conteúdo focável

### A89. [Baixa] Sem env(safe-area-inset-left/right): no celular deitado com notch o conteúdo fica sob o recorte
- **IDs:** AR-02 · **Área:** 11-AR-IN (área)
- **Local:** `frontend/src (grep); index.html:7 viewport-fit=cover`
- **Reproduzir:** grep -rn 'safe-area-inset-left\|safe-area-inset-right' frontend/src
- **Evidência:** count=0
- **Correção sugerida:** Aplicar padding-left/right com max(…, env(safe-area-inset-left/right)) no shell

### A90. [Baixa] Mensagens de validação vêm do bundle do Hibernate Validator, não dos messages*.properties
- **IDs:** IN-01 · **Área:** 11-AR-IN (área)
- **Local:** `backend/.../shared/error/GlobalExceptionHandler.java:80-83`
- **Reproduzir:** POST /api/v1/auth/register com username vazio, em pt-BR e em en
- **Evidência:** "violations":[{"field":"username","message":"não deve estar em branco"}...] / en: "must not be blank"
- **Correção sugerida:** Definir as mensagens das constraints em ValidationMessages/messages*.properties do projeto

### A91. [Baixa] Módulos conversation e message do PROMPT/ADR 0005 viraram um único messaging sem ADR
- **IDs:** §6 (ADR 0005) · **Área:** 12-PK-QA-geral (área)
- **Local:** `docs/adr/0005-monolito-modular-spring-modulith.md:17; backend/src/main/java/io/github/labdev2025cmd/papinho/messaging/`
- **Reproduzir:** ls backend/src/main/java/io/github/labdev2025cmd/papinho/
- **Evidência:** admin backup contact identity linkpreview media messaging moderation notification pki presence privacy profile shared transcription
- **Correção sugerida:** Separar os módulos ou atualizar o ADR 0005 com a decisão e o motivo.

### A92. [Baixa] Build dev/nightly do onnxruntime-web como dependência direta, sem ADR
- **IDs:** §1 · **Área:** 12-PK-QA-geral (área)
- **Local:** `frontend/package.json:39`
- **Reproduzir:** Ler a versão do onnxruntime-web em frontend/package.json
- **Evidência:** "onnxruntime-web": "1.31.0-dev.20260914-8d85527a0"
- **Correção sugerida:** Usar uma versão estável compatível ou registrar em ADR por que o build dev (fixado pelo Transformers.js 4.3.1) é aceito.

### A93. [Baixa] 52 de 173 commits do agente passam do teto de 72 caracteres do COMMITS.md
- **IDs:** §4 · **Área:** 12-PK-QA-geral (área)
- **Local:** `COMMITS.md:93`
- **Reproduzir:** Listar os commits pela API do GitHub e contar o tamanho Unicode do assunto
- **Evidência:** total 226 >72 52 por Claude 52; o maior tem 105 caracteres: 'docs: registra o ADR 0014, a lista, busca, notificações e offline no README e os aprendizados da Fase 6'
- **Correção sugerida:** Validar o tamanho no commit-msg (commitlint header-max-length 72) para os próximos commits; não reescrever o histórico já enviado.

### A94. [Baixa] user_credentials usa timestamp sem fuso e a JVM não força UTC
- **IDs:** §0 · **Área:** 12-PK-QA-geral (área)
- **Local:** `backend/src/main/resources/db/migration/identity/V202610151800__create_webauthn.sql`
- **Reproduzir:** select table_name, column_name, data_type from information_schema.columns where data_type='timestamp without time zone'
- **Evidência:** user_credentials.created timestamp without time zone; user_credentials.last_used timestamp without time zone
- **Correção sugerida:** Migrar para timestamptz ou fixar -Duser.timezone=UTC/TimeZone.setDefault(UTC) no main.

### A95. [Baixa] Componente oficial attachment do shadcn não é usado
- **IDs:** §6 · **Área:** 12-PK-QA-geral (área)
- **Local:** `frontend/src/components/ui/ (sem attachment.tsx)`
- **Reproduzir:** ls frontend/src/components/ui; grep -rl 'components/ui/attachment' frontend/src
- **Evidência:** attachment: 0 (message-scroller, message, bubble e marker: 1 cada)
- **Correção sugerida:** Adotar o attachment (pnpm dlx shadcn@latest add attachment) nos anexos ou registrar o motivo.

### A96. [Baixa] pki init recusa IP como hostname sem explicar o motivo (passkeys/WebAuthn)
- **IDs:** PK-05, PK-01 · **Área:** 12-PK-QA-geral (verificação)
- **Local:** `backend/src/main/java/io/github/labdev2025cmd/papinho/pki/PkiCommand.java:201-208`
- **Reproduzir:** PAPINHO_PKI_DIR=/tmp/x java -jar papinho.jar pki init --hostname 192.168.0.10 --ip 192.168.0.10 --network 192.168.0.0/24
- **Evidência:** 'Erro: hostname inv?lido: 192.168.0.10' (com locale POSIX os acentos também viram '?')
- **Correção sugerida:** Dizer na mensagem que é preciso um nome (por exemplo papinho.lan), porque o WebAuthn não aceita IP como RP ID, e apontar a seção 'Hostname na rede' do README.


## 6. Divergências do STATUS.md

Além dos 25 itens com veredito STATUS INCORRETO na matriz:

- **(linha de base) E2E:** o STATUS alega 79 verdes e 0 falhas.
  - Nesta sessão foram 77 verdes, 2 falhas e 8 pulados. As falhas são `view-once.spec.ts:75`, que falha 6 de 6 com o Chromium 1194 local.
  - No CI #65 houve 1 flaky (Firefox `native-controls`) escondido por `retries: 1`.
- **(linha de base)** "OutsiderAccessIT (41 ações)" conta como 1 método de teste.
- (01-ID-AD) ID-01: o STATUS diz 'UUID v7' como feito, mas media_uploads.id e scheduled_messages.id são v4 (consulta executada) e as chaves de arquivo de perfil também. A 'unicidade garantida por constraint' existe, mas nenhum teste a garante (a M1 sobrevive), e numa corrida o cliente recebe 500.
- (01-ID-AD) ID-08: o STATUS cita 'IdentityIT com a trava presa por outra conexão, nos dois sentidos' como prova contra dois admins. O teste usa um cadastrante só, e a mutação que remove a recontagem depois da trava passa em toda a IdentityIT.
- (01-ID-AD) ID-07/AD-02: o STATUS diz que os padrões seguros vêm da migration, e é verdade no banco, mas o DatabaseCleaner.java:20 os reescreve antes de cada teste. A mutação do padrão para OPEN não é pega.
- (01-ID-AD) ID-07: o JavaDoc de InviteService.java:99 ('o convite reabre a porta para uma pessoa') não condiz com o comportamento: no modo CLOSED, o cadastro com convite do admin dá 403.
- (02-CT-BK) CT-02: o STATUS cita 'RealtimeIT presença só após aceitar', mas nenhum teste de RealtimeIT usa conversa em REQUEST (todos fazem o destinatário adicionar o remetente antes). A mutação M1 sobreviveu.
- (02-CT-BK) CT-02: a leitura escondida só é testada no REST peerReadSeq; por STOMP e em Dados da mensagem não há teste (M3 e M4 sobreviveram).
- (02-CT-BK) CT-03: o STATUS lista 'presença filtrada por bloqueio' como evidência, mas não há teste; a mutação M8 sobreviveu.
- (02-CT-BK) CT-04: 'copia as 5 últimas' só vale sem avisos do sistema ou mensagens apagadas entre elas; em caixa-preta saíram 2 cópias.
- (02-CT-BK) BK-02: 'ZIP com dados.json de cada módulo e os arquivos' não inclui a foto de perfil.
- (02-CT-BK) Linha de base: o worktree começou em 668c666 (commit inicial), não em 3f4cb6d; fiz checkout --detach 3f4cb6d antes de auditar.
- (03-MS-a) MS-01: o STATUS cita parse.test.ts ('HTML vira texto') como prova da sanitização, mas esse teste cobre só o parser. Trocar o render por dangerouslySetInnerHTML não quebra nenhum teste (86/86 passam).
- (03-MS-a) MS-04: o STATUS diz 'e2e responder com salto', mas chat.spec.ts:115-117 só confere que o botão existe. No navegador, o salto deixa a original 449 px acima da área visível quando ela está longe.
- (03-MS-a) MS-07: o STATUS diz 'job com ShedLock', mas nenhum teste exercita o ShedLock (a mutação M7 passa). A trava funciona na caixa-preta (linha na tabela shedlock).
- (03-MS-a) MS-06/MS-07: 'arquivo 404' e 'job apaga arquivos' são conferidos só pela API. A remoção do disco é coberta apenas pelo MediaIT. Na caixa-preta, o disco é limpo de fato.
- (03-MS-a) Worktree recebido em 668c666 ('Commit inicial', sem STATUS.md). Foi movido para 3f4cb6d (detached) antes da auditoria.
- (04-MS-b) MS-13: o STATUS diz 'Ctrl+V cola arquivo e arrastar e soltar vêm da Fase 3 (media.spec.ts)'. Não há teste de paste nem de drop em frontend/e2e. O comportamento funciona, como mostrou o spec temporário da auditoria.
- (04-MS-b) MS-08: 'feito' com 'token de uso único', mas o token aceita várias GETs por 5 min (segunda GET -> 200). O próprio STATUS declara o desvio, com base no ADR 0012.
- (04-MS-b) MS-08: o STATUS cita view-once.spec.ts (desfoque, passada 8) como evidência, mas esse spec falha sempre neste ambiente na linha 75 (aqui 1/1; na linha de base 6/6).
- (05-MD-AU) MD-04 marcado 'feito', mas o requisito pede limitar o TEMPO e não há limite total: a prévia em gotejamento durou 120,04 s (sem teto), e 10 prévias derrubaram a instância (pool Hikari esgotado). Veredito real: PARCIAL.
- (05-MD-AU) AU-03 marcado 'feito' com 'áudio de visualização única nunca é transcrito', mas o dono pode transcrever antes de enviar e o texto fica na tabela transcripts para a mídia view_once. Veredito real: PARCIAL.
- (05-MD-AU) AU-03: o STATUS cita o e2e da transcrição automática como evidência, mas nele o POST /transcript é simulado por route.fulfill (audio.spec.ts:47-53); o cache no servidor só é provado pelo TranscriptionIT.
- (05-MD-AU) MD-04: o STATUS cita LinkPreviewIT '127.x sem prévia', mas nesse IT o loopback está liberado (test-allowed-ports) e a asserção falhou quando havia um servidor em 127.0.0.1:80.
- (05-MD-AU) AU-04: o STATUS fala em ~3,7 min no WebGPU; aqui levou 6,2 min e numa tentativa estourou os 8 min sob carga. Não é defeito de produto, mas o teste depende do ambiente.
- (06-GR) GR-06: o STATUS cita e2e de filtros "todas/favoritas/grupos/lista/arquivadas". O filtro "Não lidas", exigido pelo requisito, não tem teste (nem e2e nem Vitest), e os filtros de remetente, data e tipo e a navegação só têm teste no servidor.
- (06-GR) GR-06: a migração V202610190200 diz que "mensagens" acha "mensagem". No banco a correspondência dá f e na API 'mensagem' -> [].
- (06-GR) GR-01: o STATUS diz que GroupIT cobre as permissões, mas a permissão "quem adiciona membros" não tem teste (a mutação sobreviveu).
- (06-GR) O worktree começou no commit 668c666 (o commit inicial), não no HEAD 3f4cb6d; fiz checkout --detach 3f4cb6d antes de auditar.
- (07-PV) PV-01 feito: os interruptores não são todos independentes (mídia dentro do balão de mensagens) e o e2e só exercita o item 'messages'.
- (07-PV) PV-02 feito: a UI cria o bloqueio com o atalho Alt+Shift+L já ligado, contra 'Tudo desativado por padrão'.
- (07-PV) PV-03 feito: agendadas, citação em replyTo e exportação da conta (mais anexos por id) devolvem conteúdo sem token. A curta duração do token não tem teste (mutante sobreviveu), embora na caixa-preta ele tenha expirado em 10 min.
- (07-PV) PV-04/PV-05 feito: o online escondido vaza pelo STOMP; a evidência 'RealtimeIT.shouldHideOnlineByPrivacy' testa só o conteúdo do evento, não a existência dele.
- (07-PV) PV-05/PV-06: a reciprocidade nos eventos STOMP de leitura e no 'Dados da mensagem', e o 'grupo sempre enviada' nos caminhos Java, não são provados pelos testes citados (mutantes sobreviveram).
- (07-PV) STATUS PV-03 diz que as notificações sem conteúdo ficam em NotificationIT: confirmado; a mutação mata o teste.
- (08-NT-RT) RT-01 'feito': o seq é monotônico, mas o afterSeq perde mensagens com envios concorrentes (11/300). A alegação de 'recebe o que perdeu' não se sustenta.
- (08-NT-RT) NT-05 (lacunas) 'trancadas nunca guardadas': falso. Trancar uma conversa já guardada deixa messages:<id> e detail:<id> com o texto no IndexedDB. PV-03 diz 'NT-05 não pode guardar trancadas'.
- (08-NT-RT) NT-01 'e2e pwa.spec (permissão só pelo botão)': o pwa.spec concede a permissão com grantPermissions e não prova isso. A prova veio do e2e desta auditoria.
- (08-NT-RT) MessagingIT.shouldSendConcurrently... confere só a ausência de duplicatas, não de buracos. A contiguidade foi provada aqui pelo banco (41\|41\|1\|41\|41).
- (09-SG) SG-01: o STATUS diz 'Problem Details em 401/403/400/500 (SecurityIT)'. O SecurityIT não tem nenhum caso de 500, e trocar o detail do 500 pelo stack trace não falha nenhum teste (mut-B.log).
- (09-SG) SG-01: o STATUS diz 'WebSocket só assina a própria fila (WebSocketSecurityConfig)' como evidência, mas nenhum teste prova isso: a mutação simpSubscribeDestMatchers("/**") passou 36/36. Na caixa-preta o comportamento está correto.
- (09-SG) SG-01: 'Problem Details em todos os erros' não vale para o StrictHttpFirewall (JSON padrão do Boot), os métodos FOO e TRACE nem os 400 do Tomcat (HTML).
- (09-SG) SG-02: a evidência 'SecurityIT.shouldServeSpaShellWithSecurityHeaders' só confere a AUSÊNCIA de HSTS. 'HSTS em produção' não tem teste; ligá-lo depende de PAPINHO_HSTS=true (padrão false).
- (09-SG) SG-02: o AGENTS.md diz 'script-src 'self'', mas a CSP real é "script-src 'self' 'wasm-unsafe-eval'" (justificado no ADR 0008).
- (09-SG) SG-03: o STATUS cita 'convites e links de grupo (60/h)', mas não há teste de integração de 429 para convites. A mutação do InviteService não derrubou nenhum teste (o comportamento existe: 30×201 e depois 429).
- (09-SG) SG-03: o '120/min' do envio aceitou 125 mensagens seguidas em série, por causa da recarga greedy. Em paralelo, 55×201 e depois 429.
- (10-UI) UI-03 'feito': as telas principais (lista, conversa, contatos) mostram só um erro genérico, sem tentar de novo e sem os estados sem conexão e sem permissão. Medido com ui-states2.mjs: 403, offline e 500 produzem o mesmo texto.
- (10-UI) UI-04 'feito': com 5000 mensagens não se sustenta como performática (885 MB de heap, tarefa longa de 1,87 s, 81 s até a primeira, custo por página crescente). O STATUS mediu só 2000.
- (10-UI) UI-03 (passada 8) diz que os botões que esperavam a mutação mostram o erro num toast, mas há ~20 chamadas .mutate() sem onError. Favoritar com 409 não mostra nada (ui-silent.mjs).
- (10-UI) UI-05: confere com o STATUS (68/68/66 capturas, verde nos 3 motores). Porém o run #65 do CI teve flaky no Firefox nesse spec, escondido por retries:1, e o STATUS não registra isso.
- (11-AR-IN) AR-01 'feito': o axe acha violação serious (label-content-name-mismatch) na aba Contatos, que o a11y.spec.ts não visita
- (11-AR-IN) AR-02: o STATUS lista 'alvos de 44 px em pointer: coarse (index.css)' como feito, mas os botões de ícone com Tooltip/Dropdown medem 32 px e as ações da mensagem e o Fechar dos diálogos 28 px. O bloqueio não é só de aparelho real
- (11-AR-IN) AR-02: o teste de 320 px só mede documentElement; o /admin rola dentro do main (383>320)
- (11-AR-IN) AR-03 'feito' com 'axe sem críticas/sérias em todos os specs': falso para a aba Contatos. O teste de teclado usa focus() programático no composer e no menu
- (11-AR-IN) IN-01 'feito': o i18n.test.ts só compara os conjuntos de chaves; texto fixo ou chave apagada nos dois idiomas não falham. A contagem de 747 chaves literais não confere com a minha varredura (771 chaves literais usadas, 927 no total); ainda assim todas existem
- (12-PK-QA-geral) PK-04 'feito' com 'impressão digital errada aborta sem instalar': a recusa existe para uma CA trocada, mas é contornável com um bloco TRUSTED CERTIFICATE anexado, e nenhum teste (LanSetupIT nem verify-install-ca.sh) cobre a recusa; a mutação sobreviveu.
- (12-PK-QA-geral) QA-01 'feito' com 'unitários, slice, integração': não existe nenhum teste slice no backend.
- (12-PK-QA-geral) QA-05: o README garante que o install-ca.sh confere a impressão digital antes de instalar, e isso é contornável.
- (12-PK-QA-geral) §6: ADR 0006 (S3) e ADR 0005 (conversation/message) não batem com o código, e o STATUS não registra isso.
- (12-PK-QA-geral) QA-01: na linha de base, failsafe deu 135 testes em 41 classes, mas só 30 arquivos *IT.java (diferença provavelmente de classes aninhadas; não afeta o veredito).

## 7. Testes frágeis ou vazios e resultado das mutações

Os detalhes de cada mutação (o que foi quebrado, o comando e a saída) estão na coluna "Mutação" da matriz (seção 3). Abaixo, a lista consolidada dos testes que não protegem o que alegam:

- (01-ID-AD) IdentityIT.shouldSerializeFirstRegistration: o DisplayName diz 'dois ao mesmo tempo não viram admin', mas o teste só tem um cadastrante. Não falhou com a recontagem depois da trava removida (M6b).
- (01-ID-AD) IdentityIT.shouldEnforceUsernameRules e o resto da suíte: nenhum teste prova a constraint UNIQUE do banco. Não falhou com uq_user_accounts_username removida (M1).
- (01-ID-AD) IdentityIT.shouldRequireInviteAfterFirstUser: depende do DatabaseCleaner, que força INVITE_ONLY. Não falhou com o padrão da migration trocado para OPEN (M5b).
- (01-ID-AD) Nenhum teste mede a igualdade de tempo do login (dummyHash em IdentityService.java:144); só a minha medição de caixa-preta (32.0 contra 33.2 ms).
- (02-CT-BK) RealtimeIT: todos os casos montam a conversa já ACTIVE dos dois lados; não exercitam CT-02 (presença/leitura antes de aceitar) nem CT-03 (presença com bloqueio). M1, M3 e M8 sobreviveram.
- (02-CT-BK) MessagingIT.shouldHandleMessageRequests: afirma só peerReadSeq no REST; não pega o vazamento por STOMP nem em /messages/{id}/info (M3 e M4 sobreviveram).
- (02-CT-BK) ModerationIT.shouldReportWithLastMessagesAndBlock: só 7 mensagens de texto seguidas; não pega a perda de cópias por avisos do sistema ou apagadas.
- (02-CT-BK) AccountDataIT.shouldExportPersonalData: não verifica a foto de perfil no ZIP.
- (03-MS-a) parse.test.ts 'texto com HTML continua texto': não protege o render (mutação MS-01 não detectada)
- (03-MS-a) chat.spec.ts:115-117: confere só a visibilidade do botão do salto, não que a original aparece
- (03-MS-a) DisappearingIT: chama job.purge() direto; não pega a remoção do @SchedulerLock (M7) nem a do arquivo em disco (M5)
- (03-MS-a) MessagingIT.shouldEditAndDeleteForEveryone:298: 'arquivo removido do servidor' confere só o GET 404; passa sem storage.delete (M5)
- (04-MS-b) frontend/e2e/view-once.spec.ts:75: expect(await filter()).toBe('none') sem poll e sem devolver o foco; falha sempre com o Chromium 1194.
- (04-MS-b) ViewOnceIT.shouldValidateAndExpire: a parte dos 14 dias só confere 410, não a remoção do arquivo. A mutação do prazo ainda falha pelo 410, mas a remoção do arquivo depois de 14 dias fica sem asserção.
- (04-MS-b) StickerIT: só WebP estático; nada prova que a figurinha animada funciona.
- (05-MD-AU) LinkPreviewIT.shouldRefusePrivateTargets: a URL http://127.0.0.1/admin só fica 'sem prévia' se nada escutar em 127.0.0.1:80, porque o IT libera loopback; falhou com um servidor local na porta 80.
- (05-MD-AU) Nenhum teste cobre o tempo total da busca de prévia (gotejamento) nem a conexão do banco presa durante o fetch.
- (05-MD-AU) ViewOnceIT:111-112: removendo só MediaAccessPolicy.restricted, a asserção de transcrição continua verde (a barreira attachmentVisibleTo segura); só falha com as duas removidas. Nenhum teste cobre a transcrição criada antes de o anexo virar view-once.
- (05-MD-AU) audio.spec.ts: a transcrição (manual e automática) usa resposta simulada por route.fulfill; não exercita o servidor nem o cache.
- (05-MD-AU) whisper.spec.ts no whisper-webgpu: timeout de 8 min estourou uma vez com load average 14; depende da carga.
- (06-GR) GroupIT.shouldManagePermissionsAndMembers: liga onlyAdminsAddMembers e nunca o exercita; remover a checagem em GroupService.java:192 não falha nenhum teste.
- (06-GR) OutsiderAccessIT 'grupo: adicionar': testa só quem é de fora (404 por requireActive) e não pega a falta da permissão de membro.
- (06-GR) SearchIT: incluir LEFT, PENDING e DECLINED na busca global (MessageSearch.java:44) não falha.
- (06-GR) GroupIT.shouldJoinByInviteLink: o QR só é verificado com startsWith("<svg"); nesta sessão o conteúdo foi decodificado à parte com zxing.
- (06-GR) SearchIT 'radical em português' só testa conversa/conversas; não pega o caso -ão/-ões nem mensagem/mensagens.
- (07-PV) PrivacyIT.shouldLockConversations: não pega a mudança de IDLE e MAX_LIFETIME para 3650 dias (ConversationLocks.java:45-46).
- (07-PV) Nenhum teste falha com publishReceipts mandando readSeq a quem não compartilha (ConversationService.java:400); PrivacyIT, RealtimeIT, MessagingIT e GroupIT passaram.
- (07-PV) Nenhum teste falha com MessageService.info sem a checagem iShare (MessageService.java:502).
- (07-PV) Nenhum teste falha com shares() fazendo o grupo seguir o global (ConversationService.java:272).
- (07-PV) RealtimeIT.shouldHideOnlineByPrivacy: não detecta o evento de presença enviado a quem não deve ver nada.
- (07-PV) e2e privacy.spec.ts:75-150: testa só o item 'messages' do PV-01.
- (08-NT-RT) RealtimeIT.shouldTrackPresence: não cobre a expiração por heartbeat. A mutação E (ignorar o tempo) passou 13/13. Linhas 158-160 dependem de o frame STOMP ser processado antes do GET.
- (08-NT-RT) MessagingIT.shouldSendConcurrentlyWithoutExhaustingConnections: confere doesNotHaveDuplicates, não a sequência 1..30. A detecção da mutação A dependeu da constraint do banco.
- (08-NT-RT) pwa.spec.ts 'notificações...': grantPermissions antes, por isso não prova a permissão só após o clique.
- (08-NT-RT) pwa.spec.ts offline: não testa trancar depois de guardar, nem a troca de conta, nem o reenvio da fila após uma resposta perdida (mesmo clientMessageId já gravado).
- (08-NT-RT) Nenhum teste (unitário, IT ou e2e) de sync concorrente com afterSeq: o bug de perda de mensagens passa com a suíte verde.
- (09-SG) SecurityIT.shouldServeSpaShellWithSecurityHeaders: confere a CSP com contains parciais e não olha Permissions-Policy, CORP, object-src, base-uri nem form-action. Não falhou com essas mutações e só testa HSTS ausente.
- (09-SG) SecurityIT.shouldDenyUnknownRequests: aceitou a troca de denyAll por permitAll, porque o CSRF devolve 403 do mesmo jeito. Só shouldProtectSensitiveActuatorEndpoints pegou a mutação, e por tabela.
- (09-SG) RealtimeIT: não testa Origin, destino de SUBSCRIBE nem SEND em conversa alheia. As três mutações passaram.
- (09-SG) ProblemDetailsIT: não cobre o 500 e passou com o stack trace no detail.
- (09-SG) IdentityIT, EmailRecoveryIT e SecurityIT: passaram com log.info da senha e do usuário em claro no login. Não existe guarda de dado pessoal no log de autenticação.
- (09-SG) Não há teste de 429 para a criação ou verificação de convites (InviteService:53/88).
- (10-UI) frontend/e2e/perf.spec.ts: opcional (PAPINHO_E2E_PERF) e só afirma openMs<5000; o filtro hasText com espaço no fim é aparado pelo Playwright ('perf #1 ' casa com 'perf #1951'), o que pode encerrar a rolagem cedo.
- (10-UI) frontend/e2e/native-controls.spec.ts: para na primeira tela com problema (a mutação do color-scheme do .dark ficou encoberta pela do vídeo); pulado no celular; não verifica search-cancel nem as setas do number em runtime.
- (10-UI) frontend/e2e/a11y.spec.ts:240 (breakpoints): só no desktop-chromium; falhou na mutação, portanto não é vazio.
- (10-UI) frontend/e2e/states.spec.ts: cobre só blocked, sessions e admin, não as telas principais.
- (11-AR-IN) frontend/e2e/a11y.spec.ts:101 'fluxo completo só com o teclado': field.focus() (l.141) e actions.focus() (l.154); passou com o composer fora da ordem de Tab (mutação)
- (11-AR-IN) frontend/e2e/a11y.spec.ts:164-177: checa o foco visível de um único elemento
- (11-AR-IN) frontend/e2e/fixtures.ts:107-114 expectNoHorizontalScroll: ignora rolagem horizontal de contêiner interno (/admin main 383>320 passa)
- (11-AR-IN) frontend/e2e/a11y.spec.ts:19-29 SCREENS: não inclui a aba Contatos nem os diálogos; o axe claro só roda no teste de aparência
- (11-AR-IN) frontend/src/lib/i18n.test.ts: só a paridade de chaves; nada contra texto fixo ou chave sumida nos dois arquivos
- (12-PK-QA-geral) LanSetupIT.shouldServeInstallScriptWithFingerprint só confere o texto do script (contains EXPECTED_FINGERPRINT); passa com a checagem da impressão digital removida.
- (12-PK-QA-geral) scripts/verify-install-ca.sh não tem caso negativo (CA adulterada), e o job Debian depende de apt com internet direta.
- (12-PK-QA-geral) CertificateAuthorityTest: os 3 testes de openssl são pulados (Assumptions) sem o binário openssl, e aí a prova das Name Constraints fica só estrutural.
- (12-PK-QA-geral) Na linha de base, o CI com retries: 1 escondeu um flaky (desktop-firefox native-controls), e view-once.spec.ts:75 falha 6/6 com o Chromium 1194 local (asserção sem poll).

## 8. Vereditos refutados na verificação adversarial

Foram reexaminados 37 CUMPRIDOs: todos os críticos de PV, SG e RT, os principais de MS, ID, GR e PK e uma amostra dos demais. 9 foram rebaixados:

- **MS-02** (03-MS-a): CUMPRIDO → **PARCIAL**. Na caixa-preta o produto se comporta como pede o requisito. t1.sh: a bia, em REQUEST, marca read seq 1 e o info da ana devolve `"readAt":null`, com `{"peerDeliveredSeq":1,"peerReadSeq":null}`; depois do aceite vem readAt preenchido. Com a confirmação da ana desligada na conversa, o info dá `"readAt":null`; com o padrão (null) ele volta. t2.sh (STOMP cru com Node): com a ana desligada chega `"deliveredSeq":5,"readSeq":0`; ligada, chega `"readSeq":6`. O e2e chat.spec passou (Lida e diálogo Dados da mensagem). A regra 3, porém, não foi satisfeita. As mutações a seguir na regra 7.7 que o MS-02 tem de respeitar SOBREVIVEM aos testes. (mA) ConversationService.java:398-399 `long readSeq = member.readSeq();` (leitura de quem não compartilha, ou ainda em REQUEST, sai pelo STOMP): a suíte IT inteira passa, `Tests run: 135, Failures: 0`. (mC1) MessageService.java:502 sem `m.state() == MemberState.ACTIVE` (CT-02 no painel): Privacy/Messaging/Group/ContactIT `Tests run: 25, Failures: 0`. (mC2) MessageService.java:502 sem `iShare` (PV-05: eu desliguei e ainda vejo a leitura dos outros): `Tests run: 22, Failures: 0`. (mD) ConversationQueries.java:58 `coalesce(min(o.read_seq), 0)` sem o filtro read_receipts ('lida' no grupo inclui quem desligou): `Tests run: 27, Failures: 0`. Só mB (toView peerReadSeq sem reciprocidade) foi pega: `PrivacyIT.shouldApplyReadReceiptRules ... Expecting value to be true but was false`. O 'Enviando' só existe no cliente e eu não o reexecutei; o e2e chat.spec:81 aceita `/^(Enviada\|Entregue)$/` e não prova 'Entregue' isoladamente. Todas as mutações foram desfeitas e o diff ficou vazio. O que falta: testes de integração para recibos via STOMP com reciprocidade e CT-02, para o painel com o próprio desligado ou em REQUEST e para o 'lida' do grupo sem quem desligou.
- **MS-06** (03-MS-a): CUMPRIDO → **PARCIAL**. O caminho principal está provado. t3.sh: com 47 h, `"deleted":true HTTP=200`; com 49 h, `"status":409`; a bia apaga para todos a mensagem da ana e recebe HTTP=403; o apagar para mim da bia some do histórico e do sync com afterSeq=0 dela (`contém? 0`) e continua para a ana (`1`). Mutações: (mF2) delete sem autor, `MessagingIT.java:281 expected: 403`; (mG) sem media.delete, `MessagingIT.java:299 expected: 404`; (mH) hide sem hidden.hide, `MessagingIT.shouldHideForMe:338`; (mI) sem janela, `:307 expected: 409`. Todas foram desfeitas. O e2e messages.spec passou ('Esta mensagem foi apagada' nos dois lados). Há, porém, um caminho alternativo que viola 'o conteúdo é removido do servidor'. t1.sh T3: a ana envia `olha https://link-secreto-da-ana.invalid/caminho-privado-123`, o cliente da bia pede `GET /api/v1/link-previews?url=...` e a ana apaga para todos (`{"deleted":true,"body":""}`). A URL continua no banco: `select url... from link_previews` dá `https://link-secreto-da-ana.invalid/caminho-privado-123\|f\|2026-10-17 01:43:48`, e o `pg_dump --data-only \| grep -c caminho-privado-123` dá `1`. LinkPreviewService não tem nenhum delete ou purge (grep sem resultado), então a URL e, se a busca der certo, o título, a descrição e a imagem da página ficam para sempre. A remoção física do arquivo em disco continua sem teste próprio do MS-06 (o primeiro auditor viu isso, M5).
- **MD-02** (05-MD-AU): CUMPRIDO → **PARCIAL**. A remoção de EXIF só vale para JPEG/PNG/WebP de até 50 MB. UploadService.java:202-204: 'if (kind == MediaKind.IMAGE && Files.size(file) > MAX_PROCESSED_IMAGE) kind = MediaKind.DOCUMENT' (MAX_PROCESSED_IMAGE=50 MB em :49). ContentTypes.IMAGES (:12) não inclui HEIC/HEIF/AVIF. Caixa-preta: um JPEG 400x400 com GPS chega à bia '(sem GPS/Make)', IMAGE, thumb true. Um JPEG 3700x3700 de 56347981 B com GPS vira '{"k":"DOCUMENT","ct":"image/jpeg","thumb":false}' e a bia baixa 56347981 B com 'exif:GPSLatitude: 3/1,43/1,5/1', 'exif:GPSLongitude: 38/1,31/1,0/1', 'exif:Make: CameraAuditoria'. Um arquivo com assinatura ftyp heic vira '{"kind":"DOCUMENT","contentType":"image/heic","hasThumbnail":false}' e é baixado 'byte a byte igual ao original' (o EXIF de um HEIC real iria junto). A foto de 56 MB também sai da aba Mídia e não ganha miniatura nem thumbhash. Mutação (PhotoPipeline jpeg -> bytes originais) -> FALHA MediaIT.shouldDetectTypeAndStripExif:170; revertido. A legenda e o visualizador (Próxima imagem, zoom, Esc) passaram no e2e media.spec.ts (3 passed).
- **AU-05** (05-MD-AU): CUMPRIDO → **PARCIAL**. Caixa-preta com provedor falso em 127.0.0.1:18158: '"path": "/v1/audio/transcriptions", "ua": "Papinho/0.1.0-SNAPSHOT", "auth": "Bearer sk-VERIF05-SEGREDO-91c2", "ctype": "multipart/form-data; boundary=" ... "hotwords": "Papinho, Fortaleza"'; saúde 'GET /health' -> {"healthy":true}; GET admin e options sem a chave (grep 0); banco 'WHISPER\|v1:2bPjOiKipeGcXB74OZb2n2yXi3e\|71', pg_dump grep 0, papinho.log grep 0; padrões GROQ api.groq.com/openai, whisper-large-v3-turbo, 26214400; OPENAI api.openai.com; bia GET admin 403. Mutação UA 'OpenAI/Python' -> FALHA TranscriptionIT:121. MAS a mutação que grava a chave EM TEXTO PURO (TranscriptionService:115 e :165 sem cipher.encrypt/decrypt) SOBREVIVE: 'Tests run: 2, Failures: 0 ... BUILD SUCCESS'. Nenhum teste cobre 'cifradas em repouso' (grep api_key_encrypted/SecretCipher em backend/src/test: nada). Achados novos: (1) dois pedidos de transcrição de um WAV de 1 GB com o Whisper próprio (limite padrão do tipo: 1073741824) -> 'java.lang.OutOfMemoryError: Java heap space', RSS 3,9 GB, porque OpenAiCompatibleClient.java:59 copia o áudio inteiro num ByteArrayOutputStream; (2) trocar a baseUrl sem reenviar a chave manda o Bearer guardado para o novo destino. Groq/OpenAI reais não testados (sem chave). Faltam teste de cifragem em repouso e o envio em streaming com limite de memória.
- **GR-05** (06-GR): CUMPRIDO → **STATUS INCORRETO**. STATUS.md:202 diz 'feito' e 'fixar até 3 com 409'. O limite pode ser ultrapassado: ChatListService.java:71-75 só conta pinned_at com state in ('ACTIVE','REQUEST'), e GroupService.leave (:258-266) e ConversationMember.rejoin (:106-113) não limpam pinned_at. Caixa-preta s5.out: 'bia pin G: HTTP 204 G1: HTTP 204 G2: HTTP 204' / 'bia pin G3 (4a): HTTP 409' / 'bia sai de G: HTTP 204' / 'bia pin G3: HTTP 204' / 'bia volta pelo link: {..."pending":false}' / 'fixadas da bia na lista: 4' (banco: 4). O veredito real é PARCIAL: arquivar com 'manter arquivadas', silenciar 8h/1 semana/sempre com menções, favoritar, pastas, limpar e apagar funcionam. Mutações refeitas: MAX_PINNED=4 -> ChatListIT:84 'expected: 409'; keep_archived falso -> ChatListIT:60 '[manter arquivadas]'; dispatcher sem a exceção de menção -> NotificationIT:168 ConditionTimeout. Mutações novas que sobreviveram à suíte IT inteira (135/0): ONE_WEEK=8 dias e apagar conversa sem tirá-la das pastas. Pela caixa-preta (s4.out) as duas regras funcionam: ONE_WEEK -> '2026-10-24T01:56:10Z' (agora 2026-10-17T01:56:09Z); pasta antes 2 conversas, depois só o grupo. Também em s4.out: apagar grupo mantém ACTIVE, pasta com 41 caracteres 400, 21ª pasta 409, pasta alheia 404. e2e organize.spec.ts desktop e mobile passed, cobrindo silenciar 'Sempre' com 'Ainda notificar quando me mencionarem' (linha 65), ao contrário do que dizia a lacuna do outro auditor. Nenhum e2e apaga uma conversa.
- **PV-07** (07-PV): CUMPRIDO → **PARCIAL**. Contexto: o worktree estava em 668c666 (só os guias). Fiz `git checkout --detach 3f4cb6d` no MEU worktree. Código: GroupService.java:422-431 (requireAddable chama privacy.canAddToGroups e lança GroupAddNotAllowedException) é chamado em create (l.100) e addMembers (l.203). PrivacyService.java:172-175 -> allows(target, GROUPS_ADD, adder), com os 4 casos em l.150-156. join por link (l.332) e decideJoin não passam pela regra, e é assim por desenho ('entra pelo link'). Base: PrivacyIT 7/0 e NotificationIT 7/0. Mutação M1 (requireAddable sem a checagem de privacidade): PrivacyIT#shouldRespectWhoCanAddToGroups FALHOU com 'expected: 403'. Desfeito. Mutação M2 (CONTACTS_EXCEPT ignora as exceções só para GROUPS_ADD em PrivacyService.allows): PrivacyIT 7/0 e GroupIT 5/0 PASSARAM. O mutante sobrevive: nenhum teste automatizado cobre a opção 'Meus contatos exceto…' de PV-07 (o único teste que fala de groupsAdd é PrivacyIT, e só com CONTACTS e NOBODY). Desfeito. Caixa-preta no jar de referência (porta 18175, Postgres audit-pgv-07-PV): com CONTACTS_EXCEPT[ana], POST /groups deu {"http":"403","type":"urn:papinho:problem:group-add-not-allowed","detail":"Esta pessoa não permite ser adicionada a grupos por você. Envie o link de convite do grupo."} e POST /groups/{id}/members deu HTTP=403. Sem exceção: 201. NOBODY: addMembers 403 e create 403, e o join pelo link deu 200. CONTACTS com a carla sem a ana nos contatos: 403. groupsAdd=FOO: 400. O padrão é EVERYONE (GET /privacy). O servidor se comporta certo nas 4 opções, mas falta a prova automatizada de CONTACTS_EXCEPT (o mutante sobrevive). A UI (privacy-settings.tsx:120-124 usa o mesmo seletor com item GROUPS_ADD) não foi renderizada nem testada por mim.
- **RT-03** (08-NT-RT): CUMPRIDO → **PARCIAL**. Comportamento ok no navegador. - O e2e próprio no Chromium 1194, contra uma instância vazia na porta 18186, registrou: 'AUDIT presença com aba visível: online' / 'AUDIT presença com aba oculta: não online' / 'AUDIT presença de volta: online'. - Com o heartbeat bloqueado e o WS aberto: 'AUDIT online sumiu sem heartbeat depois de 53 s; frames bloqueados: 2'. - Código: PresenceService.java:36 (STALE_AFTER 45 s), :74-78, :81-84 (sweep a cada 15 s), :121-128; realtime.ts:52 (20 s), :101-107, :152-156.  Por que rebaixei: refiz a mutação E, tirando de isOnline o `beat.at().isAfter(limit)`. RealtimeIT (6), PresenceIT (1) e PrivacyIT (7) passaram: 'Tests run: 14, Failures: 0', BUILD SUCCESS. - Não existe teste Vitest para realtime.ts. - A metade 'presença com heartbeat' (expiração) não tem nenhum teste no repositório que a proteja. Pela regra 3, essa parte está NÃO PROVADA. - A outra metade (aba em segundo plano) é coberta por RealtimeIT.shouldTrackPresence: o auditor fez a mutação D e ela foi detectada. - O STATUS cita RealtimeIT como evidência da presença, mas esse teste não cobre o heartbeat.
- **SG-04** (09-SG): CUMPRIDO → **PARCIAL**. Trabalhei num worktree próprio em detached 3f4cb6d. A base do worktree era o commit inicial 668c666, sem o código.  (1) Mutações que derrubaram teste: - requireReadable sem hidden -> PrivacyIT.shouldLockConversations:288 - list sem filtro de trancadas -> PrivacyIT:285 [trancadas saem da lista] - busca global sem hiddenLocked -> SearchIT.shouldSkipHiddenDeletedAndLocked:85 - NotificationRecipients sem trancada -> NotificationIT.shouldHideContent:150 - hideContent=false -> NotificationIT:140 - canSeePhoto=true -> PrivacyIT:94 - canAddToGroups ignorado -> PrivacyIT:209 - canSeePresence sem reciprocidade -> PrivacyIT:126 - seeOnline=true -> RealtimeIT.shouldHideOnlineByPrivacy:233 [Bruno escondeu o online] - info() sem sharers -> PrivacyIT:159 - peerReadSeq do grupo sem iShare -> PrivacyIT:190 - ScreenLockService sem rateLimiter.consume -> PrivacyIT:260 [força bruta limitada]  (2) Mutações que NÃO derrubaram teste nenhum: - ConversationService.publishReceipts (o evento STOMP 'receipts') com readSeq=member.readSeq() para todos, sem filtrar por sharers. Rodei PrivacyIT, RealtimeIT, MessagingIT, GroupIT e NotificationIT, e nenhum teste falhou ('Tests run: 36, Failures: 4', todas as 4 de outras mutações). O caminho PV-05/PV-06 pelo WebSocket não tem teste. - MessageService.starred() sem isVisible. PrivacyIT e MessagingIT passaram. - ExportIT também passou com requireMember em prepare(), mas isso não é lacuna: label() chama view(), que já dá 403.  (3) Caminhos alternativos com vazamento real, executados: - GET /conversations/{id}/scheduled numa conversa trancada, sem token, no jar de referência (porta 18195): '-> 200 [{..."body":"segredo agendado"...}]'. Na mesma sessão, /messages deu 403. - POST de mensagem com replyToId de mensagem da conversa trancada, sem token: 'PROBE replyLeak status=201 segredo=true'. O replyTo.body vem preenchido. - STOMP de presença com online=NOBODY e lastSeen=EVERYONE. Ao ficar online, chega '{"online":false,"lastSeenAt":null}', enquanto o GET devolve o horário antigo. A UI apaga o 'visto por último' e assim revela que a pessoa está online.  (4) Sem IT: desbloqueio do PV-02 por passkey e as agendadas, o reply e o starred em conversa trancada.  Partes cumpridas e provadas: o REST de PV-02/03/04/05/06/07/08, a busca, a exportação, a galeria, o sync e as notificações. Faltam: - fechar os vazamentos em scheduled, reply e presença STOMP; - testes de integração para receipts STOMP, starred, scheduled e reply em trancada e passkey.
- **UI-05** (10-UI): CUMPRIDO → **STATUS INCORRETO**. O veredito real é PARCIAL. (1) A reexecução não ficou verde. Rodei native-controls nos 3 motores com --retries=0 contra o JAR de referência. Chromium e WebKit passaram (68/66 capturas), mas o desktop-firefox FALHOU com 'Error: erros de console ou de CSP ... + "pageerror: The operation was aborted. "' (fixtures.ts:53), depois de gerar as 68 capturas. Uma segunda rodada, numa instância nova com workers=1, passou. É um erro de página intermitente no Firefox, o mesmo que ficou escondido por retries:1 no CI #65. A regra 'e2e falha com qualquer erro de console' não fica provada de forma estável. (2) Uma mutação sobreviveu num sub-item explícito do requisito ('spinners de type=number removidos'). Removi `input[type="number"] { appearance: textfield; }` de index.css:235 e rodei native-controls no desktop-firefox: '1 passed (1.4m)'. A captura mut-m5/light-admin.png mostra as setinhas nativas nos 4 campos numéricos do admin; a captura de referência não mostra. A verificação em runtime não olha number, autofill nem o botão do search. O preenchimento automático continua sem prova. (3) Mutações que derrubaram o teste: sem scrollbar-color no html deu 'barra de rolagem padrão: <div role="region" aria-label="Mensagens"...'; sem color-scheme no .dark deu 'color-scheme "light" no tema dark'; title="Anexar" no botão deu 'title nativo: <button ... title="Anexar"'. (4) O lint falha em parte. Plantei um arquivo temporário e o eslint real acusou só confirm() e <video controls>. Escaparam type={"checkbox"}, type={kind}, spread {type:"date"}, <Button title>, createElement("select") e createElement("progress"). Os 26 testes de lint passaram. (5) Caminho alternativo: com um spec de sonda, verifiquei no tema claro, no desktop e no Pixel 7, telas fora da matriz. São elas: gaveta do contato, figurinhas e o gerenciador, menu, /settings, /add, /reset-password, /verify-email, dados do grupo e as abas Usuários, Denúncias, Transcrição e Disco do admin. Todas deram []. O tema escuro dessas telas não ficou coberto (a sonda estourou o tempo). O celular segue fora da matriz do spec (skip isMobile). A produção parece correta; o que falta é a prova automatizada estável e completa.

### Achados Altos reexaminados
- Os 7 Altos originais reproduziram. Nenhum foi falso positivo.
- 4 foram rebaixados para Média pelo verificador:
  - CT-02 e CT-03: falta teste, mas a regra funciona hoje;
  - AR-02: alvos de toque;
  - PK-04 (`install-ca.sh`): a ameaça se limita a quem controla o arquivo servido.

### Revisão por amostra feita pelo auditor principal

Li o código de produção de 5 achados, e os 5 se confirmam:
- **RT-01:** em `MessageService.java:308-316`, `sequences.current` é lido depois de `findChangedAfter`.
- **Presença:** em `PresenceService.java:130-150`, o evento vai a todo par a cada transição, com a visão filtrada.
- **`install-ca.sh:161-186`:** confere a impressão digital de um bloco e instala o arquivo inteiro.
- **MS-08:** `ViewOnceOpens.messageOfToken` não consome o token, e o TTL é de 5 min (`MessageService.java:48`).
- **MD-04:** `SafeFetcher.java:49-52` só tem timeouts de conexão, socket e resposta, sem prazo total.

Também conferi o ID-01: `UserAccount` usa `UuidGenerator.Style.VERSION_7`, mas `UploadService.java:99` e outros pontos usam `UUID.randomUUID()` (v4), como o agente apontou.


## 9. Itens NÃO VERIFICÁVEIS (no todo ou em parte)

Nenhum ID ficou com veredito final NÃO VERIFICÁVEL, mas estas partes não puderam ser executadas:

- **NT-01** (PARCIAL): Não verificável aqui: a entrega real de Web Push por FCM, Mozilla ou Apple com o app fechado (o Chromium do Playwright não tem Push API) e o comportamento no Firefox, Safari e Edge. O e2e do repositório não prova 'permissão só após ação explícita', porque concede a permissão antes.
- **AR-02** (PARCIAL): Alvos de 44 px NÃO cumpridos em pointer: coarse (32 px nos botões de ícone com Tooltip/DropdownTrigger, 28 px em ações da mensagem, Fechar e links das configurações). Ao contrário do que o STATUS diz, o 'alvos de 44 px' não está feito. /admin rola na horizontal a 320 px. Não há safe-area left/right (paisagem com notch). Teclado virtual e notch reais: NÃO VERIFICÁVEL sem aparelho
- **§11 relatório** (NÃO VERIFICÁVEL): Faltariam as transcrições das sessões para conferir os 7 itens do relatório.
- **AU-04:** o Whisper real rodou em WASM (30 s) e em WebGPU por software (passou na 2ª tentativa; a 1ª estourou o tempo com load average 14). Não foram exercitados:
  - o cache do modelo numa 2ª carga;
  - o fallback de WebGPU para WASM em tempo de execução;
  - GPU real e Safari.
- **BK-03:** backup e restauração rodaram, com a restauração num banco novo dentro do mesmo contêiner. Não foram feitas a restauração num contêiner ou máquina nova e a execução de `scripts/verify-backup.sh` (portas fixas).
- **MS-08 e PV-01/PV-02 em aparelho real:** o desfoque, a marca d'água e o toque longo foram provados no Chromium, mas não em iOS e Android reais.

Para verificar o que falta seriam necessários:
- aparelhos iOS e Android reais, com o PWA instalado;
- acesso a servidores de push reais (FCM, Mozilla, APNs);
- Safari e Edge reais;
- uma GPU;
- as transcrições das passadas anteriores, para o §11.
