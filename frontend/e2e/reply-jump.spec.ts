import type { Page } from "@playwright/test"

import { expect, test } from "./fixtures"
import { RUN, twoFriends } from "./support"

/** Cabeçalho do CSRF para chamar a API com a sessão da página. */
async function apiHeaders(page: Page) {
  const xsrf = (await page.context().cookies()).find(
    (cookie) => cookie.name === "XSRF-TOKEN"
  )!.value
  return { "X-XSRF-TOKEN": decodeURIComponent(xsrf) }
}

/**
 * MS-04: "Ir para a mensagem respondida" com a original longe no histórico (fora das páginas carregadas). As
 * mensagens do meio têm alturas bem diferentes da estimativa da lista (content-visibility), e a original precisa
 * terminar na área visível, não só ser carregada.
 */
test("salto para a mensagem respondida longe no histórico", async ({
  browser,
  pageErrors,
}) => {
  // Semeia 160 mensagens, carrega quatro páginas antigas e rola a janela quatro vezes: sob carga passa dos 90 s
  // de test.slow().
  test.setTimeout(300_000)
  const tag = `${RUN}${test.info().project.name.startsWith("mobile") ? "m" : "d"}j`
  const { gil, hana } = await twoFriends(browser, pageErrors, tag, "Salto")
  const conversationId = new URL(gil.page.url()).pathname.split("/").pop()!

  const chat = gil.page.url()
  // Fora da conversa enquanto semeia: o tempo real não desenha 160 mensagens no caminho.
  await gil.page.goto("/settings/profile")
  const headers = new Map<Page, Record<string, string>>()
  for (const page of [gil.page, hana.page]) {
    headers.set(page, await apiHeaders(page))
  }
  const post = async (page: Page, body: string, replyToId?: string) => {
    const response = await page.request.post(
      `/api/v1/conversations/${conversationId}/messages`,
      {
        headers: headers.get(page),
        data: {
          clientMessageId: crypto.randomUUID(),
          body,
          ...(replyToId ? { replyToId } : {}),
        },
      }
    )
    expect(response.status()).toBeLessThan(300)
    return (await response.json()) as { id: string }
  }
  // A original fica no meio do histórico (sem a rolagem parar no topo da lista por acaso).
  await Promise.all(
    Array.from({ length: 40 }, (_, index) => post(gil.page, `antes ${index}`))
  )
  const original = await post(gil.page, "ORIGINAL-ALVO-DO-SALTO")
  // Linhas curtas: bem mais baixas que a estimativa da lista para itens ainda não desenhados.
  for (let batch = 0; batch < 160; batch += 20) {
    await Promise.all(
      Array.from({ length: 20 }, (_, index) =>
        post(gil.page, `enchimento ${batch + index}`)
      )
    )
  }
  await post(hana.page, "RESPOSTA-COM-SALTO", original.id)

  await gil.page.goto(chat)
  const reply = gil.page
    .locator('[data-slot="message"]')
    .filter({ has: gil.page.locator("p", { hasText: "RESPOSTA-COM-SALTO" }) })
  await expect(reply).toBeVisible()
  const target = gil.page.locator('[data-slot="message"]').filter({
    has: gil.page.locator("p", { hasText: "ORIGINAL-ALVO-DO-SALTO" }),
  })
  await expect(target).toHaveCount(0)
  await reply
    .getByRole("button", { name: /Ir para a mensagem respondida/ })
    .click()
  // Carrega as páginas antigas uma a uma antes de rolar: sob carga passa dos 5 s padrão.
  await expect(target).toBeInViewport({ timeout: 30_000 })
  // A original continua na tela depois de a rolagem assentar (as alturas reais aparecem no caminho).
  await gil.page.waitForTimeout(1_500)
  await expect(target).toBeInViewport()

  // UI-04 (A31): das 202 mensagens carregadas, a lista desenha só a janela em volta da original.
  const rendered = gil.page.locator('[data-slot="message"]')
  expect(await rendered.count()).toBeLessThanOrEqual(150)
  await expect(reply).toHaveCount(0)
  // Rolar traz de volta, pelas bordas, as mensagens que saíram da janela, e a lista não salta quando linhas entram
  // ou saem acima da área visível. Sem overflow-anchor (como no WebKit), a compensação é a do próprio app; com ele
  // (Chromium), as duas não podem se somar.
  const viewport = gil.page.locator('[data-slot="message-scroller-viewport"]')
  /** Mensagem no meio da área visível (ou a pedida): posição na tela e quantas estão desenhadas. */
  const probe = (id: string | null) =>
    viewport.evaluate((element, wanted) => {
      const area = element.getBoundingClientRect()
      const middle = area.top + area.height / 2
      const item = [
        ...element.querySelectorAll<HTMLElement>("[data-message-id]"),
      ].find((candidate) => {
        const box = candidate.getBoundingClientRect()
        return wanted
          ? candidate.dataset.messageId === wanted
          : box.top <= middle && box.bottom >= middle
      })
      return {
        id: item?.dataset.messageId ?? null,
        top: item?.getBoundingClientRect().top ?? null,
        count: element.querySelectorAll('[data-slot="message"]').length,
      }
    }, id)
  const scrollInSteps = async (
    step: number,
    anchoring: "auto" | "none",
    until: typeof reply
  ) => {
    await viewport.evaluate((element, value) => {
      element.style.overflowAnchor = value
    }, anchoring)
    for (let index = 0; index < 120 && (await until.count()) === 0; index++) {
      const before = await probe(null)
      const moved = await viewport.evaluate((element, delta) => {
        const previous = element.scrollTop
        element.scrollTop += delta
        return element.scrollTop - previous
      }, step)
      await gil.page.waitForTimeout(250)
      const after = await probe(before.id)
      // A janela não cresce sem limite: revela 50 de cada vez e corta o outro lado.
      expect(after.count).toBeLessThanOrEqual(200)
      // A borda revela as próximas antes de a rolagem chegar à ponta da janela (sem "fim" falso no caminho).
      expect(moved).toBe(step)
      // Para cima sem ancoragem (como no WebKit), as linhas ainda não desenhadas acima trocam a altura estimada do
      // content-visibility (10rem) pela real ao chegar perto da tela e puxam a lista (medido: até ~1800 px por
      // passo, o mesmo de antes da janela com as páginas antigas). O que se prova ali é que as 50 linhas
      // reveladas acima (~8200 px estimados) não empurram a tela.
      const tolerance = step < 0 && anchoring === "none" ? 4_000 : 40
      // A linha que estava na tela não sai da janela (o corte só tira o que está longe).
      expect(before.id === null || after.top !== null).toBe(true)
      if (before.top !== null && after.top !== null) {
        expect(Math.abs(after.top - (before.top - moved))).toBeLessThan(
          tolerance
        )
      }
    }
    await expect(until).toBeVisible()
  }
  await scrollInSteps(250, "none", reply)
  for (const index of [0, 80, 159]) {
    await expect(
      gil.page
        .locator('[data-slot="message"]')
        .filter({ hasText: `enchimento ${index}` })
    ).toHaveCount(index < 60 ? 0 : 1)
  }
  // "Ir para as mensagens mais recentes" depois de outro salto volta ao fim do histórico, não ao fim da janela.
  await reply
    .getByRole("button", { name: /Ir para a mensagem respondida/ })
    .click()
  await expect(target).toBeInViewport({ timeout: 30_000 })
  await expect(reply).toHaveCount(0)
  await gil.page
    .getByRole("button", { name: "Ir para as mensagens mais recentes" })
    .click()
  await expect(reply).toBeInViewport({ timeout: 10_000 })
  expect(await rendered.count()).toBeLessThanOrEqual(150)
  await expect(target).toHaveCount(0)
  // Para cima até a original e de volta, com e sem a ancoragem do navegador.
  await scrollInSteps(-250, "none", target)
  await scrollInSteps(250, "auto", reply)
  await scrollInSteps(-250, "auto", target)

  await gil.context.close()
  await hana.context.close()
})
