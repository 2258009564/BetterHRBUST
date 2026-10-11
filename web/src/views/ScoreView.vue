<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="showLoginPrompt" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="score" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以查询您的全部学期成绩与GPA分析</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Top Action Bar（数据刷新统一由顶栏按钮完成） -->
      <div class="text-xs text-zinc-500">
        共 <span class="font-bold text-zinc-900 dark:text-zinc-100">{{ rawScores.length }}</span> 条成绩记录，
        合并去重后 <span class="font-bold text-zinc-900 dark:text-zinc-100">{{ stats.courseCount }}</span> 门必修课
        <span class="ml-2 text-zinc-400">上次同步：{{ lastSyncText }}</span>
      </div>

      <div class="text-[11px] text-zinc-400 dark:text-zinc-500 leading-relaxed">
        {{ statsScopeNote }}
      </div>

      <!-- Top KPI Cards -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">平均学分绩点 (五分制 GPA)</div>
          <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1.5">{{ stats.gpa.toFixed(2) }}</div>
          <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">哈理工官方算法：60 分 = 1 绩点，每分 +0.1</div>
        </UiCard>

        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">加权平均分</div>
          <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1.5">{{ stats.weightedAvg.toFixed(1) }}</div>
          <div class="text-[11px] text-emerald-600 dark:text-emerald-400 mt-1">优秀率 {{ stats.excellentRate }}%</div>
        </UiCard>

        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">已获得学分 / 修读总学分</div>
          <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1.5">
            {{ stats.earnedCredits }} <span class="text-sm font-normal text-zinc-400">/ {{ stats.totalCredits }}</span>
          </div>
          <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">{{ earnedCreditsNote }}</div>
        </UiCard>

        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">不及格课程门数</div>
          <div :class="['text-3xl font-extrabold mt-1.5', stats.failedCount > 0 ? 'text-rose-600' : 'text-zinc-900 dark:text-zinc-100']">
            {{ stats.failedCount }}
          </div>
          <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">
            累计挂科 {{ stats.failedCredits }} 学分
          </div>
        </UiCard>
      </div>

      <!-- 特色算法：学位证 / 推免 / 学业风险 -->
      <UiCard title="特色学业算法（哈理工口径）">
        <template #header-action>
          <span class="text-[11px] text-zinc-400">门槛以学校教务处当期文件为准</span>
        </template>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <!-- ① 学位证算法 -->
          <div class="p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/30">
            <div class="flex items-center justify-between">
              <span class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">学位证算法</span>
              <UiBadge size="sm" :variant="stats.degree.qualified ? 'success' : 'warning'">
                {{ stats.degree.qualified ? '达标' : '未达标' }}
              </UiBadge>
            </div>
            <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-2">{{ stats.degree.gpa.toFixed(2) }}</div>
            <GpaCalculationHelp />
        <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1 leading-relaxed">
              学业课 + E 类最高一门 + 剩余 A–E 类最高一门，门槛 {{ stats.degree.threshold.toFixed(1) }}
            </div>
            <div class="text-[11px] mt-1" :class="stats.degree.allPassed ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'">
              0 学分及其余通识选修不参与加权
            </div>
          </div>

          <!-- ② 推免 / 保研自检 -->
          <div class="p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/30">
            <div class="flex items-center justify-between">
              <span class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">推免资格自检</span>
              <UiBadge size="sm" :variant="stats.recommend.retakeWithinLimit ? 'success' : 'warning'">
                {{ stats.recommend.retakeWithinLimit ? '良好' : '超过上限' }}
              </UiBadge>
            </div>
            <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-2">
              {{ stats.recommend.retakeCount }} <span class="text-sm font-normal text-zinc-400">/ {{ stats.recommend.retakeLimit }}</span>
            </div>
            <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1 leading-relaxed">
              仅统计必修课的补考 + 重修累计门数，上限 {{ stats.recommend.retakeLimit }} 门
            </div>
            <div class="text-[11px] mt-1" :class="stats.recommend.courseCount === 0 ? 'text-zinc-500' : stats.recommend.allPassed ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'">
              {{ stats.recommend.courseCount === 0 ? '暂无必修成绩记录' : stats.recommend.allPassed ? '必修课成绩全部合格' : '存在不合格的必修课' }}
            </div>
          </div>

          <!-- ③ 学业风险预警 -->
          <div class="p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/30">
            <div class="flex items-center justify-between">
              <span class="text-xs font-semibold text-zinc-800 dark:text-zinc-200">学业风险预警</span>
              <UiBadge
                size="sm"
                :variant="stats.risk.level === 'none' ? 'success' : 'danger'"
              >
                {{ stats.risk.label }}
              </UiBadge>
            </div>
            <div class="text-2xl font-bold mt-2" :class="stats.risk.level === 'none' ? 'text-zinc-900 dark:text-zinc-100' : 'text-rose-600 dark:text-rose-400'">
              {{ stats.risk.failedCredits }} <span class="text-sm font-normal text-zinc-400">学分</span>
            </div>
            <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1 leading-relaxed">
              {{ stats.risk.description }}
            </div>
          </div>


        </div>
      </UiCard>

      <!-- Trend and Distribution Visualizer -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Semester GPA Trend Curve (SVG) -->
        <UiCard title="各学期 GPA 走向趋势（五分制 · 仅必修课）" class="lg:col-span-2">
          <div v-if="trendPoints.length === 0" class="h-56 flex items-center justify-center text-xs text-zinc-400">
            暂无足够的学期数据生成走势曲线
          </div>
          <div v-else class="h-56 w-full flex flex-col justify-end">
            <svg class="w-full h-44 overflow-visible" viewBox="0 0 500 160">
              <!-- Grid Lines -->
              <line x1="40" y1="20" x2="480" y2="20" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="50" x2="480" y2="50" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="80" x2="480" y2="80" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="110" x2="480" y2="110" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="140" x2="480" y2="140" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" />

              <!-- Y Axis labels -->
              <text x="10" y="24" class="text-[10px] fill-zinc-400">5.0</text>
              <text x="10" y="54" class="text-[10px] fill-zinc-400">4.0</text>
              <text x="10" y="84" class="text-[10px] fill-zinc-400">3.0</text>
              <text x="10" y="114" class="text-[10px] fill-zinc-400">2.0</text>
              <text x="10" y="144" class="text-[10px] fill-zinc-400">1.0</text>

              <!-- Polyline & Points -->
              <polyline
                v-if="trendPoints.length > 1"
                :points="trendPolyline"
                fill="none"
                stroke="currentColor"
                stroke-width="3"
                class="text-zinc-900 dark:text-zinc-100"
              />

              <g v-for="(p, i) in trendPoints" :key="i">
                <circle :cx="p.x" :cy="p.y" r="5" class="fill-white dark:fill-[#1c1e24] stroke-zinc-800 dark:stroke-zinc-200" stroke-width="2.5" />
                <text :x="p.x" :y="p.y - 10" text-anchor="middle" class="text-[11px] font-bold fill-zinc-800 dark:fill-zinc-200">{{ p.val }}</text>
                <text :x="p.x" y="155" text-anchor="middle" class="text-[10px] fill-zinc-500">{{ p.label }}</text>
              </g>
            </svg>
          </div>
        </UiCard>

        <!-- Grade Distribution Bar Chart (SVG) -->
        <UiCard title="必修课成绩分段分布">
          <div class="h-56 flex flex-col justify-center space-y-3.5">
            <div v-for="seg in distribution" :key="seg.label">
              <div class="flex justify-between text-xs mb-1">
                <span class="text-zinc-600 dark:text-zinc-400 font-medium">{{ seg.label }}</span>
                <span class="text-zinc-900 dark:text-zinc-100 font-bold">{{ seg.count }} 门 ({{ seg.percent }}%)</span>
              </div>
              <div class="w-full h-2 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-300"
                  :class="seg.colorClass"
                  :style="{ width: `${seg.percent}%` }"
                />
              </div>
            </div>
          </div>
        </UiCard>
      </div>

      <!-- Search & Filters -->
      <UiCard bodyClass="p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <!-- Search -->
          <div class="w-full sm:w-64">
            <UiInput
              v-model="searchQuery"
              placeholder="搜索课程名或课号..."
              size="sm"
              clearable
            >
              <template #prefix>
                <Icon name="search" customClass="w-3.5 h-3.5" />
              </template>
            </UiInput>
          </div>

          <!-- Filter Controls -->
          <div class="flex flex-wrap items-center gap-2">
            <div class="flex items-center gap-2">
              <span class="text-xs text-zinc-500">学期：</span>
              <UiSelect
                v-model="selectedSemester"
                :items="semesterItems"
                aria-label="学期筛选"
                size="sm"
                custom-class="min-w-[7.5rem]"
              />
            </div>

            <UiTabs
              :items="[
                { label: '全部属性', value: 'all' },
                { label: '必修', value: 'required' },
                { label: '限选', value: 'limited' },
                { label: '任选', value: 'elective' },
                { label: '其它', value: 'other' }
              ]"
              v-model="selectedProperty"
            />

            <UiTabs
              :items="[
                { label: '全部状态', value: 'all' },
                { label: '仅及格', value: 'passed' },
                { label: '未通过', value: 'failed' }
              ]"
              v-model="selectedPassStatus"
            />
          </div>
        </div>
      </UiCard>

      <!-- Scores Datalist Table -->
      <div class="rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#111316] overflow-x-auto shadow-xs">
        <div v-if="syncing && scores.length === 0" class="p-12 text-center text-xs text-zinc-400">
          <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2" />
          正在拉取教务成绩单...
        </div>
        <table v-else class="w-full min-w-[1120px] text-left border-collapse text-xs">
          <thead>
            <tr class="border-b border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/70 dark:bg-zinc-900/40 text-zinc-500 dark:text-zinc-400 font-medium">
              <th class="whitespace-nowrap p-3.5 pl-5">学年 / 学期</th>
              <th class="whitespace-nowrap p-3.5">课程代码</th>
              <th class="whitespace-nowrap p-3.5">课程名称</th>
              <th class="whitespace-nowrap p-3.5">课组</th>
              <th class="whitespace-nowrap p-3.5 text-center">学分</th>
              <th class="whitespace-nowrap p-3.5 text-center">绩点</th>
              <th class="whitespace-nowrap p-3.5">属性</th>
              <th class="whitespace-nowrap p-3.5">考试性质</th>
              <th class="whitespace-nowrap p-3.5 pr-5 text-right font-semibold text-zinc-700 dark:text-zinc-300">总评成绩</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-zinc-200/60 dark:divide-zinc-800/60">
            <tr
              v-for="row in filteredScores"
              :key="row.rowKey"
              class="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/30 transition-colors"
            >
              <td class="p-3.5 pl-5 text-zinc-500 whitespace-nowrap">
                <div>{{ row.year }} ({{ row.term }})</div>
                <div v-if="row.recordCount > 1" class="text-[10px] text-zinc-400 mt-0.5">
                  共 {{ row.recordCount }} 次记录 · 已合并
                </div>
              </td>
              <td class="p-3.5 font-mono text-zinc-600 dark:text-zinc-400">{{ row.courseId }}</td>
              <td class="p-3.5 font-semibold text-zinc-900 dark:text-zinc-100">
                {{ row.courseName }}
                <UiBadge v-if="row.isRetake" size="sm" variant="outline" class="ml-1.5">补考/重修</UiBadge>
              </td>
              <td class="p-3.5 text-zinc-600 dark:text-zinc-400">{{ row.courseGroup }}</td>
              <td class="p-3.5 text-center font-medium">{{ row.credit }}</td>
              <td class="p-3.5 text-center font-mono text-zinc-600 dark:text-zinc-400">{{ row.gradePoint.toFixed(1) }}</td>
              <td class="p-3.5">
                <UiBadge size="sm" :variant="isRequired(row.property) ? 'default' : 'outline'">{{ row.property }}</UiBadge>
              </td>
              <td class="p-3.5 text-zinc-500 whitespace-nowrap">{{ row.examType }}</td>
              <td class="p-3.5 pr-5 text-right whitespace-nowrap">
                <span
                  :class="[
                    'px-2.5 py-1 rounded-md text-xs font-bold inline-block',
                    getScoreClass(row.score)
                  ]"
                >
                  {{ row.score }}
                </span>
              </td>
            </tr>
            <tr v-if="filteredScores.length === 0">
              <td colspan="9" class="p-8 text-center text-zinc-400 dark:text-zinc-500">
                未找到匹配的课程成绩记录
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import GpaCalculationHelp from '@/components/GpaCalculationHelp.vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiSelect from '@/components/ui/UiSelect.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';
import {
  buildAcademicStats,
  countedCourses,
  dedupeScores,
  gpaOf,
  hasCredits,
  gradePoint,
  isRequired,
  resolveProperty,
  semesterSortKey,
  EARNED_CREDITS_NOTE,
  STATS_SCOPE_NOTE
} from '@/services/academic/stats.js';

