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
// Rede cortada de propósito (context.setOffline, NT-05): o navegador registra cada pedido que falhou.
const EXPECTED_OFFLINE = /net::ERR_INTERNET_DISCONNECTED/
// Contextos do Playwright são anônimos e o Chrome não tem Push API neles; ele avisa no console ao assinar.
const EXPECTED_NO_PUSH = /does not support the Push API in incognito mode/
// O Safari ignora a chave interactive-widget da meta viewport (só o Chromium a usa, para o teclado virtual) e
// avisa no console como erro.
const EXPECTED_VIEWPORT_KEY =
  /Viewport argument key "interactive-widget" not recognized/
// O WebKit relata como erro de página cada fetch cancelado por um reload ou por uma navegação ("Fetch API cannot load
// <url> due to access control checks"). Na mesma origem não há CORS: é só o cancelamento. Outra origem continua
// reprovando.
const CANCELLED_FETCH =
  /Fetch API cannot load (\S+) due to access control checks/

function cancelledSameOriginFetch(page: Page, text: string) {
  const url = CANCELLED_FETCH.exec(text)?.[1]
  if (!url) {
    return false
  }
  try {
    return new URL(url).origin === new URL(page.url()).origin
  } catch {
    return false
  }
}

/** Coleta erros de console e violações de CSP de uma página (também das abertas em outros contextos). */
export async function watchErrors(page: Page, errors: string[]) {
  page.on("console", (message) => {
    if (
      message.type() === "error" &&
      !EXPECTED_CLIENT_ERROR.test(message.text()) &&
      !EXPECTED_OFFLINE.test(message.text()) &&
      !EXPECTED_NO_PUSH.test(message.text()) &&
      !EXPECTED_VIEWPORT_KEY.test(message.text()) &&
      !cancelledSameOriginFetch(page, message.text())
    ) {
      errors.push(`console: ${message.text()}`)
    }
  })
  // A pilha acompanha a mensagem: sem ela, uma promessa rejeitada (ex.: AbortError) não diz de onde veio.
  page.on("pageerror", (error) => {
    const text = `${error.message}\n${error.stack ?? ""}`
    if (!cancelledSameOriginFetch(page, text)) {
      errors.push(`pageerror: ${text}`)
    }
  })
  await page.addInitScript(() => {
    document.addEventListener("securitypolicyviolation", (event) => {
      // Origem (arquivo:linha) e o começo do trecho bloqueado ajudam a achar quem injetou o conteúdo.
      console.error(
        `CSP: ${event.violatedDirective} ${event.blockedURI} ${event.sourceFile}:${event.lineNumber} ${event.sample.slice(0, 60)}`
      )
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
  // Diálogos entrando com opacidade parcial dão falso contraste: espera (até 2 s) as animações finitas
  // em andamento acabarem.
  await page.evaluate(() =>
    Promise.race([
      Promise.all(
        document
          .getAnimations()
          .filter(
            (animation) =>
              animation.playState === "running" &&
              animation.effect?.getComputedTiming().iterations !== Infinity
          )
          .map((animation) => animation.finished.catch(() => undefined))
      ),
      new Promise((resolve) => setTimeout(resolve, 2_000)),
    ])
  )
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
          .map(
            (node) =>
              `${node.target.join(" ")} ${node.html.slice(0, 160)} ${node.failureSummary ?? ""}`
          )
          .join(", ")}`
    )
  ).toEqual([])
}

/**
 * Sem rolagem horizontal acidental (AR-02): nem na página nem num contêiner interno (o main, a barra lateral, um
 * painel) que role para o lado. Só contam os que rolam de fato (overflow-x auto ou scroll); listas feitas para
 * rolar na horizontal se declaram com data-scroll-x.
 */
export async function expectNoHorizontalScroll(page: Page) {
  expect(
    await horizontalScrollers(page),
    "contêineres com rolagem horizontal"
  ).toEqual([])
}

/** Elementos que rolam para o lado (ver expectNoHorizontalScroll). */
export function horizontalScrollers(page: Page) {
  return page.evaluate(() => {
    const found: string[] = []
    const root = document.documentElement
    if (root.scrollWidth > root.clientWidth) {
      found.push(`HTML ${root.scrollWidth}>${root.clientWidth}`)
    }
    for (const element of document.body.querySelectorAll<HTMLElement>("*")) {
      if (element.scrollWidth <= element.clientWidth + 1) {
        continue
      }
      const { overflowX } = getComputedStyle(element)
      if (
        (overflowX === "auto" || overflowX === "scroll") &&
        element.closest("[data-scroll-x]") === null &&
        element.checkVisibility()
      ) {
        const slot = element.dataset.slot ? `[${element.dataset.slot}]` : ""
        found.push(
          `${element.tagName}${slot} ${element.scrollWidth}>${element.clientWidth}`
        )
      }
    }
    return found
  })
}
