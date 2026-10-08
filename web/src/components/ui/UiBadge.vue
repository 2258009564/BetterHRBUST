<template>
  <span
    :class="[
      'inline-flex items-center font-medium rounded-full select-none tracking-tight',
      sizeClasses,
      variantClasses,
      customClass
    ]"
  >
    <span
      v-if="dot"
      :class="['w-1.5 h-1.5 rounded-full mr-1.5 shrink-0', dotClasses]"
    />
    <slot />
  </span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  variant: {
    type: String,
    default: 'default' // 'default' | 'success' | 'warning' | 'danger' | 'info' | 'outline'
  },
  size: {
    type: String,
    default: 'md' // 'sm' | 'md'
  },
  dot: {
    type: Boolean,
    default: false
  },
  customClass: {
    type: String,
    default: ''
  }
});

const sizeClasses = computed(() => {
  return props.size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs';
});

const variantClasses = computed(() => {
  switch (props.variant) {
    case 'success':
      return 'bg-emerald-50 text-emerald-700 border border-emerald-200/60 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800/50';
    case 'warning':
      return 'bg-amber-50 text-amber-700 border border-amber-200/60 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800/50';
    case 'danger':
      return 'bg-rose-50 text-rose-700 border border-rose-200/60 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800/50';
    case 'info':
      return 'bg-sky-50 text-sky-700 border border-sky-200/60 dark:bg-sky-950/40 dark:text-sky-300 dark:border-sky-800/50';
    case 'outline':
      return 'bg-transparent text-zinc-600 border border-zinc-300 dark:text-zinc-400 dark:border-zinc-700';
    default:
      return 'bg-zinc-100 text-zinc-700 border border-zinc-200/80 dark:bg-zinc-800/70 dark:text-zinc-300 dark:border-zinc-700/80';
  }
});

const dotClasses = computed(() => {
  switch (props.variant) {
    case 'success': return 'bg-emerald-500';
    case 'warning': return 'bg-amber-500';
    case 'danger': return 'bg-rose-500';
    case 'info': return 'bg-sky-500';
    default: return 'bg-zinc-400 dark:bg-zinc-500';
  }
});
</script>