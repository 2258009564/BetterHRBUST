<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { GITHUB_REPO, RELEASES, USERSCRIPT } from './links.js'
import ClientDemo from './ClientDemo.vue'
const logo = `${import.meta.env.BASE_URL}BetterHRBUST.png`
const asset = name => `${import.meta.env.BASE_URL}${name}`
const pageRoot = ref(null)
const letters = Array.from('BetterHRBUST')
const ghostLines = ['校园日常，清晰有序', '一周安排，一眼看清', '把注意力，留给学习']
gsap.registerPlugin(ScrollTrigger)
let motion
const dark = ref(false)
const release = ref(null)
const version = computed(() => release.value?.tag_name || 'GitHub 最新发行版')
const platforms = [
  { name: 'Android', key: 'android', logo: 'Android_logo.svg', description: '原生移动体验，课表随身查看。', requirement: 'Android 11 及以上', pattern: /android.*\.apk$/i },
  { name: 'Windows', key: 'windows', logo: 'Windows_logo.svg', description: '独立桌面窗口，学习时随手打开。', requirement: 'Windows 10 / 11', pattern: /windows.*setup\.exe$/i },
  { name: '油猴脚本', key: 'userscript', logo: 'Tampermonkey_logo.svg', description: '在原教务网站中，换一种清晰的体验。', requirement: 'Edge / Chrome + Tampermonkey' }
]
function download(platform) {
  if (platform.key === 'userscript') return USERSCRIPT
  return release.value?.assets?.find(asset => platform.pattern.test(asset.name))?.browser_download_url || `${RELEASES}/latest`
}
function toggleTheme() {
  dark.value = !dark.value
  document.documentElement.dataset.theme = dark.value ? 'dark' : 'light'
  try { localStorage.setItem('betterhrbust-site-theme', dark.value ? 'dark' : 'light') } catch {}
}
onMounted(async () => {
  try {
    dark.value = localStorage.getItem('betterhrbust-site-theme') === 'dark'
    document.documentElement.dataset.theme = dark.value ? 'dark' : 'light'
  } catch {}
  try {
    const response = await fetch('https://api.github.com/repos/Glassous/BetterHRBUST/releases/latest', { signal: AbortSignal.timeout(8000) })
    if (response.ok) release.value = await response.json()
  } catch { /* API 不可用时保留发行页入口。 */ }
})
onMounted(() => {
  motion = gsap.matchMedia()
  motion.add({ animate: '(prefers-reduced-motion: no-preference)', finePointer: '(hover: hover) and (pointer: fine)' }, context => {
    if (!context.conditions.animate) return
    const root = pageRoot.value
    const hero = root.querySelector('.hero')
    gsap.timeline({ defaults: { ease: 'power3.out' } })
      .from('.hero-content > .eyebrow', { y: 15, opacity: 0, duration: .65 })
      .from('.hero-letter', { y: 55, opacity: 0, rotationX: 30, duration: .9, stagger: .035 }, '-=.4')
      .from('.hero-tagline, .hero-description', { y: 24, opacity: 0, duration: .8, stagger: .12 }, '-=.65')
      .from('.hero-actions, .hero-meta', { y: 18, opacity: 0, duration: .65, stagger: .1 }, '-=.55')
    gsap.to('.shape', { y: i => (i % 2 ? -1 : 1) * (14 + i * 2), rotation: i => i % 3 ? 6 : -5,
      duration: i => 7 + i * .8, repeat: -1, yoyo: true, ease: 'sine.inOut', stagger: .2 })
    const words = root.querySelectorAll('.ghost-line')
    gsap.set(words, { opacity: 0 })
    const cycle = gsap.timeline({ repeat: -1 })
    words.forEach(word => {
      cycle.fromTo(word, { opacity: 0, y: 35 }, { opacity: 1, y: 0, duration: 1.8, ease: 'sine.out' })
        .to(word, { opacity: 0, y: -25, duration: 1.5, ease: 'sine.in' }, '+=4')
    })
    gsap.to('.scroll-cue .mouse', { y: 5, duration: 1.2, repeat: -1, yoyo: true, ease: 'sine.inOut' })
    gsap.to('.hero-content', { y: -45, opacity: .15, ease: 'none', scrollTrigger: { trigger: hero, start: 'top top', end: 'bottom top', scrub: 1 } })
    gsap.to('.hero-ghost', { y: -70, x: -65, ease: 'none', scrollTrigger: { trigger: hero, start: 'top top', end: 'bottom top', scrub: .8 } })
    gsap.from('.section-heading > *', { y: 30, opacity: 0, stagger: .12, duration: .8,
      scrollTrigger: { trigger: '.section-heading', start: 'top 85%', once: true } })
    gsap.from('.workspace-preview', { y: 55, opacity: 0, duration: 1,
      scrollTrigger: { trigger: '.workspace-preview', start: 'top 85%', once: true } })
    root.querySelectorAll('.feature-row').forEach(row => {
      const copy = row.querySelector('.feature-copy')
      const preview = row.querySelector('.app-preview, .visual-panel')
      const reveal = gsap.timeline({ scrollTrigger: { trigger: row, start: 'top 82%', once: true } })
      reveal.from(copy.children, { y: 32, opacity: 0, duration: .85, stagger: .1, ease: 'power3.out' })
      if (preview) {
        reveal.from(preview, { y: 65, rotation: row.classList.contains('reverse') ? -2 : 2,
          scale: .96, opacity: 0, duration: 1.15, ease: 'power3.out' }, .15)
        gsap.fromTo(preview.firstElementChild, { y: 6 }, { y: -6, ease: 'none',
          scrollTrigger: { trigger: row, start: 'top bottom', end: 'bottom top', scrub: 1 }, immediateRender: false })
      }
    })
    gsap.from('.download-card', { y: 35, opacity: 0, stagger: .12, duration: .75,
      scrollTrigger: { trigger: '.download-grid', start: 'top 85%', once: true } })
    gsap.from('.guide-section li', { y: 25, opacity: 0, stagger: .12, duration: .7,
      scrollTrigger: { trigger: '.guide-section ol', start: 'top 85%', once: true } })
    if (context.conditions.finePointer) {
      const x = gsap.quickTo(root.querySelector('.geometry'), 'x', { duration: 1.4, ease: 'power3.out' })
      const y = gsap.quickTo(root.querySelector('.geometry'), 'y', { duration: 1.4, ease: 'power3.out' })
      const move = event => { const box = hero.getBoundingClientRect(); x((event.clientX - box.left - box.width / 2) * .012); y((event.clientY - box.top - box.height / 2) * .012) }
      const leave = () => { x(0); y(0) }
      hero.addEventListener('pointermove', move, { passive: true }); hero.addEventListener('pointerleave', leave)
      return () => { hero.removeEventListener('pointermove', move); hero.removeEventListener('pointerleave', leave) }
    }
  }, pageRoot.value)
})
onUnmounted(() => motion?.revert())
</script>

