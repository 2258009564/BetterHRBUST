<template>
  <div class="space-y-6">
    <!-- Top Calendar and Notices View -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Notices List -->
      <div class="lg:col-span-2 space-y-4">
        <UiCard title="教学运行公告">
          <template #header-action>
            <div class="flex items-center gap-2">
              <span class="text-xs text-zinc-500">周次筛选：</span>
              <UiSelect
                v-model="selectedWeek"
                :items="weekItems"
                aria-label="周次筛选"
                size="sm"
                custom-class="min-w-[8.5rem]"
                @change="fetchNotices"
              />
            </div>
          </template>

          <div v-if="loading" class="py-12 text-center text-xs text-zinc-400">
            <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2 text-zinc-500" />
            正在拉取教务运行公告与校历...
          </div>

          <div v-else-if="notices.length === 0" class="py-8 text-center text-xs text-zinc-400">
            本周教务在线暂无最新运行公告通知
          </div>

          <div v-else class="divide-y divide-zinc-100 dark:divide-zinc-800">
            <div
              v-for="(n, idx) in notices"
              :key="idx"
              class="py-3.5 flex items-start justify-between gap-4 hover:bg-zinc-50/50 dark:hover:bg-zinc-900/30 -mx-6 px-6 cursor-pointer transition-colors"
              @click="readNotice(n)"
            >
              <div>
                <div class="flex items-center gap-2 mb-1">
                  <UiBadge size="sm" variant="default">教务运行</UiBadge>
                  <span class="text-[11px] text-zinc-400 font-mono">{{ n.date }}</span>
                </div>
                <h4 class="text-xs font-semibold text-zinc-900 dark:text-zinc-100 line-clamp-2 leading-relaxed">
                  {{ n.title }}
                </h4>
              </div>
              <span class="text-xs text-zinc-400 shrink-0">查看 →</span>
            </div>
          </div>
        </UiCard>
      </div>

      <!-- School Calendar Timeline -->
      <div class="space-y-4">
        <UiCard title="校历关键周次节点 (全校日历)">
          <div class="space-y-4 text-xs"><a :href="calendarSource" target="_blank" rel="noopener" class="underline">查看教务处官方校历 ↗</a>
            <div
              v-for="m in milestones"
              :key="m.title"
              class="relative pl-5 border-l-2"
              :class="m.isPassed ? 'border-zinc-900 dark:border-zinc-100' : 'border-zinc-200 dark:border-zinc-800'"
            >
              <div
                class="absolute -left-1.5 top-0.5 w-2.5 h-2.5 rounded-full"
                :class="m.isPassed ? 'bg-zinc-900 dark:bg-zinc-100' : 'bg-zinc-300 dark:bg-zinc-700'"
              />
              <div class="font-bold text-zinc-900 dark:text-zinc-100">{{ m.title }}</div>
              <div class="text-[11px] text-zinc-400 font-mono mt-0.5">{{ m.time }}</div>
              <div class="text-zinc-500 mt-1 leading-relaxed">{{ m.desc }}</div>
            </div>
          </div>
        </UiCard>
      </div>
    </div>

    <!-- Notice Detail Modal -->
    <UiModal
      v-model="showModal"
      :title="activeNotice ? activeNotice.title : '通知详情'"
    >
      <div v-if="activeNotice" class="space-y-4 text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
        <div class="p-3 rounded-lg bg-zinc-50 dark:bg-zinc-900/60 border border-zinc-200/60 dark:border-zinc-800 flex justify-between">
          <span>来源：教务在线教学运行发布</span>
          <span>日期：{{ activeNotice.date }}</span>
        </div>
        <div class="whitespace-pre-wrap leading-relaxed">
          {{ activeNotice.content || activeNotice.title }}
        </div>
      </div>
    </UiModal>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { semesterOn, calendarSource } from '@/utils/schoolCalendar.js';
import UiCard from '@/components/ui/UiCard.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import UiModal from '@/components/ui/UiModal.vue';
import UiSelect from '@/components/ui/UiSelect.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';

const { currentWeek, semesterTeachingWeeks } = useSession();
// 当前周公告来自登录时的全量缓存；只有用户主动切换周次时才按需拉取并缓存该周
const { notices: cachedNotices, loadNoticesForWeek } = useAcademicData();

const loading = ref(false);
const weekNotices = ref([]);
const selectedWeek = ref(0);
const showModal = ref(false);
const activeNotice = ref(null);

const notices = computed(() =>
  selectedWeek.value === 0 ? cachedNotices.value || [] : weekNotices.value
);

// 周次筛选项：当前周 + 第 1-26 周
const weekItems = computed(() => [
  { label: `当前周 (第 ${currentWeek.value} 周)`, value: 0 },
  ...Array.from({ length: semesterTeachingWeeks.value }, (_, i) => ({ label: `第 ${i + 1} 周`, value: i + 1 }))
]);

async function fetchNotices() {
  if (selectedWeek.value === 0) {
    weekNotices.value = [];
    return;
  }
  loading.value = true;
  try {
    weekNotices.value = await loadNoticesForWeek(selectedWeek.value);
  } finally {
    loading.value = false;
  }
}

function readNotice(n) {
  activeNotice.value = n;
  showModal.value = true;
}

const milestones = computed(() => {
  const semester=semesterOn();
  if (!semester) return [];
  const today=new Date().toLocaleDateString('en-CA');
  return [
    { title:'学期开始上课',time:semester.startDate,desc:`${semester.label} · 教务处公布的学期起点`,isPassed:today>=semester.startDate },
    { title:'教学结束（含考试）',time:semester.teachingEndDate,desc:`共 ${semester.teachingWeeks} 个教学周，个人考试时间以排考为准`,isPassed:today>semester.teachingEndDate }
  ];
});
</script>