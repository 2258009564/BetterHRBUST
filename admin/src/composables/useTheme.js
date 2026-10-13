import { ref, computed } from 'vue';

// 与 Web 端相同的主题实现，仅使用独立存储键，避免同域部署时互相覆盖
const THEME_KEY = 'better_hrbust_admin_theme';

function readTheme() {
  try {
    return localStorage.getItem(THEME_KEY) || 'system';
  } catch {
    return 'system';
  }
}

const currentTheme = ref(readTheme());
const mediaQuery = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null;
const systemDark = ref(mediaQuery?.matches || false);

function applyTheme(theme) {
  const root = document.documentElement;
  const dark = theme === 'dark' || (theme === 'system' && systemDark.value);
  root.classList.toggle('dark', dark);
}

if (mediaQuery) {
  mediaQuery.addEventListener('change', event => {
    systemDark.value = event.matches;
    if (currentTheme.value === 'system') applyTheme('system');
  });
}

applyTheme(currentTheme.value);

export function useTheme() {
  const isDark = computed(() => {
    if (currentTheme.value === 'system') return systemDark.value;
    return currentTheme.value === 'dark';
  });

  function setTheme(theme) {
    currentTheme.value = theme;
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch {
      // 存储不可用时仅内存生效
    }
    applyTheme(theme);
  }

  function toggleTheme() {
    setTheme(isDark.value ? 'light' : 'dark');
  }

  return { isDark, themeMode: currentTheme, setTheme, toggleTheme };
}
