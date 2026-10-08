<template>
  <div class="space-y-6">
    <!-- Initial Auth Checking State: Smooth Skeleton to prevent login card flash -->
    <div v-if="!authChecked && !hasCachedData" class="space-y-6">
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div v-for="i in 4" :key="i" class="p-5 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] space-y-3">
          <div class="h-3 w-20 bg-zinc-200/70 dark:bg-zinc-800 animate-pulse rounded" />
          <div class="h-7 w-28 bg-zinc-200/80 dark:bg-zinc-800 animate-pulse rounded-md" />
        </div>
      </div>
      <div class="p-12 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] text-center text-xs text-zinc-400">
        <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2 text-zinc-400" />
        正在连接教务在线网关并验证身份...
      </div>
    </div>

    <!-- First-time Unauthenticated State: Only after auth is checked and no login & no cached data -->
    <div v-else-if="authChecked && !isLoggedIn && !hasCachedData" class="max-w-xl mx-auto py-6">
      <LoginCard
        @success="loadDashboardData"
      />
    </div>

    <!-- Authenticated State or Cached Offline State -->
    <template v-else>
      <!-- Top Metric Highlights with Loading Skeletons -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <!-- GPA Card -->
        <UiCard customClass="relative overflow-hidden">
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">平均学分绩点 (GPA)</div>
              <div v-if="loadingStats" class="mt-2 space-y-1.5">
                <div class="h-7 w-20 bg-zinc-200/80 dark:bg-zinc-800 animate-pulse rounded-md" />
                <div class="h-3 w-24 bg-zinc-200/60 dark:bg-zinc-800/60 animate-pulse rounded" />
              </div>
              <template v-else>
                <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-1">
                  {{ stats.gpa }}
                </div>
                <div class="text-[11px] text-emerald-600 dark:text-emerald-400 mt-0.5 flex items-center gap-1">
                  <span>加权均分: {{ stats.weightedAvg }}</span>
                </div>
              </template>
            </div>
            <div class="w-10 h-10 rounded-xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-zinc-700 dark:text-zinc-300 shrink-0">
              <Icon name="score" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>

        <!-- Credits Card -->
        <UiCard>
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">已获得学分 / 方案总学分</div>
              <div v-if="loadingStats" class="mt-2 space-y-1.5">
                <div class="h-7 w-28 bg-zinc-200/80 dark:bg-zinc-800 animate-pulse rounded-md" />
                <div class="h-3 w-28 bg-zinc-200/60 dark:bg-zinc-800/60 animate-pulse rounded" />
              </div>
              <template v-else>
                <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-1">
                  {{ stats.earnedCredits }} <span class="text-xs font-normal text-zinc-400">/ {{ stats.requiredCredits }}</span>
                </div>
                <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5 truncate">
                  已修读 {{ stats.totalCredits }} 学分 ({{ stats.courseCount }} 门)
                </div>
              </template>
            </div>
            <div class="w-10 h-10 rounded-xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-zinc-700 dark:text-zinc-300 shrink-0">
              <Icon name="program" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>

        <!-- Today Courses Card -->
        <UiCard>
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">今日课程安排</div>
              <div v-if="loadingTimetable" class="mt-2 space-y-1.5">
                <div class="h-7 w-20 bg-zinc-200/80 dark:bg-zinc-800 animate-pulse rounded-md" />
                <div class="h-3 w-24 bg-zinc-200/60 dark:bg-zinc-800/60 animate-pulse rounded" />
              </div>
              <template v-else>
                <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-1">
                  {{ todayCourses.length }} <span class="text-xs font-normal text-zinc-400">门待上</span>
                </div>
                <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5">
                  当前教学周第 {{ currentWeek }} 周
                </div>
              </template>
            </div>
            <div class="w-10 h-10 rounded-xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-zinc-700 dark:text-zinc-300 shrink-0">
              <Icon name="timetable" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>

        <!-- Recent Exams Card -->
        <UiCard>
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">近期考试</div>
              <div v-if="loadingExams" class="mt-2 space-y-1.5">
                <div class="h-7 w-16 bg-zinc-200/80 dark:bg-zinc-800 animate-pulse rounded-md" />
                <div class="h-3 w-28 bg-zinc-200/60 dark:bg-zinc-800/60 animate-pulse rounded" />
              </div>
              <template v-else>
                <div class="text-2xl font-bold text-rose-600 dark:text-rose-400 mt-1">
                  {{ upcomingExams.length }} <span class="text-xs font-normal text-zinc-400">门</span>
                </div>
                <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5 truncate max-w-[140px]">
                  {{ upcomingExams[0]?.courseName || '暂无近期考试' }}
                </div>
              </template>
            </div>
            <div class="w-10 h-10 rounded-xl bg-rose-50 dark:bg-rose-950/40 flex items-center justify-center text-rose-600 dark:text-rose-400 shrink-0">
              <Icon name="exam" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>
      </div>

      <!-- Main Content Two Columns -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Column: Today's Schedule & Exam Radar -->
        <div class="lg:col-span-2 space-y-6">
          <!-- Today's Schedule Card -->
          <UiCard :title="`今日课程安排 (${currentDayName})`">
            <template #header-action>
              <div class="flex items-center gap-2">
                <UiButton size="sm" variant="ghost" :loading="loadingTimetable" @click="fetchTimetableData">
                  刷新
                </UiButton>
                <UiButton size="sm" variant="ghost" @click="$emit('navigate', 'timetable')">
                  查看完整课表 →
                </UiButton>
              </div>
            </template>

            <div v-if="loadingTimetable" class="py-12 text-center text-xs text-zinc-400">
              <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2" />
              正在同步教务课表...
            </div>

            <div v-else-if="todayCourses.length === 0" class="py-8 text-center text-xs text-zinc-400">
              今日无排课或已结课，享受自习时光吧 ☕
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="item in todayCourses"
                :key="item.courseName + item.sectionIndex"
                class="p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/30 flex items-start justify-between"
              >
                <div class="flex items-start gap-4">
                  <div class="shrink-0 pt-0.5 whitespace-nowrap">
                    <div class="font-bold text-sm text-zinc-800 dark:text-zinc-200">
                      {{ item.sectionLabel || `第${item.sectionIndex}大节` }}
                    </div>
                    <div
                      v-if="getCombineSlotTime(item.sectionIndex)"
                      class="text-[10px] text-zinc-400 dark:text-zinc-500 font-mono mt-0.5"
                    >
                      {{ getCombineSlotTime(item.sectionIndex) }}
                    </div>
                  </div>
                  <div>
                    <div class="flex items-center gap-2">
                      <h4 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{{ item.courseName }}</h4>
                      <UiBadge v-if="item.courseSeq" size="sm" variant="outline">序号 {{ item.courseSeq }}</UiBadge>
                    </div>
                    <div class="flex flex-wrap items-center gap-4 text-xs text-zinc-500 dark:text-zinc-400 mt-1">
                      <span v-if="item.location" class="flex items-center gap-1">
                        <Icon name="map-pin" customClass="w-3.5 h-3.5" />
                        {{ item.location }}
                      </span>
                      <span v-if="item.teacher" class="flex items-center gap-1">
                        <Icon name="profile" customClass="w-3.5 h-3.5" />
                        {{ item.teacher }} 老师
                      </span>
                      <span v-if="item.weeks" class="text-[11px] text-zinc-400">
                        {{ item.weeks }}
                      </span>
                    </div>
                  </div>
                </div>
                <UiBadge variant="default" size="sm">在修课程</UiBadge>
              </div>
            </div>
          </UiCard>

          <!-- Exam Countdown Radar Card -->
          <UiCard title="考试安排与考场">
            <template #header-action>
              <UiButton size="sm" variant="ghost" @click="$emit('navigate', 'exam')">
                全部考试清单 →
              </UiButton>
            </template>

            <div v-if="loadingExams" class="py-8 text-center text-xs text-zinc-400">
              <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2" />
              正在同步考试日程...
            </div>

            <div v-else-if="upcomingExams.length === 0" class="py-8 text-center text-xs text-zinc-400">
              当前暂无考试安排，或本学期考试尚未发布排考考场。
            </div>

            <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div
                v-for="exam in upcomingExams"
                :key="exam.courseId + exam.time"
                class="p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#141619] relative overflow-hidden"
              >
                <div class="flex items-start justify-between">
                  <div>
                    <UiBadge variant="warning" size="sm" dot>{{ exam.property || '考试' }}</UiBadge>
                    <h4 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 mt-2">{{ exam.courseName }}</h4>
                    <div class="text-xs text-zinc-500 dark:text-zinc-400 mt-1 space-y-0.5">
                      <div>时间：{{ exam.time }}</div>
                      <div>考场：<span class="font-medium text-zinc-800 dark:text-zinc-200">{{ exam.location }}</span></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </UiCard>
        </div>

        <!-- Right Column: Announcements & Fast Tools -->
        <div class="space-y-6">
          <!-- Quick Tools -->
          <UiCard title="快捷服务">
            <div class="grid grid-cols-2 gap-2.5">
              <button
                v-for="tool in quickTools"
                :key="tool.label"
                type="button"
                class="p-3 rounded-lg border border-zinc-200/60 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-900/40 hover:bg-zinc-100 dark:hover:bg-zinc-800/80 transition-all text-left cursor-pointer group"
                @click="$emit('navigate', tool.target)"
              >
                <Icon :name="tool.icon" customClass="w-5 h-5 text-zinc-700 dark:text-zinc-300 group-hover:scale-110 transition-transform mb-1.5" />
                <div class="text-xs font-semibold text-zinc-900 dark:text-zinc-100">{{ tool.label }}</div>
                <div class="text-[10px] text-zinc-400 dark:text-zinc-500 truncate">{{ tool.desc }}</div>
              </button>
            </div>
          </UiCard>

          <!-- Latest Notices -->
          <UiCard title="最新教学运行公告">
            <template #header-action>
              <UiButton size="sm" variant="ghost" @click="$emit('navigate', 'notice')">
                查看更多 →
              </UiButton>
            </template>

            <div v-if="loadingNotices" class="py-6 text-center text-xs text-zinc-400">
              正在获取校历与公告...
            </div>

            <div v-else-if="latestNotices.length === 0" class="py-6 text-center text-xs text-zinc-400">
              本周暂无发布的教学运行新公告
            </div>

            <div v-else class="space-y-3">
              <div
                v-for="(n, idx) in latestNotices"
                :key="idx"
                class="p-2.5 rounded-lg hover:bg-zinc-100/70 dark:hover:bg-zinc-800/40 transition-colors cursor-pointer"
                @click="$emit('navigate', 'notice')"
              >
                <div class="flex items-center gap-1.5 mb-1">
                  <UiBadge size="sm" variant="default">教务通知</UiBadge>
                  <span class="text-[11px] text-zinc-400 dark:text-zinc-500">{{ n.date }}</span>
                </div>
                <h5 class="text-xs font-medium text-zinc-800 dark:text-zinc-200 line-clamp-2 leading-relaxed">
                  {{ n.title }}
                </h5>
              </div>
            </div>
          </UiCard>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import LoginCard from '@/components/auth/LoginCard.vue';
