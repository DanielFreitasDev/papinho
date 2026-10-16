---
name: PWA, Web Push, busca em português e e2e offline
description: pegadinhas encontradas na Fase 6 (stemmer, ponto EC, MIME do manifesto, build com e2e, Playwright)
type: project
category: gotcha
date: 2026-10-16
updated: 2026-10-16
---
- O stemmer `portuguese` do PostgreSQL não junta "mensagens" e "mensagem" (`mensagens` fica inteiro); "conversas"
  e "conversa" viram `convers`. Teste radicais com exemplos conferidos por `to_tsvector`.
- `KeyFactory.generatePublic` do JDK **não** confere se o ponto EC está na curva: a chave `p256dh` do navegador
  precisa da conta y² = x³ + ax + b (mod p) explícita, senão abre ataque de curva inválida no ECDH.
- `Signature.getInstance("SHA256withECDSAinP1363Format")` dá a assinatura crua r‖s que o JWS/VAPID exige, sem
  converter DER à mão.
- O Spring serve `.webmanifest` como `application/octet-stream`; com `nosniff` isso importa. `server.mime-mappings`
  resolve no servidor real, mas o MockMvc não usa esse mapeamento: prove com `curl` ou no e2e.
- O `vite-plugin-pwa` já injeta `<link rel="manifest">` no `index.html`; não declare outro.
- `pnpm build` roda `tsc -b`, que inclui `tsconfig.e2e.json`: erro de tipo num spec quebra o `./mvnw install`
  (e o e2e roda com o JAR antigo se o script de subida não parar no erro).
- Playwright: contextos são anônimos e o Chrome escreve no console que não tem Push API neles ao assinar;
  `context.setOffline(true)` gera um `net::ERR_INTERNET_DISCONNECTED` por pedido. Os dois estão na lista de
  esperados do `fixtures.ts`.
- Com service worker, a primeira visita só instala: espere `navigator.serviceWorker.ready` e recarregue antes de
  testar offline.
- O `context.route` do Playwright não alcança a busca que o próprio navegador faz para atualizar o service worker
  (`registration.update()` segue com os bytes antigos). Para testar o aviso de versão nova, registre o mesmo script
  por outro endereço (`/sw.js?versao=nova`): é um worker novo na mesma registration, instalado e esperando. O
  workbox-window trata como "externa" a atualização que chega mais de 60 s depois do registro (o caso normal de um
  deploy com a aba aberta), mas dispara o `waiting` do mesmo jeito.

**Evidência:** falhas e correções em `SearchIT`, `WebPushCryptoTest`, `pwa.spec.ts`, 2026-10-16; o teste "versão
nova do app espera o Atualizar" não via a versão nova com `context.route` e passa com o segundo endereço.
