import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { instanceQueries } from "@/features/instance/api"

/** Mostra se o backend responde; usado na tela inicial da Fase 0. */
export function ServerStatus() {
  const { t } = useTranslation()
  const health = useQuery(instanceQueries.health())

  if (health.isPending) {
    return <Skeleton className="h-5 w-32" aria-label={t("common.loading")} />
  }
  const up = health.data?.status === "UP"
  return (
    <Badge variant={up ? "default" : "destructive"} role="status">
      {up ? t("home.statusUp") : t("home.statusDown")}
    </Badge>
  )
}
