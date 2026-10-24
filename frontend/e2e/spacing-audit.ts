import type { Page } from "@playwright/test"

/** Algo encostado ou quase encostado numa borda (regra de espaçamento da interface). */
export type SpacingIssue = {
  kind: "overflow" | "inset" | "screen-edge"
  where: string
  detail: string
}

/**
 * Procura, na tela aberta, texto que vaza da própria caixa, conteúdo a menos do respiro mínimo da borda da superfície
 * que o contém (menu, popover, diálogo, botão, item, campo, balão...) e controles a menos de 12 px da borda da tela.
 * O que a tela corta de propósito (truncate, área com rolagem) e o texto só para leitor de tela ficam de fora; botão
 * só com ícone precisa de 4 px; linha que ocupa a largura do próprio contêiner tem o respiro por dentro.
 */
export async function auditSpacing(page: Page): Promise<SpacingIssue[]> {
  return page.evaluate(() => {
    /** Respiro mínimo do conteúdo até a borda, por tipo de superfície (px). */
    const SURFACES: [string, number][] = [
      ['[data-slot="dropdown-menu-content"]', 8],
      ['[data-slot="select-content"]', 8],
      ['[data-slot="popover-content"]', 8],
      ['[data-slot="dialog-content"]', 12],
      ['[data-slot="alert-dialog-content"]', 12],
      ['[data-slot="sheet-content"]', 12],
      ['[data-slot="card"]', 12],
      ['[data-slot="bubble-content"]', 8],
      ['[data-slot="input-group"]', 6],
      ['[data-slot="tooltip-content"]', 6],
      ['[data-slot="toast"]', 12],
      ['[data-slot="dropdown-menu-item"]', 6],
      ['[data-slot="select-item"]', 6],
      ['[data-slot="tabs-trigger"]', 6],
      ['[data-slot="badge"]', 4],
      ["button", 6],
      ['[role="button"]', 6],
    ]
    const issues: { kind: string; where: string; detail: string }[] = []
    const viewport = { width: document.documentElement.clientWidth }

    const visible = (element: Element) => {
      const style = getComputedStyle(element)
      if (style.visibility === "hidden" || style.display === "none") {
        return false
      }
      const rect = element.getBoundingClientRect()
      return (
        rect.width > 1 &&
        rect.height > 1 &&
        rect.bottom > 0 &&
        rect.right > 0 &&
        rect.top < window.innerHeight &&
        rect.left < viewport.width
      )
    }
    const name = (element: Element) => {
      const slot = element.getAttribute("data-slot")
      const label = element.getAttribute("aria-label")
      const text = (element.textContent ?? "").trim().replace(/\s+/g, " ")
      return `${element.tagName.toLowerCase()}${slot ? `[${slot}]` : ""}${label ? ` "${label}"` : ""}${
        text ? ` «${text.slice(0, 40)}»` : ""
      }`
    }
    /** Texto que alguém lê: visível e fora das áreas que a interface corta de propósito. */
    const readable = (node: Node) => {
      const parent = node.parentElement
      if (!parent || !(node.textContent ?? "").trim()) {
        return false
      }
      for (
        let element: Element | null = parent;
        element;
        element = element.parentElement
      ) {
        const style = getComputedStyle(element)
        if (
          style.textOverflow === "ellipsis" ||
          element.classList.contains("sr-only") ||
          (style.position === "absolute" &&
            element.getBoundingClientRect().width <= 1) ||
          Number(style.opacity) === 0
        ) {
          return false
        }
        if (element.tagName === "BODY") {
          break
        }
      }
      return visible(parent)
    }

    // Texto maior que a caixa (palavra longa num menu estreito, nome comprido num botão).
    const walker = document.createTreeWalker(
      document.body,
      NodeFilter.SHOW_TEXT
    )
    const seen = new Set<Element>()
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const parent = node.parentElement
      if (!parent || seen.has(parent) || !readable(node)) {
        continue
      }
      seen.add(parent)
      const style = getComputedStyle(parent)
      if (
        style.overflowX === "visible" &&
        style.display !== "inline" &&
        parent.scrollWidth > parent.clientWidth + 1
      ) {
        issues.push({
          kind: "overflow",
          where: name(parent),
          detail: `${parent.scrollWidth - parent.clientWidth}px além da caixa`,
        })
      }
    }

    // Conteúdo perto da borda da superfície que o contém.
    for (const [selector, minimum] of SURFACES) {
      for (const surface of document.querySelectorAll(selector)) {
        if (!visible(surface)) {
          continue
        }
        const box = surface.getBoundingClientRect()
        const style = getComputedStyle(surface)
        const left = box.left + parseFloat(style.borderLeftWidth)
        const right = box.right - parseFloat(style.borderRightWidth)
        let closest = Infinity
        const content = document.createTreeWalker(surface, NodeFilter.SHOW_TEXT)
        for (let node = content.nextNode(); node; node = content.nextNode()) {
          if (!readable(node)) {
            continue
          }
          const range = document.createRange()
          range.selectNodeContents(node)
          for (const rect of range.getClientRects()) {
            if (rect.width > 0) {
              closest = Math.min(closest, rect.left - left, right - rect.right)
            }
          }
        }
        for (const icon of surface.querySelectorAll("svg")) {
          if (visible(icon)) {
            const rect = icon.getBoundingClientRect()
            closest = Math.min(closest, rect.left - left, right - rect.right)
          }
        }
        // Botão só com ícone (ou um emoji): o glifo centrado com 4 px de folga basta. Texto só para leitor de tela
        // não conta.
        let label = ""
        const words = document.createTreeWalker(surface, NodeFilter.SHOW_TEXT)
        for (let node = words.nextNode(); node; node = words.nextNode()) {
          if (readable(node)) {
            label += node.textContent ?? ""
          }
        }
        const glyphs = [...new Intl.Segmenter().segment(label.trim())].length
        const iconOnly =
          minimum === 6 &&
          (glyphs === 0 ? surface.querySelector("svg") !== null : glyphs <= 2)
        const required = iconOnly ? 4 : minimum
        if (closest < required - 0.5) {
          issues.push({
            kind: "inset",
            where: name(surface),
            detail: `${Math.max(0, Math.round(closest))}px da borda (mínimo ${required}px)`,
          })
        }
      }
    }

    // Controles colados na borda da tela (linhas que ocupam a largura inteira têm o respiro por dentro).
    for (const control of document.querySelectorAll(
      'button, a[href], input, textarea, [role="button"], [role="tab"], [role="menuitem"]'
    )) {
      if (!visible(control)) {
        continue
      }
      const rect = control.getBoundingClientRect()
      const parent = control.parentElement?.getBoundingClientRect()
      if (
        rect.width >= viewport.width * 0.9 ||
        (parent && rect.width >= parent.width * 0.85)
      ) {
        continue
      }
      // Dentro de uma faixa que rola de lado (abas, filtros), a rolagem é que mostra o resto: a aba cortada na borda
      // (com o esmaecimento do scroll-fade-x) é o sinal de que há mais.
      let scrolling = false
      for (
        let element = control.parentElement;
        element && element.tagName !== "BODY";
        element = element.parentElement
      ) {
        const style = getComputedStyle(element)
        if (style.overflowX !== "visible") {
          scrolling = element.scrollWidth > element.clientWidth + 1
          break
        }
      }
      if (scrolling) {
        continue
      }
      const gap = Math.min(rect.left, viewport.width - rect.right)
      if (gap < 12 - 0.5) {
        issues.push({
          kind: "screen-edge",
          where: name(control),
          detail: `${Math.max(0, Math.round(gap))}px da borda da tela`,
        })
      }
    }
    // Uma linha por problema (o mesmo item repetido numa lista conta uma vez).
    const unique = new Map(
      issues.map((issue) => [
        `${issue.kind}|${issue.where}|${issue.detail}`,
        issue,
      ])
    )
    return [...unique.values()] as SpacingIssue[]
  })
}
