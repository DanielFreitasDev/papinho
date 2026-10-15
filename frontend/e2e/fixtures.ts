import AxeBuilder from "@axe-core/playwright"
import { expect, test as base, type Page } from "@playwright/test"

type Fixtures = {
  /** Erros de console e violações de CSP coletados durante o teste; precisam terminar vazios. */
  pageErrors: string[]
}

export const test = base.extend<Fixtures>({
  pageErrors: async ({ page }, use) => {
    const errors: string[] = []
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(`console: ${message.text()}`)
    })
    page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`))
    await page.addInitScript(() => {
      document.addEventListener("securitypolicyviolation", (event) => {
        console.error(`CSP: ${event.violatedDirective} ${event.blockedURI}`)
      })
    })
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
    serious.map((violation) => `${violation.id}: ${violation.help}`)
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
