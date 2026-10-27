import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { RouterProvider } from "@tanstack/react-router"

import "./index.css"
import "@/lib/i18n"
import { Providers } from "@/app/providers"
import { router } from "@/app/router"
import { applyDisplayPrefs } from "@/lib/display-prefs"
import { handleSkippedViewTransitions } from "@/lib/motion"
import { registerServiceWorker } from "@/lib/pwa"
import { installRipple } from "@/lib/ripple"
import { restoreWallpaperImage } from "@/lib/wallpaper-image"

applyDisplayPrefs()
void restoreWallpaperImage()
handleSkippedViewTransitions()
installRipple()
registerServiceWorker((path) => router.history.push(path))

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Providers>
      <RouterProvider router={router} />
    </Providers>
  </StrictMode>
)
