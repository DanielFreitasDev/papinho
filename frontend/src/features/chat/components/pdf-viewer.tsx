import { useEffect, useRef, useState, type RefObject } from "react"
import { useTranslation } from "react-i18next"
import type { PDFDocumentProxy, RenderTask, TextLayer } from "pdfjs-dist"
import {
  ChevronDownIcon,
  ChevronUpIcon,
  DownloadIcon,
  FileWarningIcon,
  XIcon,
  ZoomInIcon,
  ZoomOutIcon,
} from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { Spinner } from "@/components/ui/spinner"
import { loadPdfjs, mediaUrl, openPdf } from "@/features/media"
import { formatDay, formatTime } from "@/lib/format"
import type { Attachment } from "../types"

/** Passos de zoom sobre a largura que cabe na tela. */
const ZOOMS = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]
/** Página na largura da tela, mas sem passar disto (um PDF A4 fica confortável no desktop). */
const MAX_FIT_WIDTH = 980
/** Respiro em volta das páginas (px). */
const GUTTER = 16

type Size = { width: number; height: number }

/** O PDF aberto para ver: o anexo, quem mandou e quando. */
export type OpenDocument = {
  attachment: Attachment
  sender: string
  sentAt: string
}

/**
 * Visualizador de PDF dentro do app, como o do WhatsApp (pedido do dono de 2026-10-28): as páginas desenhadas pelo
 * pdf.js com o texto selecionável por cima, a página atual com anterior e próxima, zoom e download, sem baixar o
 * arquivo. Só desenha as páginas perto da tela. Esc fecha; + e - mudam o zoom.
 */
