export type Contact = {
  userId: string
  username: string
  displayName: string
  nickname: string | null
  label: string
  hasPhoto: boolean
  photoVersion: string
  about: string | null
}

export type Person = {
  userId: string
  username: string
  displayName: string
  about: string | null
  hasPhoto: boolean
  photoVersion: string
  contact: boolean
  blocked: boolean
  self: boolean
}

export type Blocked = {
  userId: string
  username: string
  displayName: string
  blockedAt: string
}

export type MyLink = { url: string; qrSvg: string }