import { useSession } from '@/composables/useSession.js';
import { academicApi } from '@/services/academic/api.js';
import { getCombineSlotTime } from '@/utils/periodTimes.js';

defineEmits(['navigate']);

const { isLoggedIn, authChecked, studentId, currentWeek, currentSemester } = useSession();

// Local cache keys
const CACHE_STATS_KEY = 'better_hrbust_cache_stats';
const CACHE_TIMETABLE_KEY = 'better_hrbust_cache_timetable';
const CACHE_EXAMS_KEY = 'better_hrbust_cache_exams';
const CACHE_NOTICES_KEY = 'better_hrbust_cache_notices';

const stats = ref({
  gpa: '0.00',
  weightedAvg: '0.0',
  totalCredits: 0,
  earnedCredits: 0,
  requiredCredits: 160,
  courseCount: 0
});

const allCourses = ref([]);
const upcomingExams = ref([]);
const latestNotices = ref([]);

// Restore cached data synchronously on component setup
function loadCachedData() {
  try {
    const s = localStorage.getItem(CACHE_STATS_KEY);
    if (s) {
      const parsed = JSON.parse(s);
      if (parsed && typeof parsed.totalCredits === 'number') stats.value = parsed;
    }
    const t = localStorage.getItem(CACHE_TIMETABLE_KEY);
    if (t) {
      const parsed = JSON.parse(t);
      if (Array.isArray(parsed) && parsed.length > 0) allCourses.value = parsed;
    }
    const e = localStorage.getItem(CACHE_EXAMS_KEY);
    if (e) {
      const parsed = JSON.parse(e);
      if (Array.isArray(parsed) && parsed.length > 0) upcomingExams.value = parsed.filter(isRecentExam);
    }
    const n = localStorage.getItem(CACHE_NOTICES_KEY);
    if (n) {
      const parsed = JSON.parse(n);
      if (Array.isArray(parsed) && parsed.length > 0) latestNotices.value = parsed;
    }
  } catch {
    // 忽略缓存解析错误
  }
}

