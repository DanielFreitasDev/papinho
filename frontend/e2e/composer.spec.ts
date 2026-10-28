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

/** Sufixo dos @usuario por projeto (desktop-chromium → "dc"): o mesmo spec roda em vários navegadores. */
function tag(letter: string) {
  const [device, engine] = test.info().project.name.split("-")
  return `${RUN}${device[0]}${engine[0]}${letter}`
}

/** A tecla de comando do sistema que a página vê (⌘ no Mac). */
async function mod(page: Page) {
  return (await page.evaluate(() => /Mac|iPhone|iPad/.test(navigator.platform)))
    ? "Meta"
    : "Control"
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
  browserName,
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
    tag("r"),
    "Responde"
  )
  const page = gil.page
  const field = page.getByRole("textbox", { name: "Mensagem" })
  await send(hana.page, "Topa um café amanhã?")
  const target = row(page, "Topa um café amanhã?")
  await expect(target).toBeVisible()

  // Em câmera lenta (10%, só no Chromium, pelo CDP), o puxão e a seta duram ~5 s: dá para conferir os dois no meio
  // do caminho.
  const bubble = target.locator('[data-slot="bubble"]')
  const cue = target.locator("[data-reply-cue]")
  const spot = await besideBubble(page, "Topa um café amanhã?")
  if (browserName === "chromium") {
    const timeline = await page.context().newCDPSession(page)
    await timeline.send("Animation.enable")
    await timeline.send("Animation.setPlaybackRate", { playbackRate: 0.1 })
    await page.mouse.dblclick(spot.x, spot.y)
    await expect(cue).toBeAttached()
    await expect
      .poll(() =>
        bubble.evaluate((node) => {
          const matrix = new DOMMatrix(getComputedStyle(node).transform)
          return matrix.m41
        })
      )
      .toBeGreaterThan(8)
    await timeline.send("Animation.setPlaybackRate", { playbackRate: 1 })
  } else {
    await page.mouse.dblclick(spot.x, spot.y)
  }

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

/** Seleciona `part` no campo pelo teclado, como a pessoa faria (Home e setas com Shift). */
async function selectInField(page: Page, part: string) {
  const field = page.getByRole("textbox", { name: "Mensagem" })
  const value = await field.inputValue()
  const start = value.indexOf(part)
  await field.press("Home")
  for (let index = 0; index < start; index++) {
    await field.press("ArrowRight")
  }
  for (let index = 0; index < part.length; index++) {
    await field.press("Shift+ArrowRight")
  }
}

/** Onde está, na tela, o meio do trecho `part` do campo (medido no espelho, que tem o mesmo desenho). */
async function pointOf(page: Page, part: string) {
  return page.evaluate((target) => {
    const mirror = document.querySelector<HTMLElement>(
      "[data-composer-mirror]"
    )!
    const walker = document.createTreeWalker(mirror, NodeFilter.SHOW_TEXT)
    const nodes: Text[] = []
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      nodes.push(node as Text)
    }
    const whole = nodes.map((node) => node.data).join("")
    let offset = whole.indexOf(target) + Math.floor(target.length / 2)
    const node = nodes.find((candidate) => {
      if (offset <= candidate.length) {
        return true
      }
      offset -= candidate.length
      return false
    })!
    const range = document.createRange()
    range.setStart(node, offset)
    range.setEnd(node, offset)
    const box = range.getBoundingClientRect()
    return { x: box.left, y: box.top + box.height / 2 }
  }, part)
}

/** Texto desenhado com cada aparência no espelho do campo. */
function mirrorText(page: Page, look: string) {
  return page
    .locator(`[data-composer-mirror] [data-looks~="${look}"]`)
    .allTextContents()
}

/**
 * Pedido do dono de 2026-10-28: formatar no campo como no WhatsApp, com a barra flutuante ao selecionar, as opções no
 * botão direito e os atalhos; o texto aparece formatado enquanto se digita, com os marcadores do WhatsApp.
 */
