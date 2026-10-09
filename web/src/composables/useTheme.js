import { ref, computed } from 'vue';
import { storageGetItem, storageSetItem } from '@/services/storage.js';

const THEME_KEY = 'better_hrbust_theme';

const currentTheme = ref(storageGetItem(THEME_KEY) || 'system');
const mediaQuery = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(prefers-color-scheme: dark)') : null;
const systemDark = ref(mediaQuery?.matches || false);

function applyTheme(theme) {
  const root = document.documentElement;
  const isDark = theme === 'dark' || (theme === 'system' && systemDark.value);
  if (isDark) {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
}

// Listen to OS theme changes when in system mode
if (mediaQuery) {
  mediaQuery.addEventListener('change', event => {
    systemDark.value = event.matches;
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
      return systemDark.value;
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