loadCachedData();

const hasCachedData = computed(() => {
  return stats.value.totalCredits > 0 || allCourses.value.length > 0 || upcomingExams.value.length > 0;
});

// If cached data is present, do not show blank skeletons initially
const loadingStats = ref(!hasCachedData.value);
const loadingTimetable = ref(!hasCachedData.value);
const loadingExams = ref(!hasCachedData.value);
const loadingNotices = ref(!hasCachedData.value);

const dayMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const currentDayIndex = new Date().getDay() || 7; // 1~7
const currentDayName = computed(() => dayMap[new Date().getDay()]);

const todayCourses = computed(() => {
  return allCourses.value.filter(c => c.day === currentDayIndex);
});

async function fetchStats() {
  if (!hasCachedData.value) loadingStats.value = true;
  try {
    const [scoresRes, planRes] = await Promise.all([
      academicApi.getScores().catch(() => ({ scores: [] })),
      academicApi.getCurriculumPlan().catch(() => ({ groups: [] }))
    ]);
    const scores = scoresRes.scores || [];
    const groups = planRes.groups || [];

    let totalCredits = 0;
    let earnedCredits = 0;
    let totalScoreWeight = 0;
    let totalGpaWeight = 0;

    scores.forEach(item => {
      const cr = item.credit || 0;
      const num = parseFloat(item.score);
      totalCredits += cr;
      if (item.passed) earnedCredits += cr;

      if (!isNaN(num)) {
        totalScoreWeight += num * cr;
        const gpa = num >= 60 ? (num - 50) / 10 : 0;
        totalGpaWeight += gpa * cr;
      }
    });

    const requiredCredits = groups.reduce((acc, g) => acc + (g.requiredCredits || 0), 0) || 160;

    stats.value = {
      gpa: totalCredits ? (totalGpaWeight / totalCredits).toFixed(2) : '0.00',
      weightedAvg: totalCredits ? (totalScoreWeight / totalCredits).toFixed(1) : '0.0',
      totalCredits: parseFloat(totalCredits.toFixed(1)),
      earnedCredits: parseFloat(earnedCredits.toFixed(1)),
      requiredCredits: parseFloat(requiredCredits.toFixed(1)),
      courseCount: scores.length
    };

    localStorage.setItem(CACHE_STATS_KEY, JSON.stringify(stats.value));
  } catch {
    // 忽略未登录或网络异常
  } finally {
    loadingStats.value = false;
  }
}

