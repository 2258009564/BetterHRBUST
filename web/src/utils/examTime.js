/**
 * 考试时间解析（Web 端统一口径）
 *
 * 教务系统的考试时间形如「2026-01-05 14:00-16:00」「2026年1月5日 14:00~16:00」，
 * 个别场次缺时间只有日期。概览页（DashboardView）与考试页（ExamView）
 * 共用本模块，避免两处正则口径不一致导致「永远待开考」之类的判定漂移。
 */

/**
 * 解析考试时间文本
 * @param {string} timeText 教务原始时间串
 * @returns {{date:Date, start:Date|null, end:Date|null}|null}
 *   date 为当天零点；start/end 为带时分的事件起止（缺时间时为 null）
 */
export function parseExamDateTime(timeText) {
  const text = String(timeText || '');
  const dm = text.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
  if (!dm) return null;
  const date = new Date(parseInt(dm[1], 10), parseInt(dm[2], 10) - 1, parseInt(dm[3], 10));
  if (Number.isNaN(date.getTime())) return null;

  const tm = text.match(/(\d{1,2}):(\d{2})\s*(?:[-–—~～]|至|to)\s*(\d{1,2}):(\d{2})/);
  if (!tm) return { date, start: null, end: null };
  const start = new Date(date);
  start.setHours(parseInt(tm[1], 10), parseInt(tm[2], 10), 0, 0);
  const end = new Date(date);
  end.setHours(parseInt(tm[3], 10), parseInt(tm[4], 10), 0, 0);
  return { date, start, end };
}
