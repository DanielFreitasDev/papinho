# Papinho — prompt mestre de construção

> Este arquivo é a especificação e o contrato de trabalho do projeto. Ele é executado em
> **várias passadas**: em cada uma, o agente audita o que já existe, corrige, completa e avança.
> Leia-o inteiro no início de toda passada.

---

## 0. Parâmetros do projeto (preenchidos pelo dono)

| Parâmetro | Valor |
|---|---|
| Nome do produto | Papinho |
| Repositório | `papinho` |
| groupId Maven | `io.github.SEU_USUARIO` |
| Pacote base Java | `io.github.SEU_USUARIO.papinho` |
| Hostname na LAN | `papinho.lan` |
| Idioma da interface | pt-BR (i18n preparado para en) |
| Fuso de negócio | `America/Fortaleza` (persistência sempre em UTC) |
| Branch de trabalho | `main` |

---

## 1. Missão

Construir um chat web **auto-hospedado**, inspirado no WhatsApp Web, que eu inicio na minha
máquina e uso com amigos pela rede local, e que depois poderá ir para produção pública
(VPS com domínio e HTTPS). Deve ser moderno, rápido, acessível, responsivo, instalável como
PWA e ter **privacidade nativa** (recursos que hoje exigem extensões do Chrome no WhatsApp Web).

Sem criptografia de ponta a ponta nesta versão.

Use apenas tecnologias **maduras e estáveis**. Nada experimental, preview, beta ou
abandonado. Na dúvida entre duas opções, escolha a mais estabelecida e registre o motivo
em um ADR.

---

## 2. Documentos e precedência

Os guias estão na **raiz** do repositório e **não devem ser movidos**:

| Guia | Assunto |
|---|---|
| `STRUCTURE.md` | estrutura de pastas e integração dos builds |
| `JAVA.md` | backend Java/Spring |
| `UI.md` | shadcn/ui e composição de interface |
| `RESPONSIVENESS.md` | responsividade, acessibilidade e PWA |
| `COMMITS.md` | mensagens de commit e ações Git |
| `CONTEXT.md` | `AGENTS.md`, `CLAUDE.md` e `LEARNING.md` |

Leia apenas as seções relevantes de cada guia para a tarefa da vez; não carregue todos
inteiros a cada passo.

Precedência, da maior para a menor:
1. Este `prompt.md`, que inclui as exceções explícitas da seção 4.
2. ADRs em `docs/adr/`.
3. `AGENTS.md`, `LEARNING.md` e as convenções já presentes no código.
4. Documentação oficial da versão instalada de cada ferramenta.
5. Os guias.

Exceções explícitas aos guias:
- **Autenticação:** é sessão por cookie (seção 6, ADR 0003), não JWT. Os exemplos de
  resource server JWT do `JAVA.md` não se aplicam; os demais princípios de segurança se aplicam.
- **Localização dos guias:** ficam na raiz, não em `docs/guides/`.
- **Git:** commits e push em `main` são autorizados (seção 4).

---

## 3. Modo de trabalho em passadas

Este prompt será enviado várias vezes. Cada passada deve ser **idempotente**: nunca recriar
do zero o que já existe; nunca apagar trabalho válido; sempre continuar de onde parou.

### 3.1 Arquivo de estado `STATUS.md` (raiz)

`STATUS.md` é a fonte de verdade do progresso. Ele contém uma tabela com **todos** os
requisitos deste prompt (IDs da seção 7 e 8), cada um com:

| Campo | Conteúdo |
|---|---|
| ID | identificador do requisito |
| Descrição curta | resumo do requisito |
| Status | `pendente`, `em andamento`, `feito` ou `bloqueado` |
| Evidência | teste, comando executado ou arquivo que prova o status |
| Observações | o que falta ou o que bloqueia |

Regras:
- `feito` exige **evidência executada** (teste verde, build, verificação no navegador).
  Código escrito mas não verificado é `em andamento`.
