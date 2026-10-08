<template>
  <!-- 桌面端（Tauri）专属模块：显隐由 useUpdate 的 supported 决定（Web / 油猴不渲染） -->
  <UiCard v-if="supported" title="版本更新">
    <div class="space-y-4">
      <!-- 当前版本 + 手动检查入口 -->
      <div class="flex items-center justify-between gap-3">
        <div class="min-w-0">
          <div class="text-sm font-bold text-zinc-900 dark:text-zinc-100">
            当前版本
            <span class="font-mono text-zinc-500 dark:text-zinc-400">v{{ currentVersion || '未知' }}</span>
          </div>
        </div>
        <UiButton
          variant="outline"
          size="sm"
          :disabled="isChecking"
          @click="check()"
        >
          <template v-if="isChecking" #prefix>
            <svg
              class="animate-spin h-3.5 w-3.5 text-current"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path
                class="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          </template>
          <template v-else-if="isUpToDate" #prefix>
            <Icon name="check" custom-class="w-3.5 h-3.5" />
          </template>
          <!-- 最新状态由按钮自身表达（对号 + 已是最新），不再额外占一行状态文案 -->
          <span v-if="!isChecking">{{ isUpToDate ? '已是最新' : '检查更新' }}</span>
        </UiButton>
      </div>

      <!-- 发现新版本：版本号 + 更新日志 + 前往下载 -->
      <div
        v-if="hasUpdate"
        class="rounded-xl border border-amber-300/70 dark:border-amber-500/30 bg-amber-50/60 dark:bg-amber-500/10 p-4 space-y-3"
      >
        <div class="flex items-center flex-wrap gap-x-2 gap-y-1">
          <Icon name="sparkles" custom-class="w-4 h-4 text-amber-600 dark:text-amber-400" />
          <span class="text-sm font-bold text-amber-800 dark:text-amber-300">
            发现新版本 v{{ latestRelease.version }}
          </span>
          <span v-if="publishedText" class="text-[11px] text-amber-700/70 dark:text-amber-400/60">
            {{ publishedText }} 发布
          </span>
        </div>

        <!-- 更新日志：限高滚动，避免长日志撑破布局 -->
        <p
          v-if="latestRelease.notes"
          class="max-h-40 overflow-y-auto whitespace-pre-wrap break-words text-xs leading-relaxed text-amber-900/85 dark:text-amber-200/80"
        >{{ latestRelease.notes }}</p>
        <p v-else class="text-xs text-amber-900/70 dark:text-amber-200/60">
          新版本已发布，点击下方按钮前往 Release 页面查看详情并下载安装包。
        </p>

        <UiButton size="sm" @click="openRelease()">
          <template #prefix>
            <Icon name="download" custom-class="w-3.5 h-3.5" />
          </template>
          前往下载
        </UiButton>
      </div>

      <!-- 检测失败：仅手动检测时提示（启动静默检测失败不打扰） -->
      <p v-else-if="status === 'error'" class="text-xs text-rose-600 dark:text-rose-400">
        {{ errorMessage }}
      </p>
    </div>
  </UiCard>
</template>

<script setup>
import { computed } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import Icon from '@/components/icons/Icon.vue';
import { useUpdate } from '@/composables/useUpdate.js';

const {
  status,
  currentVersion,
  latestRelease,
  errorMessage,
  hasUpdate,
  supported,
  check,
  openRelease
} = useUpdate();

const isChecking = computed(() => status.value === 'checking');
const isUpToDate = computed(() => status.value === 'uptodate');

const publishedText = computed(() => {
  const raw = latestRelease.value?.publishedAt;
  if (!raw) return '';
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
});
</script>
