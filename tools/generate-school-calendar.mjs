import { readFileSync, writeFileSync } from 'node:fs';
const data=JSON.parse(readFileSync(new URL('../shared/school-calendar.json',import.meta.url),'utf8'));
const rows=data.semesters.map(s=>`        Semester("${s.label}", LocalDate.parse("${s.startDate}"), LocalDate.parse("${s.teachingEndDate}"), LocalDate.parse("${s.periodEndDate}"), ${s.teachingWeeks})`).join(',\n');
const source=`package com.glassous.betterhrbust.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 由 shared/school-calendar.json 生成；更新校历时运行 tools/generate-school-calendar.mjs。 */
object SchoolCalendar {
    const val SOURCE = "${data.sourceUrl}"
    data class Semester(val label: String, val start: LocalDate, val teachingEnd: LocalDate, val periodEnd: LocalDate, val weeks: Int)
    private val semesters = listOf(
${rows}
    )
    fun semesterOn(date: LocalDate): Semester? = semesters.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.periodEnd) }
    fun weekOn(date: LocalDate): Int? {
        val semester = semesterOn(date) ?: return null
        if (date.isAfter(semester.teachingEnd)) return null
        return (ChronoUnit.DAYS.between(semester.start, date) / 7 + 1).toInt()
    }
}
`;
const target=new URL('../android/app/src/main/java/com/glassous/betterhrbust/core/util/SchoolCalendar.kt',import.meta.url);
if(process.argv.includes('--check')) {
  if(readFileSync(target,'utf8').replace(/\r\n/g,'\n')!==source) throw new Error('Android 校历与共享数据不同步，请运行 node tools/generate-school-calendar.mjs');
} else writeFileSync(target,source);
