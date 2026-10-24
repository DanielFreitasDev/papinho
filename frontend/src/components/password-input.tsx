import * as React from "react"
import { useTranslation } from "react-i18next"
import { EyeIcon, EyeOffIcon } from "lucide-react"

import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from "@/components/ui/input-group"

/**
 * Campo de senha com o botão "mostrar a senha" do projeto (UI-05). O CSS global esconde o botão nativo de
 * revelar do Edge; este o substitui em todos os navegadores. Repassa tudo ao <input> (inclusive o register do
 * react-hook-form), então autocomplete e gerenciadores de senha continuam funcionando.
 */
export function PasswordInput({
  className,
  ...props
}: Omit<React.ComponentProps<"input">, "type">) {
  const { t } = useTranslation()
  const [visible, setVisible] = React.useState(false)
  return (
    <InputGroup className={className}>
      <InputGroupInput type={visible ? "text" : "password"} {...props} />
      <InputGroupAddon align="inline-end">
        <InputGroupButton
          size="icon-sm"
          aria-label={
            visible ? t("common.hidePassword") : t("common.showPassword")
          }
          aria-controls={props.id}
          disabled={props.disabled}
          onClick={() => setVisible((current) => !current)}
        >
          {visible ? <EyeOffIcon /> : <EyeIcon />}
        </InputGroupButton>
      </InputGroupAddon>
    </InputGroup>
  )
}
