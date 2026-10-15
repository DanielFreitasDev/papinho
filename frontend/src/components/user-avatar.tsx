import { cn } from "cn"

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

/** Iniciais para o fallback do avatar ("Ana Souza" → "AS"). */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  const letters = parts.length > 1 ? [parts[0], parts[parts.length - 1]] : parts
  return (
    letters.map((part) => [...part][0]?.toUpperCase() ?? "").join("") || "?"
  )
}

/**
 * Avatar de um usuário: miniatura do servidor quando há foto visível (o servidor aplica bloqueio e
 * privacidade) e iniciais caso contrário. `version` muda a URL quando a foto muda.
 */
export function UserAvatar({
  userId,
  name,
  hasPhoto,
  version,
  className,
}: {
  userId: string
  name: string
  hasPhoto: boolean
  version?: string
  className?: string
}) {
  const src = hasPhoto
    ? `/api/v1/users/${userId}/photo?thumbnail=true&v=${encodeURIComponent(version ?? "")}`
    : undefined
  return (
    <Avatar className={cn("size-10", className)}>
      {src ? <AvatarImage src={src} alt="" /> : null}
      <AvatarFallback aria-hidden="true">{initials(name)}</AvatarFallback>
    </Avatar>
  )
}
