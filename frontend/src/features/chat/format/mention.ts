/**
 * Menções no campo do grupo, como no WhatsApp (MS-04): o "@" no começo ou depois de um espaço abre a lista de membros,
 * filtrada pelo que vem depois dele. O texto enviado continua `@usuario` (o servidor avisa quem foi mencionado).
 */

/** Quem pode ser mencionado: um membro do grupo (nunca eu). */
export type MentionCandidate = {
  userId: string
  username: string
  label: string
  hasPhoto: boolean
  photoVersion: string
}

/** O "@" antes do cursor: ainda sem espaço depois dele e precedido de início, espaço ou pontuação. */
const QUERY = /(^|[^\p{L}\p{N}_.@])@([\p{L}\p{N}._]{0,32})$/u

/** O "@..." que está sendo digitado no cursor, ou null. `from` é a posição do "@". */
export function mentionQuery(
  text: string,
  caret: number
): { from: number; query: string } | null {
  const match = QUERY.exec(text.slice(0, caret))
  if (!match) {
    return null
  }
  return { from: caret - match[2].length - 1, query: match[2] }
}

/** Sem acento e em minúsculas, para "jose" achar "José". */
export function fold(text: string): string {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()
}

/** Membros cujo @usuario ou alguma palavra do nome começa com o que foi digitado. */
export function filterMentions(
  candidates: MentionCandidate[],
  query: string
): MentionCandidate[] {
  const wanted = fold(query)
  if (wanted === "") {
    return candidates
  }
  return candidates.filter(
    (candidate) =>
      candidate.username.startsWith(wanted) ||
      fold(candidate.label)
        .split(/[^\p{L}\p{N}]+/u)
        .some((word) => word.startsWith(wanted))
  )
}

/** "@todos" aparece enquanto o que foi digitado for o começo dele. */
export function matchesEveryone(query: string): boolean {
  return "todos".startsWith(fold(query))
}
