import type { Message, MessagePage, PendingMessage } from "./types"

/**
 * Estado local de uma conversa aberta: mensagens carregadas (em ordem de seq), mensagens pendentes e o cursor
 * de sincronização. O cursor é o último seq de alteração já aplicado: qualquer mudança depois dele chega por
 * {@code afterSeq} (ADR 0010).
 */
export type ConversationCache = {
  messages: Message[]
  pending: PendingMessage[]
  /** Há mensagens mais antigas no servidor que ainda não foram carregadas. */
  hasOlder: boolean
  cursor: number
}

export function fromHistory(page: MessagePage): ConversationCache {
  return {
    messages: [...page.messages].sort((a, b) => a.seq - b.seq),
    pending: [],
    hasOlder: page.hasMore,
    cursor: page.lastSeq,
  }
}

/** Junta uma página mais antiga (paginação para trás). */
export function prependOlder(
  cache: ConversationCache,
  page: MessagePage
): ConversationCache {
  const known = new Set(cache.messages.map((message) => message.id))
  const older = page.messages.filter((message) => !known.has(message.id))
  return {
    ...cache,
    messages: [...older, ...cache.messages].sort((a, b) => a.seq - b.seq),
    hasOlder: page.hasMore,
  }
}

/**
 * Aplica alterações vindas de {@code afterSeq}: substitui as já carregadas, acrescenta as novas e ignora mudanças
 * em mensagens mais antigas que a janela carregada (elas chegam certas quando a página for buscada).
 */
export function applyChanges(
  cache: ConversationCache,
  page: MessagePage
): ConversationCache {
  const byId = new Map(cache.messages.map((message) => [message.id, message]))
  const oldest = cache.messages[0]?.seq ?? 0
  let cursor = cache.cursor
  for (const message of page.messages) {
    cursor = Math.max(cursor, message.changeSeq)
    if (byId.has(message.id) || !cache.hasOlder || message.seq >= oldest) {
      byId.set(message.id, message)
    }
  }
  if (!page.hasMore) {
    cursor = Math.max(cursor, page.lastSeq)
  }
  const messages = [...byId.values()].sort((a, b) => a.seq - b.seq)
  const confirmed = new Set(messages.map((message) => message.clientMessageId))
  return {
    ...cache,
    messages,
    pending: cache.pending.filter(
      (pending) => !confirmed.has(pending.clientMessageId)
    ),
    cursor,
  }
}

/** Uma mensagem confirmada pelo POST (pode chegar antes ou depois do aviso pelo WebSocket). */
export function confirmSent(
  cache: ConversationCache,
  message: Message
): ConversationCache {
  return applyChanges(
    { ...cache, cursor: cache.cursor },
    { messages: [message], lastSeq: cache.cursor, hasMore: true }
  )
}

export function addPending(
  cache: ConversationCache,
  pending: PendingMessage
): ConversationCache {
  return {
    ...cache,
    pending: [
      ...cache.pending.filter(
        (item) => item.clientMessageId !== pending.clientMessageId
      ),
      pending,
    ],
  }
}

export function markPendingFailed(
  cache: ConversationCache,
  clientMessageId: string
): ConversationCache {
  return {
    ...cache,
    pending: cache.pending.map((item) =>
      item.clientMessageId === clientMessageId
        ? { ...item, status: "failed" }
        : item
    ),
  }
}
