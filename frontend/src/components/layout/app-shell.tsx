import { useState } from "react"
import {
  Link,
  Outlet,
  useNavigate,
  useRouterState,
} from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  EllipsisVerticalIcon,
  LogOutIcon,
  QrCodeIcon,
  SettingsIcon,
  ShieldIcon,
  UserPlusIcon,
  UsersIcon,
} from "lucide-react"
import { cn } from "cn"

import { UserAvatar } from "@/components/user-avatar"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { authQueries, useLogout } from "@/features/auth"
import { AddContactDialog, ContactList, MyLinkDialog } from "@/features/contact"
import { InviteDialog } from "@/features/invite"

/**
 * Layout autenticado (UI-01): lista à esquerda e conteúdo à direita no desktop; no celular, pilha de telas —
 * a lista é a tela inicial e cada rota ocupa a tela inteira, com "voltar".
 */
export function AppShell() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const me = useQuery(authQueries.me())
  const logout = useLogout()
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  })
  const isHome = pathname === "/"
  const [addOpen, setAddOpen] = useState(false)
  const [linkOpen, setLinkOpen] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)

  if (!me.data) {
    return null
  }
  const { account, profile } = me.data

  return (
    <div className="grid h-dvh min-h-0 grid-cols-1 md:grid-cols-[minmax(18rem,26rem)_1fr]">
      <aside
        aria-label={t("shell.contacts")}
        className={cn(
          "min-h-0 flex-col border-r bg-sidebar text-sidebar-foreground",
          isHome ? "flex" : "hidden md:flex"
        )}
      >
        <header className="flex min-h-14 items-center gap-2 px-3 pt-[env(safe-area-inset-top)]">
          <Link
            to="/settings/profile"
            className="flex min-w-0 flex-1 items-center gap-2 rounded-lg p-1 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            <UserAvatar
              userId={profile.userId}
              name={profile.displayName}
              hasPhoto={profile.hasPhoto}
              version={profile.updatedAt}
              className="size-9"
            />
            <span className="flex min-w-0 flex-col">
              <span className="truncate text-sm font-medium">
                {profile.displayName}
              </span>
              <span className="truncate text-xs text-muted-foreground">
                @{account.username}
              </span>
            </span>
          </Link>
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t("shell.addContact")}
                  onClick={() => setAddOpen(true)}
                />
              }
            >
              <UserPlusIcon />
            </TooltipTrigger>
            <TooltipContent>{t("shell.addContact")}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t("contacts.myLink")}
                  onClick={() => setLinkOpen(true)}
                />
              }
            >
              <QrCodeIcon />
            </TooltipTrigger>
            <TooltipContent>{t("contacts.myLink")}</TooltipContent>
          </Tooltip>
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t("shell.menu")}
                />
              }
            >
              <EllipsisVerticalIcon />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuGroup>
                <DropdownMenuItem onClick={() => setInviteOpen(true)}>
                  <UsersIcon />
                  {t("shell.invite")}
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => void navigate({ to: "/settings/profile" })}
                >
                  <SettingsIcon />
                  {t("shell.settings")}
                </DropdownMenuItem>
                {account.admin ? (
                  <DropdownMenuItem
                    onClick={() => void navigate({ to: "/admin" })}
                  >
                    <ShieldIcon />
                    {t("shell.admin")}
                  </DropdownMenuItem>
                ) : null}
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem
                  onClick={async () => {
                    await logout.mutateAsync().catch(() => undefined)
                    await navigate({ to: "/login" })
                  }}
                >
                  <LogOutIcon />
                  {t("auth.logout")}
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>
        <h2 className="px-4 pt-2 pb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">
          {t("shell.contacts")}
        </h2>
        <ScrollArea className="min-h-0 flex-1">
          <div className="px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
            <ContactList />
          </div>
        </ScrollArea>
      </aside>
      <main
        className={cn(
          "min-h-0 min-w-0 flex-col overflow-y-auto",
          isHome ? "hidden md:flex" : "flex"
        )}
      >
        <Outlet />
      </main>
      <AddContactDialog open={addOpen} onOpenChange={setAddOpen} />
      <MyLinkDialog open={linkOpen} onOpenChange={setLinkOpen} />
      <InviteDialog open={inviteOpen} onOpenChange={setInviteOpen} />
    </div>
  )
}
