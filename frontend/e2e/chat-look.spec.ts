import type { Page } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { openMenuItem, pngFile, RUN, twoFriends } from "./support"

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

async function showList(page: Page) {
  const back = page.getByRole("link", { name: "Voltar" })
  if (await back.isVisible()) {
    await back.click()
  }
  return page.getByRole("complementary", { name: "Conversas e contatos" })
}

/**
 * Pedidos do dono de 2026-10-27, como no WhatsApp: o microfone dentro da caixa de texto com a bola que acende, o
 * texto alinhado aos ícones, a mensagem que voa do campo até a conversa, reações sem corte e a onda de toque.
 */
test("campo de mensagem, voo da mensagem enviada, reações e onda de toque", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}${mobile ? "m" : "d"}w`,
    "Visual"
  )
  const page = gil.page

  // O microfone mora dentro da pílula do campo, e o texto fica no meio da linha dos ícones.
  const pill = page.locator("[data-composer]")
  const mic = pill.getByRole("button", { name: "Gravar mensagem de voz" })
  await expect(mic).toBeVisible()
  const centers = await page.evaluate(() => {
    const field = document.querySelector<HTMLTextAreaElement>(
      "[data-composer] textarea"
    )!
    const style = getComputedStyle(field)
    const box = field.getBoundingClientRect()
    const line =
      box.top +
      Number.parseFloat(style.paddingTop) +
      Number.parseFloat(style.lineHeight) / 2
    const icon = document
      .querySelector(
        '[data-composer] button[aria-label="Gravar mensagem de voz"] svg'
      )!
      .getBoundingClientRect()
    return { line, icon: icon.top + icon.height / 2 }
  })
  expect(Math.abs(centers.line - centers.icon)).toBeLessThan(1)
  if (!mobile) {
    // Com o mouse em cima, a bola acende atrás do microfone.
    await mic.hover()
    await expect
      .poll(() =>
        mic.evaluate((element) => getComputedStyle(element, "::before").opacity)
      )
      .toBe("1")
  }

  // A mensagem enviada voa do campo até a conversa: uma cópia presa à janela, que some quando chega.
  const timeline = await page.context().newCDPSession(page)
  await timeline.send("Animation.enable")
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 0.1 })
  await page.getByRole("textbox", { name: "Mensagem" }).fill("Voando!")
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(page.locator("[data-flying]")).toHaveCount(1)
  await expect(
    page.locator("[data-message-id]").filter({ hasText: "Voando!" })
  ).toHaveAttribute("data-flight", "")
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 1 })
  await expect(page.locator("[data-flying]")).toHaveCount(0)
  await expect(
    row(page, "Voando!").getByRole("img", { name: /^(Enviada|Entregue|Lida)$/ })
  ).toBeVisible()

  // Reação: a pílula fica inteira, dentro da caixa da própria linha (cada linha recorta o que passa dela).
  await send(page, "Reage aqui")
  const target = row(hana.page, "Reage aqui")
  await expect(hana.page.getByRole("menu")).toHaveCount(0)
  await target.hover()
  await target.getByRole("button", { name: "Ações da mensagem" }).click()
  await hana.page.getByRole("menuitem", { name: "Reagir com 👍" }).click()
  const pills = row(page, "Reage aqui").locator(
    '[data-slot="bubble-reactions"]'
  )
  await expect(pills).toContainText("👍")
  const clipped = await pills.evaluate((element) => {
    const pill = element.getBoundingClientRect()
    const item = element.closest("[data-message-id]")!.getBoundingClientRect()
    const hit = document.elementFromPoint(
      pill.left + pill.width / 2,
      pill.bottom - 2
    )
    return { inside: pill.bottom <= item.bottom, onTop: element.contains(hit) }
  })
  expect(clipped).toEqual({ inside: true, onTop: true })

  // Onda de toque: tocar na conversa da lista acende o círculo a partir do ponto tocado. A animação (360 ms) fica 10
  // vezes mais lenta para o atributo, que sai no fim dela, ser visto mesmo com a máquina ocupada.
  const list = await showList(page)
  const link = list.getByRole("link", { name: new RegExp(hana.name) })
  await expect(link).toBeVisible()
  // No celular, voltar para a lista desliza a tela (view transition), que recebe o toque até acabar.
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          document
            .getAnimations()
            .filter((animation) =>
              (
                animation.effect as KeyframeEffect | null
              )?.pseudoElement?.startsWith("::view-transition")
            ).length
      )
    )
    .toBe(0)
  const box = (await link.boundingBox())!
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 0.1 })
  await page.mouse.move(box.x + 40, box.y + box.height / 2)
  await page.mouse.down()
  await expect(link).toHaveAttribute("data-rippling", "")
  await page.mouse.up()
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 1 })
  await expectNoSeriousA11yViolations(page)

  await gil.context.close()
  await hana.context.close()
})

/** No grupo, a foto de quem mandou fica ao lado da primeira bolha da sequência, com o nome colorido e o rabinho. */
test("foto e nome de quem mandou no grupo", async ({ browser, pageErrors }) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const tag = `${RUN}${mobile ? "m" : "d"}a`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Avatar")
  const groupName = `Fotos ${tag}`
  await gil.page.goto("/")
  await openMenuItem(gil.page, "Novo grupo")
  const dialog = gil.page.getByRole("dialog", { name: "Novo grupo" })
  await dialog.getByLabel("Nome do grupo").fill(groupName)
  await dialog.getByRole("checkbox", { name: /Hana Avatar/ }).check()
  await dialog.getByRole("button", { name: "Criar grupo" }).click()
  await expect(gil.page.getByRole("heading", { name: groupName })).toBeVisible()

  await hana.page.goto("/")
  await hana.page.getByRole("link", { name: new RegExp(groupName) }).click()
  await send(hana.page, "Primeira da Hana")
  await send(hana.page, "Segunda da Hana")

  const first = row(gil.page, "Primeira da Hana")
  const second = row(gil.page, "Segunda da Hana")
  await expect(second).toBeVisible()
  // A primeira da sequência: foto, nome com cor própria e rabinho; a segunda guarda o lugar da foto, sem nome.
  await expect(first.locator('[data-slot="avatar"]')).toHaveCount(1)
  await expect(first.locator("[data-member-color]")).toHaveText("Hana Avatar")
  await expect(first.locator('[data-slot="bubble"]')).toHaveAttribute(
    "data-tail",
    "start"
  )
  await expect(second.locator('[data-slot="avatar"]')).toHaveCount(0)
  await expect(second.locator("[data-member-color]")).toHaveCount(0)
  await expect(second.locator('[data-slot="bubble"]')).not.toHaveAttribute(
    "data-tail"
  )
  const left = async (text: string) =>
    (await row(gil.page, text).locator('[data-slot="bubble"]').boundingBox())!.x
  expect(
    Math.abs((await left("Primeira")) - (await left("Segunda")))
  ).toBeLessThan(1)
  await expectNoSeriousA11yViolations(gil.page)

  await gil.context.close()
  await hana.context.close()
})

/** Aparência: paletas de cor do app inteiro, papéis de parede (cor e imagem própria) e listas criadas da barra. */
test("paletas, papéis de parede e listas", async ({ browser, pageErrors }) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}${mobile ? "m" : "d"}p`,
    "Paleta"
  )
  const page = gil.page
  const html = page.locator("html")
  await expect(html).toHaveAttribute("data-palette", "papinho")

  await page.goto("/settings/appearance")
  // "Oceano" e "Rosa" existem nas duas escolhas: cada uma é um grupo de rádios com nome próprio.
  const palettes = page.getByRole("radiogroup", { name: "Cores do app" })
  const wallpapers = page.getByRole("radiogroup", {
    name: "Papel de parede da conversa",
  })
  await palettes.getByRole("radio", { name: "Oceano" }).click()
  await expect(html).toHaveAttribute("data-palette", "oceano")
  expect(
    await page.evaluate(() =>
      getComputedStyle(document.documentElement)
        .getPropertyValue("--hue")
        .trim()
    )
  ).toBe("250")
  await wallpapers.getByRole("radio", { name: "Menta" }).click()
  await expect(html).toHaveAttribute("data-wallpaper", "color-menta")
  await expectNoSeriousA11yViolations(page)

  // Imagem própria: fica no aparelho e vira o fundo da conversa por uma URL blob:.
  const chooser = page.waitForEvent("filechooser")
  await wallpapers.getByRole("radio", { name: "Sua imagem" }).click()
  await (await chooser).setFiles(await pngFile(page, "#0ea5e9", "ceu.png"))
  await expect(html).toHaveAttribute("data-wallpaper", "image")
  await expect(
    page.getByRole("button", { name: "Remover imagem" })
  ).toBeVisible()

  // Lista criada direto da barra de filtros, com a sugestão de nome; ela já abre.
  await page.goto("/")
  const list = page.getByRole("complementary", { name: "Conversas e contatos" })
  await list.getByRole("button", { name: "Criar lista" }).click()
  const folder = page.getByRole("dialog", { name: "Nova lista" })
  await folder.getByRole("button", { name: "Trabalho" }).click()
  await expect(folder.getByLabel("Nome da lista")).toHaveValue("Trabalho")
  await folder.getByRole("checkbox", { name: hana.name }).check()
  await folder.getByRole("button", { name: "Salvar lista" }).click()
  await expect(folder).toBeHidden()
  await expect(
    list.getByRole("button", { name: "Trabalho", pressed: true })
  ).toBeVisible()
  await expect(
    list.getByRole("link", { name: new RegExp(hana.name) })
  ).toBeVisible()
  await expect(
    list.getByRole("button", { name: "Editar", exact: true })
  ).toBeVisible()

  await page.getByRole("link", { name: new RegExp(hana.name) }).click()
  const wallpaper = page.locator(".chat-wallpaper")
  expect(
    await wallpaper.evaluate(
      (element) => getComputedStyle(element).backgroundImage
    )
  ).toContain("blob:")
  await expectNoSeriousA11yViolations(page)

  await page.goto("/settings/appearance")
  await page.getByRole("button", { name: "Remover imagem" }).click()
  await expect(html).toHaveAttribute("data-wallpaper", "none")
  await page
    .getByRole("radiogroup", { name: "Cores do app" })
    .getByRole("radio", { name: "Papinho" })
    .click()
  await gil.context.close()
  await hana.context.close()
})
