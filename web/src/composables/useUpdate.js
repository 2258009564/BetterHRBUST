/**
 * 版本更新检测状态（桌面端 / Tauri 专属）
 *
 * 单例状态（模块级 ref，跨组件共享，仿 useTheme 的写法）：
 *  - 启动时由 App.vue 调 maybeAutoCheck() 静默检测一次（按天节流，失败静默）；
 *  - 设置页更新模块挂载后可直接读取状态并调 check() 手动检测；
 *  - 检测口径：GitHub Release 最新版本 > 当前应用版本 时进入 available 态，
 *    由 UI 展示版本号与更新日志，用户点击按钮跳转 Release 页自行下载。
 *
 * 当前版本来源：Tauri 运行时 window.__TAURI__.app.getVersion()（权威），
 * 失败时回退到构建期注入的 __APP_VERSION__（见 vite.config.mjs 的 define）。
 */

import { computed, ref } from 'vue';
import { storageGetItem, storageSetItem } from '@/services/storage.js';
import { RELEASES_PAGE, fetchLatestRelease, isNewerVersion } from '@/services/update.js';
import { isDesktopApp, isUserscript } from '@/utils/platform.js';

const CHECKED_AT_KEY = 'better_hrbust_update_checked_at';

/** 构建期注入的版本号（Vite define）；未注入时为空串 */
const FALLBACK_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '';

// ---------------- 单例状态 ----------------

/** 'idle' | 'checking' | 'uptodate' | 'available' | 'error' */
const status = ref('idle');
const currentVersion = ref(FALLBACK_VERSION);
/** 检测到的新版本信息：{ version, title, notes, releaseUrl, publishedAt } | null */
const latestRelease = ref(null);
const errorMessage = ref('');
const lastCheckedAt = ref(Number(storageGetItem(CHECKED_AT_KEY)) || 0);

/** 应用版本是否已从 Tauri 运行时解析过（仅解析一次） */
let versionResolved = false;
/** 进行中的检测任务（并发去重：重复调用返回同一 Promise） */
let pending = null;

function dateKey(timestamp) {
  const d = new Date(timestamp);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

/** 今天是否已自动检测过（按天节流，避免频繁请求 GitHub API） */
const checkedToday = computed(
  () => lastCheckedAt.value > 0 && dateKey(lastCheckedAt.value) === dateKey(Date.now())
);

const hasUpdate = computed(() => status.value === 'available' && !!latestRelease.value);

/**
 * 调试开关：置为 true 可在 Web（浏览器）环境显示更新模块，便于查看 UI 效果。
 * 产品要求为「仅桌面端 Tauri 显示」；油猴环境始终不显示。
 */
const SHOW_ON_WEB = false;

/** 本端是否显示更新模块（仅桌面端 Tauri；油猴始终不显示） */
const supported = computed(() => isDesktopApp || (SHOW_ON_WEB && !isUserscript));

async function resolveCurrentVersion() {
  if (versionResolved) return currentVersion.value;
  versionResolved = true;

  const app = typeof window !== 'undefined' && window.__TAURI__ ? window.__TAURI__.app : null;
  if (app && typeof app.getVersion === 'function') {
    try {
      const version = await app.getVersion();
      if (version) currentVersion.value = version;
    } catch {
      // 读取失败：保持构建期注入版本作为回退
    }
  }
  return currentVersion.value;
}

/**
 * 执行一次更新检测
 * @param {{ silent?: boolean }} options silent 为 true 时（启动自动检测）
 *        无更新或失败都不改变界面状态，仅在有新版本时展示提示
 */
function check({ silent = false } = {}) {
  if (pending) return pending;

  status.value = 'checking';
  errorMessage.value = '';

  pending = (async () => {
    try {
      const current = await resolveCurrentVersion();
      const release = await fetchLatestRelease();

      lastCheckedAt.value = Date.now();
      storageSetItem(CHECKED_AT_KEY, String(lastCheckedAt.value));

      if (release && isNewerVersion(release.version, current)) {
        latestRelease.value = release;
        status.value = 'available';
      } else {
        latestRelease.value = null;
        // 启动静默检测无新版本时保持初始态，避免用户还没主动查询就显示结果
        status.value = silent ? 'idle' : 'uptodate';
      }
    } catch (err) {
      latestRelease.value = null;
      if (silent) {
        status.value = 'idle';
      } else {
        errorMessage.value = err && err.message ? `检查更新失败：${err.message}` : '检查更新失败，请稍后重试';
        status.value = 'error';
      }
    } finally {
      pending = null;
    }
  })();

  return pending;
}

/** 启动时的静默自动检测（非桌面端 / 今日已检测 / 检测中时直接跳过） */
async function maybeAutoCheck() {
  if (!isDesktopApp) return;
  if (pending) return;
  if (checkedToday.value) return;
  await check({ silent: true });
}

/**
 * 打开 Release 页面（交系统默认浏览器）
 * 桌面端主动发起同窗口导航，由 Rust 侧 on_navigation 守卫拦截非同源
 * http(s) 并转系统浏览器（见 desktop-tauri/src-tauri/src/lib.rs）。
 */
function openRelease(url) {
  const target = url || latestRelease.value?.releaseUrl || RELEASES_PAGE;
  if (!target) return;
  if (isDesktopApp) {
    window.location.assign(target);
    return;
  }
  window.open(target, '_blank', 'noopener,noreferrer');
}

export function useUpdate() {
  return {
    // 状态
    status,
    currentVersion,
    latestRelease,
    errorMessage,
    lastCheckedAt,
    checkedToday,
    hasUpdate,
    supported,

    // 行为
    check,
    maybeAutoCheck,
    openRelease
  };
}
