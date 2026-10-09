import { ref, reactive, computed } from 'vue';
import { academicApi } from '@/services/academic/api.js';
import { clearRegisteredDataCaches } from '@/composables/dataCacheBridge.js';
import { storageGetItem, storageSetItem, storageRemoveItem } from '@/services/storage.js';

const SESSION_FLAG_KEY = 'better_hrbust_has_session';
const SESSION_CACHE_KEY = 'better_hrbust_cached_profile';
const LAST_LOGIN_KEY = 'better_hrbust_last_login_at';
const LAST_PROMPT_KEY = 'better_hrbust_session_prompt_at';
const SESSION_EXPIRED_KEY = 'better_hrbust_session_expired';

/**
 * 会话失效提示间隔：一周
 * 规则：
 * - 上次登录在一周内且用户未手动刷新 → 不主动提示，仅在登录页展示"登录状态已失效"；
 * - 用户手动刷新 → 无条件要求重新登录；
 * - 距上次提示满一周 → 提示一次；用户忽略则再过一周再提示，以此类推。
 */
const PROMPT_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000;

function readNumber(key) {
  const n = Number(storageGetItem(key));
  return Number.isFinite(n) && n > 0 ? n : 0;
}

const hasSavedSession = storageGetItem(SESSION_FLAG_KEY) === 'true';
const savedProfile = (() => {
  try {
    return JSON.parse(storageGetItem(SESSION_CACHE_KEY) || 'null');
  } catch {
    return null;
  }
})();

const isLoggedIn = ref(hasSavedSession);
// 会话失效状态持久化：冷启动后仍能展示"登录状态已失效"与按周节流后的横幅
const isSessionExpired = ref(storageGetItem(SESSION_EXPIRED_KEY) === 'true');
const authChecked = ref(false);
const isCheckingAuth = ref(false);
const isLoggingIn = ref(false);
const isPreparingAccount = ref(false);
const showLoginModal = ref(false);
const loginError = ref('');

// 会话失效提示节流状态
const lastLoginAt = ref(readNumber(LAST_LOGIN_KEY));
const lastPromptAt = ref(readNumber(LAST_PROMPT_KEY));
/**
 * 当前这一轮失效是否已经决定要提示。
 * 只在本次运行期间有效：用户看过一次提示后，重新打开页面不再重复弹出，
 * 而是继续按一周节流（lastPromptAt）计算下一次提示时机。
 */
const promptActive = ref(false);
/** 用户已忽略本轮失效提示（收起横幅） */
const sessionPromptDismissed = ref(false);
/** 本次运行期间是否已对当前这一轮失效做过节流判定（重新打开应用后归零） */
let promptDecided = false;

const studentId = ref(savedProfile?.internalId || ''); // 教务内部学生 ID
const studentNumber = ref(savedProfile?.studentNumber || storageGetItem('saved_student_number') || '');
let authGeneration = 0;
let accountPreparation = null;
let loginPreparationFailed = false;

const activeTab = ref('dashboard');
const previousTab = ref('dashboard');

function navigateTo(tab) {
  if (activeTab.value !== tab) {
    previousTab.value = activeTab.value;
    activeTab.value = tab;
  }
}

const currentWeek = ref(6);
// yearId / termId 初始留空：只有从教务上下文拿到真实值后才允许请求课表，
// 避免上下文获取失败时用硬编码学期覆盖本地正确缓存
const currentSemester = reactive({
  name: '2025-2026学年 秋季学期',
  yearId: '',
  termId: '',
  startDate: ''
});

const userProfile = reactive({
  studentNumber: savedProfile?.studentNumber || '',
  internalId: savedProfile?.internalId || '',
  realName: savedProfile?.realName || '',
  college: savedProfile?.college || '',
  major: savedProfile?.major || '',
  direction: savedProfile?.direction || '',
  grade: savedProfile?.grade || '',
  className: savedProfile?.className || '',
  studentType: savedProfile?.studentType || '',
  status: savedProfile?.status || (hasSavedSession ? '在籍' : '未登录'),
  idCard: savedProfile?.idCard || '',
  email: savedProfile?.email || '',
  phone: savedProfile?.phone || '',
  address: savedProfile?.address || '',
  photoUrl: savedProfile?.photoUrl || '',
  changes: savedProfile?.changes || []
});

function resetProfile() {
  userProfile.studentNumber = '';
  userProfile.internalId = '';
  userProfile.realName = '';
  userProfile.college = '';
  userProfile.major = '';
  userProfile.direction = '';
  userProfile.grade = '';
  userProfile.className = '';
  userProfile.studentType = '';
  userProfile.status = '未登录';
  userProfile.idCard = '';
  userProfile.email = '';
  userProfile.phone = '';
  userProfile.address = '';
  userProfile.photoUrl = '';
  userProfile.changes = [];
  storageRemoveItem(SESSION_CACHE_KEY);
  storageRemoveItem(SESSION_FLAG_KEY);
}

/** 会话已失效但本地仍有缓存数据 → 进入离线只读模式 */
const offlineMode = computed(() => isSessionExpired.value && !isLoggedIn.value);