async function fetchTimetableData() {
  if (!studentId.value) return;
  if (!hasCachedData.value) loadingTimetable.value = true;
  try {
    const res = await academicApi.getTimetable({
      studentId: studentId.value,
      yearId: currentSemester.yearId || '46',
      termId: currentSemester.termId || '2',
      sectionType: 'COMBINE'
    });
    allCourses.value = res.cells || [];
    if (allCourses.value.length > 0) {
      localStorage.setItem(CACHE_TIMETABLE_KEY, JSON.stringify(allCourses.value));
    }
  } catch {
    // 失败保留现有缓存
  } finally {
    loadingTimetable.value = false;
  }
}

function isRecentExam(exam) {
  if (!exam.time) return true;
  const m = exam.time.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
  if (!m) return true;

  const examDate = new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10));
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const sevenDaysAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);

  // 严格只显示未来的考试和过去 7 天以内的考试，早于 7 天前的均不属于近期
  return examDate >= sevenDaysAgo;
}

async function fetchExamsData() {
  if (!hasCachedData.value) loadingExams.value = true;
  try {
    const res = await academicApi.getExams();
    const filtered = (res || []).filter(isRecentExam);
    filtered.sort((a, b) => {
      const ma = a.time?.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
      const mb = b.time?.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
      const ta = ma ? new Date(parseInt(ma[1], 10), parseInt(ma[2], 10) - 1, parseInt(ma[3], 10)).getTime() : 0;
      const tb = mb ? new Date(parseInt(mb[1], 10), parseInt(mb[2], 10) - 1, parseInt(mb[3], 10)).getTime() : 0;
      return ta - tb;
    });
    upcomingExams.value = filtered;
    if (upcomingExams.value.length > 0) {
      localStorage.setItem(CACHE_EXAMS_KEY, JSON.stringify(upcomingExams.value));
    }
  } catch {
    // 失败保留现有缓存
  } finally {
    loadingExams.value = false;
  }
}

async function fetchNoticesData() {
  if (!hasCachedData.value) loadingNotices.value = true;
  try {
    const res = await academicApi.getCalendarInfo(currentWeek.value);
    latestNotices.value = res.notices?.slice(0, 4) || [];
    if (latestNotices.value.length > 0) {
      localStorage.setItem(CACHE_NOTICES_KEY, JSON.stringify(latestNotices.value));
    }
  } catch {
    // 失败保留现有缓存
  } finally {
    loadingNotices.value = false;
  }
}

async function loadDashboardData() {
  if (!isLoggedIn.value) return;
  await Promise.allSettled([
    fetchStats(),
    fetchTimetableData(),
    fetchExamsData(),
    fetchNoticesData()
  ]);
}

watch(isLoggedIn, (val) => {
  if (val) loadDashboardData();
});

onMounted(() => {
  if (isLoggedIn.value) loadDashboardData();
});

const quickTools = [
  { label: '自习空教室', desc: '查找当前空闲房间', icon: 'classroom', target: 'classroom' },
  { label: 'GPA 计算器', desc: '成绩与学分走势', icon: 'score', target: 'score' },
  { label: '导出课表 ICS', desc: '导入系统日历', icon: 'download', target: 'timetable' },
  { label: '毕业方案核查', desc: '学分完成度监控', icon: 'program', target: 'program' }
];
</script>