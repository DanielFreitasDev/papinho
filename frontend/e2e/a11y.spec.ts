import type { Page } from "@playwright/test"

import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
} from "./fixtures"
import { ADMIN, PASSWORD, RUN, ensureAdmin } from "./support"

/** Telas principais, com a conta admin dos testes (que tem conversas, configurações e o painel). */
const SCREENS = [
  "/",
  "/settings/profile",
  "/settings/account",
  "/settings/privacy",
  "/settings/notifications",
  "/settings/chats",
  "/settings/appearance",
  "/admin",
  "/starred",
]

async function visit(page: Page, path: string) {
  await page.goto(path)
  await expect(
    page
      .getByRole("complementary", { name: "Conversas e contatos" })
      .or(page.getByRole("main"))
      .first()
  ).toBeVisible()
  // Espera os esqueletos de carregamento (role=status) sumirem antes do axe.
  await expect(page.getByRole("status", { name: /^Carregando/ })).toHaveCount(0)
}

/** Fase 7: tema escuro sem violações sérias do axe em todas as telas principais (AR-01). */
test("tema escuro sem violações críticas ou sérias", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const { context, page } = await ensureAdmin(browser, pageErrors)
  await page.emulateMedia({ colorScheme: "dark" })
  for (const path of SCREENS) {
    await visit(page, path)
    await expect(page.locator("html")).toHaveClass(/dark/)
    await expectNoSeriousA11yViolations(page)
  }
  await context.close()
})

/** Fase 7: 320 px sem rolagem horizontal em todas as telas principais (AR-02). */
test("320 px sem rolagem horizontal", async ({ browser, pageErrors }) => {
  test.slow()
  const { context, page } = await ensureAdmin(browser, pageErrors)
  await page.setViewportSize({ width: 320, height: 640 })
  for (const path of SCREENS) {
    await visit(page, path)
    await expectNoHorizontalScroll(page)
  }
  await context.close()
})

/** Fase 7: entrar, abrir uma conversa, mandar mensagem e usar o menu só com o teclado (AR-03). */
test("fluxo completo só com o teclado", async ({ page }) => {
  test.skip(
    test.info().project.name.startsWith("mobile"),
    "teclado físico é cenário de desktop"
  )
  await page.goto("/login")
  // Login: Tab até o campo (passando pelo que vem antes), Enter envia.
  const username = page.getByLabel("@usuario")
  for (let step = 0; step < 6; step++) {
    await page.keyboard.press("Tab")
    if (
      await username.evaluate((element) => element === document.activeElement)
    ) {
      break
    }
  }
  await expect(username).toBeFocused()
  await page.keyboard.type(ADMIN)
  await page.keyboard.press("Tab")
  await expect(page.getByLabel("Senha", { exact: true })).toBeFocused()
  await page.keyboard.type(PASSWORD)
  await page.keyboard.press("Enter")
  await expect(
    page.getByRole("complementary", { name: "Conversas e contatos" })
  ).toBeVisible()

  // Paleta (Ctrl+K) abre "Mensagens salvas"; o foco vai para a conversa e Enter envia.
  await page.keyboard.press("Control+k")
  await page.keyboard.type("salvas")
  await expect(
    page
      .getByRole("dialog", { name: "Paleta de comandos" })
      .getByRole("option", { name: "Mensagens salvas" })
      .first()
  ).toBeVisible()
  await page.keyboard.press("Enter")
  await expect(
    page.getByRole("heading", { name: "Mensagens salvas" })
  ).toBeVisible()
  const field = page.getByRole("textbox", { name: "Mensagem" })
  await field.focus()
  await page.keyboard.type(`Só com o teclado ${RUN}`)
  await page.keyboard.press("Enter")
  await expect(
    page
      .locator('[data-slot="message"]')
      .filter({ hasText: `Só com o teclado ${RUN}` })
  ).toBeVisible()

  // Menu de uma conversa na lista: Tab até o botão, Enter abre, setas navegam, Esc fecha e devolve o foco.
  const actions = page.getByRole("button", {
    name: "Ações da conversa: Mensagens salvas",
  })
  await actions.focus()
  await page.keyboard.press("Enter")
  const menu = page.getByRole("menu")
  await expect(menu).toBeVisible()
  await page.keyboard.press("ArrowDown")
  await expect(menu.getByRole("menuitem").nth(1)).toBeFocused()
  await page.keyboard.press("Escape")
  await expect(menu).toBeHidden()
  await expect(actions).toBeFocused()

  // Todo elemento focável tem indicador de foco visível (outline ou ring).
  await page.keyboard.press("Tab")
  const visibleFocus = await page.evaluate(() => {
    const element = document.activeElement as HTMLElement | null
    if (!element) {
      return false
    }
    const style = getComputedStyle(element)
    return (
      (style.outlineStyle !== "none" && style.outlineWidth !== "0px") ||
      style.boxShadow !== "none"
    )
  })
  expect(visibleFocus).toBe(true)
})

/** Fase 7: papel de parede, tamanho da fonte e densidade guardados no aparelho (UI-02), com axe. */
test("aparência: papel de parede, fonte e densidade", async ({
  browser,
  pageErrors,
}) => {
  const { context, page } = await ensureAdmin(browser, pageErrors)
  await page.goto("/settings/appearance")
  const choose = async (label: string, option: string) => {
    await page.getByRole("combobox", { name: label }).click()
    await page.getByRole("option", { name: option, exact: true }).click()
  }
  await choose("Papel de parede da conversa", "Pontos")
  await choose("Tamanho da fonte", "Grande")
  await choose("Densidade", "Compacta")
  const html = page.locator("html")
  await expect(html).toHaveAttribute("data-wallpaper", "dots")
  await expect(html).toHaveAttribute("data-font-size", "large")
  await expect(html).toHaveAttribute("data-density", "compact")
  expect(
    await html.evaluate((element) => getComputedStyle(element).fontSize)
  ).toBe("18px")

  // Vale depois de recarregar e aparece na conversa, sem violações do axe nos dois temas. Recarrega só depois
  // de a rota trocar: com o Enter ainda navegando, o reload ficava nas configurações.
  await page.keyboard.press("Control+k")
  const palette = page.getByRole("dialog", { name: "Paleta de comandos" })
  await expect(palette).toBeVisible()
  await page.keyboard.type("salvas")
  await expect(
    palette.getByRole("option", { name: "Mensagens salvas" }).first()
  ).toBeVisible()
  await page.keyboard.press("Enter")
  await expect(
    page.getByRole("heading", { name: "Mensagens salvas" })
  ).toBeVisible()
  await page.reload()
  await expect(html).toHaveAttribute("data-wallpaper", "dots")
  const wallpaper = page.locator(".chat-wallpaper")
  await expect(wallpaper).toBeVisible()
  expect(
    await wallpaper.evaluate(
      (element) => getComputedStyle(element).backgroundImage
    )
  ).toContain("radial-gradient")
  await expectNoSeriousA11yViolations(page)
  await page.emulateMedia({ colorScheme: "dark" })
  await expectNoSeriousA11yViolations(page)

  await page.goto("/settings/appearance")
  await choose("Papel de parede da conversa", "Nenhum")
  await choose("Tamanho da fonte", "Normal")
  await choose("Densidade", "Confortável")
  await context.close()
})
