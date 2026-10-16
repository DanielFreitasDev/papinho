import { queryOptions, useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Skeleton } from "@/components/ui/skeleton"
import { SettingsSection } from "@/features/account"
import { formatBytes } from "@/features/media"
import { apiFetch } from "@/lib/api-client"

type StorageUsage = {
  filesBytes: number
  fileCount: number
  databaseBytes: number
  freeBytes: number | null
  totalBytes: number | null
  top: {
    userId: string
    username: string | null
    bytes: number
    files: number
  }[]
}

const storageQuery = queryOptions({
  queryKey: ["admin", "storage"],
  queryFn: ({ signal }) =>
    apiFetch<StorageUsage>("/api/v1/admin/storage", { signal }),
})

/** Uso de disco (AD-01): arquivos enviados, banco, espaço livre e quem mais ocupa. */
export function AdminStorage() {
  const { t, i18n } = useTranslation()
  const usage = useQuery(storageQuery)
  const bytes = (value: number) => formatBytes(value, i18n.language)
  if (usage.isPending) {
    return <Skeleton className="h-40 w-full" />
  }
  if (usage.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{t("errors.genericDescription")}</AlertDescription>
      </Alert>
    )
  }
  const data = usage.data
  return (
    <div className="flex flex-col gap-4">
      <SettingsSection title={t("admin.storage.title")}>
        <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 text-sm">
          <dt>{t("admin.storage.files", { count: data.fileCount })}</dt>
          <dd className="text-right font-medium">{bytes(data.filesBytes)}</dd>
          <dt>{t("admin.storage.database")}</dt>
          <dd className="text-right font-medium">
            {bytes(data.databaseBytes)}
          </dd>
          {data.freeBytes !== null && data.totalBytes !== null ? (
            <>
              <dt>{t("admin.storage.free")}</dt>
              <dd className="text-right font-medium">
                {t("admin.storage.ofTotal", {
                  free: bytes(data.freeBytes),
                  total: bytes(data.totalBytes),
                })}
              </dd>
            </>
          ) : null}
        </dl>
      </SettingsSection>
      <SettingsSection title={t("admin.storage.top")}>
        {data.top.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t("admin.storage.none")}
          </p>
        ) : (
          <ol className="flex flex-col divide-y text-sm">
            {data.top.map((user) => (
              <li key={user.userId} className="flex justify-between gap-3 py-2">
                <span className="truncate">@{user.username ?? "?"}</span>
                <span className="shrink-0 text-muted-foreground">
                  {t("admin.storage.userFiles", {
                    count: user.files,
                    size: bytes(user.bytes),
                  })}
                </span>
              </li>
            ))}
          </ol>
        )}
      </SettingsSection>
    </div>
  )
}
