import { defineConfig, devices } from "@playwright/test"

/**
 * E2E contra o papinho.jar já em execução (mesma origem do SPA, da API e do WebSocket).
 * PAPINHO_E2E_URL aponta para a instância; PLAYWRIGHT_CHROMIUM_EXECUTABLE permite usar um Chromium já
 * instalado na máquina em vez do baixado pelo Playwright.
 */
const baseURL = process.env.PAPINHO_E2E_URL ?? "http://localhost:8080"
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL,
    locale: "pt-BR",
    timezoneId: "America/Fortaleza",
    trace: "retain-on-failure",
    launchOptions: executablePath ? { executablePath } : {},
  },
  projects: [
    { name: "desktop-chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
})
