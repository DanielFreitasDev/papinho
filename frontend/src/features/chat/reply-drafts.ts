import type { ReplyPreview } from "./types"

/** Citação a abrir no campo de uma conversa: "responder em privado" a partir de um grupo (GR-01). */
const drafts = new Map<string, ReplyPreview>()

export function setReplyDraft(conversationId: string, reply: ReplyPreview) {
  drafts.set(conversationId, reply)
}

/** Lê sem apagar (pode rodar duas vezes na renderização); apague com {@link clearReplyDraft}. */
export function peekReplyDraft(conversationId: string): ReplyPreview | null {
  return drafts.get(conversationId) ?? null
}

export function clearReplyDraft(conversationId: string) {
  drafts.delete(conversationId)
}
