/**
 * 运行环境判定（模块求值时一次性确定）
 *
 * 与 services/storage.js 的环境探测同源，抽成公共模块供平台专属功能
 * （如桌面端 Tauri 专属的更新模块）做显隐判定：
 *
 *  - 桌面端（Tauri）：initialization_script 注入 window.desktopWindow，
 *    withGlobalTauri 提供 window.__TAURI__；
 *  - 油猴：GM_getValue / GM_setValue / GM_deleteValue 可用；
 *  - Web：以上皆无。
 */

export const isTauri = typeof window !== 'undefined' && !!window.__TAURI__;

export const isDesktop = typeof window !== 'undefined' && !!window.desktopWindow;

export const isUserscript =
  typeof GM_getValue === 'function' &&
  typeof GM_setValue === 'function' &&
  typeof GM_deleteValue === 'function';

/** 是否为桌面端（Tauri）运行环境：更新模块等桌面专属功能的显隐依据 */
export const isDesktopApp = isDesktop || isTauri;
