import { createFileRoute } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { JoinGroup } from "@/features/chat"

/** Destino do link e do QR Code de convite de um grupo: /g/<código> (GR-02). */
export const Route = createFileRoute("/_app/g/$code")({
  component: JoinPage,
})

function JoinPage() {
  const { t } = useTranslation()
  const { code } = Route.useParams()
  return (
    <>
      <PageHeader title={t("groups.joinTitle")} />
      <div className="flex flex-1 justify-center p-4">
        <JoinGroup code={code} />
      </div>
    </>
  )
}
