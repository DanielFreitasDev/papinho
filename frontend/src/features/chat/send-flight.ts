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

/** Interpola a posição da cópia: da origem (deslocada do início do texto) até onde a bolha está agora. */
function lerp(from: number, to: number, progress: number) {
  return from + (to - from) * progress
}

/**
 * Voa a cópia da bolha do campo até a bolha, que fica invisível até lá. A cada quadro a cópia mira onde a bolha está
 * naquele instante (a lista pode rolar, a bolha confirmada pode mudar de largura): ela sempre pousa exatamente no
 * lugar, sem ajuste no fim. O tempo vem de duas animações sem alvo na linha do tempo do documento (o e2e a
 * desacelera pelo CDP), uma por eixo, com as curvas do tema: a subida desacelera antes do deslize para o lado, e o
 * caminho faz uma curva leve até o canto. Chama `done` no fim, também quando não dá para voar (bolha fora da tela,
 * movimento reduzido). Devolve true se voou.
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
  // Onde a cópia começa, em relação à bolha: o texto dela por cima do texto que estava no campo.
  const offsetX = from.x - start.x
  const offsetY = from.y - start.y
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
    transformOrigin: "0 0",
  })
  bubble.style.opacity = "0"
  document.body.append(ghost)

  const duration = motionMs("slow") * 1.4
  const clock = (easing: string) => {
    const animation = new Animation(
      new KeyframeEffect(null, null, { duration, easing, fill: "forwards" }),
      document.timeline
    )
    animation.play()
    return animation
  }
  const vertical = clock(motionEase("enter"))
  const horizontal = clock(motionEase("standard"))
  const progress = (animation: Animation) =>
    animation.effect?.getComputedTiming().progress ?? 1

  let finished = false
  const finish = () => {
    if (finished) {
      return
    }
    finished = true
    vertical.cancel()
    horizontal.cancel()
    ghost.remove()
    bubble.style.opacity = ""
    done()
  }
  /** Põe a cópia no ponto do voo, mirando a bolha onde ela está agora. */
  const place = () => {
    if (!bubble.isConnected) {
      // A conversa trocou no meio do voo: nada a pousar.
      finish()
      return false
    }
    const now = bubble.getBoundingClientRect()
    const py = progress(vertical)
    const px = progress(horizontal)
    const x = lerp(offsetX, now.left - target.left, px)
    const y = lerp(offsetY, now.top - target.top, py)
    const scale = lerp(0.94, 1, py)
    if (
      Math.abs(now.width - ghost.offsetWidth) > 0.5 ||
      Math.abs(now.height - ghost.offsetHeight) > 0.5
    ) {
      ghost.style.width = `${now.width}px`
      ghost.style.height = `${now.height}px`
    }
    ghost.style.transform = `translate(${x}px, ${y}px) scale(${scale})`
    ghost.style.opacity = String(lerp(0.5, 1, Math.min(1, py * 2.5)))
    return true
  }
  const frame = () => {
    if (!finished && place()) {
      window.requestAnimationFrame(frame)
    }
  }
  // Primeira pintura já com a cópia sobre o campo.
  place()
  window.requestAnimationFrame(frame)
  void Promise.all([vertical.finished, horizontal.finished])
    .then(() => {
      // Último quadro exatamente sobre a bolha, depois a troca.
      place()
      finish()
    })
    .catch(finish)
  return true
}
