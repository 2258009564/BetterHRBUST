<template>
  <div class="space-y-5">
    <!-- ==================== 第一级：反馈列表（网格） ==================== -->
    <template v-if="!selected">
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

      <FeedbackGrid :items="items" :loading="loading" :keyword="keyword" @open="openItem" />

      <div v-if="total > pageSize" class="flex items-center justify-between pt-1">
        <UiButton variant="ghost" size="sm" :disabled="page <= 1 || loading" @click="goPage(page - 1)">
          上一页
        </UiButton>
        <span class="text-[11px] text-zinc-400">第 {{ page }} / {{ totalPages }} 页</span>
        <UiButton variant="ghost" size="sm" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">
          下一页
        </UiButton>
      </div>
    </template>

    <!-- ==================== 第二级：详情 + 回复 ==================== -->
    <template v-else>
      <UiCard body-class="p-3.5">
        <div class="flex flex-wrap items-center gap-2.5">
          <UiButton variant="ghost" size="sm" @click="backToList">
            <template #prefix>
              <Icon name="chevron-left" customClass="w-4 h-4" />
            </template>
            返回列表
          </UiButton>

          <div class="hidden sm:block w-px h-4 bg-zinc-200 dark:bg-zinc-700" />

          <h1 class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 tracking-tight">反馈详情</h1>
          <UiBadge :variant="selected.isRead ? 'default' : 'danger'" size="sm" :dot="!selected.isRead">
            {{ selected.isRead ? '已读' : '未读' }}
          </UiBadge>

          <div class="ml-auto flex items-center gap-2">
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
        </div>
      </UiCard>

      <!-- 宽屏：左详情（更宽）+ 右回复；窄屏（<1280）：回复排到详情下方 -->
      <div class="grid grid-cols-1 xl:grid-cols-[minmax(0,1.65fr)_minmax(0,1fr)] gap-5 items-start">
        <UiCard body-class="p-6" custom-class="min-w-0">
          <p class="text-sm leading-[1.9] text-zinc-800 dark:text-zinc-200 whitespace-pre-wrap break-words">
            {{ selected.content }}
          </p>

          <div class="mt-5 rounded-xl border border-zinc-200/80 dark:border-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800/80">
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
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-1 p-3.5">
              <span class="text-[11px] text-zinc-400">回复次数</span>
              <span class="sm:col-span-2 text-xs text-zinc-700 dark:text-zinc-300">
                {{ selected.replies?.length ? `已回复 ${selected.replies.length} 次` : '尚未回复' }}
              </span>
            </div>
          </div>

          <p class="mt-5 text-[11px] text-zinc-400 leading-relaxed">
            反馈内容为用户自愿提交的文本，系统中不含学号、成绩、课表等任何学生身份数据。
          </p>
        </UiCard>

        <div class="space-y-4 min-w-0">
          <!-- 回复区常驻：识别到邮箱会自动填入收件人，未识别到也可手动填写 -->
          <FeedbackReplyPanel
            :key="selected.id"
            :feedback="selected"
            :templates="replyTemplates"
            :mail-enabled="mailEnabled"
            :sender-prefix="senderPrefix"
            @sent="handleReplySent"
          />

          <FeedbackReplyHistory
            v-if="selected.replies?.length"
            :feedback-id="selected.id"
            :replies="selected.replies"
          />
        </div>
      </div>
    </template>

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
import FeedbackGrid from '@/components/feedback/FeedbackGrid.vue';
import FeedbackReplyPanel from '@/components/feedback/FeedbackReplyPanel.vue';
import FeedbackReplyHistory from '@/components/feedback/FeedbackReplyHistory.vue';
import Icon from '@/components/icons/Icon.vue';
import { adminApi } from '@/services/api.js';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';
import { setUnreadCount } from '@/composables/useFeedbackStats.js';
import { formatDateTime } from '@/utils/format.js';

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
// 非空即进入第二级（详情 + 回复），为空时停留在列表网格
const selected = ref(null);
const showDeleteModal = ref(false);
// 快捷回复文案、发信能力与发件人前缀（均由服务端下发，避免前端写死配置）
const replyTemplates = ref([]);
const mailEnabled = ref(false);
const senderPrefix = ref('');

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
      // 列表不含回复历史（服务端已投影排除），因此合并更新而非整体替换
      if (fresh) selected.value = { ...selected.value, ...fresh };
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
  loadDetail(item.id);
}

function backToList() {
  selected.value = null;
}

/** 详情接口才会返回回复历史，列表点击后按需拉取一次 */
async function loadDetail(id) {
  try {
    const data = await adminApi.getFeedback(id);
    if (data?.item && selected.value?.id === id) selected.value = { ...selected.value, ...data.item };
  } catch (error) {
    console.error('[feedback] 加载反馈详情失败', error);
  }
}

/** 拉取快捷回复文案与发信能力，失败不阻塞反馈列表使用 */
async function loadReplyConfig() {
  try {
    const data = await adminApi.listReplyTemplates();
    replyTemplates.value = data?.templates || [];
    mailEnabled.value = !!data?.mailEnabled;
    senderPrefix.value = data?.fromName || '';
  } catch (error) {
    console.error('[feedback] 加载快捷回复文案失败', error);
  }
}

/** 回复成功后同步详情，使回复历史立即出现 */
function handleReplySent(updated) {
  if (!updated) return;
  selected.value = { ...selected.value, ...updated };
  const target = items.value.find(item => item.id === updated.id);
  if (target) target.isRead = updated.isRead;
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
    // 删除后回到列表网格
    selected.value = null;
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

onMounted(() => {
  load();
  loadReplyConfig();
});
onUnmounted(() => window.clearTimeout(searchTimer));
</script>
