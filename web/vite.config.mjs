import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { readFileSync } from 'node:fs'
import path from 'node:path'

// 构建期注入应用版本（桌面端更新模块的版本回退值，权威值仍取 Tauri 运行时 getVersion()）
const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

const academicProxy = {
  target: 'http://jwzx.hrbust.edu.cn',
  changeOrigin: true,
  autoRewrite: true,
  cookieDomainRewrite: '',
  headers: {
    Host: 'jwzx.hrbust.edu.cn',
    Referer: 'http://jwzx.hrbust.edu.cn/academic/'
  },
  configure: (proxy, _options) => {
    proxy.on('proxyRes', (proxyRes, req, res) => {
      const location = proxyRes.headers['location'];
      if (location) {
        // 关键：教务系统 302 跳转会带上完整域名 http://jwzx.hrbust.edu.cn/academic/...
        // 现代浏览器会触发 HTTPS 自动升级或跨域拦截，由于教务系统未开启 443 HTTPS 端口会导致 ERR_CONNECTION_CLOSED
        // 此处将重定向地址统一重写为本地相对路径 /academic/...
        proxyRes.headers['location'] = location.replace(
          /^https?:\/\/jwzx\.hrbust\.edu\.cn(?::\d+)?\/academic\/?/i,
          '/academic/'
        );
      }
    });
  }
};

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version)
  },
  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api/probe': {
        target: 'http://127.0.0.1:7788',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api\/probe/, '')
      },
      '/academic': academicProxy
    }
  },
  preview: {
    port: 5173,
    proxy: {
      '/academic': academicProxy
    }
  }
})
