import { isDesktopApp } from '@/utils/platform.js';
/** Tauri 的新窗口链接不会触发已有导航守卫，统一交给同窗口守卫打开系统浏览器。 */
export function installDesktopExternalLinks() {
  if (!isDesktopApp) return;
  document.addEventListener('click', event => {
    const link = event.target?.closest?.('a[href]');
    if (!link || event.defaultPrevented || event.button !== 0) return;
    const url = new URL(link.href, location.href);
    if (!['http:', 'https:'].includes(url.protocol) || url.origin === location.origin) return;
    event.preventDefault();
    window.location.assign(url.href);
  });
}
