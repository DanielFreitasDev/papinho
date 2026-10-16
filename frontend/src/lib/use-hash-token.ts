import { useState } from "react"

/**
 * Lê uma vez o token do fragmento da URL (links enviados por e-mail) e o tira da barra de endereços, para não
 * ficar no histórico nem ser copiado junto com o link.
 */
export function useHashToken(
  parse: (hash: string) => string | null
): string | null {
  const [token] = useState(() => {
    const value = parse(window.location.hash)
    if (window.location.hash) {
      window.history.replaceState(
        window.history.state,
        "",
        window.location.pathname + window.location.search
      )
    }
    return value
  })
  return token
}
