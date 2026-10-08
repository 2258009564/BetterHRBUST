/**
 * 课程周次匹配
 *
 * 教务系统的周次表达式形如「1-16周(单)」「3-5,9-16周(双)」「1-17周」。
 * 解析单/双周修饰与数字区间，判断课程在指定教学周是否有课。
 */

/**
 * 判断课程在指定教学周是否上课。
 * 周次表达式为空时视为全学期均有课。
 * @param {{ weeks?: string }} course
 * @param {number} week 目标教学周 (1~26)
 * @returns {boolean}
 */
export function isCourseActiveInWeek(course, week) {
  const expr = String(course?.weeks || '');
  if (!expr) return true;

  const isOdd = expr.includes('单');
  const isEven = expr.includes('双');
  if (isOdd && week % 2 === 0) return false;
  if (isEven && week % 2 !== 0) return false;

  const parts = expr.match(/\d+(?:-\d+)?/g) || [];
  for (const p of parts) {
    const [start, end] = p.split('-').map(Number);
    const to = end || start;
    if (week >= start && week <= to) return true;
  }
  return parts.length === 0;
}
