import { useEffect, useState } from "react"
import {
  Link,
  Outlet,
  useNavigate,
  useRouterState,
} from "@tanstack/react-router"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  BookmarkIcon,
  CheckCheckIcon,
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
  markAllRead,
  NewGroupDialog,
  useOfflineChat,
  useOpenDirect,
  useOpenSaved,
  useRealtimeSync,
} from "@/features/chat"
import { AddContactDialog, ContactList, MyLinkDialog } from "@/features/contact"
import { InviteDialog } from "@/features/invite"
import { NotificationPrompt, useNotifications } from "@/features/notification"
import {
  lockScreenNow,
  privacyQueries,
  ScreenLockGate,
  usePrivateMode,
} from "@/features/privacy"
import { emitShortcut, matchShortcut, useShortcut } from "@/lib/shortcuts"
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
  const queryClient = useQueryClient()
  const totalUnread = (conversations.data ?? []).reduce(
    (total, conversation) =>
      total +
      (conversation.unreadCount > 0 || conversation.markedUnread ? 1 : 0),
    0
  )
  useNotifications(
    (conversations.data ?? []).reduce(
      (total, conversation) => total + conversation.unreadCount,
      0
    )
  )
  const screenLock = useQuery(privacyQueries.screenLock())
  // A localização resolvida muda junto com o conteúdo da rota (dentro da view transition da navegação): no celular, a
  // troca entre a lista e a conversa entra na animação, e a tela que sai é capturada inteira. A location muda antes,
  // no início da navegação.
  const pathname = useRouterState({
    select: (state) => (state.resolvedLocation ?? state.location).pathname,
  })
  const isHome = pathname === "/"
  const [addOpen, setAddOpen] = useState(false)
  const [linkOpen, setLinkOpen] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [groupOpen, setGroupOpen] = useState(false)
  const [broadcastOpen, setBroadcastOpen] = useState(false)
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)

  // Atalhos globais (MS-13): Ctrl/Cmd+K abre a paleta; "?" fora de um campo de texto mostra a lista; os do WhatsApp
  // (lib/shortcuts.ts) vão para quem sabe fazer a ação.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault()
        setPaletteOpen((open) => !open)
        return
      }
      const shortcut = matchShortcut(event)
      if (shortcut) {
        event.preventDefault()
        emitShortcut(shortcut)
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

  const [tab, setTab] = useState("conversations")
  /** Abre (ou cria) a conversa com a pessoa: pela lista de contatos e pelo "Adicionar contato". */
  async function startChat(username: string) {
    try {
      const conversation = await openDirect.mutateAsync(username)
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
  }

  // Atalhos do WhatsApp que são do app inteiro (os da conversa aberta ficam na conversa).
  const conversationId = /^\/c\/([^/]+)/.exec(pathname)?.[1] ?? null
  const showSidebar = () => {
    if (!window.matchMedia("(min-width: 48rem)").matches && !isHome) {
      void navigate({ to: "/" })
    }
  }
  useShortcut("newChat", () => {
    showSidebar()
    setTab("contacts")
  })
  useShortcut("newGroup", () => setGroupOpen(true))
  useShortcut("profile", () => void navigate({ to: "/settings/profile" }))
  useShortcut("settings", () => void navigate({ to: "/settings/account" }))
  useShortcut("search", () => {
    showSidebar()
    setTab("conversations")
    // A lista pode estar entrando na tela (celular): o foco vai no quadro seguinte.
    window.requestAnimationFrame(() =>
      document
        .querySelector<HTMLInputElement>('aside [role="search"] input')
        ?.focus()
    )
  })
  const goToSibling = (step: 1 | -1) => {
    const order = (conversations.data ?? []).filter(
      (conversation) => !conversation.organization.archived
    )
    if (order.length === 0) {
      return
    }
    const index = order.findIndex(
      (conversation) => conversation.id === conversationId
    )
    const next =
      order[
        index === -1
          ? step === 1
            ? 0
            : order.length - 1
          : (index + step + order.length) % order.length
      ]
    void navigate({
      to: "/c/$conversationId",
      params: { conversationId: next.id },
    })
  }
  useShortcut("nextChat", () => goToSibling(1))
  useShortcut("previousChat", () => goToSibling(-1))

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
                    disabled={totalUnread === 0}
                    onClick={async () => {
                      try {
                        await markAllRead(queryClient, conversations.data ?? [])
                        toast.add({ title: t("chat.allMarkedRead") })
                      } catch (error) {
                        toast.add({
                          title: errorMessage(error, t("common.genericError")),
                          type: "error",
                        })
                      }
                    }}
                  >
                    <CheckCheckIcon />
                    {t("chat.markAllRead")}
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
          <NotificationPrompt />
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
                    onOpen={(contact) => startChat(contact.username)}
                  />
                </div>
              </ScrollArea>
            </TabsContent>
          </Tabs>
        </aside>
        <main
          className={cn(
            "min-h-0 min-w-0 flex-col overflow-y-auto md:[view-transition-name:main-pane]",
            isHome ? "hidden md:flex" : "flex"
          )}
        >
          <MiniPlayer />
          <Outlet />
        </main>
      </div>
      <AddContactDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        onStartChat={startChat}
      />
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
