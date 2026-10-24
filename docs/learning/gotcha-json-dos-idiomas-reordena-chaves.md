---
name: JSON dos idiomas e CSS fora do format:check
description: JSON.parse + JSON.stringify põe chaves como "86400" na frente; o prettier no index.css reformata regras alheias; edite o texto
type: project
category: gotcha
date: 2026-10-24
updated: 2026-10-24
---
Para acrescentar chaves em `frontend/src/locales/{pt-BR,en}.json`, edite o texto (linha a linha); não reescreva o arquivo
com `JSON.parse`/`JSON.stringify`.

**Problema/Contexto:** objetos JavaScript enumeram primeiro as chaves que parecem índices de array, em ordem numérica.
O bloco `chat.timer` tem as chaves `"86400"`, `"604800"` e `"7776000"` (durações das mensagens temporárias) depois de
`title`, `hint` e `off`; reescrever o arquivo por script move as três para o topo do bloco.
**Por que importa:** o diff passa a mexer em linhas que nada têm a ver com a mudança, nos dois idiomas, e esconde a
alteração de verdade na revisão.
**Como aplicar:** `sed`/edição de texto ancorada na linha da chave vizinha (confira que ela é única com `grep -c`);
depois, `node -e 'require("./src/locales/pt-BR.json")'` valida o JSON e o `i18n.test.ts` confere as mesmas chaves nos
dois idiomas.
**Evidência:** `git diff --stat` com 8 linhas por arquivo (3 do `timer` movidas) ao inserir `audio.duration` e
`audio.elapsed` pelo Node; 2 linhas por arquivo com o `sed`; 2026-10-24.

O mesmo vale para o `frontend/src/index.css`: o `format:check` do projeto cobre só `**/*.{ts,tsx}`, e o prettier no CSS
quebra em várias linhas os seletores longos do modo privado e os gradientes do papel de parede (37 linhas alheias no
diff da Fase 2 de 2026-10-24). Formate só os arquivos .ts/.tsx; no CSS, mantenha o estilo de quem já está lá.
