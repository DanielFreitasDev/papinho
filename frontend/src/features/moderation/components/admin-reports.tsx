import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { FlagIcon } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { formatDateTime } from "@/lib/format"
import { cn } from "cn"
import { moderationQueries, useCloseReport, type Report } from "../api"

/** Fila de denúncias do admin (CT-04, AD-01): abertas da mais antiga; fechadas da mais recente. */
export function AdminReports() {
  const { t } = useTranslation()
  const [open, setOpen] = useState(true)
  const reports = useQuery(moderationQueries.reports(open, 0))

  return (
    <div className="flex flex-col gap-3">
      <Tabs
        value={open ? "open" : "closed"}
        onValueChange={(value) => setOpen(value === "open")}
      >
        <TabsList>
          <TabsTrigger value="open">
            {t("moderation.open")}
            {reports.data && reports.data.openCount > 0 ? (
              <Badge variant="destructive">{reports.data.openCount}</Badge>
            ) : null}
          </TabsTrigger>
          <TabsTrigger value="closed">{t("moderation.closed")}</TabsTrigger>
        </TabsList>
      </Tabs>
      {reports.isPending ? (
        <div role="status" aria-label={t("common.loading")}>
          <Skeleton className="h-32 w-full" />
        </div>
      ) : reports.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t("common.genericError")}
        </p>
      ) : reports.data.items.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FlagIcon />
            </EmptyMedia>
            <EmptyTitle>{t("moderation.empty")}</EmptyTitle>
            <EmptyDescription>
              {t("moderation.emptyDescription")}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <ul className="flex flex-col gap-3">
          {reports.data.items.map((report) => (
            <ReportCard key={report.id} report={report} />
          ))}
        </ul>
      )}
    </div>
  )
}

function ReportCard({ report }: { report: Report }) {
  const { t } = useTranslation()
  const close = useCloseReport()
  const reported = report.reported ? `@${report.reported.username}` : "—"
  const reporter = report.reporter ? `@${report.reporter.username}` : "—"
  return (
    <li className="flex flex-col gap-3 rounded-xl border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-sm font-semibold">
          {t("moderation.reportOf", { reported, reporter })}
        </h2>
        {report.reported && !report.reported.active ? (
          <Badge variant="destructive">{t("admin.disabled")}</Badge>
        ) : null}
        {report.status !== "OPEN" ? (
          <Badge variant="secondary">
            {t(`moderation.status${report.status}`)}
          </Badge>
        ) : null}
        <span className="ml-auto text-xs text-muted-foreground">
          {formatDateTime(report.createdAt)}
        </span>
      </div>
      {report.reason ? (
        <p className="text-sm break-words whitespace-pre-wrap">
          {report.reason}
        </p>
      ) : null}
      <section aria-label={t("moderation.copiedMessages")}>
        <h3 className="mb-1 text-xs font-medium text-muted-foreground">
          {t("moderation.copiedMessages")}
        </h3>
        {report.messages.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t("moderation.noMessages")}
          </p>
        ) : (
          <ol className="flex flex-col gap-1">
            {report.messages.map((message, index) => (
              <li
                key={index}
                className={cn(
                  "rounded-md px-2 py-1 text-sm",
                  message.fromReported ? "bg-destructive/10" : "bg-muted"
                )}
              >
                <span className="font-medium">
                  {message.sender ? `@${message.sender.username}` : "—"}
                </span>{" "}
                <span className="text-xs">
                  {formatDateTime(message.sentAt)}
                </span>
                <p className="break-words whitespace-pre-wrap">
                  {message.body}
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>
      {report.status === "OPEN" ? (
        <div className="flex flex-wrap justify-end gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={close.isPending}
            onClick={() => close.mutate({ id: report.id, action: "dismiss" })}
          >
            {t("moderation.dismiss")}
          </Button>
          <Button
            size="sm"
            disabled={close.isPending}
            onClick={() => close.mutate({ id: report.id, action: "resolve" })}
          >
            {t("moderation.resolve")}
          </Button>
        </div>
      ) : null}
    </li>
  )
}
