/**
 * 轻量 HTTP 客户端
 *
 * 设计目标：零第三方依赖，仅使用 Node 18+ 内置能力
 *  - 手动 Cookie Jar（Node 的 fetch 不会自动持久化 Cookie）
 *  - 手动重定向跟随（保留每一次跳转的 Set-Cookie）
 *  - GBK 解码（教务在线全站 GBK 编码）
 *  - 超时、耗时统计与结构化错误
 */

const REDIRECT_STATUS = new Set([301, 302, 303, 307, 308]);

const DEFAULT_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';

/* ------------------------------------------------------------------ *
 * GBK 解码
 * ------------------------------------------------------------------ */

let cachedDecoder = null;
let cachedDecoderLabel = '';

/** 获取可用的 GBK 解码器；优先 gbk，其次 gb18030 / gb2312 */
export function getGbkDecoder() {
  if (cachedDecoder) return cachedDecoder;
  for (const label of ['gbk', 'gb18030', 'gb2312']) {
    try {
      cachedDecoder = new TextDecoder(label);
      cachedDecoderLabel = label;
      return cachedDecoder;
    } catch {
      /* 当前标签不被支持，继续尝试下一个 */
    }
  }
  console.warn(
    '[probe] 当前 Node 未提供 GBK 解码器（疑似 small-icu 构建），中文将可能乱码。'
  );
  console.warn('[probe] 建议使用 Node 官方发行版 18+，或设置 NODE_ICU_DATA。');
  cachedDecoder = new TextDecoder('utf-8');
  cachedDecoderLabel = 'utf-8';
  return cachedDecoder;
}

/** 当前 GBK 解码器名称，用于写入 manifest */
export function getGbkLabel() {
  getGbkDecoder();
  return cachedDecoderLabel;
}

/** 将字节流按 GBK 解码（仅作为兜底与显式调用场景） */
export function decodeGbk(buffer) {
  return getGbkDecoder().decode(buffer);
}

/* ------------------------------------------------------------------ *
 * 编码判定
 *
 * 实测结论：教务系统的编码 **并非全站统一**。
 *   manager/score/*            响应头 charset=UTF-8（整个文档都是 UTF-8）
 *   listLeft.do / currcourse   charset=GBK
 *   top.jsp                    charset=gb2312
 *   main.jsp                   charset=ISO-8859-1（正文为纯 ASCII）
 * 因此必须按「响应头 charset → HTML meta 嗅探 → GBK 兜底」的顺序解码，
 * 一律按 GBK 解码会让 UTF-8 接口的中文全部变成乱码，关键字匹配也会失败。
 * ------------------------------------------------------------------ */

/** 看起来不可靠、需要继续嗅探的 charset */
const UNRELIABLE_CHARSETS = /^(iso-8859-1|latin1|us-ascii|ascii|binary|unknown)$/i;

const decoderCache = new Map();