- `bloqueado` exige o motivo concreto (por exemplo, "Docker indisponível no ambiente").
- Nunca marque algo como verificado sem ter executado. Não invente resultados.

### 3.2 Ciclo de cada passada

1. **Contexto.** Leia este prompt, `STATUS.md` (se existir), `AGENTS.md`, `LEARNING.md` e
   `git log --oneline -30`.
2. **Auditoria.** Confronte o `STATUS.md` com o código real. Rode build e testes. Corrija o
   status de qualquer item marcado como `feito` que não se sustente. Regressões e build
   quebrado têm prioridade máxima.
3. **Planejamento.** Escolha o próximo bloco de trabalho seguindo a ordem das fases
   (seção 10). Não pule para uma fase sem a anterior estar funcional.
4. **Execução.** Implemente, com testes e migrations junto do código. Faça commits atômicos
   e frequentes, e push após cada grupo coerente de commits, para que nada se perca se a
   sessão terminar.
5. **Verificação.** Rode as checagens aplicáveis (seção 8.6). Atualize o `STATUS.md`.
6. **Encerramento.** Commit final do `STATUS.md` e do `LEARNING.md`, push e relatório
   (seção 11).

Trabalhe até onde a sessão permitir. Se o contexto ou o tempo acabar, garanta que o último
estado está commitado, enviado e refletido no `STATUS.md`.

Quando todos os itens estiverem `feito`, a passada vira uma **auditoria de qualidade**:
- revisar requisito por requisito contra este prompt;
- procurar bugs, lacunas de teste, problemas de acessibilidade e responsividade e falhas
  de segurança;
- corrigir e registrar.

---

## 4. Regras de Git (autorização explícita)

- **Autorizado:** `git commit` e `git push origin main`, sem pedir confirmação.
- **Mensagens:** seguem o `COMMITS.md` (Conventional Commits, assunto em pt-BR).
- **Commits atômicos:** um commit por mudança lógica.
- **Proibido:**
  - `--force` e `--no-verify`;
  - `amend` e `rebase` de commits já enviados;
  - `git add -A` sem revisar o que está sendo adicionado.
- **Nunca commitar:**
  - segredos e chaves (inclusive a chave privada da CA);
  - `.env` real;
  - artefatos de build, `node_modules` e certificados gerados.
- **Hooks e conflitos:** se um hook falhar, corrija a causa. Se o push for rejeitado por
  divergência, faça `git pull --rebase` apenas sobre commits locais ainda não enviados e
  resolva os conflitos.

---

## 5. Ambiente e limitações

- O ambiente pode ser um sandbox na nuvem sem Docker, sem navegador real ou com rede
  restrita. Se algo não puder ser executado (Testcontainers, Playwright, dispositivos
  reais), **não finja**: marque como `bloqueado` com o motivo e deixe o teste escrito e
  pronto para rodar.
- Versões: use o Spring Initializr e `npx shadcn@latest init` como geradores oficiais, e
  confira as versões no registry ou no Maven Central. Nunca invente uma versão.
- Banco de dados: PostgreSQL 18 via `compose.yaml`, com Testcontainers nos testes.

---

## 6. Decisões de arquitetura (ADRs obrigatórios, em pt-BR, formato MADR)

