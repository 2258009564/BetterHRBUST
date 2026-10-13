/** 时间格式化：统一使用中文本地化，秒级精度足够展示反馈时间线 */
export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString('zh-CN', { hour12: false });
}

/** 相对时间：仅用于列表的辅助信息（今天 / 昨天 / 具体日期） */
export function formatRelative(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';

  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const diff = startOfToday - date.getTime();

  if (diff <= 0) {
    return date.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' });
  }
  if (diff <= 24 * 60 * 60 * 1000) return '昨天';
  if (diff <= 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / (24 * 60 * 60 * 1000))} 天前`;
  return date.toLocaleDateString('zh-CN');
}

/** 列表摘要：折叠空白并截断，避免超长文本撑破卡片 */
export function excerpt(value, max = 72) {
  const text = String(value || '').replace(/\s+/g, ' ').trim();
  return text.length > max ? `${text.slice(0, max)}…` : text;
}
