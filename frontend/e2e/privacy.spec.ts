import type { Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import {
  createInvite,
  ensureAdmin,
  openMenuItem,
  registerThroughUi,
  RUN,
  twoFriends,
} from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(
    row(page, text).getByRole("img", { name: /^(Enviada|Entregue|Lida)$/ })
  ).toBeVisible()
}

async function openPrivacySettings(page: Page) {
  await openMenuItem(page, "Configurações")
  await page.getByRole("link", { name: "Privacidade" }).click()
  await expect(
    page.getByRole("heading", { name: "Quem pode ver" })
  ).toBeVisible()
}

async function choose(page: Page, label: string, option: string) {
  await page.getByRole("combobox", { name: label, exact: true }).click()
  await page.getByRole("option", { name: option, exact: true }).click()
  await expect(page.getByText("Alterações salvas").first()).toBeVisible()
}

/** Fase 5: privacidade recíproca (PV-04..PV-06) e modo privado (PV-01), entre dois navegadores. */
test("online e leitura recíprocos e modo privado", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}p`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Priv")
  const gilChat = gil.page.url()
  const hanaChat = hana.page.url()
  const online = (page: Page) =>
    page.getByRole("main").getByText("online", { exact: true })
  await expect(online(hana.page)).toBeVisible()
  await expect(online(gil.page)).toBeVisible()

  // Gil esconde o online: some para a Hana e, pela reciprocidade, ele também deixa de ver o dela.
  await openPrivacySettings(gil.page)
  await expectNoSeriousA11yViolations(gil.page)
  await choose(gil.page, "Online", "Ninguém")
  await expect(online(hana.page)).toBeHidden()
  await gil.page.goto(gilChat)
  await expect(gil.page.getByRole("heading", { name: hana.name })).toBeVisible()
  await expect(online(gil.page)).toBeHidden()

  // Hana desliga a confirmação de leitura: Gil fica no "Entregue" e ela também não vê a leitura dele.
  await openPrivacySettings(hana.page)
  await hana.page
    .getByRole("switch", { name: "Confirmação de leitura" })
    .click()
  await expect(hana.page.getByText("Alterações salvas").first()).toBeVisible()
  await hana.page.goto(hanaChat)
  await send(gil.page, "Leu isto?")
  await expect(row(hana.page, "Leu isto?")).toBeVisible()
  await expect(
    row(gil.page, "Leu isto?").getByRole("img", { name: "Entregue" })
  ).toBeVisible()
  await send(hana.page, "Li, mas você não sabe")
  await expect(row(gil.page, "Li, mas você não sabe")).toBeVisible()
  await expect(
    row(hana.page, "Li, mas você não sabe").getByRole("img", {
      name: "Entregue",
    })
  ).toBeVisible()
  await expect(
    row(gil.page, "Leu isto?").getByRole("img", { name: "Lida" })
  ).toHaveCount(0)

  // Modo privado pelo atalho global: a mensagem fica desfocada e o foco do teclado revela.
  await gil.page.keyboard.press("Alt+Shift+P")
  await expect(gil.page.locator("html")).toHaveAttribute(
    "data-blur",
    /messages/
  )
  const bubble = row(gil.page, "Li, mas você não sabe").locator(
    '[data-private="messages"]'
  )
  await gil.page.mouse.move(0, 0)
  await expect(bubble).toHaveCSS("filter", /blur/)
  // O foco do teclado em qualquer controle da mensagem revela; sair dela desfoca de novo.
  await row(gil.page, "Li, mas você não sabe")
    .getByRole("button", { name: "Ações da mensagem" })
    .focus()
  await expect(bubble).toHaveCSS("filter", "none")
  await gil.page.getByRole("textbox", { name: "Mensagem" }).focus()
  await expect(bubble).toHaveCSS("filter", /blur/)
  await gil.page.keyboard.press("Alt+Shift+P")
  await expect(gil.page.locator("html")).not.toHaveAttribute("data-blur", /.+/)
  await expect(bubble).toHaveCSS("filter", "none")

  await gil.context.close()
  await hana.context.close()
})

/** Fase 5: conversa trancada (PV-03) e bloqueio de tela (PV-02). */
test("conversa trancada e bloqueio de tela", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}t`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Tranca")
  const page = gil.page
  const chat = page.url()
  await send(page, "Segredo guardado")

  // Trancar pelo painel do contato: a conversa vai para a pasta e some da lista.
  await page.getByRole("button", { name: /Dados do contato/ }).click()
  await page.getByRole("button", { name: "Trancar conversa" }).click()
  const lockDialog = page.getByRole("dialog", { name: "Trancar conversa" })
  await lockDialog.getByLabel("Código", { exact: true }).fill("2468")
  await lockDialog.getByLabel("Repita o código").fill("2468")
  await expectNoSeriousA11yViolations(page)
  await lockDialog.getByRole("button", { name: "Trancar conversa" }).click()
  await expect(
    page.getByText("Conversa trancada", { exact: true })
  ).toBeVisible()
  await expect(
    page.getByRole("heading", { name: "Conversas trancadas" })
  ).toBeVisible()
  await expect(
    page.getByRole("main").getByRole("link", { name: new RegExp(hana.name) })
  ).toBeVisible()

  // Recarregar esquece o token: a pasta e a conversa pedem o código de novo.
  await page.reload()
  await expect(
    page.getByRole("heading", {
      name: "Digite o código das conversas trancadas",
    })
  ).toBeVisible()
  await page.goto(chat)
  await expect(
    page.getByRole("heading", { name: "Esta conversa está trancada" })
  ).toBeVisible()
  await expect(page.getByText("Segredo guardado")).toHaveCount(0)
  await page.getByLabel("Código", { exact: true }).fill("0000")
  await page.getByRole("button", { name: "Abrir" }).click()
  await expect(
    page.getByText("O código não abre nenhuma conversa trancada.")
  ).toBeVisible()
  await page.getByLabel("Código", { exact: true }).fill("2468")
  await page.getByRole("button", { name: "Abrir" }).click()
  await expect(row(page, "Segredo guardado")).toBeVisible()

  // Na lista principal ela não aparece; só a entrada da pasta.
  await page.goto("/")
  const sidebar = page.getByRole("complementary", {
    name: "Conversas e contatos",
  })
  await expect(
    sidebar.getByRole("link", { name: "Conversas trancadas" })
  ).toBeVisible()
  await expect(
    sidebar.getByRole("link", { name: new RegExp(hana.name) })
  ).toHaveCount(0)

  // Bloqueio de tela com PIN próprio; "Bloquear tela" cobre tudo até o PIN certo.
  await openMenuItem(page, "Configurações")
  await page.getByRole("link", { name: "Privacidade" }).click()
  await page.getByLabel("Novo PIN").fill("1357")
  await page.getByLabel("Repita para confirmar").fill("1357")
  await page.getByRole("button", { name: "Ligar o bloqueio" }).click()
  await expect(page.getByText("Bloqueio de tela ligado")).toBeVisible()
  await page.getByRole("button", { name: "Bloquear tela" }).click()
  const lock = page.getByRole("dialog", { name: "Papinho bloqueado" })
  await expect(lock).toBeVisible()
  // Tudo por trás fica inerte (sem foco nem clique) e desfocado.
  await expect(page.locator("body > [inert]").first()).toBeAttached()
  await expectNoSeriousA11yViolations(page)
  await lock.getByLabel("PIN").fill("0000")
  await lock.getByRole("button", { name: "Desbloquear" }).click()
  await expect(
    lock.getByText("O PIN ou a senha do bloqueio de tela não confere.")
  ).toBeVisible()
  // Recarregar não tira o bloqueio.
  await page.reload()
  await expect(lock).toBeVisible()
  await lock.getByLabel("PIN").fill("1357")
  await lock.getByRole("button", { name: "Desbloquear" }).click()
  await expect(lock).toBeHidden()
  await expect(
    page.getByRole("heading", { name: "Configurações" })
  ).toBeVisible()

  // O atalho padrão também bloqueia.
  await page.keyboard.press("Alt+Shift+L")
  await expect(lock).toBeVisible()
  await lock.getByLabel("PIN").fill("1357")
  await lock.getByRole("button", { name: "Desbloquear" }).click()
  await expect(lock).toBeHidden()

  // Gatilho "trocar de aba": a aba escondida bloqueia.
  await page
    .getByRole("switch", { name: "Bloquear ao trocar de aba ou de janela" })
    .click()
  await page.getByRole("button", { name: "Salvar", exact: true }).click()
  await expect(page.getByText("Alterações salvas").first()).toBeVisible()
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", {
      configurable: true,
      get: () => "hidden",
    })
    document.dispatchEvent(new Event("visibilitychange"))
  })
  await expect(lock).toBeVisible()
  await lock.getByLabel("PIN").fill("1357")
  await lock.getByRole("button", { name: "Desbloquear" }).click()
  await expect(lock).toBeHidden()

  await gil.context.close()
  await hana.context.close()
})

