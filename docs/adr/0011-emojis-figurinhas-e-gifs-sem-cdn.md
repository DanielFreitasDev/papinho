---
status: aceita
date: 2026-10-15
---

# 0011 — Emojis, figurinhas e GIFs servidos pelo próprio Papinho

## Contexto e problema

MS-10 pede seletor de emojis com busca e recentes, figurinhas próprias em WebP (inclusive animadas) e GIFs de
um provedor externo opcional. A CSP é estrita (`script-src 'self'; style-src 'self'`, `img-src` e
`connect-src` sem terceiros) e o produto é auto-hospedado: o navegador não deve falar com CDNs nem com o
provedor de GIF (isso entregaria o IP e o que a pessoa busca).

## Decisão

- **Emojis**: seletor próprio sobre o `emojibase-data` 17 (MIT, padrão de fato dos seletores), carregado sob
  demanda como pedaço do bundle e servido pelo JAR. Recentes ficam no `localStorage`, por pessoa (conveniência
  local, não dado do servidor).
- **Figurinhas**: biblioteca da instância (pacotes enviados pelos usuários e visíveis para todos), arquivos
  WebP de até 1 MB que passam só pela limpeza de EXIF/XMP (a animação fica). Tirar uma figurinha ou o pacote
  é exclusão lógica: as mensagens já enviadas continuam mostrando.
- **GIFs**: porta `GifProvider` com Tenor v2, **desligada por padrão** (`PAPINHO_GIFS_PROVIDER=none`). Com o
  provedor ligado, o servidor faz a busca, guarda as URLs por 30 minutos e entrega ao cliente só ids; prévias
  passam por `/api/v1/gifs/{id}/preview` e o GIF escolhido é baixado (apenas de `app.gifs.media-hosts`, sem
  redirecionamento, até 8 MB) e vira um arquivo de mídia de quem enviou, pelo mesmo caminho do upload (cota,
  Tika, antivírus).

## Consequências

- Boas: nada fora da CSP; nenhum dado de quem conversa vai para terceiros; figurinhas e GIFs enviados ficam no
  servidor como qualquer anexo (backup, exclusão, cota).
- Ruins: o seletor próprio não tem tons de pele nem a virtualização das bibliotecas; o JSON de emojis de cada
  idioma (≈580 KB, ≈80 KB comprimido) é baixado na primeira abertura.

## Alternativas consideradas

- `frimousse` (base do emoji-picker da comunidade shadcn): ainda 0.x, contra a regra de só usar versões
  estáveis; e busca os dados numa CDN por padrão.
- `emoji-mart` e `emoji-picker-react`: injetam `<style>` em tempo de execução (CSS-in-JS ou Shadow DOM com
  estilos), o que a CSP estrita bloqueia.
- GIF direto do provedor no navegador: exigiria liberar o provedor na CSP e entregaria o IP e as buscas.
