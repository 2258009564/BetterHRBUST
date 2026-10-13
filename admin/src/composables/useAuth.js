import { computed, ref } from 'vue';
import { adminApi, setApiToken, setUnauthorizedHandler, ErrorCode } from '@/services/api.js';

const TOKEN_KEY = 'better_hrbust_admin_token';

function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || '';
  } catch {
    return '';
  }
}

function persistToken(value) {
  try {
    if (value) localStorage.setItem(TOKEN_KEY, value);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // 存储不可用时仅内存生效
  }
}

const token = ref(readToken());
setApiToken(token.value);

const currentUser = ref(null);
const isSubmitting = ref(false);
const isVerifying = ref(false);
const authChecked = ref(false);
const authError = ref('');

const isAuthenticated = computed(() => !!token.value && !!currentUser.value);
const mustChangePassword = computed(() => !!currentUser.value?.mustChangePassword);
const isSuperAdmin = computed(() => currentUser.value?.role === 'super');

function applySession(payload) {
  token.value = payload.token || token.value;
  setApiToken(token.value);
  persistToken(token.value);
  currentUser.value = payload.user || currentUser.value;
}

function clearSession() {
  token.value = '';
  setApiToken('');
  persistToken('');
  currentUser.value = null;
  authChecked.value = true;
}

async function verifySession() {
  if (!token.value) {
    authChecked.value = true;
    return false;
  }
  isVerifying.value = true;
  try {
    const data = await adminApi.me();
    currentUser.value = data.user;
    return true;
  } catch (error) {
    clearSession();
    authError.value = error?.message || '登录状态已失效';
    return false;
  } finally {
    isVerifying.value = false;
    authChecked.value = true;
  }
}

async function login(username, password) {
  isSubmitting.value = true;
  authError.value = '';
  try {
    const data = await adminApi.login(username, password);
    applySession(data);
    authChecked.value = true;
    return true;
  } catch (error) {
    authError.value = error?.message || '登录失败，请稍后重试';
    return false;
  } finally {
    isSubmitting.value = false;
  }
}

/** 改密成功后服务端会使旧令牌立即失效，因此这里直接结束会话要求重新登录 */
async function changePassword(oldPassword, newPassword) {
  isSubmitting.value = true;
  try {
    await adminApi.changePassword(oldPassword, newPassword);
    return true;
  } finally {
    isSubmitting.value = false;
  }
}

function logout() {
  clearSession();
}

setUnauthorizedHandler(error => {
  if (error?.code === ErrorCode.UNAUTHORIZED) {
    clearSession();
    authError.value = error.message;
  }
});

export function useAuth() {
  return {
    currentUser,
    isAuthenticated,
    mustChangePassword,
    isSuperAdmin,
    isSubmitting,
    isVerifying,
    authChecked,
    authError,
    verifySession,
    login,
    changePassword,
    logout
  };
}
