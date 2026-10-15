import { Link } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"
import { FileQuestionIcon } from "lucide-react"

import { buttonVariants } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"

export function NotFound() {
  const { t } = useTranslation()
  return (
    <main className="flex flex-1 items-center justify-center p-4">
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <FileQuestionIcon />
          </EmptyMedia>
          <EmptyTitle>{t("errors.notFoundTitle")}</EmptyTitle>
          <EmptyDescription>{t("errors.notFoundDescription")}</EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Link to="/" className={buttonVariants({ variant: "outline" })}>
            {t("common.goHome")}
          </Link>
        </EmptyContent>
      </Empty>
    </main>
  )
}
