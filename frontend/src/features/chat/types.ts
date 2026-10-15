export type MemberState = "ACTIVE" | "REQUEST" | "DECLINED" | "LEFT" | "PENDING"

export type MemberRole = "MEMBER" | "ADMIN"

export type ConversationKind = "DIRECT" | "GROUP" | "SELF"

/** Mensagem de uma pessoa ou aviso da conversa (corpo em JSON, ver SystemNotice). */
export type MessageKind = "USER" | "SYSTEM"

/** Aviso do sistema num grupo ("Ana adicionou Bruno"), com os nomes da época. */
export type SystemNotice = {
  type: string
  actor: { id: string; name: string }
  targets: { id: string; name: string }[]
  value: string | null
}

/** Dados do grupo como eu o vejo (GR-01). */
export type GroupInfo = {
  name: string
  description: string
  photoId: string | null
  photoVersion: string
  memberCount: number
  myRole: MemberRole
  onlyAdminsSend: boolean
  onlyAdminsEditInfo: boolean
  onlyAdminsAddMembers: boolean
  joinApproval: boolean
  canEditInfo: boolean
  canAddMembers: boolean
}

export type GroupMember = {
  userId: string
  username: string
  displayName: string
  label: string
  hasPhoto: boolean
  photoVersion: string
  role: MemberRole
  contact: boolean
  me: boolean
}

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

export type AttachmentKind = "IMAGE" | "VIDEO" | "AUDIO" | "DOCUMENT"

export type MessagePreview = {
  id: string
  seq: number
  senderId: string | null
  /** Nome de quem mandou, só em grupo e quando não fui eu. */
  senderLabel: string | null
  body: string
  createdAt: string
  attachmentKind: AttachmentKind | null
  /** Apagada para todos (MS-06). */
  deleted: boolean
  kind: MessageKind
}

/** Arquivo anexado (MD-01); conteúdo em /api/v1/media/{id}/content. */
export type Attachment = {
  id: string
  kind: AttachmentKind
  contentType: string
  size: number
  filename: string
  width: number | null
  height: number | null
  thumbhash: string | null
  hasThumbnail: boolean
}

/** Arquivo ainda no aparelho, mostrado enquanto envia. */
export type LocalAttachment = {
  kind: AttachmentKind
  filename: string
  size: number
  contentType: string
  /** URL blob: para a prévia de imagem e vídeo. */
  previewUrl: string | null
}

export type Conversation = {
  id: string
  kind: ConversationKind
  state: MemberState
  /** A outra pessoa (individual) ou eu mesmo (mensagens salvas); nulo em grupo. */
  peer: Peer | null
  group: GroupInfo | null
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
  /** Timer de mensagens temporárias em segundos (MS-07); nulo = desligado. */
  disappearingSeconds: number | null
}

export type Reaction = { userId: string; emoji: string }

export type ReplyPreview = {
  id: string
  /** Outra conversa quando foi "responder em privado" a partir de um grupo. */
  conversationId?: string
  seq: number
  senderId: string | null
  body: string
  attachmentKind?: AttachmentKind | null
  deleted?: boolean
}

export type Message = {
  id: string
  conversationId: string
  seq: number
  changeSeq: number
  kind: MessageKind
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
  attachment: Attachment | null
  /** Quando foi editada pela última vez (MS-05). */
  editedAt: string | null
  /** Apagada para todos: sem texto nem anexo (MS-06). */
  deleted: boolean
  /** Quando a mensagem temporária some (MS-07). */
  expiresAt: string | null
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
  /** "uploading": o arquivo ainda está subindo; depois vira "sending". */
  status: "uploading" | "sending" | "failed"
  attachment: LocalAttachment | null
  /** Id da mídia já enviada, para reenviar sem subir de novo. */
  attachmentId: string | null
}

export type MessageStatus =
  "uploading" | "sending" | "failed" | "sent" | "delivered" | "read"