/** 归一化 charset 名称到 TextDecoder 支持的标签 */
export function normalizeCharset(label) {
  const value = String(label || '').trim().toLowerCase().replace(/["']/g, '');
  if (!value) return '';
  if (/^(gb2312|gb_2312|gb18030|gbk|cp936|ms936|windows-936)$/.test(value)) return 'gbk';
  if (/^(utf8|utf-8|unicode-1-1-utf-8)$/.test(value)) return 'utf-8';
  if (/^(big5|big-5|cp950)$/.test(value)) return 'big5';
  if (/^(shift_jis|sjis|cp932)$/.test(value)) return 'shift_jis';
  return value;
}

/** 从 Content-Type 头中取出 charset */
export function parseHeaderCharset(contentType) {
  const match = String(contentType || '').match(/charset\s*=\s*["']?([\w-]+)/i);
  return match ? normalizeCharset(match[1]) : '';
}

/** 从 HTML 头部的 meta 标签中嗅探 charset */
function sniffMetaCharset(buffer) {
  const head = buffer.subarray(0, 4096).toString('latin1');
  const match =
    head.match(/<meta[^>]+charset\s*=\s*["']?\s*([\w-]+)/i) ||
    head.match(/<meta[^>]+content\s*=\s*["'][^"']*charset\s*=\s*([\w-]+)/i);
  return match ? normalizeCharset(match[1]) : '';
}

/** 获取指定 charset 的解码器（不支持时回退 GBK） */
function getDecoder(charset) {
  const label = normalizeCharset(charset) || 'gbk';
  if (decoderCache.has(label)) return decoderCache.get(label);

  let decoder;
  try {
    decoder = new TextDecoder(label);
  } catch {
    console.warn(`[probe] 不支持 charset=${label}，回退 GBK`);
    decoder = getGbkDecoder();
  }
  decoderCache.set(label, decoder);
  return decoder;
}

/**
 * 判定应当使用的字符集
 * @param {Buffer} buffer
 * @param {string} [contentType] 响应头
 * @returns {string}
 */
export function resolveCharset(buffer, contentType) {
  const fromHeader = parseHeaderCharset(contentType);
  if (fromHeader && !UNRELIABLE_CHARSETS.test(fromHeader)) return fromHeader;

  // 响应头未给出可靠编码时，从 HTML meta 嗅探
  const fromMeta = sniffMetaCharset(buffer);
  if (fromMeta) return fromMeta;

  // 兜底：教务系统大量页面是 GBK
  return 'gbk';
}

/**
 * 按判定出的字符集解码字节流
 *
 * @param {Buffer} buffer
 * @param {string} [contentType] 响应头中的 Content-Type
 * @returns {{text:string, charset:string}}
 */
export function decodeBuffer(buffer, contentType) {
  const charset = resolveCharset(buffer, contentType);
  let text = getDecoder(charset).decode(buffer);

  // 兜底：出现大量替换字符说明编码判定有误，用 GBK 重试一次
  if (charset !== 'gbk' && countReplacementChars(text, buffer.length) > 0) {
    const retry = getGbkDecoder().decode(buffer);
    if (countReplacementChars(retry, buffer.length) < countReplacementChars(text, buffer.length)) {
      return { text: retry, charset: 'gbk' };
    }
  }

  return { text, charset };
}

/** 统计替换字符（U+FFFD）数量，用于判断编码是否判定错误 */
function countReplacementChars(text, byteLength) {
  let count = 0;
  for (const char of text) {
    if (char === '\uFFFD') count += 1;
  }
  // 少量替换字符可能只是原始数据里就有，只有明显异常才认为判错
  return count > Math.max(2, byteLength * 0.005) ? count : 0;
}

/* ------------------------------------------------------------------ *
 * Cookie Jar
 * ------------------------------------------------------------------ */

/**
 * 解析单条 Set-Cookie 字符串
 * @returns {{name:string, value:string, expired:boolean}|null}
 */
function parseSetCookie(raw) {
  if (!raw) return null;
  const firstSegment = raw.split(';')[0] || '';
  const eq = firstSegment.indexOf('=');
  if (eq <= 0) return null;

  const name = firstSegment.slice(0, eq).trim();
  const value = firstSegment.slice(eq + 1).trim();
  if (!name) return null;

  const lower = raw.toLowerCase();
  let expired = false;
  const maxAge = lower.match(/max-age\s*=\s*(-?\d+)/);
  if (maxAge && Number(maxAge[1]) <= 0) expired = true;
  const expires = raw.match(/expires\s*=\s*([^;]+)/i);
  if (expires) {
    const ts = Date.parse(expires[1]);
    if (!Number.isNaN(ts) && ts <= Date.now()) expired = true;
  }

  return { name, value, expired };
}

/** 会话 Cookie 容器 */
export class CookieJar {
  constructor(entries) {
    /** @type {Map<string,string>} */
    this.store = new Map();
    if (entries && typeof entries === 'object') {
      for (const [k, v] of Object.entries(entries)) this.store.set(k, v);
    }
  }

  /** 吞入若干条 Set-Cookie */
  absorb(setCookieList) {
    let changed = false;
    for (const raw of setCookieList || []) {
      const parsed = parseSetCookie(raw);
      if (!parsed) continue;
      if (parsed.expired) {
        changed = this.store.delete(parsed.name) || changed;
      } else {
        this.store.set(parsed.name, parsed.value);
        changed = true;
      }
    }
    return changed;
  }

  /** 生成 Cookie 请求头 */
  toHeader() {
    return [...this.store.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
  }

  get size() {
    return this.store.size;
  }

  get isEmpty() {
    return this.store.size === 0;
  }

  toJSON() {
    return Object.fromEntries(this.store);
  }

  static fromJSON(obj) {
    return new CookieJar(obj);
  }
}

/** 兼容性读取响应头里的 Set-Cookie 列表 */
function readSetCookies(headers) {
  if (typeof headers.getSetCookie === 'function') {
    return headers.getSetCookie() || [];
  }
  const single = headers.get('set-cookie');
  return single ? [single] : [];
}

/* ------------------------------------------------------------------ *
 * 表单编码
 * ------------------------------------------------------------------ */

/**
 * 将对象编码为 x-www-form-urlencoded
 * 注意：使用 UTF-8 百分号编码。URP 多数接口参数为数字/英文 ID，不受影响；
 * 若某个接口确需提交中文参数（如按课程名搜索），该接口需单独处理 GBK 编码。
 */
export function encodeForm(params = {}) {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null) continue;
    usp.append(k, String(v));
  }
  return usp.toString();
}

/**
 * 把绝对 URL 还原成相对基址的路径
 *
 * 用于「跟随 302 后取最终地址」这类场景，例如 accessModule.do 的真实落点、
 * 登录成功后的门户页地址。
 *
 * @param {string} url 绝对 URL
 * @param {string} baseUrl 基址
 * @returns {string} 形如 'manager/score/studentOwnScore.do?para=0'，无法解析时返回空串
 */
export function toRelativePath(url, baseUrl) {
  try {
    const target = new URL(url);
    const base = new URL(baseUrl);
    if (target.host !== base.host) return '';
    const path = target.pathname.startsWith(base.pathname)
      ? target.pathname.slice(base.pathname.length)
      : target.pathname;
    return `${path.replace(/^\/+/, '')}${target.search || ''}`;
  } catch {
    return '';
  }
}

/** 归一化基址，确保以 / 结尾 */
export function normalizeBase(base) {
  const url = new URL(base);
  if (!url.pathname.endsWith('/')) url.pathname += '/';
  return url.toString();
}

/* ------------------------------------------------------------------ *
 * HTTP 客户端
 * ------------------------------------------------------------------ */

export class HttpClient {
  /**
   * @param {Object} options
   * @param {string} [options.baseUrl] 教务系统基址，默认 http://jwzx.hrbust.edu.cn/academic/
   * @param {number} [options.timeout] 单请求超时毫秒
   * @param {CookieJar} [options.jar]   复用的 Cookie 容器
   * @param {boolean} [options.verbose] 打印每个请求
   * @param {(msg:string)=>void} [options.log] 日志回调
   */
  constructor(options = {}) {
    this.baseUrl = normalizeBase(
      options.baseUrl || 'http://jwzx.hrbust.edu.cn/academic/'
    );
    this.timeout = options.timeout ?? 20000;
    this.verbose = options.verbose ?? false;
    this.compress = true;
    this.maxRedirects = options.maxRedirects ?? 8;
    this.jar = options.jar instanceof CookieJar ? options.jar : new CookieJar();
    this.userAgent = options.userAgent || DEFAULT_USER_AGENT;
    this.log = options.log || ((msg) => console.log(msg));
    /** @type {string[]} 记录最近的跳转链路，便于调试 */
    this.lastRedirectChain = [];
  }

  /** 相对路径 → 绝对 URL */
  resolve(path) {
    return new URL(path, this.baseUrl).toString();
  }

  /** 记录一次请求日志 */
  #debug(method, url, status, elapsedMs, size) {
    if (!this.verbose) return;
    const ms = String(elapsedMs).padStart(5, ' ');
    this.log(`      ${method.padEnd(4)} ${status} ${ms}ms ${String(size).padStart(7, ' ')}B  ${url}`);
  }

  /**
   * 发起请求（自动跟随重定向并收集 Cookie）
   *
   * @param {string} path  相对 /academic/ 的路径或完整 URL
   * @param {Object} [options]
   * @param {'GET'|'POST'} [options.method]
   * @param {string|Object} [options.body] 字符串或待编码对象
   * @param {Object} [options.headers]
   * @param {number} [options.timeout]
   * @param {'follow'|'manual'} [options.redirect]
   * @returns {Promise<{
   *   ok:boolean, status:number, statusText:string, url:string,
   *   requestedUrl:string, headers:Headers, buffer:Buffer,
   *   text:string, gbk:string, elapsedMs:number,
   *   redirects:string[], error?:string
   * }>}
   */
  async request(path, options = {}) {
    const started = Date.now();
    const requestedUrl = /^https?:\/\//i.test(path) ? path : this.resolve(path);
    let currentUrl = requestedUrl;
    let method = (options.method || 'GET').toUpperCase();
    let body = options.body;
    const redirects = [];
    const followRedirect = (options.redirect || 'follow') === 'follow';

    try {
      for (let hop = 0; hop <= this.maxRedirects; hop++) {
        // 1. 组装请求体
        let payload;
        if (body !== undefined && body !== null) {
          payload = typeof body === 'string' ? body : encodeForm(body);
        }

        // 2. 组装请求头
        const headers = new Headers({
          'User-Agent': this.userAgent,
          Accept: '*/*',
          'Accept-Language': 'zh-CN,zh;q=0.9',
          Referer: currentUrl,
          ...(options.headers || {})
        });
        if (method === 'POST' && payload !== undefined) {
          if (!headers.has('Content-Type')) {
            headers.set(
              'Content-Type',
              'application/x-www-form-urlencoded; charset=UTF-8'
            );
          }
        } else if (method !== 'POST') {
          payload = undefined;
        }
        const cookieHeader = this.jar.toHeader();
        if (cookieHeader) headers.set('Cookie', cookieHeader);

        // 3. 发送
        const res = await fetch(currentUrl, {
          method,
          headers,
          body: payload,
          redirect: 'manual',
          signal: AbortSignal.timeout(options.timeout ?? this.timeout)
        });

        // 4. 收集 Cookie
        this.jar.absorb(readSetCookies(res.headers));

        // 5. 处理重定向
        const location = res.headers.get('location');
        if (
          followRedirect &&
          REDIRECT_STATUS.has(res.status) &&
          location &&
          hop < this.maxRedirects
        ) {
          // 资源需要释放，避免连接悬挂
          try {
            await res.arrayBuffer();
          } catch {
            /* ignore */
          }
          const nextUrl = new URL(location, currentUrl).toString();
          redirects.push(`${res.status} ${nextUrl}`);
          // 301/302/303 在浏览器语义下会退化为 GET
          if (res.status === 303 || ((res.status === 301 || res.status === 302) && method === 'POST')) {
            method = 'GET';
            body = undefined;
          }
          currentUrl = nextUrl;
          continue;
        }

        // 6. 读取内容（按响应头 charset 解码，而非固定 GBK）
        const buffer = Buffer.from(await res.arrayBuffer());
        const contentType = res.headers.get('content-type') || '';
        const { text, charset } = decodeBuffer(buffer, contentType);
        const elapsedMs = Date.now() - started;
        this.#debug(method, currentUrl, res.status, elapsedMs, buffer.length);
        this.lastRedirectChain = redirects;

        return {
          ok: res.status >= 200 && res.status < 400,
          status: res.status,
          statusText: res.statusText,
          url: currentUrl,
          requestedUrl,
          headers: res.headers,
          contentType,
          charset,
          buffer,
          text,
          gbk: text,
          elapsedMs,
          redirects
        };
      }

      throw new Error(`重定向次数超过上限 (${this.maxRedirects})`);
    } catch (err) {
      const elapsedMs = Date.now() - started;
      const message = err?.name === 'TimeoutError'
        ? `请求超时 (>${options.timeout ?? this.timeout}ms)`
        : err?.message || String(err);
      this.#debug(method, currentUrl, 'ERR', elapsedMs, 0);
      this.lastRedirectChain = redirects;
      return {
        ok: false,
        status: 0,
        statusText: 'NETWORK_ERROR',
        url: currentUrl,
        requestedUrl,
        headers: new Headers(),
        contentType: '',
        charset: '',
        buffer: Buffer.alloc(0),
        text: '',
        gbk: '',
        elapsedMs,
        redirects,
        error: message
      };
    }
  }

  /** GET 快捷方法 */
  get(path, options = {}) {
    return this.request(path, { ...options, method: 'GET' });
  }

  /** POST 快捷方法 */
  post(path, body, options = {}) {
    return this.request(path, { ...options, method: 'POST', body });
  }
}
