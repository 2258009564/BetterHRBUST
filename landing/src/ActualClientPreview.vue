<script setup>
import { computed } from 'vue'
import AppLayout from '../../web/src/components/layout/AppLayout.vue'
import DashboardView from '../../web/src/views/DashboardView.vue'
import TimetableView from '../../web/src/views/TimetableView.vue'
import ScoreView from '../../web/src/views/ScoreView.vue'
import ResourcesView from '../../web/src/views/ResourcesView.vue'
import EvaluationView from '../../web/src/views/EvaluationView.vue'
import { useSession } from '../../web/src/composables/useSession.js'
import { useAcademicData } from '../../web/src/composables/useAcademicData.js'
import { useTheme } from '../../web/src/composables/useTheme.js'
import { registerCourseColors } from '../../web/src/utils/courseColors.js'
const params = new URLSearchParams(location.search)
const view = params.get('client-preview') || 'dashboard'
const session = useSession(), data = useAcademicData()
session.isLoggedIn.value = true; session.authChecked.value = true
session.currentWeek.value = 6; session.semesterTeachingWeeks.value = 19; session.calendarWeekCountKnown.value = true
session.studentNumber.value = '2401234567'; session.studentId.value = 'preview-only'
Object.assign(session.userProfile, { realName:'林同学', studentNumber:'2401234567', college:'计算机科学与技术学院', major:'软件工程', grade:'2024级' })
data.exams.value=[]; data.notices.value=[]; data.currentCourses.value=[]; data.yearOptions.value=[]
const names=['高等数学','数据结构','计算机网络','操作系统','数据库系统','软件工程']
data.scores.value=names.map((name,i)=>({courseId:`DEMO${i}`,courseName:name,score:String([93,95,90,88,96,92][i]),credit:[4,4,3,3,2,3][i],passed:true,property:'必修',examType:'正常考试',courseGroup:'专业基础课程',year:'2025',term:i<3?'1':'2',hours:48}))
data.plan.value={totalRequiredCredits:160,groups:[{id:'example',name:'专业基础课程',property:'必修',requiredCredits:160,courses:[]}]}
data.timetableCombine.value={cells:[1,2,3,4,5,6,7].flatMap(day=>[1,3].map((section,i)=>({id:`${day}-${section}`,courseId:`DEMO${(day+i)%6}`,day,sectionIndex:section,sectionLabel:`第${section}大节`,courseName:names[(day+i)%6],teacher:['林老师','陈老师'][i],location:['西-新D510','西-新D302'][i],weeks:'1-18周',courseSeq:'1',hoursType:''}))),unarranged:[]}
data.timetableBase.value=data.timetableCombine.value
registerCourseColors(data.timetableCombine.value.cells)
useTheme().setTheme(params.get('theme')==='dark'?'dark':'light')
const current = computed(()=>({dashboard:DashboardView,timetable:TimetableView,gpa:ScoreView,resources:ResourcesView,evaluation:EvaluationView})[view]||DashboardView)
</script>
<template><AppLayout :active-tab="view==='gpa'?'score':view"><component :is="current" /></AppLayout></template>
