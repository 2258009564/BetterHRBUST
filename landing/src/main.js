import { createApp } from 'vue'
async function start() {
  const params = new URLSearchParams(location.search)
  if (params.has('client-preview')) {
    // 独立 iframe 使用可复现的示例时间和数据；真实组件与业务计算不改动。
    const RealDate = Date
    const instant = RealDate.parse('2026-10-09T09:00:00+08:00')
    window.Date = class extends RealDate {
      constructor(...args) { super(...(args.length ? args : [instant])) }
      static now() { return instant }
    }
    // 真实组件发出的教务请求不会离开本页。
    const fetchOriginal = window.fetch.bind(window)
    window.fetch = (input, options) => {
      const url = new URL(typeof input === 'string' ? input : input.url, location.href)
      if (url.pathname.includes('/academic/') || url.pathname.includes('/__academic/')) {
        if (url.pathname.includes('eva/index/resultlist')) return Promise.resolve(new Response('<table class="infolist_tab"><tr><td>林老师</td><td>数据结构</td><td>未评价</td><td><a href="questionnaire.jsdo?id=demo">评价</a></td></tr><tr><td>陈老师</td><td>软件工程</td><td>已评价</td><td></td></tr></table>', { headers: { 'Content-Type': 'text/html; charset=utf-8' } }))
        return Promise.reject(new Error('展示页面不会连接教务系统'))
      }
      return fetchOriginal(input, options)
    }
    await import('./preview.css')
    const { default: App } = await import('./ActualClientPreview.vue')
    createApp(App).mount('#app')
  } else {
    await import('./release.css'); await import('./client-demo.css')
    const { default: App } = await import('./ReleasePage.vue')
    createApp(App).mount('#app')
  }
}
start()
