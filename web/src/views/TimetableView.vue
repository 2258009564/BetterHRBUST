<template>
  <div class="space-y-4">
    <!-- Unauthenticated State -->
    <UiCard v-if="!isLoggedIn" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="timetable" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以同步您的个人专属课程表</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
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
              <div class="px-3 py-1 text-sm font-semibold text-zinc-900 dark:text-zinc-100 min-w-24 text-center">
                第 {{ selectedWeek }} 周
                <span v-if="selectedWeek === currentWeek" class="text-[10px] text-emerald-600 dark:text-emerald-400 font-normal block">
                  (当前周)
                </span>
              </div>
              <button
                type="button"
                class="p-1.5 rounded-lg border border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer text-zinc-600 dark:text-zinc-300"
                @click="nextWeek"
              >
                <Icon name="chevron-right" customClass="w-4 h-4" />
              </button>
            </div>

            <!-- Week Slider -->
            <input
              type="range"
              min="1"
              max="26"
              v-model.number="selectedWeek"
              class="w-32 sm:w-48 accent-zinc-900 dark:accent-zinc-100 cursor-pointer"
            />

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
          </div>

          <!-- Right: View switch, reload & ICS export -->
          <div class="flex items-center gap-2.5">
            <UiButton size="sm" variant="ghost" :loading="loading" @click="fetchTimetable">
              刷新课表
            </UiButton>

            <UiTabs
              :items="[
                { label: '大节模式 (COMBINE)', value: 'combine' },
                { label: '小节模式 (BASE)', value: 'base' }
              ]"
              v-model="viewMode"
              @update:modelValue="fetchTimetable"
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
                'p-3 text-center border-r border-zinc-200/80 dark:border-zinc-800 last:border-r-0',
                (dIdx + 1) === currentDayIndex ? 'text-zinc-900 dark:text-zinc-100 font-bold bg-zinc-100/60 dark:bg-zinc-800/40' : ''
              ]"
            >
              {{ dayName }}
              <span v-if="(dIdx + 1) === currentDayIndex" class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500 ml-1"></span>
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
              class="p-1.5 border-r border-zinc-100 dark:border-zinc-800/60 last:border-r-0 flex flex-col gap-1.5 relative min-w-0"
            >
              <div
                v-for="course in getVisibleCoursesForSlot(dayNum, slot.period)"
                :key="course.courseName + course.id"
                :class="[
                  'p-2.5 rounded-lg text-sm leading-tight transition-all duration-150 cursor-pointer border select-none h-full flex flex-col justify-between',
                  isCourseActiveThisWeek(course) ? getCourseColor(course).solid : getCourseColor(course).soft
                ]"
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
import { ref, computed, onMounted, watch } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiDrawer from '@/components/ui/UiDrawer.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useToast } from '@/composables/useToast.js';
import { academicApi } from '@/services/academic/api.js';
import { BASE_SLOT_TIMES, COMBINE_SLOT_TIMES } from '@/utils/periodTimes.js';
import { getCourseColor } from '@/utils/courseColors.js';

const { isLoggedIn, studentId, currentWeek, currentSemester, openLoginModal } = useSession();
const { showToast } = useToast();

const selectedWeek = ref(currentWeek.value);
const onlyCurrentWeek = ref(false);
const viewMode = ref('combine'); // 'combine' | 'base'
const currentDayIndex = ref(new Date().getDay() || 7);

const loading = ref(false);
const courses = ref([]);
const unarrangedCourses = ref([]);

const showDrawer = ref(false);
const selectedCourse = ref(null);

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
  const expr = course.weeks || '';
  if (!expr) return true;
  const isOdd = expr.includes('单');
  const isEven = expr.includes('双');
  const cur = selectedWeek.value;

  if (isOdd && cur % 2 === 0) return false;
  if (isEven && cur % 2 !== 0) return false;

  const parts = expr.match(/\d+(?:-\d+)?/g) || [];
  for (const p of parts) {
    const [start, end] = p.split('-').map(Number);
    const to = end || start;
    if (cur >= start && cur <= to) return true;
  }
  return parts.length === 0;
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

async function fetchTimetable() {
  if (!isLoggedIn.value || !studentId.value) return;
  loading.value = true;
  try {
    const res = await academicApi.getTimetable({
      studentId: studentId.value,
      yearId: currentSemester.yearId || '46',
      termId: currentSemester.termId || '2',
      sectionType: viewMode.value === 'combine' ? 'COMBINE' : 'BASE'
    });
    courses.value = res.cells || [];
    unarrangedCourses.value = res.unarranged || [];
  } catch (err) {
    showToast({ title: '课表加载失败', message: err.message, type: 'danger' });
  } finally {
    loading.value = false;
  }
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

watch(isLoggedIn, (val) => {
  if (val) fetchTimetable();
});

onMounted(() => {
  if (isLoggedIn.value) fetchTimetable();
});
</script>