<script setup>
import PlatformIcon from './PlatformIcon.vue'
import { GITHUB_REPO } from '../links.js'
</script>

<template>
  <footer class="relative border-t border-white/10 px-6 py-10">
    <div
      class="mx-auto flex max-w-5xl flex-col items-center justify-between gap-4 text-sm text-white/40 sm:flex-row"
    >
      <p>© 2026 BetterHRBUST · MIT License</p>
      <p class="flex items-center gap-2">
        <PlatformIcon name="shield" class="h-4 w-4 text-brand-300/80" />
        数据仅存本地，不上传任何第三方
      </p>
      <a
        :href="GITHUB_REPO"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-2 text-white/55 transition-colors hover:text-white"
      >
        <PlatformIcon name="github" class="h-4 w-4" />
        Glassous/BetterHRBUST
      </a>
    </div>
  </footer>
</template>
