import type { Page } from "@playwright/test"

import { expect, test } from "./fixtures"
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

/** Ponto vazio da linha, longe da bolha e dos atalhos ao lado dela. */
async function besideBubble(page: Page, text: string) {
  const line = (await row(page, text).boundingBox())!
  const bubble = (await row(page, text)
    .locator('[data-slot="bubble"]')
    .boundingBox())!
  const mine = bubble.x + bubble.width / 2 > line.x + line.width / 2
  return {
    x: mine ? line.x + 24 : line.x + line.width - 24,
    y: bubble.y + bubble.height / 2,
  }
}

async function selectedText(page: Page) {
  return page.evaluate(() => window.getSelection()?.toString() ?? "")
}

/**
 * Pedido do dono de 2026-10-28: dois cliques com o botão esquerdo ao lado da mensagem respondem a ela, como no
 * WhatsApp Web, com um efeito que mostra que pegou (a bolha dá um puxão e a seta de responder aparece). Só no mouse.
 */
test("dois cliques ao lado da mensagem respondem", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  test.skip(
    test.info().project.name.startsWith("mobile"),
    "gesto de mouse; no toque, Responder fica no menu ⋮"
  )
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}dr`,
    "Responde"
  )
  const page = gil.page
  const field = page.getByRole("textbox", { name: "Mensagem" })
  await send(hana.page, "Topa um café amanhã?")
  const target = row(page, "Topa um café amanhã?")
  await expect(target).toBeVisible()

  // Em câmera lenta (10%), o puxão e a seta duram ~5 s: dá para conferir os dois no meio do caminho.
  const timeline = await page.context().newCDPSession(page)
  await timeline.send("Animation.enable")
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 0.1 })
  const spot = await besideBubble(page, "Topa um café amanhã?")
  await page.mouse.dblclick(spot.x, spot.y)
  const cue = target.locator("[data-reply-cue]")
  await expect(cue).toBeAttached()
  const bubble = target.locator('[data-slot="bubble"]')
  await expect
    .poll(() =>
      bubble.evaluate((node) => {
        const matrix = new DOMMatrix(getComputedStyle(node).transform)
        return matrix.m41
      })
    )
    .toBeGreaterThan(8)
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 1 })

  // A resposta aparece no campo, que recebe o foco; os dois cliques não selecionaram texto nenhum.
  await expect(page.getByText("Respondendo a Hana Responde")).toBeVisible()
  await expect(field).toBeFocused()
  expect(await selectedText(page)).toBe("")
  await expect(cue).toHaveCount(0)
  await expect
    .poll(() => bubble.evaluate((node) => getComputedStyle(node).transform))
    .toBe("none")

  await field.fill("Topo!")
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(
    row(page, "Topo!").getByRole("button", {
      name: /Ir para a mensagem respondida/,
    })
  ).toContainText("Topa um café amanhã?")

  // Na própria bolha, os dois cliques continuam selecionando a palavra, sem responder.
  await target.locator("p").dblclick()
  expect((await selectedText(page)).trim()).not.toBe("")
  await expect(page.getByText(/^Respondendo a/)).toHaveCount(0)

  // A minha mensagem (à direita) responde pelo espaço da esquerda; com movimento reduzido, sem a animação.
  await page.emulateMedia({ reducedMotion: "reduce" })
  const mine = await besideBubble(page, "Topo!")
  await page.mouse.dblclick(mine.x, mine.y)
  await expect(page.getByText("Respondendo a Você")).toBeVisible()
  await expect(row(page, "Topo!").locator("[data-reply-cue]")).toHaveCount(0)
  await page.keyboard.press("Escape")
  await expect(page.getByText(/^Respondendo a/)).toHaveCount(0)
})
