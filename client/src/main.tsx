import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider, createRouter } from "@tanstack/react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { LucideProvider } from "lucide-react";
import "@fontsource-variable/noto-sans-sc";
import "@fontsource-variable/jetbrains-mono";
import "katex/dist/katex.min.css";
import "./index.css";
import "@/i18n";
import "@/lib/theme";
import { routeTree } from "./routeTree.gen";

const queryClient = new QueryClient();
const router = createRouter({ routeTree, context: { queryClient } });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
  interface StaticDataRouteOption {
    /** Swaps the full header for the logo-only one used on sign-in and sign-up. */
    minimalHeader?: boolean;
  }
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* Icons keep a 2px stroke at every size. */}
      <LucideProvider size={16} nonScalingStroke>
        <RouterProvider router={router} />
      </LucideProvider>
    </QueryClientProvider>
  </StrictMode>,
);
