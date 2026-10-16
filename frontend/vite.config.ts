import { resolve } from "node:path"
import tailwindcss from "@tailwindcss/vite"
import { tanstackRouter } from "@tanstack/router-plugin/vite"
import react from "@vitejs/plugin-react"
import { VitePWA } from "vite-plugin-pwa"
import { defineConfig } from "vitest/config"

const backend = process.env.PAPINHO_BACKEND_URL ?? "http://localhost:8080"

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    // O plugin do router precisa vir antes do plugin do React.
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    tailwindcss(),
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
        globIgnores: ["**/*.wasm", "screenshots/**", "assets/ort-*"],
        maximumFileSizeToCacheInBytes: 4 * 1024 * 1024,
      },
      devOptions: { enabled: false },
    }),
  ],
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
