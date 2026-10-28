import { useEffect, useRef, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { cn } from "cn"
import {
  BellOffIcon,
  CalendarClockIcon,
  CameraIcon,
  ChartBarIcon,
  ChevronUpIcon,
  CheckIcon,
  FileTextIcon,
  HeadphonesIcon,
  ImagesIcon,
  PaperclipIcon,
  StickerIcon,
  PencilIcon,
  SendHorizontalIcon,
  SmileIcon,
  XIcon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupTextarea,
} from "@/components/ui/input-group"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { EmojiPicker } from "@/features/emoji"
import {
  GifPanel,
  StickerManager,
  StickerPanel,
  stickerQueries,
  type GifResult,
} from "@/features/sticker"
import { enterSends, replaceEmoticon, useChatPrefs } from "@/lib/chat-prefs"
import { realtime } from "@/lib/realtime"
import { useShortcut } from "@/lib/shortcuts"
import { playSendSound } from "@/lib/sounds"
import { saveDraft } from "../api"
import { markSendOrigin } from "../send-flight"
import { plainText } from "../format/parse"
import {
  FormattedMirror,
  replacingText,
  useComposerFormat,
} from "./composer-format"
import { VoiceRecorder } from "./voice-recorder"
import type { ReplyPreview } from "../types"

/** Reenvia "digitando" no máximo a cada 3 s; o servidor e o outro lado expiram em ~6 s. */
const TYPING_INTERVAL = 3_000
const MAX_LENGTH = 65_536

/**
 * Grupo de ícones do campo: preso à linha de baixo e com a altura de uma linha (os 48 px da pílula menos a borda).
 * Com várias linhas de texto, os ícones continuam embaixo, como no WhatsApp.
 */
const COMPOSER_ADDON = "h-[calc(3rem-2px)] self-end gap-1 py-0"
/** Ícone redondo dentro da pílula: 40 px no mouse, 44 px no toque (AR-02, pelo próprio Button). */
const COMPOSER_ICON = "size-10"
/**
 * Tipografia e respiro do texto do campo, iguais no textarea e no espelho que desenha a formatação por baixo dele:
 * uma linha de 24 px com 11 px em cima e embaixo, no meio dos 46 px da pílula, na altura dos ícones.
 */
const FIELD_TEXT = "px-2.5 py-[11px] text-base leading-6 md:text-sm"

/** Itens do menu de anexos, na ordem do WhatsApp; a cor do círculo de cada um vem do index.css (data-attach-tone). */
const ATTACH_KINDS = [
  {
    key: "document",
    icon: FileTextIcon,
    tone: "violet",
    accept: undefined,
    capture: false,
  },
  {
    key: "media",
    icon: ImagesIcon,
    tone: "blue",
    accept: "image/*,video/*",
    capture: false,
  },
  {
    key: "camera",
    icon: CameraIcon,
    tone: "pink",
    accept: "image/*,video/*",
    capture: true,
  },
  {
    key: "audio",
    icon: HeadphonesIcon,
    tone: "orange",
    accept: "audio/*",
    capture: false,
  },
  {
    key: "poll",
    icon: ChartBarIcon,
    tone: "amber",
    accept: undefined,
    capture: false,
  },
  {
    key: "sticker",
    icon: StickerIcon,
    tone: "teal",
    accept: undefined,
    capture: false,
  },
] as const

/**
 * Campo de mensagem fora da área rolável (UI.md §8.12), numa pílula como a do WhatsApp: anexar e emojis à esquerda, o
 * microfone (ou o enviar, quando há texto) dentro dela, à direita. Enter envia no computador (Configurações > Conversas muda), Shift+Enter quebra linha;
 * no celular o Enter quebra linha e o botão envia. Avisa "digitando…" (MS-03).
 */

export function Composer({
  conversationId,
  replyTo,
  replyName,
  onCancelReply,
  onSend,
  onFiles,
  onVoice,
  disabled,
  editing = null,
  onEdit,
  onCancelEdit,
  onEditLast,
  onPoll,
  draft = null,
  onSchedule,
  onSticker,
  onGif,
}: {
  conversationId: string
  replyTo: ReplyPreview | null
  replyName: string
  onCancelReply: () => void
  onSend: (body: string, options?: { silent?: boolean }) => void
  /** Arquivos escolhidos pelo botão ou colados (Ctrl+V) no campo. */
  onFiles: (files: File[]) => void
  /** Mensagem de voz gravada (AU-01). */
  onVoice: (file: File) => void
  disabled: boolean
  /** Mensagem em edição (MS-05): o campo mostra o texto dela e o envio salva a edição. */
  editing?: { id: string; body: string } | null
  onEdit?: (id: string, body: string) => void
  onCancelEdit?: () => void
  /** ↑ com o campo vazio edita a minha última mensagem (MS-13). */
  onEditLast?: () => void
  /** Abre o diálogo de nova enquete (MS-09). */
  onPoll?: () => void
  /** Rascunho salvo no servidor (MS-11): o campo começa com ele. */
  draft?: string | null
  /** Agendar o texto atual (MS-11). */
  onSchedule?: (body: string) => void
  /** Enviar figurinha ou GIF (MS-10). */
  onSticker?: (stickerId: string) => void
  onGif?: (gif: GifResult) => void
}) {
  const { t } = useTranslation()
  const chatPrefs = useChatPrefs()
  const [value, setValue] = useState(draft ?? "")
  // Rascunho sincronizado (MS-11): salvo no servidor um instante depois de parar de digitar.
  const savedDraft = useRef(draft ?? "")
  useEffect(() => {
    if (editing || value === savedDraft.current) {
      return
    }
    const timer = window.setTimeout(() => {
      savedDraft.current = value
      void saveDraft(conversationId, value.trim() === "" ? "" : value).catch(
        () => {
          savedDraft.current = ""
        }
      )
    }, 800)
    return () => window.clearTimeout(timer)
  }, [conversationId, editing, value])
  const textarea = useRef<HTMLTextAreaElement>(null)
  // Negrito, itálico e companhia como no WhatsApp: barra na seleção, botão direito, atalhos e o texto já formatado.
  const mirror = useRef<HTMLDivElement>(null)
  const format = useComposerFormat({ field: textarea, mirror, value, disabled })
  const fileInput = useRef<HTMLInputElement>(null)
  const [recording, setRecording] = useState(false)
  const showSend = value.trim() !== "" || Boolean(editing)
  // A troca entre o microfone e o enviar e a volta do campo depois da gravação animam só depois de uma mudança: na
  // primeira tela nada pula.
  const [previous, setPrevious] = useState({ showSend, recording })
  const [swap, setSwap] = useState(false)
  const [returning, setReturning] = useState(false)
  if (previous.showSend !== showSend || previous.recording !== recording) {
    setPrevious({ showSend, recording })
    setSwap(true)
    if (previous.recording !== recording) {
      setReturning(!recording)
    }
  }
  const [emojiOpen, setEmojiOpen] = useState(false)
  // Ctrl+Alt+E abre o painel de emojis, como no WhatsApp.
  useShortcut("emoji", () => {
    if (!disabled && !recording) {
      setEmojiOpen(true)
    }
  })
  const [managingStickers, setManagingStickers] = useState(false)
  const gifStatus = useQuery({
    ...stickerQueries.gifStatus(),
    enabled: emojiOpen && onGif !== undefined,
  })
  const lastTyping = useRef(0)

  useEffect(() => {
    if (replyTo) {
      textarea.current?.focus()
    }
  }, [replyTo])

  // Ao começar uma edição o campo recebe o texto dela (ajuste de estado durante a renderização).
  const editingId = editing?.id ?? null
  const [loadedEdit, setLoadedEdit] = useState<string | null>(null)
  if (editingId !== loadedEdit) {
    setLoadedEdit(editingId)
    if (editing) {
      setValue(editing.body)
    }
  }
  useEffect(() => {
    if (editingId !== null) {
      const field = textarea.current
      field?.focus()
      window.requestAnimationFrame(() =>
        field?.setSelectionRange(field.value.length, field.value.length)
      )
    }
  }, [editingId])

  useEffect(() => {
    return () => {
      if (lastTyping.current > 0) {
        realtime.activity(conversationId, "")
      }
    }
  }, [conversationId])

  function typing(next: string) {
    setValue(next)
    const now = Date.now()
    if (next.trim() === "") {
      if (lastTyping.current > 0) {
        realtime.activity(conversationId, "")
        lastTyping.current = 0
      }
    } else if (now - lastTyping.current > TYPING_INTERVAL) {
      realtime.activity(conversationId, "typing")
      lastTyping.current = now
    }
  }

  /** Põe o emoji onde está o cursor (MS-10). */
  function insertEmoji(emoji: string) {
    const field = textarea.current
    const start = field?.selectionStart ?? value.length
    const end = field?.selectionEnd ?? value.length
    typing(value.slice(0, start) + emoji + value.slice(end))
    setEmojiOpen(false)
    window.requestAnimationFrame(() => {
      field?.focus()
      field?.setSelectionRange(start + emoji.length, start + emoji.length)
    })
  }

  /** Abre o seletor de arquivos com o filtro do tipo escolhido no menu de anexos. */
  function pickFiles(accept: string | undefined, capture: boolean) {
    const input = fileInput.current
    if (!input) {
      return
    }
    input.accept = accept ?? ""
    if (capture) {
      input.setAttribute("capture", "environment")
    } else {
      input.removeAttribute("capture")
    }
    input.click()
  }

  function cancelEdit() {
    setValue("")
    onCancelEdit?.()
  }

  function submit(options?: { silent?: boolean }) {
    const body = value.trim()
    if (editing) {
      if (body !== "" && !disabled) {
        onEdit?.(editing.id, body)
        setValue("")
      }
      return
    }
    if (body === "" || disabled) {
      return
    }
    // A bolha nova voa de onde o texto está agora (send-flight.ts).
    if (textarea.current) {
      markSendOrigin(textarea.current)
    }
    onSend(body, options)
    if (chatPrefs.sendSound) {
      playSendSound()
    }
    // O servidor apaga o rascunho ao enviar.
    savedDraft.current = ""
    setValue("")
    lastTyping.current = 0
    textarea.current?.focus()
  }

  return (
    <form
      noValidate
      className="flex flex-col gap-2 px-(--spacing-gutter) pt-1 pb-[max(0.5rem,env(safe-area-inset-bottom))]"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      {editing ? (
        <div className="flex animate-in items-start gap-2 rounded-md border-l-4 border-primary bg-muted px-2 py-1 text-xs duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-bottom-2">
          <PencilIcon aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          <div className="min-w-0 flex-1">
            <p className="font-medium">{t("chat.editing")}</p>
            <p className="line-clamp-2 text-muted-foreground">
              {plainText(editing.body)}
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("chat.cancelEdit")}
            onClick={cancelEdit}
          >
            <XIcon />
          </Button>
        </div>
      ) : null}
      {replyTo && !editing ? (
        <div className="flex animate-in items-start gap-2 rounded-md border-l-4 border-primary bg-muted px-2 py-1 text-xs duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-bottom-2">
          <div className="min-w-0 flex-1">
            <p className="font-medium">
              {t("chat.replyingTo", { name: replyName })}
            </p>
            <p className="line-clamp-2 text-muted-foreground">
              {plainText(replyTo.body)}
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t("chat.cancelReply")}
            onClick={onCancelReply}
          >
            <XIcon />
          </Button>
        </div>
      ) : null}
      <input
        ref={fileInput}
        type="file"
        multiple
        hidden
        tabIndex={-1}
        aria-hidden="true"
        onChange={(event) => {
          const chosen = [...(event.target.files ?? [])]
          event.target.value = ""
          if (chosen.length > 0) {
            onFiles(chosen)
          }
        }}
      />
      {/* A pílula fica montada também durante a gravação: o gravador mora nela (à direita) e ocupa a largura toda. */}
      <InputGroup
        data-composer=""
        data-recording={recording ? "" : undefined}
        className="h-auto min-h-12 rounded-3xl bg-card shadow-xs transition-shadow dark:bg-card"
      >
        {recording ? null : (
          <InputGroupAddon
            align="inline-start"
            className={cn(
              COMPOSER_ADDON,
              returning &&
                "animate-in duration-(--duration-base) ease-(--ease-enter) fade-in slide-in-from-left-4"
            )}
          >
            {/* Menu de anexos como o do WhatsApp: cada tipo com o próprio ícone colorido e o filtro certo no seletor
                de arquivos (a câmera abre direto no celular). */}
            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <Button
                    type="button"
                    variant="ball"
                    size="icon"
                    className={COMPOSER_ICON}
                    aria-label={t("media.attachMenu")}
                    disabled={disabled}
                  />
                }
              >
                <PaperclipIcon className="transition-transform duration-(--duration-base) ease-(--ease-spring) group-data-popup-open/button:-rotate-45" />
              </DropdownMenuTrigger>
              <DropdownMenuContent
                side="top"
                align="start"
                className="min-w-56"
              >
                {ATTACH_KINDS.map(
                  ({ key, icon: Icon, tone, accept, capture }) =>
                    key === "poll" && !onPoll ? null : (
                      <DropdownMenuItem
                        key={key}
                        className="gap-3 py-2"
                        onClick={() => {
                          if (key === "poll") {
                            onPoll?.()
                          } else if (key === "sticker") {
                            setManagingStickers(true)
                          } else {
                            pickFiles(accept, capture)
                          }
                        }}
                      >
                        <span
                          aria-hidden="true"
                          data-attach-tone={tone}
                          className="flex size-8 items-center justify-center rounded-full text-white"
                        >
                          <Icon className="size-4" />
                        </span>
                        {t(`media.attachKind.${key}`)}
                      </DropdownMenuItem>
                    )
                )}
              </DropdownMenuContent>
            </DropdownMenu>
            <Popover open={emojiOpen} onOpenChange={setEmojiOpen}>
              <PopoverTrigger
                render={
                  <Button
                    type="button"
                    variant="ball"
                    size="icon"
                    className={COMPOSER_ICON}
                    aria-label={t("emoji.open")}
                    disabled={disabled}
                  />
                }
              >
                <SmileIcon />
              </PopoverTrigger>
              <PopoverContent side="top" align="start" className="w-auto">
                <Tabs defaultValue="emoji">
                  <TabsList>
                    <TabsTrigger value="emoji">{t("emoji.tab")}</TabsTrigger>
                    {onSticker ? (
                      <TabsTrigger value="stickers">
                        {t("sticker.tab")}
                      </TabsTrigger>
                    ) : null}
                    {onGif && gifStatus.data?.enabled ? (
                      <TabsTrigger value="gifs">{t("gif.tab")}</TabsTrigger>
                    ) : null}
                  </TabsList>
                  <TabsContent value="emoji">
                    <EmojiPicker onSelect={insertEmoji} />
                  </TabsContent>
                  {onSticker ? (
                    <TabsContent value="stickers">
                      <StickerPanel
                        onSelect={(sticker) => {
                          setEmojiOpen(false)
                          onSticker(sticker.id)
                        }}
                        onManage={() => {
                          setEmojiOpen(false)
                          setManagingStickers(true)
                        }}
                      />
                    </TabsContent>
                  ) : null}
                  {onGif && gifStatus.data?.enabled ? (
                    <TabsContent value="gifs">
                      <GifPanel
                        onSelect={(gif) => {
                          setEmojiOpen(false)
                          onGif(gif)
                        }}
                      />
                    </TabsContent>
                  ) : null}
                </Tabs>
              </PopoverContent>
            </Popover>
          </InputGroupAddon>
        )}
        {recording ? null : (
          // O textarea fica com o texto transparente por cima do espelho, que mostra a formatação; cursor, seleção,
          // correção ortográfica e teclado continuam os do textarea.
          <div
            data-slot="composer-field"
            className="relative flex min-w-0 flex-1"
          >
            <FormattedMirror
              ref={mirror}
              segments={format.analysis.segments}
              text={value}
              className={cn(
                FIELD_TEXT,
                returning &&
                  "animate-in duration-(--duration-base) ease-(--ease-enter) fade-in"
              )}
            />
            <InputGroupTextarea
              ref={textarea}
              aria-label={t("chat.composerLabel")}
              placeholder={t("chat.composerPlaceholder")}
              value={value}
              maxLength={MAX_LENGTH}
              rows={1}
              disabled={disabled}
              enterKeyHint="send"
              spellCheck={chatPrefs.spellcheck}
              {...format.fieldProps}
              className={cn(
                "relative field-sizing-content max-h-40 min-h-[calc(3rem-2px)] resize-none scrollbar-gutter-stable [-webkit-text-fill-color:transparent] placeholder:[-webkit-text-fill-color:var(--muted-foreground)] forced-colors:[-webkit-text-fill-color:CanvasText]",
                FIELD_TEXT,
                returning &&
                  "animate-in duration-(--duration-base) ease-(--ease-enter) fade-in"
              )}
              onChange={(event) => {
                const field = event.target
                const caret = field.selectionStart ?? field.value.length
                // ":) " vira "🙂 " ao digitar o espaço (Configurações > Conversas), como no WhatsApp.
                const replaced =
                  chatPrefs.emojiReplace &&
                  (event.nativeEvent as InputEvent).inputType ===
                    "insertText" &&
                  !replacingText()
                    ? replaceEmoticon(field.value, caret)
                    : null
                typing(replaced?.text ?? field.value)
                if (replaced) {
                  window.requestAnimationFrame(() =>
                    field.setSelectionRange(replaced.caret, replaced.caret)
                  )
                }
              }}
              onPaste={(event) => {
                const pasted = [...event.clipboardData.files]
                if (pasted.length > 0) {
                  event.preventDefault()
                  onFiles(pasted)
                }
              }}
              onKeyDown={(event) => {
                if (format.onKeyDown(event)) {
                  return
                }
                const coarse = window.matchMedia("(pointer: coarse)").matches
                if (
                  event.key === "Enter" &&
                  !event.shiftKey &&
                  enterSends(chatPrefs, coarse) &&
                  !event.nativeEvent.isComposing
                ) {
                  event.preventDefault()
                  submit()
                }
                if (event.key === "Escape" && editing) {
                  event.preventDefault()
                  cancelEdit()
                } else if (event.key === "Escape" && replyTo) {
                  event.preventDefault()
                  onCancelReply()
                }
                if (
                  event.key === "ArrowUp" &&
                  value === "" &&
                  !editing &&
                  onEditLast
                ) {
                  event.preventDefault()
                  onEditLast()
                }
              }}
            />
          </div>
        )}
        {/* O microfone (ou o enviar, quando há texto) fica dentro da pílula, como no WhatsApp; gravando, o gravador
            ocupa a pílula inteira. */}
        <InputGroupAddon
          align="inline-end"
          className={cn(
            COMPOSER_ADDON,
            recording &&
              "min-w-0 flex-1 cursor-default justify-stretch pl-2 text-foreground"
          )}
        >
          {showSend && !editing && onSchedule && !recording ? (
            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <Button
                    type="button"
                    variant="ball"
                    size="icon"
                    className={COMPOSER_ICON}
                    aria-label={t("chat.sendOptions")}
                    disabled={disabled}
                  />
                }
              >
                <ChevronUpIcon />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" side="top">
                <DropdownMenuItem onClick={() => submit({ silent: true })}>
                  <BellOffIcon />
                  {t("chat.sendSilently")}
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => {
                    const body = value.trim()
                    if (body !== "") {
                      onSchedule(body)
                      savedDraft.current = ""
                      setValue("")
                      void saveDraft(conversationId, "").catch(() => undefined)
                    }
                  }}
                >
                  <CalendarClockIcon />
                  {t("chat.schedule")}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
          {/* O enviar ocupa o lugar redondo do microfone e os dois chegam com o "pop" na troca. */}
          {showSend && !recording ? (
            <Button
              type="submit"
              size="icon"
              className={cn(
                COMPOSER_ICON,
                "rounded-full",
                swap && "animate-pop"
              )}
              aria-label={editing ? t("chat.saveEdit") : t("chat.send")}
              disabled={disabled || value.trim() === ""}
            >
              {editing ? <CheckIcon /> : <SendHorizontalIcon />}
            </Button>
          ) : (
            <VoiceRecorder
              conversationId={conversationId}
              disabled={disabled}
              animateIn={swap}
              onActiveChange={setRecording}
              onSend={onVoice}
            />
          )}
        </InputGroupAddon>
      </InputGroup>
      <StickerManager
        open={managingStickers}
        onOpenChange={setManagingStickers}
      />
      {format.ui}
    </form>
  )
}
