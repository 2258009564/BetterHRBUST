/**
 * 意见反馈服务端接口封装。
 *
 * 隐私约定：请求体只包含反馈正文、可选联系方式、可选姓名文本与匿名标记，
 * 不携带学号、教务内部 ID、Cookie 或任何其他身份信息。
 *
 * 基址来自构建期注入的 VITE_API_BASE：
 *  - Web：默认 `/api`，开发由 Vite 代理、生产由 nginx 反代到 Go 服务；
 *  - 桌面端 / 油猴端不提供本功能入口（见 utils/platform.js 判定）。
 */

const API_BASE = (import.meta.env?.VITE_API_BASE || '/api').replace(/\/+$/, '');

/** 反馈正文与附属字段的长度限制，与服务端校验保持一致 */
export const FEEDBACK_LIMITS = {
  minContent: 5,
  maxContent: 2000,
  maxContact: 128,
  maxSignature: 64
};

/** 按 Unicode 码点计数，避免 emoji 被算作两个字符 */
export function textLength(value) {
  return Array.from(String(value || '')).length;
}

/**
 * 提交一条反馈。
 * @param {{ content: string, contact?: string, signature?: string, anonymous?: boolean }} payload
 * @returns {Promise<{ id: string, createdAt: string }>}
 */
export async function submitFeedback(payload) {
  const body = {
    content: String(payload.content || ''),
    contact: String(payload.contact || ''),
    signature: String(payload.signature || ''),
    anonymous: payload.anonymous !== false
  };

  let response;
  try {
    response = await fetch(`${API_BASE}/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  } catch (error) {
    console.error('[feedback] 请求失败', error);
    throw new Error('网络不可用，请检查连接后重试');
  }

  let result = null;
  try {
    result = await response.json();
  } catch {
    result = null;
  }

  if (!result || typeof result.code !== 'number') {
    throw new Error(`提交失败（HTTP ${response.status}）`);
  }
  if (result.code !== 0) {
    throw new Error(result.message || '提交失败，请稍后重试');
  }
  return result.data;
}
