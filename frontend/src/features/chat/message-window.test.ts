import { describe, expect, it } from "vitest"

import {
  revealNewer,
  revealOlder,
  tailWindow,
  trimWindow,
  windowAround,
  windowRange,
  WINDOW_MAX,
  WINDOW_SIZE,
  WINDOW_STEP,
} from "./message-window"

/** seqs 1..count, como o cache depois de carregar as páginas antigas. */
const seqs = (count: number, pending = 0) => [
  ...Array.from({ length: count }, (_, index) => index + 1),
  ...Array.from({ length: pending }, () => null),
]

describe("janela de mensagens desenhadas (UI-04)", () => {
  it("abre com as últimas WINDOW_SIZE e as pendentes", () => {
    const all = seqs(1000, 2)
    const window = tailWindow(all)
    expect(windowRange(all, window)).toEqual({
      start: 1002 - WINDOW_SIZE,
      end: 1002,
    })
  })

  it("abre a partir da primeira não lida se ela está carregada", () => {
    const all = seqs(1000)
    expect(windowRange(all, tailWindow(all, 500)).start).toBe(499)
    expect(tailWindow(seqs(30), 10)).toEqual({ fromSeq: null, toSeq: null })
  })

  it("salto: centra a janela na mensagem e para de seguir o fim", () => {
    const all = seqs(5000)
    const window = windowAround(all, 2000)
    expect(windowRange(all, window)).toEqual({
      start: 1999 - WINDOW_STEP,
      end: 2000 + WINDOW_STEP,
    })
    expect(windowAround(all, 4990).toSeq).toBeNull()
    expect(windowAround(all, 3).fromSeq).toBeNull()
  })

  it("revela páginas acima e abaixo até as pontas", () => {
    const all = seqs(300)
    let window = windowAround(all, 150)
    window = revealOlder(all, window).window
    expect(windowRange(all, window).start).toBe(149 - 2 * WINDOW_STEP)
    expect(revealOlder(all, window).loadPage).toBe(false)
    window = revealOlder(all, revealOlder(all, window).window).window
    // Na ponta fica aberta: a página antiga que chegar entra na janela.
    expect(window.fromSeq).toBeNull()
    expect(windowRange([-1, ...all], window).start).toBe(0)
    window = revealNewer(all, revealNewer(all, revealNewer(all, window)))
    expect(window.toSeq).toBeNull()
    expect(windowRange([...all, 301], window).end).toBe(301)
  })

  it("no começo do cache, abre a borda mesmo se a mensagem dela saiu", () => {
    // A mensagem de seq 3 abria a janela e foi apagada: sem abrir, as páginas antigas nunca entrariam.
    const all = [5, 6, 7, 8]
    const window = { fromSeq: 3, toSeq: null }
    expect(windowRange(all, window).start).toBe(0)
    expect(revealOlder(all, window)).toEqual({
      window: { fromSeq: null, toSeq: null },
      loadPage: true,
    })
    const open = { fromSeq: null, toSeq: null }
    expect(revealOlder(all, open).window).toBe(open)
    expect(revealNewer(all, open)).toBe(open)
  })

  it("corta do lado oposto a quem lê, só depois de WINDOW_MAX", () => {
    const all = seqs(1000)
    const big = { fromSeq: 1, toSeq: WINDOW_MAX }
    expect(trimWindow(all, big, "start")).toBeNull()
    const bigger = { fromSeq: 1, toSeq: WINDOW_MAX + 1 }
    expect(windowRange(all, trimWindow(all, bigger, "start")!)).toEqual({
      start: 0,
      end: WINDOW_SIZE,
    })
    expect(windowRange(all, trimWindow(all, bigger, "end")!)).toEqual({
      start: WINDOW_MAX + 1 - WINDOW_SIZE,
      end: WINDOW_MAX + 1,
    })
  })

  it("não corta no meio das pendentes, que não têm seq", () => {
    const all = seqs(0, WINDOW_MAX + 5)
    expect(trimWindow(all, { fromSeq: null, toSeq: null }, "start")).toBeNull()
  })

  it("janela que ficou vazia volta para o fim", () => {
    const all = seqs(400)
    expect(windowRange(all, { fromSeq: 900, toSeq: 950 })).toEqual({
      start: 400 - WINDOW_SIZE,
      end: 400,
    })
    expect(windowRange([], { fromSeq: 1, toSeq: 2 })).toEqual({
      start: 0,
      end: 0,
    })
  })
})
