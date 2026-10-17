import type { Page, Route } from "@playwright/test"

import { expect, expectNoSeriousA11yViolations, test } from "./fixtures"
import { ADMIN, loginThroughUi } from "./support"

/**
 * Estados das telas (UI-03): erro com "Tentar de novo", sem conexão e sem permissão, forçados pelo navegador
 * (a API real não falha sob comando). Só 4xx e rede cortada: 5xx reprovaria o teste pelo console.
 */
test.describe("estados das telas", () => {
  test("erro com tentar de novo, sem conexão e sem permissão", async ({
    page,
  }) => {
    await loginThroughUi(page, ADMIN)
    await expect(
      page.getByRole("complementary", { name: "Conversas e contatos" })
    ).toBeVisible()

    let fail = true
    await page.route("**/api/v1/blocks", (route) =>
      fail
        ? route.fulfill({
            status: 409,
            contentType: "application/problem+json",
            body: JSON.stringify({
              status: 409,
              title: "Conflito",
              detail: "Falha simulada na lista",
            }),
          })
        : route.fallback()
    )
    await page.goto("/settings/blocked")
    const error = page.getByRole("alert").filter({
      hasText: "Falha simulada na lista",
    })
    await expect(error).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    fail = false
    await error.getByRole("button", { name: "Tentar de novo" }).click()
    await expect(page.getByText("Ninguém bloqueado")).toBeVisible()

    await page.route("**/api/v1/sessions", (route) =>
      route.abort("internetdisconnected")
    )
    await page.goto("/settings/sessions")
    await expect(
      page.getByRole("alert").filter({ hasText: "Sem conexão com o servidor" })
    ).toBeVisible({ timeout: 15_000 })

    await page.route("**/api/v1/admin/settings", (route) =>
      route.fulfill({
        status: 403,
        contentType: "application/problem+json",
        body: JSON.stringify({ status: 403, title: "Acesso negado" }),
      })
    )
    await page.goto("/admin")
    const forbidden = page
      .getByRole("alert")
      .filter({ hasText: "Sem permissão" })
    await expect(forbidden).toBeVisible()
    await expect(forbidden.getByRole("button")).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)
  })
  /**
   * Lista de conversas, conversa e contatos (UI-03): sem permissão (403) sem botão, sem conexão (pedido cortado)
   * e erro do servidor com "Tentar de novo", que volta a carregar a tela quando a falha passa.
   */
  test("lista, conversa e contatos distinguem as falhas e tentam de novo", async ({
    page,
  }) => {
    test.slow()
    await loginThroughUi(page, ADMIN)
    const sidebar = page.getByRole("complementary", {
      name: "Conversas e contatos",
    })
    await expect(sidebar).toBeVisible()
    const xsrf = (await page.context().cookies()).find(
      (cookie) => cookie.name === "XSRF-TOKEN"
    )!.value
    const saved = (await (
      await page.request.post("/api/v1/conversations/saved", {
        headers: { "X-XSRF-TOKEN": decodeURIComponent(xsrf) },
      })
    ).json()) as { id: string }

    type Mode = "forbidden" | "offline" | "error" | "ok"
    const failWith = (mode: () => Mode) => (route: Route) => {
      switch (mode()) {
        case "forbidden":
          return route.fulfill({
            status: 403,
            contentType: "application/problem+json",
            body: JSON.stringify({ status: 403, title: "Acesso negado" }),
          })
        case "offline":
          return route.abort("internetdisconnected")
        case "error":
          return route.fulfill({
            status: 409,
            contentType: "application/problem+json",
            body: JSON.stringify({
              status: 409,
              title: "Conflito",
              detail: "Falha simulada na tela",
            }),
          })
        default:
          return route.fallback()
      }
    }
    const expectProblems = async (
      area: ReturnType<Page["getByRole"]>,
      set: (mode: Mode) => void,
      open: () => Promise<void>,
      loaded: ReturnType<Page["getByRole"]>
    ) => {
      set("forbidden")
      await open()
      const forbidden = area
        .getByRole("alert")
        .filter({ hasText: "Sem permissão" })
      await expect(forbidden).toBeVisible()
      await expect(forbidden.getByRole("button")).toHaveCount(0)
      await expectNoSeriousA11yViolations(page)
      set("offline")
      await open()
      const offline = area
        .getByRole("alert")
        .filter({ hasText: "Sem conexão com o servidor" })
      await expect(offline).toBeVisible({ timeout: 15_000 })
      set("error")
      await offline.getByRole("button", { name: "Tentar de novo" }).click()
      const error = area
        .getByRole("alert")
        .filter({ hasText: "Falha simulada na tela" })
      await expect(error).toBeVisible()
      set("ok")
      await error.getByRole("button", { name: "Tentar de novo" }).click()
      await expect(loaded).toBeVisible()
    }

    // Lista de conversas.
    let list: Mode = "ok"
    await page.route(
      (url) => url.pathname === "/api/v1/conversations" && url.search === "",
      failWith(() => list)
    )
    await expectProblems(
      sidebar,
      (mode) => (list = mode),
      async () => {
        await page.goto("/")
      },
      sidebar.getByRole("link", { name: /Mensagens salvas/ })
    )

    // Contatos.
    let contacts: Mode = "ok"
    await page.route(
      "**/api/v1/contacts",
      failWith(() => contacts)
    )
    await expectProblems(
      sidebar,
      (mode) => (contacts = mode),
      async () => {
        await page.goto("/")
        await sidebar.getByRole("tab", { name: "Contatos" }).click()
      },
      sidebar
        .getByText("Nenhum contato ainda")
        .or(sidebar.getByRole("button", { name: /^Conversar com / }))
        .first()
    )

    // Conversa aberta.
    let detail: Mode = "ok"
    await page.route(
      `**/api/v1/conversations/${saved.id}`,
      failWith(() => detail)
    )
    const main = page.getByRole("main")
    await expectProblems(
      main,
      (mode) => (detail = mode),
      async () => {
        await page.goto(`/c/${saved.id}`)
      },
      main.getByRole("textbox", { name: "Mensagem" })
    )
  })

  /** Ação recusada pela API (UI-03): o detalhe aparece num toast de erro, não some em silêncio. */
  test("ação que falha mostra o erro", async ({ page }) => {
    await loginThroughUi(page, ADMIN)
    await expect(
      page.getByRole("complementary", { name: "Conversas e contatos" })
    ).toBeVisible()
    const xsrf = (await page.context().cookies()).find(
      (cookie) => cookie.name === "XSRF-TOKEN"
    )!.value
    const saved = (await (
      await page.request.post("/api/v1/conversations/saved", {
        headers: { "X-XSRF-TOKEN": decodeURIComponent(xsrf) },
      })
    ).json()) as { id: string }
    await page.goto(`/c/${saved.id}`)
    const text = `Favoritar com falha ${Date.now()}`
    await page.getByRole("textbox", { name: "Mensagem" }).fill(text)
    await page.getByRole("button", { name: "Enviar" }).click()
    const row = page
      .locator('[data-slot="message"]')
      .filter({ has: page.locator("p", { hasText: text }) })
    await expect(
      row.getByRole("img", { name: /^(Enviada|Entregue|Lida)$/ })
    ).toBeVisible()

    await page.route(/\/api\/v1\/messages\/[^/]+\/star$/, (route) =>
      route.fulfill({
        status: 409,
        contentType: "application/problem+json",
        body: JSON.stringify({
          status: 409,
          title: "Conflito",
          detail: "Falha simulada ao favoritar",
        }),
      })
    )
    await row.hover()
    await row.getByRole("button", { name: "Ações da mensagem" }).click()
    await page.getByRole("menuitem", { name: "Favoritar" }).click()
    await expect(page.getByText("Falha simulada ao favoritar")).toBeVisible()
  })
})
