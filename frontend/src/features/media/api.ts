import Uppy from "@uppy/core"
import Tus from "@uppy/tus"

import i18n from "@/lib/i18n"
import { readCookie } from "@/lib/api-client"

/** Limite por arquivo (MD-01); o servidor também recusa na criação do upload. */
export const MAX_FILE_SIZE = 1024 * 1024 * 1024

/** Pedaços de 50 MB: retomada fina sem estourar limites de proxy. */
const CHUNK_SIZE = 50 * 1024 * 1024

export type MediaKind = "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT"

/**
 * Endereço do arquivo. O anexo de uma conversa trancada vem com um grant que vai na URL: o `<img>` não manda o
 * cabeçalho do token (PV-03).
 */
export function mediaUrl(
  media: string | { id: string; grant?: string | null },
  part: "content" | "thumbnail" = "content",
  download = false
) {
  const { id, grant } = typeof media === "string" ? { id: media } : media
  const params = new URLSearchParams()
  if (download) {
    params.set("download", "true")
  }
  if (grant) {
    params.set("grant", grant)
  }
  const query = params.toString()
  return `/api/v1/media/${id}/${part}${query ? `?${query}` : ""}`
}

/** Tipo provável pelo arquivo local, só para a prévia enquanto envia; o servidor decide pelo conteúdo. */
export function guessKind(file: Blob): MediaKind {
  if (/^image\/(jpeg|png|gif|webp)$/.test(file.type)) {
    return "IMAGE"
  }
  if (file.type.startsWith("video/")) {
    return "VIDEO"
  }
  if (file.type.startsWith("audio/")) {
    return "AUDIO"
  }
  return "DOCUMENT"
}

export type UploadHandle = {
  /** Resolve com o id da mídia criada. */
  done: Promise<string>
  /** Cancela e apaga o parcial no servidor (tus termination). */
  cancel: () => void
}

/**
 * Upload retomável com Uppy + tus (ADR 0009): pedaços com retomada automática após queda e cancelamento. O
 * cabeçalho CSRF é lido do cookie a cada requisição.
 */
export function uploadFile(
  file: File,
  onProgress: (fraction: number) => void
): UploadHandle {
  const uppy = new Uppy({
    autoProceed: false,
    allowMultipleUploadBatches: false,
  }).use(Tus, {
    endpoint: "/api/v1/uploads",
    chunkSize: CHUNK_SIZE,
    retryDelays: [0, 1000, 3000, 5000, 10000],
    removeFingerprintOnSuccess: true,
    withCredentials: true,
    onBeforeRequest: (request) => {
      const token = readCookie("XSRF-TOKEN")
      if (token) {
        request.setHeader("X-XSRF-TOKEN", decodeURIComponent(token))
      }
      request.setHeader("Accept-Language", i18n.language || "pt-BR")
    },
  })
  uppy.addFile({ name: file.name, type: file.type, data: file })
  let settled = false
  const done = new Promise<string>((resolve, reject) => {
    uppy.on("upload-progress", (_file, progress) => {
      if (progress.bytesTotal) {
        onProgress(progress.bytesUploaded / progress.bytesTotal)
      }
    })
    uppy.on("upload-success", (_file, response) => {
      settled = true
      const url = response.uploadURL ?? ""
      // Sem destroy(): ele tentaria cancelar (DELETE) o upload já concluído.
      resolve(url.slice(url.lastIndexOf("/") + 1))
    })
    uppy.on("upload-error", (_file, error) => {
      settled = true
      reject(error)
    })
    uppy.on("cancel-all", () => {
      if (!settled) {
        settled = true
        reject(new DOMException("cancelled", "AbortError"))
      }
    })
  })
  void uppy.upload()
  return {
    done,
    cancel: () => {
      if (!settled) {
        uppy.cancelAll()
        uppy.destroy()
      }
    },
  }
}

export function formatBytes(bytes: number, locale: string): string {
  const units = ["B", "KB", "MB", "GB"]
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: unit === 0 ? 0 : 1 }).format(value)} ${units[unit]}`
}
