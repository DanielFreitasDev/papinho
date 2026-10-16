/** Quem pode ver (ou adicionar a grupos): as quatro opções do WhatsApp (PV-04, PV-07). */
export type Visibility = "EVERYONE" | "CONTACTS" | "CONTACTS_EXCEPT" | "NOBODY"

export type PrivacyItem =
  "LAST_SEEN" | "ONLINE" | "PHOTO" | "ABOUT" | "GROUPS_ADD"

/** Desfoque do modo privado (PV-01). */
export type BlurSettings = {
  privateMode: boolean
  messages: boolean
  previews: boolean
  names: boolean
  photos: boolean
  media: boolean
  /** Tempo com o mouse em cima para revelar; 0 = na hora. */
  revealDelayMs: number
}

export type PrivacySettings = {
  lastSeen: Visibility
  online: Visibility
  photo: Visibility
  about: Visibility
  groupsAdd: Visibility
  readReceipts: boolean
  hideNotificationContent: boolean
  blur: BlurSettings
}

export type PrivacyState = {
  settings: PrivacySettings
  exceptions: Record<PrivacyItem, string[]>
}

export type PrivacySettingsChange = Partial<
  Omit<PrivacySettings, "blur"> & {
    privateMode: boolean
    blurMessages: boolean
    blurPreviews: boolean
    blurNames: boolean
    blurPhotos: boolean
    blurMedia: boolean
    revealDelayMs: number
  }
>

export type ScreenLockKind = "PIN" | "PASSWORD"

/** Bloqueio de tela (PV-02); tudo desligado por padrão. */
export type ScreenLock = {
  enabled: boolean
  kind: ScreenLockKind | null
  idleMinutes: number | null
  lockOnHidden: boolean
  shortcut: string | null
  allowPasskey: boolean
}

export type ScreenLockChange = {
  kind?: ScreenLockKind
  secret?: string
  currentSecret?: string
  idleMinutes: number | null
  lockOnHidden: boolean
  shortcut: string | null
  allowPasskey: boolean
}

export type Unlocked = { token: string; expiresAt: string }