<template>
  <a class="skip-link" href="#main">跳转到内容</a>
  <header class="site-header">
    <a class="brand" href="#"><img :src="logo" alt="" width="28" height="28" />BetterHRBUST</a>
    <nav aria-label="主要导航"><a href="#features">功能</a><a href="#downloads">下载</a><a href="#guide">使用指南</a><a :href="GITHUB_REPO" target="_blank" rel="noopener">GitHub ↗</a><button class="theme-toggle" :aria-label="dark ? '切换浅色主题' : '切换深色主题'" @click="toggleTheme">{{ dark ? '☾' : '☼' }}</button></nav>
  </header>
  <main id="main" ref="pageRoot">
    <section class="hero" aria-labelledby="hero-title">
      <div class="geometry" aria-hidden="true"><i v-for="n in 12" :key="n" :class="`shape shape-${n}`"></i></div>
      <div class="hero-ghost" aria-hidden="true"><span v-for="line in ghostLines" :key="line" class="ghost-line">{{ line }}</span></div>
      <div class="hero-content"><span class="eyebrow">为哈理工的每一天</span><h1 id="hero-title" aria-label="BetterHRBUST"><span v-for="(letter, index) in letters" :key="index" class="hero-letter" aria-hidden="true">{{ letter }}</span></h1><p class="hero-tagline">教务日常，清晰有序。</p><p class="hero-description">课表、成绩与考试安排，都在你熟悉的地方。<br />让校园生活多一点从容。</p><div class="hero-actions"><a class="button primary" href="#downloads">立即下载 ↗</a><a class="button quiet" href="#guide">使用指南</a><a class="button quiet" :href="GITHUB_REPO" target="_blank" rel="noopener">GitHub 仓库 ↗</a></div><div class="hero-meta"><span>开源 · MIT</span><span>Android / Windows / 浏览器</span></div></div>
      <a class="scroll-cue" href="#features"><span class="mouse"></span>向下探索</a>
    </section>
    <section id="features" class="features section-container">
      <div class="overview-row"><div class="section-heading"><span class="eyebrow">概览</span><h2>重要的事，<br />一眼就看清。</h2><p>GPA、学分进度与今日课程，一屏掌握。<br />从概览开始，安排你的校园日常。</p></div>
      <div class="workspace-preview app-preview"><ClientDemo view="dashboard" compact :dark="dark" /></div></div>
      <article class="feature-row">
        <div class="feature-copy"><span class="feature-number">01 / 课表</span><h3>一周节奏，<br />心中有数。</h3><p>按周查看课程、教师和地点，区分单双周。打开课表，就知道今天该去哪里。</p><div class="tags"><span>每周课表</span><span>单双周</span><span>课程详情</span></div></div>
        <div class="app-preview"><ClientDemo view="timetable" compact :dark="dark" /></div>
      </article>
      <article class="feature-row reverse">
        <div class="feature-copy"><span class="feature-number">02 / GPA 分析</span><h3>每一份努力，<br />都有清晰的刻度。</h3><p>汇总课程成绩、学分与五分制 GPA。必修课加权统计，成绩明细与学分进度放在一起，让学业情况更容易理解。</p><div class="tags"><span>五分制 GPA</span><span>加权平均</span><span>成绩明细</span></div></div>
        <div class="app-preview"><ClientDemo view="gpa" compact :dark="dark" /></div>
      </article>
      <article class="feature-row">
        <div class="feature-copy"><span class="feature-number">03 / 资料查找</span><h3>需要的资料，<br />不必翻来翻去。</h3><p>收录教务处公开下载栏目，按标题和分类搜索，直达学校原文与附件。学生证补办、缓考、四六级等表格，可以从这里查找。</p><div class="tags"><span>9 个分类</span><span>标题搜索</span><span>原站附件</span></div></div>
        <div class="app-preview"><ClientDemo view="resources" compact :dark="dark" /></div>
      </article>
      <article class="feature-row reverse">
        <div class="feature-copy"><span class="feature-number">04 / 手机端与多端使用</span><h3>换个设备，<br />还是熟悉的日常。</h3><p>手机随身查看，桌面专注学习，浏览器直接使用。登录后同步数据，之后从本地缓存读取；每日首次打开或手动刷新时更新。教学评价助手按你配置的选项逐门处理，并核对学校返回的结果。</p><div class="tags"><span>离线缓存</span><span>原生 Android</span><span>教学评价</span></div></div>
        <div class="devices-demo visual-panel"><div class="mini-window"><div class="window-bar"><i></i><i></i><i></i></div><div class="mini-content"><img :src="logo" alt="" /><span>BetterHRBUST</span><div class="mini-lines"><i></i><i></i><i></i></div></div></div><div class="mini-phone"><div class="phone-notch"></div><img :src="logo" alt="" /><strong>今天，也从容一点。</strong><div class="mini-lines"><i></i><i></i><i></i></div></div></div>
      </article>
    </section>
    <section id="downloads" class="download-section section-container"><span class="eyebrow">从这里开始</span><h2>选一个你习惯的方式。</h2><p class="section-subtitle">{{ version }} · 下载来自原仓库的正式发行版</p><div class="download-grid"><article v-for="platform in platforms" :key="platform.key" class="download-card"><img class="platform-icon" :src="asset(platform.logo)" alt="" width="34" height="34" /><h3>{{ platform.name }}</h3><p>{{ platform.description }}</p><small>{{ platform.requirement }}</small><a class="button secondary" :href="download(platform)" target="_blank" rel="noopener">{{ platform.key === 'userscript' ? '安装脚本' : '下载 ' + platform.name }} ↗</a></article></div><p class="download-note">便携版、历史版本和更新内容：<a :href="RELEASES" target="_blank" rel="noopener">查看全部发行版 ↗</a></p></section>
    <section id="guide" class="guide-section section-container"><span class="eyebrow">第一次使用</span><h2>三步，回到你的校园日常。</h2><ol><li><span>01</span><div><h3>选择客户端</h3><p>下载 Android 或 Windows 版本。浏览器用户先安装 Tampermonkey，再安装油猴脚本。</p></div></li><li><span>02</span><div><h3>连接并登录</h3><p>使用教务学号、密码与验证码登录。网络无法访问学校教务时，需连接校园网或学校 VPN。</p></div></li><li><span>03</span><div><h3>同步后，轻松查看</h3><p>登录后同步，日常使用本地缓存。评教需学校开放评价，再由你配置和确认提交。</p></div></li></ol><a class="text-link" :href="`${GITHUB_REPO}#readme`" target="_blank" rel="noopener">阅读完整使用说明 ↗</a></section>
    <section class="closing section-container"><h2>把注意力，留给学习。</h2><p>BetterHRBUST 是开源的第三方教务客户端，非学校官方产品。<br />欢迎一起改进，也欢迎反馈你遇到的问题。</p><a class="button primary" :href="`${GITHUB_REPO}/issues`" target="_blank" rel="noopener">反馈与建议 ↗</a></section>
  </main>
  <footer class="site-footer"><span>BetterHRBUST</span><span>MIT License · 项目贡献者</span><a :href="GITHUB_REPO" target="_blank" rel="noopener">GitHub ↗</a><a href="#">回到顶部 ↑</a></footer>
</template>
