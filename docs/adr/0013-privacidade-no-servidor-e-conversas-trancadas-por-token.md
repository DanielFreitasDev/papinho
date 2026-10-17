---
status: aceita
date: 2026-10-16
---

# 0013 — Privacidade no servidor, reciprocidade por par e conversas trancadas por token

## Contexto e problema

A Fase 5 pede que toda regra de privacidade (PV-*) seja aplicada no servidor (SG-04): visibilidade com
reciprocidade, confirmação de leitura com sobreposição por conversa, conversas trancadas cuja API só devolve
conteúdo com um token curto e um bloqueio de tela com segredo próprio. O módulo `privacy` precisa ser consultado
por mensagens e presença sem criar ciclo entre módulos do Spring Modulith.

## Decisão

- **Módulo `privacy`** depende só de contatos, identidade e perfil. Mensagens e presença o consultam; a
  participação nas conversas chega por uma porta (`ConversationMembership`) implementada pelo módulo de
  mensagens. Foto e recado usam a porta já existente `ProfileVisibilityPolicy`.
- **Reciprocidade por par (PV-05):** `viewer` vê o visto por último (ou o online) de `owner` só se a regra de
  `owner` deixa `viewer` ver **e** a regra de `viewer` deixa `owner` ver. Cobre o "Ninguém" do WhatsApp e
  também listas "exceto…". O que não pode ser visto volta como offline e sem horário, igual a quem nunca ficou
  online (não revela que foi escondido).
- **Confirmação de leitura (PV-06):** coluna `read_receipts` por participante (nulo = padrão). Na conversa
  individual o padrão é o global da pessoa; no grupo é sempre ligada. Quem desliga não mostra a própria leitura
  e não vê a dos outros naquela conversa. No grupo, quem desligou fica fora do "lida por todos"; se ninguém
  mais compartilha, não há leitura para mostrar. A entrega continua visível.
- **Conversas trancadas (PV-03):** códigos com hash Argon2 por pessoa; trancar com um código que já existe põe a
  conversa nesse código (código compartilhado). As trancadas saem da lista e das favoritas (a busca de GR-06 deve seguir a mesma regra); o
  conteúdo (mensagens, sincronização, galeria, fixadas, dados da mensagem) exige o cabeçalho
  `X-Papinho-Unlock` com um token aleatório que cobre os códigos digitados. O token fica **só na memória do
  servidor** (hash SHA-256), vale 10 minutos desde o último uso e no máximo 1 hora; reiniciar tranca tudo.
  No cliente fica só na memória da aba. Tentativas de código têm limite (SG-03). Citar uma mensagem e listar
  ou criar agendadas também exigem o token (auditoria de 2026-10-17, achados A23 e A24).
- **Exportação da conta (BK-02) com conversa trancada** (achado A25): a exportação leva só que a conversa
  existe (`trancada: true`); textos enviados, rascunhos, agendadas, com quem é a conversa e os arquivos
  anexados nela ficam de fora quando o token da requisição não a abre. O link de download do navegador não
  manda cabeçalhos, então na tela a exportação sai sempre sem as trancadas; para levá-las, a pessoa as destranca
  antes. Exigir o token ou uma nova senha para exportar foi descartado: o download por link não carrega o
  cabeçalho e a portabilidade da LGPD não pode depender de abrir a pasta.
- **Bloqueio de tela (PV-02):** PIN ou senha próprios com Argon2 e gatilhos guardados no servidor, todos
  desligados por padrão. O estado "bloqueado" é do cliente (`localStorage` por conta, sincronizado entre abas):
  é proteção visual e a sessão continua ativa. O desbloqueio por passkey reutiliza a cerimônia de login por
  passkey; se a passkey for de outra conta, a sessão passa a ser dela e a página recarrega.

## Consequências

- Boas: nenhuma regra depende do cliente; o módulo novo não cria ciclo; reiniciar o servidor nunca deixa uma
  conversa trancada aberta.
- Ruins: anexos de uma conversa trancada continuam acessíveis por id para quem participa (o `<img>` não manda
  cabeçalhos); o id só chega junto das mensagens, que exigem o token. Com várias instâncias, o token precisaria
  ir para um armazenamento compartilhado. O "Bloquear tela" não protege contra quem tem acesso às ferramentas
  do navegador, como documentado na própria tela.

## Alternativas consideradas

- Reciprocidade só para "Ninguém" (como o texto do WhatsApp): deixava assimetrias com "exceto…".
- Token de desbloqueio em cookie: iria em toda requisição e em `<img>`, mas não dá para limitar por aba nem
  descartar ao fechar a pasta sem outra chamada; o cabeçalho é explícito.
- Endpoint próprio de verificação por WebAuthn para o bloqueio de tela: exigiria expor e reconfigurar as
  operações de relying party do Spring Security só para isso.
