import type { ReactNode } from "react"
import { Radio } from "@base-ui/react/radio"
import { RadioGroup } from "@base-ui/react/radio-group"
import { CheckIcon } from "lucide-react"
import { cn } from "cn"

export type SwatchOption<T extends string> = {
  value: T
  label: string
  /** Valor de data-swatch: as cores da amostra vêm do index.css (CSP sem estilo inline). */
  swatch: string
  /** Conteúdo sobre a amostra (ícone de "nenhum", da imagem própria). */
  children?: ReactNode
}

/**
 * Escolha por amostras redondas com o nome embaixo (paleta de cor, papel de parede), como as do WhatsApp. É um grupo
 * de rádios: setas mudam a escolha, Tab sai do grupo, e o nome de cada amostra é o rótulo dela.
 */
export function SwatchPicker<T extends string>({
  label,
  value,
  options,
  onChange,
  className,
}: {
  label: string
  value: T
  options: SwatchOption<T>[]
  onChange: (value: T) => void
  className?: string
}) {
  return (
    <RadioGroup
      aria-label={label}
      value={value}
      onValueChange={(next) => onChange(next as T)}
      className={cn(
        "grid grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-x-2 gap-y-3",
        className
      )}
    >
      {options.map((option) => (
        <label
          key={option.value}
          className="group/swatch flex min-w-0 cursor-pointer flex-col items-center gap-1.5 rounded-lg py-1 text-center text-xs text-muted-foreground has-data-checked:font-medium has-data-checked:text-foreground"
        >
          <Radio.Root
            value={option.value}
            data-swatch={option.swatch}
            className="relative flex size-12 shrink-0 items-center justify-center rounded-full shadow-xs ring-1 ring-foreground/10 transition-[transform,box-shadow] duration-(--duration-base) ease-(--ease-spring) outline-none group-hover/swatch:scale-105 focus-visible:ring-3 focus-visible:ring-ring/50 active:scale-95 data-checked:ring-3 data-checked:ring-primary data-checked:ring-offset-2 data-checked:ring-offset-background"
          >
            {option.children}
            {/* Selo no canto, que chega com o "pop": a amostra continua mostrando as cores dela. */}
            <Radio.Indicator
              keepMounted
              className="absolute -right-1 -bottom-1 flex size-5 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-sm ring-2 ring-background transition-[opacity,transform] duration-(--duration-base) ease-(--ease-spring) data-unchecked:scale-50 data-unchecked:opacity-0"
            >
              <CheckIcon className="size-3.5" aria-hidden="true" />
            </Radio.Indicator>
          </Radio.Root>
          <span className="w-full px-0.5 leading-tight text-balance break-words">
            {option.label}
          </span>
        </label>
      ))}
    </RadioGroup>
  )
}
