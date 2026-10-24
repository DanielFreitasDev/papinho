import type { Page } from "@playwright/test"

import { expect, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

function row(page: Page, text: string) {
  return page
    .locator('[data-slot="message"]')
    .filter({ has: page.locator("p", { hasText: text }) })
}

/** Linha da lista de mensagens (o item que recebe a animação de entrada). */
function item(page: Page, text: string) {
  return page
    .locator("[data-message-id]")
    .filter({ has: page.locator("p", { hasText: text }) })
}

/** Pixels pintados na onda da gravação (canal alfa somado). */
function waveInk(page: Page) {
  return page
    .getByRole("group", { name: "Gravando mensagem de voz" })
    .locator("canvas")
    .evaluate((canvas: HTMLCanvasElement) => {
      const data = canvas
        .getContext("2d")!
        .getImageData(0, 0, canvas.width, canvas.height).data
      let ink = 0
      for (let index = 3; index < data.length; index += 4) {
        ink += data[index]
      }
      return ink
    })
}

/**
 * Fase 4 (animações): a gravação de voz como no WhatsApp (onda ao vivo, lixeira ao descartar, microfone e enviar no
 * mesmo lugar), mensagens que entram só quando chegam ao vivo e nada disso com movimento reduzido. O foco volta ao
 * microfone e o leitor de tela ouve o descarte.
 */
test("gravação animada, troca microfone/enviar e entrada das mensagens", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    `${RUN}${mobile ? "m" : "d"}r`,
    "Movimento"
  )
  const page = gil.page
  const mic = page.getByRole("button", { name: "Gravar mensagem de voz" })
  const text = page.getByRole("textbox", { name: "Mensagem" })

  // Enviar ocupa o lugar redondo do microfone e chega com o "pop".
  const micBox = (await mic.boundingBox())!
  await text.fill("Oi")
  const send = page.getByRole("button", { name: "Enviar" })
  await expect(send).toHaveClass(/animate-pop/)
  await expect(mic).toHaveCount(0)
  // Medido depois do "pop": durante a escala, a caixa é menor e deslocada.
  await send.evaluate((element) =>
    Promise.all(element.getAnimations().map((animation) => animation.finished))
  )
  const sendBox = (await send.boundingBox())!
  expect(Math.abs(sendBox.x - micBox.x)).toBeLessThan(1)
  expect(Math.abs(sendBox.width - micBox.width)).toBeLessThan(1)
  await text.fill("")
  await expect(mic).toBeVisible()

  // Gravando: a onda pinta o volume ao vivo; descartar joga o microfone na lixeira e devolve o campo e o foco.
  await mic.click()
  const recorder = page.getByRole("group", { name: "Gravando mensagem de voz" })
  await expect(recorder).toBeVisible()
  await expect.poll(() => waveInk(page)).toBeGreaterThan(0)
  await recorder.getByRole("button", { name: "Cancelar gravação" }).click()
  await expect(page.locator("[data-trash]")).toBeVisible()
  await expect(
    page.getByRole("status").filter({ hasText: "Gravação descartada" })
  ).toHaveCount(1)
  await expect(mic).toBeVisible()
  await expect(page.locator("[data-trash]")).toHaveCount(0)
  await expect(mic).toBeFocused()
  await expect(text).toBeVisible()

  // Mensagem que chega ao vivo entra animada, do lado dela; a própria sobe do campo. As animações ficam 10 vezes mais
  // lentas (CDP do Chromium) para o atributo, que sai no fim da animação, ser visto mesmo com a máquina ocupada.
  const timeline = await page.context().newCDPSession(page)
  await timeline.send("Animation.enable")
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 0.1 })
  await hana.page
    .getByRole("textbox", { name: "Mensagem" })
    .fill("Chegou agora!")
  await hana.page.getByRole("button", { name: "Enviar" }).click()
  await expect(item(page, "Chegou agora!")).toHaveAttribute(
    "data-enter",
    "in-start"
  )
  await text.fill("Indo!")
  await send.click()
  await expect(item(page, "Indo!")).toHaveAttribute("data-enter", "sent")
  await timeline.send("Animation.setPlaybackRate", { playbackRate: 1 })
  // Ao terminar, o atributo sai; a confirmação da bolha não anima de novo (a linha não remonta).
  await expect(item(page, "Indo!")).not.toHaveAttribute("data-enter")
  await expect(
    row(page, "Indo!").getByRole("img", { name: "Lida" })
  ).toBeVisible()
  await expect(item(page, "Indo!")).not.toHaveAttribute("data-enter")

  // Reabrir a conversa não anima o histórico.
  await page.reload()
  await expect(item(page, "Chegou agora!")).toBeVisible()
  await expect(page.locator("[data-enter]")).toHaveCount(0)

  // Toque (celular): segurar grava; deslizar para a esquerda descarta, para cima trava.
  if (mobile) {
    const touch = await page.context().newCDPSession(page)
    const press = async (moves: { x: number; y: number }[]) => {
      const box = (await mic.boundingBox())!
      const start = { x: box.x + box.width / 2, y: box.y + box.height / 2 }
      await touch.send("Input.dispatchTouchEvent", {
        type: "touchStart",
        touchPoints: [start],
      })
      await expect(recorder).toBeVisible()
      await expect(recorder.getByText("Deslize para cancelar")).toBeVisible()
      for (const move of moves) {
        await touch.send("Input.dispatchTouchEvent", {
          type: "touchMove",
          touchPoints: [{ x: start.x + move.x, y: start.y + move.y }],
        })
      }
      await touch.send("Input.dispatchTouchEvent", {
        type: "touchEnd",
        touchPoints: [],
      })
    }
    await press([
      { x: -60, y: 0 },
      { x: -150, y: 0 },
    ])
    await expect(page.locator("[data-trash]")).toBeVisible()
    await expect(mic).toBeVisible()
    await press([
      { x: 0, y: -40 },
      { x: 0, y: -90 },
    ])
    await expect(
      recorder.getByRole("img", { name: "Gravação travada" })
    ).toBeVisible()
    await recorder.getByRole("button", { name: "Cancelar gravação" }).click()
    await expect(mic).toBeVisible()
  }

  // Movimento reduzido: descartar volta direto ao microfone (sem lixeira) e as animações vão direto ao fim.
  await page.emulateMedia({ reducedMotion: "reduce" })
  await mic.click()
  await expect(recorder).toBeVisible()
  await recorder.getByRole("button", { name: "Cancelar gravação" }).click()
  await expect(mic).toBeVisible()
  await expect(page.locator("[data-trash]")).toHaveCount(0)
  await text.fill("Sem movimento")
  await expect(send).toHaveCSS("animation-duration", "0.001s")
})
