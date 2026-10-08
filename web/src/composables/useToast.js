import { ref } from 'vue';

const toasts = ref([]);
let idCounter = 0;

export function useToast() {
  function showToast({ title, message = '', type = 'info', duration = 3200 }) {
    const id = ++idCounter;
    toasts.value.push({ id, title, message, type });
    if (duration > 0) {
      setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  }

  function removeToast(id) {
    const idx = toasts.value.findIndex(t => t.id === id);
    if (idx !== -1) {
      toasts.value.splice(idx, 1);
    }
  }

  return {
    toasts,
    showToast,
    removeToast
  };
}