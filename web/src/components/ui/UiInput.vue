<template>
  <div class="relative w-full">
    <div
      v-if="$slots.prefix"
      class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-zinc-400 dark:text-zinc-500"
    >
      <slot name="prefix" />
    </div>

    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :class="[
        'w-full rounded-lg text-sm transition-all duration-150 outline-none',
        'bg-zinc-50/60 dark:bg-[#1f2127] border border-zinc-200 dark:border-zinc-700/60',
        'text-zinc-900 dark:text-zinc-100 placeholder-zinc-400 dark:placeholder-zinc-500',
        'focus:border-zinc-400 dark:focus:border-zinc-600 focus:ring-2 focus:ring-zinc-400/10 dark:focus:ring-zinc-600/20',
        'disabled:opacity-50 disabled:cursor-not-allowed',
        $slots.prefix ? 'pl-9' : 'pl-3.5',
        $slots.suffix || clearable ? 'pr-9' : 'pr-3.5',
        size === 'sm' ? 'py-1.5 text-xs' : 'py-2',
        customClass
      ]"
      @input="$emit('update:modelValue', $event.target.value)"
      @keydown.enter="$emit('enter', $event)"
    />

    <div
      v-if="$slots.suffix || (clearable && modelValue)"
      class="absolute inset-y-0 right-0 pr-2.5 flex items-center text-zinc-400 hover:text-zinc-600 dark:text-zinc-500 dark:hover:text-zinc-300"
    >
      <button
        v-if="clearable && modelValue"
        type="button"
        class="p-0.5 rounded cursor-pointer hover:bg-zinc-200/50 dark:hover:bg-zinc-700/50"
        @click="$emit('update:modelValue', '')"
      >
        <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
      </button>
      <slot name="suffix" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  size: {
    type: String,
    default: 'md'
  },
  clearable: {
    type: Boolean,
    default: false
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

defineEmits(['update:modelValue', 'enter']);
</script>