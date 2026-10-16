/** Tipos da API de identidade (espelham os records do backend). */

export type RegistrationMode = "OPEN" | "INVITE_ONLY" | "CLOSED"

export type Instance = {
  name: string
  registrationMode: RegistrationMode
  setupRequired: boolean
  /** Janela para editar mensagens (MS-05). */
  messageEditWindowMinutes: number
  /** Janela para apagar para todos (MS-06). */
  messageDeleteWindowMinutes: number
  /** A instância envia links de recuperação por e-mail (ID-05). */
  emailRecovery: boolean
}

export type Account = {
  id: string
  username: string
  admin: boolean
  totpEnabled: boolean
  remainingRecoveryCodes: number
  createdAt: string
  nextUsernameChangeAt: string | null
  /** E-mail de recuperação (ID-05), confirmado ou não. */
  email: string | null
  emailVerified: boolean
}

export type Profile = {
  userId: string
  displayName: string
  about: string | null
  hasPhoto: boolean
  updatedAt: string
}

export type Me = {
  account: Account
  profile: Profile
}

export type RegisterResponse = {
  me: Me
  recoveryCodes: string[]
}
