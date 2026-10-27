import type { Page } from "@playwright/test"

import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  horizontalScrollers,
  test,
} from "./fixtures"
import {
  ADMIN,
  PASSWORD,
  RUN,
  ensureAdmin,
  openContactsTab,
  openMenuItem,
  twoFriends,
} from "./support"

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
  // Junta todas as telas antes de afirmar, para a falha mostrar cada uma que rola.
  const scrolling: [string, string[]][] = []
  for (const path of SCREENS) {
    await visit(page, path)
    scrolling.push([path, await horizontalScrollers(page)])
  }
  expect(scrolling.filter(([, found]) => found.length > 0)).toEqual([])
  await context.close()
})

/**
 * Celular deitado (RESPONSIVENESS §63): pouca altura. As telas principais não rolam para o lado e, na conversa, o campo
 * de mensagem continua à vista.
 */
test("celular deitado: sem rolagem horizontal e com o campo de mensagem à vista", async ({
  browser,
  pageErrors,
}) => {
  test.skip(
    !test.info().project.name.startsWith("mobile"),
    "a orientação é do celular"
  )
  test.slow()
  const { context, page } = await ensureAdmin(browser, pageErrors)
  await page.setViewportSize({ width: 915, height: 412 })
  for (const path of SCREENS) {
    await visit(page, path)
    await expectNoHorizontalScroll(page)
  }
  await page.goto("/")
  await openMenuItem(page, "Mensagens salvas")
  await expect(
    page.getByRole("heading", { name: "Mensagens salvas" })
  ).toBeVisible()
  await expect(page.getByRole("textbox", { name: "Mensagem" })).toBeInViewport()
  await expectNoHorizontalScroll(page)

  // Recorte da câmera nas laterais (viewport-fit=cover): o conteúdo fica fora das áreas inseguras.
  const cdp = await context.newCDPSession(page)
  await cdp.send(
    "Emulation.setSafeAreaInsetsOverride" as never,
    {
      insets: { left: 44, right: 32 },
    } as never
  )
  for (const path of ["/", "/settings"]) {
    await visit(page, path)
    const box = (await page.locator("#root").boundingBox())!
    expect(box.x, path).toBeGreaterThanOrEqual(44)
    expect(box.x + box.width, path).toBeLessThanOrEqual(915 - 32)
    await expectNoHorizontalScroll(page)
  }
  await context.close()
})

