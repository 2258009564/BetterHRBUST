<template>
  <div class="space-y-5">
    <!-- 顶部工具栏 -->
    <UiCard body-class="p-4">
      <div class="flex flex-col gap-3.5 lg:flex-row lg:items-center lg:justify-between">
        <div class="flex items-center gap-3 min-w-0">
          <div class="w-10 h-10 rounded-xl bg-zinc-800 text-white dark:bg-zinc-200 dark:text-zinc-900 flex items-center justify-center shrink-0">
            <Icon name="inbox" customClass="w-5 h-5" />
          </div>
          <div class="min-w-0">
            <h1 class="text-base font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">意见反馈</h1>
            <p class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5">
              共 {{ total }} 条 · 未读 {{ unread }} 条
            </p>
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2.5">
          <div class="w-full sm:w-64">
            <UiInput v-model="keyword" placeholder="搜索反馈正文、姓名或联系方式" clearable @enter="reload">
              <template #prefix>
                <Icon name="search" customClass="w-4 h-4" />
              </template>
            </UiInput>
          </div>
          <UiTabs :items="stateTabs" :modelValue="state" @update:modelValue="handleStateChange" />
          <UiButton variant="outline" size="md" :loading="loading" @click="reload">
            <template #prefix>
              <Icon name="refresh" customClass="w-4 h-4" :class="loading ? 'animate-spin' : ''" />
            </template>
            刷新
          </UiButton>
        </div>
      </div>
    </UiCard>

    <div class="grid grid-cols-1 lg:grid-cols-[minmax(0,380px)_minmax(0,1fr)] gap-5 items-start">
      <!-- 列表 -->
      <div :class="['space-y-3', mobileDetail ? 'hidden lg:block' : '']">
        <div
          v-if="loading && items.length === 0"
          class="p-8 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] flex items-center justify-center gap-2.5 text-xs text-zinc-500"
        >
          <Icon name="refresh" customClass="w-4 h-4 animate-spin" />
          正在加载反馈…
        </div>

        <div
          v-else-if="items.length === 0"
          class="p-8 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] text-center"
        >
          <Icon name="message" customClass="w-6 h-6 mx-auto text-zinc-300 dark:text-zinc-600" />
          <p class="text-xs text-zinc-500 mt-2.5">
            {{ keyword ? '没有匹配的反馈' : '暂时还没有反馈' }}
          </p>
        </div>

        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          :class="[
            'w-full text-left p-4 rounded-xl border transition-all duration-150 cursor-pointer',
            selected?.id === item.id
              ? 'border-zinc-400 dark:border-zinc-600 bg-white dark:bg-[#1c1e24] shadow-md'
              : 'border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] hover:border-zinc-300 dark:hover:border-zinc-700 hover:shadow-sm'
          ]"
          @click="openItem(item)"
        >
          <div class="flex items-center gap-2">
            <span
              class="w-2 h-2 rounded-full shrink-0"
              :class="item.isRead ? 'bg-zinc-300 dark:bg-zinc-600' : 'bg-rose-500'"
            />
            <span class="text-[11px] font-medium" :class="item.isRead ? 'text-zinc-400' : 'text-rose-500'">
              {{ item.isRead ? '已读' : '未读' }}
            </span>
            <span class="ml-auto text-[11px] text-zinc-400">{{ formatRelative(item.createdAt) }}</span>
          </div>

          <p class="mt-2 text-xs leading-relaxed text-zinc-700 dark:text-zinc-300">
            {{ excerpt(item.content) }}
          </p>

          <div class="mt-2.5 flex items-center gap-2 text-[11px] text-zinc-400">
            <UiBadge :variant="item.anonymous ? 'default' : 'info'" size="sm">
              {{ item.anonymous ? '匿名' : (item.signature || '实名') }}
            </UiBadge>
            <span v-if="item.contact" class="inline-flex items-center gap-1 truncate">
              <Icon name="mail" customClass="w-3 h-3 shrink-0" />
              <span class="truncate">{{ item.contact }}</span>
            </span>
          </div>
        </button>

        <!-- 分页 -->
        <div v-if="total > pageSize" class="flex items-center justify-between pt-1">
          <UiButton variant="ghost" size="sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">
            上一页
          </UiButton>
          <span class="text-[11px] text-zinc-400">第 {{ page }} / {{ totalPages }} 页</span>
          <UiButton variant="ghost" size="sm" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">
            下一页
          </UiButton>
        </div>
      </div>

      <!-- 详情 -->
      <div :class="[mobileDetail ? '' : 'hidden lg:block']">
        <UiCard v-if="selected" body-class="p-0">
          <template #header>
            <div class="flex items-center gap-2.5 min-w-0">
              <button
                type="button"
                class="lg:hidden p-1.5 -ml-1 rounded-lg text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer"
                @click="mobileDetail = false"
              >
                <Icon name="chevron-left" customClass="w-4 h-4" />
              </button>
              <h3 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate">反馈详情</h3>
              <UiBadge :variant="selected.isRead ? 'default' : 'danger'" size="sm" :dot="!selected.isRead">
                {{ selected.isRead ? '已读' : '未读' }}
              </UiBadge>
            </div>
            <div class="flex items-center gap-2">
              <UiButton
                v-if="!selected.isRead"
                variant="secondary"
                size="sm"
                :loading="actionLoading"
                @click="toggleRead(true)"
              >
                <template #prefix>
                  <Icon name="check" customClass="w-3.5 h-3.5" />
                </template>
                标记已读
              </UiButton>
              <UiButton
                v-else
                variant="ghost"
                size="sm"
                :loading="actionLoading"
                @click="toggleRead(false)"
              >
                标记未读
              </UiButton>
              <UiButton
                v-if="isSuperAdmin"
                variant="danger"
                size="sm"
                @click="showDeleteModal = true"
              >
                <template #prefix>
                  <Icon name="trash" customClass="w-3.5 h-3.5" />
                </template>
                删除
              </UiButton>
            </div>
          </template>

          <div class="p-6 space-y-5">
            <p class="text-sm leading-[1.9] text-zinc-800 dark:text-zinc-200 whitespace-pre-wrap break-words">
              {{ selected.content }}
            </p>

            <div class="rounded-xl border border-zinc-200/80 dark:border-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800/80">
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-1 p-3.5">
                <span class="text-[11px] text-zinc-400">提交时间</span>
                <span class="sm:col-span-2 text-xs text-zinc-700 dark:text-zinc-300">{{ formatDateTime(selected.createdAt) }}</span>
              </div>
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-1 p-3.5">
                <span class="text-[11px] text-zinc-400">发布方式</span>
                <span class="sm:col-span-2 text-xs text-zinc-700 dark:text-zinc-300">
                  {{ selected.anonymous ? '匿名提交' : `实名提交（${selected.signature || '未填写姓名'}）` }}
                </span>
              </div>
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-1 p-3.5">
                <span class="text-[11px] text-zinc-400">联系方式</span>
                <span class="sm:col-span-2 text-xs text-zinc-700 dark:text-zinc-300 break-all">
                  {{ selected.contact || '未填写' }}
                </span>
              </div>
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-1 p-3.5">
                <span class="text-[11px] text-zinc-400">阅读时间</span>
                <span class="sm:col-span-2 text-xs text-zinc-700 dark:text-zinc-300">{{ formatDateTime(selected.readAt) }}</span>
              </div>
            </div>

            <p class="text-[11px] text-zinc-400 leading-relaxed">
              反馈内容为用户自愿提交的文本，系统中不含学号、成绩、课表等任何学生身份数据。
            </p>
          </div>
        </UiCard>

        <div
          v-else
          class="hidden lg:flex flex-col items-center justify-center h-64 rounded-xl border border-dashed border-zinc-200 dark:border-zinc-800 text-center"
        >
          <Icon name="inbox" customClass="w-6 h-6 text-zinc-300 dark:text-zinc-600" />
          <p class="text-xs text-zinc-500 mt-2.5">从左侧选择一条反馈查看详情</p>
        </div>
      </div>
    </div>

    <!-- 删除确认 -->
    <UiModal v-model="showDeleteModal" title="删除这条反馈？" max-width="sm">
      <p class="text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
        删除后该反馈将从数据库中永久移除，无法恢复。请确认这是敏感信息或无效内容后再执行。
      </p>
      <template #footer>
        <UiButton variant="ghost" size="sm" @click="showDeleteModal = false">取消</UiButton>
        <UiButton variant="danger" size="sm" :loading="actionLoading" @click="confirmDelete">确认删除</UiButton>
      </template>
    </UiModal>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiModal from '@/components/ui/UiModal.vue';
