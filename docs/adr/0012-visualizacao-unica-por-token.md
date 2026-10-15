---
status: aceita
date: 2026-10-15
---

# 0012 — Visualização única por token emitido uma vez por destinatário

## Contexto e problema

MS-08 pede foto, vídeo e áudio de visualização única com token de uso único, `Cache-Control: no-store`,
arquivo apagado depois de aberto (ou após 14 dias) e nada cacheado pelo service worker. Vídeo e áudio são
lidos pelo navegador em várias requisições com `Range`, então um token que morre na primeira requisição quebra
a reprodução.

## Decisão

- O id do arquivo de uma mensagem de visualização única **nunca vai para o cliente**; as rotas normais de mídia
  recusam esse arquivo para todos, inclusive para quem enviou (`MediaAccessPolicy.restricted`).
- Cada destinatário pede **uma única vez** um token (`POST /messages/{id}/view-once`): o servidor grava a
  abertura com o hash do token e a validade de 5 minutos; um segundo pedido recebe 410. Dentro da validade o
  mesmo token serve as faixas do vídeo ou do áudio, sempre com `no-store`.
- Um job com ShedLock tira o arquivo do servidor quando todos os destinatários ativos abriram e os tokens
  venceram, ou após 14 dias; a mensagem fica como "Foto aberta".
- Áudio de visualização única não é transcrito (a transcrição usa a rota de mídia, que recusa o arquivo).
- No cliente: sem download, encaminhar, copiar ou menu de contexto, marca d'água em SVG com o `@usuario` de
  quem vê, desfoque quando a janela perde o foco e o aviso de que o navegador não impede capturas de tela.

## Consequências

- Boas: reabrir é impossível pelo servidor; vídeos longos tocam; o arquivo some sem ação manual.
- Ruins: durante os 5 minutos o token pode ser repetido pela mesma sessão (necessário para `Range`); capturas
  de tela continuam possíveis, como em qualquer navegador.

## Alternativas consideradas

- Token invalidado na primeira requisição: quebra vídeo e áudio com `Range`.
- Entregar o conteúdo cifrado ao cliente: não impede captura e complica sem ganho real.
