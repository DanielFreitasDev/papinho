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
