import { expect, test, watchErrors } from "./fixtures"
import {
  createInvite,
  ensureAdmin,
  loginThroughUi,
  registerThroughUi,
  RUN,
} from "./support"

/** Fase 1: perfil com foto recortada (ID-02), troca de @usuario (ID-03) e sessões ativas (ID-06). */
test("perfil, troca de @usuario e encerramento das outras sessões", async ({
  page,
  browser,
  pageErrors,
}) => {
  const suffix = test.info().project.name.startsWith("mobile") ? "m" : "d"
  const username = `perfil.${RUN}${suffix}`
  const renamed = `novo.${RUN}${suffix}`

  const owner = await ensureAdmin(browser, pageErrors)
  const invite = await createInvite(owner.page)
  await owner.context.close()
  await page.goto(invite)
  await registerThroughUi(page, username, "Pessoa Perfil")

  // Foto: gera um PNG no próprio navegador, passa pelo recorte e salva.
  await page.goto("/settings/profile")
  const png = await page.evaluate(() => {
    const canvas = document.createElement("canvas")
    canvas.width = 300
    canvas.height = 200
    const context = canvas.getContext("2d")!
    context.fillStyle = "#3b82f6"
    context.fillRect(0, 0, 300, 200)
    return canvas.toDataURL("image/png").split(",")[1]
  })
  await page.locator('input[type="file"]').setInputFiles({
    name: "foto.png",
    mimeType: "image/png",
    buffer: Buffer.from(png, "base64"),
  })
  const crop = page.getByRole("dialog", { name: "Ajustar foto" })
  await expect(crop).toBeVisible()
  await crop.getByRole("button", { name: "Salvar" }).click()
  await expect(crop).toBeHidden()
  await expect(page.getByRole("button", { name: "Remover foto" })).toBeVisible()

  await page.getByLabel("Recado").fill("Disponível de manhã")
  await page.getByRole("button", { name: "Salvar" }).click()
  await expect(page.getByText("Alterações salvas")).toBeVisible()

  // @usuario: troca e o novo aparece; o cooldown passa a ser informado.
  await page.goto("/settings/account")
  await page.getByLabel("@usuario").fill(renamed)
  await page.getByRole("button", { name: "Salvar" }).click()
  await expect(page.getByText("@usuario alterado")).toBeVisible()
  await expect(page.getByText(/Você poderá trocar de novo em/)).toBeVisible()

  // Sessões: entra em outro contexto, encerra as outras e aquela sessão cai.
  const other = await browser.newContext()
  const otherPage = await other.newPage()
  await watchErrors(otherPage, pageErrors)
  await loginThroughUi(otherPage, renamed)
  await expect(
    otherPage.getByRole("complementary", { name: "Contatos" })
  ).toBeVisible()

  await page.goto("/settings/sessions")
  await expect(page.getByText("Esta sessão")).toBeVisible()
  await page.getByRole("button", { name: "Encerrar todas as outras" }).click()
  await expect(page.getByText("Sessão encerrada")).toBeVisible()

  await otherPage.reload()
  await expect(otherPage).toHaveURL(/\/login/)
  await other.close()
  expect(pageErrors).toEqual([])
})
