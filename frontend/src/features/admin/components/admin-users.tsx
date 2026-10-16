import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { SearchIcon, UserPlusIcon } from "lucide-react"

import { QueryState } from "@/components/query-state"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { toast } from "@/components/ui/toast"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupInput,
} from "@/components/ui/input-group"
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemTitle,
} from "@/components/ui/item"
import { useDebouncedValue } from "@/hooks/use-debounced-value"
import { authQueries } from "@/features/auth"
import { InviteDialog } from "@/features/invite"
import { adminQueries, useUserAction, type AdminUser } from "../api"

/** Usuários da instância: convidar, desativar, reativar, redefinir senha e tornar admin (AD-01). */
export function AdminUsers() {
  const { t } = useTranslation()
  const me = useQuery(authQueries.me())
  const [query, setQuery] = useState("")
  const debounced = useDebouncedValue(query, 300)
  const users = useQuery(adminQueries.users(debounced, 0))
  const action = useUserAction()
  const [password, setPassword] = useState<{
    user: AdminUser
    value: string
  } | null>(null)
  const [inviteOpen, setInviteOpen] = useState(false)

  return (
    <div className="flex flex-col gap-3">
      <Button className="self-start" onClick={() => setInviteOpen(true)}>
        <UserPlusIcon data-icon="inline-start" />
        {t("admin.createInvite")}
      </Button>
      <InviteDialog open={inviteOpen} onOpenChange={setInviteOpen} />
      <InputGroup>
        <InputGroupAddon>
          <SearchIcon aria-hidden="true" />
        </InputGroupAddon>
        <InputGroupInput
          aria-label={t("admin.search")}
          placeholder={t("admin.search")}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </InputGroup>
      <QueryState
        query={users}
        isEmpty={(page) => page.items.length === 0}
        empty={{ title: t("admin.noUsers") }}
      >
        {(page) => (
          <ItemGroup>
            {page.items.map((user) => {
              const self = user.id === me.data?.account.id
              return (
                <Item key={user.id} variant="outline" size="sm">
                  <ItemContent className="min-w-0">
                    <ItemTitle className="truncate">
                      {user.displayName}
                      {user.admin ? (
                        <Badge variant="secondary">admin</Badge>
                      ) : null}
                      {!user.enabled ? (
                        <Badge variant="destructive">
                          {t("admin.disabled")}
                        </Badge>
                      ) : null}
                    </ItemTitle>
                    <ItemDescription>
                      @{user.username}
                      {self ? ` (${t("admin.you")})` : ""}
                    </ItemDescription>
                  </ItemContent>
                  {!self ? (
                    <ItemActions className="flex-wrap">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() =>
                          action.mutate({
                            id: user.id,
                            action: user.enabled ? "disable" : "enable",
                          })
                        }
                      >
                        {user.enabled ? t("admin.disable") : t("admin.enable")}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={async () => {
                          const result = await action.mutateAsync({
                            id: user.id,
                            action: "reset-password",
                          })
                          if (result?.temporaryPassword) {
                            setPassword({
                              user,
                              value: result.temporaryPassword,
                            })
                          }
                        }}
                      >
                        {t("admin.resetPassword")}
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() =>
                          action.mutate(
                            {
                              id: user.id,
                              action: user.admin
                                ? "remove-admin"
                                : "make-admin",
                            },
                            {
                              onSuccess: () =>
                                toast.add({ title: t("admin.roleChanged") }),
                            }
                          )
                        }
                      >
                        {user.admin
                          ? t("admin.removeAdmin")
                          : t("admin.makeAdmin")}
                      </Button>
                    </ItemActions>
                  ) : null}
                </Item>
              )
            })}
          </ItemGroup>
        )}
      </QueryState>
      <Dialog
        open={password !== null}
        onOpenChange={(open) => !open && setPassword(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t("admin.temporaryPassword", {
                name: password?.user.displayName ?? "",
              })}
            </DialogTitle>
            <DialogDescription>
              {t("admin.temporaryPasswordDescription")}
            </DialogDescription>
          </DialogHeader>
          <code className="rounded-md bg-muted p-3 text-center font-mono text-lg select-all">
            {password?.value}
          </code>
          <DialogFooter>
            <Button onClick={() => setPassword(null)}>
              {t("common.close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
