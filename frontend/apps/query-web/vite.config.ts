import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api/v1/auth": "http://127.0.0.1:18080",
      "/api": "http://127.0.0.1:18081",
    },
  },
  build: {
    outDir: "dist/app",
    sourcemap: true,
  },
});
