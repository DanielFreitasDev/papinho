import { expect, test } from "./fixtures"
import { ADMIN, PASSWORD, RUN } from "./support"

const TOTAL = Number(process.env.PAPINHO_E2E_PERF_MESSAGES ?? 2000)

/**
 * Medição da lista de mensagens com milhares de itens (UI-04). Opcional e lento (semeia pela API): roda só com
 * PAPINHO_E2E_PERF=1, contra uma instância com PAPINHO_RATE_LIMIT_MULTIPLIER alto. Mede abrir a conversa, subir
 * até a primeira mensagem carregando as páginas antigas, as tarefas longas do navegador no caminho e o tamanho do
 * DOM e do heap no fim, com limites para cada um (A31, A86).
 */
test("lista com milhares de mensagens", async ({ page }) => {
  test.skip(!process.env.PAPINHO_E2E_PERF, "só com PAPINHO_E2E_PERF=1")
  test.skip(test.info().project.name.startsWith("mobile"), "uma medição basta")
  test.setTimeout(15 * 60_000)

  // Semeia pela API com a sessão da página: "Mensagens salvas" do admin, em lotes paralelos.
  await page.goto("/login")
  await page.getByLabel("@usuario").fill(ADMIN)
  await page.getByLabel("Senha", { exact: true }).fill(PASSWORD)
  await page.getByRole("button", { name: "Entrar", exact: true }).click()
  await expect(
    page.getByRole("complementary", { name: "Conversas e contatos" })
  ).toBeVisible()
  const xsrf = (await page.context().cookies()).find(
    (cookie) => cookie.name === "XSRF-TOKEN"
  )!.value
  const headers = { "X-XSRF-TOKEN": decodeURIComponent(xsrf) }
  const saved = await (
    await page.request.post("/api/v1/conversations/saved", { headers })
  ).json()
  const started = Date.now()
  // Respeita o limite de envio (SG-03): 429 espera e tenta de novo com o mesmo clientMessageId.
  const post = async (index: number) => {
    const clientMessageId = crypto.randomUUID()
    for (;;) {
      const response = await page.request.post(
        `/api/v1/conversations/${saved.id}/messages`,
        {
          headers,
          data: {
            clientMessageId,
            body: `perf ${RUN} #${index} *negrito* e um texto médio para ocupar a linha`,
          },
        }
      )
      if (response.status() !== 429) {
        expect(response.status()).toBeLessThan(300)
        return
      }
      await new Promise((resolve) => setTimeout(resolve, 1_000))
    }
  }
  for (let batch = 0; batch < TOTAL; batch += 25) {
    await Promise.all(
      Array.from({ length: Math.min(25, TOTAL - batch) }, (_, index) =>
        post(batch + index)
      )
    )
  }
  console.log(`semeadas ${TOTAL} mensagens em ${Date.now() - started} ms`)

  // Tarefas longas (> 50 ms) do navegador durante a abertura e a rolagem.
  await page.addInitScript(() => {
    const longTasks: number[] = []
    ;(window as unknown as { longTasks: number[] }).longTasks = longTasks
    new PerformanceObserver((list) =>
      list.getEntries().forEach((entry) => longTasks.push(entry.duration))
    ).observe({ type: "longtask", buffered: true })
  })
  const opening = Date.now()
  await page.goto(`/c/${saved.id}`)
  await expect(
    page.locator('[data-slot="message"]').filter({ hasText: `#${TOTAL - 1} ` })
  ).toBeVisible()
  const openMs = Date.now() - opening

  const viewport = page.locator('[data-slot="message-scroller-viewport"]')
  const scrolling = Date.now()
  let pages = 0
  while (
    (await page
      .locator('[data-slot="message"]')
      .filter({ hasText: `perf ${RUN} #0 ` })
      .count()) === 0 &&
    pages < 200
  ) {
    await viewport.evaluate((element) => (element.scrollTop = 0))
    await page.waitForTimeout(150)
    pages++
  }
  const scrollMs = Date.now() - scrolling
  const result = await page.evaluate(() => ({
    messages: document.querySelectorAll('[data-slot="message"]').length,
    nodes: document.getElementsByTagName("*").length,
    longTasks: (window as unknown as { longTasks: number[] }).longTasks,
    // Só o Chromium expõe o heap do JS (performance.memory).
    heapMB: Math.round(
      ((
        performance as unknown as {
          memory?: { usedJSHeapSize: number }
        }
      ).memory?.usedJSHeapSize ?? 0) /
        2 ** 20
    ),
  }))
  const longest = Math.max(0, ...result.longTasks)
  console.log(
    JSON.stringify({
      total: TOTAL,
      openMs,
      scrollToFirstMs: scrollMs,
      scrollSteps: pages,
      messagesInDom: result.messages,
      domNodes: result.nodes,
      heapMB: result.heapMB,
      longTasks: result.longTasks.length,
      longestTaskMs: Math.round(longest),
    })
  )
  await expect(
    page.locator('[data-slot="message"]').filter({ hasText: `perf ${RUN} #0 ` })
  ).toHaveCount(1)
  expect(openMs).toBeLessThan(5_000)
  // A31: o DOM não cresce com o histórico (a lista desenha só uma janela) e nenhuma página trava a rolagem.
  expect(result.messages).toBeLessThanOrEqual(200)
  expect(result.nodes).toBeLessThan(8_000)
  expect(longest).toBeLessThan(500)
  expect(scrollMs).toBeLessThan(TOTAL * 15)
  expect(result.heapMB).toBeLessThan(300)

  // Voltar ao fim traz as mais novas de volta, com a janela ainda limitada.
  await page
    .getByRole("button", { name: "Ir para as mensagens mais recentes" })
    .click()
  await expect(
    page.locator('[data-slot="message"]').filter({ hasText: `#${TOTAL - 1} ` })
  ).toBeInViewport({ timeout: 10_000 })
  expect(
    await page.locator('[data-slot="message"]').count()
  ).toBeLessThanOrEqual(200)
})
