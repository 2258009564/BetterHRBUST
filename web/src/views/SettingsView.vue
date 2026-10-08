<template>
  <div class="space-y-6 max-w-4xl">
    <!-- Academic Affairs Online Connection Status -->
    <UiCard title="教务在线连接状态">
      <div class="flex items-center justify-between gap-3">
        <div class="flex items-center gap-2.5">
          <span
            class="w-2.5 h-2.5 rounded-full"
            :class="isLoggedIn ? 'bg-emerald-500' : 'bg-amber-500'"
          />
          <span class="text-sm font-bold text-zinc-900 dark:text-zinc-100">
            {{ isLoggedIn ? '教务在线已连接并已登录' : '教务在线网关已配置（未登录）' }}
          </span>
        </div>
        <UiButton variant="outline" size="sm" @click="openLoginModal">
          {{ isLoggedIn ? '重新登录' : '登录教务在线' }}
        </UiButton>
      </div>
    </UiCard>

    <!-- Theme Settings Card -->
    <UiCard title="外观与个性化设置">
      <div class="flex items-center justify-between p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800">
        <div>
          <div class="text-sm font-bold text-zinc-900 dark:text-zinc-100">深色模式 / 浅色模式</div>
          <div class="text-xs text-zinc-500 mt-0.5">现代化低刺激黑白配色系统 (曜石黑与柔和灰白)</div>
        </div>
        <UiTabs
          :items="[
            { label: '跟随系统', value: 'system' },
            { label: '浅色', value: 'light' },
            { label: '深色', value: 'dark' }
          ]"
          :modelValue="themeMode"
          @update:modelValue="setTheme($event)"
        />
      </div>
    </UiCard>

    <!-- System Architecture & Probe Summary Card -->
    <UiCard title="关于 BetterHRBUST">
      <div class="text-xs text-zinc-600 dark:text-zinc-400 space-y-3 leading-relaxed">
        <p>
          <strong>BetterHRBUST</strong> 是针对哈尔滨理工大学教务在线（清华教育在线 / 优慕课 URP 架构）的现代化套壳客户端。
        </p>
        <p>
          本项目为开源软件，遵循 <a
            href="https://github.com/Glassous/BetterHRBUST/blob/main/LICENSE"
            target="_blank"
            rel="noopener noreferrer"
            class="font-semibold text-zinc-800 dark:text-zinc-200 underline decoration-zinc-300 dark:decoration-zinc-600 underline-offset-2 hover:decoration-zinc-500 dark:hover:decoration-zinc-400 transition-colors"
          >MIT License</a> 协议发布，任何人都可以自由获取、修改与分发源码。欢迎前往仓库提交 Issue 或 Pull Request 参与共建，也欢迎点亮 Star 支持项目发展。
        </p>
        <div class="pt-1">
          <a
            href="https://github.com/Glassous/BetterHRBUST"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-2.5 px-3.5 py-2 rounded-lg border border-zinc-200/80 dark:border-zinc-700/80 bg-zinc-50/60 dark:bg-zinc-800/40 hover:bg-zinc-100 dark:hover:bg-zinc-800 hover:border-zinc-300 dark:hover:border-zinc-600 transition-all duration-150 group"
          >
            <Icon name="github" custom-class="w-4 h-4 text-zinc-900 dark:text-zinc-100" />
            <span class="font-medium text-zinc-800 dark:text-zinc-200">GitHub 开源仓库</span>
            <span class="font-mono text-[11px] text-zinc-500 dark:text-zinc-500 group-hover:text-zinc-600 dark:group-hover:text-zinc-400 transition-colors">Glassous/BetterHRBUST</span>
            <Icon name="chevron-right" custom-class="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-600 group-hover:translate-x-0.5 transition-transform" />
          </a>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import Icon from '@/components/icons/Icon.vue';
import { useTheme } from '@/composables/useTheme.js';
import { useSession } from '@/composables/useSession.js';

const { themeMode, setTheme } = useTheme();
const { isLoggedIn, openLoginModal } = useSession();
</script>