import Icon from '@/components/icons/Icon.vue';
import { adminApi } from '@/services/api.js';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { setUnreadCount } from '@/composables/useFeedbackStats.js';
import { excerpt, formatDateTime, formatRelative } from '@/utils/format.js';

const { isSuperAdmin } = useAuth();
const { showToast } = useToast();

const items = ref([]);
const total = ref(0);
const unread = ref(0);
const page = ref(1);
const pageSize = 20;
const keyword = ref('');
const state = ref('all');
const loading = ref(false);
const actionLoading = ref(false);
const selected = ref(null);
const mobileDetail = ref(false);
const showDeleteModal = ref(false);

let searchTimer;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const stateTabs = computed(() => [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread', badge: unread.value > 0 ? unread.value : undefined },
  { label: '已读', value: 'read' }
]);

async function load() {
  loading.value = true;
  try {
    const data = await adminApi.listFeedback({
      keyword: keyword.value.trim(),
      state: state.value,
      page: page.value,
      pageSize
    });
    items.value = data.items || [];
    total.value = data.total || 0;
    unread.value = data.unread || 0;
    setUnreadCount(unread.value);
    if (selected.value) {
      const fresh = items.value.find(item => item.id === selected.value.id);
      if (fresh) selected.value = fresh;
    }
  } catch (error) {
    showToast({ title: '加载失败', message: error?.message || '请稍后重试', type: 'danger' });
  } finally {
    loading.value = false;
  }
}

