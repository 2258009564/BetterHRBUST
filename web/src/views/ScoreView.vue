<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="!isLoggedIn" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="score" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以查询您的全部学期成绩与GPA分析</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Top Action Bar -->
      <div class="flex items-center justify-between">
        <div class="text-xs text-zinc-500">
          共查询到 <span class="font-bold text-zinc-900 dark:text-zinc-100">{{ scores.length }}</span> 门课程成绩记录
        </div>
        <UiButton size="sm" variant="outline" :loading="loading" @click="fetchScoresData">
          <template #prefix>
            <Icon name="refresh" customClass="w-3.5 h-3.5" />
          </template>
          同步最新成绩
        </UiButton>
      </div>

      <!-- Top KPI Cards -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">总平均学分绩点 (GPA)</div>
          <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1.5">{{ stats.gpa }}</div>
          <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">按标准 4.0 算法加权计算</div>
        </UiCard>

        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">加权平均分</div>
          <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1.5">{{ stats.weightedAvg }}</div>
          <div class="text-[11px] text-emerald-600 dark:text-emerald-400 mt-1">优秀率 {{ stats.excellentRate }}%</div>
        </UiCard>

        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">获得学分 / 修读总学分</div>
          <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1.5">
            {{ stats.earnedCredits }} <span class="text-sm font-normal text-zinc-400">/ {{ stats.totalCredits }}</span>
          </div>
          <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">修读 {{ scores.length }} 门课程</div>
        </UiCard>

        <UiCard>
          <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">不及格课程门数</div>
          <div :class="['text-3xl font-extrabold mt-1.5', stats.failedCount > 0 ? 'text-rose-600' : 'text-zinc-900 dark:text-zinc-100']">
            {{ stats.failedCount }}
          </div>
          <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">
            {{ stats.failedCount === 0 ? '全科及格' : '需注意重修与补考' }}
          </div>
        </UiCard>
      </div>

      <!-- Trend and Distribution Visualizer -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Semester GPA Trend Curve (SVG) -->
        <UiCard title="各学期 GPA 走向趋势" class="lg:col-span-2">
          <div v-if="trendPoints.length === 0" class="h-56 flex items-center justify-center text-xs text-zinc-400">
            暂无足够的学期数据生成走势曲线
          </div>
          <div v-else class="h-56 w-full flex flex-col justify-end">
            <svg class="w-full h-44 overflow-visible" viewBox="0 0 500 160">
              <!-- Grid Lines -->
              <line x1="40" y1="20" x2="480" y2="20" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="60" x2="480" y2="60" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="100" x2="480" y2="100" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" stroke-dasharray="4 4" />
              <line x1="40" y1="140" x2="480" y2="140" stroke="currentColor" class="text-zinc-200 dark:text-zinc-800" />

              <!-- Y Axis labels -->
              <text x="10" y="24" class="text-[10px] fill-zinc-400">4.0</text>
              <text x="10" y="64" class="text-[10px] fill-zinc-400">3.5</text>
              <text x="10" y="104" class="text-[10px] fill-zinc-400">3.0</text>
              <text x="10" y="144" class="text-[10px] fill-zinc-400">2.0</text>

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
        <UiCard title="成绩分段分布">
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
            <UiTabs
              :items="[
                { label: '全部属性', value: 'all' },
                { label: '必修', value: '必修' },
                { label: '限选', value: '限选' },
                { label: '任选', value: '任选' }
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
        <div v-if="loading" class="p-12 text-center text-xs text-zinc-400">
          <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2" />
          正在拉取教务成绩单...
        </div>
        <table v-else class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="border-b border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/70 dark:bg-zinc-900/40 text-zinc-500 dark:text-zinc-400 font-medium">
              <th class="p-3.5 pl-5">学年 / 学期</th>
              <th class="p-3.5">课程代码</th>
              <th class="p-3.5">课程名称</th>
              <th class="p-3.5">课组</th>
              <th class="p-3.5 text-center">学分</th>
              <th class="p-3.5 text-center">学时</th>
              <th class="p-3.5">属性</th>
              <th class="p-3.5">考试性质</th>
              <th class="p-3.5 pr-5 text-right font-semibold text-zinc-700 dark:text-zinc-300">总评成绩</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-zinc-200/60 dark:divide-zinc-800/60">
            <tr
              v-for="row in filteredScores"
              :key="row.courseId + row.courseSeq + row.year + row.term"
              class="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/30 transition-colors"
            >
              <td class="p-3.5 pl-5 text-zinc-500 whitespace-nowrap">{{ row.year }} ({{ row.term }})</td>
              <td class="p-3.5 font-mono text-zinc-600 dark:text-zinc-400">{{ row.courseId }}</td>
              <td class="p-3.5 font-semibold text-zinc-900 dark:text-zinc-100">{{ row.courseName }}</td>
              <td class="p-3.5 text-zinc-600 dark:text-zinc-400">{{ row.courseGroup }}</td>
              <td class="p-3.5 text-center font-medium">{{ row.credit }}</td>
              <td class="p-3.5 text-center text-zinc-500">{{ row.hours }}</td>
              <td class="p-3.5">
                <UiBadge size="sm" :variant="row.property === '必修' ? 'default' : 'outline'">{{ row.property }}</UiBadge>
              </td>
              <td class="p-3.5 text-zinc-500">{{ row.examType }}</td>
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
import { ref, computed, onMounted, watch } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useToast } from '@/composables/useToast.js';
import { academicApi } from '@/services/academic/api.js';

