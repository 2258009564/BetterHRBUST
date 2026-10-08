<template>
  <div :class="[embedded ? '' : 'p-6 rounded-2xl bg-white dark:bg-[#1c1e24] border border-zinc-200/80 dark:border-zinc-800 shadow-xl']">
    <div class="mb-5 text-center">
      <div class="inline-flex items-center justify-center w-11 h-11 rounded-xl bg-zinc-800 text-white dark:bg-zinc-700 dark:text-zinc-100 mb-3 font-bold text-base shadow-sm">
        JW
      </div>
      <h2 class="text-lg font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">哈理工教务在线登录</h2>
      <p class="text-xs text-zinc-500 mt-1">连接 http://jwzx.hrbust.edu.cn/academic · 真实账号数据直达</p>
    </div>

    <!-- Current Session Notice if already logged in -->
    <div
      v-if="isLoggedIn"
      class="mb-4 p-3 rounded-xl border border-zinc-200 dark:border-zinc-750 bg-zinc-50/70 dark:bg-zinc-800/40 text-xs flex items-center justify-between"
    >
      <div class="flex items-center gap-2 min-w-0">
        <span class="w-2 h-2 rounded-full bg-emerald-500 shrink-0" />
        <span class="truncate font-medium text-zinc-800 dark:text-zinc-200">
          当前用户：{{ userProfile.realName || studentNumber }} ({{ studentNumber }})
        </span>
      </div>
      <span class="text-[11px] text-zinc-400 shrink-0">重新认证或换号</span>
    </div>

    <!-- Error Alert -->
    <div
      v-if="errorMessage"
      class="mb-4 p-3 rounded-xl border border-rose-500/20 bg-rose-500/10 text-rose-600 dark:text-rose-400 text-xs flex items-center gap-2 animate-shake"
    >
      <Icon name="warning" customClass="w-4 h-4 shrink-0" />
      <span class="flex-1">{{ errorMessage }}</span>
    </div>

    <!-- Form -->
    <form @submit.prevent="handleLoginSubmit" class="space-y-4">
      <!-- Username -->
      <div>
        <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">学号</label>
        <UiInput
          v-model="form.username"
          placeholder="请输入教务学号 (如 2023000001)"
          autocomplete="username"
          clearable
          :disabled="isLoggingIn"
        >
          <template #prefix>
            <Icon name="profile" customClass="w-4 h-4" />
          </template>
        </UiInput>
      </div>

      <!-- Password -->
      <div>
        <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">密码</label>
        <div class="relative">
          <UiInput
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入教务在线密码"
            autocomplete="current-password"
            :disabled="isLoggingIn"
          >
            <template #prefix>
              <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
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
      </div>

      <!-- Captcha -->
      <div>
        <label class="block text-xs font-medium text-zinc-700 dark:text-zinc-300 mb-1.5">验证码</label>
        <div class="flex items-center gap-3">
          <div class="flex-1">
            <UiInput
              v-model="form.captcha"
              placeholder="4 位验证码"
              maxlength="4"
              :disabled="isLoggingIn"
            >
              <template #prefix>
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
              </template>
            </UiInput>
          </div>

          <!-- Captcha image container -->
          <div
            class="relative h-[38px] w-28 bg-zinc-100 dark:bg-zinc-800 rounded-lg border border-zinc-200 dark:border-zinc-700 overflow-hidden flex items-center justify-center cursor-pointer select-none group"
            title="点击更换验证码"
            @click="refreshCaptcha"
          >
            <img
              v-if="captchaImgUrl"
              :src="captchaImgUrl"
              alt="验证码"
              class="w-full h-full object-cover transition-opacity"
              :class="{ 'opacity-40': captchaLoading }"
              @load="captchaLoading = false"
              @error="onCaptchaLoadError"
            />
            <div
              v-if="captchaLoading"
              class="absolute inset-0 flex items-center justify-center bg-zinc-900/10 dark:bg-zinc-100/10"
            >
              <Icon name="refresh" customClass="w-4 h-4 animate-spin text-zinc-600 dark:text-zinc-300" />
            </div>
            <div
              v-if="captchaError"
              class="text-[11px] text-rose-500 font-medium px-1 text-center"
            >
              加载失败点击重试
            </div>
          </div>
        </div>
      </div>

      <!-- Remember & Network info -->
      <div class="flex items-center justify-between text-xs pt-1">
        <label class="flex items-center gap-2 text-zinc-600 dark:text-zinc-400 cursor-pointer select-none">
          <input
            type="checkbox"
            v-model="rememberMe"
            class="rounded border-zinc-300 dark:border-zinc-700 text-zinc-900 focus:ring-0 cursor-pointer"
          />
          <span>记住学号</span>
        </label>
        <span class="text-[11px] text-zinc-400">校园网 / VPN 环境直连</span>
      </div>

      <!-- Submit button -->
      <div class="pt-2">
        <UiButton
          type="submit"
          variant="primary"
          block
          :loading="isLoggingIn"
          :disabled="!canSubmit"
        >
          {{ isLoggingIn ? '正在验证身份...' : (isLoggedIn ? '重新认证 / 切换登录' : '登录教务在线') }}
        </UiButton>
      </div>
    </form>

    <!-- Footer Security Notice -->
    <div class="mt-5 pt-4 border-t border-zinc-100 dark:border-zinc-800/80 text-[11px] text-zinc-400 text-center leading-relaxed">
      登录信息仅通过本地代理发送至哈尔滨理工大学教务系统，会话 Cookie 仅存储于当前浏览器中，无模拟假数据，无云端中转。
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import Icon from '@/components/icons/Icon.vue';
import { useSession } from '@/composables/useSession.js';
import { academicApi } from '@/services/academic/api.js';

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['success']);

const { isLoggingIn, loginError, login, studentNumber, isLoggedIn, userProfile } = useSession();

const showPassword = ref(false);
const rememberMe = ref(true);
const captchaImgUrl = ref('');
const captchaLoading = ref(false);
const captchaError = ref(false);
const errorMessage = ref('');

const form = reactive({
  username: studentNumber.value || '',
  password: '',
  captcha: ''
});

const canSubmit = computed(() => {
  return form.username.trim() && form.password.trim() && form.captcha.trim().length === 4;
});

function refreshCaptcha() {
  captchaLoading.value = true;
  captchaError.value = false;
  form.captcha = '';
  captchaImgUrl.value = academicApi.getCaptchaUrl();
}

function onCaptchaLoadError() {
  captchaLoading.value = false;
  captchaError.value = true;
}

async function handleLoginSubmit() {
  if (!canSubmit.value || isLoggingIn.value) return;
  errorMessage.value = '';

  const res = await login({
    username: form.username,
    password: form.password,
    captcha: form.captcha,
    remember: rememberMe.value
  });

  if (res.success) {
    emit('success');
  } else {
    errorMessage.value = res.message || '登录失败，请检查账号密码及验证码';
    refreshCaptcha();
  }
}

onMounted(() => {
  refreshCaptcha();
});
</script>

<style scoped>
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-4px); }
  40%, 80% { transform: translateX(4px); }
}
.animate-shake {
  animation: shake 0.3s ease-in-out;
}
</style>
