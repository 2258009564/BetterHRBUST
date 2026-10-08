<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="!isLoggedIn" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="program" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以核查您的培养方案与毕业学分完成度</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Top Action Bar -->
      <div class="flex items-center justify-between">
        <div class="text-xs text-zinc-500">
          基于教务培养方案 <code class="font-mono">studentScheduleShowByTerm.do</code> 与已修成绩单动态核对
        </div>
        <UiButton size="sm" variant="outline" :loading="loading" @click="loadProgramData">
          <template #prefix>
            <Icon name="refresh" customClass="w-3.5 h-3.5" />
          </template>
          同步方案与学分
        </UiButton>
      </div>

      <!-- Overall Graduation Progress Card -->
      <UiCard>
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">毕业方案总学分达成进度</div>
            <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1">
              {{ earnedCreditsTotal }} <span class="text-base font-normal text-zinc-400">/ {{ requiredCreditsTotal }} 学分</span>
            </div>
            <div class="text-xs text-zinc-500 mt-1">
              学生专业：<span class="font-semibold text-zinc-800 dark:text-zinc-200">{{ userProfile.college }} · {{ userProfile.major }} ({{ userProfile.grade }}级)</span>
            </div>
          </div>

          <div class="flex items-center gap-3">
            <div class="text-right">
              <div class="text-2xl font-black text-zinc-900 dark:text-zinc-100">{{ completionPercent }}%</div>
              <div class="text-[11px] text-zinc-400">总学分达成率</div>
            </div>
          </div>
        </div>

        <!-- Main Progress Bar -->
        <div class="w-full h-3 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden mt-5">
          <div
            class="h-full rounded-full bg-zinc-900 dark:bg-zinc-100 transition-all duration-500"
            :style="{ width: `${completionPercent}%` }"
          />
        </div>
      </UiCard>

      <!-- Loading skeleton -->
      <div v-if="loading" class="p-16 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#111316] text-center text-xs text-zinc-400">
        <Icon name="refresh" customClass="w-6 h-6 animate-spin mx-auto mb-2.5 text-zinc-500" />
        正在拉取全套教学计划并计算课组完成度...
      </div>

      <!-- Categories Breakdown Grid -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <UiCard
          v-for="cat in categories"
          :key="cat.name"
          :title="cat.name"
        >
          <template #header-action>
            <UiBadge
              size="sm"
              :variant="cat.earned >= cat.required ? 'success' : 'default'"
              :dot="cat.earned >= cat.required"
            >
              {{ cat.earned >= cat.required ? '已达标' : '修读中' }}
            </UiBadge>
          </template>

          <div class="space-y-3">
            <div class="flex justify-between items-baseline">
              <span class="text-xs text-zinc-500">学分进度：</span>
              <span class="text-sm font-bold text-zinc-900 dark:text-zinc-100">
                {{ cat.earned }} / {{ cat.required }}
              </span>
            </div>

            <div class="w-full h-2 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden">
              <div
                :class="[
                  'h-full rounded-full transition-all duration-300',
                  cat.earned >= cat.required ? 'bg-emerald-500' : 'bg-zinc-800 dark:bg-zinc-200'
                ]"
                :style="{ width: `${cat.required ? Math.min(100, Math.round((cat.earned / cat.required) * 100)) : 100}%` }"
              />
            </div>

            <div class="flex justify-between text-xs text-zinc-500 pt-1">
              <span>要求修读：{{ cat.required }} 学分</span>
              <span :class="cat.earned >= cat.required ? 'text-emerald-600 font-semibold' : ''">
                已获通过：{{ cat.earned }} 学分
              </span>
            </div>
          </div>
        </UiCard>
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
import { useSession } from '@/composables/useSession.js';
import { useToast } from '@/composables/useToast.js';
import { academicApi } from '@/services/academic/api.js';

const { isLoggedIn, userProfile, openLoginModal } = useSession();
const { showToast } = useToast();

const loading = ref(false);
const categories = ref([]);

const earnedCreditsTotal = computed(() => {
  const sum = categories.value.reduce((acc, c) => acc + c.earned, 0);
  return parseFloat(sum.toFixed(1));
});

const requiredCreditsTotal = computed(() => {
  const sum = categories.value.reduce((acc, c) => acc + c.required, 0);
  return parseFloat(sum.toFixed(1)) || 160;
});

const completionPercent = computed(() => {
  if (!requiredCreditsTotal.value) return 0;
  return Math.min(100, Math.round((earnedCreditsTotal.value / requiredCreditsTotal.value) * 100));
});

async function loadProgramData() {
  if (!isLoggedIn.value) return;
  loading.value = true;
  try {
    const [planRes, scoresRes] = await Promise.all([
      academicApi.getCurriculumPlan().catch(() => ({ groups: [] })),
      academicApi.getScores().catch(() => ({ scores: [] }))
    ]);

    const groups = planRes.groups || [];
    const scores = scoresRes.scores || [];

    // 精准统计：以已及格成绩为基准，确保总已获得学分与概览页100%一致
    const passedScores = scores.filter(s => s.passed);
    const matchedCourseIds = new Set();

    if (groups.length > 0) {
      const computedCats = groups.map(g => {
        let earned = 0;
        passedScores.forEach(s => {
          if (!matchedCourseIds.has(s.courseId)) {
            // 匹配课程代码/名称，或课组名相符
            const inGroup = g.courses?.some(c => c.code === s.courseId || c.name === s.courseName);
            const nameMatch = s.courseGroup && (
              g.name.includes(s.courseGroup) ||
              s.courseGroup.includes(g.name) ||
              g.name.slice(0, 3) === s.courseGroup.slice(0, 3)
            );
            if (inGroup || nameMatch) {
              earned += (s.credit || 0);
              matchedCourseIds.add(s.courseId);
            }
          }
        });
        return {
          name: g.name,
          property: g.property,
          required: g.requiredCredits,
          earned: parseFloat(earned.toFixed(1))
        };
      });

      // 汇总未归属到指定培养方案课组的素质选修、跨专业及实践拓展学分
      let leftoverCredits = 0;
      passedScores.forEach(s => {
        if (!matchedCourseIds.has(s.courseId)) {
          leftoverCredits += (s.credit || 0);
        }
      });

      if (leftoverCredits > 0) {
        computedCats.push({
          name: '通识选修与实践拓展课程',
          property: '任选',
          required: 0,
          earned: parseFloat(leftoverCredits.toFixed(1))
        });
      }

      categories.value = computedCats;
    } else {
      // 降级使用成绩单中的全部课组
      const groupMap = new Map();
      passedScores.forEach(s => {
        const grp = s.courseGroup || '其它';
        groupMap.set(grp, (groupMap.get(grp) || 0) + (s.credit || 0));
      });
      const defaultCats = [];
      for (const [grpName, cr] of groupMap.entries()) {
        defaultCats.push({
          name: grpName,
          required: parseFloat((cr * 1.2).toFixed(1)),
          earned: parseFloat(cr.toFixed(1))
        });
      }
      categories.value = defaultCats;
    }
  } catch (err) {
    showToast({ title: '培养方案加载异常', message: err.message, type: 'danger' });
  } finally {
    loading.value = false;
  }
}

watch(isLoggedIn, (val) => {
  if (val) loadProgramData();
});

onMounted(() => {
  if (isLoggedIn.value) loadProgramData();
});
</script>