---
name: Mídia — ImageIO, Uppy, MockMvc e TRUNCATE
description: armadilhas da Fase 3 com metadados JPEG, cancelamento do Uppy, query codificada no MockMvc e TRUNCATE em cascata
type: project
category: gotcha
date: 2026-10-15
updated: 2026-10-16
---
- **ImageIO e EXIF:** ler metadados JPEG com `reader.setInput(stream, true, false)` lança exceção em arquivos
  comuns de celular (APP1 "Exif" antes do APP0 JFIF). A orientação é lida percorrendo os segmentos do JPEG
  (`ImageProcessing.jpegOrientation`) e a imagem é lida ignorando metadados.
- **Uppy + tus:** `uppy.destroy()` depois do `upload-success` cancela os uploads e manda `DELETE` para o upload
  já concluído (404 vira `pageerror` no e2e). Não destrua a instância no sucesso.
- **MockMvc:** `get("/x?url=" + URLEncoder.encode(...))` codifica de novo (`%3A` vira `%253A`); passe a URL crua
  no template.
  **Voltou (2026-10-17, achado A65):** o helper de busca do `SearchIT` codificava a consulta à mão, e nenhuma consulta
  acentuada chegava certa ao servidor (`reuni%C3%A3o` virava texto); os testes passavam porque só usavam ASCII. Use
  `MockMvcRequestBuilders.get(path).queryParam("q", valor)` em vez de montar a query string.
- **TRUNCATE ... CASCADE** apaga também tabelas que referenciam a truncada — inclusive linhas únicas de
  configuração (`transcription_settings`). No `DatabaseCleaner`, use `DELETE` para essas tabelas.
- **Vite + onnxruntime-web:** importar `onnxruntime-web/dist/...wasm?url` falha (exports); use os subcaminhos
  exportados (`onnxruntime-web/ort-wasm-simd-threaded.asyncify.wasm?url`). A rota nova do TanStack só entra no
  `routeTree.gen.ts` com `vite build` (o `pnpm build` roda `tsc -b` antes e falha).
- **Qual build do onnxruntime-web (passada 8):** com o Transformers.js 4, o WebGPU só existe nos builds `asyncify`
  e `jspi` (os que exportam `webgpuInit`); com o `jsep` o WASM funciona, mas com um adaptador WebGPU o pipeline falha
  com "no available backend found ... webgpuInit is not a function". O worker usa `asyncify` quando há
  `navigator.gpu` e o build simples (só WASM, 13,6 MB contra 25,6 MB) no resto, e cai para WASM se o WebGPU falhar.
  Para ver o erro real do worker, rode-o direto numa página da instância (o asset é público) e leia a mensagem que
  ele devolve: a interface só mostra "Não foi possível transcrever".
- **Provar o AU-04:** o Chromium headless não tem adaptador WebGPU (`requestAdapter()` nulo) e cobre o WASM; com
  `--enable-unsafe-webgpu` aparece o adaptador por software (SwiftShader), lento (~3,7 min para 11 s de fala) mas
  funcional. Os arquivos baixados do modelo dizem o caminho: `encoder_model.onnx` + `decoder_model_merged_q4.onnx`
  no WebGPU, os `_quantized` no WASM.

**Evidência:** falhas e correções em `MediaHelpersTest`, `media.spec.ts`, `LinkPreviewIT`, `TranscriptionIT` e no
`pnpm build`, 2026-10-15.

Passada 6 (2026-10-16): o `TRUNCATE ... CASCADE` do `DatabaseCleaner` pode ser vítima de deadlock com um ouvinte
assíncrono (`@ApplicationModuleListener`) do teste anterior que ainda está em transação
(`PessimisticLockingFailureException`, "deadlock detected" no `MessagingIT`). O cleaner repete com espera
crescente; não trate como teste instável.

**Evidência (passada 8):** sonda do worker com `--enable-unsafe-webgpu` devolveu o erro do `webgpuInit` com o
`jsep`; `whisper.spec.ts` verde nos projetos `desktop-chromium` (WASM, 1,1 min) e `whisper-webgpu` (3,7 min) e a
sonda no Firefox (build simples) transcreveu, 2026-10-16.
