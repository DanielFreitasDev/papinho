import { createFileRoute, Link, Outlet } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { tabsListVariants } from "@/components/ui/tabs"

const SECTIONS = [
  { to: "/settings/profile", key: "settings.profile" },
  { to: "/settings/account", key: "settings.account" },
  { to: "/settings/security", key: "settings.security" },
  { to: "/settings/sessions", key: "settings.sessions" },
  { to: "/settings/blocked", key: "settings.blocked" },
  { to: "/settings/appearance", key: "settings.appearance" },
  { to: "/settings/transcription", key: "settings.transcription" },
] as const

export const Route = createFileRoute("/_app/settings")({
  component: SettingsLayout,
})

/** Configurações com navegação por seções; cada seção tem rota própria (deep link). */
function SettingsLayout() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeader title={t("settings.title")} />
      <nav
        aria-label={t("settings.title")}
        className="overflow-x-auto px-2 pt-3 md:px-4"
      >
        <div className={tabsListVariants({ className: "w-max" })}>
          {SECTIONS.map((section) => (
            <Link
              key={section.to}
              to={section.to}
              className="inline-flex min-h-7 items-center rounded-md px-2.5 text-sm text-foreground/90 transition-colors hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none data-[status=active]:bg-background data-[status=active]:font-medium data-[status=active]:text-foreground data-[status=active]:shadow-sm"
            >
              {t(section.key)}
            </Link>
          ))}
        </div>
      </nav>
      <div className="mx-auto w-full max-w-2xl p-2 pb-[max(1rem,env(safe-area-inset-bottom))] md:p-4">
        <Outlet />
      </div>
    </>
  )
}
