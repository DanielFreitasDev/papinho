import {
  cpSync,
  createReadStream,
  existsSync,
  readFileSync,
  statSync,
} from "node:fs"
import { extname, resolve } from "node:path"
import tailwindcss from "@tailwindcss/vite"
import { tanstackRouter } from "@tanstack/router-plugin/vite"
import react from "@vitejs/plugin-react"
import { VitePWA } from "vite-plugin-pwa"
import { defineConfig, type Plugin } from "vitest/config"

const backend = process.env.PAPINHO_BACKEND_URL ?? "http://localhost:8080"

const pdfjsRoot = resolve(import.meta.dirname, "node_modules/pdfjs-dist")
const pdfjsVersion = (
  JSON.parse(readFileSync(resolve(pdfjsRoot, "package.json"), "utf8")) as {
    version: string
  }
).version
/** Os dados do pdf.js ficam em /assets (cache de um ano no servidor): a versão no caminho troca a URL a cada versão. */
const pdfjsData = `/assets/pdfjs-${pdfjsVersion}/`
const PDFJS_FOLDERS = ["standard_fonts", "cmaps", "iccs", "wasm"]
const PDFJS_TYPES: Record<string, string> = {
  ".wasm": "application/wasm",
  ".js": "text/javascript",
}

/**
 * Fontes padrão, mapas de caracteres, perfis de cor e decodificadores WASM do pdf.js servidos pelo próprio Papinho:
 * copiados para o build e, no desenvolvimento, lidos direto do node_modules.
 */
function pdfjsAssets(): Plugin {
  return {
    name: "papinho-pdfjs-assets",
    configureServer(server) {
      server.middlewares.use(pdfjsData, (request, response, next) => {
        const path = decodeURIComponent((request.url ?? "").split("?")[0])
        const file = resolve(pdfjsRoot, `.${path}`)
        const allowed = PDFJS_FOLDERS.some((folder) =>
          file.startsWith(resolve(pdfjsRoot, folder))
        )
        if (!allowed || !existsSync(file) || !statSync(file).isFile()) {
          next()
          return
        }
        response.setHeader(
          "Content-Type",
          PDFJS_TYPES[extname(file)] ?? "application/octet-stream"
        )
        createReadStream(file).pipe(response)
      })
    },
    writeBundle(options) {
      const out = options.dir ?? resolve(import.meta.dirname, "dist")
      for (const folder of PDFJS_FOLDERS) {
        cpSync(
          resolve(pdfjsRoot, folder),
          resolve(out, pdfjsData.slice(1), folder),
          { recursive: true }
        )
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    // O plugin do router precisa vir antes do plugin do React.
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    tailwindcss(),
    pdfjsAssets(),
    // PWA instalável (NT-04): service worker próprio (injectManifest) e atualização só quando a pessoa pede.
    VitePWA({
      strategies: "injectManifest",
      srcDir: "src",
      filename: "sw.ts",
      registerType: "prompt",
      injectRegister: false,
      manifestFilename: "manifest.webmanifest",
      manifest: {
        id: "/",
        name: "Papinho",
        short_name: "Papinho",
        description: "Chat auto-hospedado com privacidade nativa",
        lang: "pt-BR",
        start_url: "/",
        scope: "/",
        display: "standalone",
        orientation: "any",
        background_color: "#ffffff",
        theme_color: "#16a34a",
        categories: ["social", "communication"],
        icons: [
          { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
          {
            src: "/icons/maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
          { src: "/favicon.svg", sizes: "any", type: "image/svg+xml" },
        ],
        screenshots: [
          {
            src: "/screenshots/wide.png",
            sizes: "1280x800",
            type: "image/png",
            form_factor: "wide",
            label: "Conversas no computador",
          },
          {
            src: "/screenshots/narrow.png",
            sizes: "390x844",
            type: "image/png",
            form_factor: "narrow",
            label: "Conversa no celular",
          },
        ],
      },
      injectManifest: {
        // O app em si; o runtime ONNX (dezenas de MB) e as capturas do manifesto ficam de fora.
        globPatterns: ["**/*.{js,css,html,woff2}"],
        // O pdf.js (o pacote, o worker e os dados) só vale com rede: os anexos não ficam offline.
        globIgnores: [
          "**/*.wasm",
          "screenshots/**",
          "assets/ort-*",
          "assets/pdfjs-*/**",
          "assets/pdf-*",
          "assets/pdf.worker*",
        ],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
      devOptions: { enabled: false },
    }),
  ],
  define: { PDFJS_DATA: JSON.stringify(pdfjsData) },
  resolve: {
    alias: {
      "@": resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    // Mesma origem em desenvolvimento: sem CORS, o cookie de sessão funciona.
    proxy: {
      "/api": { target: backend, changeOrigin: false },
      "/ws": { target: backend, ws: true, changeOrigin: false },
      "/setup": { target: backend, changeOrigin: false },
      "/actuator": { target: backend, changeOrigin: false },
    },
  },
  build: { outDir: "dist", sourcemap: false },
  test: {
    projects: [
      {
        extends: true,
        test: {
          name: "app",
          environment: "jsdom",
          setupFiles: ["./src/test/setup.ts"],
          include: ["src/**/*.test.{ts,tsx}"],
          css: false,
        },
      },
      {
        // Regra de lint do UI-05 com o ESLint de verdade: ambiente node, sem o setup do app.
        extends: true,
        test: {
          name: "lint",
          environment: "node",
          include: ["lint/**/*.test.ts"],
        },
      },
    ],
  },
})
