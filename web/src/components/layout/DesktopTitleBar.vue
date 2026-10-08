<template>
  <!-- 仅在桌面端（Electron preload / Tauri shim 注入 window.desktopWindow）渲染；浏览器环境不出现 -->
  <div
    v-if="available"
    class="tb-drag sticky top-0 z-[60] h-10 flex items-center justify-end px-3 select-none bg-[#f6f7f9]/95 dark:bg-[#14161a]/95 backdrop-blur-md"
    @mousedown="onBarMouseDown"
  >
    <!-- 左侧留空：仅作为可拖拽区域（双击可切换最大化） -->
    <div class="flex-1 h-full"></div>

    <!-- 右侧窗口控制按钮：与侧栏开关按钮同风格 -->
    <div class="flex items-center gap-2 tb-nodrag">
      <button type="button" title="最小化" :class="btnClass" @click="win.minimize()">
        <svg
          class="w-4 h-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
        >
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
      </button>
      <button
        type="button"
        :title="isMaximized ? '向下还原' : '最大化'"
        :class="btnClass"
        @click="win.toggleMaximize()"
      >
        <svg
          v-if="!isMaximized"
          class="w-4 h-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <rect x="6" y="6" width="12" height="12" rx="2.5" />
        </svg>
        <svg
          v-else
          class="w-4 h-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M9.5 5.5H16A2.5 2.5 0 0 1 18.5 8v6.5" />
          <rect x="5.5" y="9.5" width="9" height="9" rx="2.5" />
        </svg>
      </button>
      <button type="button" title="关闭" :class="closeClass" @click="win.close()">
        <svg
          class="w-4 h-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
        >
          <line x1="6" y1="6" x2="18" y2="18" />
          <line x1="18" y1="6" x2="6" y2="18" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

const win = typeof window !== 'undefined' ? window.desktopWindow : null;
const available = !!win;
// Tauri 桌面端：WebView2 不认 -webkit-app-region，拖拽/双击最大化改由 mousedown 手动处理
const isTauri = typeof window !== 'undefined' && !!window.__TAURI__;
const isMaximized = ref(false);
let unsubscribe = null;

function onBarMouseDown(e) {
  if (!isTauri || !win) return; // Electron：CSS 拖拽自动生效，无需处理
  if (e.buttons !== 1) return; // 仅响应左键
  if (e.target.closest('.tb-nodrag')) return; // 窗口控制按钮区域不参与拖拽
  if (e.detail === 2) win.toggleMaximize();
  else win.startDrag();
}

// 与 AppHeader 侧栏开关按钮同源的样式语言（圆形、白/黑底、细边框、按压缩放）
const btnBase =
  'w-8 h-8 rounded-full border flex items-center justify-center cursor-pointer transition-all duration-150 active:scale-95 shadow-sm bg-white text-zinc-800 border-zinc-200/80 dark:bg-black dark:text-zinc-100 dark:border-zinc-800';
const btnClass = `${btnBase} hover:bg-zinc-50 dark:hover:bg-zinc-900`;
const closeClass = `${btnBase} hover:bg-red-50 hover:text-red-600 hover:border-red-300 dark:hover:bg-red-500/10 dark:hover:text-red-400 dark:hover:border-red-500/30`;

onMounted(async () => {
  if (!win) return;
  try {
    isMaximized.value = await win.isMaximized();
  } catch {
    // 初始化失败保持默认非最大化
  }
  unsubscribe = win.onMaximizeChange((value) => {
    isMaximized.value = value;
  });
});

onUnmounted(() => {
  if (unsubscribe) unsubscribe();
});
</script>

<style scoped>
/* Electron 窗口拖拽：整条标题栏可拖动窗口（双击切换最大化），按钮区域排除 */
.tb-drag {
  -webkit-app-region: drag;
}
.tb-nodrag,
.tb-drag button {
  -webkit-app-region: no-drag;
}
</style>