| ADR | Decisão |
|---|---|
| 0001 | Registrar decisões em ADRs |
| 0002 | **Layout D2** (`STRUCTURE.md` 5.6): Maven multi-módulo; o SPA React é empacotado no JAR; um único artefato serve SPA, API e WebSocket na mesma origem. Motivo: auto-hospedagem simples (um JAR + PostgreSQL) |
| 0003 | **Sessão por cookie**, em vez de JWT (detalhes abaixo) |
| 0004 | **Tempo real**: STOMP sobre WebSocket (Spring WebSocket + `@stomp/stompjs`) com o broker simples em memória. Destinos por usuário (`/user/queue/...`). Caminho de escala documentado: relay para RabbitMQ sem mudar o protocolo |
| 0005 | **Monólito modular** com Spring Modulith, eventos com publicação via outbox e testes de modularidade (detalhes abaixo) |
| 0006 | **Armazenamento de arquivos** atrás de uma porta: sistema de arquivos (padrão) e S3-compatível (produção) |
| 0007 | **PKI própria** para HTTPS na LAN (seção 9) |
| 0008 | **Transcrição**: Whisper no navegador como padrão, provedores de servidor compatíveis com OpenAI como alternativa |
| 0009 | **Upload retomável** via protocolo tus (Uppy no frontend), limite de 1 GB por arquivo |
| 0010 | **Sincronização** por número de sequência monotônico por conversa e `clientMessageId` idempotente |

ADR 0003 — detalhes da sessão por cookie:
- Spring Session JDBC.
- Cookie `__Host-SESSION` com `HttpOnly`, `Secure` e `SameSite=Lax`.
- `csrf.spa()` ativo.
- `Argon2PasswordEncoder` para senhas.
- Passkeys (`spring-security-webauthn`) e TOTP opcional.
- Handshake do WebSocket autenticado pelo mesmo cookie, com validação de `Origin`; CSRF
  exigido no frame `CONNECT` do STOMP.
- Cadeia de segurança final com `denyAll`.

ADR 0005 — módulos do Spring Modulith:
`identity`, `profile`, `contact`, `conversation`, `message`, `media`, `presence`,
`notification`, `transcription`, `linkpreview`, `privacy`, `moderation`, `backup`,
`admin`, `pki`.

Stack do frontend (`STRUCTURE.md` 5.3, `UI.md`):
- Vite, React 19, TypeScript, shadcn/ui sobre Base UI, Tailwind v4;
- TanStack Router e TanStack Query;
- react-hook-form + zod;
- Dexie (IndexedDB), `@stomp/stompjs`, Uppy + tus;
- Transformers.js num Web Worker;
- vite-plugin-pwa (injectManifest);
- Vitest + Testing Library + msw, Playwright + axe.

Use os componentes oficiais de chat do shadcn (`message-scroller`, `message`, `bubble`,
`attachment`, `marker`) antes de criar componentes próprios.

---

## 7. Requisitos funcionais

### 7.1 Identidade e conta (ID)

- **ID-01** — Cadastro com `@usuario` único: case-insensitive, normalizado (NFKC,
  minúsculas, `[a-z0-9._]`, 3–32 caracteres), com lista de nomes reservados. Unicidade
  garantida por constraint no banco. IDs internos em UUID v7.
- **ID-02** — Nome de exibição, foto de perfil (recorte, remoção de EXIF, miniaturas) e
  recado ("about"), separados do `@usuario`.
- **ID-03** — Troca de `@usuario` com cooldown configurável. O nome antigo fica reservado
  por 30 dias para impedir que outra pessoa o use para se passar pelo dono.
- **ID-04** — Login por senha (Argon2, mínimo de 12 caracteres, checagem de senha vazada
  opcional) e por passkey. Limite de tentativas com Bucket4j. Mensagens de erro uniformes,
  que não revelam se o usuário existe.
- **ID-05** — Códigos de recuperação gerados no cadastro. TOTP opcional. E-mail opcional
  (desligado por padrão).
- **ID-06** — Tela de sessões ativas: dispositivo, data, IP aproximado, revogar uma
  sessão, encerrar todas as outras.
- **ID-07** — Modos de cadastro da instância:
  - aberto;
  - **somente convite** (padrão);
  - fechado.

  Convites por link e QR Code, com expiração e limite de usos. Botão "Convidar amigos".
- **ID-08** — O primeiro usuário cadastrado vira admin da instância.

### 7.2 Contatos (CT)

