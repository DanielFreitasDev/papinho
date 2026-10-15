---
status: aceita
date: 2026-10-15
---

# 0008 — Transcrição: Whisper no navegador por padrão, servidores OpenAI-compatíveis como alternativa

## Contexto e problema

Transcrever áudios (AU-03) melhora acessibilidade e privacidade, mas mandar áudio a terceiros fere a
proposta do produto, e a máquina do dono pode não ter GPU.

## Decisão

- **Padrão: no navegador** de quem recebe, com **Transformers.js** num **Web Worker**, WebGPU com
  fallback para WASM, modelo multilíngue `whisper-base` (ou `small` quantizado, configurável), baixado
  sob demanda com progresso e cache.
- **Alternativa: provedores no servidor** no padrão OpenAI-compatible
  (`POST {base}/v1/audio/transcriptions`, multipart, `Authorization: Bearer`, resposta `{"text": ...}`):
  Groq, Whisper próprio (com `hotwords` e `GET {base}/health`), OpenAI e URL personalizada.
- O backend é o proxy: chaves cifradas em repouso, nunca enviadas ao cliente nem aos logs;
  **User-Agent `Papinho/<versão>`** (nunca começando com `OpenAI/`, bloqueado pela Cloudflare).
- O resultado fica em cache no servidor para os demais membros; áudio de visualização única nunca tem
  transcrição persistida. O admin define o padrão; o usuário escolhe nas configurações.

## Consequências

- Boas: privacidade por padrão, sem custo; servidores rápidos quando o dono quiser.
- Ruins: o primeiro uso no navegador baixa dezenas de MB; a CSP precisa liberar `wasm-unsafe-eval` e a
  origem do modelo quando a funcionalidade entrar.

## Alternativas consideradas

- Só servidor: exige GPU ou envio a terceiros.
- Web Speech API: depende do provedor do navegador e não transcreve arquivos.

## Notas de implementação (Fase 3, 2026-10-16)

- **Transformers.js 4** (`@huggingface/transformers`, linha estável desde março de 2026) num Web Worker;
  WebGPU quando há adaptador, senão WASM (`q8`). Modelos `onnx-community/whisper-base` (padrão) e
  `whisper-small`, escolhidos por aparelho.
- O runtime ONNX (`.wasm`/`.mjs`) é servido pelo próprio JAR, sem CDN; a CSP ganhou `'wasm-unsafe-eval'` e
  `connect-src` do Hugging Face (`huggingface.co`, `*.huggingface.co`, `*.hf.co`).
- Provedores no servidor: o admin cadastra quantos quiser; o "padrão da instância" pode ser um deles ou o
  navegador. O pedido ao servidor responde 409 quando a escolha é o navegador, e o cliente envia o texto.
