import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
  type RefObject,
} from "react"
import { createPortal } from "react-dom"
import { useTranslation } from "react-i18next"
import { cn } from "cn"
import {
  BoldIcon,
  ClipboardPasteIcon,
  CodeIcon,
  CopyIcon,
  EyeOffIcon,
  ItalicIcon,
  ListIcon,
  ListOrderedIcon,
  ScissorsIcon,
  SquareCodeIcon,
  StrikethroughIcon,
  TextQuoteIcon,
  TextSelectIcon,
  type LucideIcon,
} from "lucide-react"

import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
} from "@/components/ui/dropdown-menu"
import { Separator } from "@/components/ui/separator"
import { toast } from "@/components/ui/toast"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { useContextMenu } from "@/hooks/use-context-menu"
import {
  activeFormats,
  analyze,
  applyFormat,
  BLOCK_FORMATS,
  canFormat,
  FORMAT_KEYS,
  formatShortcut,
  INLINE_FORMATS,
  MOD,
  type Analysis,
  type Edit,
  type Format,
  type Look,
  type Segment,
} from "../format/compose"

const ICONS: Record<Format, LucideIcon> = {
  bold: BoldIcon,
  italic: ItalicIcon,
  strike: StrikethroughIcon,
  code: CodeIcon,
  spoiler: EyeOffIcon,
  quote: TextQuoteIcon,
  bulletList: ListIcon,
  orderedList: ListOrderedIcon,
  codeBlock: SquareCodeIcon,
}

let replacing = false

/** Uma troca da formatação está em andamento: o input que ela dispara não é digitação (nada de ":)" virar emoji). */
export function replacingText() {
  return replacing
}

/**
 * Troca o trecho pelo `insertText` do navegador, que entra no desfazer (Ctrl+Z) e dispara o input do React; sem ele
 * (navegador antigo), o setRangeText e um input sintético fazem o mesmo, só sem o desfazer.
 */
function replaceText(field: HTMLTextAreaElement, edit: Edit) {
  const before = field.value
  const expected =
    before.slice(0, edit.from) + edit.insert + before.slice(edit.to)
  field.focus()
  field.setSelectionRange(edit.from, edit.to)
  let done: boolean
  replacing = true
  try {
    done =
      edit.insert === ""
        ? document.execCommand("delete")
        : document.execCommand("insertText", false, edit.insert)
  } catch {
    done = false
  } finally {
    replacing = false
  }
  if (!done || field.value !== expected) {
    if (field.value !== before) {
      field.setRangeText(before, 0, field.value.length)
    }
    field.setRangeText(edit.insert, edit.from, edit.to, "end")
    replacing = true
    try {
      field.dispatchEvent(new Event("input", { bubbles: true }))
    } finally {
      replacing = false
    }
  }
  field.setSelectionRange(edit.selection[0], edit.selection[1])
}

/** O nó de texto e a posição dentro dele para a posição `offset` do texto inteiro. */
function locate(root: HTMLElement, offset: number): [Node, number] {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  let remaining = offset
  let last: Text | null = null
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const text = node as Text
    if (remaining <= text.length) {
      return [text, remaining]
    }
    remaining -= text.length
    last = text
  }
  return last ? [last, last.length] : [root, 0]
}

/** A primeira linha da seleção na tela, medida no espelho do texto (o textarea não diz onde cada letra está). */
function selectionBox(mirror: HTMLElement, start: number, end: number) {
  const range = document.createRange()
  range.setStart(...locate(mirror, start))
  range.setEnd(...locate(mirror, end))
  const rects = [...range.getClientRects()].filter(
    (rect) => rect.width > 0 || rect.height > 0
  )
  if (rects.length === 0) {
    return range.getBoundingClientRect()
  }
  const top = Math.min(...rects.map((rect) => rect.top))
  const line = rects.filter((rect) => rect.top < top + 2)
  const left = Math.min(...line.map((rect) => rect.left))
  const right = Math.max(...line.map((rect) => rect.right))
  return new DOMRect(left, top, right - left, line[0].height)
}