const { isLoggedIn, isSessionExpired, openLoginModal } = useSession();
const { scores: rawScores, syncing, lastSyncText } = useAcademicData();

const searchQuery = ref('');
const selectedSemester = ref('all');
const selectedProperty = ref('all');
const selectedPassStatus = ref('all');

// 未登录且无离线缓存时才展示登录引导
const showLoginPrompt = computed(() => !isLoggedIn.value && !isSessionExpired.value);

const earnedCreditsNote = EARNED_CREDITS_NOTE;
const statsScopeNote = STATS_SCOPE_NOTE;

/** 去重后的成绩记录（附加 rowKey / gradePoint / isRetake 等展示字段） */
const scores = computed(() => {
  return dedupeScores(rawScores.value).map(item => ({
    ...item,
    // 仅用于列表渲染的唯一键，与去重口径无关
    rowKey: `${item.courseId || item.courseName}-${item.courseSeq || ''}-${item.year}${item.term}`,
    propertyKey: resolveProperty(item.property),
    gradePoint: gradePoint(item.score)
  }));
});

const stats = computed(() => buildAcademicStats(rawScores.value));

// 参与学业统计的课程（仅必修课，已合并去重）——走势图与分段图统一使用该口径
const countedRecords = computed(() => countedCourses(rawScores.value));

