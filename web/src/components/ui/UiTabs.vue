<template>
  <div
    :class="[
      'inline-flex p-1 rounded-lg bg-zinc-100/90 dark:bg-[#1a1c22] border border-zinc-200/60 dark:border-zinc-800/60',
      customClass
    ]"
  >
    <button
      v-for="tab in items"
      :key="tab.value"
      type="button"
      :class="[
        'px-3 py-1.5 text-xs font-medium rounded-md transition-all duration-150 cursor-pointer select-none flex items-center gap-1.5',
        modelValue === tab.value
          ? 'bg-white dark:bg-zinc-700/80 text-zinc-900 dark:text-zinc-100 shadow-[0_1px_2px_rgba(0,0,0,0.06)]'
          : 'text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-200'
      ]"
      @click="$emit('update:modelValue', tab.value)"
    >
      <span v-if="tab.badge" class="px-1.5 py-0.2 rounded-full text-[10px] bg-zinc-200 dark:bg-zinc-700">
        {{ tab.badge }}
      </span>
      {{ tab.label }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    required: true // [{ label: '大节', value: 'combine', badge?: 2 }]
  },
  modelValue: {
    type: [String, Number],
    required: true
  },
  customClass: {
    type: String,
    default: ''
  }
});

defineEmits(['update:modelValue']);
</script>