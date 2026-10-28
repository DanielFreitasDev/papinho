---
name: Visualizador de PDF com o pdf.js 6 sob a CSP estrita
description: pdf.js carregado só ao abrir, dados (fontes, cmaps, ICC, WASM) servidos em /assets/pdfjs-<versão>/, sem isEvalSupported no 6, TextLayer pede --total-scale-factor, cartão sobre o Attachment do shadcn
type: project
category: decision
date: 2026-10-28
---
O PDF abre dentro do app pelo pdf.js (Mozilla) sem afrouxar a CSP (`script-src 'self' 'wasm-unsafe-eval'`,
`style-src 'self'`).

**Problema/Contexto:** o dono pediu o PDF como no WhatsApp: prévia, páginas e um visualizador sem baixar
(2026-10-28). O servidor gera a prévia (ver [[decisao-previa-de-pdf-em-processo-separado]]); o visualizador é do navegador.
**Escolhas e por quê:**
- `pdfjs-dist` 6.4.299 (lançada em 2026-10-03) por `import()` só ao abrir: o pacote e o worker passam de 2 MB. O
  worker vem por `?url` do Vite (mesma origem) e fica fora do precache do service worker, como os dados.
- O pdf.js 6 não usa mais `eval` e tirou a opção `isEvalSupported` (o TypeScript recusa); nada de `unsafe-eval`.
- Fontes padrão (Helvetica sem embutir vira Foxit/Liberation), mapas de caracteres, perfis ICC e decodificadores
  WASM saem do próprio Papinho em `/assets/pdfjs-<versão>/`: um plugin no `vite.config.ts` copia as pastas do
  `node_modules` no build e as serve no `pnpm dev`. O `/assets/**` do servidor já entrega qualquer arquivo dali com
  cache de um ano; a versão no caminho troca a URL a cada atualização.
- A camada de texto (selecionar e copiar) é o `TextLayer` do pdf.js com uma versão enxuta do `pdf_viewer.css` no
  `index.css`; ele precisa de `--total-scale-factor` no contêiner (posto por `style.setProperty`, que a CSP aceita).
- Só as páginas perto da tela são desenhadas (IntersectionObserver); o tamanho reservado é o da primeira página até a
  própria carregar.
- O cartão do documento continua o Attachment do shadcn (A95 da auditoria), com a prévia como uma linha inteira no
  alto; o `media.spec.ts` confere os `data-slot` dele.
**Como aplicar:** dependência nova que busca arquivos em tempo de execução vai para `/assets/<nome>-<versão>/`, não
para a raiz (o `/**` do servidor cai no SPA). Confira contra o JAR: o `pnpm dev` não manda a CSP.
**Evidência:** e2e `documents.spec.ts` (Chromium desktop e celular) contra o JAR com a CSP: prévia de 480 px, "2
páginas", texto da página na camada, página 2 de 2, zoom e download; `media.spec.ts` verde depois de voltar ao
Attachment (commits de 2026-10-28).