- **CT-01** — Adicionar contato por `@usuario`, link ou QR Code. Apelido local por contato.
- **CT-02** — Solicitações de mensagem: mensagens de quem não está nos seus contatos vão
  para "Solicitações", com as ações aceitar, recusar e bloquear. Antes de aceitar, o
  remetente não vê online, visto por último nem confirmação de leitura.
- **CT-03** — Bloquear e desbloquear. Um bloqueado não envia mensagens, não vê presença
  nem foto e não pode adicionar quem o bloqueou a grupos.
- **CT-04** — Denunciar usuário: envia as 5 últimas mensagens da conversa para a fila de
  moderação da instância, com a opção de bloquear junto.

### 7.3 Mensagens (MS)

- **MS-01** — Texto com a formatação do WhatsApp:
  - `*negrito*`, `_itálico_`, `~riscado~` e `` `mono` ``;
  - blocos ``` com destaque de sintaxe;
  - listas e citação `>`;
  - spoiler `||texto||`, que revela ao clicar.

  O conteúdo é sanitizado; nunca injetar HTML do usuário.
- **MS-02** — Status por mensagem: enviando, enviada, entregue e lida. Painel "Dados da
  mensagem" com quem recebeu e quem leu, respeitando a seção 7.7.
- **MS-03** — Indicadores "digitando…" e "gravando áudio…".
- **MS-04** — Responder (com salto para a mensagem original), encaminhar (selo
  "encaminhada", ou "encaminhada várias vezes" após 5 saltos), reagir com emoji, mencionar
  `@usuario` e `@todos`, fixar na conversa, favoritar (estrelar) e selecionar várias.
- **MS-05** — Editar: só o autor, com janela configurável pelo admin (padrão 15 min), selo
  "editada".
- **MS-06** — Apagar para mim. Apagar para todos: só o autor, em conversa individual e em
  grupo, com janela configurável (padrão 48 h); deixa o aviso "Esta mensagem foi apagada".
  O conteúdo e os arquivos são removidos do servidor.
- **MS-07** — Mensagens temporárias por conversa (desligado, 24 h, 7 dias ou 90 dias),
  mais um timer padrão para conversas novas. Um job (com ShedLock) remove as mensagens e
  os arquivos delas.
- **MS-08** — Visualização única (foto, vídeo e áudio):
  - **Servidor:** token de uso único, `Cache-Control: no-store`, nunca cacheado pelo
    service worker; arquivo apagado após aberto ou após 14 dias.
  - **Cliente:** sem download, encaminhar, copiar ou menu de contexto. Desfoque ao perder o
    foco ou trocar de aba. **Marca d'água com o `@usuario` de quem visualiza.**
  - **Aviso explícito ao usuário:** o navegador não consegue impedir capturas de tela.
- **MS-09** — Enquetes: escolha única ou múltipla, opção anônima, data de encerramento,
  resultados em tempo real.
- **MS-10** — Emojis (seletor com busca e recentes), figurinhas (pacotes próprios
  enviados pelos usuários, em WebP, inclusive animadas; favoritas) e GIFs (provedor
  externo opcional, desligado por padrão, atrás de uma porta).
- **MS-11** — Mensagens agendadas, envio silencioso (sem notificação) e rascunho
  sincronizado entre dispositivos.
- **MS-12** — "Mensagens salvas": uma conversa consigo mesmo.
- **MS-13** — Atalhos de teclado:
  - paleta de comandos `Ctrl/Cmd+K`;
  - `↑` edita a última mensagem;
  - `Esc` fecha painéis;
  - `Ctrl+V` cola imagem;
  - arrastar e soltar arquivos.

  Lista de atalhos acessível com `?`.
- **MS-14** — Marcador de não lidas, botão "ir para a primeira não lida", marcar conversa
  como não lida.

### 7.4 Mídia, arquivos e links (MD)

- **MD-01** — Envio de imagem, vídeo, documento (PDF, DOCX, PNG etc.) até **1 GB**.
  - Upload retomável (tus), com barra de progresso e opção de cancelar.
  - Tipo do arquivo detectado pelo conteúdo (Apache Tika); nome do arquivo sanitizado.
  - Cota de armazenamento por usuário, configurável pelo admin.
- **MD-02** — Imagens: remoção de EXIF, miniaturas e placeholder (thumbhash), legenda,
  visualizador em tela cheia com zoom e navegação.
- **MD-03** — Galeria da conversa com abas de mídia, links, documentos e áudios.
- **MD-04** — Prévia de link gerada pelo servidor (Open Graph e oEmbed), com **proteção
  contra SSRF**:
  - bloquear IPs privados, de loopback e link-local, inclusive depois de resolver o DNS;
  - limitar redirecionamentos, tamanho da resposta e tempo;
  - aceitar só http e https.
- **MD-05** — YouTube incorporado via `youtube-nocookie` no padrão "clique para
  carregar". Vimeo e Spotify via lista permitida de oEmbed.
- **MD-06** — Antivírus (ClamAV) opcional, desligado por padrão, atrás de uma porta.

### 7.5 Áudio e transcrição (AU)

- **AU-01** — Gravação com `MediaRecorder` (Opus em WebM/Ogg; AAC/MP4 no Safari).
  Forma de onda, travar gravação, pré-escuta antes de enviar e cancelamento.
- **AU-02** — Reprodução em 1×, 1,5× e 2×. Mini-player global que continua tocando ao
  trocar de conversa. Áudios consecutivos tocam em sequência.
- **AU-03** — Transcrição sob demanda por quem recebe, com opção de transcrição
  automática. O resultado fica em cache no servidor para os demais membros.
  Áudio de visualização única nunca tem transcrição persistida.
- **AU-04** — Provedor no navegador:
  - Transformers.js num Web Worker;
  - WebGPU com fallback WASM;
  - modelo multilíngue `whisper-base` (ou `small` quantizado, configurável);
  - download sob demanda com progresso e cache.
- **AU-05** — Provedores no servidor, todos no padrão OpenAI-compatible
  (`POST {base}/v1/audio/transcriptions`, multipart, `Authorization: Bearer`, resposta
  `{"text": ...}`). Pré-configurados:
  - Groq (`https://api.groq.com/openai`, `whisper-large-v3-turbo`, limite de 25 MB);
  - Whisper próprio (base configurável, campo opcional `hotwords`, verificação de saúde
    em `GET {base}/health`);
  - OpenAI;
  - URL personalizada.

  Regras de implementação:
  - o backend faz o papel de proxy;
  - as chaves ficam cifradas em repouso e nunca vão para o cliente nem para os logs;
  - o **User-Agent é `Papinho/<versão>`**, nunca começando com `OpenAI/`, porque a
    Cloudflare bloqueia esse padrão;
  - o padrão é definido pelo admin; o usuário escolhe nas configurações.

