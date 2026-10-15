import type { ComponentProps } from "react"

import { Input } from "@/components/ui/input"

/**
 * Campo de código de 6 dígitos (TOTP). Um `Input` comum com teclado numérico e preenchimento automático
 * do código recebido: o componente input-otp do registry injeta um <style> em tempo de execução, que a CSP
 * estrita (style-src 'self') bloqueia.
 */
export function OtpInput({
  value,
  onChange,
  className,
  ...props
}: Omit<ComponentProps<typeof Input>, "onChange" | "value"> & {
  value: string
  onChange: (value: string) => void
}) {
  return (
    <Input
      type="text"
      inputMode="numeric"
      autoComplete="one-time-code"
      pattern="[0-9]{6}"
      maxLength={6}
      spellCheck={false}
      value={value}
      onChange={(event) =>
        onChange(event.target.value.replace(/\D/g, "").slice(0, 6))
      }
      className={[
        "w-36 text-center font-mono text-lg tracking-[0.4em]",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      {...props}
    />
  )
}
