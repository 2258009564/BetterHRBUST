import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// 介绍页为纯静态单页：不请求教务系统，无需任何代理，
// 仅注册 vue() + tailwindcss() 两个插件（与 web/ 端同一套技术栈）。
export default defineConfig({
  base: './',
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), './src')
    }
  },
  // 与 web/ 端开发端口（5173）错开，便于两个项目同时启动
  server: {
    port: 5174,
    host: '0.0.0.0',
    allowedHosts: true
  },
  preview: {
    port: 5174,
    host: '0.0.0.0',
    allowedHosts: true
  }
})
