import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// 管理端与 Web 端保持同一套构建方式（Vue 3 + Vite 5 + Tailwind CSS v4）。
// 开发期通过代理访问 Go 服务端，避免跨域；生产环境可由任意静态服务器托管，
// 再把 /api 反向代理到服务端（或通过 VITE_API_BASE 指定绝对地址）。
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), './src')
    }
  },
  server: {
    port: 5174,
    host: '0.0.0.0',
    allowedHosts: true,
    proxy: {
      '/api': {
        target: process.env.BETTER_HRBUST_API || 'http://127.0.0.1:9191',
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 5174,
    proxy: {
      '/api': {
        target: process.env.BETTER_HRBUST_API || 'http://127.0.0.1:9191',
        changeOrigin: true
      }
    }
  }
})
