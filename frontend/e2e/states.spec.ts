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
})
