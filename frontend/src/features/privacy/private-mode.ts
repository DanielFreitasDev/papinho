import { useEffect } from "react"
import { useQuery } from "@tanstack/react-query"

import { privacyQueries, useUpdatePrivacy } from "./api"
import type { BlurSettings } from "./types"

/** Atrasos aceitos para revelar ao passar o mouse; o CSS tem uma regra para cada um. */
export const REVEAL_DELAYS = [0, 500, 1000, 2000, 3000] as const

/** Itens que o modo privado desfoca, na ordem das configurações. */
export const BLUR_ITEMS = [
  "messages",
  "previews",
  "names",
  "photos",
  "media",
] as const

/** Atalho global do modo privado (PV-01): Alt+Shift+P. */
export function isPrivateModeShortcut(event: KeyboardEvent): boolean {
  return (
    event.altKey &&
    event.shiftKey &&
    !event.ctrlKey &&
    !event.metaKey &&
    event.code === "KeyP"
  )
}

/** Atributos de <html> que ligam o desfoque (lidos pelo index.css). */
export function blurAttributes(blur: BlurSettings | undefined): {
  blur: string | null
  delay: string
} {
  if (!blur?.privateMode) {
    return { blur: null, delay: "0" }
  }
  const items = BLUR_ITEMS.filter((item) => blur[item])
  return {
    blur: items.length > 0 ? items.join(" ") : null,
    delay: String(
      REVEAL_DELAYS.find((delay) => delay >= blur.revealDelayMs) ?? 3000
    ),
  }
}

/** Quanto o toque precisa durar para revelar, e quanto tempo o item fica revelado depois de soltar. */
const LONG_PRESS_MS = 450
const KEEP_REVEALED_MS = 2500

/**
 * Aplica o modo privado (PV-01) à página: atributos em <html>, atalho global Alt+Shift+P para alternar e toque
 * longo para revelar no celular.
 */
export function usePrivateMode() {
  const settings = useQuery(privacyQueries.settings())
  const update = useUpdatePrivacy()
  const blur = settings.data?.settings.blur
  const { blur: items, delay } = blurAttributes(blur)

  useEffect(() => {
    const root = document.documentElement
    if (items) {
      root.dataset.blur = items
      root.dataset.revealDelay = delay
    } else {
      delete root.dataset.blur
      delete root.dataset.revealDelay
    }
    return () => {
      delete root.dataset.blur
      delete root.dataset.revealDelay
    }
  }, [items, delay])

  const privateMode = blur?.privateMode ?? false
  const ready = settings.isSuccess
  const { mutate } = update
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (ready && isPrivateModeShortcut(event)) {
        event.preventDefault()
        mutate({ privateMode: !privateMode })
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [ready, privateMode, mutate])

  useEffect(() => {
    if (!items) {
      return
    }
    let timer: number | undefined
    let revealed: HTMLElement | null = null
    const hide = new Map<HTMLElement, number>()
    const onDown = (event: PointerEvent) => {
      if (event.pointerType !== "touch") {
        return
      }
      const target = (event.target as HTMLElement | null)?.closest<HTMLElement>(
        "[data-private]"
      )
      if (!target) {
        return
      }
      timer = window.setTimeout(() => {
        window.clearTimeout(hide.get(target))
        target.dataset.revealed = ""
        revealed = target
      }, LONG_PRESS_MS)
    }
    const onUp = () => {
      window.clearTimeout(timer)
      const element = revealed
      revealed = null
      if (element) {
        hide.set(
          element,
          window.setTimeout(() => {
            delete element.dataset.revealed
            hide.delete(element)
          }, KEEP_REVEALED_MS)
        )
      }
    }
    document.addEventListener("pointerdown", onDown)
    document.addEventListener("pointerup", onUp)
    document.addEventListener("pointercancel", onUp)
    return () => {
      window.clearTimeout(timer)
      hide.forEach((id, element) => {
        window.clearTimeout(id)
        delete element.dataset.revealed
      })
      document.removeEventListener("pointerdown", onDown)
      document.removeEventListener("pointerup", onUp)
      document.removeEventListener("pointercancel", onUp)
    }
  }, [items])
}
