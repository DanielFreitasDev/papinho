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
  // O cadastro faz 11 hashes Argon2 (senha e códigos): numa instância recém-iniciada e com a máquina cheia, passa
  // dos 5 s padrão sem estar errado.
  await expect(dialog).toBeVisible({ timeout: 15_000 })
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
  await page.getByLabel("Senha", { exact: true }).fill(password)
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

/**
 * Sai pelo menu e espera o próprio app chegar ao login. Navegar antes cancela o POST do logout no navegador, mas o
 * servidor ainda o processa: a página nova vê a sessão viva e, logo depois, só recebe 401.
 */
export async function logoutThroughUi(page: Page) {
  await openMenuItem(page, "Sair")
  await expect(page).toHaveURL(/\/login/)
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

/** Duas pessoas que se adicionaram e estão com a conversa aberta; `tag` é única por spec (gil.<tag>). */
export async function twoFriends(
  browser: Browser,
  errors: string[],
  tag: string,
  label = "Midia"
) {
  const owner = await ensureAdmin(browser, errors)
  const invite = await createInvite(owner.page)
  await owner.context.close()
  const people = []
  for (const [username, name] of [
    [`gil.${tag}`, `Gil ${label}`],
    [`hana.${tag}`, `Hana ${label}`],
  ]) {
    const context = await browser.newContext()
    const page = await context.newPage()
    await watchErrors(page, errors)
    await page.goto(invite)
    await registerThroughUi(page, username, name)
    people.push({ context, page, username, name })
  }
  const [gil, hana] = people
  // Primeiro os dois se adicionam; só depois abrem a conversa (assim ela não nasce como solicitação).
  for (const [me, other] of [
    [gil, hana],
    [hana, gil],
  ]) {
    await me.page.getByRole("button", { name: "Adicionar contato" }).click()
    const add = me.page.getByRole("dialog", { name: "Adicionar contato" })
    await add.getByLabel("@usuario").fill(other.username)
    await add.getByRole("button", { name: "Adicionar", exact: true }).click()
    await expect(add).toBeHidden()
  }
  for (const [me, other] of [
    [gil, hana],
    [hana, gil],
  ]) {
    await me.page
      .getByRole("complementary")
      .getByRole("tab", { name: "Contatos" })
      .click()
    await me.page
      .getByRole("button", { name: `Conversar com ${other.name}` })
      .click()
    await expect(
      me.page.getByRole("heading", { name: other.name })
    ).toBeVisible()
  }
  return { gil, hana }
}

/** PNG desenhado num canvas do próprio navegador. */
export async function pngFile(page: Page, color: string, name: string) {
  const dataUrl = await page.evaluate((fill) => {
    const canvas = document.createElement("canvas")
    canvas.width = 320
    canvas.height = 200
    const context = canvas.getContext("2d")!
    context.fillStyle = fill
    context.fillRect(0, 0, 320, 200)
    return canvas.toDataURL("image/png")
  }, color)
  return {
    name,
    mimeType: "image/png",
    buffer: Buffer.from(dataUrl.split(",")[1], "base64"),
  }
}

/** Abre o menu de anexos do campo de mensagem e escolhe um item ("Documento", "Fotos e vídeos", "Enquete"...). */
export async function chooseAttachment(page: Page, item: string) {
  await page.getByRole("button", { name: "Anexar", exact: true }).click()
  await page.getByRole("menuitem", { name: item }).click()
}

/** Anexa os arquivos pelo menu de anexos do composer e envia, com legenda opcional. */
export async function attach(
  page: Page,
  files: Awaited<ReturnType<typeof pngFile>>[],
  caption?: string
) {
  const chooser = page.waitForEvent("filechooser")
  await chooseAttachment(page, "Fotos e vídeos")
  await (await chooser).setFiles(files)
  const dialog = page.getByRole("dialog", { name: /Enviar \d arquivo/ })
  await expect(dialog).toBeVisible()
  if (caption) {
    await dialog.getByLabel("Legenda (opcional)").fill(caption)
  }
  await dialog.getByRole("button", { name: "Enviar" }).click()
  await expect(dialog).toBeHidden()
}
