import { useSyncExternalStore } from "react"
import { useNavigate } from "@tanstack/react-router"
import { useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { LockIcon } from "lucide-react"

import { PageHeader } from "@/components/layout/page-header"
import { Button } from "@/components/ui/button"
import { UnlockForm, useRelockAll } from "@/features/privacy"
import { subscribeUnlockToken, unlockToken } from "@/lib/unlock-token"
import { chatKeys } from "../api"
import type { Conversation } from "../types"
import { ConversationList } from "./conversation-list"
import { useErrorToast } from "@/hooks/use-error-toast"

/**
 * Pasta das conversas trancadas (PV-03): pede o código e então lista as conversas que ele abre. "Trancar de
 * novo" invalida o token e tira o conteúdo carregado da memória.
 */
export function LockedFolder({ meId }: { meId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const relock = useRelockAll()
  const onError = useErrorToast()
  const token = useSyncExternalStore(subscribeUnlockToken, unlockToken)
  return (
    <>
      <PageHeader
        title={t("privacy.locked.folder")}
        actions={
          token ? (
            <Button
              variant="outline"
              onClick={() => {
                const ids = (
                  queryClient.getQueryData<Conversation[]>(chatKeys.locked) ??
                  []
                ).map((conversation) => conversation.id)
                relock.mutate(ids, {
                  onSettled: () => void navigate({ to: "/" }),
                  onError,
                })
              }}
            >
              <LockIcon />
              {t("privacy.locked.relock")}
            </Button>
          ) : null
        }
      />
      <div className="px-(--spacing-gutter) py-3 md:py-4">
        {token ? (
          <ConversationList meId={meId} locked />
        ) : (
          <UnlockForm title={t("privacy.locked.folderTitle")} />
        )}
      </div>
    </>
  )
}