const { isLoggedIn, openLoginModal } = useSession();
const { showToast } = useToast();

const loading = ref(false);
const scores = ref([]);
const searchQuery = ref('');
const selectedProperty = ref('all');
const selectedPassStatus = ref('all');

async function fetchScoresData() {
  if (!isLoggedIn.value) return;
  loading.value = true;
  try {
    const res = await academicApi.getScores();
    scores.value = res.scores || [];
  } catch (err) {
    showToast({ title: '成绩获取失败', message: err.message, type: 'danger' });
  } finally {
    loading.value = false;
  }
}

const stats = computed(() => {
  let totalCredits = 0;
  let earnedCredits = 0;
  let totalScoreWeight = 0;
  let totalGpaWeight = 0;
  let failedCount = 0;
  let excCount = 0;

  scores.value.forEach(item => {
    const cr = item.credit || 0;
    const num = parseFloat(item.score);
    totalCredits += cr;
    if (item.passed) earnedCredits += cr;
    else failedCount += 1;

    if (!isNaN(num)) {
      totalScoreWeight += num * cr;
      if (num >= 90) excCount += 1;
      const gpa = num >= 60 ? (num - 50) / 10 : 0;
      totalGpaWeight += gpa * cr;
    }
  });

  const weightedAvg = totalCredits ? (totalScoreWeight / totalCredits).toFixed(1) : '0.0';
  const gpa = totalCredits ? (totalGpaWeight / totalCredits).toFixed(2) : '0.00';
  const excellentRate = scores.value.length ? Math.round((excCount / scores.value.length) * 100) : 0;

  return {
    totalCredits: parseFloat(totalCredits.toFixed(1)),
    earnedCredits: parseFloat(earnedCredits.toFixed(1)),
    weightedAvg,
    gpa,
    failedCount,
    excellentRate
  };
});

