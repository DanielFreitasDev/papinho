import type { Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

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

/** No celular a lista fica na tela inicial; no desktop está sempre ao lado. */
async function showList(page: Page) {
  const back = page.getByRole("link", { name: "Voltar" })
  if (await back.isVisible()) {
    await back.click()
  }
  return page.getByRole("complementary", { name: "Conversas e contatos" })
}

async function conversationAction(page: Page, name: string, item: string) {
  const list = await showList(page)
  await list.getByRole("button", { name: `Ações da conversa: ${name}` }).click()
  await page.getByRole("menuitem", { name: item }).click()
}

/** Fase 6: arquivar, fixar, silenciar, favoritar, listas, limpar (GR-05) e busca e filtros (GR-06). */
test("organizar a lista e buscar conversas e mensagens", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}o`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Organiza")
  await send(gil.page, "Reunião às três na Fortaleza")
  await expect(row(hana.page, "Reunião às três")).toBeVisible()
  await send(hana.page, "combinado")
  await expect(row(gil.page, "combinado")).toBeVisible()

  // Arquivar: sai de "Todas" e vai para "Arquivadas"; desarquivar traz de volta.
  await conversationAction(gil.page, hana.name, "Arquivar")
  const list = await showList(gil.page)
  const hanaLink = list.getByRole("link", { name: new RegExp(hana.name) })
  await expect(hanaLink).toHaveCount(0)
  await list.getByRole("button", { name: /Arquivadas/ }).click()
  await expect(hanaLink).toBeVisible()
  await conversationAction(gil.page, hana.name, "Desarquivar")
  await list.getByRole("button", { name: "Voltar" }).click()
  await expect(hanaLink).toBeVisible()

  // Fixar, silenciar sempre (diálogo com axe) e favoritar.
  await conversationAction(gil.page, hana.name, "Fixar no topo")
  await expect(list.getByRole("img", { name: "Fixada" })).toBeVisible()
  await conversationAction(gil.page, hana.name, "Silenciar")
  const mute = gil.page.getByRole("dialog", { name: `Silenciar ${hana.name}` })
  await mute.getByRole("radio", { name: "Sempre" }).click()
  await mute.getByText("Ainda notificar quando me mencionarem").click()
  await expectNoSeriousA11yViolations(gil.page)
  await mute.getByRole("button", { name: "Silenciar" }).click()
  await expect(list.getByRole("img", { name: "Silenciada" })).toBeVisible()
  await conversationAction(gil.page, hana.name, "Favoritar")
  const filters = list.getByRole("group", { name: "Filtrar conversas" })
  await filters.getByRole("button", { name: "Favoritas" }).click()
  await expect(hanaLink).toBeVisible()
  await filters.getByRole("button", { name: "Grupos" }).click()
  await expect(hanaLink).toHaveCount(0)
  await expect(list.getByText("Nenhuma conversa neste filtro")).toBeVisible()
  await expectNoSeriousA11yViolations(gil.page)
  await filters.getByRole("button", { name: "Todas" }).click()

  // Busca global sem acento e por trecho: abre a conversa na mensagem.
  const search = list.getByRole("searchbox", {
    name: "Buscar conversas, contatos e mensagens",
  })
  const messages = list.getByRole("region", { name: "Mensagens" })
  await search.fill("fortal")
  await expect(
    messages.getByRole("link", { name: /Reunião às três/ })
  ).toBeVisible()
  await search.fill("organiza")
  await expect(
    list
      .getByRole("region", { name: "Conversas" })
      .getByRole("link", { name: hana.name })
  ).toBeVisible()
  await search.fill("reuniao")
  await messages.getByRole("link", { name: /Reunião às três/ }).click()
  await expect(row(gil.page, "Reunião às três")).toBeVisible()

  // Busca dentro da conversa por texto e por remetente.
  await gil.page.getByRole("button", { name: "Buscar nesta conversa" }).click()
  const inside = gil.page.getByRole("search", {
    name: "Buscar nesta conversa",
  })
  await inside.getByRole("searchbox").fill("combinado")
  await expect(inside.getByRole("status")).toHaveText("1 de 1")
  await expectNoSeriousA11yViolations(gil.page)
  await inside.getByRole("searchbox").press("Escape")
  await expect(inside).toBeHidden()
  await (await showList(gil.page)).getByRole("searchbox").fill("")

  // Limpar some com as mensagens só para o Gil.
  await conversationAction(gil.page, hana.name, "Limpar conversa")
  await gil.page
    .getByRole("alertdialog", { name: `Limpar a conversa com ${hana.name}?` })
    .getByRole("button", { name: "Limpar conversa" })
    .click()
  await gil.page.getByRole("link", { name: new RegExp(hana.name) }).click()
  await expect(row(gil.page, "combinado")).toHaveCount(0)
  await expect(row(hana.page, "combinado")).toBeVisible()

  // Listas personalizadas: criar em Configurações > Conversas e filtrar pela lista.
  await gil.page.goto("/settings/chats")
  await gil.page.getByRole("button", { name: "Nova lista" }).click()
  const folder = gil.page.getByRole("dialog", { name: "Nova lista" })
  await folder.getByLabel("Nome da lista").fill("Trabalho")
  await folder.getByRole("checkbox", { name: hana.name }).check()
  await expectNoSeriousA11yViolations(gil.page)
  await folder.getByRole("button", { name: "Salvar lista" }).click()
  await expect(folder).toBeHidden()
  await gil.page.goto("/")
  const home = gil.page.getByRole("complementary", {
    name: "Conversas e contatos",
  })
  await home
    .getByRole("group", { name: "Filtrar conversas" })
    .getByRole("button", { name: "Trabalho" })
    .click()
  await expect(
    home.getByRole("link", { name: new RegExp(hana.name) })
  ).toBeVisible()

  await gil.context.close()
  await hana.context.close()
})
