import { createFileRoute, redirect } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AdminSettingsForm, AdminUsers } from "@/features/admin"
import { AdminReports } from "@/features/moderation"
import { AdminTranscription } from "@/features/transcription"

/** Painel do admin (AD-01); o servidor também exige ROLE_ADMIN em toda a API /admin. */
export const Route = createFileRoute("/_app/admin")({
  beforeLoad: ({ context }) => {
    if (!context.me.account.admin) {
      throw redirect({ to: "/" })
    }
  },
  component: AdminPage,
})

function AdminPage() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeader title={t("admin.title")} />
      <div className="mx-auto w-full max-w-2xl p-2 md:p-4">
        <Tabs defaultValue="settings">
          <TabsList>
            <TabsTrigger value="settings">{t("admin.settings")}</TabsTrigger>
            <TabsTrigger value="users">{t("admin.users")}</TabsTrigger>
            <TabsTrigger value="reports">{t("admin.reports")}</TabsTrigger>
            <TabsTrigger value="transcription">
              {t("admin.transcription")}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="settings" className="pt-3">
            <AdminSettingsForm />
          </TabsContent>
          <TabsContent value="users" className="pt-3">
            <AdminUsers />
          </TabsContent>
          <TabsContent value="reports" className="pt-3">
            <AdminReports />
          </TabsContent>
          <TabsContent value="transcription" className="pt-3">
            <AdminTranscription />
          </TabsContent>
        </Tabs>
      </div>
    </>
  )
}
