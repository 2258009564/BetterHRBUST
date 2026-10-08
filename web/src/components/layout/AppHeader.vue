<template>
  <header
    class="h-14 px-6 sticky top-[var(--tb)] z-20 bg-transparent flex items-center justify-between pointer-events-none"
  >
    <!-- Left: Sidebar Toggle + Data Refresh -->
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
          <!-- Plump rounded window frame -->
          <rect x="3" y="3.5" width="18" height="17" rx="3.5" />
          <!-- Substantial left panel divider line (no arrow in middle) -->
          <line x1="9.5" y1="3.5" x2="9.5" y2="20.5" />
        </svg>
      </button>

      <!-- 数据刷新：登录后教务数据不再自动获取，仅此处手动触发全量同步 -->
      <button
        v-if="isLoggedIn || isSessionExpired"
        type="button"
        class="w-10 h-10 rounded-full bg-[#f6f7f9] text-zinc-800 hover:bg-[#eceef2] border border-zinc-200/80 dark:bg-[#14161a] dark:text-zinc-100 dark:border-zinc-800 dark:hover:bg-[#1e2127] cursor-pointer transition-all duration-150 flex items-center justify-center shadow-md active:scale-95 disabled:opacity-70 disabled:cursor-wait"
        :title="`手动刷新教务数据（上次同步：${lastSyncText}）`"
        :disabled="syncing"
        @click="handleRefresh"
      >
        <Icon name="refresh" customClass="w-5 h-5" :class="syncing ? 'animate-spin' : ''" />
      </button>
    </div>

    <!-- Right: Session Expired Notice Pill with Re-login Button -->
    <div v-if="shouldShowSessionBanner" class="pointer-events-auto">
      <div
        class="flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-700 dark:text-amber-400 text-xs shadow-sm backdrop-blur-md"
      >
        <span class="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
        <span class="font-medium">登录状态已失效</span>
        <button
          type="button"
          class="ml-1 px-2 py-1 rounded-full font-medium text-xs transition-colors cursor-pointer active:scale-95 hover:bg-amber-500/15"
          @click="dismissSessionPrompt"
        >
          忽略
        </button>
        <button
          type="button"
          class="px-3 py-1 rounded-full bg-amber-500 hover:bg-amber-600 text-white font-medium text-xs transition-colors cursor-pointer shadow-xs active:scale-95"
          @click="openLoginModal"
        >
          重新登录
        </button>
      </div>
    </div>
  </header>
</template>

<script setup>
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';
import { useToast } from '@/composables/useToast.js';

const { isLoggedIn, isSessionExpired, shouldShowSessionBanner, dismissSessionPrompt, openLoginModal, navigateTo } =
  useSession();
const { syncing, lastSyncText, refreshAll } = useAcademicData();
const { showToast } = useToast();

defineProps({
  isCollapsed: {
    type: Boolean,
    default: false
  }
});

defineEmits(['toggle-sidebar']);

async function handleRefresh() {
  const res = await refreshAll();
  if (res.expired) {
    showToast({
      title: '登录状态已失效',
      message: '请重新登录教务在线后再刷新数据',
      type: 'warning'
    });
    navigateTo('login');
    return;
  }
  showToast({
    title: res.success ? '数据已同步' : '部分数据同步失败',
    message: res.message,
    type: res.success ? 'success' : 'warning'
  });
}
</script>
