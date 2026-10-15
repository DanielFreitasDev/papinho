/** Mensagens entre a página e o worker do Whisper (AU-04). */
export type WorkerRequest = {
  id: string
  model: string
  /** PCM mono em 16 kHz. */
  audio: Float32Array
  language: string | null
}

export type WorkerResponse =
  | {
      type: "progress"
      id: string
      file: string
      loaded: number
      total: number
    }
  | { type: "loaded"; id: string }
  | { type: "result"; id: string; text: string }
  | { type: "error"; id: string; message: string }

/** Modelos multilíngues oferecidos (AU-04); o base é o padrão. */
export const WHISPER_MODELS = {
  base: "onnx-community/whisper-base",
  small: "onnx-community/whisper-small",
} as const

export type WhisperModel = keyof typeof WHISPER_MODELS
