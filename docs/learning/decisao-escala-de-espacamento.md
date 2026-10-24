---
name: Escala de espaçamento e auditoria das bordas (Fase 3)
description: tokens gutter/inset/item/icon na forma de variável por causa do cn; menu com largura própria e collisionPadding; fieldset com min-w-0; addons do composer presos embaixo; auditSpacing no e2e
type: project
category: decision
date: 2026-10-24
---
Regra do dono: nada encosta (nem quase) em bordas, cantos ou outros elementos. Como ficou e por quê:

- **Tokens** em `@theme static` no `index.css`: `--spacing-gutter` 16 px (margem lateral das áreas da tela: cabeçalhos,
  campo de mensagem, buscas, páginas), `--spacing-inset` 6 px (padding de menus e listas de opções), `--spacing-item`
  12 px (respiro horizontal dos itens) e `--spacing-icon` 12 px (ícone até o texto, igual em todo item de menu). Nas
  classes, sempre a forma de variável, `px-(--spacing-gutter)`: o `cn` do shadcn (tailwind-merge) não reconhece
  nomes próprios como `px-gutter` no grupo do `px-*`, então `cn("px-item", "px-1.5")` mantinha as duas classes e quem
  ganhava era a ordem do CSS gerado; com `px-(--spacing-item)` a sobrescrita do componente vence, como com `px-3`.
- **Menu do Base UI com a largura do botão:** o `DropdownMenuContent` do shadcn vem com `w-(--anchor-width) min-w-32`;
  aberto por um botão de 32 px, o menu ficava com 128 px, os rótulos quebravam e encostavam na borda (o print do
  "Configurações"). Agora `w-max min-w-48 max-w-[calc(100vw-1.5rem)]`; menus, listas do Select e popovers recebem
  `collisionPadding={12}` no Positioner para não colar na borda da tela.
- **`<fieldset>` tem `min-inline-size: min-content`** por padrão: um nome longo na lista de contatos do "Novo grupo"
  deixava o fieldset, e com ele o diálogo, mais largo que a tela do celular. `min-w-0` no `FieldSet` (e `*:min-w-0` no
  conteúdo dos diálogos, que é grid) resolve; vale para qualquer fieldset novo fora do `FieldSet`.
- **Campo de mensagem:** os addons do `InputGroup` ficavam centrados na altura do grupo (46 px, definida pelo addon)
  e o microfone, fora dele, alinhado embaixo: 7 px de diferença. Os addons ficam presos embaixo com a altura de uma
  linha (`h-[calc(2.75rem-2px)] self-end`), também quando o texto cresce; o microfone é redondo, de 44 px.
- **Auditoria automática** (`frontend/e2e/spacing-audit.ts`, usada no `spacing.spec.ts` e nas capturas): texto maior
  que a caixa, conteúdo a menos do respiro mínimo da superfície (menus 8 px, diálogos/cartões/toasts 12 px, botões e
  itens 6 px, botão só com ícone 4 px) e controle a menos de 12 px da borda da tela. Ficam de fora o que a tela corta
  de propósito (`text-overflow: ellipsis`, área com rolagem), texto `sr-only` ou com opacidade 0, linhas que ocupam a
  largura do contêiner (o respiro é interno) e controles dentro de faixas que rolam de lado (abas e filtros com
  `scroll-fade-x`, marcadas com `data-scroll-x`: a aba cortada é o sinal de que há mais). Sem essas exclusões, a
  primeira versão contava 571 ocorrências no mesmo build, quase todas falsas.

**Evidência:** capturas da Fase 3 em 49 telas × 1280/820/390 px × claro/escuro (2026-10-24), com a mesma auditoria nas
duas versões: 113 ocorrências (23 problemas distintos) em 70 das 147 capturas antes, nenhuma depois; com a interface em
inglês, 24 telas em desktop e celular sem nenhuma. `spacing.spec.ts` verde em desktop e celular contra instância nova.
`cn` do pacote do projeto pelo Node: `cn("px-item", "px-1.5")` devolve as duas classes e `cn("px-(--spacing-item)",
"px-1.5")` devolve só `px-1.5` (o mesmo com `gap-*` e `p-*`). O `px-2` na faixa de filtros da lista fez o
`a11y.spec.ts` acusar rolagem horizontal em 320 px (311 > 303): faixa que rola de propósito precisa do `data-scroll-x`.
