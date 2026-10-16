import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { ensureAdmin } from "./support"

/** Fase 4: paleta de comandos com Ctrl+K e a lista de atalhos com "?" (MS-13). */
test("paleta de comandos e lista de atalhos pelo teclado", async ({
  browser,
  pageErrors,
}) => {
  const { context, page } = await ensureAdmin(browser, pageErrors)

  await page.keyboard.press("Control+k")
  const palette = page.getByRole("dialog", { name: "Paleta de comandos" })
  await expect(palette).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  await page.keyboard.type("salvas")
  // O admin é compartilhado: depois de um projeto, "Mensagens salvas" também aparece entre as conversas.
  await expect(
    palette.getByRole("option", { name: "Mensagens salvas" }).first()
  ).toBeVisible()
  await page.keyboard.press("Enter")
  await expect(palette).toBeHidden()
  await expect(
    page.getByRole("heading", { name: "Mensagens salvas" })
  ).toBeVisible()

  // "?" fora de um campo de texto abre a lista; Esc fecha.
  await page.evaluate(() =>
    (document.activeElement as HTMLElement | null)?.blur()
  )
  await page.keyboard.press("Shift+?")
  const list = page.getByRole("dialog", { name: "Atalhos do teclado" })
  await expect(list).toBeVisible()
  await expect(list.getByText("Abrir a paleta de comandos")).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  await page.keyboard.press("Escape")
  await expect(list).toBeHidden()

  // Dentro do campo de mensagem "?" é só texto.
  const field = page.getByRole("textbox", { name: "Mensagem" })
  await field.click()
  // O rascunho é sincronizado entre aparelhos (MS-11): o outro projeto pode ter deixado este mesmo texto.
  await field.clear()
  await page.keyboard.type("tudo bem?")
  await expect(field).toHaveValue("tudo bem?")
  await expect(list).toBeHidden()

  expect(pageErrors).toEqual([])
  await context.close()
})
