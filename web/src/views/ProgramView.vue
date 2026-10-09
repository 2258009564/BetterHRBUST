<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="showLoginPrompt" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="program" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以核查您的培养方案与毕业学分完成度</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Top Action Bar（数据刷新统一由顶栏按钮完成） -->
      <div class="text-xs text-zinc-500">
        基于教务培养方案 与已修成绩单动态核对
        <span class="ml-2 text-zinc-400">上次同步：{{ lastSyncText }}</span>
      </div>

      <div class="text-[11px] text-zinc-400 dark:text-zinc-500 leading-relaxed">
        {{ earnedCreditsNote }}
      </div>

      <!-- Overall Graduation Progress Card -->
      <UiCard>
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div class="text-xs text-zinc-500 dark:text-zinc-400 font-medium">毕业方案总学分达成进度</div>
            <div class="text-3xl font-extrabold text-zinc-900 dark:text-zinc-100 mt-1">
              {{ creditsProgress.earnedTotal }} <span class="text-base font-normal text-zinc-400">/ {{ creditsProgress.requiredTotal || "待同步" }} 学分</span>
            </div>
            <div class="text-xs text-zinc-500 mt-1">
              学生专业：<span class="font-semibold text-zinc-800 dark:text-zinc-200">{{ userProfile.college }} · {{ userProfile.major }} ({{ userProfile.grade ? String(userProfile.grade).trim().replace(/(?:\s*级)+$/, '') + '级' : '—' }})</span>
            </div>
            <div class="text-[11px] text-zinc-400 mt-1">{{ earnedCreditsNote }}</div>
          </div>

          <div class="flex items-center gap-3">
            <div class="text-right">
              <div class="text-2xl font-black text-zinc-900 dark:text-zinc-100">{{ creditsProgress.completionPercent }}%</div>
              <div class="text-[11px] text-zinc-400">总学分达成率</div>
            </div>
          </div>
        </div>

        <!-- Main Progress Bar -->
        <div class="w-full h-3 rounded-full bg-zinc-100 dark:bg-zinc-800 overflow-hidden mt-5">
          <div
            class="h-full rounded-full bg-zinc-900 dark:bg-zinc-100 transition-all duration-500"
            :style="{ width: `${creditsProgress.completionPercent}%` }"
          />
        </div>
      </UiCard>

      <!-- Loading skeleton -->
      <div v-if="syncing && categories.length === 0" class="p-16 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#111316] text-center text-xs text-zinc-400">
        <Icon name="refresh" customClass="w-6 h-6 animate-spin mx-auto mb-2.5 text-zinc-500" />
        正在拉取全套教学计划并计算课组完成度...
      </div>

      <div v-else-if="categories.length === 0" class="p-16 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#111316] text-center text-xs text-zinc-400">
        暂未获取到培养方案数据，请点击顶栏的刷新按钮手动同步。
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
              :variant="cat.required > 0 && cat.earned >= cat.required ? 'success' : 'default'"
              :dot="cat.required > 0 && cat.earned >= cat.required"
            >
              {{ cat.required > 0 && cat.earned >= cat.required ? '已达标' : (cat.required > 0 ? '修读中' : '未设要求') }}
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
                  cat.required > 0 && cat.earned >= cat.required ? 'bg-emerald-500' : 'bg-zinc-800 dark:bg-zinc-200'
                ]"
                :style="{ width: `${cat.required ? Math.min(100, Math.round((cat.earned / cat.required) * 100)) : (cat.earned > 0 ? 100 : 0)}%` }"
              />
            </div>

            <div class="flex justify-between text-xs text-zinc-500 pt-1">
              <span>要求修读：{{ cat.required }} 学分<span v-if="cat.requiredCourses === 4"> · 10 选 4</span></span>
              <span :class="cat.earned >= cat.required ? 'text-emerald-600 font-semibold' : ''">
                已获通过：{{ cat.earned }} 学分
              </span>
            </div>

            <div v-if="creditsProgress.requiredOnly && isElectiveCategory(cat.property)" class="text-[11px] text-zinc-400 leading-relaxed">
              选修类课程学分不计入"已获得学分"，此处仅展示课组要求
            </div>
          </div>
        </UiCard>
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
import { useSession } from '@/composables/useSession.js';
import { useAcademicData } from '@/composables/useAcademicData.js';
import {
  computeCreditsProgress,
  resolveProperty,
  EARNED_CREDITS_NOTE
} from '@/services/academic/stats.js';

const { isLoggedIn, isSessionExpired, userProfile, openLoginModal } = useSession();
const { scores, plan, syncing, lastSyncText } = useAcademicData();

const showLoginPrompt = computed(() => !isLoggedIn.value && !isSessionExpired.value);
const earnedCreditsNote = EARNED_CREDITS_NOTE;

const creditsProgress = computed(() =>
  computeCreditsProgress(scores.value, plan.value?.groups || [], { planTotalCredits: plan.value?.totalRequiredCredits })
);
const categories = computed(() => creditsProgress.value.categories);

function isElectiveCategory(property) {
  const p = resolveProperty(property);
  return p === 'limited' || p === 'elective';
}


</script>
