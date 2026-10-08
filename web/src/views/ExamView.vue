<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="showLoginPrompt" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="exam" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以查询您的考试日程安排与考场位置</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Top Action Bar（数据刷新统一由顶栏按钮完成） -->
      <div class="text-xs text-zinc-500">
        共获取到 <span class="font-bold text-zinc-900 dark:text-zinc-100">{{ exams.length }}</span> 门考试安排
        <span class="ml-2 text-zinc-400">上次同步：{{ lastSyncText }}</span>
      </div>

      <!-- Top Countdown Radar Highlight -->
      <UiCard title="临近考试倒计时雷达">
        <div v-if="loading" class="py-8 text-center text-xs text-zinc-400">
          <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2" />
          正在加载考试日程...
        </div>

        <div v-else-if="upcomingExams.length === 0" class="py-8 text-center text-xs text-zinc-400">
          当前暂无近期考试安排，请关注教务在线通知。
        </div>

        <div v-else class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div
            v-for="ex in upcomingExams"
            :key="ex.courseId + ex.time"
            class="p-5 rounded-xl border border-zinc-200/90 dark:border-zinc-800 bg-zinc-50/50 dark:bg-[#15171a] flex flex-col justify-between"
          >
            <div>
              <div class="flex items-center justify-between">
                <UiBadge
                  :variant="ex.countdownDays <= 3 ? 'danger' : 'warning'"
                  dot
                  size="sm"
                >
                  {{ ex.countdownDays <= 0 ? '今日或已考' : `倒计时 ${ex.countdownDays} 天` }}
                </UiBadge>
                <span class="text-xs text-zinc-400 font-mono">{{ ex.courseId }}</span>
              </div>
              <h4 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mt-3">{{ ex.courseName }}</h4>
              <div class="mt-3 space-y-1.5 text-xs text-zinc-600 dark:text-zinc-400">
                <div class="flex items-center gap-1.5">
                  <Icon name="clock" customClass="w-3.5 h-3.5 text-zinc-400" />
                  <span>{{ ex.time }}</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <Icon name="map-pin" customClass="w-3.5 h-3.5 text-zinc-400" />
                  <span class="font-medium text-zinc-800 dark:text-zinc-200">{{ ex.location }}</span>
                </div>
              </div>
            </div>

            <div class="mt-4 pt-3 border-t border-zinc-200/60 dark:border-zinc-800 flex items-center justify-between">
              <span class="text-xs text-zinc-500">{{ ex.property }}</span>
              <UiButton size="sm" variant="ghost" @click="exportExamIcs(ex)">
                加到日历
              </UiButton>
            </div>
          </div>
        </div>
      </UiCard>

      <!-- All Exams Archive Table -->
      <UiCard title="全部考试日程列表 (studentQueryAllExam.do)">
        <template #header-action>
          <div class="flex items-center gap-2">
            <UiTabs
              :items="[
                { label: '全部', value: 'all' },
                { label: '待考试', value: 'upcoming' },
                { label: '已结束', value: 'finished' }
              ]"
              v-model="examFilter"
            />
          </div>
        </template>

        <div class="rounded-lg border border-zinc-200/80 dark:border-zinc-800 overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="border-b border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/70 dark:bg-zinc-900/40 text-zinc-500 font-medium">
                <th class="p-3 pl-4">课程代码</th>
                <th class="p-3">课程名称</th>
                <th class="p-3">考试时间</th>
                <th class="p-3">考场地点</th>
                <th class="p-3">性质</th>
                <th class="p-3 pr-4 text-right">状态</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-zinc-200/60 dark:divide-zinc-800/60">
              <tr
                v-for="e in filteredExams"
                :key="e.courseId + e.time"
                class="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/30 transition-colors"
              >
                <td class="p-3 pl-4 font-mono text-zinc-500">{{ e.courseId }}</td>
                <td class="p-3 font-semibold text-zinc-900 dark:text-zinc-100">{{ e.courseName }}</td>
                <td class="p-3 text-zinc-600 dark:text-zinc-400">{{ e.time }}</td>
                <td class="p-3 font-medium">{{ e.location }}</td>
                <td class="p-3"><UiBadge size="sm" variant="default">{{ e.property }}</UiBadge></td>
                <td class="p-3 pr-4 text-right">
                  <UiBadge size="sm" :variant="e.isUpcoming ? 'warning' : 'outline'">
                    {{ e.isUpcoming ? '待开考' : '已归档' }}
                  </UiBadge>
                </td>
              </tr>
              <tr v-if="filteredExams.length === 0">
                <td colspan="6" class="p-8 text-center text-zinc-400">
                  暂无匹配的考试记录
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </UiCard>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';
import { useToast } from '@/composables/useToast.js';

const { isLoggedIn, isSessionExpired } = useSession();
// 考试数据来自登录时的全量缓存，页面不再自动联网
const { exams: cachedExams, syncing, lastSyncText } = useAcademicData();
const { showToast } = useToast();

const showLoginPrompt = computed(() => !isLoggedIn.value && !isSessionExpired.value);
const examFilter = ref('all');

const loading = computed(() => syncing.value && cachedExams.value.length === 0);

const exams = computed(() => {
  const now = new Date();
  return (cachedExams.value || []).map(e => {
    let countdownDays = null;
    let isUpcoming = true;

    const dateStr = (e.time || '').split(' ')[0];
    if (dateStr) {
      const examDate = new Date(dateStr.replace(/-/g, '/'));
      if (!isNaN(examDate.getTime())) {
        countdownDays = Math.ceil((examDate.getTime() - now.getTime()) / (1000 * 3600 * 24));
        isUpcoming = countdownDays >= 0;
      }
    }

    return {
      ...e,
      countdownDays: countdownDays !== null ? countdownDays : 0,
      isUpcoming
    };
  });
});

const upcomingExams = computed(() => {
  return exams.value.filter(e => e.isUpcoming);
});

const filteredExams = computed(() => {
  if (examFilter.value === 'upcoming') return exams.value.filter(e => e.isUpcoming);
  if (examFilter.value === 'finished') return exams.value.filter(e => !e.isUpcoming);
  return exams.value;
});

function exportExamIcs(ex) {
  const ics = `BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//BetterHRBUST//Exam//CN\nBEGIN:VEVENT\nSUMMARY:考试: ${ex.courseName}\nLOCATION:${ex.location}\nDESCRIPTION:${ex.property} 时间:${ex.time}\nEND:VEVENT\nEND:VCALENDAR`;
  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `考试_${ex.courseName}.ics`;
  a.click();
  URL.revokeObjectURL(url);
  showToast({ title: '已生成日历文件', message: `${ex.courseName} (${ex.location})`, type: 'success' });
}
</script>