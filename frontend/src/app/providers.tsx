import type { ReactNode } from "react"
import { QueryClientProvider } from "@tanstack/react-query"
import { CSPProvider } from "@base-ui/react/csp-provider"

import { ThemeProvider } from "@/components/theme-provider"
import { Toaster } from "@/components/ui/toast"
import { TooltipProvider } from "@/components/ui/tooltip"
import { queryClient } from "@/lib/query-client"

export function Providers({ children }: { children: ReactNode }) {
  return (
    // A CSP estrita (style-src 'self') bloqueia os <style> que o Base UI injeta; as mesmas regras estão
    // no index.css (classe base-ui-disable-scrollbar).
    <CSPProvider disableStyleElements>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <TooltipProvider>
            <Toaster>{children}</Toaster>
          </TooltipProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </CSPProvider>
  )
}
