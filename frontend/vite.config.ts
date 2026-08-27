import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { DEV_BACKEND_ORIGIN } from './src/config/devProxy.ts'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: DEV_BACKEND_ORIGIN,
        changeOrigin: true,
      },
      '/ws': {
        // 使用 HTTP target 进行 Upgrade，使 rewriteWsOrigin 生成 Spring 接受的 http(s) Origin。
        // 写成 ws:// 会把 Origin 也改成 ws://，从而被同源校验以 403 拒绝。
        target: DEV_BACKEND_ORIGIN,
        ws: true,
        changeOrigin: true,
        rewriteWsOrigin: true,
      },
    },
  },
})
