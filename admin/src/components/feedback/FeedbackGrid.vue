<template>
  <div
    v-if="loading && items.length === 0"
    class="p-10 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] flex items-center justify-center gap-2.5 text-xs text-zinc-500"
  >
    <Icon name="refresh" customClass="w-4 h-4 animate-spin" />
    正在加载反馈…
  </div>

  <div
    v-else-if="items.length === 0"
    class="p-14 rounded-xl border border-dashed border-zinc-200 dark:border-zinc-800 text-center"
  >
    <Icon name="inbox" customClass="w-6 h-6 mx-auto text-zinc-300 dark:text-zinc-600" />
    <p class="text-xs text-zinc-500 mt-2.5">{{ keyword ? '没有匹配的反馈' : '暂时还没有反馈' }}</p>
  </div>

  <div v-else class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-4">
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      class="group h-full flex flex-col text-left p-4 rounded-xl border cursor-pointer transition-all duration-150 border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] hover:border-zinc-300 dark:hover:border-zinc-600 hover:shadow-md hover:-translate-y-0.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-400/30"
      @click="$emit('open', item)"
    >
      <div class="flex items-center gap-2">
        <span
          class="w-2 h-2 rounded-full shrink-0"
          :class="item.isRead ? 'bg-zinc-300 dark:bg-zinc-600' : 'bg-rose-500'"
        />
        <span class="text-[11px] font-medium" :class="item.isRead ? 'text-zinc-400' : 'text-rose-500'">
          {{ item.isRead ? '已读' : '未读' }}
        </span>
        <span class="ml-auto text-[11px] text-zinc-400">{{ formatRelative(item.createdAt) }}</span>
      </div>

      <p class="mt-2.5 text-xs leading-relaxed text-zinc-700 dark:text-zinc-300 line-clamp-3">
        {{ excerpt(item.content, 96) }}
      </p>

      <div class="mt-auto pt-3 flex items-center gap-2 text-[11px] text-zinc-400">
        <UiBadge :variant="item.anonymous ? 'default' : 'info'" size="sm">
          {{ item.anonymous ? '匿名' : (item.signature || '实名') }}
        </UiBadge>
        <span v-if="item.contact" class="inline-flex items-center gap-1 min-w-0">
          <Icon
            name="mail"
            customClass="w-3 h-3 shrink-0"
            :class="item.email ? 'text-emerald-500' : ''"
          />
          <span class="truncate">{{ item.contact }}</span>
        </span>
        <span v-else class="inline-flex items-center gap-1 shrink-0">
          <Icon name="mail" customClass="w-3 h-3 shrink-0" />
          未填联系方式
        </span>
        <Icon
          name="chevron-right"
          customClass="w-3.5 h-3.5 ml-auto shrink-0 text-zinc-400 opacity-0 -translate-x-1 transition-all duration-150 group-hover:opacity-100 group-hover:translate-x-0"
        />
      </div>
    </button>
  </div>
</template>

<script setup>
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import { excerpt, formatRelative } from '@/utils/format.js';

defineProps({
  items: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  // 仅用于空状态文案：有搜索词时提示「没有匹配的反馈」
  keyword: {
    type: String,
    default: ''
  }
});

defineEmits(['open']);
</script>
