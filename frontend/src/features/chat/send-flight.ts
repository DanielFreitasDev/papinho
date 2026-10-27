import { motionEase, motionMs, prefersReducedMotion } from "@/lib/motion"

/**
 * A mensagem enviada sai da caixa de texto e voa até o lugar dela na conversa. A bolha de verdade não pode sair da
 * própria linha (a lista rola e cada linha recorta o que passa dela: content-visibility), então quem voa é uma cópia
 * dela, presa à janela (position: fixed), do ponto onde o texto estava no campo até onde a bolha está; a bolha só
 * aparece quando a cópia chega. Web Animations API, só transform e opacidade (a linha do tempo do documento: o e2e a
 * desacelera pelo CDP como as outras animações); com movimento reduzido, nada voa.
 */
type Origin = { x: number; y: number; at: number }

let origin: Origin | null = null

/** O texto vale como origem por pouco tempo: a bolha pendente aparece no mesmo instante do envio. */
const ORIGIN_MS = 1_000

/** Guarda onde o texto começa no campo, no instante do envio. */
export function markSendOrigin(field: HTMLElement) {
  const box = field.getBoundingClientRect()
  const style = getComputedStyle(field)
  origin = {
    x: box.left + Number.parseFloat(style.paddingLeft),
    y: box.top + Number.parseFloat(style.paddingTop),
    at: performance.now(),
  }
}

/** A origem do último envio, uma vez só; null se já passou ou se não há. */
export function takeSendOrigin(): Origin | null {
  const found = origin
  origin = null
  return found && performance.now() - found.at < ORIGIN_MS ? found : null
}

/** Onde o texto começa dentro da bolha (o canto do conteúdo mais o respiro). */
function textStart(content: HTMLElement) {
  const box = content.getBoundingClientRect()
  const style = getComputedStyle(content)
  return {
    x: box.left + Number.parseFloat(style.paddingLeft),
    y: box.top + Number.parseFloat(style.paddingTop),
  }
}

/**
 * Voa a cópia da bolha do campo até a bolha, que fica invisível até lá. Chama `done` no fim, também quando não dá para
 * voar (bolha fora da tela, movimento reduzido). Devolve true se voou.
 */
export function flyBubble(
  item: HTMLElement,
  from: Origin,
  done: () => void
): boolean {
  const bubble = item.querySelector<HTMLElement>('[data-slot="bubble"]')
  const content = bubble?.querySelector<HTMLElement>(
    '[data-slot="bubble-content"]'
  )
  const viewport = item.closest<HTMLElement>(
    '[data-slot="message-scroller-viewport"]'
  )
  if (!bubble || !content || !viewport || prefersReducedMotion()) {
    done()
    return false
  }
  const target = bubble.getBoundingClientRect()
  const area = viewport.getBoundingClientRect()
  if (target.bottom < area.top || target.top > area.bottom) {
    done()
    return false
  }
  const start = textStart(content)
  const ghost = bubble.cloneNode(true) as HTMLElement
  ghost.setAttribute("aria-hidden", "true")
  ghost.inert = true
  ghost.removeAttribute("data-flash")
  ghost.dataset.flying = ""
  // Posição e tamanho pelo CSSOM (a CSP barra estilo inline no HTML, não o CSSOM).
  Object.assign(ghost.style, {
    position: "fixed",
    left: `${target.left}px`,
    top: `${target.top}px`,
    width: `${target.width}px`,
    height: `${target.height}px`,
    margin: "0",
    zIndex: "40",
    pointerEvents: "none",
  })
  bubble.style.opacity = "0"
  document.body.append(ghost)
  const finish = () => {
    ghost.remove()
    bubble.style.opacity = ""
    done()
  }
  const flight = ghost.animate(
    [
      {
        transform: `translate(${from.x - start.x}px, ${from.y - start.y}px) scale(0.96)`,
        opacity: 0.55,
      },
      { opacity: 1, offset: 0.4 },
      { transform: "none", opacity: 1 },
    ],
    { duration: motionMs("slow") * 1.25, easing: motionEase("enter") }
  )
  flight.addEventListener("finish", finish)
  flight.addEventListener("cancel", finish)
  return true
}
