import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发环境：Vite dev server（默认 5173）把 /api、/actuator 代理到 Spring Boot（8080），
 * 这样前端代码里始终写相对路径，不需要 CORS；
 * 生产环境由 Nginx 做同样的反向代理（见 frontend/nginx.conf）。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 900
  }
})