function syncScroll(
  field: HTMLTextAreaElement | null,
  mirror: HTMLElement | null
) {
  if (field && mirror) {
    mirror.scrollTop = field.scrollTop
  }
}

/** Distância da barra até a borda da tela, como menus e popovers (px). */
const EDGE = 12

/**
 * Estilo de cada aparência no espelho. Nada muda a largura das letras (o cursor e a seleção são os do textarea, por
 * cima): o negrito é um contorno do próprio texto, o itálico é o sintetizado da Geist (que não tem itálico), código e
 * spoiler ganham fundo sem respiro e os marcadores ficam apagados.
 */
const LOOKS: Record<Look, string> = {
  bold: "[-webkit-text-stroke:0.035em_currentColor]",
  italic: "italic",
  strike: "line-through",
  spoiler: "rounded-sm bg-foreground/15",
  code: "rounded-sm bg-foreground/10",
  codeBlock: "bg-foreground/10",
  marker: "text-muted-foreground",
  link: "underline underline-offset-2",
  mention: "text-primary",
}

/**
 * Espelho do campo: o mesmo texto, letra por letra no mesmo lugar, desenhado com a formatação por baixo do textarea
 * (que fica com o texto transparente). Tipografia, respiro e quebra de linha precisam ser iguais aos do textarea.
 */
export function FormattedMirror({
  ref,
  segments,
  text,
  className,
}: {
  ref: RefObject<HTMLDivElement | null>
  segments: Segment[]
  text: string
  className?: string
}) {
  return (
    <div
      ref={ref}
      aria-hidden="true"
      data-composer-mirror=""
      className={cn(
        "pointer-events-none absolute inset-0 scrollbar-gutter-stable overflow-hidden wrap-break-word whitespace-pre-wrap text-foreground forced-colors:invisible",
        className
      )}
    >
      {segments.map((segment) => (
        <span
          key={segment.from}
          data-looks={segment.looks.join(" ") || undefined}
          className={cn(segment.looks.map((look) => LOOKS[look]))}
        >
          {text.slice(segment.from, segment.to)}
        </span>
      ))}
      {/* A última linha vazia (texto que termina em "\n") também ocupa altura, como no textarea. */}
      {"​"}
    </div>
  )
}

/**
 * Formatação no campo de mensagem: o espelho com o texto formatado, a barra que aparece ao selecionar (como a do
 * WhatsApp Web), o menu do botão direito com a formatação e o recortar/copiar/colar, e os atalhos do teclado. O
 * campo recebe os handlers em `fieldProps`.
 */