// 动态计算学期走势（仅必修课；聚合口径统一走 stats.gpaOf，与总 GPA 完全一致）
const trendPoints = computed(() => {
  const termMap = new Map();

  countedRecords.value.forEach(s => {
    const key = `${s.year} ${s.term}`;
    if (!termMap.has(key)) termMap.set(key, { label: key, year: s.year, term: s.term, list: [] });
    termMap.get(key).list.push(s);
  });

  // 教务接口返回顺序不可依赖，必须显式按「学年 + 学期」时间先后升序排列，再取最近 6 个学期
  const sortedTerms = Array.from(termMap.values())
    .filter(({ list }) => hasCredits(list))
    .sort((a, b) => semesterSortKey(a.year, a.term) - semesterSortKey(b.year, b.term))
    .slice(-6);
  if (sortedTerms.length === 0) return [];

  const width = 440;
  const step = sortedTerms.length > 1 ? width / (sortedTerms.length - 1) : 0;

  return sortedTerms.map(({ label, list }, idx) => {
    const gpaVal = gpaOf(list).toFixed(2);
    const num = parseFloat(gpaVal);
    // 映射 1.0 → y:140，5.0 → y:20
    const clamped = Math.max(1.0, Math.min(5.0, num));
    const y = 140 - ((clamped - 1.0) / 4.0) * 120;
    const x = sortedTerms.length === 1 ? 240 : 40 + idx * step;
    return { label, val: gpaVal, x: Math.round(x), y: Math.round(y) };
  });
});

