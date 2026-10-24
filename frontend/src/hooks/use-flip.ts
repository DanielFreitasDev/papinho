import { type RefObject, useLayoutEffect, useRef } from "react"

import { motionEase, motionMs, prefersReducedMotion } from "@/lib/motion"

/**
 * Itens de uma lista que mudam de lugar deslizam até o novo (técnica FLIP, só transform): depois de cada commit,
 * compara a posição de cada [data-flip-key] dentro do contêiner com a do commit anterior e anima a diferença; item
 * novo surge com um fade curto. A posição é relativa ao contêiner: rolar a lista não conta como mudança. Nada anima
 * no primeiro desenho nem com movimento reduzido.
 */
export function useFlip(container: RefObject<HTMLElement | null>) {
  const positions = useRef(new Map<string, number>())
  useLayoutEffect(() => {
    const root = container.current
    if (!root) {
      return
    }
    const previous = positions.current
    const animate = previous.size > 0 && !prefersReducedMotion()
    const origin = root.getBoundingClientRect().top
    const next = new Map<string, number>()
    for (const item of root.querySelectorAll<HTMLElement>("[data-flip-key]")) {
      const key = item.dataset.flipKey ?? ""
      const top = item.getBoundingClientRect().top - origin
      next.set(key, top)
      if (!animate) {
        continue
      }
      const before = previous.get(key)
      if (before === undefined) {
        item.animate(
          [
            { opacity: 0, transform: "scale(0.98)" },
            { opacity: 1, transform: "none" },
          ],
          { duration: motionMs("base"), easing: motionEase("enter") }
        )
      } else if (Math.abs(before - top) > 0.5) {
        item.animate(
          [
            { transform: `translateY(${before - top}px)` },
            { transform: "none" },
          ],
          { duration: motionMs("base"), easing: motionEase("standard") }
        )
      }
    }
    positions.current = next
  })
}
