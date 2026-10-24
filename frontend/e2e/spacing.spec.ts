import type { Page } from "@playwright/test"

import { expect, test } from "./fixtures"
import { RUN, twoFriends } from "./support"
import { auditSpacing } from "./spacing-audit"

/** Sobrenome comprido de propósito: nomes longos eram o que mais empurrava texto contra as bordas. */
const LONG = "Albuquerque de Souza Figueiredo Cavalcanti Neto"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

async function send(page: Page, text: string) {
  await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(
    row(page, text.slice(0, 20)).getByRole("img", {
      name: /^(Enviada|Entregue|Lida)$/,
    })
  ).toBeVisible()
}

async function expectRoomy(page: Page, screen: string) {
  // Animação de abertura terminada: o tamanho medido é o final.
  await page.waitForTimeout(300)
  expect(await auditSpacing(page), screen).toEqual([])
}

/** Centro vertical do ícone de cada botão. */
async function iconCenter(page: Page, name: string) {
  const box = await page
    .getByRole("button", { name, exact: true })
    .locator("svg")
    .boundingBox()
  return box!.y + box!.height / 2
}

/**
 * Regra de espaçamento: nenhum texto ou ícone encosta (ou quase) na borda do menu, popover, diálogo, painel, botão ou
 * da tela, com nomes longos; os ícones do campo de mensagem e o microfone ficam na mesma linha; todo item de menu tem a
 * mesma distância entre ícone e texto.
 */
test("nada encosta nas bordas, com nomes longos", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const { gil } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}${mobile ? "m" : "d"}s`,
    LONG
  )
  const peer = `Hana ${LONG}`
  await send(
    gil.page,
    "Oi! Confirmo a reunião do condomínio na quinta; preciso da lista de presença até amanhã de manhã."
  )
  await send(
    gil.page,
    "https://exemplo.com.br/condominio/assembleias/2026/ata-da-reuniao-extraordinaria-de-outubro.pdf"
  )
  await expectRoomy(gil.page, "conversa")

  // Campo de mensagem: ícones de dentro e o microfone alinhados; o microfone longe da borda.
  const mic = await iconCenter(gil.page, "Gravar mensagem de voz")
  for (const name of ["Anexar arquivo", "Emojis", "Nova enquete"]) {
    expect(
      Math.abs((await iconCenter(gil.page, name)) - mic),
      name
    ).toBeLessThan(1)
  }
  const micBox = await gil.page
    .getByRole("button", { name: "Gravar mensagem de voz" })
    .boundingBox()
  const width = gil.page.viewportSize()!.width
  expect(width - (micBox!.x + micBox!.width)).toBeGreaterThanOrEqual(16)

  const message = row(gil.page, "Confirmo a reunião")
  await message.hover()
  await message.getByRole("button", { name: "Ações da mensagem" }).click()
  await expect(
    gil.page.getByRole("menuitem", { name: "Responder" })
  ).toBeVisible()
  await expectRoomy(gil.page, "menu da mensagem")
  await gil.page.keyboard.press("Escape")

  await gil.page.getByRole("button", { name: "Emojis" }).click()
  await expect(
    gil.page.getByRole("tablist", { name: "Categorias de emoji" })
  ).toBeVisible()
  await expectRoomy(gil.page, "emojis")
  await gil.page.keyboard.press("Escape")

  await gil.page.getByRole("button", { name: /^Dados do contato/ }).click()
  await expect(gil.page.getByText(`Denunciar ${peer}`)).toBeVisible()
  await expectRoomy(gil.page, "painel do contato")
  await gil.page.keyboard.press("Escape")

  // Menu principal: largura própria (antes era a do botão) e a mesma distância entre ícone e texto em todo item.
  if (mobile) {
    await gil.page.getByRole("link", { name: "Voltar" }).click()
  }
  await gil.page.getByRole("button", { name: "Menu" }).click()
  await expect(
    gil.page.getByRole("menuitem", { name: "Configurações" })
  ).toBeVisible()
  await expectRoomy(gil.page, "menu principal")
  const gaps = await gil.page
    .locator('[data-slot="dropdown-menu-item"]')
    .evaluateAll((items) =>
      items.map((item) => {
        const icon = item.querySelector("svg")!.getBoundingClientRect()
        const range = document.createRange()
        range.selectNodeContents(item)
        const text = [...range.getClientRects()].find(
          (rect) => rect.left > icon.right
        )!
        return Math.round((text.left - icon.right) * 2) / 2
      })
    )
  expect(new Set(gaps).size, `distâncias: ${gaps.join(", ")}`).toBe(1)
  await gil.page.getByRole("menuitem", { name: "Novo grupo" }).click()
  const group = gil.page.getByRole("dialog", { name: "Novo grupo" })
  await expect(
    group.getByRole("checkbox", { name: new RegExp(peer) })
  ).toBeVisible()
  await expectRoomy(gil.page, "novo grupo")
  await gil.page.keyboard.press("Escape")

  // Configurações: a seção aberta à vista na faixa de abas e os cartões com a margem da tela.
  await gil.page.goto("/settings/privacy")
  await expect(
    gil.page.getByRole("heading", { name: "Quem pode ver" })
  ).toBeVisible()
  await expect(
    gil.page.getByRole("link", { name: "Privacidade" })
  ).toBeInViewport({
    ratio: 1,
  })
  await expectRoomy(gil.page, "configurações")
})
