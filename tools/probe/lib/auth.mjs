/**
 * 认证流程：验证码 → Acegi 表单登录 → 会话校验 → 登出
 *
 * 教务在线使用 Spring Acegi Security，登录表单提交到 j_acegi_security_check，
 * 字段为 j_username / j_password / j_captcha。
 */

import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { CookieJar } from './http.mjs';

/** 登录页特征（出现即代表当前处于未登录状态） */
const LOGIN_PAGE_MARKERS = ['j_acegi_security_check', 'getCaptcha.do', 'j_captcha'];

/** 登录失败页特征 */
const LOGIN_FAILURE_MARKERS = [
  'badCredentials',
  '密码错误',
  '用户名不存在',
  '验证码错误',
  'Bad credentials',
  '用户不存在'
];

/** 判断 HTML 是否为登录页 */
export function isLoginPage(html) {
  if (!html) return true;
  return LOGIN_PAGE_MARKERS.some((m) => html.includes(m));
}

/** 从登录失败页中提取可读原因 */
export function describeLoginFailure(html) {
  const text = String(html || '')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1>/gi, ' ')
    .replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ');
  if (/锁定|已冻结|已禁用|accountLocked/i.test(text)) return '账号已被系统锁定，请稍后再试';
  if (/用户不存在|学号不存在|用户名不存在/.test(text)) return '该学号不存在';
  if (/验证码\s*(?:输入|校验|验证)?\s*(错误|已?过期|失效|不正确|无效)|captcha\s*(invalid|incorrect|expired)/i.test(text)) return '验证码错误或已过期';
  if (/密码\s*(?:输入)?\s*(错误|不正确|无效)|badCredentials|Bad credentials/i.test(text)) return '学号或密码错误';
  if (!text.trim()) return '登录失败（无响应内容，可能未连接校园网）';
  return '登录失败，请检查学号与密码';
}

/**
 * 用系统默认程序打开文件（便于用户查看验证码图片）
 * 仅在 Windows / macOS 上尝试，失败时静默忽略。
 */
export function openWithSystemViewer(filePath) {
  try {
    const abs = path.resolve(filePath);
    if (!fs.existsSync(abs)) return false;
    const platform = process.platform;
    let child;
    if (platform === 'win32') {
      child = spawn('cmd', ['/c', 'start', '', abs], {
        detached: true,
        stdio: 'ignore',
        windowsHide: true
      });
    } else if (platform === 'darwin') {
      child = spawn('open', [abs], { detached: true, stdio: 'ignore' });
    } else {
      child = spawn('xdg-open', [abs], { detached: true, stdio: 'ignore' });
    }
    child.on('error', () => {});
    child.unref();
    return true;
  } catch {
    return false;
  }
}

/**
 * 拉取图形验证码并存盘
 *
 * @param {import('./http.mjs').HttpClient} client
 * @param {string} outFile 保存路径（含扩展名）
 * @returns {Promise<{ok:boolean, file:string, size:number, status:number, error?:string}>}
 */
export async function fetchCaptcha(client, outFile) {
  const res = await client.get(`getCaptcha.do?_t=${Date.now()}`, {
    headers: { Accept: 'image/avif,image/webp,image/png,image/*,*/*;q=0.8' }
  });

  if (!res.ok || res.buffer.length === 0) {
    return {
      ok: false,
      file: outFile,
      size: res.buffer.length,
      status: res.status,
      error: res.error || `HTTP ${res.status}`
    };
  }

  const contentType = res.headers.get('content-type') || '';

  // 教务系统实际返回 JPEG，但调用方通常按 .png 命名。
  // 若扩展名与实际格式不符，系统看图工具可能拒绝打开，因此这里按真实格式纠正。
  const actualFile = withCorrectExtension(outFile, contentType);

  fs.mkdirSync(path.dirname(path.resolve(actualFile)), { recursive: true });
  fs.writeFileSync(actualFile, res.buffer);

  return {
    ok: true,
    file: actualFile,
    size: res.buffer.length,
    status: res.status,
    contentType
  };
}

/** 依据 Content-Type 纠正图片文件扩展名 */
function withCorrectExtension(file, contentType) {
  const type = String(contentType).toLowerCase();
  let ext = null;

  if (type.includes('png')) ext = '.png';
  else if (type.includes('jpeg') || type.includes('jpg')) ext = '.jpg';
  else if (type.includes('gif')) ext = '.gif';
  else if (type.includes('bmp')) ext = '.bmp';

  if (!ext) return file;

  const current = path.extname(file).toLowerCase();
  if (current === ext) return file;
  return file.slice(0, file.length - current.length) + ext;
}

/**
 * 校验验证码是否有效
 * @returns {Promise<{valid:boolean|null, raw:string, status:number}>}
 *   valid === null 表示该接口不可用（应由登录接口最终判定）
 */