/** 是否展示"登录状态已失效"提示（按周节流后的最终判定，且用户未忽略） */
const shouldShowSessionBanner = computed(
  () => isSessionExpired.value && promptActive.value && !sessionPromptDismissed.value
);

/** 用户点击忽略：收起本轮失效横幅 */
function dismissSessionPrompt() {
  sessionPromptDismissed.value = true;
}

/** 登录页需要展示"登录状态已失效"（不满足提示条件时仅在此处体现） */
const showLoginPageExpiredHint = computed(() => isSessionExpired.value);

/**
 * 标记会话失效，并按"一周节流"规则决定是否提示
 * @param {Object} [options]
 * @param {boolean} [options.manual] 是否由用户手动刷新触发（手动刷新无条件要求重新登录）
 */
function markSessionExpired(options = {}) {
  const manual = options.manual === true;
  isSessionExpired.value = true;
  isLoggedIn.value = false;
  storageSetItem(SESSION_EXPIRED_KEY, 'true');
  storageRemoveItem(SESSION_FLAG_KEY);

  // 本次运行期间本轮失效已判定过 → 不重复评估，避免提示时间被后续请求不断后推；
  // 重新打开应用后 promptDecided 归零，故"再过一周再提示"仍能生效
  if (promptDecided && !manual) return;
  promptDecided = true;

  const now = Date.now();
  const base = Math.max(lastLoginAt.value || 0, lastPromptAt.value || 0);

  if (manual || base === 0 || now - base >= PROMPT_INTERVAL_MS) {
    // 触发一次提示：记录提示时间，作为下一次提示的起点
    promptActive.value = true;
    lastPromptAt.value = now;
    storageSetItem(LAST_PROMPT_KEY, String(now));
    // 新的一次提示：重新展示横幅（覆盖上一次的"忽略"）
    sessionPromptDismissed.value = false;
  } else {
    promptActive.value = false;
  }
}

/** 会话恢复有效（登录成功或校验通过）时清理失效状态 */
function clearSessionExpired() {
  isSessionExpired.value = false;
  promptActive.value = false;
  sessionPromptDismissed.value = false;
  promptDecided = false;
  lastPromptAt.value = 0;
  storageRemoveItem(LAST_PROMPT_KEY);
  storageRemoveItem(SESSION_EXPIRED_KEY);
}

function recordLoginTime() {
  lastLoginAt.value = Date.now();
  storageSetItem(LAST_LOGIN_KEY, String(lastLoginAt.value));
}

/**
 * 校验当前会话并同步身份
 * @param {Object} [options]
 * @param {boolean} [options.light] 轻量模式：仅校验上下文，不拉取档案与校历（用于日常打开时的静默校验）
 */
async function checkAuth(options = {}) {
  const light = options.light === true;
  if (isCheckingAuth.value) return isLoggedIn.value;
  isCheckingAuth.value = true;
  const generation = authGeneration;
  try {
    const ctx = await academicApi.getStudentContext();
    if (generation !== authGeneration) return false;
    if (studentId.value && ctx.studentId && studentId.value !== ctx.studentId) {
      clearRegisteredDataCaches();
      resetProfile();
      studentId.value = '';
      markSessionExpired({ manual: false });
      return false;
    }
    if (ctx.studentId) {
      isLoggedIn.value = true;
      clearSessionExpired();
      studentId.value = ctx.studentId;
      userProfile.internalId = ctx.studentId;
      if (ctx.year) currentSemester.yearId = ctx.year;
      if (ctx.term) currentSemester.termId = ctx.term;

      storageSetItem(SESSION_FLAG_KEY, 'true');

      if (!light) {
        // 顺带拉取个人基本信息与周次
        try {
          const info = await academicApi.getPersonalInfo();
          if (generation !== authGeneration) return false;
          Object.assign(userProfile, info);
          userProfile.internalId = ctx.studentId;
          if (info.studentNumber) studentNumber.value = info.studentNumber;
          storageSetItem(SESSION_CACHE_KEY, JSON.stringify(userProfile));
        } catch {
          // 忽略局部非关键错误
        }

        try {
          const cal = await academicApi.getCalendarInfo();
          if (generation !== authGeneration) return false;
          if (cal.currentWeek) currentWeek.value = cal.currentWeek;
          if (cal.semesterName) currentSemester.name = cal.semesterName;
        } catch {
          // 忽略
        }
      }

      return true;
    }

    // 未检测到有效 studentId，判定为会话失效
    if (studentNumber.value || hasSavedSession) {
      markSessionExpired({ manual: false });
    } else {
      isLoggedIn.value = false;
    }
  } catch {
    if (generation !== authGeneration) return false;
    // 异常（如重定向登录页/断网），若先前有学号或会话标记则判定为会话过期
    if (studentNumber.value || hasSavedSession) {
      markSessionExpired({ manual: false });
    } else {
      isLoggedIn.value = false;
    }
  } finally {
    if (generation === authGeneration) {
      isCheckingAuth.value = false;
      authChecked.value = true;
    }
  }
  return false;
}