/** Fase 5: bloqueio por inatividade e desbloqueio com passkey (PV-02). */
test("bloqueio por inatividade e desbloqueio com passkey", async ({
  page,
  browserName,
  pageErrors,
}) => {
  test.skip(
    browserName !== "chromium",
    "autenticador virtual só existe no Chromium (CDP)"
  )
  test.slow()
  const cdp = await page.context().newCDPSession(page)
  await cdp.send("WebAuthn.enable")
  await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "internal",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
    },
  })
  const username = `ocioso.${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}`
  const owner = await ensureAdmin(page.context().browser()!, pageErrors)
  const invite = await createInvite(owner.page)
  await owner.context.close()
  await page.goto(invite)
  await registerThroughUi(page, username, "Pessoa Ociosa")

  await page.goto("/settings/security")
  await page.getByLabel("Nome da passkey").fill("Notebook de teste")
  await page.getByRole("button", { name: "Adicionar passkey" }).click()
  await expect(page.getByText("Notebook de teste")).toBeVisible()

  // Relógio controlado: a inatividade passa sem esperar minutos de verdade.
  await page.clock.install()
  await page.goto("/settings/privacy")
  await page.getByLabel("Novo PIN").fill("8642")
  await page.getByLabel("Repita para confirmar").fill("8642")
  await page
    .getByRole("combobox", { name: "Bloquear depois de um tempo sem uso" })
    .click()
  await page.getByRole("option", { name: "1 minuto", exact: true }).click()
  await page
    .getByRole("switch", { name: "Desbloquear também com passkey" })
    .click()
  await page.getByRole("button", { name: "Ligar o bloqueio" }).click()
  await expect(page.getByText("Bloqueio de tela ligado")).toBeVisible()

  const lock = page.getByRole("dialog", { name: "Papinho bloqueado" })
  await expect(lock).toBeHidden()
  await page.clock.fastForward("01:10")
  await expect(lock).toBeVisible()
  await lock.getByRole("button", { name: "Usar passkey" }).click()
  await expect(lock).toBeHidden()
  // Mesma conta, mesma tela: a passkey só confirmou quem já estava ali.
  await expect(
    page.getByRole("heading", { name: "Configurações" })
  ).toBeVisible()
  await expect(page.getByText("Bloqueio de tela ligado")).toHaveCount(0)
})
