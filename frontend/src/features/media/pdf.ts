import type { PDFDocumentLoadingTask } from "pdfjs-dist"

/**
 * pdf.js (Mozilla), carregado só quando alguém abre um PDF: o pacote e o worker pesam ~2 MB. Fontes padrão, mapas de
 * caracteres, perfis de cor e decodificadores WASM saem do próprio Papinho (`/assets/pdfjs-<versão>/`, copiados no
 * build pelo vite.config), então a CSP continua só com 'self' (o pdf.js 6 não usa mais `eval`). Sem formulários XFA
 * nem scripts do PDF.
 */
type Pdfjs = typeof import("pdfjs-dist")

let loading: Promise<Pdfjs> | null = null

export function loadPdfjs(): Promise<Pdfjs> {
  loading ??= Promise.all([
    import("pdfjs-dist"),
    import("pdfjs-dist/build/pdf.worker.min.mjs?url"),
  ]).then(([pdfjs, worker]) => {
    pdfjs.GlobalWorkerOptions.workerSrc = worker.default
    return pdfjs
  })
  return loading
}

/** Abre o PDF pela URL do anexo (mesma origem: vai com o cookie e usa Range nos arquivos grandes). */
export async function openPdf(url: string): Promise<PDFDocumentLoadingTask> {
  const pdfjs = await loadPdfjs()
  return pdfjs.getDocument({
    url,
    enableXfa: false,
    standardFontDataUrl: `${PDFJS_DATA}standard_fonts/`,
    cMapUrl: `${PDFJS_DATA}cmaps/`,
    cMapPacked: true,
    wasmUrl: `${PDFJS_DATA}wasm/`,
    iccUrl: `${PDFJS_DATA}iccs/`,
  })
}

/**
 * Capa de um PDF ainda no aparelho (antes de enviar): a primeira página como imagem e o número de páginas. Null se o
 * pdf.js não abrir o arquivo (com senha ou corrompido).
 */
export async function pdfCover(
  file: Blob,
  width: number
): Promise<{ src: string; pages: number } | null> {
  const pdfjs = await loadPdfjs()
  const task = pdfjs.getDocument({
    data: new Uint8Array(await file.arrayBuffer()),
    enableXfa: false,
    standardFontDataUrl: `${PDFJS_DATA}standard_fonts/`,
    cMapUrl: `${PDFJS_DATA}cmaps/`,
    cMapPacked: true,
    wasmUrl: `${PDFJS_DATA}wasm/`,
    iccUrl: `${PDFJS_DATA}iccs/`,
  })
  try {
    const pdf = await task.promise
    const page = await pdf.getPage(1)
    const base = page.getViewport({ scale: 1 })
    const viewport = page.getViewport({ scale: width / base.width })
    const canvas = document.createElement("canvas")
    canvas.width = Math.ceil(viewport.width)
    canvas.height = Math.ceil(viewport.height)
    await page.render({ canvas, viewport }).promise
    return { src: canvas.toDataURL("image/png"), pages: pdf.numPages }
  } catch {
    return null
  } finally {
    void task.destroy()
  }
}
