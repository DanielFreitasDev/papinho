import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  ChartBarIcon,
  CheckIcon,
  PaperclipIcon,
  PencilIcon,
  SendHorizontalIcon,
  XIcon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupTextarea,
} from "@/components/ui/input-group"
import { realtime } from "@/lib/realtime"
import { plainText } from "../format/parse"
import { VoiceRecorder } from "./voice-recorder"
import type { ReplyPreview } from "../types"

/** Reenvia "digitando" no máximo a cada 3 s; o servidor e o outro lado expiram em ~6 s. */
const TYPING_INTERVAL = 3_000
const MAX_LENGTH = 65_536

/**
 * Campo de mensagem fora da área rolável (UI.md §8.12): Enter envia no computador, Shift+Enter quebra linha;
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
}: {
  conversationId: string
  replyTo: ReplyPreview | null
  replyName: string
  onCancelReply: () => void
  onSend: (body: string) => void
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
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState("")
  const textarea = useRef<HTMLTextAreaElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const [recording, setRecording] = useState(false)
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

  function cancelEdit() {
    setValue("")
    onCancelEdit?.()
  }

  function submit() {
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
    onSend(body)
    setValue("")
    lastTyping.current = 0
    textarea.current?.focus()
  }

  return (
    <form
      className="flex flex-col gap-2 border-t bg-background px-3 pt-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]"
      onSubmit={(event) => {
        event.preventDefault()
        submit()
      }}
    >
      {editing ? (
        <div className="flex items-start gap-2 rounded-md border-l-4 border-primary bg-muted px-2 py-1 text-xs">
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
        <div className="flex items-start gap-2 rounded-md border-l-4 border-primary bg-muted px-2 py-1 text-xs">
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
      <div className="flex items-end gap-2">
        {recording ? null : (
          <InputGroup className="flex-1">
            <InputGroupAddon align="inline-start">
              <InputGroupButton
                type="button"
                size="icon-sm"
                aria-label={t("media.attach")}
                disabled={disabled}
                onClick={() => fileInput.current?.click()}
              >
                <PaperclipIcon />
              </InputGroupButton>
              {onPoll ? (
                <InputGroupButton
                  type="button"
                  size="icon-sm"
                  aria-label={t("poll.new")}
                  disabled={disabled}
                  onClick={onPoll}
                >
                  <ChartBarIcon />
                </InputGroupButton>
              ) : null}
            </InputGroupAddon>
            <InputGroupTextarea
              ref={textarea}
              aria-label={t("chat.composerLabel")}
              placeholder={t("chat.composerPlaceholder")}
              value={value}
              maxLength={MAX_LENGTH}
              rows={1}
              disabled={disabled}
              enterKeyHint="send"
              className="field-sizing-content max-h-40 min-h-11 resize-none"
              onChange={(event) => typing(event.target.value)}
              onPaste={(event) => {
                const pasted = [...event.clipboardData.files]
                if (pasted.length > 0) {
                  event.preventDefault()
                  onFiles(pasted)
                }
              }}
              onKeyDown={(event) => {
                const coarse = window.matchMedia("(pointer: coarse)").matches
                if (
                  event.key === "Enter" &&
                  !event.shiftKey &&
                  !coarse &&
                  !event.nativeEvent.isComposing
                ) {
                  event.preventDefault()
                  submit()
                }
                if (event.key === "Escape" && editing) {
                  event.preventDefault()
                  cancelEdit()
                } else if (event.key === "Escape" && replyTo) {
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
            {value.trim() !== "" || editing ? (
              <InputGroupAddon align="inline-end">
                <InputGroupButton
                  type="submit"
                  size="icon-sm"
                  variant="default"
                  aria-label={editing ? t("chat.saveEdit") : t("chat.send")}
                  disabled={disabled || value.trim() === ""}
                >
                  {editing ? <CheckIcon /> : <SendHorizontalIcon />}
                </InputGroupButton>
              </InputGroupAddon>
            ) : null}
          </InputGroup>
        )}
        {(value.trim() === "" && !editing) || recording ? (
          <div className={recording ? "flex min-w-0 flex-1" : "flex"}>
            <VoiceRecorder
              conversationId={conversationId}
              disabled={disabled}
              onActiveChange={setRecording}
              onSend={onVoice}
            />
          </div>
        ) : null}
      </div>
    </form>
  )
}
