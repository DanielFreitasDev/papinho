import type { Page } from "@playwright/test"

import {
  expect,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  test,
} from "./fixtures"
import { openMenuItem, RUN, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(row(page, text)).toBeVisible()
}

/**
 * Fase 4: grupo entre dois navegadores (GR-01) — criar, avisos do sistema, nome de quem envia, permissões, link
 * de convite (GR-02), responder em privado — e mensagens salvas (MS-12).
 */
test("grupo com avisos, permissões, convite e responder em privado", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}g`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Grupo")
  const groupName = `Amigos ${tag}`

  // Gil cria o grupo com a Hana pelo menu.
  await gil.page.goto("/")
  await openMenuItem(gil.page, "Novo grupo")
  const dialog = gil.page.getByRole("dialog", { name: "Novo grupo" })
  await dialog.getByLabel("Nome do grupo").fill(groupName)
  await dialog.getByRole("checkbox", { name: /Hana Grupo/ }).check()
  await expectNoSeriousA11yViolations(gil.page)
  await dialog.getByRole("button", { name: "Criar grupo" }).click()
  await expect(gil.page.getByRole("heading", { name: groupName })).toBeVisible()
  await expect(
    gil.page.getByRole("main").getByText(`Você criou o grupo "${groupName}"`)
  ).toBeVisible()
  await expect(
    gil.page.getByRole("main").getByText("Você adicionou Hana Grupo")
  ).toBeVisible()
  await send(gil.page, "Bem-vinda ao grupo!")

  // A Hana vê o grupo na lista com quem mandou a última mensagem e o nome do Gil no balão.
  await hana.page.goto("/")
  const item = hana.page.getByRole("link", { name: new RegExp(groupName) })
  await expect(item).toContainText("Gil Grupo: Bem-vinda ao grupo!")
  await item.click()
  const welcome = row(hana.page, "Bem-vinda ao grupo!")
  await expect(welcome.getByText("Gil Grupo", { exact: true })).toBeVisible()
  await expect(
    hana.page.getByRole("main").getByText("Gil Grupo adicionou você")
  ).toBeVisible()
  await send(hana.page, "Obrigada! @todos")
  await expect(
    row(gil.page, "Obrigada!").locator("strong", { hasText: "@todos" })
  ).toBeVisible()
  await expect(
    row(gil.page, "Obrigada!").getByRole("link", { name: "@todos" })
  ).toHaveCount(0)
  await expect(row(gil.page, "Obrigada!")).toBeVisible()

  // Enquete (MS-09): o voto da Hana aparece para o Gil em tempo real, com o nome de quem votou.
  await gil.page.getByRole("button", { name: "Nova enquete" }).click()
  const pollDialog = gil.page.getByRole("dialog", { name: "Nova enquete" })
  await pollDialog.getByLabel("Pergunta").fill("Onde vamos?")
  await pollDialog.getByLabel("Opção 1").fill("Praia")
  await pollDialog.getByLabel("Opção 2").fill("Serra")
  await expectNoSeriousA11yViolations(gil.page)
  await pollDialog.getByRole("button", { name: "Enviar enquete" }).click()
  await expect(pollDialog).toBeHidden()
  const pollForHana = hana.page.getByRole("group", { name: "Onde vamos?" })
  await pollForHana.getByRole("radio", { name: /Praia/ }).click()
  await expect(
    pollForHana.getByRole("radio", { name: /Praia/ })
  ).toHaveAttribute("aria-checked", "true")
  const pollForGil = gil.page.getByRole("group", { name: "Onde vamos?" })
  await expect(pollForGil.getByText("1 pessoa votou")).toBeVisible()
  await expect(
    pollForGil.getByRole("radio", { name: /Praia/ }).getByText("Hana Grupo")
  ).toBeVisible()

  // Dados do grupo: membros, link de convite com QR Code e "só admins enviam".
  await gil.page.getByRole("button", { name: /Dados do grupo/ }).click()
  const details = gil.page
    .getByRole("complementary", { name: "Dados do grupo" })
    .or(gil.page.getByRole("dialog", { name: "Dados do grupo" }))
  await expect(details.getByText("2 membros").first()).toBeVisible()
  await expect(details.getByText("Admin", { exact: true })).toBeVisible()
  await expectNoSeriousA11yViolations(gil.page)
  await details.getByRole("button", { name: "Convidar por link" }).click()
  const invite = gil.page.getByRole("dialog", { name: "Convidar por link" })
  await expect(
    invite.getByRole("img", { name: "QR Code do convite do grupo" })
  ).toBeVisible()
  await expect(invite.locator("code")).toContainText("/g/")
  await gil.page.keyboard.press("Escape")
  await expect(invite).toBeHidden()
  await details
    .getByRole("switch", { name: "Só admins enviam mensagens" })
    .click()
  await expect(
    hana.page
      .getByRole("main")
      .getByText("Só admins podem enviar mensagens neste grupo.")
  ).toBeVisible()
  await expect(
    hana.page.getByRole("textbox", { name: "Mensagem" })
  ).toHaveCount(0)
  await expectNoHorizontalScroll(hana.page)

  // Responder em privado: abre a conversa individual com a citação do grupo.
  const target = row(hana.page, "Bem-vinda ao grupo!")
  await target.hover()
  await target.getByRole("button", { name: "Ações da mensagem" }).click()
  await hana.page
    .getByRole("menuitem", { name: "Responder em privado" })
    .click()
  await expect(
    hana.page.getByRole("heading", { name: "Gil Grupo" })
  ).toBeVisible()
  await expect(
    hana.page.getByRole("main").getByText("Respondendo a Gil Grupo")
  ).toBeVisible()
  await send(hana.page, "Respondendo só para você")
  await gil.page.goto("/")
  await gil.page.getByRole("link", { name: /Hana Grupo/ }).click()
  const privateReply = row(gil.page, "Respondendo só para você")
  await expect(privateReply.getByText("Bem-vinda ao grupo!")).toBeVisible()

  // Lista de transmissão (GR-03): chega para a Hana na conversa individual com o Gil.
  await openMenuItem(gil.page, "Nova lista de transmissão")
  const broadcast = gil.page.getByRole("dialog", {
    name: "Nova lista de transmissão",
  })
  await broadcast.getByLabel("Nome da lista").fill(`Avisos ${tag}`)
  await broadcast.getByRole("checkbox", { name: /Hana Grupo/ }).check()
  await expectNoSeriousA11yViolations(gil.page)
  await broadcast.getByRole("button", { name: "Criar lista" }).click()
  await expect(
    gil.page.getByRole("heading", { name: `Avisos ${tag}` })
  ).toBeVisible()
  await expect(
    gil.page.getByRole("main").getByText("1 destinatário")
  ).toBeVisible()
  await send(gil.page, "Aviso geral para todos")
  await hana.page.goto("/")
  await hana.page.getByRole("link", { name: /^Gil Grupo/ }).click()
  await expect(row(hana.page, "Aviso geral para todos")).toBeVisible()

  // Mensagens salvas (MS-12).
  await openMenuItem(gil.page, "Mensagens salvas")
  await expect(
    gil.page.getByRole("heading", { name: "Mensagens salvas" })
  ).toBeVisible()
  await send(gil.page, "Lembrete só meu")
  await expectNoSeriousA11yViolations(gil.page)

  expect(pageErrors).toEqual([])
  await gil.context.close()
  await hana.context.close()
})
