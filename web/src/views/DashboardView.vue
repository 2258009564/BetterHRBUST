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
      <LoginCard />
    </div>

    <!-- Authenticated State or Cached Offline State -->
    <template v-else>
      <!-- Top Metric Highlights -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <!-- GPA Card -->
        <UiCard customClass="relative overflow-hidden">
          <div class="flex items-center justify-between">
            <div class="flex-1">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">平均学分绩点 (五分制 GPA)</div>
              <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-1">
                {{ stats.gpa.toFixed(2) }}
              </div>
              <div class="text-[11px] text-emerald-600 dark:text-emerald-400 mt-0.5 flex items-center gap-1">
                <span>加权均分: {{ stats.weightedAvg.toFixed(1) }}</span>
              </div>
              <div class="text-[11px] text-zinc-400 dark:text-zinc-500 mt-0.5">仅统计必修课</div>
            </div>
            <div class="w-10 h-10 rounded-xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-zinc-700 dark:text-zinc-300 shrink-0">
              <Icon name="score" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>

        <!-- Credits Card（与"培养方案与学分"页同口径） -->
        <UiCard>
          <div class="flex items-center justify-between">
            <div class="flex-1 min-w-0">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">已获得学分 / 方案总学分</div>
              <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-1">
                {{ creditsProgress.earnedTotal }} <span class="text-xs font-normal text-zinc-400">/ {{ creditsProgress.requiredTotal }}</span>
              </div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5 truncate">
                必修课已修读 {{ stats.totalCredits }} 学分 ({{ stats.courseCount }} 门)
              </div>
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
              <div class="text-2xl font-bold text-zinc-900 dark:text-zinc-100 mt-1">
                {{ todayCourses.length }} <span class="text-xs font-normal text-zinc-400">门待上</span>
              </div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5">
                当前教学周第 {{ currentWeek }} 周
              </div>
            </div>
            <div class="w-10 h-10 rounded-xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-zinc-700 dark:text-zinc-300 shrink-0">
              <Icon name="timetable" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>

        <!-- Recent Exams Card -->
        <UiCard>
          <div class="flex items-center justify-between">
            <div class="flex-1 min-w-0">
              <div class="text-xs font-medium text-zinc-500 dark:text-zinc-400">近期考试</div>
              <div class="text-2xl font-bold text-rose-600 dark:text-rose-400 mt-1">
                {{ upcomingExams.length }} <span class="text-xs font-normal text-zinc-400">门</span>
              </div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5 truncate max-w-[140px]">
                {{ upcomingExams[0]?.courseName || '暂无近期考试' }}
              </div>
            </div>
            <div class="w-10 h-10 rounded-xl bg-rose-50 dark:bg-rose-950/40 flex items-center justify-center text-rose-600 dark:text-rose-400 shrink-0">
              <Icon name="exam" customClass="w-5 h-5" />
            </div>
          </div>
        </UiCard>
      </div>

      <!-- 特色学业算法摘要（与成绩页共用同一口径） -->
      <UiCard>
        <template #header-action>
          <UiButton size="sm" variant="ghost" @click="$emit('navigate', 'score')">
            查看成绩分析 →
          </UiButton>
        </template>

        <div class="flex flex-wrap items-center justify-between gap-4">
          <div class="flex flex-wrap items-center gap-x-8 gap-y-3">
            <div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400">学位绩点</div>
              <div class="text-lg font-bold" :class="stats.degree.qualified ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'">
                {{ stats.degree.gpa.toFixed(2) }}
              </div>
            </div>
            <div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400">补考 / 重修</div>
              <div class="text-lg font-bold" :class="stats.recommend.qualified ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'">
                {{ stats.recommend.retakeCount }} / {{ stats.recommend.retakeLimit }}
              </div>
            </div>
            <div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400">累计挂科学分</div>
              <div class="text-lg font-bold" :class="stats.risk.level === 'none' ? 'text-zinc-900 dark:text-zinc-100' : 'text-rose-600 dark:text-rose-400'">
                {{ stats.risk.failedCredits }}
              </div>
            </div>
            <div>
              <div class="text-[11px] text-zinc-500 dark:text-zinc-400">提前毕业</div>
              <div class="text-lg font-bold" :class="stats.earlyGraduation.qualified ? 'text-emerald-600 dark:text-emerald-400' : 'text-zinc-900 dark:text-zinc-100'">
                {{ stats.earlyGraduation.qualified ? '达标' : '未达标' }}
              </div>
            </div>
            <UiBadge size="sm" :variant="stats.risk.level === 'none' ? 'success' : 'danger'">
              {{ stats.risk.label }}
            </UiBadge>
          </div>

          <div class="text-[11px] text-zinc-400 dark:text-zinc-500 leading-relaxed max-w-md">
            {{ statsScopeNote }}
          </div>
        </div>
      </UiCard>

      <!-- Main Content Two Columns -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Column: Today's Schedule & Exam Radar -->
        <div class="lg:col-span-2 space-y-6">
          <!-- Today's Schedule Card -->
          <UiCard :title="`今日课程安排 (${currentDayName})`">
            <template #header-action>
              <UiButton size="sm" variant="ghost" @click="$emit('navigate', 'timetable')">
                查看完整课表 →
              </UiButton>
            </template>

            <div v-if="todayCourses.length === 0" class="py-8 text-center text-xs text-zinc-400">
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
                      <span class="w-2 h-2 rounded-full shrink-0" :class="getCourseColor(item).dot"></span>
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

            <div v-if="upcomingExams.length === 0" class="py-8 text-center text-xs text-zinc-400">
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

            <div v-if="latestNotices.length === 0" class="py-6 text-center text-xs text-zinc-400">
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
import { computed } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import Icon from '@/components/icons/Icon.vue';
import LoginCard from '@/components/auth/LoginCard.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';
import { buildAcademicStats, computeCreditsProgress, STATS_SCOPE_NOTE } from '@/services/academic/stats.js';
import { getCombineSlotTime } from '@/utils/periodTimes.js';
import { getCourseColor } from '@/utils/courseColors.js';
import { isCourseActiveInWeek } from '@/utils/courseWeeks.js';