export function PdfViewer({
  open,
  onClose,
}: {
  open: OpenDocument | null
  onClose: () => void
}) {
  return (
    <Dialog
      open={open !== null}
      onOpenChange={(next) => {
        if (!next) {
          onClose()
        }
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="flex h-dvh max-h-dvh w-screen max-w-none flex-col gap-0 rounded-none border-0 bg-neutral-900 p-0 text-white sm:max-w-none"
      >
        {open ? (
          <PdfDocument
            key={open.attachment.id}
            document={open}
            onClose={onClose}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  )
}

function PdfDocument({
  document: { attachment, sender, sentAt },
  onClose,
}: {
  document: OpenDocument
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null)
  const [failure, setFailure] = useState<"password" | "error" | null>(null)
  const [first, setFirst] = useState<Size | null>(null)
  const [zoom, setZoom] = useState(2)
  const [current, setCurrent] = useState(1)
  const [width, setWidth] = useState(0)
  const scroller = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    let task: Awaited<ReturnType<typeof openPdf>> | null = null
    void openPdf(mediaUrl(attachment))
      .then((loading) => {
        task = loading
        return loading.promise
      })
      .then(async (loaded) => {
        const page = await loaded.getPage(1)
        const viewport = page.getViewport({ scale: 1 })
        if (!cancelled) {
          setFirst({ width: viewport.width, height: viewport.height })
          setDoc(loaded)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailure(
            (error as { name?: string })?.name === "PasswordException"
              ? "password"
              : "error"
          )
        }
      })
    return () => {
      cancelled = true
      // Destruir a carga também fecha o documento e libera o worker.
      void task?.destroy()
    }
  }, [attachment])

  // A largura disponível decide a escala que "cabe na tela"; acompanha o redimensionamento da janela.
  useEffect(() => {
    const node = scroller.current
    if (!node) {
      return
    }
    const observer = new ResizeObserver(([entry]) =>
      setWidth(entry.contentRect.width)
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  const fit =
    first && width > 0
      ? Math.min(width - GUTTER * 2, MAX_FIT_WIDTH) / first.width
      : 1
  const scale = fit * ZOOMS[zoom]
  const pages = doc?.numPages ?? 0

  // Página atual: a que ocupa o meio da área visível.
  useEffect(() => {
    const node = scroller.current
    if (!node || pages === 0) {
      return
    }
    const update = () => {
      const middle = node.getBoundingClientRect().top + node.clientHeight / 2
      let best = 1
      let distance = Infinity
      node.querySelectorAll<HTMLElement>("[data-pdf-page]").forEach((page) => {
        const box = page.getBoundingClientRect()
        const gap = Math.abs(box.top + box.height / 2 - middle)
        if (gap < distance) {
          distance = gap
          best = Number(page.dataset.pdfPage)
        }
      })
      setCurrent(best)
    }
    update()
    node.addEventListener("scroll", update, { passive: true })
    return () => node.removeEventListener("scroll", update)
  }, [pages, scale])

  function goTo(page: number) {
    scroller.current
      ?.querySelector(`[data-pdf-page="${page}"]`)
      ?.scrollIntoView({ block: "start" })
  }

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) {
        return
      }
      if (event.key === "+" || event.key === "=") {
        setZoom((value) => Math.min(value + 1, ZOOMS.length - 1))
      } else if (event.key === "-") {
        setZoom((value) => Math.max(value - 1, 0))
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [])

  const iconButton = "text-white hover:bg-white/10 hover:text-white"
  return (
    <>
      <div className="flex items-center gap-1 border-b border-white/10 px-2 py-2 pt-[max(0.5rem,env(safe-area-inset-top))]">
        <div className="min-w-0 flex-1 px-2">
          <DialogTitle className="truncate text-sm font-medium text-white">
            {attachment.filename}
          </DialogTitle>
          <DialogDescription className="truncate text-xs text-white/70">
            {t("media.documentFrom", {
              name: sender,
              day: formatDay(sentAt),
              time: formatTime(sentAt),
            })}
          </DialogDescription>
        </div>
        <Button
          variant="ghost"
          size="icon"
          className={iconButton}
          aria-label={t("media.zoomOut")}
          disabled={zoom === 0 || !doc}
          onClick={() => setZoom((value) => Math.max(value - 1, 0))}
        >
          <ZoomOutIcon />
        </Button>
        <span
          aria-live="polite"
          className="hidden w-12 text-center text-xs text-white/80 tabular-nums sm:inline"
        >
          {t("media.zoomLevel", { percent: Math.round(ZOOMS[zoom] * 100) })}
        </span>
        <Button
          variant="ghost"
          size="icon"
          className={iconButton}
          aria-label={t("media.zoomIn")}
          disabled={zoom === ZOOMS.length - 1 || !doc}
          onClick={() =>
            setZoom((value) => Math.min(value + 1, ZOOMS.length - 1))
          }
        >
          <ZoomInIcon />
        </Button>
        <a
          href={mediaUrl(attachment, "content", true)}
          download={attachment.filename}
          aria-label={t("media.download")}
          className={buttonVariants({
            variant: "ghost",
            size: "icon",
            className: iconButton,
          })}
        >
          <DownloadIcon />
        </a>
        <Button
          variant="ghost"
          size="icon"
          className={iconButton}
          aria-label={t("common.close")}
          onClick={onClose}
        >
          <XIcon />
        </Button>
      </div>
      <div className="relative flex min-h-0 flex-1 flex-col">
        <div
          ref={scroller}
          role="region"
          aria-label={t("media.pdfPages")}
          tabIndex={0}
          className="min-h-0 flex-1 overflow-auto outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          {failure ? (
            <div
              role="alert"
              className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center"
            >
              <FileWarningIcon aria-hidden="true" className="size-10" />
              <p className="max-w-sm text-sm">
                {failure === "password"
                  ? t("media.pdfPassword")
                  : t("media.pdfError")}
              </p>
              <a
                href={mediaUrl(attachment, "content", true)}
                download={attachment.filename}
                className={buttonVariants({ variant: "secondary" })}
              >
                <DownloadIcon data-icon="inline-start" />
                {t("media.download")}
              </a>
            </div>
          ) : !doc || !first ? (
            <p
              role="status"
              className="flex h-full items-center justify-center gap-2 text-sm text-white/80"
            >
              <Spinner role="none" aria-hidden="true" aria-label={undefined} />
              {t("media.pdfLoading")}
            </p>
          ) : (
            <div className="flex w-max min-w-full flex-col items-center gap-4 p-(--spacing-gutter)">
              {Array.from({ length: pages }, (_, index) => (
                <PdfPage
                  key={index}
                  doc={doc}
                  number={index + 1}
                  total={pages}
                  size={first}
                  scale={scale}
                  root={scroller}
                />
              ))}
            </div>
          )}
        </div>
        {doc && pages > 0 ? (
          <div className="pointer-events-none absolute inset-x-0 bottom-[max(1rem,env(safe-area-inset-bottom))] flex justify-center sm:justify-end sm:px-6">
            <div className="pointer-events-auto flex items-center gap-1 rounded-full bg-black/70 p-1 text-white shadow-md ring-1 ring-white/10">
              <Button
                variant="ghost"
                size="icon"
                className={iconButton}
                aria-label={t("media.previousPage")}
                disabled={current <= 1}
                onClick={() => goTo(current - 1)}
              >
                <ChevronUpIcon />
              </Button>
              <span
                role="status"
                className="min-w-16 text-center text-sm tabular-nums"
              >
                {t("media.pageOf", { current, total: pages })}
              </span>
              <Button
                variant="ghost"
                size="icon"
                className={iconButton}
                aria-label={t("media.nextPage")}
                disabled={current >= pages}
                onClick={() => goTo(current + 1)}
              >
                <ChevronDownIcon />
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </>
  )
}

/**
 * Uma página: o espaço dela já reservado (o tamanho da primeira, até a própria carregar) e o desenho só perto da área
 * visível. Por cima do canvas, a camada de texto do pdf.js (transparente) deixa selecionar e copiar.
 */
function PdfPage({
  doc,
  number,
  total,
  size,
  scale,
  root,
}: {
  doc: PDFDocumentProxy
  number: number
  total: number
  size: Size
  scale: number
  root: RefObject<HTMLDivElement | null>
}) {
  const { t } = useTranslation()
  const box = useRef<HTMLDivElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const text = useRef<HTMLDivElement>(null)
  const [near, setNear] = useState(false)
  const [own, setOwn] = useState<Size | null>(null)
  const page = own ?? size

  useEffect(() => {
    const node = box.current
    if (!node) {
      return
    }
    const observer = new IntersectionObserver(
      ([entry]) => setNear(entry.isIntersecting),
      { root: root.current, rootMargin: "100% 0px" }
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [root])

  useEffect(() => {
    if (!near) {
      return
    }
    let cancelled = false
    let render: RenderTask | null = null
    let layer: TextLayer | null = null
    void (async () => {
      const proxy = await doc.getPage(number)
      const viewport = proxy.getViewport({ scale })
      const natural = proxy.getViewport({ scale: 1 })
      if (cancelled || !canvas.current || !text.current) {
        return
      }
      setOwn({ width: natural.width, height: natural.height })
      const ratio = window.devicePixelRatio || 1
      const node = canvas.current
      node.width = Math.floor(viewport.width * ratio)
      node.height = Math.floor(viewport.height * ratio)
      node.style.width = `${Math.floor(viewport.width)}px`
      node.style.height = `${Math.floor(viewport.height)}px`
      render = proxy.render({
        canvas: node,
        viewport,
        transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0],
      })
      await render.promise
      if (cancelled) {
        return
      }
      const { TextLayer: Layer } = await loadPdfjs()
      const container = text.current
      container.replaceChildren()
      container.style.setProperty("--total-scale-factor", String(scale))
      layer = new Layer({
        textContentSource: proxy.streamTextContent(),
        container,
        viewport,
      })
      await layer.render()
    })().catch(() => undefined)
    return () => {
      cancelled = true
      render?.cancel()
      layer?.cancel()
    }
  }, [near, doc, number, scale])

  return (
    <section
      ref={box}
      data-pdf-page={number}
      aria-label={t("media.pageOf", { current: number, total })}
      className="relative shrink-0 bg-white shadow-lg"
      style={{
        width: Math.floor(page.width * scale),
        height: Math.floor(page.height * scale),
      }}
    >
      <canvas ref={canvas} aria-hidden="true" className="block" />
      <div ref={text} className="textLayer" />
    </section>
  )
}