/**
 * 登录
 */
/** 切换账号前先结束旧服务端会话，再重新获取属于新会话的验证码。 */
async function prepareLoginAccount(username) {
  if (accountPreparation) return accountPreparation;
  const target = String(username || '').trim();
  const current = userProfile.studentNumber || studentNumber.value;
  if (!target) return false;
  if (!loginPreparationFailed && (!current || target === current || (!isLoggedIn.value && !isSessionExpired.value))) return false;
  isPreparingAccount.value = true;
  accountPreparation = (async () => { await logout(); return true; })()
    .finally(() => { isPreparingAccount.value = false; accountPreparation = null; });
  return accountPreparation;
}

async function login({ username, password, captcha, remember = true }) {
  if (isLoggingIn.value || isPreparingAccount.value) return { success: false, message: '登录或账号切换正在处理中，请稍候' };
  username = String(username).trim();
  try {
    if (await prepareLoginAccount(username)) return { success: false, message: '账号已切换，请输入新验证码后登录' };
  } catch (error) { return { success: false, message: error.message || '无法退出旧账号，请重试' }; }
  isLoggingIn.value = true;
  isLoggedIn.value = false;
  storageRemoveItem(SESSION_FLAG_KEY);
  if (userProfile.studentNumber) {
    isSessionExpired.value = true;
    storageSetItem(SESSION_EXPIRED_KEY, 'true');
  }
  isCheckingAuth.value = false;
  const generation = ++authGeneration;
  loginError.value = '';
  try {
    const res = await academicApi.login(username, password, captcha);
    if (!res.success) {
      loginError.value = res.message || '登录失败';
      return { success: false, message: loginError.value };
    }
    const ctx = await academicApi.getStudentContext();
    const profile = await academicApi.getPersonalInfo();
    if (generation !== authGeneration) throw new Error('登录请求已失效，请重新登录');
    if (!ctx.studentId || String(profile.studentNumber || '').trim() !== username) {
      await logout();
      throw new Error('教务返回的账号与输入学号不一致，请刷新验证码重新登录');
    }
    clearRegisteredDataCaches();
    resetProfile();
    Object.assign(userProfile, profile, { studentNumber: username, internalId: ctx.studentId });
    studentNumber.value = username;
    studentId.value = ctx.studentId;
    currentSemester.yearId = ctx.year || '';
    currentSemester.termId = ctx.term || '';
    if (remember) storageSetItem('saved_student_number', username);
    else storageRemoveItem('saved_student_number');
    isLoggedIn.value = true;
    clearSessionExpired();
    recordLoginTime();
    authChecked.value = true;
    showLoginModal.value = false;
    storageSetItem(SESSION_FLAG_KEY, 'true');
    storageSetItem(SESSION_CACHE_KEY, JSON.stringify(userProfile));
    return { success: true, message: '登录成功' };
  } catch (err) {
    loginError.value = err.message || '登录异常';
    return { success: false, message: loginError.value };
  } finally { isLoggingIn.value = false; }
}

/**
 * 登出（同时清理节流时间戳与本地数据缓存）
 */
async function logout() {
  loginPreparationFailed = true;
  ++authGeneration;
  isCheckingAuth.value = false;
  isLoggedIn.value = false;
  isSessionExpired.value = false;
  promptActive.value = false;
  sessionPromptDismissed.value = false;
  promptDecided = false;
  lastLoginAt.value = 0;
  lastPromptAt.value = 0;
  studentId.value = '';
  storageRemoveItem(LAST_LOGIN_KEY);
  storageRemoveItem(LAST_PROMPT_KEY);
  storageRemoveItem(SESSION_EXPIRED_KEY);
  resetProfile();

  // 通过桥接模块清理集中式数据缓存，避免与 useAcademicData 形成循环依赖
  clearRegisteredDataCaches();
  currentSemester.yearId = '';
  currentSemester.termId = '';
  currentSemester.name = '';
  studentNumber.value = '';
  await academicApi.logout();
  loginPreparationFailed = false;
}

function setWeek(w) {
  currentWeek.value = Math.max(1, Math.min(26, w));
}

function openLoginModal() {
  navigateTo('login');
}

function closeLoginModal() {
  if (activeTab.value === 'login') {
    activeTab.value = previousTab.value || 'dashboard';
  }
}

export function useSession() {
  return {
    activeTab,
    previousTab,
    navigateTo,
    isLoggedIn,
    isSessionExpired,
    offlineMode,
    shouldShowSessionBanner,
    sessionPromptDismissed,
    dismissSessionPrompt,
    showLoginPageExpiredHint,
    authChecked,
    isCheckingAuth,
    isLoggingIn,
    isPreparingAccount,
    showLoginModal,
    loginError,
    studentId,
    studentNumber,
    currentWeek,
    currentSemester,
    userProfile,
    lastLoginAt,
    lastPromptAt,
    checkAuth,
    login,
    prepareLoginAccount,
    logout,
    markSessionExpired,
    setWeek,
    openLoginModal,
    closeLoginModal
  };
}
