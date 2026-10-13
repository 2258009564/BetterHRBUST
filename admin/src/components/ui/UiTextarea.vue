<template>
  <div class="relative w-full">
    <textarea
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :rows="rows"
      :maxlength="maxlength > 0 ? maxlength : undefined"
      :class="[
        'w-full rounded-lg transition-all duration-150 outline-none resize-y leading-relaxed',
        'bg-zinc-50/60 dark:bg-[#1f2127] border border-zinc-200 dark:border-zinc-700/60',
        'text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 dark:placeholder-zinc-500',
        'focus:border-zinc-400 dark:focus:border-zinc-600 focus:ring-2 focus:ring-zinc-400/10 dark:focus:ring-zinc-600/20',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        size === 'sm' ? 'px-3 py-1.5 text-xs' : 'px-3.5 py-2.5 text-sm',
        customClass
      ]"
      @blur="$emit('blur', $event)"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <div
      v-if="$slots.hint"
      class="mt-1.5 flex items-center justify-between gap-3 text-[11px] text-zinc-400 dark:text-zinc-500"
    >
      <slot name="hint" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  rows: {
    type: Number,
    default: 6
  },
  // 为 0 表示不限制输入长度（长度仍由页面自行校验）
  maxlength: {
    type: Number,
    default: 0
  },
  size: {
    type: String,
    default: 'md'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  readonly: {
    type: Boolean,
    default: false
  },
  customClass: {
    type: String,
    default: ''
  }
});

defineEmits(['update:modelValue', 'blur']);
</script>
