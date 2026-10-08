/**
 * 教务在线 HTTP 客户端
 * 负责处理 GBK / UTF-8 编码自适应解码、会话状态识别与错误捕获
 */

const BASE_PREFIX = '/academic/';

// 登录页标记特征
const LOGIN_PAGE_MARKERS = ['j_acegi_security_check', 'getCaptcha.do', 'j_captcha'];

// 登录失败特征
const LOGIN_FAILURE_MARKERS = [
  'badCredentials',
  '密码错误',
  '用户名不存在',
  '验证码错误',
  'Bad credentials',
  '用户不存在'
];

/**
 * 判断 HTML 是否为登录页
 */
export function isLoginPage(html) {
  if (!html) return false;
  return LOGIN_PAGE_MARKERS.some(marker => html.includes(marker));
}

/**
 * 从登录失败页中提取错误原因
 */
export function parseLoginFailureReason(html) {
  const text = String(html || '');
  if (text.includes('验证码')) return '验证码错误或已过期，请刷新重试';
  if (text.includes('密码') || text.includes('badCredentials') || text.includes('Bad credentials')) {
    return '学号或密码错误';
  }
  if (text.includes('用户名') || text.includes('用户不存在')) return '该学号不存在';
  if (text.includes('锁定')) return '账号已被系统锁定，请稍后再试';
  return '登录失败，请检查学号与密码';
}

/**
 * 智能响应解码
 * @param {Response} response
 * @param {string} [preferredEncoding]
 * @returns {Promise<string>}
 */
async function decodeResponse(response, preferredEncoding) {
  const buffer = await response.arrayBuffer();
  const contentType = (response.headers.get('content-type') || '').toLowerCase();

  let charset = preferredEncoding || 'utf-8';

  if (!preferredEncoding) {
    if (contentType.includes('charset=')) {
      const match = contentType.match(/charset=([a-z0-9_-]+)/i);
      if (match && match[1]) {
        charset = match[1].toLowerCase();
      }
    }
  }

  // 标准化编码名称
  if (charset.includes('gbk') || charset.includes('gb2312') || charset.includes('gb18030')) {
    charset = 'gbk';
  } else {
    charset = 'utf-8';
  }

  try {
    const decoder = new TextDecoder(charset);
    return decoder.decode(buffer);
  } catch {
    // 降级使用 UTF-8
    const fallback = new TextDecoder('utf-8');
    return fallback.decode(buffer);
  }
}

/**
 * 格式化相对路径为完整请求路径
 */
function resolveUrl(path) {
  if (!path) return BASE_PREFIX;
  // 关键防线：教务系统仅支持 HTTP (不支持 HTTPS)。若直接请求或跟随绝对域名，浏览器可能自动升级 HTTPS 导致 ERR_CONNECTION_CLOSED
  // 因此无论传入何种格式（包括含域名的绝对 URL），一律剥离域名，强制通过本地 /academic/ 代理
  let clean = String(path).replace(/^https?:\/\/jwzx\.hrbust\.edu\.cn(?::\d+)?\/?/i, '');
  clean = clean.replace(/^\/+/g, '');
  if (clean.startsWith('academic/')) {
    return '/' + clean;
  }
  return BASE_PREFIX + clean;
}

/**
 * 执行 HTTP 请求
 */
export async function request(path, options = {}) {
  const url = resolveUrl(path);
  const {
    method = 'GET',
    headers = {},
    body = null,
    encoding, // 'gbk' 或 'utf-8'，未指定时按 header 判断
    checkAuth = true
  } = options;

  const fetchOptions = {
    method,
    headers: {
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
      'X-Requested-With': 'XMLHttpRequest',
      ...headers
    },
    credentials: 'include' // 必传，携带与接收 Cookie
  };

  if (body) {
    if (typeof body === 'object' && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
      const params = new URLSearchParams();
      Object.entries(body).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          params.append(key, String(value));
        }
      });
      fetchOptions.body = params;
      fetchOptions.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
    } else {
      fetchOptions.body = body;
    }
  }

  let response;
  try {
    response = await fetch(url, fetchOptions);
  } catch (err) {
    throw new Error(`网络连接失败：${err.message || '无法连接到教务系统，请确认是否处于校园网或VPN环境'}`);
  }

  const html = await decodeResponse(response, encoding);

  // 会话过期判定
  if (checkAuth && isLoginPage(html)) {
    const error = new Error('教务会话已失效或未登录');
    error.isSessionExpired = true;
    error.status = 401;
    throw error;
  }

  return {
    ok: response.ok,
    status: response.status,
    url: response.url,
    headers: response.headers,
    html
  };
}

/**
 * 获取验证码图片完整 URL
 */
export function getCaptchaUrl() {
  return `${BASE_PREFIX}getCaptcha.do?_t=${Date.now()}`;
}

/**
 * 校验验证码（教务系统自身的前置校验接口）
 */
export async function checkCaptcha(captchaCode) {
  try {
    const res = await request(`checkCaptcha.do?captchaCode=${encodeURIComponent(captchaCode)}`, {
      method: 'POST',
      checkAuth: false
    });
    const result = res.html.trim().toLowerCase();
    return result === 'true';
  } catch {
    return true; // 即使前置校验接口超时，亦允许尝试提交主登录表单
  }
}

/**
 * 提交登录认证
 */
export async function postLogin(username, password, captcha) {
  const form = new URLSearchParams();
  form.append('j_username', String(username).trim());
  form.append('j_password', String(password).trim());
  form.append('j_captcha', String(captcha).trim());

  let res;
  try {
    res = await fetch(`${BASE_PREFIX}j_acegi_security_check`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: form,
      credentials: 'include',
      redirect: 'follow'
    });
  } catch (err) {
    throw new Error(`网络连接超时或被阻断：${err.message}`);
  }

  const html = await decodeResponse(res, 'gbk');

  // 如果依然是登录页或者包含失败标记
  if (isLoginPage(html) || LOGIN_FAILURE_MARKERS.some(m => html.includes(m))) {
    return {
      success: false,
      message: parseLoginFailureReason(html)
    };
  }

  return {
    success: true,
    message: '登录成功'
  };
}

/**
 * 登出
 */
export async function postLogout() {
  try {
    await fetch(`${BASE_PREFIX}j_acegi_logout`, {
      credentials: 'include'
    });
  } catch {
    // 静默忽略
  }
}
