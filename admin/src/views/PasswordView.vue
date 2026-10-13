<template>
  <div :class="forced ? 'min-h-screen flex items-center justify-center p-4 bg-[#f6f7f9] dark:bg-[#14161a]' : ''">
    <div :class="forced ? 'w-full max-w-md' : 'space-y-6 max-w-xl'">
      <div
        v-if="forced"
        class="mb-4 p-3.5 rounded-xl border border-amber-500/25 bg-amber-500/10 text-amber-800 dark:text-amber-300 text-xs flex items-start gap-2.5"
      >
        <Icon name="alert-triangle" customClass="w-4 h-4 shrink-0 mt-0.5" />
        <div class="min-w-0 leading-relaxed">
          <div class="font-semibold">请先修改初始密码</div>
          <div class="text-[11px] mt-0.5">
            为保证账号安全，首次登录必须设置你自己的密码后才能进入管理端。
          </div>
        </div>
      </div>

      <div
        :class="[
          forced
            ? 'p-6 rounded-2xl bg-white dark:bg-[#1c1e24] border border-zinc-200/80 dark:border-zinc-800 shadow-xl'
            : ''
        ]"
      >
        <UiCard :title="forced ? '设置新密码' : '账号安全'">
          <div v-if="!forced" class="mb-5 flex items-center justify-between gap-3 p-3.5 rounded-xl border border-zinc-200/80 dark:border-zinc-800">
            <div class="flex items-center gap-2.5 min-w-0">
              <div class="w-9 h-9 rounded-full bg-zinc-200 dark:bg-zinc-700 text-zinc-800 dark:text-zinc-200 font-bold text-sm flex items-center justify-center shrink-0">
                {{ avatarText }}
              </div>
              <div class="min-w-0 leading-tight">
                <div class="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate">
                  {{ currentUser?.nickname || currentUser?.username }}
                </div>
                <div class="text-[11px] text-zinc-400 truncate mt-0.5">
                  {{ currentUser?.username }} · {{ isSuperAdmin ? '超级管理员' : '管理员' }}
                </div>
              </div>
            </div>
            <UiBadge :variant="isSuperAdmin ? 'info' : 'default'">
              {{ isSuperAdmin ? '全部权限' : '反馈阅读' }}
            </UiBadge>
          </div>

          <form class="space-y-4" @submit.prevent="handleSubmit">
            <div>
              <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">
                {{ forced ? '初始密码' : '当前密码' }}
              </label>
              <UiInput v-model="form.oldPassword" type="password" placeholder="请输入当前使用的密码">
                <template #prefix>
                  <Icon name="lock" customClass="w-4 h-4" />
                </template>
              </UiInput>
            </div>

            <div>
              <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">新密码</label>
              <UiInput v-model="form.newPassword" type="password" placeholder="至少 8 位，建议包含字母与数字">
                <template #prefix>
                  <Icon name="key" customClass="w-4 h-4" />
                </template>
              </UiInput>
            </div>

            <div>
              <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">确认新密码</label>
              <UiInput v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码">
                <template #prefix>
                  <Icon name="check-circle" customClass="w-4 h-4" />
                </template>
              </UiInput>
            </div>

            <div
              v-if="errorMessage"
              class="p-3 rounded-xl border border-rose-500/20 bg-rose-500/10 text-rose-600 dark:text-rose-400 text-xs flex items-start gap-2"
            >
              <Icon name="alert-triangle" customClass="w-4 h-4 shrink-0 mt-0.5" />
              <span class="flex-1 leading-relaxed">{{ errorMessage }}</span>
            </div>

            <div class="flex items-center justify-between gap-3 pt-1">
              <p class="text-[11px] text-zinc-400 dark:text-zinc-500 leading-relaxed">
                修改成功后当前登录会失效，需要使用新密码重新登录。
              </p>
              <UiButton type="submit" variant="primary" :loading="isSubmitting" :disabled="!canSubmit">
                保存新密码
              </UiButton>
            </div>
          </form>
        </UiCard>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import UiCard from '@/components/ui/UiCard.vue';
import UiBadge from '@/components/ui/UiBadge.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import Icon from '@/components/icons/Icon.vue';
import { useAuth } from '@/composables/useAuth.js';
import { useToast } from '@/composables/useToast.js';

const props = defineProps({
  // 首次登录强制改密：全屏渲染且不提供任何跳过入口
  forced: {
    type: Boolean,
    default: false
  }
});

const { currentUser, isSuperAdmin, isSubmitting, changePassword, logout } = useAuth();
const { showToast } = useToast();

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const localError = ref('');

const avatarText = computed(() => {
  const name = currentUser.value?.nickname || currentUser.value?.username || '?';
  return String(name).slice(0, 1).toUpperCase();
});

const canSubmit = computed(() => {
  return (
    form.oldPassword.length > 0 &&
    form.newPassword.length >= 8 &&
    form.newPassword === form.confirmPassword
  );
});

const errorMessage = computed(() => {
  if (localError.value) return localError.value;
  if (form.confirmPassword.length > 0 && form.newPassword !== form.confirmPassword) {
    return '两次输入的新密码不一致';
  }
  return '';
});

async function handleSubmit() {
  localError.value = '';
  if (form.newPassword.length < 8) {
    localError.value = '新密码至少 8 位';
    return;
  }
  if (form.newPassword !== form.confirmPassword) {
    localError.value = '两次输入的新密码不一致';
    return;
  }

  try {
    await changePassword(form.oldPassword, form.newPassword);
    showToast({
      title: '密码已更新',
      message: props.forced ? '请使用新密码重新登录' : '当前登录已失效，请重新登录',
      type: 'success'
    });
    form.oldPassword = '';
    form.newPassword = '';
    form.confirmPassword = '';
    logout();
  } catch (error) {
    localError.value = error?.message || '修改失败，请稍后重试';
  }
}
</script>
