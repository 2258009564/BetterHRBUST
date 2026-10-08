<template>
  <header
    class="h-14 px-6 sticky top-0 z-20 bg-transparent flex items-center justify-between pointer-events-none"
  >
    <!-- Left: Plump Sidebar Toggle Button with Black/White Circular Background -->
    <div class="pointer-events-auto">
      <button
        type="button"
        class="w-10 h-10 rounded-full bg-white text-zinc-800 hover:bg-zinc-50 border border-zinc-200/80 dark:bg-black dark:text-zinc-100 dark:border-zinc-800 dark:hover:bg-zinc-900 cursor-pointer transition-all duration-150 flex items-center justify-center shadow-md active:scale-95"
        :title="isCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="$emit('toggle-sidebar')"
      >
        <svg
          class="w-5 h-5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.4"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <!-- Plump rounded window frame -->
          <rect x="3" y="3.5" width="18" height="17" rx="3.5" />
          <!-- Substantial left panel divider line (no arrow in middle) -->
          <line x1="9.5" y1="3.5" x2="9.5" y2="20.5" />
        </svg>
      </button>
    </div>

    <!-- Right: Session Expired Notice Pill with Re-login Button -->
    <div v-if="isSessionExpired" class="pointer-events-auto">
      <div
        class="flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-700 dark:text-amber-400 text-xs shadow-sm backdrop-blur-md"
      >
        <span class="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
        <span class="font-medium">登录状态已失效 (当前为离线数据)</span>
        <button
          type="button"
          class="ml-1 px-3 py-1 rounded-full bg-amber-500 hover:bg-amber-600 text-white font-medium text-xs transition-colors cursor-pointer shadow-xs active:scale-95"
          @click="openLoginModal"
        >
          重新登录
        </button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { useSession } from '@/composables/useSession.js';

const { isSessionExpired, openLoginModal } = useSession();

defineProps({
  isCollapsed: {
    type: Boolean,
    default: false
  }
});

defineEmits(['toggle-sidebar']);
</script>