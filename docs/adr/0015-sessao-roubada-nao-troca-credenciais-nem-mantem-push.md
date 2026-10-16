---
status: aceita
date: 2026-10-16
---

# 0015 — Quem só tem a sessão não troca credenciais nem continua recebendo push

## Contexto e problema

A sessão por cookie (ADR 0003) protege bem contra roubo remoto do cookie (`HttpOnly`, `Secure`, `SameSite`), mas o
caso real de um chat entre amigos é o aparelho destravado: alguém senta no computador de outra pessoa por alguns
minutos. A revisão de segurança da passada 8 encontrou o que essa pessoa conseguia fazer sem saber a senha e que
sobrevivia a o dono "limpar" a conta depois (trocar a senha, encerrar as outras sessões):

- cadastrar uma passkey própria (entra sem senha nem TOTP e não era apagada por troca de senha nem redefinição);
- trocar o segredo do TOTP: `POST /account/totp` desligava o TOTP atual (o que o `DELETE`, com senha, deveria
  proteger) e o código do app dela ligava o novo, trancando o dono fora até usar um código de recuperação;
- ligar as notificações no próprio navegador e continuar recebendo o nome de quem mandou e a prévia das mensagens,
  porque a assinatura de Web Push só pertencia à conta;
- ficar com o WebSocket aberto até 30 s depois da revogação, recebendo os eventos.

## Decisão

- **Modo sudo para credenciais.** Passkey nova e ligar o TOTP exigem a senha confirmada nos últimos 5 minutos,
  guardada como atributo da sessão. O login por senha e o cadastro contam como confirmação; nos outros casos (login
  por passkey, sessão antiga) o servidor responde 403 `password-confirmation-required`, a tela pede a senha
  (`POST /api/v1/account/password-confirmation`) e refaz a ação. O guarda das passkeys é um filtro antes dos filtros
  de registro do Spring (o de opções roda antes da autorização). Com o TOTP ligado, configurar outro app é recusado
  (409): primeiro desliga, com a senha. Desligar o TOTP, gerar códigos e trocar o e-mail continuam pedindo a senha no
  próprio pedido.
- **Assinatura de push presa à sessão que a criou.** A assinatura guarda uma chave aleatória que também fica num
  atributo da sessão (o id da sessão muda a cada login, o atributo não). No envio, só recebem as assinaturas cujas
  sessões ainda existem; as outras são apagadas. O app confirma a assinatura a cada abertura, o que liga de novo uma
  assinatura antiga à sessão atual.
- **Revogar derruba o WebSocket na hora**, pelo próprio `SessionService`; a varredura periódica fica para as
  sessões que expiram sozinhas.
- **Código TOTP de uso único** (RFC 6238): o login guarda o passo do último código aceito numa troca atômica.

## Consequências

- Boas: o que o dono faz para retomar a conta (trocar a senha, encerrar as outras sessões, o admin desativar) agora
  corta também passkey nova, push e eventos ao vivo de quem teve a sessão; o fluxo normal (entrar com senha e logo
  configurar passkey ou TOTP) não pede a senha de novo.
- Ruins: quem entrou por passkey digita a senha para cadastrar outra passkey ou ligar o TOTP; depois de atualizar,
  os navegadores com push assinado antes desta mudança deixam de receber até o app ser aberto uma vez; o envio de
  push lê as sessões da pessoa a cada mensagem (uma consulta por destinatário com assinatura).

## Alternativas consideradas

- Pedir a senha dentro de cada pedido (como desligar o TOTP): impossível nos endpoints de passkey do Spring
  Security, que não aceitam campos extras.
- Guardar o id da sessão na assinatura de push: o id muda em todo login (proteção contra fixação), inclusive no
  desbloqueio de tela por passkey, e cortaria o push de quem só destravou a tela.
- Apagar todas as assinaturas e passkeys da pessoa ao trocar a senha: também derrubaria os aparelhos legítimos e
  não resolveria a revogação de uma sessão só.
