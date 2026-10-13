<template>
  <aside
    :class="[
      'h-screen flex flex-col bg-white/95 dark:bg-[#14161a]/95 border-r border-zinc-200/80 dark:border-zinc-800/70 backdrop-blur-md select-none',
      'lg:sticky lg:top-0 lg:shrink-0 lg:z-30',
      'lg:transition-[width] lg:duration-300 lg:ease-in-out',
      isCollapsedState ? 'lg:w-[76px]' : 'lg:w-[260px]',
      'fixed top-0 bottom-0 left-0 z-50 w-[260px] transition-transform duration-300 ease-in-out',
      mobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
    ]"
  >
    <div class="h-18 flex items-center px-3.5 overflow-hidden">
      <div class="w-full flex items-center justify-between">
        <div class="flex items-center min-w-0">
          <div class="w-12 h-12 flex items-center justify-center shrink-0">
            <div class="w-9 h-9 rounded-full overflow-hidden shadow-xs shrink-0 flex items-center justify-center bg-zinc-100 dark:bg-zinc-800">
              <img
                :src="logoUrl"
                alt="BetterHRBUST"
                class="w-full h-full object-cover rounded-full shrink-0"
              />
            </div>
          </div>
          <div v-if="!isCollapsedState" class="ml-1.5 min-w-0 flex-1 whitespace-nowrap overflow-hidden">
            <div class="font-bold text-base text-zinc-900 dark:text-zinc-100 tracking-tight truncate">
              BetterHRBUST
            </div>
            <div class="text-[11px] text-zinc-400 dark:text-zinc-500 truncate">意见反馈管理端</div>
          </div>
        </div>

        <button
          type="button"
          class="lg:hidden p-2 -mr-1 rounded-xl text-zinc-500 hover:text-zinc-900 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:text-zinc-100 dark:hover:bg-zinc-800/80 cursor-pointer transition-colors shrink-0"
          title="关闭侧边栏"
          @click="$emit('close')"
        >
          <Icon name="x" customClass="w-5 h-5" />
        </button>
      </div>
    </div>

    <nav class="flex-1 overflow-y-auto overflow-x-hidden px-3.5 py-2 space-y-4">
      <div v-for="group in navGroups" :key="group.title">
        <div class="h-5 flex items-center px-2 mb-1.5 overflow-hidden select-none">
          <span
            v-if="!isCollapsedState"
            class="text-xs font-semibold uppercase tracking-wider text-zinc-400 dark:text-zinc-500 whitespace-nowrap overflow-hidden truncate"
          >
            {{ group.title }}
          </span>
        </div>
        <div class="space-y-1">
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            :title="isCollapsedState ? item.label : undefined"
            :class="[
              'w-full h-11 flex items-center rounded-xl text-sm font-medium transition-colors duration-150 cursor-pointer select-none group overflow-hidden',
              activeTab === item.id
                ? 'bg-zinc-800 text-zinc-50 dark:bg-zinc-700/80 dark:text-zinc-100 shadow-xs'
                : 'text-zinc-600 hover:text-zinc-900 hover:bg-zinc-100/90 dark:text-zinc-300 dark:hover:text-white dark:hover:bg-zinc-800/50'
            ]"
            @click="$emit('update:activeTab', item.id)"
          >
            <div class="w-12 h-11 flex items-center justify-center shrink-0">
              <Icon
                :name="item.icon"
                :custom-class="[
                  'w-5 h-5 shrink-0 transition-transform duration-150',
                  activeTab === item.id ? '' : 'group-hover:scale-105'
                ]"
              />
            </div>
            <span
              v-if="!isCollapsedState"
              class="ml-1.5 truncate whitespace-nowrap overflow-hidden select-none flex-1 text-left"
            >
              {{ item.label }}
            </span>
            <span
              v-if="!isCollapsedState && item.id === 'feedback' && unreadCount > 0"
              class="mr-3 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-rose-500 text-white shrink-0"
            >
              {{ unreadCount > 99 ? '99+' : unreadCount }}
            </span>
          </button>
        </div>
      </div>
    </nav>

    <div class="p-3.5 overflow-hidden">
      <div class="flex items-center gap-2.5">
        <div class="w-9 h-9 rounded-full bg-zinc-200 dark:bg-zinc-700 text-zinc-800 dark:text-zinc-200 font-bold text-sm flex items-center justify-center shrink-0">
          {{ avatarText }}
        </div>
        <div v-if="!isCollapsedState" class="min-w-0 flex-1 leading-tight">
          <div class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate">
            {{ currentUser?.nickname || currentUser?.username || '未登录' }}
          </div>
          <div class="text-xs text-zinc-400 truncate mt-0.5">
            {{ roleLabel }}
          </div>
        </div>
        <button
          v-if="!isCollapsedState"
          type="button"
          class="p-2 rounded-xl text-zinc-400 hover:text-rose-600 hover:bg-rose-500/10 dark:hover:text-rose-400 cursor-pointer transition-colors shrink-0"
          title="退出登录"
          @click="$emit('logout')"
        >
          <Icon name="logout" customClass="w-4 h-4" />
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import Icon from '@/components/icons/Icon.vue';
import logoUrl from '@/assets/BetterHRBUST.png';
import { useAuth } from '@/composables/useAuth.js';
import { useFeedbackStats } from '@/composables/useFeedbackStats.js';

const props = defineProps({
  activeTab: {
    type: String,
    default: 'feedback'
  },
  isCollapsed: {
    type: Boolean,
    default: false
  },
  mobileOpen: {
    type: Boolean,
    default: false
  }
});

defineEmits(['update:activeTab', 'close', 'logout']);

const { currentUser, isSuperAdmin } = useAuth();
const { unreadCount } = useFeedbackStats();

const isMobile = ref(false);
function checkMobile() {
  if (typeof window !== 'undefined') isMobile.value = window.innerWidth < 1024;
}
onMounted(() => {
  checkMobile();
  window.addEventListener('resize', checkMobile);
});
onUnmounted(() => window.removeEventListener('resize', checkMobile));

const isCollapsedState = computed(() => (isMobile.value ? false : props.isCollapsed));

const avatarText = computed(() => {
  const name = currentUser.value?.nickname || currentUser.value?.username || '?';
  return String(name).slice(0, 1).toUpperCase();
});

const roleLabel = computed(() =>
  isSuperAdmin.value ? '超级管理员 · 全部权限' : '管理员 · 反馈阅读'
);

const navGroups = computed(() => [
  {
    title: '反馈',
    items: [{ id: 'feedback', label: '意见反馈阅读', icon: 'inbox' }]
  },
  {
    title: '账号',
    items: [
      ...(isSuperAdmin.value ? [{ id: 'admins', label: '管理员管理', icon: 'users' }] : []),
      { id: 'password', label: '账号安全', icon: 'lock' }
    ]
  }
]);
</script>
