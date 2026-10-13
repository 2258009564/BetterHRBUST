<template>
  <section class="rounded-xl border border-zinc-200/80 dark:border-zinc-800 overflow-hidden bg-white dark:bg-[#1c1e24]">
    <button
      type="button"
      :disabled="!collapsible"
      :class="[
        'w-full flex items-center gap-2 px-4 py-3 text-left transition-colors duration-150',
        collapsible ? 'cursor-pointer hover:bg-zinc-50 dark:hover:bg-[#1f2127]' : 'cursor-default'
      ]"
      @click="toggle"
    >
      <Icon name="mail" customClass="w-3.5 h-3.5 text-zinc-400" />
      <span class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">回复历史</span>
      <UiBadge variant="info" size="sm">{{ sorted.length }} 次</UiBadge>
      <span class="ml-auto min-w-0 truncate text-[11px] text-zinc-400 dark:text-zinc-500">
        {{ collapsible ? `最近 ${formatRelative(latest.sentAt)} · ${latest.adminName || '管理员'}` : formatDateTime(latest.sentAt) }}
      </span>
      <Icon
        v-if="collapsible"
        :name="expanded ? 'chevron-up' : 'chevron-down'"
        customClass="w-3.5 h-3.5 shrink-0 text-zinc-400"
      />
    </button>

    <div
      class="grid transition-[grid-template-rows] duration-300 ease-out"
      :class="expanded ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
    >
      <div class="overflow-hidden">
        <div class="divide-y divide-zinc-100 dark:divide-zinc-800/80 border-t border-zinc-100 dark:border-zinc-800/80">
          <article v-for="(reply, index) in sorted" :key="reply.id || index" class="px-4 py-3.5 space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <UiBadge variant="default" size="sm">{{ reply.adminName || '管理员' }}</UiBadge>
              <span class="text-[11px] text-zinc-400 dark:text-zinc-500">第 {{ sorted.length - index }} 次回复</span>
              <span class="ml-auto text-[11px] text-zinc-400 dark:text-zinc-500">{{ formatDateTime(reply.sentAt) }}</span>
            </div>

            <p class="text-xs font-semibold text-zinc-800 dark:text-zinc-200 break-words">
              {{ reply.subject || '（无标题）' }}
            </p>
            <p class="text-xs leading-[1.8] text-zinc-600 dark:text-zinc-400 whitespace-pre-wrap break-words">
              {{ reply.body }}
            </p>
            <p class="inline-flex items-center gap-1 text-[11px] text-zinc-400 dark:text-zinc-500 break-all">
              <Icon name="mail" customClass="w-3 h-3 shrink-0" />
              已发送至 {{ reply.to }}
            </p>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import { formatDateTime, formatRelative } from '@/utils/format.js';

const props = defineProps({
  // 所属反馈 ID：切换反馈时重置折叠状态
  feedbackId: {
    type: String,
    default: ''
  },
  replies: {
    type: Array,
    default: () => []
  }
});

// 最新的回复排在最前，符合「先看最近一次处理」的阅读习惯
const sorted = computed(() => [...props.replies].sort((a, b) => new Date(b.sentAt || 0) - new Date(a.sentAt || 0)));
const latest = computed(() => sorted.value[0] || {});
const collapsible = computed(() => sorted.value.length > 1);
const expanded = ref(false);

// 单条直接展示，多条默认折叠；仅在切换反馈时重置，新增回复不会打断当前展开状态
watch(
  () => props.feedbackId,
  () => {
    expanded.value = sorted.value.length <= 1;
  },
  { immediate: true }
);

function toggle() {
  if (!collapsible.value) return;
  expanded.value = !expanded.value;
}
</script>