const trendPolyline = computed(() => {
  return trendPoints.value.map(p => `${p.x},${p.y}`).join(' ');
});

const distribution = computed(() => {
  const total = countedRecords.value.length || 1;
  const segs = { '90分及以上 (优秀)': 0, '80-89分 (良好)': 0, '70-79分 (中等)': 0, '60-69分 (及格)': 0, '60分以下 (不及格)': 0 };
  countedRecords.value.forEach(s => {
    const n = parseFloat(s.score);
    if (n >= 90 || s.score === '优秀' || s.score === '优') segs['90分及以上 (优秀)'] += 1;
    else if (n >= 80 || s.score === '良好' || s.score === '良') segs['80-89分 (良好)'] += 1;
    else if (n >= 70 || s.score === '中等' || s.score === '中') segs['70-79分 (中等)'] += 1;
    else if (n >= 60 || s.score === '及格' || s.score === '合格') segs['60-69分 (及格)'] += 1;
    else segs['60分以下 (不及格)'] += 1;
  });

  return [
    { label: '90分及以上 (优秀)', count: segs['90分及以上 (优秀)'], percent: Math.round((segs['90分及以上 (优秀)'] / total) * 100), colorClass: 'bg-zinc-900 dark:bg-zinc-100' },
    { label: '80-89分 (良好)', count: segs['80-89分 (良好)'], percent: Math.round((segs['80-89分 (良好)'] / total) * 100), colorClass: 'bg-zinc-700 dark:bg-zinc-300' },
    { label: '70-79分 (中等)', count: segs['70-79分 (中等)'], percent: Math.round((segs['70-79分 (中等)'] / total) * 100), colorClass: 'bg-zinc-500 dark:bg-zinc-500' },
    { label: '60-69分 (及格)', count: segs['60-69分 (及格)'], percent: Math.round((segs['60-69分 (及格)'] / total) * 100), colorClass: 'bg-zinc-400 dark:bg-zinc-600' },
    { label: '60分以下 (不及格)', count: segs['60分以下 (不及格)'], percent: Math.round((segs['60分以下 (不及格)'] / total) * 100), colorClass: 'bg-rose-500' }
  ];
});

