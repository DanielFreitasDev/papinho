---
status: aceita
date: 2026-10-16
---

# 0014 — Notificações decididas no servidor, Web Push próprio e PWA com dados offline por conta

## Contexto e problema

A Fase 6 pede notificações com o app aberto (Notification API) e fechado (Web Push com VAPID) em Chrome,
Firefox, Edge e Safari (NT-01), configuráveis por tipo, conversa e som (NT-03), respeitando "ocultar conteúdo"
(PV-08), conversas trancadas (PV-03), mensagens silenciosas (MS-11) e conversas silenciadas com menções (GR-05).
Também pede um PWA instalável com atualização controlada (NT-04) e uso offline sem guardar respostas
autenticadas no Cache Storage (NT-05). As regras de privacidade precisam valer no servidor (SG-04).

## Decisão

- **O servidor decide quem é avisado e com que texto.** O módulo de mensagens publica `MessagePosted` (só ids)
  na transação do envio; o módulo `notification` o ouve com `@ApplicationModuleListener` (registro de eventos do
  Spring Modulith, ADR 0005, com as publicações concluídas apagadas) e pergunta ao módulo de mensagens quem são
  os destinatários (participantes ativos; solicitações de quem não é contato não notificam), se a conversa está
  silenciada, se houve menção e se o conteúdo deve sumir (trancada, visualização única). Preferências por tipo e
  som e o "ocultar conteúdo" completam a regra. Sem conteúdo, o texto é só "Nova mensagem".
- **Dois caminhos com a mesma `tag`.** O aviso vai pelo WebSocket (`notify`, o app aberto mostra quando a aba está
  escondida ou em outra conversa) e por Web Push para cada navegador assinado (o service worker mostra, a menos
  que a conversa esteja aberta e em foco). Com a mesma `tag` (`conversation-<id>`) as duas notificações se
  substituem em vez de duplicar, sem o servidor precisar saber se o app está aberto.
- **Web Push implementado com o JDK** (RFC 8291 `aes128gcm` e RFC 8292 VAPID ES256), sem biblioteca extra: ECDH
  P-256, HKDF-SHA-256 e AES-128-GCM, conferidos byte a byte com o exemplo da RFC 8291 em teste. O par VAPID é
  gerado na primeira vez e guardado no banco com a privada cifrada pelo `SecretCipher`.
- **Sem SSRF:** o servidor faz POST no endereço que o navegador informa, então só aceita HTTPS em serviços de
  push conhecidos (`fcm.googleapis.com`, `push.services.mozilla.com`, `notify.windows.com`, `push.apple.com`;
  configurável) e valida a chave do navegador como ponto da curva. Respostas 404/410 apagam a assinatura.
- **Permissão só por ação explícita**, no botão de Configurações > Notificações. O logout tira a assinatura do
  navegador antes de encerrar a sessão.
- **PWA com `vite-plugin-pwa` 1.3.0 (`injectManifest`)**: a 2.0 saiu duas semanas antes desta decisão; a 1.3 é a
  linha madura e já suporta o Vite 8. O service worker só pré-armazena o build do app; API, WebSocket e mídia
  não têm rota registrada e vão direto para a rede. A versão nova espera a pessoa tocar em "Atualizar"
  (`SKIP_WAITING` por mensagem), nunca troca sozinha.
- **Offline com Dexie, um banco por conta** (`papinho-<id>`): a lista, os dados e as últimas 100 mensagens de cada
  conversa da lista principal (as trancadas nunca estão nela), o perfil para abrir sem rede e a fila de textos com
  `clientMessageId`. Rede primeiro: o guardado só é usado quando a rede falha. Abrir outra conta apaga os bancos das anteriores; o logout apaga o da conta.

## Consequências

- Boas: as regras de notificação têm testes de integração no servidor e não dependem do cliente; nenhuma
  biblioteca nova no backend; o mesmo conteúdo serve à página e ao service worker.
- Ruins: o push depende da internet e dos serviços de push dos navegadores (em LAN sem internet só há aviso com o
  app aberto); no iOS só funciona com o PWA instalado na tela de início (avisos na tela de configurações). A fila
  offline cobre só texto: anexos precisam da rede na hora. Trocar o par VAPID invalidaria as assinaturas, por isso
  ele nunca é recriado sozinho (entra no backup do banco).

## Alternativas consideradas

- `nl.martijndwars:web-push`: sem versão nova desde 2021 e puxa dependências antigas; a parte de cifra cabe em
  ~150 linhas com o JDK, testadas contra a RFC.
- Decidir a notificação no cliente: duplicaria as regras de privacidade e não serviria ao push com o app fechado.
- Mandar push só quando não houver WebSocket conectado: exigiria presença precisa por aba e ainda perderia avisos
  com a aba em segundo plano; a `tag` resolve a duplicação no próprio navegador.
