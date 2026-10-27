import { prefersReducedMotion } from "@/lib/motion"

/**
 * Onda de toque: em todo elemento com data-ripple (botões, itens de menu e de lista, abas), um círculo cresce a partir
 * do ponto tocado e some, com a cor do texto dele (index.css). Um ouvinte só, no documento, para o app inteiro. As
 * coordenadas vão por propriedades do CSSOM (a CSP barra estilo inline no HTML, não o CSSOM). Com movimento reduzido,
 * não há onda.
 */
let installed = false

function start(event: PointerEvent) {
  if (event.button !== 0 || prefersReducedMotion()) {
    return
  }
  const host = (event.target as Element | null)?.closest<HTMLElement>(
    "[data-ripple]"
  )
  if (
    !host ||
    host.matches(":disabled, [aria-disabled='true'], [data-disabled]")
  ) {
    return
  }
  const box = host.getBoundingClientRect()
  const x = event.clientX - box.left
  const y = event.clientY - box.top
  // Diâmetro que cobre o elemento todo a partir do ponto tocado.
  const size =
    2 * Math.hypot(Math.max(x, box.width - x), Math.max(y, box.height - y))
  host.style.setProperty("--ripple-x", `${x}px`)
  host.style.setProperty("--ripple-y", `${y}px`)
  host.style.setProperty("--ripple-size", `${size}px`)
  // Reinicia a animação mesmo com toques seguidos no mesmo elemento.
  host.removeAttribute("data-rippling")
  void host.offsetWidth
  host.setAttribute("data-rippling", "")
}

function end(event: AnimationEvent) {
  if (event.animationName === "ripple") {
    ;(event.target as HTMLElement).removeAttribute("data-rippling")
  }
}

export function installRipple() {
  if (installed || typeof document === "undefined") {
    return
  }
  installed = true
  document.addEventListener("pointerdown", start, { passive: true })
  document.addEventListener("animationend", end)
}
