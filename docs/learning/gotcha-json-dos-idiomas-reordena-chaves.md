---
name: JSON dos idiomas reescrito por script reordena chaves numéricas
description: JSON.parse + JSON.stringify põe chaves como "86400" na frente; inserir chaves em pt-BR.json/en.json editando o texto
type: project
category: gotcha
date: 2026-10-24
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