### 7.6 Grupos, transmissão e organização (GR)

- **GR-01** — Grupos: nome, foto e descrição; admins; permissões (quem envia, quem edita
  os dados, quem adiciona membros); modo "só admins enviam"; sair silenciosamente;
  responder em privado a partir do grupo.
- **GR-02** — Convite por link e QR Code, com aprovação de entrada opcional. Respeita a
  privacidade "quem pode me adicionar a grupos".
- **GR-03** — Lista de transmissão: os destinatários recebem como conversa individual, e
  somente quem tem o remetente nos contatos recebe.
- **GR-04** — Lista de transmissão temporária, em dois modos:
  - a lista expira numa data definida;
  - as mensagens enviadas por ela são temporárias.
- **GR-05** — Lista de conversas:
  - arquivar, com a opção "manter arquivadas ao receber nova mensagem";
  - fixar até 3 conversas;
  - silenciar por 8 h, 1 semana ou sempre, com opção de ainda notificar menções;
  - favoritar;
  - listas personalizadas (pastas);
  - limpar conversa e apagar conversa.
- **GR-06** — Busca e filtros:
  - **na lista:** todas, não lidas, favoritas, grupos, listas personalizadas e arquivadas;
  - **busca global:** conversas, contatos e mensagens;
  - **dentro da conversa:** texto, remetente, data e tipo (mídia, link, documento, áudio),
    com navegação entre os resultados.

  Implementação no PostgreSQL com `unaccent`, `pg_trgm` e a configuração `portuguese`.

