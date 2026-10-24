import { useEffect, useState } from "react"
import {
  Link,
  Outlet,
  useNavigate,
  useRouterState,
} from "@tanstack/react-router"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  BookmarkIcon,
  EllipsisVerticalIcon,
  LockIcon,
  LockKeyholeIcon,
  LogOutIcon,
  MegaphoneIcon,
  MessageSquarePlusIcon,
  QrCodeIcon,
  SettingsIcon,
  ShieldIcon,
  StarIcon,
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { toast } from "@/components/ui/toast"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { authQueries, useLogout, type Me } from "@/features/auth"
import {
  BroadcastDialog,
  chatQueries,
  ConversationList,
  NewGroupDialog,
  useOfflineChat,
  useOpenDirect,
  useOpenSaved,
  useRealtimeSync,
} from "@/features/chat"
import { AddContactDialog, ContactList, MyLinkDialog } from "@/features/contact"
import { InviteDialog } from "@/features/invite"
import { useNotifications } from "@/features/notification"
import {
  lockScreenNow,
  privacyQueries,
  ScreenLockGate,
  usePrivateMode,
} from "@/features/privacy"
import { CommandPalette } from "./command-palette"
import { ShortcutsDialog } from "./shortcuts-dialog"
import { ConnectionBanner } from "./connection-banner"
import { MiniPlayer } from "@/features/media"
import { errorMessage } from "@/lib/api-client"

/**
 * Layout autenticado (UI-01): lista à esquerda e conteúdo à direita no desktop; no celular, pilha de telas —
 * a lista é a tela inicial e cada rota ocupa a tela inteira, com "voltar". Mantém o WebSocket ligado.
 */
export function AppShell() {
  const me = useQuery(authQueries.me())
  if (!me.data) {
    return null
  }
  return <SignedInShell me={me.data} />
}

