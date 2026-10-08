<template>
  <div
    :class="[
      'rounded-xl border transition-all duration-150',
      'bg-white/95 dark:bg-[#1c1e24] border-zinc-200/80 dark:border-zinc-800/70 shadow-[0_1px_3px_rgba(0,0,0,0.03)]',
      hoverable ? 'hover:border-zinc-300 dark:hover:border-zinc-700/80 hover:shadow-md' : '',
      customClass
    ]"
  >
    <div
      v-if="$slots.header || title"
      class="px-5 py-3.5 border-b border-zinc-100 dark:border-zinc-800/70 flex items-center justify-between"
    >
      <slot name="header">
        <div class="flex items-center gap-2">
          <slot name="title-prefix" />
          <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 tracking-tight">{{ title }}</h3>
        </div>
        <slot name="header-action" />
      </slot>
    </div>
    <div :class="['p-5', bodyClass]">
      <slot />
    </div>
    <div
      v-if="$slots.footer"
      class="px-5 py-3 border-t border-zinc-100 dark:border-zinc-800/70 bg-zinc-50/50 dark:bg-zinc-800/30 rounded-b-xl"
    >
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  hoverable: {
    type: Boolean,
    default: false
  },
  customClass: {
    type: String,
    default: ''
  },
  bodyClass: {
    type: String,
    default: ''
  }
});
</script>