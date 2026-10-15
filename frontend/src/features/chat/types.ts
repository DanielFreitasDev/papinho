export type MemberState = "ACTIVE" | "REQUEST" | "DECLINED"

export type Peer = {
  userId: string
  username: string
  displayName: string
  label: string
  hasPhoto: boolean
  photoVersion: string
  active: boolean
  contact: boolean
}

export type MessagePreview = {
  id: string
  seq: number
  senderId: string | null
  body: string
  createdAt: string
}

export type Conversation = {
  id: string
  kind: "DIRECT"
  state: MemberState
  peer: Peer
  lastSeq: number
  lastMessage: MessagePreview | null
  unreadCount: number
  firstUnreadSeq: number | null
  markedUnread: boolean
  readSeq: number
  peerDeliveredSeq: number
  /** Nulo enquanto a outra pessoa não aceitou a conversa (CT-02). */
  peerReadSeq: number | null
  updatedAt: string
  canSend: boolean
  blockedByMe: boolean
}

export type Reaction = { userId: string; emoji: string }

export type ReplyPreview = {
  id: string
  seq: number
  senderId: string | null
  body: string
}

export type Message = {
  id: string
  conversationId: string
  seq: number
  changeSeq: number
  senderId: string | null
  clientMessageId: string
  body: string
  createdAt: string
  replyTo: ReplyPreview | null
  forwardCount: number
  forwardedMany: boolean
  pinnedAt: string | null
  reactions: Reaction[]
  starred: boolean
}

export type MessagePage = {
  messages: Message[]
  lastSeq: number
  hasMore: boolean
}

export type RecipientInfo = {
  userId: string
  username: string
  displayName: string
  deliveredAt: string | null
  readAt: string | null
}

export type MessageInfo = { message: Message; recipients: RecipientInfo[] }

export type Presence = {
  userId: string
  online: boolean
  lastSeenAt: string | null
}

/** Mensagem ainda não confirmada pelo servidor ("enviando" ou "falhou"). */
export type PendingMessage = {
  clientMessageId: string
  conversationId: string
  body: string
  replyTo: ReplyPreview | null
  createdAt: string
  status: "sending" | "failed"
}

export type MessageStatus = "sending" | "failed" | "sent" | "delivered" | "read"
