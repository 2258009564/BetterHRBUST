<template>
  <div class="space-y-5 max-w-4xl">
    <UiCard body-class="p-4">
      <div class="flex flex-col gap-3.5 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex items-center gap-3 min-w-0">
          <div class="w-10 h-10 rounded-xl bg-zinc-800 text-white dark:bg-zinc-200 dark:text-zinc-900 flex items-center justify-center shrink-0">
            <Icon name="users" customClass="w-5 h-5" />
          </div>
          <div class="min-w-0">
            <h1 class="text-base font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">管理员管理</h1>
            <p class="text-[11px] text-zinc-500 dark:text-zinc-400 mt-0.5">
              管理员仅能阅读反馈；创建后初始密码只显示一次
            </p>
          </div>
        </div>
        <UiButton variant="primary" @click="openCreateModal">
          <template #prefix>
            <Icon name="plus" customClass="w-4 h-4" />
          </template>
          新建管理员
        </UiButton>
      </div>
    </UiCard>

    <div v-if="loading" class="p-8 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] flex items-center justify-center gap-2.5 text-xs text-zinc-500">
      <Icon name="refresh" customClass="w-4 h-4 animate-spin" />
      正在加载管理员列表…
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="admin in admins"
        :key="admin.id"
        class="p-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-[#1c1e24] flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <div class="flex items-center gap-3 min-w-0">
          <div class="w-10 h-10 rounded-full bg-zinc-200 dark:bg-zinc-700 text-zinc-800 dark:text-zinc-200 font-bold text-sm flex items-center justify-center shrink-0">
            {{ (admin.nickname || admin.username).slice(0, 1).toUpperCase() }}
          </div>
          <div class="min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate">
                {{ admin.nickname || admin.username }}
              </span>
              <UiBadge :variant="admin.role === 'super' ? 'info' : 'default'" size="sm">
                {{ admin.role === 'super' ? '超级管理员' : '管理员' }}
              </UiBadge>
              <UiBadge
                :variant="admin.status === 'active' ? 'success' : 'danger'"
                size="sm"
                :dot="admin.status !== 'active'"
              >
                {{ admin.status === 'active' ? '正常' : '已暂停' }}
              </UiBadge>
              <UiBadge v-if="admin.mustChangePassword" variant="warning" size="sm">待改密</UiBadge>
            </div>
            <div class="text-[11px] text-zinc-400 mt-1 truncate">
              {{ admin.username }} · 创建于 {{ formatDateTime(admin.createdAt) }}
              <span v-if="admin.createdBy"> · 由 {{ admin.createdBy }} 创建</span>
            </div>
          </div>
        </div>

        <div class="flex items-center gap-2 shrink-0">
          <UiButton
            v-if="admin.role !== 'super' && admin.status === 'active'"
            variant="outline"
            size="sm"
            @click="askSuspend(admin)"
          >
            <template #prefix>
              <Icon name="ban" customClass="w-3.5 h-3.5" />
            </template>
            暂停资格
          </UiButton>
          <UiButton
            v-else-if="admin.role !== 'super'"
            variant="secondary"
            size="sm"
            :loading="actionLoading"
            @click="setStatus(admin, 'active')"
          >
            <template #prefix>
              <Icon name="check" customClass="w-3.5 h-3.5" />
            </template>
            恢复资格
          </UiButton>
          <span v-else class="text-[11px] text-zinc-400">不可暂停</span>
        </div>
      </div>
    </div>

    <!-- 新建管理员 -->
    <UiModal v-model="showCreateModal" title="新建管理员" max-width="md">
      <form class="space-y-4" @submit.prevent="submitCreate">
        <div>
          <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">昵称</label>
          <UiInput v-model="form.nickname" placeholder="用于管理端展示，例如：只读审核员" :maxlength="32">
            <template #prefix>
              <Icon name="profile" customClass="w-4 h-4" />
            </template>
          </UiInput>
        </div>

        <div>
          <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">用户名</label>
          <UiInput v-model="form.username" placeholder="3-32 位字母、数字、下划线、点或短横线">
            <template #prefix>
              <Icon name="users" customClass="w-4 h-4" />
            </template>
          </UiInput>
        </div>

        <div>
          <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">
            初始密码 <span class="text-zinc-400 dark:text-zinc-500 font-normal">（留空自动生成）</span>
          </label>
          <UiInput v-model="form.password" placeholder="留空则由系统生成 14 位随机密码">
            <template #prefix>
              <Icon name="key" customClass="w-4 h-4" />
            </template>
          </UiInput>
        </div>

        <div class="p-3 rounded-xl border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-800/30 text-[11px] text-zinc-500 leading-relaxed">
          新管理员首次登录会被要求立即修改密码；该账号仅能阅读意见反馈，无法管理其他管理员。
        </div>

        <div
          v-if="createError"
          class="p-3 rounded-xl border border-rose-500/20 bg-rose-500/10 text-rose-600 dark:text-rose-400 text-xs flex items-start gap-2"
        >
          <Icon name="alert-triangle" customClass="w-4 h-4 shrink-0 mt-0.5" />
          <span class="flex-1 leading-relaxed">{{ createError }}</span>
        </div>
      </form>

      <template #footer>
        <UiButton variant="ghost" size="sm" @click="showCreateModal = false">取消</UiButton>
        <UiButton variant="primary" size="sm" :loading="actionLoading" @click="submitCreate">
          创建管理员
        </UiButton>
      </template>
    </UiModal>

    <!-- 初始密码一次性展示 -->
    <UiModal v-model="showCredentialModal" title="管理员创建成功" max-width="md">
      <p class="text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
        请立即把下面的登录信息交给对方。初始密码只在此处显示一次，数据库中仅保存哈希，关闭后无法再次查看。
      </p>
      <div class="mt-4 rounded-xl border border-zinc-200/80 dark:border-zinc-800 divide-y divide-zinc-100 dark:divide-zinc-800/80">
        <div class="flex items-center justify-between gap-3 p-3.5">
          <span class="text-[11px] text-zinc-400 shrink-0">用户名</span>
          <span class="text-xs font-mono text-zinc-800 dark:text-zinc-200">{{ createdAccount.username }}</span>
        </div>
        <div class="flex items-center justify-between gap-3 p-3.5">
          <span class="text-[11px] text-zinc-400 shrink-0">初始密码</span>
          <div class="flex items-center gap-2 min-w-0">
            <span class="text-xs font-mono font-semibold text-zinc-900 dark:text-zinc-100 break-all">
              {{ createdAccount.initialPassword }}
            </span>
            <button
              type="button"
              class="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 cursor-pointer shrink-0"
              title="复制初始密码"
              @click="copyPassword"
            >
              <Icon name="check-circle" customClass="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
      <template #footer>
        <UiButton variant="primary" size="sm" @click="showCredentialModal = false">我已保存</UiButton>
      </template>
    </UiModal>

    <!-- 暂停确认 -->
    <UiModal v-model="showSuspendModal" title="暂停该管理员的资格？" max-width="sm">
      <p class="text-xs text-zinc-600 dark:text-zinc-400 leading-relaxed">
        暂停后「{{ suspending?.nickname || suspending?.username }}」将无法登录管理端，
        已签发的登录状态也会立即失效。你可以随时恢复其资格。
      </p>
      <template #footer>
        <UiButton variant="ghost" size="sm" @click="showSuspendModal = false">取消</UiButton>
        <UiButton variant="danger" size="sm" :loading="actionLoading" @click="confirmSuspend">确认暂停</UiButton>
      </template>
    </UiModal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiModal from '@/components/ui/UiModal.vue';