test("formatação no campo: barra, botão direito, atalhos e texto formatado", async ({
  browser,
  pageErrors,
}) => {
  test.slow()
  const mobile = test.info().project.name.startsWith("mobile")
  const { gil, hana } = await twoFriends(
    browser,
    pageErrors,
    tag("f"),
    "Formata"
  )
  const page = gil.page
  const key = await mod(page)
  const field = page.getByRole("textbox", { name: "Mensagem" })
  const toolbar = page.getByRole("toolbar", { name: "Formatação" })
  await field.fill("olá mundo bonito")
  await expect(toolbar).toBeHidden()

  // Selecionar mostra a barra sobre o texto; o botão aplica e mostra o formato aplicado.
  await selectInField(page, "mundo")
  await expect(toolbar).toBeVisible()
  await expectNoSeriousA11yViolations(page)
  // A barra fica acima da linha selecionada (o texto começa 11 px abaixo da borda do campo), sem cobri-la.
  await toolbar.evaluate((node) =>
    Promise.all(node.getAnimations().map((animation) => animation.finished))
  )
  const fieldBox = (await field.boundingBox())!
  const barBox = (await toolbar.boundingBox())!
  expect(barBox.y + barBox.height).toBeLessThanOrEqual(fieldBox.y + 11)
  await page.screenshot({ path: test.info().outputPath("barra.png") })
  await toolbar.getByRole("button", { name: "Negrito" }).click()
  await expect(field).toHaveValue("olá *mundo* bonito")
  await expect(field).toBeFocused()
  await expect(
    toolbar.getByRole("button", { name: "Negrito" })
  ).toHaveAttribute("aria-pressed", "true")
  expect(await mirrorText(page, "bold")).toEqual(["mundo"])
  await toolbar.getByRole("button", { name: "Itálico" }).click()
  await expect(field).toHaveValue("olá *_mundo_* bonito")
  expect(await mirrorText(page, "italic")).toEqual(["mundo"])

  // O desfazer do navegador volta um passo de cada vez.
  await field.press(`${key}+z`)
  await expect(field).toHaveValue("olá *mundo* bonito")

  if (!mobile) {
    // Atalho do teclado e botão direito.
    await selectInField(page, "bonito")
    await field.press(`${key}+b`)
    await expect(field).toHaveValue("olá *mundo* *bonito*")
    // Botão direito em cima do texto selecionado (fora dele, o navegador move o cursor para o ponto do clique).
    await selectInField(page, "olá")
    const word = await pointOf(page, "olá")
    await page.mouse.click(word.x, word.y, { button: "right" })
    const menu = page.getByRole("menu")
    await expect(
      menu.getByRole("menuitemcheckbox", { name: /Negrito/ })
    ).toBeVisible()
    await expect(menu.getByText(/Shift \+ botão direito/)).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: test.info().outputPath("menu.png") })
    await menu.getByRole("menuitemcheckbox", { name: /Tachado/ }).click()
    await expect(menu).toBeHidden()
    await expect(field).toHaveValue("~olá~ *mundo* *bonito*")
    await expect(field).toBeFocused()

    // Escape esconde a barra sem cancelar nada; a lista entra em todas as linhas.
    await field.fill("primeiro\nsegundo")
    await field.press(`${key}+a`)
    await expect(toolbar).toBeVisible()
    await field.press("Escape")
    await expect(toolbar).toBeHidden()
    await field.press(`${key}+Shift+8`)
    await expect(field).toHaveValue("- primeiro\n- segundo")
    await field.press(`${key}+a`)
    await toolbar.getByRole("button", { name: "Lista numerada" }).click()
    await expect(field).toHaveValue("1. primeiro\n2. segundo")
  }

  // O espelho acompanha o textarea letra por letra: com o negrito, as linhas quebram no mesmo lugar.
  const long = Array.from(
    { length: 12 },
    (_, index) =>
      `*palavra${index}* com texto _longo_ ~para~ \`quebrar\` linhas`
  ).join(" ")
  await field.fill(long)
  const heights = await page.evaluate(() => {
    const textarea = document.querySelector<HTMLTextAreaElement>(
      "[data-composer] textarea"
    )!
    const mirror = document.querySelector<HTMLElement>(
      "[data-composer-mirror]"
    )!
    return { textarea: textarea.scrollHeight, mirror: mirror.scrollHeight }
  })
  expect(Math.abs(heights.textarea - heights.mirror)).toBeLessThanOrEqual(1)
  expect(heights.textarea).toBeGreaterThan(100)
  // E na horizontal: debaixo de cada palavra desenhada no espelho está a mesma letra do textarea (o navegador diz
  // qual letra fica em cada ponto; o espelho não recebe o ponteiro).
  const misplaced = await page.evaluate(() => {
    const textarea = document.querySelector<HTMLTextAreaElement>(
      "[data-composer] textarea"
    )!
    const mirror = document.querySelector<HTMLElement>(
      "[data-composer-mirror]"
    )!
    if (typeof document.caretPositionFromPoint !== "function") {
      return null
    }
    textarea.scrollTop = 0
    mirror.scrollTop = 0
    const walker = document.createTreeWalker(mirror, NodeFilter.SHOW_TEXT)
    const wrong: string[] = []
    let probed = 0
    let offset = 0
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const text = node as Text
      for (const match of text.data.matchAll(/linhas|texto/g)) {
        const range = document.createRange()
        range.setStart(text, match.index + 1)
        range.setEnd(text, match.index + 2)
        const box = range.getBoundingClientRect()
        const area = textarea.getBoundingClientRect()
        if (box.bottom > area.bottom || box.top < area.top) {
          continue
        }
        const caret = document.caretPositionFromPoint(
          box.left + box.width / 2 - 0.5,
          box.top + box.height / 2
        )
        probed++
        const expected = offset + match.index + 1
        if (
          caret?.offsetNode !== textarea ||
          Math.abs(caret.offset - expected) > 1
        ) {
          wrong.push(`${match[0]}@${expected}→${caret?.offset}`)
        }
      }
      offset += text.data.length
    }
    return { wrong, probed }
  })
  if (misplaced) {
    expect(misplaced.wrong).toEqual([])
    expect(misplaced.probed).toBeGreaterThan(5)
  }
  await page.screenshot({ path: test.info().outputPath("espelho.png") })

  // A mensagem chega formatada para a outra pessoa.
  await field.fill("olá *mundo* _lindo_ ~velho~ `código` ||segredo||")
  await page.getByRole("button", { name: "Enviar" }).click()
  const bubble = row(hana.page, "olá")
  await expect(bubble.locator("strong", { hasText: "mundo" })).toBeVisible()
  await expect(bubble.locator("em", { hasText: "lindo" })).toBeVisible()
  await expect(bubble.locator("s", { hasText: "velho" })).toBeVisible()
  await expect(bubble.locator("code", { hasText: "código" })).toBeVisible()
  await Promise.all([gil.context.close(), hana.context.close()])
})
