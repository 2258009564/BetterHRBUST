import { ref, computed } from 'vue';
import { storageGetItem, storageSetItem } from '@/services/storage.js';

const THEME_KEY = 'better_hrbust_theme';

const currentTheme = ref(storageGetItem(THEME_KEY) || 'system');

function applyTheme(theme) {
  const root = document.documentElement;
  const isDark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  if (isDark) {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
}

// Listen to OS theme changes when in system mode
if (typeof window !== 'undefined' && window.matchMedia) {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  mediaQuery.addEventListener('change', () => {
    if (currentTheme.value === 'system') {
      applyTheme('system');
    }
  });
}

// Initial apply
applyTheme(currentTheme.value);

export function useTheme() {
  const isDark = computed(() => {
    if (currentTheme.value === 'system') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
    return currentTheme.value === 'dark';
  });

  function setTheme(theme) {
    currentTheme.value = theme;
    storageSetItem(THEME_KEY, theme);
    applyTheme(theme);
  }

  function toggleTheme() {
    setTheme(isDark.value ? 'light' : 'dark');
  }

  return {
    isDark,
    themeMode: currentTheme,
    setTheme,
    toggleTheme
  };
}