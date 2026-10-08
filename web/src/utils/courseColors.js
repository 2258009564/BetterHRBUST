/**
 * 课程柔和配色
 *
 * 以「课程名称」做稳定哈希取色：同一门课在课表、概览等页面颜色一致。
 * 色板为低饱和的淡彩（100/50 级底色 + 深色文字），避免高饱和撞色；
 * 12 个色相中冷、暖色调交错排列，绿色系仅保留 teal / emerald 两支。
 *
 * 类名必须以完整字面量书写，Tailwind v4 源码扫描才能生成对应样式。
 */

const COURSE_PALETTE = [
  {
    dot: 'bg-blue-400',
    solid: 'bg-blue-100 text-blue-900 border-blue-300/70 dark:bg-blue-400/20 dark:text-blue-50 dark:border-blue-400/30 shadow-xs',
    soft: 'bg-blue-50 text-blue-700 border-blue-200/70 dark:bg-blue-400/10 dark:text-blue-300 dark:border-blue-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-amber-400',
    solid: 'bg-amber-100 text-amber-900 border-amber-300/70 dark:bg-amber-400/20 dark:text-amber-50 dark:border-amber-400/30 shadow-xs',
    soft: 'bg-amber-50 text-amber-700 border-amber-200/70 dark:bg-amber-400/10 dark:text-amber-300 dark:border-amber-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-violet-400',
    solid: 'bg-violet-100 text-violet-900 border-violet-300/70 dark:bg-violet-400/20 dark:text-violet-50 dark:border-violet-400/30 shadow-xs',
    soft: 'bg-violet-50 text-violet-700 border-violet-200/70 dark:bg-violet-400/10 dark:text-violet-300 dark:border-violet-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-teal-400',
    solid: 'bg-teal-100 text-teal-900 border-teal-300/70 dark:bg-teal-400/20 dark:text-teal-50 dark:border-teal-400/30 shadow-xs',
    soft: 'bg-teal-50 text-teal-700 border-teal-200/70 dark:bg-teal-400/10 dark:text-teal-300 dark:border-teal-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-rose-400',
    solid: 'bg-rose-100 text-rose-900 border-rose-300/70 dark:bg-rose-400/20 dark:text-rose-50 dark:border-rose-400/30 shadow-xs',
    soft: 'bg-rose-50 text-rose-700 border-rose-200/70 dark:bg-rose-400/10 dark:text-rose-300 dark:border-rose-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-sky-400',
    solid: 'bg-sky-100 text-sky-900 border-sky-300/70 dark:bg-sky-400/20 dark:text-sky-50 dark:border-sky-400/30 shadow-xs',
    soft: 'bg-sky-50 text-sky-700 border-sky-200/70 dark:bg-sky-400/10 dark:text-sky-300 dark:border-sky-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-orange-400',
    solid: 'bg-orange-100 text-orange-900 border-orange-300/70 dark:bg-orange-400/20 dark:text-orange-50 dark:border-orange-400/30 shadow-xs',
    soft: 'bg-orange-50 text-orange-700 border-orange-200/70 dark:bg-orange-400/10 dark:text-orange-300 dark:border-orange-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-indigo-400',
    solid: 'bg-indigo-100 text-indigo-900 border-indigo-300/70 dark:bg-indigo-400/20 dark:text-indigo-50 dark:border-indigo-400/30 shadow-xs',
    soft: 'bg-indigo-50 text-indigo-700 border-indigo-200/70 dark:bg-indigo-400/10 dark:text-indigo-300 dark:border-indigo-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-pink-400',
    solid: 'bg-pink-100 text-pink-900 border-pink-300/70 dark:bg-pink-400/20 dark:text-pink-50 dark:border-pink-400/30 shadow-xs',
    soft: 'bg-pink-50 text-pink-700 border-pink-200/70 dark:bg-pink-400/10 dark:text-pink-300 dark:border-pink-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-emerald-400',
    solid: 'bg-emerald-100 text-emerald-900 border-emerald-300/70 dark:bg-emerald-400/20 dark:text-emerald-50 dark:border-emerald-400/30 shadow-xs',
    soft: 'bg-emerald-50 text-emerald-700 border-emerald-200/70 dark:bg-emerald-400/10 dark:text-emerald-300 dark:border-emerald-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-purple-400',
    solid: 'bg-purple-100 text-purple-900 border-purple-300/70 dark:bg-purple-400/20 dark:text-purple-50 dark:border-purple-400/30 shadow-xs',
    soft: 'bg-purple-50 text-purple-700 border-purple-200/70 dark:bg-purple-400/10 dark:text-purple-300 dark:border-purple-400/15 opacity-75 hover:opacity-100'
  },
  {
    dot: 'bg-slate-400',
    solid: 'bg-slate-100 text-slate-800 border-slate-300/70 dark:bg-slate-400/20 dark:text-slate-50 dark:border-slate-400/30 shadow-xs',
    soft: 'bg-slate-50 text-slate-600 border-slate-200/70 dark:bg-slate-400/10 dark:text-slate-300 dark:border-slate-400/15 opacity-75 hover:opacity-100'
  }
];

function hashString(str) {
  let h = 5381;
  for (let i = 0; i < str.length; i++) {
    h = (((h << 5) + h) + str.charCodeAt(i)) >>> 0;
  }
  return h;
}

/**
 * 取某门课程的配色方案。
 * 以课程名称为主键（同一门课颜色稳定）；名称为空时退回课序号、课程 ID。
 * @param {{ courseName?: string, courseSeq?: string, courseId?: string }} course
 * @returns {{ dot: string, solid: string, soft: string }}
 */
export function getCourseColor(course) {
  const key = String(course?.courseName || course?.courseSeq || course?.courseId || '')
    .replace(/\s+/g, '')
    .trim();
  if (!key) return COURSE_PALETTE[11];
  return COURSE_PALETTE[hashString(key) % COURSE_PALETTE.length];
}
