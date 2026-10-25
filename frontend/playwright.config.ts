import { defineConfig, devices } from "@playwright/test"

/**
 * E2E contra o papinho.jar já em execução (mesma origem do SPA, da API e do WebSocket).
 * PAPINHO_E2E_URL aponta para a instância; PLAYWRIGHT_CHROMIUM_EXECUTABLE permite usar um Chromium já
 * instalado na máquina em vez do baixado pelo Playwright.
 */
const baseURL = process.env.PAPINHO_E2E_URL ?? "http://localhost:8080"
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE
/**
 * Instância HTTPS (perfil lan, certificado da CA própria) para o WebKit: ele recusa cookies Secure em
 * http://localhost, então o __Host-SESSION e o XSRF-TOKEN só funcionam por HTTPS.
 */
const httpsURL = process.env.PAPINHO_E2E_HTTPS_URL

/** Opções só do Chromium: o microfone falso e o modo headless novo não existem no Firefox e no WebKit. */
const chromium = {
  // Chromium completo no modo headless novo: o headless shell padrão nega notificações mesmo com a permissão
  // concedida (Notification.permission fica "denied").
  ...(executablePath ? {} : { channel: "chromium" }),
  // Microfone falso para a gravação de voz (AU-01): tom sintético e permissão sem diálogo.
  launchOptions: {
    ...(executablePath ? { executablePath } : {}),
    args: [
      "--use-fake-ui-for-media-stream",
      "--use-fake-device-for-media-stream",
    ],
  },
  permissions: ["microphone"],
}

/** UI-05 (aparência própria) também roda no Firefox e no WebKit. */
const NATIVE_CONTROLS = /native-controls\.spec\.ts/

/**
 * Whisper de verdade (AU-04), só com PAPINHO_E2E_WHISPER: o projeto desktop-chromium cobre o WASM (o headless não
 * tem adaptador WebGPU) e este, com o adaptador WebGPU por software do Chromium, cobre o caminho WebGPU.
 */
const whisperWebgpu = process.env.PAPINHO_E2E_WHISPER
  ? [
      {
        name: "whisper-webgpu",
        testMatch: /whisper\.spec\.ts/,
        use: {
          ...devices["Desktop Chrome"],
          ...chromium,
          launchOptions: {
            ...chromium.launchOptions,
            args: [...chromium.launchOptions.args, "--enable-unsafe-webgpu"],
          },
        },
        dependencies: ["primeiro-admin"],
      },
    ]
  : []

/**
 * O e2e só fala com a instância local: conexão direta. Num desktop com proxy configurado, o Firefox usa o
 * proxy do sistema e o WebKit as variáveis *_proxy, e o WebSocket e o HTTPS local passam pelo proxy (e falham).
 */
const PROXY_VARIABLES = /^(https?|all|no)_proxy$/i
const directEnv = Object.fromEntries(
  Object.entries(process.env).filter(
    (entry): entry is [string, string] =>
      entry[1] !== undefined && !PROXY_VARIABLES.test(entry[0])
  )
)

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
  },
  projects: [
    // Cria o primeiro admin pela UI uma vez só; os outros projetos rodam em paralelo depois dele.
    {
      name: "primeiro-admin",
      testMatch: /first-admin\.setup\.ts/,
      use: { ...devices["Desktop Chrome"], ...chromium },
    },
    {
      name: "desktop-chromium",
      use: { ...devices["Desktop Chrome"], ...chromium },
      dependencies: ["primeiro-admin"],
    },
    {
      name: "mobile-chromium",
      use: { ...devices["Pixel 7"], ...chromium },
      dependencies: ["primeiro-admin"],
    },
    {
      name: "desktop-firefox",
      testMatch: NATIVE_CONTROLS,
      use: {
        ...devices["Desktop Firefox"],
        launchOptions: { firefoxUserPrefs: { "network.proxy.type": 0 } },
      },
      dependencies: ["primeiro-admin"],
    },
    {
      name: "desktop-webkit",
      testMatch: NATIVE_CONTROLS,
      // O WebKit do runner do CI (Ubuntu 26.04, sem GPU) é bem mais lento que numa máquina comum: do clique em
      // "Entrar" até a lista levou 2,3 s, e 5,5 s no primeiro teste da rodada (aqui, 0,4 s). O spec é de aparência.
      expect: { timeout: 15_000 },
      use: {
        ...devices["Desktop Safari"],
        ...(httpsURL ? { baseURL: httpsURL, ignoreHTTPSErrors: true } : {}),
        launchOptions: { env: directEnv },
      },
      dependencies: ["primeiro-admin"],
    },
    ...whisperWebgpu,
  ],
})
