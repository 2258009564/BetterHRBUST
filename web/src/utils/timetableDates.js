import { semesterOn } from './schoolCalendar.js';
/** 按当前教学周所在周的周一计算所选周，使用本地日期避免时区导致跨日。 */
export function timetableDayDate(currentWeek, selectedWeek, day, today = new Date()) {
  if (day < 1 || day > 7 || Number.isNaN(today.getTime())) return '';
  // currentWeek 仅在未知学期的兜底路径参与计算，保留旧的 1..26 宽松上限；
  if (!Number.isInteger(currentWeek) || currentWeek < 1 || currentWeek > 26) return '';
  const semester = semesterOn(today);
  // selectedWeek 才是真正定位的周次：已知学期按教学周上限约束，避免旧缓存/翻页把 20..26 当作教学周误用；未知学期沿用 1..26 兜底。
  const maxWeek = semester ? semester.teachingWeeks : 26;
  if (!Number.isInteger(selectedWeek) || selectedWeek < 1 || selectedWeek > maxWeek) return '';
  if (semester) {
    const [year,month,dayOfMonth]=semester.startDate.split('-').map(Number);
    const date=new Date(year,month-1,dayOfMonth+(selectedWeek-1)*7+day-1);
    return `${String(date.getMonth()+1).padStart(2,'0')}/${String(date.getDate()).padStart(2,'0')}`;
  }
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  date.setDate(date.getDate() - (date.getDay() + 6) % 7 + (selectedWeek - currentWeek) * 7 + day - 1);
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`;
}
