import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { SendHorizontalIcon, XIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupTextarea,
} from "@/components/ui/input-group"
import { realtime } from "@/lib/realtime"
import { plainText } from "../format/parse"
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
  disabled,
}: {
  conversationId: string
  replyTo: ReplyPreview | null
  replyName: string
  onCancelReply: () => void
  onSend: (body: string) => void
  disabled: boolean
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState("")
  const textarea = useRef<HTMLTextAreaElement>(null)
  const lastTyping = useRef(0)

  useEffect(() => {
    if (replyTo) {
      textarea.current?.focus()
    }
  }, [replyTo])

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

  function submit() {
    const body = value.trim()
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
      {replyTo ? (
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
      <InputGroup>
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
            if (event.key === "Escape" && replyTo) {
              onCancelReply()
            }
          }}
        />
        <InputGroupAddon align="inline-end">
          <InputGroupButton
            type="submit"
            size="icon-sm"
            variant="default"
            aria-label={t("chat.send")}
            disabled={disabled || value.trim() === ""}
          >
            <SendHorizontalIcon />
          </InputGroupButton>
        </InputGroupAddon>
      </InputGroup>
    </form>
  )
}
