import { useCallback } from "react"
import { useTranslation } from "react-i18next"

import { toast } from "@/components/ui/toast"
import { errorMessage } from "@/lib/api-client"

/**
 * Falha de uma ação visível (UI-03): o detalhe do Problem Details num toast de erro. Use como `onError` de um
 * `.mutate(...)`; a regra de lint exige um `onError` em toda chamada.
 */
export function useErrorToast() {
  const { t } = useTranslation()
  return useCallback(
    (error: unknown) =>
      toast.add({
        title: errorMessage(error, t("common.genericError")),
        type: "error",
      }),
    [t]
  )
}
