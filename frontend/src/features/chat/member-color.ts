/** Quantas cores de nome existem no index.css ([data-member-color="0"] a "11"). */
export const MEMBER_COLORS = 12

/**
 * Cor do nome de quem escreveu no grupo, como no WhatsApp: sempre a mesma para a mesma pessoa (vem do id), em qualquer
 * aparelho, e diferente entre pessoas na maioria dos grupos.
 */
export function memberColor(userId: string): number {
  let hash = 0
  for (const char of userId) {
    hash = (Math.imul(hash, 31) + char.charCodeAt(0)) | 0
  }
  return Math.abs(hash) % MEMBER_COLORS
}
