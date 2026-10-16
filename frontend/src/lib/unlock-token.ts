/**
 * Token de desbloqueio das conversas trancadas (PV-03). Fica só na memória da aba: recarregar ou sair tranca
 * tudo de novo. O cliente HTTP manda o token no cabeçalho {@link UNLOCK_HEADER} quando há um.
 */
export const UNLOCK_HEADER = "X-Papinho-Unlock"

let token: string | null = null
const listeners = new Set<() => void>()

export function unlockToken(): string | null {
  return token
}

export function setUnlockToken(next: string | null) {
  token = next
  listeners.forEach((listener) => listener())
}

export function subscribeUnlockToken(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
