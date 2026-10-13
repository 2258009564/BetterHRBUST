/**
 * 管理端接口封装。
 *
 * 令牌保存在内存 + localStorage，请求时以 Bearer 头携带；
 * 统一处理业务码（code !== 0 抛出 ApiError），并在 401 时通知调用方退出登录。
 */

const API_BASE = (import.meta.env?.VITE_API_BASE || '/api').replace(/\/+$/, '');

export const ErrorCode = {
  OK: 0,
  RATE_LIMITED: 1002,
  MAIL_NOT_CONFIGURED: 1009,
  MAIL_SEND_FAILED: 1010,
  NO_REPLY_TARGET: 1011,
  INVALID_REPLY_TARGET: 1012,
  UNAUTHORIZED: 2001,
  FORBIDDEN: 2002,
  PASSWORD_CHANGE_REQUIRED: 2003,
  ACCOUNT_SUSPENDED: 2004,
  INVALID_CREDENTIAL: 2005,
  NOT_FOUND: 3001
};

export class ApiError extends Error {
  constructor(message, code = -1, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

let token = '';
let unauthorizedHandler = null;

export function setApiToken(value) {
  token = value || '';
}

export function getApiToken() {
  return token;
}

/** 注册 401 统一处理（由 useAuth 注入，避免循环依赖） */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch (error) {
    console.error('[admin-api] 请求失败', error);
    throw new ApiError('网络不可用，请检查连接后重试');
  }

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }

  if (!payload || typeof payload.code !== 'number') {
    throw new ApiError(`请求失败（HTTP ${response.status}）`, -1, response.status);
  }

  if (payload.code !== ErrorCode.OK) {
    const error = new ApiError(payload.message || '请求失败', payload.code, response.status);
    if (error.code === ErrorCode.UNAUTHORIZED && typeof unauthorizedHandler === 'function') {
      unauthorizedHandler(error);
    }
    throw error;
  }

  return payload.data;
}

export const adminApi = {
  health: () => request('/healthz', { auth: false }),
  login: (username, password) =>
    request('/admin/auth/login', { method: 'POST', body: { username, password }, auth: false }),
  me: () => request('/admin/auth/me'),
  changePassword: (oldPassword, newPassword) =>
    request('/admin/auth/change-password', { method: 'POST', body: { oldPassword, newPassword } }),

  listFeedback: params => request(`/admin/feedback?${buildQuery(params)}`),
  getFeedback: id => request(`/admin/feedback/${encodeURIComponent(id)}`),
  setFeedbackRead: (id, isRead) =>
    request(`/admin/feedback/${encodeURIComponent(id)}/read`, { method: 'PATCH', body: { isRead } }),
  deleteFeedback: id => request(`/admin/feedback/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  // 快捷回复文案与发信能力状态
  listReplyTemplates: () => request('/admin/reply-templates'),
  replyFeedback: (id, payload) =>
    request(`/admin/feedback/${encodeURIComponent(id)}/reply`, { method: 'POST', body: payload }),

  listUsers: () => request('/admin/users'),
  createUser: payload => request('/admin/users', { method: 'POST', body: payload }),
  setUserStatus: (id, status) =>
    request(`/admin/users/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } })
};

function buildQuery(params = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  });
  return search.toString();
}
