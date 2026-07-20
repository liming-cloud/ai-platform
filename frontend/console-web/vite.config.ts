import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    target: 'es2022',
    rollupOptions: {
      output: {
        experimentalMinChunkSize: 2_000,
        onlyExplicitManualChunks: true,
        manualChunks(id) {
          return /\/src\/(?:governance|identity|overview)-pages\.tsx$/.test(id)
            ? 'operations-pages'
            : undefined;
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  test: {
    environment: 'jsdom',
    // Fluent UI's document-scoped focus manager is intentionally singleton-based.
    // Serial files keep accessibility state deterministic in the shared jsdom document.
    fileParallelism: false,
    include: ['src/**/*.test.{ts,tsx}', 'src/**/*.spec.{ts,tsx}'],
    setupFiles: './src/test.setup.ts',
    globals: true,
  },
});
