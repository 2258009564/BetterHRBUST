<template>
  <div class="min-h-screen flex flex-col bg-[#f6f7f9] dark:bg-[#14161a] text-zinc-900 dark:text-zinc-100 antialiased selection:bg-zinc-200 selection:text-zinc-900 dark:selection:bg-zinc-800 dark:selection:text-zinc-100">
    <!-- 桌面端自定义标题栏（浏览器环境不渲染，占位高度由 --tb 驱动） -->
    <DesktopTitleBar />

    <!-- Responsive Backdrop: Click outside sidebar to close -->
    <Transition
      enter-active-class="transition-opacity duration-250 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="mobileOpen"
        class="fixed inset-0 bg-black/50 backdrop-blur-xs z-40 lg:hidden"
        @click="mobileOpen = false"
      />
    </Transition>

    <!-- Main Content Flow -->
    <div class="flex-1 flex">
      <!-- Collapsible / Responsive Overlay Sidebar -->
      <AppSidebar
        :isCollapsed="isCollapsed"
        :mobileOpen="mobileOpen"
        :activeTab="activeTab"
        @update:activeTab="handleTabSelect($event)"
        @close="mobileOpen = false"
      />

      <div class="flex-1 flex flex-col min-w-0">
        <AppHeader
          :isCollapsed="isCollapsed"
          @toggle-sidebar="handleToggleSidebar"
        />
        <main class="academic-main flex-1 min-w-0 px-4 sm:px-6 lg:px-8 pb-10 max-w-[1600px] w-full mx-auto">
          <slot />
        </main>
      </div>
    </div>

    <!-- Global Toast Container -->
    <UiToast />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import AppSidebar from './AppSidebar.vue';
import AppHeader from './AppHeader.vue';
import DesktopTitleBar from './DesktopTitleBar.vue';
import UiToast from '@/components/ui/UiToast.vue';

defineProps({
  activeTab: {
    type: String,
    default: 'dashboard'
  }
});

const emit = defineEmits(['update:activeTab']);

const isCollapsed = ref(false);
const mobileOpen = ref(false);

function handleToggleSidebar() {
  if (typeof window !== 'undefined' && window.innerWidth < 1024) {
    mobileOpen.value = !mobileOpen.value;
  } else {
    isCollapsed.value = !isCollapsed.value;
  }
}

function handleTabSelect(tabId) {
  emit('update:activeTab', tabId);
  mobileOpen.value = false;
}

function handleResize() {
  if (typeof window !== 'undefined' && window.innerWidth >= 1024 && mobileOpen.value) {
    mobileOpen.value = false;
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
});
</script>