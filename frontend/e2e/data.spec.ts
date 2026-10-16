import { readFile } from "node:fs/promises"
import type { Download, Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { RUN, ensureAdmin, registerThroughUi, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

/** Conteúdo do ZIP baixado: começa com "PK" e lista os nomes das entradas sem compressão. */
async function zipText(download: Download) {
  const bytes = await readFile((await download.path())!)
  expect(bytes.subarray(0, 2).toString("latin1")).toBe("PK")
  return bytes.toString("latin1")
}

/** No celular a lista fica na tela inicial; no desktop está sempre ao lado. */
async function showList(page: Page) {
  const back = page.getByRole("link", { name: "Voltar" })
  if (await back.isVisible()) {
    await back.click()
  }
  return page.getByRole("complementary", { name: "Conversas e contatos" })
}

/** Fase 7: exportar conversa (BK-01), dados da conta e excluir a conta (BK-02). */
test("exportar conversa e dados e excluir a conta", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}x`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Dados")
  await gil.page
    .getByRole("textbox", { name: "Mensagem" })
    .fill("Fica no histórico")
  await gil.page.getByRole("button", { name: "Enviar" }).click()
  await expect(row(hana.page, "Fica no histórico")).toBeVisible()

  // Exportar a conversa pelo menu: ZIP com o texto.
  const list = await showList(hana.page)
  await list
    .getByRole("button", { name: `Ações da conversa: ${gil.name}` })
    .click()
  await hana.page.getByRole("menuitem", { name: "Exportar conversa" }).click()
  const dialog = hana.page.getByRole("alertdialog", {
    name: `Exportar a conversa com ${gil.name}?`,
  })
  await expectNoSeriousA11yViolations(hana.page)
  const [chatZip] = await Promise.all([
    hana.page.waitForEvent("download"),
    dialog.getByRole("button", { name: "Exportar conversa" }).click(),
  ])
  expect(await zipText(chatZip)).toContain("Conversa.txt")

  // Seus dados: ZIP com dados.json.
  await hana.page.goto("/settings/account")
  const [dataZip] = await Promise.all([
    hana.page.waitForEvent("download"),
    hana.page.getByRole("link", { name: "Baixar meus dados" }).click(),
  ])
  expect(await zipText(dataZip)).toContain("dados.json")

  // Excluir conta: senha errada avisa; a certa encerra tudo e leva ao login.
  await hana.page.getByRole("button", { name: "Excluir minha conta" }).click()
  const confirm = hana.page.getByRole("dialog", {
    name: "Excluir sua conta para sempre?",
  })
  await confirm
    .getByLabel("Senha", { exact: true })
    .fill("senha errada qualquer")
  await confirm.getByRole("button", { name: "Excluir conta" }).click()
  await expect(confirm.getByRole("alert")).toBeVisible()
  await expectNoSeriousA11yViolations(hana.page)
  await confirm
    .getByLabel("Senha", { exact: true })
    .fill("senha muito segura 123")
  await confirm.getByRole("button", { name: "Excluir conta" }).click()
  await expect(hana.page).toHaveURL(/\/login/)

  // Para o Gil, a conversa continua como "Conta apagada", sem campo para escrever.
  await gil.page.reload()
  await expect(row(gil.page, "Fica no histórico")).toBeVisible()
  await expect(
    gil.page.getByRole("heading", { name: "Conta apagada" })
  ).toBeVisible()
  await expect(gil.page.getByRole("textbox", { name: "Mensagem" })).toHaveCount(
    0
  )

  await gil.context.close()
  await hana.context.close()
})

/** Fase 7: painel do admin com convite, papel de admin e uso de disco (AD-01). */
test("painel do admin: convite, uso de disco e usuários", async ({
  browser,
  pageErrors,
}) => {
  const { context, page } = await ensureAdmin(browser, pageErrors)
  await page.goto("/admin")
  await page.getByRole("tab", { name: "Usuários" }).click()
  await page.getByRole("button", { name: "Gerar convite" }).click()
  const invite = page.getByRole("dialog", { name: "Convidar amigos" })
  await invite.getByRole("button", { name: "Criar convite" }).click()
  const url = (await invite.locator("code").textContent())!.trim()
  expect(url).toContain("/register?invite=")
  await page.keyboard.press("Escape")

  // Quem entrou pelo convite pode virar admin da instância.
  const guest = await browser.newContext()
  const guestPage = await guest.newPage()
  const username = `convidado.${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}`
  await guestPage.goto(url)
  await registerThroughUi(guestPage, username, "Pessoa Convidada")
  await guest.close()
  await page
    .getByRole("textbox", { name: "Buscar por @usuario" })
    .fill(username)
  const person = page.getByRole("listitem").filter({ hasText: `@${username}` })
  await person.getByRole("button", { name: "Tornar admin" }).click()
  await expect(person.getByText("admin", { exact: true })).toBeVisible()
  await person.getByRole("button", { name: "Tirar admin" }).click()
  await expect(
    person.getByRole("button", { name: "Tornar admin" })
  ).toBeVisible()

  await page.getByRole("tab", { name: "Disco" }).click()
  await expect(
    page.getByRole("heading", { name: "Uso de disco" })
  ).toBeVisible()
  await expect(page.getByText("Banco de dados")).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  await context.close()
})