// 学期筛选选项：全部学期 + 按时间先后倒序（最新学期在前），与走势图共用同一排序口径
const semesterItems = computed(() => {
  const map = new Map();
  scores.value.forEach(item => {
    const key = `${item.year} ${item.term}`;
    if (!map.has(key)) map.set(key, { value: key, label: key, year: item.year, term: item.term });
  });
  const list = Array.from(map.values())
    .sort((a, b) => semesterSortKey(b.year, b.term) - semesterSortKey(a.year, a.term))
    .map(({ value, label }) => ({ value, label }));
  return [{ value: 'all', label: '全部学期' }, ...list];
});

// 数据变化（切换账号 / 重新同步）后，若所选学期已不存在则回退到全部学期
watch(semesterItems, items => {
  if (selectedSemester.value !== 'all' && !items.some(i => i.value === selectedSemester.value)) {
    selectedSemester.value = 'all';
  }
});

const filteredScores = computed(() => {
  return scores.value.filter(item => {
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase();
      const matchName = (item.courseName || '').toLowerCase().includes(q);
      const matchId = (item.courseId || '').toLowerCase().includes(q);
      if (!matchName && !matchId) return false;
    }
    if (selectedSemester.value !== 'all' && `${item.year} ${item.term}` !== selectedSemester.value) {
      return false;
    }
    if (selectedProperty.value !== 'all' && item.propertyKey !== selectedProperty.value) {
      return false;
    }
    if (selectedPassStatus.value === 'passed' && !item.passed) return false;
    if (selectedPassStatus.value === 'failed' && item.passed) return false;

    return true;
  });
});

function getScoreClass(score) {
  const n = parseFloat(score);
  if (!isNaN(n)) {
    if (n >= 90) return 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900';
    if (n >= 80) return 'bg-zinc-200 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100';
    if (n >= 70) return 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-300';
    return 'bg-rose-500/10 text-rose-600 dark:text-rose-400';
  }
  if (score === '优秀' || score === '优') return 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900';
  if (score === '良好' || score === '良') return 'bg-zinc-200 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100';
  if (score === '中等' || score === '中') return 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-300';
  return 'bg-rose-500/10 text-rose-600 dark:text-rose-400';
}
</script>
