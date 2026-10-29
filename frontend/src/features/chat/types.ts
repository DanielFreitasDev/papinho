export type MemberState = "ACTIVE" | "REQUEST" | "DECLINED" | "LEFT" | "PENDING"

export type MemberRole = "MEMBER" | "ADMIN"

export type ConversationKind = "DIRECT" | "GROUP" | "SELF" | "BROADCAST"

/** Lista de transmissão (GR-03, GR-04). */
export type BroadcastInfo = {
  name: string
  recipientCount: number
  expiresAt: string | null
  messageTtlSeconds: number | null
  expired: boolean
}

/** Mensagem de uma pessoa ou aviso da conversa (corpo em JSON, ver SystemNotice). */
export type MessageKind = "USER" | "SYSTEM" | "POLL"

/** Enquete (MS-09); a pergunta é o corpo da mensagem. */
export type Poll = {
  multiple: boolean
  anonymous: boolean
  closesAt: string | null
  closed: boolean
  options: { id: string; text: string; votes: number; voters: string[] }[]
  totalVoters: number
  myVotes: string[]
}

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
  sticker: boolean
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
  /**
   * Duração do áudio lida do arquivo pelo servidor (AU-02); nula quando o arquivo não diz e ausente nas mensagens
   * guardadas offline antes de o campo existir.
   */
  durationMs?: number | null
  /** Páginas do PDF, lidas pelo servidor; nula em outros documentos e ausente nas mensagens antigas guardadas offline. */
  pageCount?: number | null
  /** Só em conversa que a pessoa trancou: vai na URL do conteúdo (PV-03). */
  grant?: string | null
}

/** Arquivo ainda no aparelho, mostrado enquanto envia. */
export type LocalAttachment = {
  kind: AttachmentKind
  filename: string
  size: number
  contentType: string
  /** URL blob: para a prévia de imagem e vídeo. */
  previewUrl: string | null
  /** Enviar como visualização única (MS-08). */
  viewOnce?: boolean
  /** Duração do áudio medida no aparelho, para a bolha mostrar enquanto envia (AU-02). */
  durationMs?: number | null
}

export type Conversation = {
  id: string
  kind: ConversationKind
  state: MemberState
  /** A outra pessoa (individual) ou eu mesmo (mensagens salvas); nulo em grupo. */
  peer: Peer | null
  group: GroupInfo | null
  broadcast: BroadcastInfo | null
  lastSeq: number
  lastMessage: MessagePreview | null
  unreadCount: number
  firstUnreadSeq: number | null
  markedUnread: boolean
  /** No grupo, alguma não lida me menciona (MS-04): o "@" na lista. Ausente no cache offline antigo. */
  unreadMention?: boolean
  readSeq: number
  peerDeliveredSeq: number
  /** Trancada por mim (PV-03): fora da lista, conteúdo só com o código. */
  locked: boolean
  /** Minha escolha de confirmação de leitura aqui; nulo segue o padrão (PV-06). */
  readReceipts: boolean | null
  /** A confirmação de leitura vale para mim nesta conversa. */
  readReceiptsEnabled: boolean
  /** Nulo enquanto a outra pessoa não aceitou a conversa (CT-02) ou se um dos dois desligou a leitura (PV-06). */
  peerReadSeq: number | null
  updatedAt: string
  canSend: boolean
  blockedByMe: boolean
  /** Timer de mensagens temporárias em segundos (MS-07); nulo = desligado. */
  disappearingSeconds: number | null
  /** Meu rascunho nesta conversa, sincronizado entre dispositivos (MS-11). */
  draft: string | null
  /** Como organizei a conversa na minha lista (GR-05); ninguém mais vê. */
  organization: Organization
}

/** Arquivar, fixar, silenciar, favoritar e pastas (GR-05). */
export type Organization = {
  /** Arquivada agora: mensagem nova de outra pessoa tira do arquivo, salvo "manter arquivadas". */
  archived: boolean
  pinnedAt: string | null
  /** Silenciada até (ano 9999 = sempre); pode já ter passado. */
  mutedUntil: string | null
  /** Mesmo silenciada, menções a mim notificam. */
  muteMentions: boolean
  favorite: boolean
  folderIds: string[]
}

/** Lista personalizada (pasta) de conversas (GR-05). */
export type Folder = {
  id: string
  name: string
  conversationIds: string[]
}

export type MuteDuration = "EIGHT_HOURS" | "ONE_WEEK" | "ALWAYS"

/** Preferências de mensagens (MS-07, GR-05). */
export type MessagingPreferences = {
  defaultDisappearingSeconds: number | null
  keepArchived: boolean
}

/** Mensagem agendada (MS-11). */
export type ScheduledMessage = {
  id: string
  conversationId: string
  body: string
  sendAt: string
  silent: boolean
  failedAt: string | null
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
  poll: Poll | null
  /** Visualização única (MS-08): o arquivo só abre por token, uma vez. */
  viewOnce: ViewOnce | null
  /** Enviada sem notificação (MS-11). */
  silent: boolean
  /** Figurinha (MS-10). */
  sticker: { id: string; mediaId: string } | null
}

export type ViewOnce = {
  kind: AttachmentKind
  openedByMe: boolean
  openedCount: number
  expired: boolean
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