### 7.7 Privacidade (PV) — o diferencial do produto

- **PV-01** — Desfoque configurável, com um interruptor independente para cada item:
  - mensagens;
  - prévia na lista de conversas;
  - nomes dos contatos;
  - fotos dos contatos;
  - mídia.

  Formas de revelar:
  - mouse em cima, de forma instantânea ou após X segundos;
  - foco do teclado (acessibilidade);
  - toque longo no celular.

  Respeitar `prefers-reduced-motion`. Atalho global para alternar o modo privado.
- **PV-02** — Bloqueio de tela:
  - **Gatilhos:** inatividade (tempo configurável), troca de aba ou janela e atalho de
    teclado configurável. **Tudo desativado por padrão.**
  - **Desbloqueio:** PIN ou senha próprios do bloqueio (separados da senha da conta), com
    Argon2 no servidor, ou passkey.
  - **Na interface:** desfoque total e nada interativo por trás.
  - **Documentar:** é proteção visual; a sessão continua ativa.
- **PV-03** — Conversa trancada:
  - **Código:** um código próprio por conversa, ou um código compartilhado por várias,
    guardado com hash Argon2.
  - **Pasta oculta:** as conversas trancadas saem da lista e ficam numa pasta separada.
  - **API:** só devolve mensagens com um token de desbloqueio válido e de curta duração.
  - **Notificações:** sem conteúdo.
  - **Proteção contra força bruta:** limite de tentativas.
- **PV-04** — Visibilidade de visto por último, online, foto de perfil e recado. Cada uma
  pode ser Todos, Meus contatos, Meus contatos exceto… ou Ninguém. O padrão é tudo
  visível, como no WhatsApp.
- **PV-05** — Reciprocidade, **aplicada no servidor**:
  - quem não compartilha visto por último ou online não vê o dos outros;
  - quem desliga a confirmação de leitura não vê a dos outros.
- **PV-06** — Confirmação de leitura:
  - configuração global, com sobreposição por conversa individual e por grupo;
  - o padrão segue o WhatsApp (nos grupos é sempre enviada), mas o usuário pode desligar
    por grupo;
  - a reciprocidade vale no escopo desligado.
- **PV-07** — "Quem pode me adicionar a grupos" (mesmas quatro opções).
- **PV-08** — Notificações com a opção "ocultar conteúdo", que mostra só "Nova mensagem".

### 7.8 Notificações e PWA (NT)

- **NT-01** — Notificações no navegador (Chrome, Firefox, Edge, Safari) via Notification
  API com o app aberto e Web Push (VAPID) com o app fechado. A permissão é pedida só após
  uma ação explícita do usuário.
- **NT-02** — Avisos dentro do app:
  - o push depende de internet (servidores de push do navegador);
  - no iOS, o push só funciona com o PWA instalado na tela inicial.
- **NT-03** — Configuração de notificações: global, por tipo (individual, grupos,
  menções), por conversa e por som. Contador de não lidas no ícone (Badging API).
- **NT-04** — PWA instalável:
  - manifesto completo, ícones e ícone maskable, screenshots;
  - modo standalone;
  - aviso de nova versão com atualização controlada (sem `skipWaiting` cego).
- **NT-05** — Uso offline:
  - conversas recentes no IndexedDB, isoladas por usuário e apagadas no logout ou na
    troca de conta;
  - fila de envio de texto com `clientMessageId`;
  - estados de offline e reconexão visíveis.

  Nenhuma resposta autenticada vai para o Cache Storage.

