import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

/** Fase 4: foto de visualização única entre dois navegadores (MS-08). */
test("visualização única abre uma vez, com marca d'água e sem cache", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}v`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Unica")

  const dataUrl = await gil.page.evaluate(() => {
    const canvas = document.createElement("canvas")
    canvas.width = 200
    canvas.height = 120
    const context = canvas.getContext("2d")!
    context.fillStyle = "#7c3aed"
    context.fillRect(0, 0, 200, 120)
    return canvas.toDataURL("image/png")
  })
  const chooser = gil.page.waitForEvent("filechooser")
  await gil.page.getByRole("button", { name: "Anexar arquivo" }).click()
  await (
    await chooser
  ).setFiles({
    name: "segredo.png",
    mimeType: "image/png",
    buffer: Buffer.from(dataUrl.split(",")[1], "base64"),
  })
  const dialog = gil.page.getByRole("dialog", { name: /Enviar 1 arquivo/ })
  await dialog.getByRole("switch", { name: "Visualização única" }).click()
  await expect(
    dialog.getByText(/não consegue impedir capturas de tela/)
  ).toBeVisible()
  await dialog.getByRole("button", { name: "Enviar" }).click()
  await expect(
    gil.page.getByText("Foto de visualização única", { exact: true })
  ).toBeVisible()

  // A Hana abre uma vez: o conteúdo vem com no-store e a marca d'água tem o @usuario dela.
  const open = hana.page.getByRole("button", {
    name: "Abrir Foto de visualização única",
  })
  await expect(open).toBeVisible()
  const response = hana.page.waitForResponse((r) =>
    r.url().includes("/api/v1/view-once/")
  )
  await open.click()
  const viewer = hana.page.getByRole("dialog", { name: "Visualização única" })
  await expect(
    viewer.getByRole("img", { name: /visualização única/ })
  ).toBeVisible()
  expect((await response).headers()["cache-control"]).toContain("no-store")
  await expect(
    viewer.getByTestId("view-once-watermark").locator("text")
  ).toHaveText(`@hana.${tag}`)
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.keyboard.press("Escape")
  await expect(viewer).toBeHidden()
  await expect(
    hana.page.getByText("Foto aberta", { exact: true })
  ).toBeVisible()
  await expect(open).toHaveCount(0)
  await expect(gil.page.getByText("Foto aberta por 1 pessoa")).toBeVisible()

  expect(pageErrors).toEqual([])
  await gil.context.close()
  await hana.context.close()
})
