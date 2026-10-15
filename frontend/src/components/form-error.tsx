import type { FieldError as HookFieldError } from "react-hook-form"
import { useTranslation } from "react-i18next"

import { FieldError } from "@/components/ui/field"

/**
 * Erro de um campo do react-hook-form. Os schemas zod guardam chaves de tradução como mensagem, para que o
 * texto acompanhe a troca de idioma sem recriar os schemas.
 */
export function FormFieldError({ error }: { error?: HookFieldError }) {
  const { t } = useTranslation()
  if (!error?.message) {
    return null
  }
  return <FieldError>{t(error.message)}</FieldError>
}
