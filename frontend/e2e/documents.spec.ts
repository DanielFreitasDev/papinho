import type { Page } from "@playwright/test"

import {
  expect,
  expectNoSeriousA11yViolations,
  screenshot,
  test,
} from "./fixtures"
import { chooseAttachment, RUN, twoFriends } from "./support"

/** PDF de verdade, com uma página por texto (Helvetica sem embutir: usa as fontes padrão servidas pelo Papinho). */
function pdf(pages: string[]) {
  const objects: string[] = []
  const pageIds = pages.map((_, index) => 4 + index * 2)
  objects[1] = "<< /Type /Catalog /Pages 2 0 R >>"
  objects[2] = `<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(" ")}] /Count ${pages.length} >>`
  objects[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
  pages.forEach((text, index) => {
    const page = pageIds[index]
    const stream = `BT /F1 36 Tf 72 700 Td (${text}) Tj ET`
    objects[page] =
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] ` +
      `/Resources << /Font << /F1 3 0 R >> >> /Contents ${page + 1} 0 R >>`
    objects[page + 1] =
      `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`
  })
  let out = "%PDF-1.4\n"
  const offsets: number[] = []
  for (let id = 1; id < objects.length; id++) {
    offsets[id] = Buffer.byteLength(out, "latin1")
    out += `${id} 0 obj\n${objects[id]}\nendobj\n`
  }
  const xref = Buffer.byteLength(out, "latin1")
  out +=
    `xref\n0 ${objects.length}\n0000000000 65535 f \n` +
    offsets
      .slice(1)
      .map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`)
      .join("") +
    `trailer\n<< /Size ${objects.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(out, "latin1")
}

async function sendDocument(page: Page, name: string, buffer: Buffer) {
  const chooser = page.waitForEvent("filechooser")
  await chooseAttachment(page, "Documento")
  await (await chooser).setFiles({ name, mimeType: "application/pdf", buffer })
  const dialog = page.getByRole("dialog", { name: /Enviar \d arquivo/ })
  await dialog.getByRole("button", { name: "Enviar" }).click()
  await expect(dialog).toBeHidden()
}

/**
 * Pedido do dono de 2026-10-28: PDF como no WhatsApp. A bolha mostra a prévia da primeira página, o nome, as páginas,
 * o tipo e o tamanho; tocar abre o visualizador do app, sem baixar: páginas com o texto selecionável, página atual com
 * anterior e próxima, zoom, download e Esc para fechar.
 */
test("PDF abre no visualizador do app", async ({ browser, pageErrors }) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}${mobile ? "m" : "d"}b`,
    "Documento"
  )
  await sendDocument(
    gil.page,
    "roteiro-da-trilha.pdf",
    pdf(["Roteiro da trilha", "Lista do que levar"])
  )

  const page = hana.page
  const card = page.locator("[data-document]").filter({
    hasText: "roteiro-da-trilha.pdf",
  })
  await expect(card).toBeVisible()
  // O servidor leu as páginas e desenhou a primeira para a prévia no alto da bolha.
  await expect(card).toContainText(/2 páginas • PDF • \d/)
  const preview = card.locator("img").last()
  await expect(preview).toBeVisible()
  await expect
    .poll(() =>
      preview.evaluate((image: HTMLImageElement) => image.naturalWidth)
    )
    .toBe(480)
  await screenshot(page, test.info().outputPath("bolha.png"), pageErrors)
  await card
    .getByRole("button", { name: "Abrir roteiro-da-trilha.pdf" })
    .click()

  const viewer = page.getByRole("dialog", { name: "roteiro-da-trilha.pdf" })
  await expect(viewer).toBeVisible()
  await expect(viewer.getByText(/Gil Documento ·/)).toBeVisible()
  const status = viewer.getByRole("status").filter({ hasText: /Página/ })
  await expect(status).toHaveText("Página 1 de 2")
  // A primeira página foi desenhada e o texto dela está na camada que dá para selecionar.
  const first = viewer.locator('[data-pdf-page="1"]')
  await expect(first.locator(".textLayer")).toContainText("Roteiro da trilha")
  expect(
    await first
      .locator("canvas")
      .evaluate((canvas: HTMLCanvasElement) => canvas.width)
  ).toBeGreaterThan(100)
  await expectNoSeriousA11yViolations(page)
  await screenshot(page, test.info().outputPath("visualizador.png"), pageErrors)

  await viewer.getByRole("button", { name: "Próxima página" }).click()
  await expect(status).toHaveText("Página 2 de 2")
  await expect(viewer.locator('[data-pdf-page="2"] .textLayer')).toContainText(
    "Lista do que levar"
  )

  const width = async () => (await first.boundingBox())?.width ?? 0
  const before = await width()
  await viewer.getByRole("button", { name: "Ampliar" }).click()
  await expect.poll(width).toBeGreaterThan(before * 1.1)
  await expect(viewer.getByRole("link", { name: "Baixar" })).toHaveAttribute(
    "download",
    "roteiro-da-trilha.pdf"
  )

  await page.keyboard.press("Escape")
  await expect(viewer).toBeHidden()
  await Promise.all([gil.context.close(), hana.context.close()])
})
