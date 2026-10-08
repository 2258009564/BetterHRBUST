<template>
  <div class="space-y-4">
    <!-- Unauthenticated State -->
    <UiCard v-if="showLoginPrompt" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="timetable" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以同步您的个人专属课程表</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- 数据来源提示：全部走本地缓存，刷新统一由顶栏按钮完成 -->
      <div class="text-[11px] text-zinc-400 dark:text-zinc-500">上次同步：{{ lastSyncText }}</div>

      <!-- Top Controls: Section mode, Week selector, Actions -->
      <UiCard bodyClass="p-4">
        <div class="flex flex-wrap items-center justify-between gap-4">
          <!-- Left: Week selector -->
          <div class="flex items-center gap-3">
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="p-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer text-zinc-600 dark:text-zinc-300"
                @click="prevWeek"
              >
                <Icon name="chevron-left" customClass="w-4 h-4" />
              </button>
              <!-- 点击呼出周次选择卡片 -->
              <button
                type="button"
                class="px-3 py-1 rounded-lg border border-transparent hover:border-zinc-200 dark:hover:border-zinc-800 hover:bg-zinc-50 dark:hover:bg-zinc-800/60 cursor-pointer min-w-24 flex items-center justify-center gap-1"
                title="点击选择教学周次"
                @click="showWeekPicker = true"
              >
                <span class="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                  第 {{ selectedWeek }} 周
                </span>
                <Icon name="chevron-down" customClass="w-3.5 h-3.5 text-zinc-400 dark:text-zinc-500" />
              </button>
              <button
                type="button"
                class="p-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer text-zinc-600 dark:text-zinc-300"
                @click="nextWeek"
              >
                <Icon name="chevron-right" customClass="w-4 h-4" />
              </button>
            </div>

            <!-- Only This Week Toggle -->
            <button
              type="button"
              :class="[
                'px-2.5 py-1 text-xs rounded-lg border cursor-pointer transition-colors',
                onlyCurrentWeek
                  ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 border-transparent'
                  : 'border-zinc-200 dark:border-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800'
              ]"
              @click="onlyCurrentWeek = !onlyCurrentWeek"
            >
              仅看本周课程
            </button>

            <!-- 当前周标记 / 回到本周（互斥显示，同一位置且等高，无布局跳动） -->
            <span
              v-if="selectedWeek === currentWeek"
              class="px-2.5 py-1 text-xs rounded-lg border border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 flex items-center gap-1 select-none"
            >
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              当前周
            </span>
            <button
              v-else
              type="button"
              class="px-2.5 py-1 text-xs rounded-lg border border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 hover:bg-emerald-500/20 cursor-pointer transition-colors flex items-center gap-1"
              @click="backToCurrentWeek"
            >
              <Icon name="refresh" customClass="w-3 h-3" />
              回到本周
            </button>
          </div>

          <!-- Right: View switch & ICS export（数据刷新统一由顶栏按钮完成） -->
          <div class="flex items-center gap-2.5">
            <UiTabs
              :items="[
                { label: '大节模式 (COMBINE)', value: 'combine' },
                { label: '小节模式 (BASE)', value: 'base' }
              ]"
              v-model="viewMode"
            />

            <UiButton size="sm" variant="outline" @click="exportIcs">
              <template #prefix>
                <Icon name="download" customClass="w-3.5 h-3.5" />
              </template>
              导出 ICS
            </UiButton>
          </div>
        </div>
      </UiCard>

      <!-- Loading skeleton -->
      <div v-if="loading" class="p-16 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#111316] text-center text-xs text-zinc-400">
        <Icon name="refresh" customClass="w-6 h-6 animate-spin mx-auto mb-2.5 text-zinc-500" />
        正在从哈理工教务系统拉取最新课表...
      </div>

      <!-- Timetable Grid -->
      <div v-else class="rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#111316] overflow-x-auto shadow-xs">
        <div class="min-w-[1040px]">
          <!-- Table Header (Days of week) -->
          <div
            class="grid border-b border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/80 dark:bg-zinc-900/60 text-sm font-semibold text-zinc-700 dark:text-zinc-300"
            :style="{ gridTemplateColumns: gridTemplate }"
          >
            <div class="p-3 text-center text-zinc-400 dark:text-zinc-500 border-r border-zinc-200/80 dark:border-zinc-800">
              节次 / 时间
            </div>
            <div
              v-for="(dayName, dIdx) in daysOfWeek"
              :key="dayName"
              :class="[
                'p-3 text-center border-r border-zinc-200/80 dark:border-zinc-800 last:border-r-0 transition-colors',
                isTodayColumn(dIdx + 1)
                  ? 'text-zinc-900 dark:text-zinc-100 font-bold bg-emerald-500/[0.1] dark:bg-emerald-400/[0.08]'
                  : ''
              ]"
            >
              {{ dayName }}
              <span v-if="isTodayColumn(dIdx + 1)" class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500 ml-1"></span>
            </div>
          </div>

          <!-- Table Rows -->
          <div
            v-for="slot in activeSlots"
            :key="slot.period"
            :class="[
              'grid border-b border-zinc-100 dark:border-zinc-800/60 last:border-b-0',
              isRowOccupied(slot.period) ? 'min-h-[118px]' : 'min-h-[44px]',
              slot.period % 2 === 0 ? 'bg-zinc-50/30 dark:bg-zinc-900/10' : ''
            ]"
            :style="{ gridTemplateColumns: gridTemplate }"
          >
            <!-- Period Header -->
            <div
              :class="[
                'p-2 border-r border-zinc-100 dark:border-zinc-800/60 flex flex-col justify-center items-center text-center',
                isRowOccupied(slot.period) ? '' : 'opacity-70'
              ]"
            >
              <span
                :class="isRowOccupied(slot.period) ? 'text-sm' : 'text-xs'"
                class="font-bold text-zinc-800 dark:text-zinc-200"
              >
                {{ viewMode === 'combine' ? `第 ${slot.period} 大节` : `第 ${slot.period} 节` }}
              </span>
              <span
                :class="isRowOccupied(slot.period) ? 'text-xs' : 'text-[10px]'"
                class="text-zinc-400 font-mono mt-0.5"
              >
                {{ slot.time }}
              </span>
            </div>

            <!-- 7 Days Slots -->
            <div
              v-for="dayNum in 7"
              :key="dayNum"
              :class="[
                'p-1.5 border-r border-zinc-100 dark:border-zinc-800/60 last:border-r-0 flex flex-col gap-1.5 relative min-w-0 transition-colors',
                isTodayColumn(dayNum) ? 'bg-emerald-500/[0.07] dark:bg-emerald-400/[0.06]' : ''
              ]"
            >
              <div
                v-for="course in getVisibleCoursesForSlot(dayNum, slot.period)"
                :key="course.courseName + course.id"
                :class="[
                  'p-2.5 rounded-lg text-sm leading-tight transition-all duration-150 cursor-pointer border select-none h-full flex flex-col justify-between',
                  isCourseActiveThisWeek(course) ? 'course-card' : COURSE_MUTED
                ]"
                :style="courseColorStyle(course)"
                @click="openCourseDetail(course)"
              >
                <div>
                  <div class="font-bold line-clamp-2 tracking-tight">{{ course.courseName }}</div>
                  <div class="text-xs opacity-80 mt-1.5 flex items-center gap-1">
                    <span class="truncate">{{ course.location || '待定' }}</span>
                  </div>
                </div>
                <div class="text-[11px] opacity-75 mt-1.5 truncate">
                  {{ course.teacher }} · {{ course.weeks }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Unarranged courses table -->
      <UiCard v-if="unarrangedCourses.length > 0" title="未安排固定节次/地点的课程 (实践/实验/重修)">
        <div class="divide-y divide-zinc-100 dark:divide-zinc-800 text-xs">
          <div
            v-for="item in unarrangedCourses"
            :key="item.courseId"
            class="py-2.5 flex items-center justify-between"
          >
            <div>
              <span class="font-semibold text-zinc-900 dark:text-zinc-100">{{ item.courseName }}</span>
              <span class="text-zinc-400 ml-2 font-mono text-[11px]">{{ item.courseId }}</span>
            </div>
            <div class="text-zinc-500 space-x-3">
              <span>教师: {{ item.teacher || '待定' }}</span>
              <span>周次: {{ item.weeks || '全学期' }}</span>
            </div>
          </div>
        </div>
      </UiCard>

      <!-- Week Picker Modal -->
      <UiModal v-model="showWeekPicker" title="选择教学周次">
        <div class="text-xs text-zinc-500 dark:text-zinc-400 mb-3">
          共 26 个教学周，绿色标记为当前教学周
        </div>
        <div class="grid grid-cols-5 sm:grid-cols-7 gap-2">
          <button
            v-for="w in 26"
            :key="w"
            type="button"
            :class="[
              'h-12 rounded-lg border flex flex-col items-center justify-center gap-0.5 cursor-pointer select-none transition-colors',
              w === selectedWeek
                ? 'bg-zinc-900 text-white border-transparent dark:bg-zinc-100 dark:text-zinc-900'
                : 'border-zinc-200 dark:border-zinc-800 text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800'
            ]"
            @click="pickWeek(w)"
          >
            <span class="text-sm font-bold leading-none">{{ w }}</span>
            <span
              v-if="w === currentWeek"
              :class="[
                'text-[9px] leading-none font-medium',
                w === selectedWeek ? 'opacity-80' : 'text-emerald-600 dark:text-emerald-400'
              ]"
            >
              本周
            </span>
          </button>
        </div>
      </UiModal>

      <!-- Course Detail Drawer -->
      <UiDrawer
        v-model="showDrawer"
        :title="selectedCourse?.courseName || '课程详情'"
      >
        <div v-if="selectedCourse" class="space-y-4 text-xs">
          <div class="p-3 rounded-xl bg-zinc-50 dark:bg-zinc-900/50 border border-zinc-200/80 dark:border-zinc-800 space-y-2">
            <div class="flex justify-between">
              <span class="text-zinc-500">课程序号</span>
              <span class="font-semibold">{{ selectedCourse.courseSeq || '无' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-zinc-500">任课教师</span>
              <span class="font-semibold">{{ selectedCourse.teacher }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-zinc-500">上课地点</span>
              <span class="font-semibold">{{ selectedCourse.location }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-zinc-500">授课周次</span>
              <span class="font-semibold">{{ selectedCourse.weeks }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-zinc-500">课时属性</span>
              <span class="font-semibold">{{ selectedCourse.hoursType || '讲课学时' }}</span>
            </div>
          </div>

          <div class="p-3 rounded-lg border border-emerald-500/20 bg-emerald-500/5 text-emerald-700 dark:text-emerald-300 text-[11px] leading-relaxed">
            数据直连自哈理工教务系统接口 <code class="font-mono">showTimetable.do</code>
          </div>
        </div>
      </UiDrawer>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiModal from '@/components/ui/UiModal.vue';
import UiDrawer from '@/components/ui/UiDrawer.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';
import { useToast } from '@/composables/useToast.js';
import { BASE_SLOT_TIMES, COMBINE_SLOT_TIMES } from '@/utils/periodTimes.js';
import { getCourseColor, COURSE_MUTED } from '@/utils/courseColors.js';
import { isCourseActiveInWeek } from '@/utils/courseWeeks.js';

const { isLoggedIn, isSessionExpired, currentWeek } = useSession();
// 课表数据来自登录时的全量缓存，切换周次/视图不再触网
const { timetableCombine, timetableBase, syncing, lastSyncText } = useAcademicData();
const { showToast } = useToast();

// 会话失效但仍持有离线缓存时，继续展示课表而非登录引导
const showLoginPrompt = computed(() => !isLoggedIn.value && !isSessionExpired.value);

const selectedWeek = ref(currentWeek.value);
const onlyCurrentWeek = ref(false);
const viewMode = ref('combine'); // 'combine' | 'base'
const currentDayIndex = ref(new Date().getDay() || 7);

const loading = computed(() => syncing.value && activeTimetable.value.cells.length === 0);

const activeTimetable = computed(() =>
  viewMode.value === 'combine' ? timetableCombine.value : timetableBase.value
);
const courses = computed(() => activeTimetable.value.cells || []);
const unarrangedCourses = computed(() => activeTimetable.value.unarranged || []);

const showDrawer = ref(false);
const selectedCourse = ref(null);
const showWeekPicker = ref(false);

const daysOfWeek = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

const activeSlots = computed(() => {
  return viewMode.value === 'combine' ? COMBINE_SLOT_TIMES : BASE_SLOT_TIMES;
});

function prevWeek() {
  if (selectedWeek.value > 1) selectedWeek.value -= 1;
}

function nextWeek() {
  if (selectedWeek.value < 26) selectedWeek.value += 1;
}

function isCourseActiveThisWeek(course) {
  return isCourseActiveInWeek(course, selectedWeek.value);
}

/** 点击周次选择卡片中的某一周 */
function pickWeek(week) {
  selectedWeek.value = week;
  showWeekPicker.value = false;
}

/** 快速跳回当前教学周 */
function backToCurrentWeek() {
  selectedWeek.value = currentWeek.value;
}

/**
 * 仅当正在浏览当前教学周时，"今天"所在的整列才高亮；
 * 查看其他周次时不显示今天标记，避免误导。
 */
function isTodayColumn(dayNum) {
  return selectedWeek.value === currentWeek.value && dayNum === currentDayIndex.value;
}

/** 课程卡片动态配色（非当前教学周返回 null，走中性灰 COURSE_MUTED 样式） */
function courseColorStyle(course) {
  return isCourseActiveThisWeek(course) ? getCourseColor(course).style : null;
}

// 当前筛选条件下实际展示的课程（"仅看本周" 关闭时为全部课程）
const visibleCourses = computed(() =>
  courses.value.filter(c => !onlyCurrentWeek.value || isCourseActiveThisWeek(c))
);

const hasAnyCourse = computed(() => visibleCourses.value.length > 0);
const occupiedSections = computed(() => new Set(visibleCourses.value.map(c => c.sectionIndex)));
const occupiedDays = computed(() => new Set(visibleCourses.value.map(c => c.day)));

// 整行 / 整列无课时压缩，把空间让给有课的格子
function isRowOccupied(period) {
  return !hasAnyCourse.value || occupiedSections.value.has(period);
}

function isDayOccupied(day) {
  return !hasAnyCourse.value || occupiedDays.value.has(day);
}

// 有课列宽 > 108px 并均分剩余空间，无课列收窄
const gridTemplate = computed(() => {
  const dayCols = daysOfWeek.map((_, idx) =>
    isDayOccupied(idx + 1) ? 'minmax(132px, 1fr)' : 'minmax(56px, 0.28fr)'
  );
  return `96px ${dayCols.join(' ')}`;
});

function getVisibleCoursesForSlot(day, period) {
  return visibleCourses.value.filter(c => c.day === day && c.sectionIndex === period);
}

function openCourseDetail(course) {
  selectedCourse.value = course;
  showDrawer.value = true;
}

function exportIcs() {
  if (courses.value.length === 0) {
    showToast({ title: '暂无可导出的课程数据', type: 'warning' });
    return;
  }
  const ics = `BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//BetterHRBUST//Timetable//CN\nCALSCALE:GREGORIAN\n` +
    courses.value.map(c => `BEGIN:VEVENT\nSUMMARY:${c.courseName}\nLOCATION:${c.location || ''}\nDESCRIPTION:教师: ${c.teacher || ''} 周次: ${c.weeks || ''}\nEND:VEVENT`).join('\n') +
    `\nEND:VCALENDAR`;
  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `HRBUST_课表_第${selectedWeek.value}周.ics`;
  a.click();
  URL.revokeObjectURL(url);
  showToast({ title: '日历已成功导出', message: '已生成 .ics 标准日历文件', type: 'success' });
}
</script>