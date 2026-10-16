import type { ReactNode } from "react"
import type { UseQueryResult } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import {
  CircleAlertIcon,
  InboxIcon,
  LockIcon,
  RotateCwIcon,
  WifiOffIcon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { Spinner } from "@/components/ui/spinner"
import { ApiError, errorMessage } from "@/lib/api-client"

export type QueryProblem = "forbidden" | "offline" | "error"

/** Classifica a falha: 403 é "sem permissão"; falha sem resposta do servidor é "sem conexão". */
export function queryProblem(error: unknown): QueryProblem {
  if (error instanceof ApiError) {
    return error.status === 403 ? "forbidden" : "error"
  }
  return "offline"
}

type QueryStateProps<T> = {
  query: Pick<
    UseQueryResult<T>,
    "data" | "error" | "isPending" | "isError" | "isFetching" | "refetch"
  >
  children: (data: T) => ReactNode
  /** Dados que contam como vazio (por exemplo, lista sem itens). */
  isEmpty?: (data: T) => boolean
  empty?: { title: string; description?: string; icon?: ReactNode }
  /** Esqueleto próprio do carregamento; o padrão é um indicador com texto. */
  loading?: ReactNode
}

/**
 * Estados de uma consulta (UI-03): carregando, vazio, erro com "Tentar de novo", sem conexão e sem permissão.
 * Os dados só chegam ao `children` quando existem.
 */
export function QueryState<T>({
  query,
  children,
  isEmpty,
  empty,
  loading,
}: QueryStateProps<T>) {
  const { t } = useTranslation()
  if (query.isPending) {
    return (
      loading ?? (
        <p
          role="status"
          className="flex items-center justify-center gap-2 py-6 text-sm text-muted-foreground"
        >
          <Spinner role="none" aria-hidden="true" aria-label={undefined} />
          {t("common.loading")}
        </p>
      )
    )
  }
  if (query.isError || query.data === undefined) {
    const problem = queryProblem(query.error)
    const icon =
      problem === "forbidden" ? (
        <LockIcon />
      ) : problem === "offline" ? (
        <WifiOffIcon />
      ) : (
        <CircleAlertIcon />
      )
    return (
      <Empty role="alert" className="py-6">
        <EmptyHeader>
          <EmptyMedia variant="icon">{icon}</EmptyMedia>
          <EmptyTitle>{t(`states.${problem}Title`)}</EmptyTitle>
          <EmptyDescription>
            {problem === "error"
              ? errorMessage(query.error, t("errors.genericDescription"))
              : t(`states.${problem}Description`)}
          </EmptyDescription>
        </EmptyHeader>
        {problem === "forbidden" ? null : (
          <EmptyContent>
            <Button
              variant="outline"
              disabled={query.isFetching}
              onClick={() => void query.refetch()}
            >
              {query.isFetching ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <RotateCwIcon data-icon="inline-start" />
              )}
              {t("common.retry")}
            </Button>
          </EmptyContent>
        )}
      </Empty>
    )
  }
  if (empty && isEmpty?.(query.data)) {
    return (
      <Empty className="py-6">
        <EmptyHeader>
          <EmptyMedia variant="icon">{empty.icon ?? <InboxIcon />}</EmptyMedia>
          <EmptyTitle>{empty.title}</EmptyTitle>
          {empty.description ? (
            <EmptyDescription>{empty.description}</EmptyDescription>
          ) : null}
        </EmptyHeader>
      </Empty>
    )
  }
  return <>{children(query.data)}</>
}
