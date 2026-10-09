<script setup>
import HeroSection from './components/HeroSection.vue'
import PlatformsSection from './components/PlatformsSection.vue'
import SiteFooter from './components/SiteFooter.vue'
</script>

<template>
  <div
    class="relative min-h-screen bg-ink-950 text-[#f0f2f5] selection:bg-brand-500/40 selection:text-white"
  >
    <main>
      <HeroSection />
      <PlatformsSection />
    </main>
    <SiteFooter />
  </div>
</template>
