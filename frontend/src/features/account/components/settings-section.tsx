import type { ReactNode } from "react"

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

/** Bloco de uma tela de configurações. */
export function SettingsSection({
  title,
  description,
  children,
  action,
}: {
  title: string
  description?: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">{title}</h2>
        </CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
        {action}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}
