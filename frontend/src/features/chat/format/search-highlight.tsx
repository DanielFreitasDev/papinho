import type { ReactNode } from "react"

import { findMatches, type SearchTerms } from "./search-match"

/**
 * Texto com os trechos achados pela busca num `<mark>` (GR-06). Monta elementos React a partir do texto, nunca HTML:
 * o que a pessoa escreveu continua sendo só texto.
 */
export function highlightText(
  text: string,
  terms: SearchTerms | null | undefined
): ReactNode {
  if (!terms || !text) {
    return text
  }
  const ranges = findMatches(text, terms)
  if (ranges.length === 0) {
    return text
  }
  const parts: ReactNode[] = []
  let at = 0
  ranges.forEach(([start, end]) => {
    if (start > at) {
      parts.push(text.slice(at, start))
    }
    parts.push(
      <mark key={start} data-search-match="">
        {text.slice(start, end)}
      </mark>
    )
    at = end
  })
  if (at < text.length) {
    parts.push(text.slice(at))
  }
  return parts
}
