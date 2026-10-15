import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  BookmarkIcon,
  KeyboardIcon,
  MegaphoneIcon,
  MessageSquarePlusIcon,
  SettingsIcon,
  StarIcon,
  UserPlusIcon,
  UsersIcon,
} from "lucide-react"

import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { chatQueries, useConversationLabel } from "@/features/chat"

export type PaletteAction =
  | "newGroup"
  | "newBroadcast"
  | "saved"
  | "addContact"
  | "starred"
  | "settings"
  | "invite"
  | "shortcuts"

/** Paleta de comandos (Ctrl/Cmd+K, MS-13): ir para uma conversa ou rodar uma ação do menu. */
export function CommandPalette({
  open,
  onOpenChange,
  onConversation,
  onAction,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConversation: (id: string) => void
  onAction: (action: PaletteAction) => void
}) {
  const { t } = useTranslation()
  const conversations = useQuery({ ...chatQueries.list(false), enabled: open })
  const labelOf = useConversationLabel()
  const actions: {
    action: PaletteAction
    label: string
    icon: typeof StarIcon
  }[] = [
    {
      action: "newGroup",
      label: t("groups.newGroup"),
      icon: MessageSquarePlusIcon,
    },
    { action: "newBroadcast", label: t("broadcast.new"), icon: MegaphoneIcon },
    { action: "saved", label: t("chat.savedMessages"), icon: BookmarkIcon },
    { action: "addContact", label: t("shell.addContact"), icon: UserPlusIcon },
    { action: "starred", label: t("chat.starredMessages"), icon: StarIcon },
    { action: "invite", label: t("shell.invite"), icon: UsersIcon },
    { action: "settings", label: t("shell.settings"), icon: SettingsIcon },
    { action: "shortcuts", label: t("shortcuts.title"), icon: KeyboardIcon },
  ]
  const run = (callback: () => void) => {
    onOpenChange(false)
    callback()
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="top-1/3 translate-y-0 overflow-hidden rounded-xl! p-0"
        showCloseButton={false}
      >
        <DialogTitle className="sr-only">{t("shortcuts.palette")}</DialogTitle>
        <DialogDescription className="sr-only">
          {t("shortcuts.paletteDescription")}
        </DialogDescription>
        <Command label={t("shortcuts.palette")}>
          <CommandInput placeholder={t("shortcuts.search")} />
          <CommandList>
            <CommandEmpty>{t("shortcuts.noResults")}</CommandEmpty>
            {conversations.data && conversations.data.length > 0 ? (
              <CommandGroup heading={t("chat.conversations")}>
                {conversations.data.map((conversation) => (
                  <CommandItem
                    key={conversation.id}
                    value={`${labelOf(conversation)} ${conversation.peer?.username ?? ""} ${conversation.id}`}
                    onSelect={() => run(() => onConversation(conversation.id))}
                  >
                    {labelOf(conversation)}
                  </CommandItem>
                ))}
              </CommandGroup>
            ) : null}
            <CommandGroup heading={t("shortcuts.actions")}>
              {actions.map(({ action, label, icon: Icon }) => (
                <CommandItem
                  key={action}
                  value={label}
                  onSelect={() => run(() => onAction(action))}
                >
                  <Icon />
                  {label}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </DialogContent>
    </Dialog>
  )
}