export function useComposerFormat({
  field,
  mirror,
  value,
  disabled,
}: {
  field: RefObject<HTMLTextAreaElement | null>
  /** O espelho (FormattedMirror) do mesmo campo. */
  mirror: RefObject<HTMLDivElement | null>
  value: string
  disabled: boolean
}) {
  const { t } = useTranslation()
  const analysis = useMemo(() => analyze(value), [value])
  const bar = useRef<HTMLDivElement>(null)
  const menu = useContextMenu()
  const [selection, setSelection] = useState({ start: 0, end: 0 })
  const [focused, setFocused] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [inBar, setInBar] = useState(false)
  // Esc esconde a barra até a seleção mudar; Alt+F10 mostra mesmo sem seleção (para chegar nela pelo teclado).
  const [dismissed, setDismissed] = useState<string | null>(null)
  const [summoned, setSummoned] = useState(false)
  const selected = selection.start !== selection.end
  const key = `${selection.start}:${selection.end}`
  const visible =
    !disabled &&
    !menu.open &&
    (focused || inBar) &&
    (summoned || (selected && !dragging && dismissed !== key))

  const read = () => {
    const node = field.current
    if (!node) {
      return
    }
    setSelection((current) =>
      current.start === node.selectionStart && current.end === node.selectionEnd
        ? current
        : { start: node.selectionStart, end: node.selectionEnd }
    )
  }
  const readRef = useRef(read)
  useEffect(() => {
    readRef.current = read
  })
  // A seleção muda também pelo teclado e pelo próprio navegador; o selectionchange chega ao documento.
  useEffect(() => {
    const listener = () => {
      if (document.activeElement === field.current) {
        readRef.current()
      }
    }
    document.addEventListener("selectionchange", listener)
    return () => document.removeEventListener("selectionchange", listener)
  }, [field])
  // Soltar o botão fora do campo também encerra a seleção com o mouse.
  useEffect(() => {
    if (!dragging) {
      return
    }
    const stop = () => {
      setDragging(false)
      readRef.current()
    }
    window.addEventListener("pointerup", stop)
    window.addEventListener("pointercancel", stop)
    return () => {
      window.removeEventListener("pointerup", stop)
      window.removeEventListener("pointercancel", stop)
    }
  }, [dragging])

  // O espelho rola junto com o campo.
  useLayoutEffect(() => syncScroll(field.current, mirror.current))

  // A barra fica sobre a primeira linha selecionada, dentro da tela; no toque, mais acima, para o menu do sistema
  // (copiar, colar...) caber entre ela e o texto.
  useLayoutEffect(() => {
    const node = bar.current
    if (!visible || !node || !mirror.current || !field.current) {
      return
    }
    const place = () => {
      const area = field.current!.getBoundingClientRect()
      const box = selectionBox(mirror.current!, selection.start, selection.end)
      const width = node.offsetWidth
      const height = node.offsetHeight
      const center = box.width > 0 ? box.left + box.width / 2 : box.left
      const anchorTop = Math.max(box.top, area.top)
      const gap = window.matchMedia("(pointer: coarse)").matches ? 64 : 8
      const left = Math.min(
        Math.max(center - width / 2, EDGE),
        window.innerWidth - width - EDGE
      )
      let top = anchorTop - height - gap
      if (top < EDGE) {
        top = Math.min(box.bottom, area.bottom) + gap
      }
      node.style.left = `${Math.round(left)}px`
      node.style.top = `${Math.round(top)}px`
      node.style.visibility = "visible"
    }
    place()
    const textarea = field.current
    window.addEventListener("resize", place)
    window.visualViewport?.addEventListener("resize", place)
    textarea.addEventListener("scroll", place)
    return () => {
      window.removeEventListener("resize", place)
      window.visualViewport?.removeEventListener("resize", place)
      textarea.removeEventListener("scroll", place)
    }
  }, [visible, selection, value, field, mirror])

  const apply = (format: Format) => {
    const node = field.current
    if (!node || disabled) {
      return
    }
    const edit = applyFormat(
      analyze(node.value),
      node.selectionStart,
      node.selectionEnd,
      format
    )
    if (edit) {
      replaceText(node, edit)
      read()
    }
  }

  const backToField = () => {
    setSummoned(false)
    field.current?.focus()
  }

  const active = activeFormats(analysis, selection.start, selection.end)
  const allowed = (format: Format) =>
    canFormat(analysis, selection.start, selection.end, format)

  const toolbar = visible
    ? createPortal(
        <div
          ref={bar}
          role="toolbar"
          aria-label={t("format.toolbar")}
          data-format-toolbar=""
          data-scroll-x=""
          // Fora da tela até ser posicionada (antes de pintar): nada pisca no canto.
          className="invisible fixed top-0 left-0 z-50 flex max-w-[calc(100vw-1.5rem)] scroll-fade-x animate-in items-center gap-1 overflow-x-auto rounded-full bg-popover p-1 text-popover-foreground shadow-md ring-1 ring-foreground/10 duration-(--duration-fast) ease-(--ease-enter) fade-in-0 zoom-in-95"
          // O campo continua com o foco e a seleção: o botão não toma o foco ao ser tocado.
          onPointerDown={(event) => event.preventDefault()}
          onMouseDown={(event) => event.preventDefault()}
          onFocus={() => setInBar(true)}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) {
              setInBar(false)
              setSummoned(false)
            }
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.preventDefault()
              event.stopPropagation()
              backToField()
            }
          }}
        >
          {[INLINE_FORMATS, BLOCK_FORMATS].map((group, index) => (
            <FormatGroup
              key={index}
              formats={group}
              active={active}
              allowed={allowed}
              onFormat={apply}
              separated={index > 0}
            />
          ))}
        </div>,
        document.body
      )
    : null

  const onContextMenu = (event: MouseEvent<HTMLTextAreaElement>) => {
    // Shift + botão direito: o menu do navegador, com a correção ortográfica.
    if (event.shiftKey || disabled) {
      return
    }
    const area = event.currentTarget.getBoundingClientRect()
    const inside =
      event.clientX >= area.left &&
      event.clientX <= area.right &&
      event.clientY >= area.top &&
      event.clientY <= area.bottom &&
      (event.clientX !== 0 || event.clientY !== 0)
    if (inside) {
      menu.onContextMenu(event)
      return
    }
    // Pela tecla de menu ou Shift+F10: abre onde está o cursor do texto.
    if (!window.matchMedia("(pointer: fine)").matches || !mirror.current) {
      return
    }
    event.preventDefault()
    const node = event.currentTarget
    const box = selectionBox(
      mirror.current,
      node.selectionStart,
      node.selectionEnd
    )
    menu.openAt(box.left, box.bottom)
  }

  const clipboard = async (action: "cut" | "copy" | "paste" | "selectAll") => {
    const node = field.current
    if (!node) {
      return
    }
    node.focus()
    const text = node.value.slice(node.selectionStart, node.selectionEnd)
    if (action === "selectAll") {
      node.select()
      read()
      return
    }
    if (action === "paste") {
      try {
        const pasted = await navigator.clipboard.readText()
        if (pasted) {
          replaceText(node, {
            from: node.selectionStart,
            to: node.selectionEnd,
            insert: pasted,
            selection: [
              node.selectionStart + pasted.length,
              node.selectionStart + pasted.length,
            ],
          })
        }
      } catch {
        toast.add({ title: t("format.pasteBlocked", { keys: `${MOD}+V` }) })
      }
      return
    }
    let done: boolean
    try {
      done = document.execCommand(action)
    } catch {
      done = false
    }
    if (!done) {
      try {
        await navigator.clipboard.writeText(text)
      } catch {
        toast.add({ title: t("common.genericError"), type: "error" })
        return
      }
      if (action === "cut") {
        replaceText(node, {
          from: node.selectionStart,
          to: node.selectionEnd,
          insert: "",
          selection: [node.selectionStart, node.selectionStart],
        })
      }
    }
    read()
  }

  const contextMenu = (
    <DropdownMenu open={menu.open} onOpenChange={menu.onOpenChange}>
      <DropdownMenuContent
        anchor={menu.anchor}
        align="start"
        finalFocus={field}
      >
        <DropdownMenuGroup>
          <DropdownMenuLabel>{t("format.toolbar")}</DropdownMenuLabel>
          {INLINE_FORMATS.map((format) => (
            <FormatMenuItem
              key={format}
              format={format}
              checked={active.has(format)}
              disabled={!allowed(format)}
              onFormat={apply}
            />
          ))}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          {BLOCK_FORMATS.map((format) => (
            <FormatMenuItem
              key={format}
              format={format}
              checked={active.has(format)}
              disabled={!allowed(format)}
              onFormat={apply}
            />
          ))}
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <ClipboardItem
            icon={ScissorsIcon}
            label={t("format.cut")}
            keys={[MOD, "X"]}
            disabled={!selected}
            onSelect={() => void clipboard("cut")}
          />
          <ClipboardItem
            icon={CopyIcon}
            label={t("common.copy")}
            keys={[MOD, "C"]}
            disabled={!selected}
            onSelect={() => void clipboard("copy")}
          />
          <ClipboardItem
            icon={ClipboardPasteIcon}
            label={t("format.paste")}
            keys={[MOD, "V"]}
            onSelect={() => void clipboard("paste")}
          />
          <ClipboardItem
            icon={TextSelectIcon}
            label={t("format.selectAll")}
            keys={[MOD, "A"]}
            disabled={value === ""}
            onSelect={() => void clipboard("selectAll")}
          />
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuLabel className="max-w-64 font-normal">
            {t("format.browserMenu")}
          </DropdownMenuLabel>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )

  /** Trata a tecla antes do campo; devolve true quando ela era da formatação. */
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    const format = formatShortcut({
      ctrlKey: event.ctrlKey,
      metaKey: event.metaKey,
      altKey: event.altKey,
      shiftKey: event.shiftKey,
      key: event.key,
      code: event.code,
      isComposing: event.nativeEvent.isComposing,
    })
    if (format) {
      event.preventDefault()
      apply(format)
      return true
    }
    if (event.key === "F10" && event.altKey && !disabled) {
      event.preventDefault()
      setSummoned(true)
      window.requestAnimationFrame(() =>
        bar.current
          ?.querySelector<HTMLElement>("button:not([disabled])")
          ?.focus()
      )
      return true
    }
    if (event.key === "Escape" && visible) {
      event.preventDefault()
      setDismissed(key)
      setSummoned(false)
      return true
    }
    return false
  }

  const fieldProps = {
    onSelect: read,
    onKeyUp: read,
    onFocus: () => {
      setFocused(true)
      read()
    },
    onBlur: () => setFocused(false),
    onPointerDown: (event: { button: number }) => {
      if (event.button === 0) {
        setDragging(true)
      }
    },
    onScroll: () => syncScroll(field.current, mirror.current),
    onContextMenu,
  }

  return {
    analysis,
    fieldProps,
    onKeyDown,
    ui: (
      <>
        {toolbar}
        {contextMenu}
      </>
    ) as ReactNode,
  }
}