/** A aba Contatos com um contato, nos dois temas (AR-01, AR-03): o nome acessível contém o texto visível. */
test("aba Contatos sem violações críticas ou sérias", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}k`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Contato")
  const page = gil.page
  await page.goto("/")
  await openContactsTab(page)
  const contact = page.getByRole("button", {
    name: new RegExp(`Conversar com ${hana.name}`),
  })
  await expect(contact).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  await page.emulateMedia({ colorScheme: "dark" })
  await expect(page.locator("html")).toHaveClass(/dark/)
  await expectNoSeriousA11yViolations(page)
  await gil.context.close()
  await hana.context.close()
})

/**
 * Elemento com o foco e se o indicador de foco aparece (outline ou ring nele ou no InputGroup que o envolve, que
 * desenha o anel do campo).
 */
function focusState(page: Page) {
  return page.evaluate(() => {
    const element = document.activeElement as HTMLElement | null
    if (!element || element === document.body) {
      return { name: "body", visible: true }
    }
    const shows = (candidate: Element | null) => {
      if (!candidate) {
        return false
      }
      const style = getComputedStyle(candidate)
      return (
        (style.outlineStyle !== "none" && style.outlineWidth !== "0px") ||
        style.boxShadow !== "none"
      )
    }
    return {
      name: `${element.tagName.toLowerCase()} "${(element.getAttribute("aria-label") ?? element.textContent ?? "").trim().slice(0, 40)}"`,
      visible:
        shows(element) || shows(element.closest('[data-slot="input-group"]')),
    }
  })
}

/** Tab (ou Shift+Tab) até o alvo, sem focus() programático, conferindo o foco visível em cada parada. */
async function tabTo(
  page: Page,
  target: ReturnType<Page["getByRole"]>,
  key: "Tab" | "Shift+Tab",
  max = 80
) {
  const stops: string[] = []
  for (let step = 0; step < max; step++) {
    if (
      await target.evaluate((element) => element === document.activeElement)
    ) {
      return
    }
    await page.keyboard.press(key)
    const focus = await focusState(page)
    stops.push(focus.name)
    expect(focus.visible, `foco visível em ${focus.name}`).toBe(true)
  }
  throw new Error(
    `o alvo não está na ordem do ${key}; paradas: ${stops.join(" → ")}`
  )
}

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
  await tabTo(page, field, "Tab")
  await page.keyboard.type(`Só com o teclado ${RUN}`)
  await page.keyboard.press("Enter")
  await expect(
    page
      .locator('[data-slot="message"]')
      .filter({ hasText: `Só com o teclado ${RUN}` })
  ).toBeVisible()

  // Menu de uma conversa na lista: Shift+Tab até o botão, Enter abre, setas navegam, Esc fecha e devolve o foco.
  const actions = page.getByRole("button", {
    name: "Ações da conversa: Mensagens salvas",
  })
  await tabTo(page, actions, "Shift+Tab")
  await page.keyboard.press("Enter")
  const menu = page.getByRole("menu")
  await expect(menu).toBeVisible()
  await page.keyboard.press("ArrowDown")
  await expect(menu.getByRole("menuitem").nth(1)).toBeFocused()
  await page.keyboard.press("Escape")
  await expect(menu).toBeHidden()
  await expect(actions).toBeFocused()
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
  const wallpapers = page.getByRole("radiogroup", {
    name: "Papel de parede da conversa",
  })
  await wallpapers.getByRole("radio", { name: "Pontos" }).click()
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
  await page
    .getByRole("radiogroup", { name: "Papel de parede da conversa" })
    .getByRole("radio", { name: "Padrão" })
    .click()
  await choose("Tamanho da fonte", "Normal")
  await choose("Densidade", "Confortável")
  await context.close()
})

/**
 * UI-01 nas bordas dos breakpoints (RESPONSIVENESS §62): 768 px separa a pilha do celular das duas colunas e
 * 1280 px separa a gaveta de detalhes da terceira coluna. Em B−1, B e B+1, o layout certo e nada de rolagem
 * horizontal.
 */
test("bordas dos breakpoints de colunas e do painel de detalhes", async ({
  browser,
  pageErrors,
}) => {
  test.skip(
    test.info().project.name !== "desktop-chromium",
    "o redimensionamento é o mesmo nos dois projetos"
  )
  test.slow()
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}b`,
    "Borda"
  )
  const page = gil.page
  const chat = page.url()
  const sidebar = page.getByRole("complementary", {
    name: "Conversas e contatos",
  })
  const main = page.getByRole("main")
  for (const width of [767, 768, 769]) {
    await page.setViewportSize({ width, height: 800 })
    await page.goto("/")
    await expect(sidebar).toBeVisible()
    await (width < 768 ? expect(main).toBeHidden() : expect(main).toBeVisible())
    await expectNoHorizontalScroll(page)
    await page.goto(chat)
    await expect(page.getByRole("heading", { name: hana.name })).toBeVisible()
    await (width < 768
      ? expect(sidebar).toBeHidden()
      : expect(sidebar).toBeVisible())
    await expectNoHorizontalScroll(page)
  }
  for (const width of [1279, 1280, 1281]) {
    await page.setViewportSize({ width, height: 800 })
    await page.getByRole("button", { name: /^Dados do contato/ }).click()
    const panel =
      width < 1280
        ? page.getByRole("dialog", { name: "Dados do contato" })
        : page.getByRole("complementary", { name: "Dados do contato" })
    await expect(panel).toBeVisible()
    await expectNoHorizontalScroll(page)
    await page.keyboard.press("Escape")
    await expect(panel).toBeHidden()
  }
  await gil.context.close()
  await hana.context.close()
})
