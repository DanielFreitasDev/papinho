import type { Locator, Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { attach, pngFile, RUN, twoFriends } from "./support"

/** Cola (ClipboardEvent) ou solta (DragEvent) uma imagem PNG gerada no navegador sobre o elemento. */
async function dispatchFile(
  target: Locator,
  kind: "paste" | "drop",
  name: string
) {
  await target.evaluate(
    async (element, { kind, name }) => {
      const canvas = document.createElement("canvas")
      canvas.width = 120
      canvas.height = 80
      canvas.getContext("2d")!.fillRect(0, 0, 120, 80)
      const blob = await new Promise<Blob>((resolve) =>
        canvas.toBlob((value) => resolve(value!), "image/png")
      )
      const data = new DataTransfer()
      data.items.add(new File([blob], name, { type: "image/png" }))
      const init = { bubbles: true, cancelable: true }
      if (kind === "paste") {
        element.dispatchEvent(
          new ClipboardEvent("paste", { ...init, clipboardData: data })
        )
      } else {
        for (const type of ["dragenter", "dragover", "drop"]) {
          element.dispatchEvent(
            new DragEvent(type, { ...init, dataTransfer: data })
          )
        }
      }
    },
    { kind, name }
  )
}

async function sendFromDialog(page: Page) {
  const dialog = page.getByRole("dialog", { name: "Enviar 1 arquivo" })
  await expect(dialog).toBeVisible()
  await dialog.getByRole("button", { name: "Enviar" }).click()
  await expect(dialog).toBeHidden()
}

/**
 * Fase 3: fotos com legenda e visualizador (MD-01, MD-02), documento para baixar e cancelamento de upload, entre
 * dois navegadores.
 */
test("fotos, documento e cancelamento de envio entre dois navegadores", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag)

  // Duas fotos de uma vez, com legenda na primeira.
  await attach(
    gil.page,
    [
      await pngFile(gil.page, "#16a34a", "verde.png"),
      await pngFile(gil.page, "#dc2626", "vermelha.png"),
    ],
    "Olha que *cores*"
  )
  const first = hana.page.getByRole("button", {
    name: "Abrir imagem verde.png",
  })
  await expect(first).toBeVisible()
  await expect(
    hana.page.getByRole("main").locator("strong", { hasText: "cores" })
  ).toBeVisible()
  await expect(
    hana.page.getByRole("button", { name: "Abrir imagem vermelha.png" })
  ).toBeVisible()
  await expect
    .poll(() =>
      first
        .locator("img")
        .last()
        .evaluate((img: HTMLImageElement) => img.naturalWidth)
    )
    .toBeGreaterThan(0)
  await expectNoSeriousA11yViolations(hana.page)

  // Visualizador: abre, navega para a próxima, amplia e fecha com Esc.
  await first.click()
  const viewer = hana.page.getByRole("dialog", { name: /verde\.png/ })
  await expect(viewer).toBeVisible()
  await expect(viewer.getByText("1 de 2")).toBeVisible()
  await viewer.getByRole("button", { name: "Próxima imagem" }).click()
  await expect(
    hana.page.getByRole("dialog", { name: /vermelha\.png/ })
  ).toBeVisible()
  await hana.page.getByRole("button", { name: "Ampliar" }).click()
  await expect(
    hana.page.getByRole("button", { name: "Reduzir" })
  ).toHaveAttribute("aria-pressed", "true")
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.keyboard.press("Escape")
  await expect(hana.page.getByRole("dialog", { name: /\.png/ })).toBeHidden()

  // Documento: cartão com nome e tamanho; o download preserva o nome.
  const chooser = gil.page.waitForEvent("filechooser")
  await gil.page.getByRole("button", { name: "Anexar arquivo" }).click()
  await (
    await chooser
  ).setFiles({
    name: "ata da reunião.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n% ata\n" + "linha\n".repeat(200)),
  })
  await gil.page
    .getByRole("dialog", { name: "Enviar 1 arquivo" })
    .getByRole("button", { name: "Enviar" })
    .click()
  const doc = hana.page.getByRole("link", { name: /ata da reunião\.pdf/ })
  await expect(doc).toBeVisible()
  const download = hana.page.waitForEvent("download")
  await doc.click()
  // O nome sugerido depende da versão do Chromium; o cabeçalho é o que o servidor garante.
  const response = await hana.page.request.get((await download).url())
  expect(response.headers()["content-type"]).toBe("application/octet-stream")
  expect(response.headers()["content-disposition"]).toContain(
    "filename*=UTF-8''ata%20da%20reuni%C3%A3o.pdf"
  )

  // Upload preso no meio: cancelar tira a mensagem da conversa.
  await gil.page.route("**/api/v1/uploads/*", async (route) => {
    if (route.request().method() === "PATCH") {
      await new Promise((resolve) => setTimeout(resolve, 30_000))
    }
    await route.continue().catch(() => undefined)
  })
  await attach(gil.page, [await pngFile(gil.page, "#2563eb", "azul.png")])
  const cancel = gil.page.getByRole("button", { name: "Cancelar envio" })
  await expect(cancel).toBeVisible()
  await expect(
    gil.page.getByRole("progressbar", { name: "Enviando azul.png" })
  ).toBeVisible()
  await cancel.click()
  await expect(cancel).toBeHidden()
  await expect(
    hana.page.getByRole("button", { name: "Abrir imagem azul.png" })
  ).toHaveCount(0)
  await gil.page.unroute("**/api/v1/uploads/*")

  // Ctrl+V com um arquivo no composer e arrastar e soltar na conversa abrem o mesmo diálogo de envio (MS-13).
  await dispatchFile(
    gil.page.getByRole("textbox", { name: "Mensagem" }),
    "paste",
    "colada.png"
  )
  await sendFromDialog(gil.page)
  await dispatchFile(
    gil.page.locator('[data-slot="message-scroller"]'),
    "drop",
    "arrastada.png"
  )
  await sendFromDialog(gil.page)
  for (const name of ["colada.png", "arrastada.png"]) {
    await expect(
      hana.page.getByRole("button", { name: `Abrir imagem ${name}` })
    ).toBeVisible()
  }

  // Galeria da conversa (MD-03): abas de mídia e documentos, e a foto abre no visualizador.
  await hana.page
    .getByRole("button", { name: /^Dados do contato: Gil Midia/ })
    .click()
  await hana.page.getByRole("button", { name: "Mídia, links e docs" }).click()
  const gallery = hana.page.getByRole("tabpanel", { name: "Mídia" })
  await expect(
    gallery.getByRole("button", { name: /Abrir imagem/ })
  ).toHaveCount(4)
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.getByRole("tab", { name: "Docs" }).click()
  await expect(
    hana.page
      .getByRole("tabpanel", { name: "Docs" })
      .getByRole("link", { name: /ata da reunião\.pdf/ })
  ).toBeVisible()
  await hana.page.getByRole("tab", { name: "Mídia" }).click()
  await gallery.getByRole("button", { name: "Abrir imagem verde.png" }).click()
  await expect(
    hana.page.getByRole("dialog", { name: /verde\.png/ })
  ).toBeVisible()
  await hana.page.keyboard.press("Escape")
  await hana.page.keyboard.press("Escape")

  // A prévia da lista mostra o tipo do último anexo (no celular, voltando para a lista).
  const back = hana.page.getByRole("link", { name: "Voltar" })
  if (await back.isVisible()) {
    await back.click()
  }
  await expect(
    hana.page.getByRole("complementary").getByText("Foto", { exact: true })
  ).toBeVisible()

  await Promise.all([gil.context.close(), hana.context.close()])
})
