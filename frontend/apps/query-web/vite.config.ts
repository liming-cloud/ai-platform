import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  base: "/query/",
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
    rollupOptions: {
      output: {
        manualChunks(moduleId) {
          if (moduleId.includes("/node_modules/@fluentui/")) {
            return "fluent-ui";
          }
          if (
            moduleId.includes("/node_modules/react/")
            || moduleId.includes("/node_modules/react-dom/")
            || moduleId.includes("/node_modules/scheduler/")
          ) {
            return "react-runtime";
          }
          return undefined;
        },
      },
    },
  },
});
