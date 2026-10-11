import calendar from '../../../shared/school-calendar.json' with { type: 'json' };
const iso = date => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
export function semesterOn(date = new Date()) {
  const day=iso(date);
  return calendar.semesters.find(s => day >= s.startDate && day <= s.periodEndDate) || null;
}
export function teachingWeekOn(date = new Date()) {
  const semester=semesterOn(date);
  if (!semester || iso(date)>semester.teachingEndDate) return null;
  const start=Date.parse(semester.startDate+'T00:00:00Z');
  const current=Date.UTC(date.getFullYear(),date.getMonth(),date.getDate());
  return Math.floor((current-start)/604800000)+1;
}
export const calendarSource = calendar.sourceUrl;
