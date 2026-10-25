import type { Page } from "@playwright/test"

import { expect, test } from "./fixtures"
import { ensureAdmin, RUN } from "./support"

/** Tamanho mínimo dos alvos de toque nas telas de toque (AR-02, RESPONSIVENESS): 44 × 44 CSS px. */
const MIN = 44

/**
 * Controles visíveis menores que o mínimo. Um campo dentro de um InputGroup conta pela caixa do grupo (o toque em
 * qualquer ponto dela chega ao campo) e a área de um ::after que transborda o controle também conta; links dentro de
 * um parágrafo são texto corrido (exceção da WCAG 2.5.8).
 */
async function smallTargets(page: Page) {
  return page.evaluate((min) => {
    const selector = [
      "button",
      "a[href]",
      "input:not([type=hidden])",
      "textarea",
      "[role=tab]",
      "[role=switch]",
      "[role=combobox]",
      "[role=menuitem]",
      "[role=option]",
    ].join(",")
    const small: string[] = []
    for (const element of document.querySelectorAll<HTMLElement>(selector)) {
      if (
        !element.checkVisibility({ visibilityProperty: true }) ||
        element.closest("[inert],[aria-hidden=true],.sr-only") !== null ||
        element.closest("p,li[data-slot=message-text]") !== null ||
        (element instanceof HTMLInputElement && element.hidden)
      ) {
        continue
      }
      const own = (
        element.closest('[data-slot="input-group"]') ?? element
      ).getBoundingClientRect()
      // A área de toque inclui um ::after posicionado que transborda o controle (o Switch do shadcn usa isso).
      const after = getComputedStyle(element, "::after")
      const grow = (value: string) => Math.max(0, -(parseFloat(value) || 0))
      const box =
        after.content !== "none" && after.position === "absolute"
          ? {
              width: own.width + grow(after.left) + grow(after.right),
              height: own.height + grow(after.top) + grow(after.bottom),
            }
          : own
      if (box.width === 0 || box.height === 0) {
        continue
      }
      if (box.width < min - 0.5 || box.height < min - 0.5) {
        const name =
          element.getAttribute("aria-label") ??
          element.textContent?.trim().slice(0, 30) ??
          ""
        small.push(
          `${element.tagName.toLowerCase()}[${element.dataset.slot ?? ""}] "${name}" ${Math.round(box.width)}x${Math.round(box.height)}`
        )
      }
    }
    return small
  }, MIN)
}

test("alvos de toque de 44 px no celular", async ({ browser, pageErrors }) => {
  test.skip(
    !test.info().project.name.startsWith("mobile"),
    "alvos de toque são do celular (pointer: coarse)"
  )
  test.slow()
  const { context, page } = await ensureAdmin(browser, pageErrors)
  expect(
    await page.evaluate(() => matchMedia("(pointer: coarse)").matches)
  ).toBe(true)

  const screens: Record<string, string[]> = {}
  const measure = async (label: string) => {
    await expect(page.getByRole("status", { name: /^Carregando/ })).toHaveCount(
      0
    )
    // Diálogos entram com escala: medido no meio da animação, o botão de 44 px dá 43. As infinitas (spinner, brilho
    // do esqueleto) e as presas à rolagem (o esmaecido das faixas que rolam) ficam de fora, senão a espera não acaba.
    await page.evaluate(() =>
      Promise.all(
        document
          .getAnimations()
          .filter(
            (animation) =>
              animation.timeline === document.timeline &&
              animation.effect?.getComputedTiming().endTime !== Infinity
          )
          .map((animation) => animation.finished.catch(() => undefined))
      )
    )
    screens[label] = await smallTargets(page)
  }

  await page.goto("/")
  await measure("/")

  // Conversa com uma mensagem própria: cabeçalho, ações da mensagem e composer.
  const xsrf = (await context.cookies()).find(
    (cookie) => cookie.name === "XSRF-TOKEN"
  )!.value
  const saved = (await (
    await page.request.post("/api/v1/conversations/saved", {
      headers: { "X-XSRF-TOKEN": decodeURIComponent(xsrf) },
    })
  ).json()) as { id: string }
  await page.goto(`/c/${saved.id}`)
  await page.getByRole("textbox", { name: "Mensagem" }).fill(`Toque ${RUN}`)
  await page.getByRole("button", { name: "Enviar" }).click()
  await expect(
    page.locator('[data-slot="message"]').filter({ hasText: `Toque ${RUN}` })
  ).toBeVisible()
  await measure("conversa")

  // Diálogo: o botão de fechar também é alvo frequente.
  await page.goto("/")
  await page.getByRole("button", { name: "Adicionar contato" }).click()
  await expect(
    page.getByRole("dialog", { name: "Adicionar contato" })
  ).toBeVisible()
  await measure("diálogo")
  await page.keyboard.press("Escape")

  for (const path of [
    "/settings/profile",
    "/settings/privacy",
    "/settings/appearance",
  ]) {
    await page.goto(path)
    await expect(page.getByRole("main")).toBeVisible()
    await measure(path)
  }

  expect(
    Object.entries(screens).filter(([, small]) => small.length > 0)
  ).toEqual([])
  await context.close()
})
