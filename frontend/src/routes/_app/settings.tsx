import { useEffect, useRef } from "react"
import {
  createFileRoute,
  Link,
  Outlet,
  useRouterState,
} from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/layout/page-header"
import { tabsListVariants } from "@/components/ui/tabs"

const SECTIONS = [
  { to: "/settings/profile", key: "settings.profile" },
  { to: "/settings/account", key: "settings.account" },
  { to: "/settings/security", key: "settings.security" },
  { to: "/settings/sessions", key: "settings.sessions" },
  { to: "/settings/privacy", key: "settings.privacy" },
  { to: "/settings/blocked", key: "settings.blocked" },
  { to: "/settings/chats", key: "settings.chats" },
  { to: "/settings/notifications", key: "settings.notifications" },
  { to: "/settings/appearance", key: "settings.appearance" },
  { to: "/settings/transcription", key: "settings.transcription" },
] as const

export const Route = createFileRoute("/_app/settings")({
  component: SettingsLayout,
})

/** Configurações com navegação por seções; cada seção tem rota própria (deep link). */
function SettingsLayout() {
  const { t } = useTranslation()
  const nav = useRef<HTMLElement>(null)
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  })
  // A seção aberta fica à vista na faixa que rola de lado (no celular, "Privacidade" em diante ficava cortada).
  useEffect(() => {
    const strip = nav.current
    const active = strip?.querySelector<HTMLElement>("[data-status=active]")
    if (strip && active) {
      const box = active.getBoundingClientRect()
      const area = strip.getBoundingClientRect()
      strip.scrollLeft += box.left - area.left - (area.width - box.width) / 2
    }
  }, [pathname])
  return (
    <>
      <PageHeader title={t("settings.title")} />
      {/* Lista de seções que rola de lado de propósito no celular (data-scroll-x, AR-02). shrink-0: com
          overflow, o item do flex do <main> perde a altura mínima e some numa seção alta (Privacidade). */}
      <nav
        ref={nav}
        aria-label={t("settings.title")}
        className="shrink-0 scroll-fade-x overflow-x-auto px-(--spacing-gutter) pt-3"
        data-scroll-x=""
      >
        <div className={tabsListVariants({ className: "w-max" })}>
          {SECTIONS.map((section) => (
            <Link
              key={section.to}
              to={section.to}
              className="inline-flex min-h-7 items-center rounded-md px-2.5 text-sm text-foreground/90 transition-colors hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none data-[status=active]:bg-background data-[status=active]:font-medium data-[status=active]:text-foreground data-[status=active]:shadow-sm pointer-coarse:min-h-11"
            >
              {t(section.key)}
            </Link>
          ))}
        </div>
      </nav>
      <div className="mx-auto w-full max-w-2xl px-(--spacing-gutter) pt-3 pb-[max(1rem,env(safe-area-inset-bottom))] md:pt-4">
        <Outlet />
      </div>
    </>
  )
}
