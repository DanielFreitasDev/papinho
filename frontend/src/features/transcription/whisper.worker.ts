/// <reference lib="webworker" />
import {
  env,
  pipeline,
  type AutomaticSpeechRecognitionPipeline,
} from "@huggingface/transformers"
import webgpuMjs from "onnxruntime-web/ort-wasm-simd-threaded.asyncify.mjs?url"
import webgpuWasm from "onnxruntime-web/ort-wasm-simd-threaded.asyncify.wasm?url"
import wasmMjs from "onnxruntime-web/ort-wasm-simd-threaded.mjs?url"
import wasmWasm from "onnxruntime-web/ort-wasm-simd-threaded.wasm?url"

import type { WorkerRequest, WorkerResponse } from "./protocol"

/**
 * Whisper no navegador (AU-04) com Transformers.js num Web Worker: WebGPU quando houver, senão WASM. O modelo
 * é baixado sob demanda do Hugging Face e fica no cache do navegador; o runtime ONNX é servido pelo próprio
 * Papinho (sem CDN de terceiros).
 */
env.allowLocalModels = false
env.useBrowserCache = true
// Com o cache de WASM, o Transformers.js reescreve o .mjs do runtime e o importa de uma URL blob:, que a CSP
// (script-src 'self') recusa. Sem ele, o runtime vem direto do Papinho, já com cache HTTP de um ano.
env.useWasmCache = false
// O WebGPU do onnxruntime só existe no build "asyncify" (o único que exporta webgpuInit; o "jsep" falha com "no
// available backend"), que também roda WASM. Sem WebGPU no navegador, o build simples, menor e só WASM, basta: a
// mesma escolha que o Transformers.js faz quando usa a CDN.
const webgpuApi = "gpu" in navigator
if (env.backends.onnx.wasm) {
  env.backends.onnx.wasm.wasmPaths = webgpuApi
    ? { mjs: webgpuMjs, wasm: webgpuWasm }
    : { mjs: wasmMjs, wasm: wasmWasm }
}

const scope = self as unknown as DedicatedWorkerGlobalScope
const loaded = new Map<string, Promise<AutomaticSpeechRecognitionPipeline>>()

function post(message: WorkerResponse) {
  scope.postMessage(message)
}

function create(model: string, id: string, device: "webgpu" | "wasm") {
  return pipeline("automatic-speech-recognition", model, {
    device,
    dtype:
      device === "webgpu"
        ? { encoder_model: "fp32", decoder_model_merged: "q4" }
        : "q8",
    progress_callback: (progress: {
      status: string
      file?: string
      loaded?: number
      total?: number
    }) => {
      if (progress.status === "progress" && progress.total) {
        post({
          type: "progress",
          id,
          file: progress.file ?? "",
          loaded: progress.loaded ?? 0,
          total: progress.total,
        })
      }
    },
  }) as Promise<AutomaticSpeechRecognitionPipeline>
}

async function load(model: string, id: string) {
  const key = model
  let running = loaded.get(key)
  if (!running) {
    const webgpu = webgpuApi && (await hasAdapter())
    running = webgpu
      ? // Adaptador presente não garante WebGPU funcionando (driver, memória): aí vale o WASM.
        create(model, id, "webgpu").catch(() => create(model, id, "wasm"))
      : create(model, id, "wasm")
    loaded.set(key, running)
    running.catch(() => loaded.delete(key))
  }
  return running
}

async function hasAdapter(): Promise<boolean> {
  try {
    const gpu = (
      navigator as Navigator & { gpu?: { requestAdapter(): Promise<unknown> } }
    ).gpu
    return Boolean(await gpu?.requestAdapter())
  } catch {
    return false
  }
}

scope.addEventListener(
  "message",
  async (event: MessageEvent<WorkerRequest>) => {
    const request = event.data
    try {
      const recognizer = await load(request.model, request.id)
      post({ type: "loaded", id: request.id })
      const output = await recognizer(request.audio, {
        chunk_length_s: 30,
        stride_length_s: 5,
        language: request.language ?? undefined,
        task: "transcribe",
      })
      const text = Array.isArray(output)
        ? output.map((part) => part.text).join(" ")
        : output.text
      post({ type: "result", id: request.id, text: text.trim() })
    } catch (error) {
      post({
        type: "error",
        id: request.id,
        message: error instanceof Error ? error.message : String(error),
      })
    }
  }
)
