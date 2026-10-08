import { ref, reactive } from 'vue';
import { academicApi } from '@/services/academic/api.js';

const SESSION_FLAG_KEY = 'better_hrbust_has_session';
const SESSION_CACHE_KEY = 'better_hrbust_cached_profile';

const hasSavedSession = localStorage.getItem(SESSION_FLAG_KEY) === 'true';
const savedProfile = (() => {
  try {
    return JSON.parse(localStorage.getItem(SESSION_CACHE_KEY) || 'null');
  } catch {
    return null;
  }
})();

const isLoggedIn = ref(hasSavedSession);
const isSessionExpired = ref(false);
const authChecked = ref(false);
const isCheckingAuth = ref(false);
const isLoggingIn = ref(false);
const showLoginModal = ref(false);
const loginError = ref('');

const studentId = ref(savedProfile?.internalId || ''); // 教务内部学生 ID
const studentNumber = ref(localStorage.getItem('saved_student_number') || savedProfile?.studentNumber || '');

const activeTab = ref('dashboard');
const previousTab = ref('dashboard');

function navigateTo(tab) {
  if (activeTab.value !== tab) {
    previousTab.value = activeTab.value;
    activeTab.value = tab;
  }
}

const currentWeek = ref(6);
const currentSemester = reactive({
  name: '2025-2026学年 秋季学期',
  yearId: '46',
  termId: '2',
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
  localStorage.removeItem(SESSION_CACHE_KEY);
  localStorage.removeItem(SESSION_FLAG_KEY);
}

/**
 * 校验当前会话并同步身份
 */
async function checkAuth() {
  if (isCheckingAuth.value) return isLoggedIn.value;
  isCheckingAuth.value = true;
  try {
    const ctx = await academicApi.getStudentContext();
    if (ctx.studentId) {
      isLoggedIn.value = true;
      isSessionExpired.value = false;
      studentId.value = ctx.studentId;
      userProfile.internalId = ctx.studentId;
      if (ctx.year) currentSemester.yearId = ctx.year;
      if (ctx.term) currentSemester.termId = ctx.term;

      localStorage.setItem(SESSION_FLAG_KEY, 'true');

      // 顺带拉取个人基本信息与周次
      try {
        const info = await academicApi.getPersonalInfo();
        Object.assign(userProfile, info);
        userProfile.internalId = ctx.studentId;
        if (info.studentNumber) studentNumber.value = info.studentNumber;
        localStorage.setItem(SESSION_CACHE_KEY, JSON.stringify(userProfile));
      } catch {
        // 忽略局部非关键错误
      }

      try {
        const cal = await academicApi.getCalendarInfo();
        if (cal.currentWeek) currentWeek.value = cal.currentWeek;
        if (cal.semesterName) currentSemester.name = cal.semesterName;
      } catch {
        // 忽略
      }

      return true;
    } else {
      // 未检测到有效 studentId，判定为会话失效
      if (studentNumber.value || hasSavedSession) {
        isSessionExpired.value = true;
      }
      isLoggedIn.value = false;
      localStorage.removeItem(SESSION_FLAG_KEY);
    }
  } catch {
    // 异常（如重定向登录页/断网），若先前有学号或会话标记则判定为会话过期
    if (studentNumber.value || hasSavedSession) {
      isSessionExpired.value = true;
    }
    isLoggedIn.value = false;
    localStorage.removeItem(SESSION_FLAG_KEY);
  } finally {
    isCheckingAuth.value = false;
    authChecked.value = true;
  }
  return false;
}

/**
 * 登录
 */
async function login({ username, password, captcha, remember = true }) {
  isLoggingIn.value = true;
  loginError.value = '';
  try {
    const res = await academicApi.login(username, password, captcha);
    if (!res.success) {
      loginError.value = res.message || '登录失败';
      return { success: false, message: loginError.value };
    }

    if (remember) {
      localStorage.setItem('saved_student_number', username);
    } else {
      localStorage.removeItem('saved_student_number');
    }
    studentNumber.value = username;

    // 同步上下文
    const ctx = await academicApi.getStudentContext();
    studentId.value = ctx.studentId;
    userProfile.internalId = ctx.studentId;
    if (ctx.year) currentSemester.yearId = ctx.year;
    if (ctx.term) currentSemester.termId = ctx.term;

    try {
      const info = await academicApi.getPersonalInfo();
      Object.assign(userProfile, info);
      userProfile.internalId = ctx.studentId;
      if (!userProfile.studentNumber) userProfile.studentNumber = username;
    } catch {
      userProfile.studentNumber = username;
      userProfile.realName = username;
      userProfile.status = '在籍';
    }

    try {
      const cal = await academicApi.getCalendarInfo();
      if (cal.currentWeek) currentWeek.value = cal.currentWeek;
      if (cal.semesterName) currentSemester.name = cal.semesterName;
    } catch {
      // 忽略
    }

    isLoggedIn.value = true;
    isSessionExpired.value = false;
    authChecked.value = true;
    showLoginModal.value = false;
    localStorage.setItem(SESSION_FLAG_KEY, 'true');
    localStorage.setItem(SESSION_CACHE_KEY, JSON.stringify(userProfile));
    return { success: true, message: '登录成功' };
  } catch (err) {
    loginError.value = err.message || '登录异常';
    return { success: false, message: loginError.value };
  } finally {
    isLoggingIn.value = false;
  }
}

/**
 * 登出
 */
async function logout() {
  try {
    await academicApi.logout();
  } catch {
    // 忽略
  }
  isLoggedIn.value = false;
  isSessionExpired.value = false;
  studentId.value = '';
  resetProfile();
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
    authChecked,
    isCheckingAuth,
    isLoggingIn,
    showLoginModal,
    loginError,
    studentId,
    studentNumber,
    currentWeek,
    currentSemester,
    userProfile,
    checkAuth,
    login,
    logout,
    setWeek,
    openLoginModal,
    closeLoginModal
  };
}