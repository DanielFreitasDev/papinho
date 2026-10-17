import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  BanIcon,
  ChevronRightIcon,
  FlagIcon,
  ImagesIcon,
  UserPlusIcon,
  XIcon,
} from "lucide-react"

import { ConfirmDialog } from "@/components/confirm-dialog"
import { UserAvatar } from "@/components/user-avatar"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { toast } from "@/components/ui/toast"
import { contactQueries, useAddContact, useBlock } from "@/features/contact"
import { ReportDialog } from "@/features/moderation"
import { errorMessage } from "@/lib/api-client"
import { refreshQueries } from "@/lib/query-refresh"
import { chatKeys } from "../api"
import type { Attachment, Conversation, Peer } from "../types"
import { ConversationGallery } from "./conversation-gallery"
import { ConversationPrivacy } from "./conversation-privacy"
import { DisappearingSetting } from "./disappearing-select"
import { useErrorToast } from "@/hooks/use-error-toast"

/**
 * Painel "Dados do contato" (UI-01): foto, nome, @usuario e recado, com adicionar aos contatos, bloquear
 * (CT-03) e denunciar (CT-04). À direita da conversa em telas largas; numa gaveta nas menores.
 */
export function ContactDetails({
  conversation,
  peer,
  onClose,
  onOpenImage,
}: {
  conversation: Conversation
  peer: Peer
  onClose: () => void
  onOpenImage: (images: Attachment[], id: string) => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  // Nas mensagens salvas o "contato" sou eu: sem adicionar, bloquear nem denunciar (MS-12).
  const self = conversation.kind === "SELF"
  const person = useQuery({
    ...contactQueries.person(peer.username),
    enabled: peer.active && !self,
  })
  const add = useAddContact()
  const block = useBlock()
  const onError = useErrorToast()
  const [confirmBlock, setConfirmBlock] = useState(false)
  const [reporting, setReporting] = useState(false)
  const [gallery, setGallery] = useState(false)
  const blocked = person.data?.blocked ?? conversation.blockedByMe

  function setBlocked(value: boolean) {
    block.mutate(
      { userId: peer.userId, blocked: value },
      {
        onSuccess: () => {
          void refreshQueries(queryClient, chatKeys.conversations)
          toast.add({
            title: value ? t("chat.blockedToast") : t("chat.unblockedToast"),
          })
        },
        onError: (error) =>
          toast.add({
            title: errorMessage(error, t("common.genericError")),
            type: "error",
          }),
      }
    )
  }

  if (gallery) {
    return (
      <ConversationGallery
        conversationId={conversation.id}
        onBack={() => setGallery(false)}
        onOpenImage={onOpenImage}
      />
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex min-h-14 items-center gap-2 border-b px-2">
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("common.close")}
          onClick={onClose}
        >
          <XIcon />
        </Button>
        <h2 className="text-base font-semibold">{t("chat.contactInfo")}</h2>
      </div>
      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-4">
        <div className="flex flex-col items-center gap-2 text-center">
          <UserAvatar
            privateItem
            userId={peer.userId}
            name={peer.label}
            hasPhoto={peer.hasPhoto}
            version={peer.photoVersion}
            className="size-28 text-2xl"
          />
          <p className="text-lg font-semibold break-words">{peer.label}</p>
          <p className="text-sm text-muted-foreground">@{peer.username}</p>
          {peer.label !== peer.displayName ? (
            <p className="text-sm text-muted-foreground">
              {t("chat.displayNameIs", { name: peer.displayName })}
            </p>
          ) : null}
        </div>
        {person.isPending && peer.active && !self ? (
          <Skeleton className="h-10 w-full" />
        ) : person.data?.about ? (
          <section className="flex flex-col gap-1">
            <h3 className="text-xs font-medium text-muted-foreground">
              {t("chat.about")}
            </h3>
            <p className="text-sm break-words whitespace-pre-wrap">
              {person.data.about}
            </p>
          </section>
        ) : null}
        <Separator />
        <Button
          variant="ghost"
          className="justify-start"
          onClick={() => setGallery(true)}
        >
          <ImagesIcon data-icon="inline-start" />
          <span className="flex-1 text-left">{t("gallery.title")}</span>
          <ChevronRightIcon data-icon="inline-end" />
        </Button>
        {self ? null : (
          <>
            <Separator />
            <DisappearingSetting
              conversation={conversation}
              disabled={conversation.blockedByMe}
            />
            <ConversationPrivacy conversation={conversation} />
          </>
        )}
        <Separator />
        <div className={self ? "hidden" : "flex flex-col gap-1"}>
          {person.data && !person.data.contact ? (
            <Button
              variant="ghost"
              className="justify-start"
              disabled={add.isPending}
              onClick={() =>
                add.mutate(
                  { username: peer.username },
                  {
                    onSuccess: () => {
                      void refreshQueries(queryClient, chatKeys.conversations)
                      toast.add({ title: t("chat.contactAdded") })
                    },
                    onError,
                  }
                )
              }
            >
              <UserPlusIcon data-icon="inline-start" />
              {t("chat.addContact")}
            </Button>
          ) : null}
          <Button
            variant="ghost"
            className="justify-start text-destructive hover:text-destructive"
            disabled={block.isPending}
            onClick={() =>
              blocked ? setBlocked(false) : setConfirmBlock(true)
            }
          >
            <BanIcon data-icon="inline-start" />
            {blocked
              ? t("chat.unblockName", { name: peer.label })
              : t("chat.blockName", { name: peer.label })}
          </Button>
          <Button
            variant="ghost"
            className="justify-start text-destructive hover:text-destructive"
            onClick={() => setReporting(true)}
          >
            <FlagIcon data-icon="inline-start" />
            {t("moderation.reportName", { name: peer.label })}
          </Button>
        </div>
      </div>
      <ConfirmDialog
        open={confirmBlock}
        onOpenChange={setConfirmBlock}
        title={t("chat.blockConfirmTitle", { name: peer.label })}
        description={t("chat.blockConfirmDescription")}
        confirmLabel={t("chat.block")}
        onConfirm={() => setBlocked(true)}
      />
      <ReportDialog
        open={reporting}
        onOpenChange={setReporting}
        conversationId={conversation.id}
        name={peer.label}
        alreadyBlocked={blocked}
      />
    </div>
  )
}