export async function checkCaptcha(client, captchaCode) {
  const res = await client.post(
    `checkCaptcha.do?captchaCode=${encodeURIComponent(captchaCode)}`
  );
  if (!res.ok) return { valid: null, raw: res.error || '', status: res.status };
  const raw = res.text.trim();
  if (/^true$/i.test(raw)) return { valid: true, raw, status: res.status };
  if (/^false$/i.test(raw)) return { valid: false, raw, status: res.status };
  return { valid: null, raw, status: res.status };
}

/**
 * 提交 Acegi 登录表单
 *
 * @param {import('./http.mjs').HttpClient} client
 * @param {{username:string, password:string, captcha:string}} credentials
 * @returns {Promise<{success:boolean, reason:string, message:string, finalUrl:string, html:string, status:number}>}
 */
export async function submitLogin(client, credentials) {
  const { username, password, captcha } = credentials;

  if (!username || !password) {
    return {
      success: false,
      reason: 'MISSING_CREDENTIALS',
      message: '缺少学号或密码',
      finalUrl: '',
      html: '',
      status: 0
    };
  }
  if (!/^\d{4}$/.test(String(captcha || '').trim())) {
    return {
      success: false,
      reason: 'CAPTCHA_INVALID',
      message: '验证码格式不正确（应为 4 位）',
      finalUrl: '',
      html: '',
      status: 0
    };
  }

  const res = await client.post('j_acegi_security_check', {
    j_username: username.trim(),
    j_password: password.trim(),
    j_captcha: String(captcha).trim()
  });

  if (!res.ok && res.status === 0) {
    return {
      success: false,
      reason: 'NETWORK_ERROR',
      message: res.error || '网络不可达',
      finalUrl: res.url,
      html: '',
      status: 0
    };
  }

  const html = res.text || '';
  const stillLoginPage = isLoginPage(html);
  const hitFailureMarker = LOGIN_FAILURE_MARKERS.some((m) => html.includes(m));

  // 失败典型特征：被重定向回登录页 / 页面含失败关键字
  if (stillLoginPage || hitFailureMarker) {
    return {
      success: false,
      reason: 'CREDENTIAL_ERROR',
      message: describeLoginFailure(html),
      finalUrl: res.url,
      html,
      status: res.status
    };
  }

  return {
    success: true,
    reason: 'OK',
    message: '登录成功',
    finalUrl: res.url,
    html,
    status: res.status
  };
}

/**
 * 通过一个受保护的接口确认当前会话是否有效，并顺带提取学生上下文
 *
 * @param {import('./http.mjs').HttpClient} client
 * @returns {Promise<{loggedIn:boolean, studentId:string, year:string, term:string, raw:string, status:number}>}
 */
export async function verifySession(client) {
  const res = await client.get('student/currcourse/currcourse.jsdo');

  if (!res.ok) {
    return { loggedIn: false, studentId: '', year: '', term: '', raw: '', status: res.status };
  }

  const html = res.text || '';
  if (isLoginPage(html)) {
    return { loggedIn: false, studentId: '', year: '', term: '', raw: html, status: res.status };
  }

  const pick = (name) => {
    const m =
      html.match(new RegExp(`${name}\\s*=\\s*["']([^"']+)["']`, 'i')) ||
      html.match(new RegExp(`${name}\\s*=\\s*([\\w.-]+)`, 'i'));
    return m ? m[1] : '';
  };

  const studentId = pick('studentid') || pick('studentId');
  const year = pick('year');
  const term = pick('term');

  // 命中 studentid 才认为会话真正有效
  const loggedIn = Boolean(studentId) || !isLoginPage(html);
  return { loggedIn, studentId, year, term, raw: html, status: res.status };
}

/** 登出（清理服务端会话） */
export async function logout(client) {
  const res = await client.get('logout_security_check');
  return { ok: res.ok, status: res.status, url: res.url };
}

/* ------------------------------------------------------------------ *
 * 会话持久化
 * ------------------------------------------------------------------ */

const SESSION_VERSION = 1;

/** 将 Cookie 保存到文件，便于跳过验证码重跑 */
export function saveSession(client, file, extra = {}) {
  fs.mkdirSync(path.dirname(path.resolve(file)), { recursive: true });
  fs.writeFileSync(
    file,
    JSON.stringify(
      {
        version: SESSION_VERSION,
        savedAt: new Date().toISOString(),
        baseUrl: client.baseUrl,
        cookies: client.jar.toJSON(),
        ...extra
      },
      null,
      2
    ),
    'utf8'
  );
}

/** 从文件恢复 Cookie 到客户端 */
export function loadSession(client, file) {
  if (!fs.existsSync(file)) return null;
  try {
    const data = JSON.parse(fs.readFileSync(file, 'utf8'));
    client.jar = CookieJar.fromJSON(data.cookies || {});
    return data;
  } catch (err) {
    console.warn(`[probe] 会话文件解析失败：${err.message}`);
    return null;
  }
}
