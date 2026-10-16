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
    // Chromium completo no modo headless novo: o headless shell padrão nega notificações mesmo com a permissão
    // concedida (Notification.permission fica "denied").
    ...(executablePath ? {} : { channel: "chromium" }),
    locale: "pt-BR",
    timezoneId: "America/Fortaleza",
    trace: "retain-on-failure",
    // Microfone falso para a gravação de voz (AU-01): tom sintético e permissão sem diálogo.
    launchOptions: {
      ...(executablePath ? { executablePath } : {}),
      args: [
        "--use-fake-ui-for-media-stream",
        "--use-fake-device-for-media-stream",
      ],
    },
    permissions: ["microphone"],
  },
  projects: [
    // Cria o primeiro admin pela UI uma vez só; os outros projetos rodam em paralelo depois dele.
    {
      name: "primeiro-admin",
      testMatch: /first-admin\.setup\.ts/,
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "desktop-chromium",
      use: { ...devices["Desktop Chrome"] },
      dependencies: ["primeiro-admin"],
    },
    {
      name: "mobile-chromium",
      use: { ...devices["Pixel 7"] },
      dependencies: ["primeiro-admin"],
    },
  ],
})
