---
name: Prévia de anexos, menções com @ e citação no estilo do WhatsApp
description: prévia no lugar do diálogo com legenda por arquivo e capa do PDF no navegador, área de soltar por contador, menção grava @usuario e mostra o nome, "@" na lista calculado no SQL, armadilhas do e2e (dispatch no alvo certo, botão invisível que pega o clique, 44 px no toque)
type: project
category: decision
date: 2026-10-29
---
Pedido do dono de 2026-10-29, com capturas do WhatsApp Web: anexos arrastados com a prévia grande, "@" no grupo
com a lista de membros e notificação, e a citação de resposta dentro da caixa do campo.

**Decisões:**
- A prévia (`attach-editor.tsx`) cobre só a área abaixo do cabeçalho (um envoltório `relative` em volta de tudo
  depois do `<header>` no `conversation-view`), com `role="dialog"` e o nome "Enviar N arquivo(s)", o mesmo do
  diálogo antigo: os specs que enviam arquivo continuaram valendo. Cada arquivo tem a sua legenda (como no
  WhatsApp); a resposta vai só no primeiro.
- A capa do PDF ainda no aparelho sai do próprio pdf.js (`pdfCover` em `features/media/pdf.ts`, `getDocument({data})`
  e `canvas.toDataURL`): a CSP já aceita `img-src data:`. PDF que o pdf.js não abre cai no ícone com "Nenhuma
  prévia disponível".
- Área de soltar: contador de `dragenter`/`dragleave` (os filhos disparam pares próprios); o `drop` zera.
- Menção: o texto guardado continua `@usuario` (o servidor já notificava por ele, `NotificationRecipients`); a bolha
  troca pelo nome do membro via contexto `MentionNames` (o nome do próprio membro, não "Você", como no WhatsApp).
- O "@" na lista (`unreadMention`) é calculado na consulta da lista com `~*` e as mesmas bordas do padrão Java,
  só nas não lidas do grupo: sem migration nem tabela de menções.

**Armadilhas encontradas no e2e:**
- Evento sintético disparado num ancestral (`main`) não chega ao elemento com o handler, que fica dentro dele:
  solte no `[data-slot="message-scroller"]` ou na própria prévia.
- Botão com `opacity-0` continua pegando o clique: o X das miniaturas escondido precisa de `pointer-events-none`.
- No toque todo botão tem 44 px (AR-02): um X no canto de uma miniatura de 56 px a cobre e o axe acusa
  `target-size`. No toque o X da miniatura some e a lixeira do topo tira o arquivo em destaque.
- Com 14 navegadores em paralelo, `documents.spec` e `groups.spec` estouraram tempo; isolados ou com `--workers=4`
  passam.

**Evidência:** `GroupIT.shouldFlagUnreadMentions`; `mention.test.ts`; `e2e/whatsapp-flows.spec.ts` verde em
desktop e celular (Chromium) contra instância local; specs de mídia, documentos, composer, grupos, chat, UI-05,
espaçamento, animações e atalhos verdes.

**Aplicabilidade:** qualquer mudança no envio de anexos, no campo de mensagem ou nas menções.