// 动态计算学期走势
const trendPoints = computed(() => {
  const termMap = new Map();

  scores.value.forEach(s => {
    const key = `${s.year} ${s.term}`;
    if (!termMap.has(key)) termMap.set(key, { totalCr: 0, totalGpaCr: 0 });
    const cr = s.credit || 0;
    const num = parseFloat(s.score);
    if (!isNaN(num) && cr > 0) {
      const gpa = num >= 60 ? (num - 50) / 10 : 0;
      const entry = termMap.get(key);
      entry.totalCr += cr;
      entry.totalGpaCr += gpa * cr;
    }
  });

  const sortedTerms = Array.from(termMap.entries()).slice(-6);
  if (sortedTerms.length === 0) return [];

  const width = 440;
  const step = sortedTerms.length > 1 ? width / (sortedTerms.length - 1) : 0;

  return sortedTerms.map(([label, data], idx) => {
    const gpaVal = data.totalCr ? (data.totalGpaCr / data.totalCr).toFixed(2) : '0.00';
    const num = parseFloat(gpaVal);
    // 映射 GPA 2.0 -> y:140, 4.0 -> y:20
    const clamped = Math.max(2.0, Math.min(4.0, num));
    const y = 140 - ((clamped - 2.0) / 2.0) * 120;
    const x = sortedTerms.length === 1 ? 240 : 40 + idx * step;
    return { label, val: gpaVal, x: Math.round(x), y: Math.round(y) };
  });
});

const trendPolyline = computed(() => {
  return trendPoints.value.map(p => `${p.x},${p.y}`).join(' ');
});

const distribution = computed(() => {
  const total = scores.value.length || 1;
  let segs = { '90分及以上 (优秀)': 0, '80-89分 (良好)': 0, '70-79分 (中等)': 0, '60-69分 (及格)': 0, '60分以下 (不及格)': 0 };
  scores.value.forEach(s => {
    const n = parseFloat(s.score);
    if (n >= 90 || s.score === '优秀') segs['90分及以上 (优秀)'] += 1;
    else if (n >= 80 || s.score === '良好') segs['80-89分 (良好)'] += 1;
    else if (n >= 70 || s.score === '中等') segs['70-79分 (中等)'] += 1;
    else if (n >= 60 || s.score === '及格' || s.score === '合格') segs['60-69分 (及格)'] += 1;
    else segs['60分以下 (不及格)'] += 1;
  });

  return [
    { label: '90分及以上 (优秀)', count: segs['90分及以上 (优秀)'], percent: Math.round((segs['90分及以上 (优秀)']/total)*100), colorClass: 'bg-zinc-900 dark:bg-zinc-100' },
    { label: '80-89分 (良好)', count: segs['80-89分 (良好)'], percent: Math.round((segs['80-89分 (良好)']/total)*100), colorClass: 'bg-zinc-700 dark:bg-zinc-300' },
    { label: '70-79分 (中等)', count: segs['70-79分 (中等)'], percent: Math.round((segs['70-79分 (中等)']/total)*100), colorClass: 'bg-zinc-500 dark:bg-zinc-500' },
    { label: '60-69分 (及格)', count: segs['60-69分 (及格)'], percent: Math.round((segs['60-69分 (及格)']/total)*100), colorClass: 'bg-zinc-400 dark:bg-zinc-600' },
    { label: '60分以下 (不及格)', count: segs['60分以下 (不及格)'], percent: Math.round((segs['60分以下 (不及格)']/total)*100), colorClass: 'bg-rose-500' }
  ];
});

const filteredScores = computed(() => {
  return scores.value.filter(item => {
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase();
      const matchName = item.courseName.toLowerCase().includes(q);
      const matchId = item.courseId.toLowerCase().includes(q);
      if (!matchName && !matchId) return false;
    }
    if (selectedProperty.value !== 'all' && item.property !== selectedProperty.value) {
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
    if (n >= 60) return 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-300';
    return 'bg-rose-500/10 text-rose-600 dark:text-rose-400';
  }
  if (score === '优秀') return 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900';
  if (score === '良好') return 'bg-zinc-200 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-100';
  if (score === '及格' || score === '合格') return 'bg-zinc-100 text-zinc-800 dark:bg-zinc-800/60 dark:text-zinc-300';
  return 'bg-rose-500/10 text-rose-600 dark:text-rose-400';
}

watch(isLoggedIn, (val) => {
  if (val) fetchScoresData();
});

onMounted(() => {
  if (isLoggedIn.value) fetchScoresData();
});
</script>