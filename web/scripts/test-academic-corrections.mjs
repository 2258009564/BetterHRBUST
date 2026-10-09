import assert from 'node:assert/strict';
import { degreeCourses, buildAcademicStats, computeCreditsProgress, isLowScore, countedCourses, semesterSortKey } from '../src/services/academic/stats.js';
import { hasCombineSlotEnded } from '../src/utils/periodTimes.js';
const course = (courseId, score, credit, courseGroup = '', property = '必修', examType = '正常考试') => ({courseId, courseName: courseId, score: String(score), credit, courseGroup, property, examType, year: '2024', term: '1', passed: score >= 60});
const scores = [course('REQ',65,3),course('ZERO',0,0),course('E1',95,2,'E类','任选'),course('E2',90,2,'E类','任选'),course('A1',89,2,'A类','任选'),course('D1',60,2,'D类','任选')];
assert.deepEqual(degreeCourses(scores).map(s=>s.courseId),['REQ','E1','E2']);
assert.deepEqual(degreeCourses(scores.filter(s=>s.courseGroup!=='E类')).map(s=>s.courseId),['REQ']);
assert.equal(buildAcademicStats(scores).degree.requiredCredits,7);
assert.equal(buildAcademicStats(scores).degree.gpa,3.07);
const normal=course('NORMAL',90,3);
assert.equal(buildAcademicStats([normal,normal]).retakeCount,0);
assert.equal(buildAcademicStats([normal,{...normal,examType:'补考'}]).retakeCount,1);
const group={id:'1',name:'基础课',requiredCredits:20,courses:[]};
assert.equal(computeCreditsProgress([], [group,group]).requiredTotal,20);
assert.equal(computeCreditsProgress([], [group], {planTotalCredits:158.5}).requiredTotal,158.5);
assert.equal(computeCreditsProgress(scores, []).requiredTotal,0);
assert.equal(isLowScore('69'),true);assert.equal(isLowScore('70'),false);
assert.equal(hasCombineSlotEnded(1,new Date(2026,9,9,9,49)),false);
assert.equal(hasCombineSlotEnded(1,new Date(2026,9,9,9,50)),true);
assert.equal([1,2,3,4,5,6].every(i=>hasCombineSlotEnded(i,new Date(2026,9,9,21,30))),true);
console.log('通过：两门选修规则、0 学分排除、重复与补考、真实方案学分、70 分颜色阈值及课程结束边界');

const direction = {id:'direction',name:'专业限选',property:'限选',requiredCredits:10,courses:Array.from({length:10},(_,i)=>({code:'DIR'+i,name:'方向课程'+i,credit:2.5}))};
assert.equal(computeCreditsProgress([], [direction]).requiredTotal,10);
assert.equal(computeCreditsProgress([], [direction]).categories[0].required,10);

const ungraded = buildAcademicStats([course('VALID',90,3),{...course('PENDING',0,10),score:'缓考'}]);
assert.equal(ungraded.degree.gpa,4);assert.equal(ungraded.degree.qualified,true);

assert.deepEqual(degreeCourses([{...course('MATH',85,3),courseName:'高等数学(A)'},course('MAJOR',90,2.5,'专业选修','任选')]).map(s=>s.courseId),['MATH','MAJOR']);

// GPA 走势学期顺序：教务「学年」列是学期所在公历年份（2023 秋 → 2024 春 → 2024 秋 连续），同一年内先春后秋
assert.ok(semesterSortKey('2024','春') < semesterSortKey('2024','秋'));
assert.ok(semesterSortKey('2023','秋') < semesterSortKey('2024','春'));
assert.ok(semesterSortKey('2025','秋') < semesterSortKey('2026','春'));
assert.ok(semesterSortKey('2024','1') < semesterSortKey('2024','2')); // 下拉序号 1=春、2=秋
const semesterScores = [
  { courseId: 'C6', score: '70', credit: 2, property: '必修', year: '2026', term: '春', passed: true },
  { courseId: 'C3', score: '80', credit: 2, property: '必修', year: '2024', term: '秋', passed: true },
  { courseId: 'C1', score: '90', credit: 2, property: '必修', year: '2023', term: '秋', passed: true },
  { courseId: 'C2', score: '85', credit: 2, property: '必修', year: '2024', term: '春', passed: true },
  { courseId: 'C5', score: '75', credit: 2, property: '必修', year: '2025', term: '秋', passed: true },
  { courseId: 'C4', score: '95', credit: 2, property: '必修', year: '2025', term: '春', passed: true }
];
const termGroups = new Map();
countedCourses(semesterScores).forEach(s => {
  const key = `${s.year} ${s.term}`;
  if (!termGroups.has(key)) termGroups.set(key, { year: s.year, term: s.term, list: [] });
  termGroups.get(key).list.push(s);
});
const trendLabels = [...termGroups.values()]
  .sort((a, b) => semesterSortKey(a.year, a.term) - semesterSortKey(b.year, b.term))
  .slice(-6)
  .map(g => `${g.year} ${g.term}`);
assert.deepEqual(trendLabels, ['2023 秋', '2024 春', '2024 秋', '2025 春', '2025 秋', '2026 春']);
