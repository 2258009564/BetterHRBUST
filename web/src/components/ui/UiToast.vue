<template>
  <Teleport to="body">
    <div class="fixed bottom-5 right-5 z-50 flex flex-col gap-2 pointer-events-none max-w-sm w-full">
      <TransitionGroup
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="transform translate-y-2 opacity-0"
        enter-to-class="transform translate-y-0 opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="pointer-events-auto p-3.5 rounded-xl border shadow-lg flex items-start gap-3 bg-white/95 dark:bg-[#16181d]/95 backdrop-blur-md border-zinc-200 dark:border-zinc-800 text-zinc-900 dark:text-zinc-100"
        >
          <span
            :class="[
              'w-2 h-2 rounded-full mt-1.5 shrink-0',
              toast.type === 'success' ? 'bg-emerald-500' :
              toast.type === 'danger' ? 'bg-rose-500' :
              toast.type === 'warning' ? 'bg-amber-500' : 'bg-sky-500'
            ]"
          />
          <div class="flex-1 min-w-0">
            <h4 class="text-xs font-semibold tracking-tight">{{ toast.title }}</h4>
            <p v-if="toast.message" class="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">{{ toast.message }}</p>
          </div>
          <button
            type="button"
            class="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300 p-0.5 cursor-pointer"
            @click="removeToast(toast.id)"
          >
            <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useToast } from '@/composables/useToast.js';
const { toasts, removeToast } = useToast();
</script>