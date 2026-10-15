import { createFileRoute } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { PersonCard } from "@/features/contact"

/** Destino do link e do QR Code de um usuário: /add/<usuario> (CT-01). */
export const Route = createFileRoute("/_app/add/$username")({
  component: AddPage,
})

function AddPage() {
  const { t } = useTranslation()
  const { username } = Route.useParams()
  return (
    <>
      <PageHeader title={t("contacts.addTitle")} />
      <div className="flex flex-1 justify-center p-4">
        <PersonCard username={username} />
      </div>
    </>
  )
}
