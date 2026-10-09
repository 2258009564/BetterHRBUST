<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="!isLoggedIn" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="course" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以查询自己本学期修读的课程</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Course Query & Filter -->
      <UiCard bodyClass="p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="w-full sm:w-80">
            <UiInput
              v-model="searchKey"
              placeholder="检索课程名称、课程编号或任课教师..."
              size="sm"
              clearable
            >
              <template #prefix>
                <Icon name="search" customClass="w-3.5 h-3.5" />
              </template>
            </UiInput>
          </div>

          <div class="flex items-center gap-2">
            <UiTabs
              :items="[
                { label: '全部已修/在修', value: 'all' },
                { label: '本学期在修', value: 'current' },
                { label: '必修课', value: '必修' },
                { label: '选修课', value: '选' }
              ]"
              v-model="categoryFilter"
            />
          </div>
        </div>
      </UiCard>

      <div v-if="syncing && courses.length === 0" class="py-16 text-center text-xs text-zinc-400">
        <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2 text-zinc-500" />
        正在拉取课程名录...
      </div>

      <div v-else-if="filteredCourses.length === 0" class="py-12 text-center text-xs text-zinc-400">
        未找到匹配的课程
      </div>

      <!-- Course Catalog Grid -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <UiCard
          v-for="c in filteredCourses"
          :key="c.id + c.name"
          hoverable
        >
          <div class="space-y-3">
            <div class="flex items-start justify-between">
              <span class="text-xs font-mono text-zinc-400">{{ c.id }}</span>
              <UiBadge size="sm" :variant="c.property === '必修' ? 'default' : 'outline'">{{ c.property }}</UiBadge>
            </div>

            <div>
              <h4 class="text-base font-bold text-zinc-900 dark:text-zinc-100">{{ c.name }}</h4>
              <div class="text-xs text-zinc-500 mt-1">
                {{ c.group || '课程' }} · {{ c.credits }} 学分
                <span v-if="c.hours">({{ c.hours }} 学时)</span>
              </div>
            </div>

            <div class="pt-3 border-t border-zinc-100 dark:border-zinc-800 flex items-center justify-between text-xs">
              <span class="text-zinc-500">
                教师：<span class="font-medium text-zinc-900 dark:text-zinc-100">{{ c.teacher || '任课教师' }}</span>
              </span>
              <span v-if="c.isCurrent" class="text-emerald-600 font-semibold text-[11px]">本学期</span>
            </div>
          </div>
        </UiCard>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';

const { isLoggedIn, openLoginModal } = useSession();
// 全部课程名录由登录时的全量缓存拼装，页面不再自动联网
const { currentCourses, scores } = useAcademicData();

const searchKey = ref('');
const categoryFilter = ref('all');

const courses = computed(() => {
  const list = [];
  const seen = new Set();

  // 1. 本学期课程
  (currentCourses.value || []).forEach(c => {
    if (!c.courseId) return;
    seen.add(c.courseId);
    list.push({
      id: c.courseId,
      name: c.courseName,
      property: c.property || '必修',
      group: '本学期排课',
      credits: c.credit || 0,
      hours: 0,
      teacher: c.teacher || '',
      isCurrent: true
    });
  });

  // 2. 成绩单中修读过的课程
  (scores.value || []).forEach(s => {
    if (s.courseId && !seen.has(s.courseId)) {
      seen.add(s.courseId);
      list.push({
        id: s.courseId,
        name: s.courseName,
        property: s.property || '必修',
        group: s.courseGroup || '',
        credits: s.credit || 0,
        hours: s.hours || 0,
        teacher: '',
        isCurrent: false
      });
    }
  });

  return list;
});

const filteredCourses = computed(() => {
  return courses.value.filter(c => {
    if (categoryFilter.value === 'current' && !c.isCurrent) return false;
    if (categoryFilter.value === '必修' && !c.property.includes('必修')) return false;
    if (categoryFilter.value === '选' && !c.property.includes('选')) return false;

    if (searchKey.value) {
      const q = searchKey.value.toLowerCase();
      if (!c.name.toLowerCase().includes(q) && !c.id.toLowerCase().includes(q) && !c.teacher.toLowerCase().includes(q)) {
        return false;
      }
    }
    return true;
  });
});
</script>