function SignedInShell({ me }: { me: Me }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const logout = useLogout()
  const openDirect = useOpenDirect()
  const openSaved = useOpenSaved()
  useRealtimeSync(me.account.id)
  useOfflineChat(me.account.id, me)
  usePrivateMode()
  const conversations = useQuery(chatQueries.list(false))
  useNotifications(
    (conversations.data ?? []).reduce(
      (total, conversation) => total + conversation.unreadCount,
      0
    )
  )
  const screenLock = useQuery(privacyQueries.screenLock())
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  })
  const isHome = pathname === "/"
  const [addOpen, setAddOpen] = useState(false)
  const [linkOpen, setLinkOpen] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [groupOpen, setGroupOpen] = useState(false)
  const [broadcastOpen, setBroadcastOpen] = useState(false)
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)

  // Atalhos globais (MS-13): Ctrl/Cmd+K abre a paleta; "?" fora de um campo de texto mostra a lista.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault()
        setPaletteOpen((open) => !open)
        return
      }
      const target = event.target as HTMLElement | null
      const typing =
        target?.isContentEditable ||
        ["INPUT", "TEXTAREA", "SELECT"].includes(target?.tagName ?? "")
      if (event.key === "?" && !typing && !event.ctrlKey && !event.metaKey) {
        event.preventDefault()
        setShortcutsOpen(true)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [])

  async function openSavedMessages() {
    try {
      const saved = await openSaved.mutateAsync()
      await navigate({
        to: "/c/$conversationId",
        params: { conversationId: saved.id },
      })
    } catch (error) {
      toast.add({
        title: errorMessage(error, t("common.genericError")),
        type: "error",
      })
    }
  }
  const [tab, setTab] = useState("conversations")
  const { account, profile } = me

  return (
    <div className="flex h-dvh min-h-0 flex-col">
      {/* Sem conexão ou reconectando (NT-05): acima das duas colunas, visível também no celular. */}
      <ConnectionBanner />
      <div className="grid min-h-0 flex-1 grid-cols-1 md:grid-cols-[minmax(18rem,26rem)_1fr]">
        <aside
          aria-label={t("chat.sidebar")}
          className={cn(
            "min-h-0 flex-col border-r bg-sidebar text-sidebar-foreground",
            isHome ? "flex" : "hidden md:flex"
          )}
        >
          <header className="flex min-h-14 items-center gap-2 px-(--spacing-gutter) pt-[env(safe-area-inset-top)]">
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
                  <DropdownMenuItem onClick={() => setGroupOpen(true)}>
                    <MessageSquarePlusIcon />
                    {t("groups.newGroup")}
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => setBroadcastOpen(true)}>
                    <MegaphoneIcon />
                    {t("broadcast.new")}
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => void openSavedMessages()}>
                    <BookmarkIcon />
                    {t("chat.savedMessages")}
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={() => setInviteOpen(true)}>
                    <UsersIcon />
                    {t("shell.invite")}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => void navigate({ to: "/starred" })}
                  >
                    <StarIcon />
                    {t("chat.starredMessages")}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => void navigate({ to: "/locked" })}
                  >
                    <LockKeyholeIcon />
                    {t("privacy.locked.folder")}
                  </DropdownMenuItem>
                  {screenLock.data?.enabled ? (
                    <DropdownMenuItem onClick={() => lockScreenNow()}>
                      <LockIcon />
                      {t("privacy.screenLock.lockNow")}
                    </DropdownMenuItem>
                  ) : null}
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
          <Tabs
            value={tab}
            onValueChange={(value) => setTab(String(value))}
            className="min-h-0 flex-1 gap-1"
          >
            <TabsList className="mx-(--spacing-gutter) w-[calc(100%-2*var(--spacing-gutter))]">
              <TabsTrigger value="conversations">
                {t("chat.conversations")}
              </TabsTrigger>
              <TabsTrigger value="contacts">{t("shell.contacts")}</TabsTrigger>
            </TabsList>
            <TabsContent value="conversations" className="min-h-0 flex-1">
              <ScrollArea className="h-full">
                <div className="px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
                  <ConversationList meId={account.id} />
                </div>
              </ScrollArea>
            </TabsContent>
            <TabsContent value="contacts" className="min-h-0 flex-1">
              <ScrollArea className="h-full">
                <div className="px-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
                  <ContactList
                    onOpen={async (contact) => {
                      try {
                        const conversation = await openDirect.mutateAsync(
                          contact.username
                        )
                        setTab("conversations")
                        await navigate({
                          to: "/c/$conversationId",
                          params: { conversationId: conversation.id },
                        })
                      } catch (error) {
                        toast.add({
                          title: errorMessage(error, t("common.genericError")),
                          type: "error",
                        })
                      }
                    }}
                  />
                </div>
              </ScrollArea>
            </TabsContent>
          </Tabs>
        </aside>
        <main
          className={cn(
            "min-h-0 min-w-0 flex-col overflow-y-auto",
            isHome ? "hidden md:flex" : "flex"
          )}
        >
          <MiniPlayer />
          <Outlet />
        </main>
      </div>
      <AddContactDialog open={addOpen} onOpenChange={setAddOpen} />
      <MyLinkDialog open={linkOpen} onOpenChange={setLinkOpen} />
      <InviteDialog open={inviteOpen} onOpenChange={setInviteOpen} />
      <NewGroupDialog open={groupOpen} onOpenChange={setGroupOpen} />
      {broadcastOpen ? (
        <BroadcastDialog open onOpenChange={setBroadcastOpen} />
      ) : null}
      <CommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        onConversation={(conversationId) =>
          void navigate({
            to: "/c/$conversationId",
            params: { conversationId },
          })
        }
        onAction={(action) => {
          switch (action) {
            case "newGroup":
              return setGroupOpen(true)
            case "newBroadcast":
              return setBroadcastOpen(true)
            case "saved":
              return void openSavedMessages()
            case "addContact":
              return setAddOpen(true)
            case "starred":
              return void navigate({ to: "/starred" })
            case "settings":
              return void navigate({ to: "/settings/profile" })
            case "invite":
              return setInviteOpen(true)
            case "shortcuts":
              return setShortcutsOpen(true)
          }
        }}
      />
      <ShortcutsDialog open={shortcutsOpen} onOpenChange={setShortcutsOpen} />
      <ScreenLockGate userId={account.id} />
    </div>
  )
}