function reload() {
  page.value = 1;
  load();
}

function goPage(next) {
  if (next < 1 || next > totalPages.value) return;
  page.value = next;
  load();
}

function handleStateChange(value) {
  state.value = value;
  reload();
}

function openItem(item) {
  selected.value = item;
  mobileDetail.value = true;
}

async function toggleRead(isRead) {
  if (!selected.value) return;
  actionLoading.value = true;
  try {
    const data = await adminApi.setFeedbackRead(selected.value.id, isRead);
    selected.value = data.item;
    const target = items.value.find(item => item.id === data.item.id);
    if (target) target.isRead = data.item.isRead;
    await load();
    showToast({ title: isRead ? '已标记为已读' : '已标记为未读', type: 'success' });
  } catch (error) {
    showToast({ title: '操作失败', message: error?.message, type: 'danger' });
  } finally {
    actionLoading.value = false;
  }
}

async function confirmDelete() {
  if (!selected.value) return;
  actionLoading.value = true;
  try {
    await adminApi.deleteFeedback(selected.value.id);
    showDeleteModal.value = false;
    selected.value = null;
    mobileDetail.value = false;
    await load();
    showToast({ title: '反馈已删除', type: 'success' });
  } catch (error) {
    showToast({ title: '删除失败', message: error?.message, type: 'danger' });
  } finally {
    actionLoading.value = false;
  }
}

watch(keyword, () => {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(reload, 350);
});

onMounted(load);
onUnmounted(() => window.clearTimeout(searchTimer));
</script>
