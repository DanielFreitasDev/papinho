import {
  WHISPER_MODELS,
  type WhisperModel,
  type WorkerResponse,
} from "./protocol"

const MODEL_KEY = "papinho.whisper-model"

/** Modelo escolhido neste aparelho (o download é por navegador). */
export function whisperModel(): WhisperModel {
  try {
    const saved = localStorage.getItem(MODEL_KEY)
    return saved === "small" ? "small" : "base"
  } catch {
    return "base"
  }
}

export function setWhisperModel(model: WhisperModel) {
  try {
    localStorage.setItem(MODEL_KEY, model)
  } catch {
    // Sem armazenamento local: fica o padrão.
  }
}

let worker: Worker | null = null

function getWorker() {
  worker ??= new Worker(new URL("./whisper.worker.ts", import.meta.url), {
    type: "module",
  })
  return worker
}

/** Converte o áudio para PCM mono de 16 kHz, o formato que o Whisper espera. */
export async function decodeTo16k(data: ArrayBuffer): Promise<Float32Array> {
  const context = new AudioContext()
  try {
    const decoded = await context.decodeAudioData(data)
    const offline = new OfflineAudioContext(
      1,
      Math.ceil(decoded.duration * 16_000),
      16_000
    )
    const source = offline.createBufferSource()
    source.buffer = decoded
    source.connect(offline.destination)
    source.start()
    const rendered = await offline.startRendering()
    return rendered.getChannelData(0)
  } finally {
    void context.close()
  }
}

/**
 * Transcreve no próprio navegador (AU-04). `onProgress` recebe a fração baixada do modelo (só na primeira vez
 * em cada aparelho).
 */
export async function transcribeInBrowser(
  audioUrl: string,
  language: string | null,
  onProgress: (fraction: number) => void
): Promise<string> {
  const response = await fetch(audioUrl, { credentials: "same-origin" })
  const audio = await decodeTo16k(await response.arrayBuffer())
  const id = crypto.randomUUID()
  const target = getWorker()
  const files = new Map<string, { loaded: number; total: number }>()
  return new Promise<string>((resolve, reject) => {
    const listener = (event: MessageEvent<WorkerResponse>) => {
      const message = event.data
      if (message.id !== id) {
        return
      }
      if (message.type === "progress") {
        files.set(message.file, {
          loaded: message.loaded,
          total: message.total,
        })
        const totals = [...files.values()].reduce(
          (sum, item) => ({
            loaded: sum.loaded + item.loaded,
            total: sum.total + item.total,
          }),
          { loaded: 0, total: 0 }
        )
        onProgress(totals.total ? totals.loaded / totals.total : 0)
      } else if (message.type === "loaded") {
        onProgress(1)
      } else {
        target.removeEventListener("message", listener)
        if (message.type === "result") {
          resolve(message.text)
        } else {
          reject(new Error(message.message))
        }
      }
    }
    target.addEventListener("message", listener)
    target.postMessage(
      { id, model: WHISPER_MODELS[whisperModel()], audio, language },
      [audio.buffer]
    )
  })
}
