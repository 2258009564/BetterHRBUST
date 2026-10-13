<template>
  <div
    class="min-h-screen flex items-center justify-center p-4 bg-[#f6f7f9] dark:bg-[#14161a] relative overflow-hidden"
  >
    <div class="pointer-events-none absolute -top-40 -left-32 w-96 h-96 rounded-full bg-zinc-900/5 dark:bg-zinc-100/5 blur-3xl" />
    <div class="pointer-events-none absolute -bottom-40 -right-24 w-96 h-96 rounded-full bg-zinc-900/5 dark:bg-zinc-100/5 blur-3xl" />

    <div class="w-full max-w-sm relative">
      <div
        class="p-6 rounded-2xl bg-white dark:bg-[#1c1e24] border border-zinc-200/80 dark:border-zinc-800 shadow-xl"
      >
        <div class="mb-5 text-center">
          <div
            class="inline-flex items-center justify-center w-20 h-20 rounded-full overflow-hidden mb-3.5 shadow-lg ring-1 ring-zinc-200/80 dark:ring-zinc-800 bg-zinc-100 dark:bg-zinc-800"
          >
            <img :src="logoUrl" alt="BetterHRBUST" class="w-full h-full object-cover rounded-full" />
          </div>
          <h2 class="text-lg font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">BetterHRBUST 管理端</h2>
          <p class="text-xs text-zinc-500 mt-1">意见反馈阅读 · 账号由超级管理员分配</p>
        </div>

        <div
          v-if="errorMessage"
          class="mb-4 p-3 rounded-xl border border-rose-500/20 bg-rose-500/10 text-rose-600 dark:text-rose-400 text-xs flex items-center gap-2 animate-shake"
        >
          <Icon name="alert-triangle" customClass="w-4 h-4 shrink-0" />
          <span class="flex-1 leading-relaxed">{{ errorMessage }}</span>
        </div>

        <form class="space-y-4" @submit.prevent="handleSubmit">
          <div>
            <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">用户名</label>
            <UiInput v-model="form.username" placeholder="请输入管理员用户名" autocomplete="username">
              <template #prefix>
                <Icon name="profile" customClass="w-4 h-4" />
              </template>
            </UiInput>
          </div>

          <div>
            <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">密码</label>
            <UiInput
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
            >
              <template #prefix>
                <Icon name="lock" customClass="w-4 h-4" />
              </template>
              <template #suffix>
                <button
                  type="button"
                  class="p-1 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 cursor-pointer"
                  @click="showPassword = !showPassword"
                >
                  <Icon :name="showPassword ? 'eye-off' : 'eye'" customClass="w-3.5 h-3.5" />
                </button>
              </template>
            </UiInput>
          </div>

          <div class="pt-1">
            <UiButton
              type="submit"
              variant="primary"
              custom-class="w-full"
              :loading="isSubmitting"
              :disabled="!canSubmit"
            >
              {{ isSubmitting ? '正在验证身份…' : '登录管理端' }}
            </UiButton>
          </div>
        </form>

        <div class="mt-5 pt-4 border-t border-zinc-100 dark:border-zinc-800/80 text-[11px] text-zinc-400 text-center leading-relaxed">
          管理端只承载意见反馈文本，系统中不存在任何学生身份数据。
        </div>
      </div>

      <p class="text-[11px] text-zinc-400 dark:text-zinc-500 text-center mt-4">
        初始账号由服务端命令行创建：<span class="font-mono">go run ./cmd/seed-admin</span>
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import Icon from '@/components/icons/Icon.vue';
import logoUrl from '@/assets/BetterHRBUST.png';
import { useAuth } from '@/composables/useAuth.js';

const { login, isSubmitting, authError } = useAuth();

const showPassword = ref(false);
const localError = ref('');

const form = reactive({
  username: '',
  password: ''
});

const canSubmit = computed(() => form.username.trim().length > 0 && form.password.length > 0);
const errorMessage = computed(() => localError.value || authError.value);

async function handleSubmit() {
  if (!canSubmit.value) return;
  localError.value = '';
  const ok = await login(form.username.trim(), form.password);
  if (!ok) form.password = '';
}
</script>
