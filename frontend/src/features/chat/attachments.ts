import { useSyncExternalStore } from "react"
import type { QueryClient } from "@tanstack/react-query"

import { guessKind, uploadFile, type UploadHandle } from "@/features/media"
import { addPending, markPendingFailed, type ConversationCache } from "./cache"
import type { LocalAttachment, PendingMessage, ReplyPreview } from "./types"

/**
 * Envio de arquivos (MD-01): a mensagem aparece na hora como pendente com a prévia local e o progresso; o
 * arquivo sobe por tus e, ao terminar, a mensagem é enviada com o id da mídia e o mesmo clientMessageId
 * (RT-02). O progresso fica fora do cache do React Query para não redesenhar a conversa a cada pedaço.
 */
const progress = new Map<string, number>()
const handles = new Map<string, UploadHandle>()
const files = new Map<string, File>()
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

export function useUploadProgress(clientMessageId: string): number {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => progress.get(clientMessageId) ?? 0
  )
}

export type SendWithAttachment = (input: {
  clientMessageId: string
  body: string
  replyTo: ReplyPreview | null
  attachmentId: string
  attachment: LocalAttachment
}) => Promise<unknown>

function messagesKey(conversationId: string) {
  return ["messages", conversationId] as const
}

/**
 * Sobe o arquivo e envia a mensagem quando terminar. Vários arquivos sobem em paralelo, mas cada mensagem só
 * sai depois de `after` (a anterior), para manter a ordem escolhida. Resolve quando a mensagem foi enviada ou
 * o envio falhou.
 */
export function sendFile(
  queryClient: QueryClient,
  conversationId: string,
  file: File,
  body: string,
  replyTo: ReplyPreview | null,
  send: SendWithAttachment,
  clientMessageId: string = crypto.randomUUID(),
  after: Promise<unknown> = Promise.resolve()
): Promise<void> {
  const kind = guessKind(file)
  const attachment: LocalAttachment = {
    kind,
    filename: file.name,
    size: file.size,
    contentType: file.type,
    previewUrl:
      kind === "IMAGE" || kind === "VIDEO" ? URL.createObjectURL(file) : null,
  }
  const pending: PendingMessage = {
    clientMessageId,
    conversationId,
    body,
    replyTo,
    createdAt: new Date().toISOString(),
    status: "uploading",
    attachment,
    attachmentId: null,
  }
  queryClient.setQueryData<ConversationCache>(
    messagesKey(conversationId),
    (current) => (current ? addPending(current, pending) : current)
  )
  files.set(clientMessageId, file)
  progress.set(clientMessageId, 0)
  const handle = uploadFile(file, (fraction) => {
    progress.set(clientMessageId, fraction)
    emit()
  })
  handles.set(clientMessageId, handle)
  return handle.done
    .then(async (attachmentId) => {
      await after.catch(() => undefined)
      files.delete(clientMessageId)
      await send({ clientMessageId, body, replyTo, attachmentId, attachment })
    })
    .catch((error: unknown) => {
      if (error instanceof DOMException && error.name === "AbortError") {
        return
      }
      queryClient.setQueryData<ConversationCache>(
        messagesKey(conversationId),
        (current) =>
          current ? markPendingFailed(current, clientMessageId) : current
      )
    })
    .finally(() => {
      handles.delete(clientMessageId)
      progress.delete(clientMessageId)
      emit()
    })
}

/** Tenta de novo: reenvia a mensagem se o arquivo já subiu; senão sobe outra vez. */
export function retryFile(
  queryClient: QueryClient,
  pending: PendingMessage,
  send: SendWithAttachment
): boolean {
  if (pending.attachmentId && pending.attachment) {
    void send({
      clientMessageId: pending.clientMessageId,
      body: pending.body,
      replyTo: pending.replyTo,
      attachmentId: pending.attachmentId,
      attachment: pending.attachment,
    })
    return true
  }
  const file = files.get(pending.clientMessageId)
  if (!file) {
    return false
  }
  void sendFile(
    queryClient,
    pending.conversationId,
    file,
    pending.body,
    pending.replyTo,
    send,
    pending.clientMessageId
  )
  return true
}

/** Cancela o upload e tira a mensagem pendente da conversa. */
export function cancelFile(
  queryClient: QueryClient,
  conversationId: string,
  clientMessageId: string
) {
  handles.get(clientMessageId)?.cancel()
  handles.delete(clientMessageId)
  files.delete(clientMessageId)
  progress.delete(clientMessageId)
  queryClient.setQueryData<ConversationCache>(
    messagesKey(conversationId),
    (current) => {
      if (!current) {
        return current
      }
      const removed = current.pending.find(
        (item) => item.clientMessageId === clientMessageId
      )
      if (removed?.attachment?.previewUrl) {
        URL.revokeObjectURL(removed.attachment.previewUrl)
      }
      return {
        ...current,
        pending: current.pending.filter(
          (item) => item.clientMessageId !== clientMessageId
        ),
      }
    }
  )
  emit()
}
