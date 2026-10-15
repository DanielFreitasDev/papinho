import { createFileRoute } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { StarredMessages } from "@/features/chat"

/** Mensagens favoritas (MS-04). */
export const Route = createFileRoute("/_app/starred")({
  component: StarredPage,
})

function StarredPage() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeader title={t("chat.starredMessages")} />
      <StarredMessages />
    </>
  )
}
