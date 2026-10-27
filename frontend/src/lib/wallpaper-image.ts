import { useSyncExternalStore } from "react"

import { getDisplayPrefs, setDisplayPrefs } from "@/lib/display-prefs"
import { onLogout } from "@/lib/logout-cleanup"

/**
 * Imagem própria como papel de parede da conversa (UI-02). Fica só neste aparelho, no IndexedDB, e nunca vai ao
 * servidor. Antes de guardar, a foto é redesenhada num canvas: perde os metadados (EXIF com localização) e fica com
 * no máximo 2560 px. A tela usa uma URL blob: (a CSP aceita blob: em img-src) numa propriedade do CSSOM. Sair da conta
 * apaga a imagem, como os outros dados da pessoa neste navegador (NT-05).
 */
const DATABASE = "papinho-device"
const STORE = "files"
const KEY = "wallpaper"
const MAX_SIDE = 2560

let url: string | null = null
const listeners = new Set<() => void>()

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1)
    request.onupgradeneeded = () => request.result.createObjectStore(STORE)
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function run<T>(
  mode: IDBTransactionMode,
  work: (store: IDBObjectStore) => IDBRequest<T>
): Promise<T> {
  const db = await open()
  try {
    return await new Promise<T>((resolve, reject) => {
      const request = work(db.transaction(STORE, mode).objectStore(STORE))
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  } finally {
    db.close()
  }
}

/** Mostra (ou tira) a imagem na tela, trocando a URL blob: e liberando a anterior. */
function show(blob: Blob | null) {
  if (url) {
    URL.revokeObjectURL(url)
  }
  url = blob ? URL.createObjectURL(blob) : null
  const root = document.documentElement
  if (url) {
    root.style.setProperty("--wallpaper-image", `url("${url}")`)
  } else {
    root.style.removeProperty("--wallpaper-image")
  }
  listeners.forEach((listener) => listener())
}

/** Redesenha a foto (sem EXIF, no máximo 2560 px) em JPEG; falha com o que não for imagem que o navegador abra. */
async function prepare(file: Blob): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  try {
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement("canvas")
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    canvas
      .getContext("2d")!
      .drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, "image/jpeg", 0.86)
    )
    if (!blob) {
      throw new Error("canvas sem imagem")
    }
    return blob
  } finally {
    bitmap.close()
  }
}

/** Abre o app com a imagem guardada, se a escolha é ela. */
export async function restoreWallpaperImage() {
  if (getDisplayPrefs().wallpaper !== "image") {
    return
  }
  try {
    const blob = await run<Blob | undefined>("readonly", (store) =>
      store.get(KEY)
    )
    show(blob ?? null)
    if (!blob) {
      setDisplayPrefs({ wallpaper: "none" })
    }
  } catch {
    // Sem IndexedDB (janela privada de alguns navegadores): fica a cor da conversa.
    setDisplayPrefs({ wallpaper: "none" })
  }
}

/** Guarda a foto escolhida e passa a usá-la. */
export async function chooseWallpaperImage(file: Blob) {
  const blob = await prepare(file)
  await run("readwrite", (store) => store.put(blob, KEY))
  show(blob)
  setDisplayPrefs({ wallpaper: "image" })
}

/** Apaga a imagem; se ela estava em uso, volta à cor da conversa. */
export async function removeWallpaperImage() {
  show(null)
  if (getDisplayPrefs().wallpaper === "image") {
    setDisplayPrefs({ wallpaper: "none" })
  }
  await run("readwrite", (store) => store.delete(KEY))
}

/** URL da imagem em uso (para a amostra e a prévia), ou null. */
export function useWallpaperImageUrl() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => url
  )
}

onLogout(() => removeWallpaperImage().catch(() => undefined))