function FormatGroup({
  formats,
  active,
  allowed,
  onFormat,
  separated,
}: {
  formats: Format[]
  active: Set<Format>
  allowed: (format: Format) => boolean
  onFormat: (format: Format) => void
  separated: boolean
}) {
  const { t } = useTranslation()
  return (
    <>
      {separated ? (
        <Separator
          orientation="vertical"
          className="mx-0.5 h-5 shrink-0 data-vertical:self-center"
        />
      ) : null}
      <ToggleGroup
        multiple
        spacing={0}
        value={formats.filter((format) => active.has(format))}
        aria-label={t(separated ? "format.blocks" : "format.inline")}
        className="shrink-0"
      >
        {formats.map((format) => {
          const Icon = ICONS[format]
          const keys = FORMAT_KEYS[format]
          const label = t(`format.${format}`)
          return (
            <Tooltip key={format}>
              <TooltipTrigger
                render={
                  <ToggleGroupItem
                    value={format}
                    aria-label={label}
                    disabled={!allowed(format)}
                    className="rounded-full"
                    onPressedChange={() => onFormat(format)}
                  />
                }
              >
                <Icon />
              </TooltipTrigger>
              <TooltipContent>
                {keys ? `${label} (${keys.join("+")})` : label}
              </TooltipContent>
            </Tooltip>
          )
        })}
      </ToggleGroup>
    </>
  )
}

function FormatMenuItem({
  format,
  checked,
  disabled,
  onFormat,
}: {
  format: Format
  checked: boolean
  disabled: boolean
  onFormat: (format: Format) => void
}) {
  const { t } = useTranslation()
  const Icon = ICONS[format]
  const keys = FORMAT_KEYS[format]
  return (
    <DropdownMenuCheckboxItem
      checked={checked}
      disabled={disabled}
      closeOnClick
      onCheckedChange={() => onFormat(format)}
    >
      <Icon />
      {t(`format.${format}`)}
      {keys ? (
        <DropdownMenuShortcut>{keys.join("+")}</DropdownMenuShortcut>
      ) : null}
    </DropdownMenuCheckboxItem>
  )
}

function ClipboardItem({
  icon: Icon,
  label,
  keys,
  disabled = false,
  onSelect,
}: {
  icon: LucideIcon
  label: string
  keys: string[]
  disabled?: boolean
  onSelect: () => void
}) {
  return (
    <DropdownMenuItem disabled={disabled} onClick={onSelect}>
      <Icon />
      {label}
      <DropdownMenuShortcut>{keys.join("+")}</DropdownMenuShortcut>
    </DropdownMenuItem>
  )
}

export type { Analysis }
