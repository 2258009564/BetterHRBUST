<script setup>
import { computed } from 'vue'
// 来自真实客户端的卡片、图标、统计函数与课程配色，不复制一套假 UI 算法。
import UiCard from '../../web/src/components/ui/UiCard.vue'
import Icon from '../../web/src/components/icons/Icon.vue'
import { buildAcademicStats, computeCreditsProgress } from '../../web/src/services/academic/stats.js'
import { getCourseColor, registerCourseColors } from '../../web/src/utils/courseColors.js'
import catalog from '../../shared/resources.json'
const props = defineProps({ view: { type: String, default: 'dashboard' }, compact: Boolean, dark: Boolean })
const logo = `${import.meta.env.BASE_URL}BetterHRBUST.png`
const tabs = [
  { id: 'dashboard', label: '概览', icon: 'dashboard' }, { id: 'timetable', label: '课表', icon: 'timetable' },
  { id: 'gpa', label: 'GPA 分析', icon: 'score' }, { id: 'resources', label: '资料查找', icon: 'notice' }
]
const names = ['高等数学', '数据结构', '计算机网络', '操作系统', '数据库原理', '软件工程']
const grades = names.map((name, index) => ({ courseId: `DEMO-${index}`, courseName: name,
  score: [93, 95, 90, 88, 96, 92][index], credit: [4, 4, 3, 3, 2, 3][index], passed: true,
  property: '必修', examType: '正常考试', courseGroup: index < 2 ? '公共与基础课程' : '专业核心课程' }))
const stats = buildAcademicStats(grades)
const progress = computeCreditsProgress(grades, [
  { name: '公共与基础课程', property: '必修', requiredCredits: 24, courses: grades.slice(0, 2).map(grade => ({ code: grade.courseId, name: grade.courseName })) },
  { name: '专业核心课程', property: '必修', requiredCredits: 36, courses: grades.slice(2).map(grade => ({ code: grade.courseId, name: grade.courseName })) },
  { name: '实践与毕业设计', property: '必修', requiredCredits: 16, courses: [] }
])
const courses = [
  { day: 1, slot: 1, name: names[0], teacher: '陈老师', room: '新 A · 201', weeks: '1–16 周' },
  { day: 1, slot: 3, name: names[2], teacher: '王老师', room: '新 B · 302', weeks: '1–16 周' },
  { day: 2, slot: 2, name: names[1], teacher: '张老师', room: '新 A · 203', weeks: '1–16 周' },
  { day: 3, slot: 1, name: names[3], teacher: '李老师', room: '新 B · 301', weeks: '1–16 周' },
  { day: 3, slot: 3, name: names[4], teacher: '赵老师', room: '新 A · 202', weeks: '单周', inactive: true },
  { day: 4, slot: 2, name: names[5], teacher: '刘老师', room: '新 B · 305', weeks: '1–16 周' },
  { day: 5, slot: 1, name: names[1], teacher: '张老师', room: '新 A · 201', weeks: '1–16 周' },
  { day: 5, slot: 3, name: names[3], teacher: '李老师', room: '新 B · 301', weeks: '1–16 周' }
]
registerCourseColors(courses.map(course => ({ courseName: course.name })))
const courseStyle = course => getCourseColor({ courseName: course.name }).style
const sampleResources = catalog.items.filter(item => /补办学生证|缓考申请|四六级/.test(item.title)).slice(0, 2)
const title = computed(() => tabs.find(tab => tab.id === props.view)?.label)
</script>

