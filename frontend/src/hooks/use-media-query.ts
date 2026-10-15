import { useSyncExternalStore } from "react"

/** Acompanha uma media query (por exemplo, para trocar painel lateral por gaveta em telas menores). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query)
      list.addEventListener("change", onChange)
      return () => list.removeEventListener("change", onChange)
    },
    () => window.matchMedia(query).matches,
    () => false
  )
}
