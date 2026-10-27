import { useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { ImagePlusIcon, RefreshCwIcon, Trash2Icon } from "lucide-react"

import { SwatchPicker, type SwatchOption } from "@/components/swatch-picker"
import { Bubble, BubbleContent } from "@/components/ui/bubble"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import { Slider } from "@/components/ui/slider"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/toast"
import {
  setDisplayPrefs,
  useDisplayPrefs,
  WALLPAPER_DIMS,
  WALLPAPERS,
} from "@/lib/display-prefs"
import {
  chooseWallpaperImage,
  removeWallpaperImage,
  useWallpaperImageUrl,
} from "@/lib/wallpaper-image"

type Wallpaper = (typeof WALLPAPERS)[number]

/**
 * Papel de parede da conversa (UI-02), como no WhatsApp: prévia ao vivo, a cor da conversa, cores, degradês, padrões,
 * desenhos por cima e uma imagem própria (só neste aparelho), com escurecimento.
 */
export function WallpaperPicker() {
  const { t } = useTranslation()
  const display = useDisplayPrefs()
  const imageUrl = useWallpaperImageUrl()
  const fileInput = useRef<HTMLInputElement>(null)
  const [saving, setSaving] = useState(false)
  const option = (value: Wallpaper): SwatchOption<Wallpaper> => ({
    value,
    label: t(`appearance.wallpapers.${value}`),
    swatch: `wallpaper-${value}`,
    children:
      value === "image" && !imageUrl ? (
        <ImagePlusIcon aria-hidden="true" className="size-5" />
      ) : undefined,
  })
  const colors = WALLPAPERS.filter(
    (value) => value === "none" || value.startsWith("color-")
  )
  const patterns = WALLPAPERS.filter(
    (value) =>
      value.startsWith("gradient-") ||
      ["dots", "grid", "waves", "tint"].includes(value)
  )

  async function choose(file: File) {
    setSaving(true)
    try {
      await chooseWallpaperImage(file)
    } catch {
      toast.add({ title: t("appearance.imageError"), type: "error" })
    } finally {
      setSaving(false)
    }
  }

  return (
    <Field>
      <FieldLabel id="appearance-wallpaper">
        {t("appearance.wallpaper")}
      </FieldLabel>
      {/* Prévia com as mesmas regras da conversa (classe chat-wallpaper): cor, desenhos, imagem e escurecimento. */}
      <div
        aria-hidden="true"
        className="chat-wallpaper flex h-36 flex-col justify-end gap-1.5 overflow-hidden rounded-xl border p-3 transition-[background-color] duration-(--duration-base)"
      >
        <Bubble variant="incoming" className="max-w-[75%]">
          <BubbleContent>{t("appearance.previewIncoming")}</BubbleContent>
        </Bubble>
        <Bubble variant="tinted" align="end" className="max-w-[75%]">
          <BubbleContent>{t("appearance.previewOutgoing")}</BubbleContent>
        </Bubble>
      </div>
      <SwatchPicker
        label={t("appearance.wallpaper")}
        value={display.wallpaper}
        sections={[
          {
            label: t("appearance.wallpaperColors"),
            options: colors.map(option),
          },
          {
            label: t("appearance.wallpaperPatterns"),
            options: patterns.map(option),
          },
          { label: t("appearance.wallpaperImage"), options: [option("image")] },
        ]}
        onChange={(wallpaper) => {
          if (wallpaper === "image" && !imageUrl) {
            // Sem imagem guardada: a amostra abre a escolha do arquivo (a escolha só vale depois de guardar).
            fileInput.current?.click()
            return
          }
          setDisplayPrefs({ wallpaper })
        }}
      />
      <input
        ref={fileInput}
        type="file"
        accept="image/*"
        hidden
        tabIndex={-1}
        aria-hidden="true"
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ""
          if (file) {
            void choose(file)
          }
        }}
      />
      {display.wallpaper === "image" ? (
        <div className="flex flex-col gap-3 rounded-xl border p-3">
          <Field className="max-w-sm">
            <FieldLabel id="appearance-dim">{t("appearance.dim")}</FieldLabel>
            <Slider
              aria-labelledby="appearance-dim"
              min={0}
              max={WALLPAPER_DIMS[WALLPAPER_DIMS.length - 1]}
              step={10}
              value={[display.wallpaperDim]}
              onValueChange={(value) => {
                const next = Array.isArray(value) ? value[0] : value
                setDisplayPrefs({
                  wallpaperDim: next as (typeof WALLPAPER_DIMS)[number],
                })
              }}
              className="py-3"
            />
          </Field>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={saving}
              onClick={() => fileInput.current?.click()}
            >
              {saving ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <RefreshCwIcon data-icon="inline-start" />
              )}
              {t("appearance.changeImage")}
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={() =>
                void removeWallpaperImage().catch(() =>
                  toast.add({
                    title: t("common.genericError"),
                    type: "error",
                  })
                )
              }
            >
              <Trash2Icon data-icon="inline-start" />
              {t("appearance.removeImage")}
            </Button>
          </div>
        </div>
      ) : null}
      {saving && display.wallpaper !== "image" ? (
        <p
          role="status"
          className="flex items-center gap-2 text-sm text-muted-foreground"
        >
          <Spinner />
          {t("appearance.savingImage")}
        </p>
      ) : null}
      <label className="flex min-h-12 items-center justify-between gap-3 text-sm">
        <span className="flex min-w-0 flex-col">
          {t("appearance.doodles")}
          <FieldDescription className="text-xs">
            {t("appearance.doodlesHint")}
          </FieldDescription>
        </span>
        <Switch
          checked={display.doodles}
          disabled={display.wallpaper === "image"}
          onCheckedChange={(doodles) => setDisplayPrefs({ doodles })}
        />
      </label>
    </Field>
  )
}
