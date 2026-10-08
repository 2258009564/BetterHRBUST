<template>
  <button
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :class="[
      'relative inline-flex shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-zinc-400/20',
      size === 'sm' ? 'h-5 w-9' : 'h-6 w-11',
      modelValue ? 'bg-zinc-900 dark:bg-zinc-100' : 'bg-zinc-300 dark:bg-zinc-700'
    ]"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span
      :class="[
        'pointer-events-none inline-block transform rounded-full bg-white dark:bg-zinc-900 shadow ring-0 transition duration-200 ease-in-out',
        size === 'sm' ? 'h-4 w-4 mt-0.5' : 'h-5 w-5 mt-0.5',
        modelValue
          ? (size === 'sm' ? 'translate-x-4 ml-0.5' : 'translate-x-5 ml-0.5')
          : 'translate-x-0.5'
      ]"
    />
  </button>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: 'md'
  }
});

defineEmits(['update:modelValue']);
</script>