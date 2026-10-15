import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { SearchIcon } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
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
import { adminQueries, useUserAction, type AdminUser } from "../api"

/** Usuários da instância: desativar, reativar e redefinir senha (AD-01). */
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

  return (
    <div className="flex flex-col gap-3">
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
      <ItemGroup>
        {users.data?.items.map((user) => {
          const self = user.id === me.data?.account.id
          return (
            <Item key={user.id} variant="outline" size="sm">
              <ItemContent className="min-w-0">
                <ItemTitle className="truncate">
                  {user.displayName}
                  {user.admin ? <Badge variant="secondary">admin</Badge> : null}
                  {!user.enabled ? (
                    <Badge variant="destructive">{t("admin.disabled")}</Badge>
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
                        setPassword({ user, value: result.temporaryPassword })
                      }
                    }}
                  >
                    {t("admin.resetPassword")}
                  </Button>
                </ItemActions>
              ) : null}
            </Item>
          )
        })}
      </ItemGroup>
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