import Icon from '@/components/icons/Icon.vue';
import { adminApi } from '@/services/api.js';
import { useToast } from '@/composables/useToast.js';
import { formatDateTime } from '@/utils/format.js';

const { showToast } = useToast();

const admins = ref([]);
const loading = ref(true);
const actionLoading = ref(false);
const showCreateModal = ref(false);
const showCredentialModal = ref(false);
const showSuspendModal = ref(false);
const createError = ref('');
const suspending = ref(null);
const createdAccount = reactive({ username: '', initialPassword: '' });

const form = reactive({ nickname: '', username: '', password: '' });

async function load() {
  loading.value = true;
  try {
    const data = await adminApi.listUsers();
    admins.value = data.items || [];
  } catch (error) {
    showToast({ title: '加载失败', message: error?.message, type: 'danger' });
  } finally {
    loading.value = false;
  }
}

function openCreateModal() {
  form.nickname = '';
  form.username = '';
  form.password = '';
  createError.value = '';
  showCreateModal.value = true;
}

async function submitCreate() {
  if (!form.nickname.trim() || !form.username.trim()) {
    createError.value = '请填写昵称与用户名';
    return;
  }
  actionLoading.value = true;
  createError.value = '';
  try {
    const data = await adminApi.createUser({
      nickname: form.nickname.trim(),
      username: form.username.trim(),
      password: form.password.trim()
    });
    createdAccount.username = data.user.username;
    createdAccount.initialPassword = data.initialPassword;
    showCreateModal.value = false;
    showCredentialModal.value = true;
    await load();
  } catch (error) {
    createError.value = error?.message || '创建失败，请稍后重试';
  } finally {
    actionLoading.value = false;
  }
}

function askSuspend(admin) {
  suspending.value = admin;
  showSuspendModal.value = true;
}

async function confirmSuspend() {
  if (!suspending.value) return;
  actionLoading.value = true;
  try {
    await setStatus(suspending.value, 'suspended');
    showSuspendModal.value = false;
  } finally {
    actionLoading.value = false;
    suspending.value = null;
  }
}

async function setStatus(admin, status) {
  actionLoading.value = true;
  try {
    await adminApi.setUserStatus(admin.id, status);
    await load();
    showToast({
      title: status === 'suspended' ? '已暂停该管理员资格' : '已恢复该管理员资格',
      type: 'success'
    });
  } catch (error) {
    showToast({ title: '操作失败', message: error?.message, type: 'danger' });
  } finally {
    actionLoading.value = false;
  }
}

async function copyPassword() {
  try {
    await navigator.clipboard.writeText(createdAccount.initialPassword);
    showToast({ title: '初始密码已复制', type: 'success' });
  } catch {
    showToast({ title: '复制失败', message: '请手动选择并复制密码', type: 'warning' });
  }
}

onMounted(load);
</script>
