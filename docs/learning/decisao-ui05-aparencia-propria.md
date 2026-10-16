---
name: UI-05 — escolhas para eliminar a aparência nativa
description: componentes próprios de busca, senha, data/hora e mídia, noValidate, regra de lint e como o e2e prova
type: project
category: decision
date: 2026-10-16
---
Decisões da passada 7 para o UI-05 que o código não explica sozinho:

- **Data e hora:** `DatePicker` (Calendar do shadcn num Popover) e `DateTimePicker` (dia + hora e minutos em
  `Select`, minutos de 5 em 5; um valor antigo fora do passo continua aparecendo). Os valores seguem o formato dos
  inputs nativos (`AAAA-MM-DD` e `AAAA-MM-DDTHH:mm`, hora local) para não mexer nos formulários. O calendário
  (react-day-picker 10 + date-fns, ~79 kB) carrega sob demanda. Nada de `captionLayout="dropdown"`: ele usa
  `<select>` nativo por baixo.
- **Senha:** o CSS esconde o botão de revelar do Edge (nativo); `PasswordInput` devolve a função com botão próprio
  para todos. Nos e2e, `getByLabel("Senha")` precisa de `exact` ("Mostrar a senha" também casa).
- **Busca:** `SearchInput` com lupa e botão de limpar próprio; o CSS esconde o do navegador.
- **Formulários:** todo `<form>` tem `noValidate`; erros aparecem com FieldError, botão desabilitado ou toast do
  Problem Details, nunca no balão do navegador.
- **Mídia:** `VideoPlayer` com a barra **abaixo** do vídeo (tokens do tema, sem sobrepor a imagem) e o botão grande
  de tocar só para mouse/toque (`aria-hidden`); sem PiP e sem transmissão remota. Tela cheia é do contêiner, para os
  controles continuarem os nossos; sem suporte (iPhone), o botão some. Na galeria, o vídeo vira miniatura que abre
  um diálogo. A trilha do `Slider` dentro dos balões usa um tom do texto (no balão recebido a trilha `bg-muted`
  sumia).
- **Lint:** `no-restricted-syntax` fora de `src/components/ui/` (lista no `eslint.config.js`), `no-alert` e o import
  do NativeSelect bloqueado; `title` só no `<iframe>` (é o nome acessível). O teste em `frontend/lint/` roda o ESLint
  de verdade para a regra não morrer em silêncio.
- **Prova no e2e:** além das capturas, `nativeAppearance` procura controles nativos visíveis (o input escondido do
  Checkbox/Slider/Select do Base UI tem 1 px e não conta), `title`, áreas roláveis sem `scrollbar-color` (ou sem a
  regra `::-webkit-scrollbar` onde o motor não entende a propriedade) e `color-scheme` diferente do tema.

**Evidência:** commits `3b12084` a `b8bab71`; `native-controls.spec.ts` verde em Chromium, Firefox e WebKit (claro
e escuro) e as capturas revisadas em 2026-10-16.
