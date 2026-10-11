import { semesterOn } from './schoolCalendar.js';
/** 按当前教学周所在周的周一计算所选周，使用本地日期避免时区导致跨日。 */
export function timetableDayDate(currentWeek, selectedWeek, day, today = new Date()) {
  if (![currentWeek, selectedWeek].every(w => Number.isInteger(w) && w >= 1 && w <= 26) || day < 1 || day > 7 || Number.isNaN(today.getTime())) return '';
  const semester=semesterOn(today);
  if (semester) {
    if (selectedWeek > semester.teachingWeeks) return '';
    const [year,month,dayOfMonth]=semester.startDate.split('-').map(Number);
    const date=new Date(year,month-1,dayOfMonth+(selectedWeek-1)*7+day-1);
    return `${String(date.getMonth()+1).padStart(2,'0')}/${String(date.getDate()).padStart(2,'0')}`;
  }
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  date.setDate(date.getDate() - (date.getDay() + 6) % 7 + (selectedWeek - currentWeek) * 7 + day - 1);
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`;
}
