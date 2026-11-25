import { fileURLToPath, URL } from "node:url";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import { codeInspectorPlugin } from "code-inspector-plugin";

export default defineConfig(({ mode }) => ({
  plugins: [
    codeInspectorPlugin({ bundler: "vite" }),
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    // Listen on all interfaces so other devices on the LAN can reach the dev server.
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: loadEnv(mode, process.cwd(), "").IOJ_API_PROXY_TARGET,
        changeOrigin: true,
        // The backend CORS list only has localhost:5173, so a POST from a LAN address gets 403.
        // Behind the proxy the request is same-origin for the backend, so the header can go.
        configure: (proxy) => {
          proxy.on("proxyReq", (request) => request.removeHeader("origin"));
        },
      },
    },
  },
}));
