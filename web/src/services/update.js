/**
 * GitHub Release 版本检测（桌面端更新模块专用）
 *
 * 数据源：GitHub REST API `GET /repos/{owner}/{repo}/releases/latest`
 *  - 请求使用绝对 URL，绕过桌面端本地反代（Rust 侧仅代理 /academic 与 /__app）；
 *  - 未认证请求限流 60 次/小时，调用方需保证低频（见 composables/useUpdate.js 的按天节流）；
 *  - 仓库尚无任何 Release 时 GitHub 返回 404，属正常状态：按「暂无更新」处理，不视为错误。
 *
 * 版本口径：tag 形如 `v1.0.0` / `desktop-v1.0.0` / `1.0` 均可，
 * 统一剥离非数字前缀后按 `.` 分段做数值比较（兼容段数不同的写法）。
 */

const REPO = 'Glassous/BetterHRBUST';

export const RELEASE_API = `https://api.github.com/repos/${REPO}/releases/latest`;
export const RELEASES_PAGE = `https://github.com/${REPO}/releases`;

/**
 * 剥离 v / desktop-v / android-v 等前缀，仅保留数字版本段
 * （"desktop-v1.0.0" → "1.0.0"，"1.0" → "1.0"，无法识别时返回空串）
 */
export function normalizeVersion(raw) {
  if (typeof raw !== 'string') return '';
  const match = raw.match(/\d+(?:\.\d+)*/);
  return match ? match[0] : '';
}

/**
 * 语义化版本比较：a > b 返回 1，a < b 返回 -1，相等返回 0
 * 段数不同时按缺位补 0 处理（"1.0" 与 "1.0.0" 视为相同）
 */
export function compareVersions(a, b) {
  const left = normalizeVersion(a)
    .split('.')
    .map((seg) => Number(seg) || 0);
  const right = normalizeVersion(b)
    .split('.')
    .map((seg) => Number(seg) || 0);
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i += 1) {
    const l = left[i] || 0;
    const r = right[i] || 0;
    if (l !== r) return l > r ? 1 : -1;
  }
  return 0;
}

/**
 * candidate 是否严格新于 current（版本相同不算有更新）
 * 任一侧无法解析出版本号时返回 false：版本未知宁可不提示，也不误报「有新版本」
 */
export function isNewerVersion(candidate, current) {
  if (!normalizeVersion(candidate) || !normalizeVersion(current)) return false;
  return compareVersions(candidate, current) > 0;
}

/**
 * 拉取最新 Release 信息
 *
 * @returns {Promise<{version: string, title: string, notes: string, releaseUrl: string, publishedAt: string}|null>}
 *          仓库暂无 Release（404）或 tag 无法解析出版本号时返回 null
 */
export async function fetchLatestRelease() {
  const res = await fetch(RELEASE_API, {
    headers: {
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28'
    },
    cache: 'no-store'
  });

  if (res.status === 404) return null;
  if (res.status === 403 || res.status === 429) {
    throw new Error('请求过于频繁，请稍后再试');
  }
  if (!res.ok) {
    throw new Error(`GitHub API 返回 ${res.status}`);
  }

  const data = await res.json();
  const version = normalizeVersion(data?.tag_name || data?.name || '');
  if (!version) return null;

  return {
    version,
    title: (data.name || data.tag_name || '').trim(),
    notes: (data.body || '').trim(),
    releaseUrl: data.html_url || RELEASES_PAGE,
    publishedAt: data.published_at || ''
  };
}
