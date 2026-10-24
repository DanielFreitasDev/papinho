import type { Locator, Page } from "@playwright/test"

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

/** Bolha que pisca depois do salto (index.css, [data-flash]). */
function flashing(message: Locator) {
  return message.locator('[data-slot="bubble"][data-flash]')
}

/**
 * Destaque da busca (GR-06): o termo fica no marca-texto, sem diferenciar acentos e maiúsculas e pelo radical (como a
 * busca do servidor), nas mensagens e nos resultados; anterior e próximo com contador; o resultado aberto leva à
 * mensagem, que pisca, com a busca da conversa preenchida e o termo fora da URL.
 */
test("destaca o termo buscado nas mensagens e nos resultados", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}h`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Destaque")
  for (const text of [
    "A Ação de amanhã está confirmada",
    "Reunião às 10h",
    "Reuniões semanais começam na segunda",
    `<img src=x onerror="alert(1)"> sem ação nenhuma`,
  ]) {
    await send(gil.page, text)
  }
  await expect(row(hana.page, "Reuniões semanais")).toBeVisible()

  // Na conversa: "acao" marca "Ação" (e o texto que parecia HTML continua texto).
  await hana.page.getByRole("button", { name: "Buscar nesta conversa" }).click()
  const inside = hana.page.getByRole("search", {
    name: "Buscar nesta conversa",
  })
  const box = inside.getByRole("searchbox")
  await box.fill("acao")
  await expect(inside.getByRole("status")).toHaveText("1 de 2")
  await expect(
    row(hana.page, "confirmada").locator("mark[data-search-match]")
  ).toHaveText("Ação")
  const hostile = row(hana.page, "onerror")
  await expect(hostile.locator("mark[data-search-match]")).toHaveText("ação")
  await expect(hostile.locator("img")).toHaveCount(0)
  // O marca-texto passa no contraste do axe nos dois temas.
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.emulateMedia({ colorScheme: "dark" })
  await expectNoSeriousA11yViolations(hana.page)
  await hana.page.emulateMedia({ colorScheme: "light" })

  // Plural e radical como no servidor: "reunião" marca "Reunião" e "Reuniões"; anterior/próximo com contador.
  await box.fill("reunião")
  await expect(inside.getByRole("status")).toHaveText("1 de 2")
  await expect(
    row(hana.page, "Reuniões semanais").locator("mark[data-search-match]")
  ).toHaveText("Reuniões")
  await expect(
    row(hana.page, "Reunião às 10h").locator("mark[data-search-match]")
  ).toHaveText("Reunião")
  await inside.getByRole("button", { name: "Resultado mais antigo" }).click()
  await expect(inside.getByRole("status")).toHaveText("2 de 2")
  await expect(flashing(row(hana.page, "Reunião às 10h"))).toBeVisible()
  await inside.getByRole("button", { name: "Resultado mais recente" }).click()
  await expect(inside.getByRole("status")).toHaveText("1 de 2")
  await expect(flashing(row(hana.page, "Reuniões semanais"))).toBeVisible()

  // Com movimento reduzido, a bolha não anima (contorno parado).
  await hana.page.emulateMedia({ reducedMotion: "reduce" })
  await inside.getByRole("button", { name: "Resultado mais antigo" }).click()
  const still = flashing(row(hana.page, "Reunião às 10h"))
  await expect(still).toBeVisible()
  expect(
    await still
      .locator('[data-slot="bubble-content"]')
      .evaluate((element) => getComputedStyle(element, "::after").animationName)
  ).toBe("none")
  await hana.page.emulateMedia({ reducedMotion: "no-preference" })
  await box.press("Escape")
  await expect(inside).toBeHidden()
  await expect(hana.page.locator("mark[data-search-match]")).toHaveCount(0)

  // Busca global: o resultado mostra o termo marcado; abrir leva à mensagem, que pisca, com a busca preenchida.
  const list = await showList(hana.page)
  await list
    .getByRole("searchbox", { name: "Buscar conversas, contatos e mensagens" })
    .fill("ACAO")
  const result = list
    .getByRole("region", { name: "Mensagens" })
    .getByRole("link", { name: /confirmada/ })
  await expect(result.locator("mark[data-search-match]")).toHaveText("Ação")
  await expectNoSeriousA11yViolations(hana.page)
  await result.click()
  await expect(flashing(row(hana.page, "confirmada"))).toBeVisible()
  await expect(
    hana.page
      .getByRole("search", { name: "Buscar nesta conversa" })
      .getByRole("searchbox")
  ).toHaveValue("ACAO")
  await expect(
    row(hana.page, "confirmada").locator("mark[data-search-match]")
  ).toHaveText("Ação")
  expect(hana.page.url()).not.toMatch(/acao/i)

  await Promise.all([gil.context.close(), hana.context.close()])
})
