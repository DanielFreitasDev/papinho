---
name: Mídia — ImageIO, Uppy, MockMvc e TRUNCATE
description: armadilhas da Fase 3 com metadados JPEG, cancelamento do Uppy, query codificada no MockMvc e TRUNCATE em cascata
type: project
category: gotcha
date: 2026-10-15
---
- **ImageIO e EXIF:** ler metadados JPEG com `reader.setInput(stream, true, false)` lança exceção em arquivos
  comuns de celular (APP1 "Exif" antes do APP0 JFIF). A orientação é lida percorrendo os segmentos do JPEG
  (`ImageProcessing.jpegOrientation`) e a imagem é lida ignorando metadados.
- **Uppy + tus:** `uppy.destroy()` depois do `upload-success` cancela os uploads e manda `DELETE` para o upload
  já concluído (404 vira `pageerror` no e2e). Não destrua a instância no sucesso.
- **MockMvc:** `get("/x?url=" + URLEncoder.encode(...))` codifica de novo (`%3A` vira `%253A`); passe a URL crua
  no template.
- **TRUNCATE ... CASCADE** apaga também tabelas que referenciam a truncada — inclusive linhas únicas de
  configuração (`transcription_settings`). No `DatabaseCleaner`, use `DELETE` para essas tabelas.
- **Vite + onnxruntime-web:** importar `onnxruntime-web/dist/...wasm?url` falha (exports); use os subcaminhos
  exportados `onnxruntime-web/ort-wasm-simd-threaded.jsep.wasm?url`. A rota nova do TanStack só entra no
  `routeTree.gen.ts` com `vite build` (o `pnpm build` roda `tsc -b` antes e falha).

**Evidência:** falhas e correções em `MediaHelpersTest`, `media.spec.ts`, `LinkPreviewIT`, `TranscriptionIT` e no
`pnpm build`, 2026-10-15.
