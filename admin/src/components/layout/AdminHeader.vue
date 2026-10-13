<template>
  <header class="h-14 px-6 sticky top-0 z-20 bg-transparent flex items-center justify-between pointer-events-none">
    <div class="pointer-events-auto flex items-center gap-2.5">
      <button
        type="button"
        class="w-10 h-10 rounded-full bg-[#f6f7f9] text-zinc-800 hover:bg-[#eceef2] border border-zinc-200/80 dark:bg-[#14161a] dark:text-zinc-100 dark:border-zinc-800 dark:hover:bg-[#1e2127] cursor-pointer transition-all duration-150 flex items-center justify-center shadow-md active:scale-95"
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
          <rect x="3" y="3.5" width="18" height="17" rx="3.5" />
          <line x1="9.5" y1="3.5" x2="9.5" y2="20.5" />
        </svg>
      </button>
    </div>

    <div class="pointer-events-auto flex items-center gap-2.5">
      <span
        v-if="isSuperAdmin"
        class="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-900/5 dark:bg-zinc-100/10 border border-zinc-200/70 dark:border-zinc-800 text-[11px] font-medium text-zinc-600 dark:text-zinc-300"
      >
        <Icon name="shield" customClass="w-3.5 h-3.5" />
        超级管理员
      </span>
      <button
        type="button"
        class="w-10 h-10 rounded-full border border-zinc-200 dark:border-zinc-800 bg-[#f6f7f9] dark:bg-[#14161a] flex items-center justify-center cursor-pointer shadow-md active:scale-95"
        :title="isDark ? '切换浅色模式' : '切换深色模式'"
        :aria-label="isDark ? '切换浅色模式' : '切换深色模式'"
        @click="toggleTheme"
      >
        <Icon :name="isDark ? 'sun' : 'moon'" customClass="w-5 h-5" />
      </button>
    </div>
  </header>
</template>

<script setup>
import Icon from '@/components/icons/Icon.vue';
import { useTheme } from '@/composables/useTheme.js';
import { useAuth } from '@/composables/useAuth.js';

defineProps({
  isCollapsed: {
    type: Boolean,
    default: false
  }
});

defineEmits(['toggle-sidebar']);

const { isDark, toggleTheme } = useTheme();
const { isSuperAdmin } = useAuth();
</script>
