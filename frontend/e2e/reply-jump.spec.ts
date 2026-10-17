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
  // Semeia 160 mensagens e carrega quatro páginas antigas: sob carga passa dos 90 s de test.slow().
  test.setTimeout(180_000)
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

  await gil.context.close()
  await hana.context.close()
})
