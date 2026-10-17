/**
 * Janela de mensagens desenhadas na conversa (UI-04). O cache pode ter milhares de mensagens carregadas; a lista
 * desenha só uma faixa contígua delas, que cresce pelas bordas conforme a rolagem e é cortada do lado oposto.
 * As bordas são guardadas por seq (estável quando chegam páginas antigas, mensagens novas ou somem temporárias).
 * {@code null} numa borda quer dizer "até a ponta do cache": a mais antiga carregada ou a mais nova, com as
 * pendentes (que ainda não têm seq e ficam sempre no fim).
 */
export type MessageWindow = { fromSeq: number | null; toSeq: number | null }

/** Quantas mensagens cada borda revela de uma vez (o mesmo tamanho da página do servidor). */
export const WINDOW_STEP = 50
/** Tamanho da janela depois de cortada. */
export const WINDOW_SIZE = 100
/** A janela só é cortada quando passa deste tamanho (evita cortar a cada mensagem nova). */
export const WINDOW_MAX = WINDOW_SIZE + WINDOW_STEP

type Seqs = readonly (number | null)[]

const after = (seq: number | null, bound: number) => seq === null || seq > bound

/** Índices [start, end) desenhados. Uma janela que ficou vazia volta para o fim. */
export function windowRange(seqs: Seqs, window: MessageWindow) {
  const fromSeq = window.fromSeq
  const toSeq = window.toSeq
  const start =
    fromSeq === null ? 0 : firstIndex(seqs, (seq) => after(seq, fromSeq - 1))
  const end =
    toSeq === null ? seqs.length : firstIndex(seqs, (seq) => after(seq, toSeq))
  if (start >= end && seqs.length > 0) {
    return { start: Math.max(0, seqs.length - WINDOW_SIZE), end: seqs.length }
  }
  return { start, end }
}

function firstIndex(seqs: Seqs, test: (seq: number | null) => boolean) {
  const index = seqs.findIndex(test)
  return index === -1 ? seqs.length : index
}

/** Borda que começa (ou termina) no índice: na ponta do cache vira null. */
function fromAt(seqs: Seqs, index: number) {
  return index <= 0 ? null : seqs[index]
}

function toAt(seqs: Seqs, index: number) {
  return index >= seqs.length - 1 ? null : seqs[index]
}

/** As últimas mensagens, incluindo a primeira não lida se ela estiver carregada. */
export function tailWindow(seqs: Seqs, keepSeq: number | null = null) {
  let start = Math.max(0, seqs.length - WINDOW_SIZE)
  if (keepSeq !== null) {
    start = Math.min(
      start,
      windowRange(seqs, { fromSeq: keepSeq, toSeq: null }).start
    )
  }
  return { fromSeq: fromAt(seqs, start), toSeq: null }
}

/** Janela centrada numa mensagem (salto para respondida, busca, não lidas e fixadas). */
export function windowAround(seqs: Seqs, seq: number): MessageWindow {
  const index = seqs.indexOf(seq)
  if (index === -1) {
    return tailWindow(seqs)
  }
  const start = Math.max(0, index - WINDOW_STEP)
  const end = Math.min(seqs.length - 1, index + WINDOW_STEP)
  return { fromSeq: fromAt(seqs, start), toSeq: toAt(seqs, end) }
}

/**
 * Revela até WINDOW_STEP mensagens carregadas acima. Com a mais antiga do cache já na janela, abre a borda (mesmo se
 * ela ainda aponta para uma mensagem que saiu do cache) e pede a página anterior, que entra por ela.
 */
export function revealOlder(
  seqs: Seqs,
  window: MessageWindow
): { window: MessageWindow; loadPage: boolean } {
  const { start } = windowRange(seqs, window)
  const fromSeq = fromAt(seqs, start - WINDOW_STEP)
  return {
    window: fromSeq === window.fromSeq ? window : { ...window, fromSeq },
    loadPage: start === 0,
  }
}

/** Revela até WINDOW_STEP mensagens abaixo; chegando à mais nova, volta a seguir as que chegarem. */
export function revealNewer(seqs: Seqs, window: MessageWindow): MessageWindow {
  const { end } = windowRange(seqs, window)
  const toSeq = toAt(seqs, end - 1 + WINDOW_STEP)
  return toSeq === window.toSeq ? window : { ...window, toSeq }
}

/**
 * Corta a janela que passou de WINDOW_MAX para WINDOW_SIZE, do lado oposto a quem está lendo: {@code "start"}
 * mantém as mais antigas (a pessoa está no alto) e {@code "end"} as mais novas. Devolve null se não precisa.
 */
export function trimWindow(
  seqs: Seqs,
  window: MessageWindow,
  keep: "start" | "end"
): MessageWindow | null {
  const { start, end } = windowRange(seqs, window)
  if (end - start <= WINDOW_MAX) {
    return null
  }
  if (keep === "start") {
    const last = start + WINDOW_SIZE - 1
    // As pendentes não têm seq: a borda de baixo não as separa das confirmadas.
    return seqs[last] === null ? null : { ...window, toSeq: seqs[last] }
  }
  return { ...window, fromSeq: fromAt(seqs, end - WINDOW_SIZE) }
}
