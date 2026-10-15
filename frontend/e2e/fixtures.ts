import AxeBuilder from "@axe-core/playwright"
import { expect, test as base, type Page } from "@playwright/test"

type Fixtures = {
  /** Erros de console e violações de CSP coletados durante o teste; precisam terminar vazios. */
  pageErrors: string[]
}

// Respostas 4xx esperadas (login errado, @usuario em uso) aparecem como erro de rede no console;
// não são falhas da aplicação. 5xx e qualquer outro erro continuam reprovando o teste.
const EXPECTED_CLIENT_ERROR =
  /Failed to load resource: the server responded with a status of 4\d\d/

/** Coleta erros de console e violações de CSP de uma página (também das abertas em outros contextos). */
export async function watchErrors(page: Page, errors: string[]) {
  page.on("console", (message) => {
    if (
      message.type() === "error" &&
      !EXPECTED_CLIENT_ERROR.test(message.text())
    ) {
      errors.push(`console: ${message.text()}`)
    }
  })
  page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`))
  await page.addInitScript(() => {
    document.addEventListener("securitypolicyviolation", (event) => {
      console.error(`CSP: ${event.violatedDirective} ${event.blockedURI}`)
    })
  })
}

export const test = base.extend<Fixtures>({
  pageErrors: async ({ page, context }, use) => {
    const errors: string[] = []
    await watchErrors(page, errors)
    // Páginas novas do mesmo contexto também são vigiadas.
    context.on("page", (other) => void watchErrors(other, errors))
    await use(errors)
    expect(errors, "erros de console ou de CSP").toEqual([])
  },
})

export { expect }

/** axe sem violações críticas ou sérias (AR-03). */
export async function expectNoSeriousA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze()
  const serious = results.violations.filter(
    (violation) =>
      violation.impact === "critical" || violation.impact === "serious"
  )
  expect(
    serious.map(
      (violation) =>
        `${violation.id}: ${violation.help} → ${violation.nodes
          .map((node) => node.target.join(" "))
          .join(", ")}`
    )
  ).toEqual([])
}

/** Sem rolagem horizontal acidental (AR-02). */
export async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth
  )
  expect(overflow).toBeLessThanOrEqual(0)
}