defineEmits(['navigate']);

const { isLoggedIn, authChecked, currentWeek } = useSession();
// 登录后数据全部走本地缓存，仅顶栏刷新按钮与每日首次打开会触网
const { scores, plan, timetableCombine, exams, notices, hasCachedData } = useAcademicData();

const stats = computed(() => buildAcademicStats(scores.value));
const statsScopeNote = STATS_SCOPE_NOTE;

// 与"培养方案与学分"页共用同一函数，保证两页数据完全一致
const creditsProgress = computed(() => computeCreditsProgress(scores.value, plan.value?.groups || []));

const dayMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const currentDayIndex = new Date().getDay() || 7; // 1~7
const currentDayName = computed(() => dayMap[new Date().getDay()]);

const todayCourses = computed(() => {
  const cells = timetableCombine.value?.cells || [];
  return cells.filter(
    c => c.day === currentDayIndex && isCourseActiveInWeek(c, currentWeek.value)
  );
});

function isRecentExam(exam) {
  if (!exam.time) return true;
  const m = exam.time.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
  if (!m) return true;

  const examDate = new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10));
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const sevenDaysAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000);

  // 严格只显示未来的考试和过去 7 天以内的考试
  return examDate >= sevenDaysAgo;
}

const upcomingExams = computed(() => {
  return (exams.value || [])
    .filter(isRecentExam)
    .slice()
    .sort((a, b) => {
      const ma = a.time?.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
      const mb = b.time?.match(/(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})/);
      const ta = ma ? new Date(parseInt(ma[1], 10), parseInt(ma[2], 10) - 1, parseInt(ma[3], 10)).getTime() : 0;
      const tb = mb ? new Date(parseInt(mb[1], 10), parseInt(mb[2], 10) - 1, parseInt(mb[3], 10)).getTime() : 0;
      return ta - tb;
    });
});

const latestNotices = computed(() => (notices.value || []).slice(0, 4));

const quickTools = [
  { label: '自习空教室', desc: '查找当前空闲房间', icon: 'classroom', target: 'classroom' },
  { label: 'GPA 计算器', desc: '成绩与学分走势', icon: 'score', target: 'score' },
  { label: '导出课表 ICS', desc: '导入系统日历', icon: 'download', target: 'timetable' },
  { label: '毕业方案核查', desc: '学分完成度监控', icon: 'program', target: 'program' }
];
</script>
