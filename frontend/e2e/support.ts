import { createHmac } from "node:crypto"
import type { Browser, Page } from "@playwright/test"

import { expect, watchErrors } from "./fixtures"

export const PASSWORD = "senha muito segura 123"

/** @usuario do admin dos testes ("admin" é reservado pelo servidor). */
export const ADMIN = "anfitriao"

/** Sufixo único por execução, para rodar contra uma instância já usada sem colidir @usuarios. */
export const RUN = Date.now().toString(36).slice(-5)

/** TOTP da RFC 6238 (o mesmo algoritmo do servidor) para o teste digitar o código do "app". */
export function totp(secret: string, now = Date.now()): string {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
  let bits = ""
  for (const char of secret.replace(/=+$/, "").toUpperCase()) {
    bits += alphabet.indexOf(char).toString(2).padStart(5, "0")
  }
  const bytes = Buffer.from(
    bits.match(/.{8}/g)!.map((byte) => parseInt(byte, 2))
  )
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(now / 30_000)))
  const hash = createHmac("sha1", bytes).update(counter).digest()
  const offset = hash[hash.length - 1] & 0x0f
  const value = (hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000
  return value.toString().padStart(6, "0")
}

/** Preenche o cadastro e confirma os códigos de recuperação. */
export async function registerThroughUi(
  page: Page,
  username: string,
  displayName: string
) {
  await page.getByLabel("Nome de exibição").fill(displayName)
  await page.getByLabel("@usuario").fill(username)
  await page.getByLabel("Senha", { exact: true }).fill(PASSWORD)
  await page.getByLabel("Confirmar senha").fill(PASSWORD)
  await page.getByRole("button", { name: "Criar conta" }).click()
  const dialog = page.getByRole("dialog", {
    name: "Guarde seus códigos de recuperação",
  })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole("listitem")).toHaveCount(10)
  const codes = await dialog.getByRole("listitem").allTextContents()
  await dialog.getByRole("button", { name: "Guardei os códigos" }).click()
  await expect(
    page.getByRole("complementary", { name: "Conversas e contatos" })
  ).toBeVisible()
  return codes
}

export async function loginThroughUi(
  page: Page,
  username: string,
  password = PASSWORD
) {
  await page.goto("/login")
  await page.getByLabel("@usuario").fill(username)
  await page.getByLabel("Senha").fill(password)
  await page.getByRole("button", { name: "Entrar", exact: true }).click()
}

/** A barra lateral abre em "Conversas"; os contatos ficam na outra aba. */
export async function openContactsTab(page: Page) {
  await page
    .getByRole("complementary")
    .getByRole("tab", { name: "Contatos" })
    .click()
}

export async function openMenuItem(page: Page, name: string) {
  const menu = page.getByRole("button", { name: "Menu" })
  const back = page.getByRole("link", { name: "Voltar" })
  // No celular o menu fica na tela inicial (lista); nas outras telas há "voltar". Espera um dos dois
  // aparecer antes de decidir, porque isVisible() não espera a tela renderizar.
  await expect(menu.or(back).first()).toBeVisible()
  if (!(await menu.isVisible())) {
    await back.click()
  }
  await menu.click()
  await page.getByRole("menuitem", { name }).click()
}

/** Entra com a conta admin, criada pelo projeto "primeiro-admin" (first-admin.setup.ts). */
export async function ensureAdmin(browser: Browser, errors?: string[]) {
  const context = await browser.newContext()
  const page = await context.newPage()
  if (errors) {
    await watchErrors(page, errors)
  }
  await loginThroughUi(page, ADMIN)
  await expect(
    page.getByRole("complementary", { name: "Conversas e contatos" })
  ).toBeVisible()
  return { context, page }
}

/** Cria um convite pelo diálogo "Convidar amigos" e devolve a URL. */
export async function createInvite(page: Page) {
  await openMenuItem(page, "Convidar amigos")
  const dialog = page.getByRole("dialog", { name: "Convidar amigos" })
  await dialog.getByRole("button", { name: "Criar convite" }).click()
  const url = (await dialog.locator("code").textContent())!.trim()
  expect(url).toContain("/register?invite=")
  await page.keyboard.press("Escape")
  return url
}
