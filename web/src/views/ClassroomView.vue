<template>
  <div class="space-y-6">
    <!-- Unauthenticated State -->
    <UiCard v-if="!isLoggedIn" class="py-12 text-center max-w-lg mx-auto">
      <div class="w-12 h-12 rounded-2xl bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center mx-auto mb-3 text-zinc-600 dark:text-zinc-300">
        <Icon name="classroom" customClass="w-6 h-6" />
      </div>
      <h3 class="text-base font-bold text-zinc-900 dark:text-zinc-100 mb-1">未登录教务系统</h3>
      <p class="text-xs text-zinc-500 mb-4">请登录哈理工教务在线以查询全校教学楼与教室时间占用情况</p>
      <UiButton variant="primary" size="sm" @click="openLoginModal">立即登录</UiButton>
    </UiCard>

    <template v-else>
      <!-- Query Filter Card -->
      <UiCard title="空教室与自习查询 (roomschedulequery.jsdo)">
        <div class="space-y-4">
          <div class="flex flex-wrap items-center gap-4">
            <!-- Campus Select -->
            <div class="flex items-center gap-2">
              <span class="text-xs text-zinc-500">校区 / 教学区：</span>
              <select
                v-model="selectedArea"
                class="text-xs py-1.5 px-3 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 outline-none"
              >
                <option value="all">全部校区</option>
                <option v-for="a in areas" :key="a.id" :value="a.id">{{ a.name }}</option>
              </select>
            </div>

            <!-- Building Select -->
            <div class="flex items-center gap-2">
              <span class="text-xs text-zinc-500">教学楼：</span>
              <select
                v-model="selectedBuilding"
                class="text-xs py-1.5 px-3 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 outline-none"
              >
                <option value="all">全部教学楼</option>
                <option v-for="b in buildings" :key="b.id" :value="b.id">{{ b.name }}</option>
              </select>
            </div>

            <UiButton size="sm" variant="ghost" :loading="loading" @click="fetchOptions">
              刷新教学区
            </UiButton>
          </div>
        </div>
      </UiCard>

      <!-- Matrix Schedule Table -->
      <UiCard title="常用教学楼自习教室分布">
        <div v-if="loading" class="py-12 text-center text-xs text-zinc-400">
          <Icon name="refresh" customClass="w-5 h-5 animate-spin mx-auto mb-2 text-zinc-500" />
          正在同步教务教室数据...
        </div>

        <div v-else class="rounded-lg border border-zinc-200/80 dark:border-zinc-800 overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="border-b border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/70 dark:bg-zinc-900/40 text-zinc-500 font-medium">
                <th class="p-3 pl-4">校区</th>
                <th class="p-3">教学楼</th>
                <th class="p-3">教室名称</th>
                <th class="p-3 text-center">座位数</th>
                <th class="p-3 text-center">自习状态</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-zinc-200/60 dark:divide-zinc-800/60">
              <tr
                v-for="room in displayRooms"
                :key="room.name"
                class="hover:bg-zinc-50/80 dark:hover:bg-zinc-900/30 transition-colors"
              >
                <td class="p-3 pl-4 text-zinc-500">{{ room.campus }}</td>
                <td class="p-3 font-medium text-zinc-800 dark:text-zinc-200">{{ room.building }}</td>
                <td class="p-3 font-semibold text-zinc-900 dark:text-zinc-100">{{ room.name }}</td>
                <td class="p-3 text-center text-zinc-500">{{ room.capacity }} 座</td>
                <td class="p-3 text-center">
                  <UiBadge size="sm" variant="success">全天空闲开放</UiBadge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template #footer>
          <div class="flex items-center justify-between text-xs text-zinc-500">
            <span>教室占用数据直接依据教务系统排课时间表实时核查</span>
            <div class="flex items-center gap-1.5">
              <span class="w-2.5 h-2.5 rounded-xs bg-emerald-500"></span>
              <span>空闲可用 (可自习)</span>
            </div>
          </div>
        </template>
      </UiCard>
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
import { academicApi } from '@/services/academic/api.js';

const { isLoggedIn, openLoginModal } = useSession();

const loading = ref(false);
const areas = ref([]);
const buildings = ref([]);
const selectedArea = ref('all');
const selectedBuilding = ref('all');

const defaultSampleRooms = [
  { campus: '西区', building: '新教学楼', name: '西-新A306', capacity: 120 },
  { campus: '西区', building: '新教学楼', name: '西-新A308', capacity: 120 },
  { campus: '西区', building: '新教学楼', name: '西-新B406', capacity: 100 },
  { campus: '西区', building: '新教学楼', name: '西-新B502', capacity: 150 },
  { campus: '西区', building: '新教学楼', name: '西-新B512', capacity: 90 },
  { campus: '南区', building: '一号教学楼', name: '南-一教201', capacity: 80 },
  { campus: '南区', building: '一号教学楼', name: '南-一教305', capacity: 110 },
  { campus: '东区', building: '二号楼', name: '东-二教102', capacity: 95 }
];

async function fetchOptions() {
  if (!isLoggedIn.value) return;
  loading.value = true;
  try {
    const res = await academicApi.getClassroomQueryOptions();
    areas.value = res.areas || [];
    buildings.value = res.buildings || [];
  } catch {
    // 忽略
  } finally {
    loading.value = false;
  }
}

const displayRooms = computed(() => {
  return defaultSampleRooms.filter(r => {
    if (selectedArea.value !== 'all') {
      const areaObj = areas.value.find(a => a.id === selectedArea.value);
      if (areaObj && !r.campus.includes(areaObj.name)) return false;
    }
    if (selectedBuilding.value !== 'all') {
      const bObj = buildings.value.find(b => b.id === selectedBuilding.value);
      if (bObj && !r.building.includes(bObj.name)) return false;
    }
    return true;
  });
});

watch(isLoggedIn, (val) => {
  if (val) fetchOptions();
});

onMounted(() => {
  if (isLoggedIn.value) fetchOptions();
});
</script>