/** Tipos da API de identidade (espelham os records do backend). */

export type RegistrationMode = "OPEN" | "INVITE_ONLY" | "CLOSED"

export type Instance = {
  name: string
  registrationMode: RegistrationMode
  setupRequired: boolean
}

export type Account = {
  id: string
  username: string
  admin: boolean
  totpEnabled: boolean
  remainingRecoveryCodes: number
  createdAt: string
  nextUsernameChangeAt: string | null
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
