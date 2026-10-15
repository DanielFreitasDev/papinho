import { useEffect, useRef, useState, type PointerEvent } from "react"
import { useTranslation } from "react-i18next"

import { Field, FieldLabel } from "@/components/ui/field"
import { Slider } from "@/components/ui/slider"

const VIEW = 256
const OUTPUT = 512

/**
 * Recorte quadrado da foto de perfil (ID-02): arrastar para posicionar, controle de zoom e teclas de seta.
 * O resultado é desenhado num canvas e exportado em JPEG: o navegador aplica a orientação e nenhum
 * metadado (EXIF, GPS) do original segue para o servidor, que ainda recodifica por garantia.
 */
export function PhotoCropper({
  file,
  onChange,
}: {
  file: File
  onChange: (blob: Blob | null) => void
}) {
  const { t } = useTranslation()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [image, setImage] = useState<ImageBitmap | null>(null)
  const [zoom, setZoom] = useState(1)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const drag = useRef<{ x: number; y: number } | null>(null)

  useEffect(() => {
    let active = true
    createImageBitmap(file, { imageOrientation: "from-image" })
      .then((bitmap) => {
        if (active) {
          setImage(bitmap)
          setZoom(1)
          setOffset({ x: 0, y: 0 })
        }
      })
      .catch(() => onChange(null))
    return () => {
      active = false
    }
  }, [file, onChange])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !image) {
      return
    }
    const context = canvas.getContext("2d")
    if (!context) {
      return
    }
    const base = Math.max(OUTPUT / image.width, OUTPUT / image.height)
    const scale = base * zoom
    const width = image.width * scale
    const height = image.height * scale
    const maxX = Math.max(0, (width - OUTPUT) / 2)
    const maxY = Math.max(0, (height - OUTPUT) / 2)
    const x = Math.min(maxX, Math.max(-maxX, offset.x))
    const y = Math.min(maxY, Math.max(-maxY, offset.y))
    context.fillStyle = "#fff"
    context.fillRect(0, 0, OUTPUT, OUTPUT)
    context.drawImage(
      image,
      (OUTPUT - width) / 2 + x,
      (OUTPUT - height) / 2 + y,
      width,
      height
    )
    canvas.toBlob((blob) => onChange(blob), "image/jpeg", 0.9)
  }, [image, zoom, offset, onChange])

  function move(dx: number, dy: number) {
    setOffset((current) => ({ x: current.x + dx, y: current.y + dy }))
  }

  function onPointerDown(event: PointerEvent<HTMLCanvasElement>) {
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { x: event.clientX, y: event.clientY }
  }

  function onPointerMove(event: PointerEvent<HTMLCanvasElement>) {
    if (!drag.current) {
      return
    }
    const factor = OUTPUT / VIEW
    move(
      (event.clientX - drag.current.x) * factor,
      (event.clientY - drag.current.y) * factor
    )
    drag.current = { x: event.clientX, y: event.clientY }
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <canvas
        ref={canvasRef}
        width={OUTPUT}
        height={OUTPUT}
        tabIndex={0}
        role="img"
        aria-label={t("profile.cropDescription")}
        className="size-64 max-w-full cursor-grab touch-none rounded-full bg-muted outline-offset-2 active:cursor-grabbing"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={() => (drag.current = null)}
        onPointerCancel={() => (drag.current = null)}
        onKeyDown={(event) => {
          const step = 16
          const moves: Record<string, [number, number]> = {
            ArrowLeft: [step, 0],
            ArrowRight: [-step, 0],
            ArrowUp: [0, step],
            ArrowDown: [0, -step],
          }
          const delta = moves[event.key]
          if (delta) {
            event.preventDefault()
            move(delta[0], delta[1])
          }
        }}
      />
      <Field className="w-full max-w-64">
        <FieldLabel id="photo-zoom-label">{t("profile.zoom")}</FieldLabel>
        <Slider
          aria-labelledby="photo-zoom-label"
          min={1}
          max={3}
          step={0.01}
          value={[zoom]}
          onValueChange={(value) =>
            setZoom(Array.isArray(value) ? value[0] : value)
          }
          className="py-4"
        />
      </Field>
    </div>
  )
}
