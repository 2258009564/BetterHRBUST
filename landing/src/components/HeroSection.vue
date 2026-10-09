<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { gsap } from 'gsap'
import PlatformIcon from './PlatformIcon.vue'
import { ANDROID_APK, PLATFORM_LOGOS, WINDOWS_SETUP } from '../links.js'

const root = ref(null)
let ctx
let mm

// 识别访客系统：优先 UA-CH（Chromium 系），回退 UA 字符串；
// Windows / Android 直达对应安装包，其余系统引导到下方「随处可用」自行挑选。
const detectVisitorPlatform = () => {
  const platform = navigator.userAgentData?.platform ?? ''
  const ua = navigator.userAgent ?? ''

  if (/android/i.test(platform) || /android/i.test(ua)) return 'android'
  if (/windows/i.test(platform) || /windows|win32|win64/i.test(ua)) return 'windows'
  return 'other'
}

const visitorPlatform = detectVisitorPlatform()

const primaryDownload = computed(() => {
  if (visitorPlatform === 'android') {
    return { label: '下载 Android 安装包', href: ANDROID_APK, logo: '/Android_logo.svg', external: true }
  }
  if (visitorPlatform === 'windows') {
    return { label: '下载 Windows 安装版', href: WINDOWS_SETUP, logo: '/Windows_logo.svg', external: true }
  }
  return { label: '选择你的平台', href: '#platforms', logo: '', external: false }
})

// 入场动画：吉祥物 → 标题 → 简介 → 按钮 → 徽章 依次淡入上移；
// 吉祥物随后进入无限轻微浮动。仅在用户未偏好减弱动效时启用。
onMounted(() => {
  if (!root.value) return
  ctx = gsap.context(() => {
    mm = gsap.matchMedia()
    mm.add('(prefers-reduced-motion: no-preference)', () => {
      const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })
      tl.from('.hero-mascot', { autoAlpha: 0, y: 30, scale: 0.88, duration: 0.9 })
        .from('.hero-title', { autoAlpha: 0, y: 44, duration: 0.85 }, '-=0.6')
        .from('.hero-sub', { autoAlpha: 0, y: 26, duration: 0.7 }, '-=0.55')
        .from('.hero-actions', { autoAlpha: 0, y: 22, duration: 0.65 }, '-=0.5')
        .from('.hero-meta', { autoAlpha: 0, y: 16, duration: 0.6 }, '-=0.45')

      gsap.to('.hero-mascot-img', {
        y: 14,
        duration: 2.8,
        delay: 1,
        ease: 'sine.inOut',
        yoyo: true,
        repeat: -1
      })
    })
  }, root.value)
})

onUnmounted(() => {
  mm?.revert()
  ctx?.revert()
})
</script>

<template>
  <section
    ref="root"
    class="relative flex min-h-[100svh] flex-col items-center justify-center overflow-hidden px-6 pb-28 pt-20 text-center"
  >
    <!-- 背景装饰：细网格纹理 + 三处径向光斑 -->
    <div class="pointer-events-none absolute inset-0" aria-hidden="true">
      <div
        class="bg-grid absolute inset-0 [mask-image:radial-gradient(ellipse_62%_52%_at_50%_36%,black,transparent_78%)]"
      ></div>
      <div
        class="absolute left-1/2 top-[12%] h-[560px] w-[860px] max-w-[140vw] -translate-x-1/2 rounded-full bg-[radial-gradient(closest-side,rgba(99,102,241,0.34),rgba(139,92,246,0.14),transparent)] blur-3xl"
      ></div>
      <div
        class="absolute left-[8%] top-[58%] h-80 w-80 rounded-full bg-[radial-gradient(closest-side,rgba(34,211,238,0.16),transparent)] blur-3xl"
      ></div>
      <div
        class="absolute right-[6%] top-[22%] h-72 w-72 rounded-full bg-[radial-gradient(closest-side,rgba(139,92,246,0.2),transparent)] blur-3xl"
      ></div>
    </div>

    <!-- 吉祥物（大尺寸 + 柔和辉光 + 轻微浮动） -->
    <div class="hero-mascot relative">
      <div
        class="absolute inset-0 scale-150 rounded-full bg-[radial-gradient(closest-side,rgba(99,102,241,0.4),transparent)] blur-2xl"
        aria-hidden="true"
      ></div>
      <img
        src="/mascot.png"
        alt="BetterHRBUST 吉祥物"
        width="224"
        height="224"
        fetchpriority="high"
        draggable="false"
        class="hero-mascot-img relative h-44 w-44 select-none drop-shadow-[0_18px_50px_rgba(99,102,241,0.35)] sm:h-56 sm:w-56"
      />
    </div>

    <!-- 超大渐变标题 -->
    <h1
      class="hero-title text-gradient mt-8 text-5xl font-extrabold leading-[1.05] tracking-tight sm:text-7xl lg:text-8xl"
    >
      BetterHRBUST
    </h1>

    <!-- 一句话简介 -->
    <p class="hero-sub mt-6 max-w-2xl text-lg font-medium text-white/65 sm:text-xl">
      现代化哈理工教务在线 · 课程表 · 成绩 GPA · 考试日程
    </p>

    <!-- 真实下载 / 入口按钮：按访客系统动态区分（Windows / Android 直达安装包） -->
    <div
      class="hero-actions mt-10 flex w-full flex-col items-center justify-center gap-4 sm:w-auto sm:flex-row"
    >
      <a
        :href="primaryDownload.href"
        :target="primaryDownload.external ? '_blank' : undefined"
        :rel="primaryDownload.external ? 'noopener noreferrer' : undefined"
        class="inline-flex w-full items-center justify-center gap-2.5 rounded-2xl bg-gradient-to-r from-brand-600 via-brand-500 to-brand-400 px-8 py-4 text-base font-semibold text-white shadow-[0_20px_50px_-20px_rgba(99,102,241,0.95)] transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_28px_66px_-20px_rgba(99,102,241,1)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-300 sm:w-auto"
      >
        <img
          v-if="primaryDownload.logo"
          :src="primaryDownload.logo"
          alt=""
          draggable="false"
          class="h-5 w-auto select-none"
        />
        <PlatformIcon v-else name="download" class="h-5 w-5" />
        {{ primaryDownload.label }}
      </a>
      <a
        href="#platforms"
        class="glass-card inline-flex w-full items-center justify-center gap-3 rounded-2xl px-8 py-4 text-base font-semibold text-white/85 transition-all duration-300 hover:-translate-y-0.5 hover:border-white/25 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-300 sm:w-auto"
      >
        <!-- 六端品牌图标：象牙白圆形底托 + 轻微堆叠 -->
        <span class="flex shrink-0 items-center -space-x-2" aria-hidden="true">
          <img
            v-for="logo in PLATFORM_LOGOS"
            :key="logo"
            :src="logo"
            alt=""
            draggable="false"
            class="h-6 w-6 select-none rounded-full bg-ivory object-contain p-[3px] ring-1 ring-black/10"
          />
        </span>
        查看全部版本
      </a>
    </div>

    <!-- 一行小徽章 -->
    <ul
      class="hero-meta mt-10 flex flex-wrap items-center justify-center gap-x-7 gap-y-2.5 text-sm text-white/45"
    >
      <li class="flex items-center gap-1.5">
        <PlatformIcon name="shield" class="h-4 w-4 text-brand-300" />
        数据仅存本地
      </li>
      <li class="flex items-center gap-1.5">
        <PlatformIcon name="sparkles" class="h-4 w-4 text-brand-300" />
        MIT 开源免费
      </li>
    </ul>
  </section>
</template>