### 7.9 Backup e dados (BK)

- **BK-01** — Exportar conversa em ZIP: texto no estilo do WhatsApp mais a mídia,
  opcional.
- **BK-02** — Exportar todos os dados da conta (portabilidade da LGPD) e excluir a conta.
- **BK-03** — Backup e restauração da instância: script com `pg_dump` mais o volume de
  arquivos, procedimento documentado no README e restauração testada.

### 7.10 Administração (AD)

- **AD-01** — Painel do admin:
  - usuários (desativar, redefinir senha, gerar convite);
  - fila de denúncias;
  - modo de cadastro;
  - cotas;
  - janelas de edição e exclusão;
  - provedores de transcrição;
  - uso de disco.
- **AD-02** — Configurações da instância persistidas no banco, com padrões seguros.

---

## 8. Requisitos não funcionais

### 8.1 Segurança (SG)

- **SG-01** — Seguir `JAVA.md` Parte 8 (exceto JWT): checagem de propriedade (IDOR) em
  toda consulta; Problem Details em todos os erros; nenhum segredo ou dado pessoal em logs.
- **SG-02** — Content Security Policy estrita, cabeçalhos de segurança, HSTS em produção.
- **SG-03** — Limite de requisições em login, convites, códigos de desbloqueio, envio de
  mensagens e upload.
- **SG-04** — Toda regra de privacidade (PV-*) aplicada no servidor e coberta por testes
  de integração.

### 8.2 Tempo real e consistência (RT)

- **RT-01** — Cada mensagem tem `seq` monotônico por conversa. Ao reconectar, o cliente
  pede `afterSeq` e recebe o que perdeu, sem duplicar.
- **RT-02** — Reenvio com o mesmo `clientMessageId` não duplica a mensagem.
- **RT-03** — Presença com heartbeat; a aba em segundo plano não conta como online.

### 8.3 Interface (UI)

- **UI-01** — Layout de duas colunas no desktop (lista + conversa, com painel de detalhes
  opcional à direita). No celular, navegação em pilha, com rota própria para cada conversa.
- **UI-02** — Temas claro, escuro e do sistema; papel de parede da conversa; tamanho de
  fonte; densidade.
- **UI-03** — Todos os estados implementados: carregando, vazio, erro, offline, sem
  permissão.
- **UI-04** — Lista de mensagens performática com milhares de itens (virtualização ou
  `message-scroller` com paginação reversa).

### 8.4 Acessibilidade e responsividade (AR)

- **AR-01** — WCAG 2.2 AA como referência; `RESPONSIVENESS.md` aplicado.
- **AR-02** — Funciona a partir de 320 px, sem rolagem horizontal acidental. Áreas de
  toque de pelo menos 44 px; safe areas respeitadas; o teclado virtual não esconde o campo
  de mensagem.
- **AR-03** — Fluxo completo usando só o teclado; axe sem violações críticas ou sérias.

### 8.5 i18n (IN)

- **IN-01** — Textos da interface externalizados; pt-BR completo e estrutura pronta para
  en.

### 8.6 Testes e qualidade (QA)

- **QA-01** — Backend:
  - testes unitários, slice e de integração (Testcontainers);
  - ArchUnit e testes de modularidade do Spring Modulith;
  - comando `./mvnw verify`.
- **QA-02** — Frontend: lint, typecheck e Vitest.
- **QA-03** — E2E com Playwright e axe nos fluxos críticos:
  - cadastro por convite;
  - conversa entre **dois usuários em dois contextos**;
  - grupo;
  - upload;
  - apagar para todos;
  - privacidade recíproca;
  - conversa trancada;
  - bloqueio de tela.
- **QA-04** — CI no GitHub Actions com backend, frontend e e2e, cada um em seu job.
- **QA-05** — README em pt-BR: como rodar em desenvolvimento, como subir na LAN, como
  instalar a CA, backup e produção.

