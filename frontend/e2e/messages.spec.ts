import type { Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function openActions(page: Page, text: string) {
  const target = row(page, text)
  await target.hover()
  await target.getByRole("button", { name: "Ações da mensagem" }).click()
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
  // Confirmada pelo servidor (não mais "Enviando"): só então pode ser editada.
  await expect(
    row(page, text).getByRole("img", { name: /^(Enviada|Entregue|Lida)$/ })
  ).toBeVisible()
}

/** Fase 4: editar (MS-05), apagar para todos e para mim (MS-06) e timer de temporárias (MS-07), entre dois navegadores. */
test("editar, apagar, temporárias, rascunho, silenciosa e agendada", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}e`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Edita")

  // Editar pelo menu: o selo "editada" e o texto novo chegam para a outra pessoa.
  await send(gil.page, "Oi Hana, tudo bme?")
  await expect(row(hana.page, "tudo bme?")).toBeVisible()
  await openActions(gil.page, "tudo bme?")
  await gil.page.getByRole("menuitem", { name: "Editar" }).click()
  await expect(gil.page.getByText("Editando mensagem")).toBeVisible()
  const field = gil.page.getByRole("textbox", { name: "Mensagem" })
  await expect(field).toHaveValue("Oi Hana, tudo bme?")
  await field.fill("Oi Hana, tudo bem?")
  await gil.page.getByRole("button", { name: "Salvar edição" }).click()
  const edited = row(hana.page, "tudo bem?")
  await expect(edited).toBeVisible()
  await expect(edited.getByText("editada")).toBeVisible()
  await expect(row(hana.page, "tudo bme?")).toHaveCount(0)

  // ↑ com o campo vazio edita a última mensagem própria; Esc cancela.
  await send(gil.page, "Segunda msg")
  await field.press("ArrowUp")
  await expect(field).toHaveValue("Segunda msg")
  await field.press("Escape")
  await expect(gil.page.getByText("Editando mensagem")).toBeHidden()
  await expect(field).toHaveValue("")

  // Apagar para todos: os dois veem o aviso no lugar da mensagem.
  await openActions(gil.page, "Segunda msg")
  await gil.page.getByRole("menuitem", { name: "Apagar" }).click()
  const dialog = gil.page.getByRole("alertdialog", { name: "Apagar mensagem?" })
  await expect(dialog).toBeVisible()
  await expectNoSeriousA11yViolations(gil.page)
  await dialog.getByRole("button", { name: "Apagar para todos" }).click()
  await expect(
    gil.page.getByRole("main").getByText("Você apagou esta mensagem")
  ).toBeVisible()
  await expect(
    hana.page.getByRole("main").getByText("Esta mensagem foi apagada")
  ).toBeVisible()
  await expect(row(hana.page, "Segunda msg")).toHaveCount(0)

  // Apagar para mim: some só para quem apagou.
  await openActions(hana.page, "tudo bem?")
  await hana.page.getByRole("menuitem", { name: "Apagar" }).click()
  const forMe = hana.page.getByRole("alertdialog", { name: "Apagar mensagem?" })
  await expect(
    forMe.getByRole("button", { name: "Apagar para todos" })
  ).toHaveCount(0)
  await forMe.getByRole("button", { name: "Apagar para mim" }).click()
  await expect(row(hana.page, "tudo bem?")).toHaveCount(0)
  await expect(row(gil.page, "tudo bem?")).toBeVisible()
  await hana.page.reload()
  await expect(
    hana.page.getByRole("main").getByText("Esta mensagem foi apagada")
  ).toBeVisible()
  await expect(row(hana.page, "tudo bem?")).toHaveCount(0)

  // Mensagens temporárias (MS-07): o timer vira aviso para os dois e vale para as próximas mensagens.
  await gil.page.getByRole("button", { name: /Dados do contato/ }).click()
  await gil.page
    .getByRole("combobox", { name: "Mensagens temporárias" })
    .click()
  await gil.page.getByRole("option", { name: "24 horas" }).click()
  await expect(
    hana.page
      .getByRole("main")
      .getByText("Gil Edita ativou as mensagens temporárias: 24 horas")
  ).toBeVisible()
  await expect(
    gil.page
      .getByRole("main")
      .getByText("Você ativou as mensagens temporárias: 24 horas")
  ).toBeVisible()

  // Esc fecha o painel de dados do contato (coluna ou gaveta, MS-13).
  await gil.page.keyboard.press("Escape")
  await expect(
    gil.page.getByRole("combobox", { name: "Mensagens temporárias" })
  ).toBeHidden()

  // Emojis (MS-10): busca no seletor do campo e reação com qualquer emoji.
  await gil.page.getByRole("button", { name: "Emojis" }).click()
  await gil.page.getByRole("searchbox", { name: "Buscar emoji" }).fill("pizza")
  await gil.page.getByRole("button", { name: "pizza", exact: true }).click()
  await expect(field).toHaveValue("🍕")
  await field.press("End")
  await field.pressSequentially(" hoje?")
  await gil.page.getByRole("button", { name: "Enviar" }).click()
  const pizza = row(hana.page, "🍕 hoje?")
  await pizza.hover()
  await pizza.getByRole("button", { name: "Ações da mensagem" }).click()
  await hana.page.getByRole("menuitem", { name: "Mais reações" }).click()
  const picker = hana.page.getByRole("dialog", { name: "Mais reações" })
  await expectNoSeriousA11yViolations(hana.page)
  await picker.getByRole("searchbox", { name: "Buscar emoji" }).fill("pizza")
  await picker.getByRole("button", { name: "pizza", exact: true }).click()
  await expect(
    row(gil.page, "🍕 hoje?").getByLabel("Reações").getByText("🍕")
  ).toBeVisible()

  // Rascunho sincronizado (MS-11): sobrevive a recarregar a página.
  const hanaField = hana.page.getByRole("textbox", { name: "Mensagem" })
  await hanaField.fill("rascunho que fica")
  await hana.page.waitForResponse((r) => r.url().endsWith("/draft"))
  await hana.page.reload()
  await expect(
    hana.page.getByRole("textbox", { name: "Mensagem" })
  ).toHaveValue("rascunho que fica")

  // Envio silencioso e agendado (MS-11).
  await field.fill("psiu")
  await gil.page.getByRole("button", { name: "Mais opções de envio" }).click()
  await gil.page
    .getByRole("menuitem", { name: "Enviar sem notificação" })
    .click()
  await expect(
    row(hana.page, "psiu").getByRole("img", { name: "Enviada sem notificação" })
  ).toBeVisible()
  await field.fill("Mensagem para depois")
  await gil.page.getByRole("button", { name: "Mais opções de envio" }).click()
  await gil.page.getByRole("menuitem", { name: "Agendar envio" }).click()
  const schedule = gil.page.getByRole("dialog", { name: "Agendar mensagem" })
  // Seletor próprio de data e hora (UI-05): já vem com o dia e a hora preenchidos.
  const when = schedule.getByRole("group", { name: "Enviar em" })
  await expect(when.getByRole("button", { name: /^Data: \d/ })).toBeVisible()
  await expect(when.getByRole("combobox", { name: "Hora" })).not.toHaveText(
    "--"
  )
  await expectNoSeriousA11yViolations(gil.page)
  await schedule.getByRole("button", { name: "Agendar envio" }).click()
  await expect(schedule).toBeHidden()
  await gil.page.getByRole("button", { name: "1 mensagem agendada" }).click()
  const list = gil.page.getByRole("dialog", { name: "Mensagens agendadas" })
  await expect(list.getByText("Mensagem para depois")).toBeVisible()
  await list.getByRole("button", { name: "Cancelar" }).click()
  await expect(list.getByText("Mensagem para depois")).toBeHidden()
  await gil.page.keyboard.press("Escape")
  await expect(
    gil.page.getByRole("button", { name: "1 mensagem agendada" })
  ).toBeHidden()

  expect(pageErrors).toEqual([])
  await gil.context.close()
  await hana.context.close()
})
