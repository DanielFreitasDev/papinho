/** Data URL de um SVG gerado pelo servidor (QR Codes), exibido em <img> para não injetar marcação no DOM. */
export function svgDataUrl(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}