<template>
  <div class="client-demo" :class="{ 'compact-demo': compact }" :data-view="view" :aria-label="title + '静态界面展示'">
    <aside class="demo-sidebar">
      <div class="demo-brand"><img :src="logo" alt="" /><strong>BetterHRBUST</strong></div>
      <span class="demo-nav-label">教务核心</span>
      <div class="demo-nav"><span v-for="tab in tabs" :key="tab.id" :class="{ selected: view === tab.id }"><Icon :name="tab.icon" custom-class="w-4 h-4" />{{ tab.label }}</span></div>
      <div class="demo-user"><span class="demo-avatar">林</span><div><strong>林同学</strong><small>2024000001 · 示例账户</small></div></div>
    </aside>
    <div class="demo-main">
      <header class="demo-toolbar"><span>{{ title }}</span><div><span class="demo-badge">示例数据</span><span class="demo-theme"><Icon :name="dark ? 'sun' : 'moon'" custom-class="w-4 h-4" /></span></div></header>
      <div class="demo-content">
        <template v-if="view === 'dashboard'">
          <div class="demo-welcome"><div><span>2026 秋季 · 第 6 周</span><h3>林同学，今天也从容一点。</h3></div><small>软件工程 · 示例学业数据</small></div>
          <div class="demo-metrics">
            <UiCard><span class="metric-label">平均学分绩点</span><strong class="metric-value">{{ stats.gpa.toFixed(2) }}<small>/ 5.0</small></strong><span class="metric-note">必修课加权统计</span></UiCard>
            <UiCard><span class="metric-label">已获得学分</span><strong class="metric-value">{{ progress.earnedTotal }}<small>/ {{ progress.requiredTotal }}</small></strong><span class="metric-note">培养方案完成 {{ progress.completionPercent }}%</span></UiCard>
            <UiCard><span class="metric-label">今日课程</span><strong class="metric-value">2<small>门</small></strong><span class="metric-note">周五 · 第 1、3 大节</span></UiCard>
            <UiCard><span class="metric-label">加权平均分</span><strong class="metric-value">{{ stats.weightedAvg.toFixed(1) }}</strong><span class="metric-note">已通过 {{ stats.courseCount }} 门课程</span></UiCard>
          </div>
          <div class="demo-overview-grid">
            <UiCard title="今天的课程"><div v-for="course in courses.filter(course => course.day === 5)" :key="course.name" class="demo-course course-card" :style="courseStyle(course)"><div><strong>{{ course.name }}</strong><span>{{ course.teacher }} · {{ course.room }}</span></div><small>第 {{ course.slot }} 大节</small></div></UiCard>
            <UiCard title="学分完成情况"><div v-for="group in progress.categories" :key="group.name" class="demo-progress-row"><div><span>{{ group.name }}</span><small>{{ group.earned }} / {{ group.required }} 学分</small></div><progress :value="group.earned" :max="group.required || 1"></progress></div></UiCard>
          </div>
          <div class="demo-shortcuts"><span><Icon name="score" custom-class="w-4 h-4" />GPA 分析</span><span><Icon name="program" custom-class="w-4 h-4" />培养方案</span><span><Icon name="classroom" custom-class="w-4 h-4" />空教室与自习</span></div>
        </template>
        <template v-else-if="view === 'timetable'">
          <UiCard><div class="demo-week"><div><span>‹</span><strong>第 6 周</strong><span>›</span></div><small>大节模式 · 当前周</small></div></UiCard>
          <div class="demo-table-wrap"><table class="demo-timetable"><thead><tr><th>节次</th><th v-for="label in ['周一','周二','周三','周四','周五']" :key="label">{{ label }}</th></tr></thead><tbody><tr v-for="slot in 3" :key="slot"><th>第 {{ slot }} 大节<br /><small>{{ ['08:10','10:10','13:30'][slot - 1] }}</small></th><td v-for="day in 5" :key="day"><div v-for="course in courses.filter(course => course.day === day && course.slot === slot)" :key="course.name" class="course-card" :class="{ 'demo-course-inactive': course.inactive }" :style="courseStyle(course)"><strong>{{ course.name }}</strong><span>{{ course.room }}</span><small>{{ course.teacher }} · {{ course.weeks }}</small></div></td></tr></tbody></table></div>
        </template>
        <template v-else-if="view === 'gpa'">
          <div class="demo-gpa-summary"><UiCard title="平均学分绩点"><strong class="metric-value">{{ stats.gpa.toFixed(2) }}<small>/ 5.0</small></strong><span class="metric-note">必修课 · 五分制</span></UiCard><UiCard title="加权平均分"><strong class="metric-value">{{ stats.weightedAvg.toFixed(1) }}</strong><span class="metric-note">已获得 {{ stats.earnedCredits }} 学分</span></UiCard></div>
          <UiCard title="成绩明细"><table class="demo-grades"><thead><tr><th>课程</th><th>学分</th><th>总评</th><th>状态</th></tr></thead><tbody><tr v-for="grade in grades" :key="grade.courseId"><td>{{ grade.courseName }}</td><td>{{ grade.credit }}</td><td class="demo-score">{{ grade.score }}</td><td class="demo-passed">已通过</td></tr></tbody></table></UiCard>
        </template>
        <template v-else-if="view === 'resources'">
          <UiCard title="资料查找"><div class="demo-search"><Icon name="search" custom-class="w-4 h-4" /><span>学生证、缓考、四六级…</span><small>全部分类⌄</small></div><p class="demo-hint">{{ catalog.categories.length }} 类 · {{ catalog.items.length }} 条公开资料 · 教务处原站</p></UiCard>
          <UiCard v-for="item in sampleResources" :key="item.id" :title="item.title"><p class="demo-hint">{{ item.category }} · {{ item.date }}</p><span class="demo-source-link">查看学校原文 ↗</span><div v-for="file in item.attachments.slice(0, 1)" :key="file.url" class="demo-file">↓ {{ file.title }}</div></UiCard>
        </template>
      </div>
      <footer class="demo-footer">实际组件与算法 · 账户和成绩为示例</footer>
    </div>
  </div>
</template>
