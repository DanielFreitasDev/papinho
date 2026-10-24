import * as React from "react"
import { useTranslation } from "react-i18next"
import { SearchIcon, XIcon } from "lucide-react"

import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from "@/components/ui/input-group"

/**
 * Campo de busca com lupa e botão de limpar do projeto (UI-05): o botão nativo do type="search" fica escondido
 * pelo CSS global e este o substitui, devolvendo o foco ao campo.
 */
export function SearchInput({
  value,
  onValueChange,
  className,
  ref,
  ...props
}: Omit<React.ComponentProps<"input">, "value" | "onChange" | "type"> & {
  value: string
  onValueChange: (value: string) => void
}) {
  const { t } = useTranslation()
  const input = React.useRef<HTMLInputElement | null>(null)
  const setRef = (element: HTMLInputElement | null) => {
    input.current = element
    if (typeof ref === "function") {
      ref(element)
    } else if (ref) {
      ref.current = element
    }
  }
  return (
    <InputGroup className={className}>
      <InputGroupAddon>
        <SearchIcon aria-hidden="true" />
      </InputGroupAddon>
      <InputGroupInput
        ref={setRef}
        type="search"
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        {...props}
      />
      {value ? (
        <InputGroupAddon align="inline-end">
          <InputGroupButton
            size="icon-sm"
            aria-label={t("common.clearSearch")}
            onClick={() => {
              onValueChange("")
              input.current?.focus()
            }}
          >
            <XIcon />
          </InputGroupButton>
        </InputGroupAddon>
      ) : null}
    </InputGroup>
  )
}
