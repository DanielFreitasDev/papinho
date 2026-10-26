import { useState, type MouseEvent } from "react"

/**
 * Menu próprio no botão direito, como no WhatsApp Web: abre um DropdownMenu controlado no ponto do clique, no lugar
 * do menu do navegador. Só com mouse; no toque o pressionar longo continua com o navegador (e com o PV-01), e as
 * ações ficam no botão ⋮. Um menu mais interno que já tratou o clique ({@code preventDefault}) tem a vez.
 */
export function useContextMenu() {
  const [open, setOpen] = useState(false)
  const [point, setPoint] = useState<{ x: number; y: number } | null>(null)
  return {
    open,
    /** Para o {@code onOpenChange} do DropdownMenu. */
    onOpenChange(next: boolean) {
      // Aberto pelo ⋮: o menu volta a sair do botão. Ao fechar, fica onde estava durante a animação.
      if (next) {
        setPoint(null)
      }
      setOpen(next)
    },
    /** Para o {@code anchor} do DropdownMenuContent. */
    anchor: point
      ? { getBoundingClientRect: () => new DOMRect(point.x, point.y, 0, 0) }
      : undefined,
    onContextMenu(event: MouseEvent) {
      if (
        event.defaultPrevented ||
        !window.matchMedia("(pointer: fine)").matches
      ) {
        return
      }
      event.preventDefault()
      setPoint({ x: event.clientX, y: event.clientY })
      setOpen(true)
    },
  }
}

export type ContextMenu = ReturnType<typeof useContextMenu>