---

## 9. HTTPS na rede local (PKI)

- **PK-01** — Subcomando `java -jar papinho.jar pki init`, usando BouncyCastle:
  - **CA:** ECDSA P-256, validade de 10 anos, com **Name Constraints** restringindo a
    emissão ao hostname configurado e à faixa de IP da LAN. Assim, a CA instalada nos
    amigos não serve para falsificar outros sites.
  - **Certificado do servidor:** validade de até 398 dias, com SAN `DNS:papinho.lan` e o
    IP da LAN.
  - **Chave privada da CA:** guardada fora do repositório, com permissão `0600`.
- **PK-02** — O Spring serve TLS via SSL bundle, com HTTP/2. Na porta HTTP, só ficam
  disponíveis `/setup/**` e o redirecionamento para HTTPS.
- **PK-03** — Página `/setup`:
  - download da CA (`.crt` e `.pem`), impressão digital SHA-256 visível e QR Code;
  - detecção do sistema e do navegador, com instruções específicas para:
    - Ubuntu/Debian, Fedora e Arch;
    - Windows e macOS;
    - iOS (perfil + Ajustes de Confiança de Certificado) e Android;
    - Firefox, que usa armazenamento próprio de certificados.
- **PK-04** — `GET /setup/install-ca.sh`, usado com
  `curl -fsSL http://papinho.lan/setup/install-ca.sh | sh`. O script:
  - **confere a impressão digital SHA-256 da CA** que baixou antes de instalar;
  - detecta a distribuição e instala no sistema (`update-ca-certificates`,
    `update-ca-trust` ou `trust anchor`);
  - adiciona a CA às bases NSS (`~/.pki/nssdb` e perfis do Firefox) com `certutil`,
    quando disponível;
  - é idempotente e tem uma opção para desinstalar.
- **PK-05** — Documentação de hostname: mDNS/Avahi ou DNS do roteador, com as ressalvas.
  Passkeys exigem um hostname: WebAuthn não aceita IP como RP ID.
- **PK-06** — Em produção, a PKI fica desligada; o TLS vem do proxy ou de uma CA pública.

---

## 10. Fases e ordem de execução

| Fase | Conteúdo | Requisitos |
|---|---|---|
| 0 | Fundação: geradores, layout D2, `AGENTS.md`, `CLAUDE.md`, `LEARNING.md`, `STATUS.md`, ADRs, `compose.yaml`, CI, segurança base, PKI | QA-04, QA-05 (parcial), PK-* |
| 1 | Contas e contatos | ID-*, CT-* |
| 2 | Conversas individuais em tempo real | MS-01..04, MS-14, RT-*, UI-01 |
| 3 | Mídia, áudio e links | MD-*, AU-* |
| 4 | Grupos e mensagens avançadas | GR-01..04, MS-05..13 |
| 5 | Privacidade | PV-*, SG-04 |
| 6 | Organização, busca, notificações e PWA | GR-05, GR-06, NT-* |
| 7 | Backup, admin, endurecimento e e2e completo | BK-*, AD-*, AR-*, QA-03 |

Uma fase só começa quando a anterior compila, passa nos testes e está utilizável.
Prefira entregar funcionalidades completas de ponta a ponta (backend, frontend e testes)
a camadas soltas.

`AGENTS.md` deve mencionar `prompt.md` e `STATUS.md` como leitura obrigatória no início
de cada sessão.

---

## 11. Relatório de fim de passada

Ao final de cada passada, responda com:
1. O que foi feito nesta passada (IDs).
2. Correções feitas em itens antes marcados como `feito`.
3. Verificações executadas e resultados; o que ficou pendente e por quê.
4. Itens `bloqueado` e o que é preciso para desbloqueá-los.
5. Próximos passos para a próxima passada.
6. Hash do último commit enviado.
7. Por último, a linha `LEARNING:` (`CONTEXT.md`).