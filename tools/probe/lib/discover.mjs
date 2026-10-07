/**
 * 接口自动发现
 *
 * 思路：教务在线的左侧菜单与主框架页里嵌入了大量功能链接，
 * 直接对页面 HTML 做正则扫描即可提取出 *.do / *.jsdo / *.jsp 链接，
 * 无需在 Node 侧引入 DOM 解析器。
 */

import { toRelativePath } from './http.mjs';

/** 匹配链接路径（含可选 query） */
const LINK_RE = /([A-Za-z0-9_@.\-/]+\.(?:jsdo|do|jsp|action))(\?[^"'\s<>()\\]*)?/gi;

/** 明显不是业务接口的路径片段 */
const NOISE_PATTERNS = [
  /^javascript:/i,
  /\.(?:css|js|png|jpe?g|gif|svg|ico|woff2?)$/i,
  /^https?:\/\/(?!jwzx)/i,
  /favicon/i,
  /jquery/i,
  // JS 表达式片段（如 window.parent.headerFrame.do），不是真实地址
  /^(?:window|document|top|parent|self|this)\./i,
  /\.parent\./i,
  /\.frames\[/i
];

/** 路径 → 模块推断规则（按顺序匹配，先匹配到的胜出） */
const MODULE_RULES = [
  [/score|chengji/i, 'score'],
  [/timetable|coursearrange|paike/i, 'timetable'],
  [/exam|kaoshi/i, 'exam'],
  [/teachingplan|credit|peiyang|coursetotal/i, 'plan'],
  [/elective|selectcourse|xuanke/i, 'elective'],
  [/classroom|jiaoshi/i, 'classroom'],
  [/message|notice|announce|gonggao/i, 'notice'],
  [/studentinfo|student\/|xueji/i, 'student'],
  [/login|logout|captcha|security|listleft|index\.jsp|main\.jsp|top\.jsp/i, 'auth']
];

/**
 * 归一化链接路径
 * @returns {string|null} 形如 'manager/score/studentOwnScore.do?para=0'，失败返回 null
 */
export function normalizeLink(rawPath, rawQuery = '') {
  let p = String(rawPath || '').trim();
  if (!p) return null;
  if (NOISE_PATTERNS.some((re) => re.test(p))) return null;

  p = p.replace(/^https?:\/\/[^/]+/i, ''); // 去掉协议与域名
  p = p.replace(/^\/?academic\//i, ''); // 去掉基址前缀
  p = p.replace(/^(?:\.\.?\/)+/, ''); // 去掉 ./ ../
  p = p.replace(/^\/+/, '');

  if (!p || p.includes('..')) return null;

  const query = String(rawQuery || '').replace(/^[?&]+/, '');
  return query ? `${p}?${query}` : p;
}

/** 从 HTML 中提取全部候选链接 */
export function extractLinks(html) {
  if (!html) return [];
  const found = new Map();
  LINK_RE.lastIndex = 0;
  let match;
  while ((match = LINK_RE.exec(html)) !== null) {
    const normalized = normalizeLink(match[1], match[2] || '');
    if (!normalized) continue;
    if (!found.has(normalized)) found.set(normalized, match[0]);
  }
  return [...found.keys()];
}

/** 依据路径推断所属模块 */
export function inferModule(path) {
  for (const [re, moduleName] of MODULE_RULES) {
    if (re.test(path)) return moduleName;
  }
  return 'auth';
}

/** 路径 → 稳定的 key 片段 */
function slugify(path) {
  return path
    .replace(/[?#].*$/, '')
    .replace(/\.(jsdo|do|jsp|action)$/i, '')
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();
}

/** 解析 query 字符串为参数对象 */
export function parseQuery(query) {
  const params = {};
  if (!query) return params;
  for (const pair of String(query).split('&')) {
    if (!pair) continue;
    const eq = pair.indexOf('=');
    const key = eq === -1 ? pair : pair.slice(0, eq);
    const value = eq === -1 ? '' : decodeURIComponent(pair.slice(eq + 1));
    if (key) params[key] = value;
  }
  return params;
}

/**
 * 从菜单页中提取全部 moduleId
 *
 * URP 的菜单是「模块调度」结构：菜单项指向 accessModule.do?moduleId=NNN，
 * 由服务端按权限把它重定向到真实功能页。因此 moduleId 是发现真实接口的关键线索。
 */
export function extractModuleIds(html) {
  const ids = new Set();
  const patterns = [
    /moduleId\s*[=:]\s*["']?(\d+)/gi,
    /[({,]\s*(?:id|value|moduleid)\s*:\s*["'](\d{2,6})["']/gi
  ];
  for (const re of patterns) {
    re.lastIndex = 0;
    let match;
    while ((match = re.exec(html)) !== null) ids.add(match[1]);
  }
  return [...ids];
}

/** 生成 URP 需要的 randomString：yyyyMMddHHmmss + 6 位随机字符 */
export function makeRandomString() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const stamp =
    `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}` +
    `${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let random = '';
  for (let i = 0; i < 6; i += 1) {
    random += chars[Math.floor(Math.random() * chars.length)];
  }
  return stamp + random;
}

/** 把绝对 URL 还原成相对基址的路径（实现见 http.mjs，此处直接用） */

/**
 * 通过菜单页发现接口
 *
 * 三个阶段：
 *   ① 抓取菜单/框架入口页，正则提取全部 *.do / *.jsdo / *.jsp 链接
 *   ② 提取 moduleId，逐个请求 accessModule.do 并跟随 302，得到真实功能页地址
 *   ③ 合并去重
 *
 * @param {import('./http.mjs').HttpClient} client
 * @param {Object} [options]
 * @param {string[]} [options.seeds] 用作发现入口的页面
 * @param {boolean} [options.followModules=true] 是否跟随 accessModule.do 揭示真实地址
 * @param {number} [options.maxModules=120] 最多跟随的模块数
 * @param {(msg:string)=>void} [options.log]
 * @returns {Promise<{endpoints:Array, seeds:Array, moduleIds:string[], modules:Array}>}
 */
export async function discoverEndpoints(client, options = {}) {
  const seeds = options.seeds || [
    'index_new.jsp',
    'listLeft.do',
    'top.jsp',
    'main.jsp'
  ];
  const log = options.log || (() => {});
  const followModules = options.followModules !== false;
  const maxModules = options.maxModules ?? 200;

  const discovered = new Map();
  const seedReports = [];
  const moduleIds = new Set();

  /* -------- ⓪ 先注入外部线索（通常来自门户页分析结果） -------- */
  const extraLinks = options.extraLinks || [];
  const extraModuleIds = options.extraModuleIds || [];

  for (const raw of extraLinks) {
    const qIndex = String(raw).indexOf('?');
    const normalized = normalizeLink(
      qIndex === -1 ? raw : String(raw).slice(0, qIndex),
      qIndex === -1 ? '' : String(raw).slice(qIndex + 1)
    );
    if (normalized && !discovered.has(normalized)) discovered.set(normalized, '门户页线索');
  }
  for (const id of extraModuleIds) moduleIds.add(String(id).trim());

  if (extraLinks.length > 0 || extraModuleIds.length > 0) {
    log(
      `   · 注入门户页线索：${extraLinks.length} 个候选地址、${extraModuleIds.length} 个模块 ID`
    );
  }

  /* -------- ① 抓取入口页并提取链接与 moduleId -------- */
  for (const seed of seeds) {
    const res = await client.get(seed);
    const links = res.ok ? extractLinks(res.text) : [];
    seedReports.push({
      seed,
      status: res.status,
      ok: res.ok,
      bytes: res.buffer.length,
      charset: res.charset,
      linkCount: links.length,
      error: res.error
    });
    if (!res.ok) {
      log(`   · 菜单入口 ${seed} 不可用（${res.error || `HTTP ${res.status}`}）`);
      continue;
    }

    for (const id of extractModuleIds(res.text)) moduleIds.add(id);

    log(
      `   · 菜单入口 ${seed} 提取到 ${links.length} 个链接` +
        (res.charset ? `（编码 ${res.charset}）` : '')
    );
    for (const link of links) {
      if (!discovered.has(link)) discovered.set(link, seed);
    }
  }

  /* -------- ①.5 用 listLeft.do?moduleType=N 枚举补全模块 ID --------
   *
   * showHeader.do 里暴露了 `listLeft.do?moduleType=-1|3|5|6|7|8`，
   * 说明菜单可以按「模块类别」分页拉取。把这些类别都拉一遍，
   * 就能拿到不带该参数时看不到的模块 ID。
   */
  if (options.enumerateModuleTypes !== false) {
    const types = new Set(['-1', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9']);

    // 已发现链接里的 moduleType 一并纳入
    for (const link of discovered.keys()) {
      const m = String(link).match(/moduleType=(-?\d+)/);
      if (m) types.add(m[1]);
    }

    const sorted = [...types].sort((a, b) => Number(a) - Number(b)).slice(0, 16);
    let gained = 0;

    for (const type of sorted) {
      const res = await client.get(`listLeft.do?moduleType=${type}`);
      if (!res.ok) continue;

      const before = moduleIds.size;
      for (const id of extractModuleIds(res.text)) moduleIds.add(id);
      const delta = moduleIds.size - before;

      if (delta > 0) {
        gained += delta;
        log(`   · moduleType=${type} 额外发现 ${delta} 个模块 ID`);
      }
    }

    log(`   · moduleType 枚举结束，共补充 ${gained} 个模块 ID（累计 ${moduleIds.size} 个）`);
  }

  /* -------- ② 跟随 accessModule.do 揭示真实功能页 -------- */
  const modules = [];
  const ids = [...moduleIds].slice(0, maxModules);

  if (followModules && ids.length > 0) {
    log(`   · 发现 ${moduleIds.size} 个模块 ID，逐个跟随 accessModule.do 解析真实地址…`);

    for (const id of ids) {
      const randomString = makeRandomString();
      const res = await client.get(
        `accessModule.do?moduleId=${id}&groupId=&randomString=${randomString}`
      );

      const finalPath = toRelativePath(res.url, client.baseUrl);
      const redirected = (res.redirects || []).length > 0;

      modules.push({
        moduleId: id,
        status: res.status,
        ok: res.ok,
        finalPath,
        redirected
      });

      // 只在真正发生了跳转、且跳转到的不是入口页本身时，才认为拿到了功能页地址
      if (res.ok && finalPath && redirected && !finalPath.includes('accessModule.do')) {
        if (!discovered.has(finalPath)) discovered.set(finalPath, `accessModule.do?moduleId=${id}`);
        log(`   · moduleId=${id} → ${finalPath}`);
      }
    }
  }

  /* -------- ③ 合并去重 -------- */
  const endpoints = [];
  const usedKeys = new Set();

  for (const [link, from] of discovered) {
    const qIndex = link.indexOf('?');
    const path = qIndex === -1 ? link : link.slice(0, qIndex);
    const params = qIndex === -1 ? {} : parseQuery(link.slice(qIndex + 1));

    let key = `discovered.${slugify(path)}`;
    let suffix = 2;
    while (usedKeys.has(key)) key = `discovered.${slugify(path)}-${suffix++}`;
    usedKeys.add(key);

    endpoints.push({
      key,
      name: `（自动发现）${path}`,
      module: inferModule(path),
      path,
      method: 'GET',
      params,
      confidence: 'discovered',
      discoveredFrom: from,
      note: from.startsWith('accessModule.do')
        ? `由 ${from} 解析得到真实地址`
        : `由 ${from} 菜单页自动发现`,
      raw: link
    });
  }

  return { endpoints, seeds: seedReports, moduleIds: [...moduleIds], modules };
}

/**
 * 合并内置目录与自动发现的接口
 *  - 已存在的（同 path + method）不重复添加，仅补充 discovered 标记
 *  - 发现到新路径的追加进清单
 *
 * @param {Array} catalog 内置目录
 * @param {Array} discovered 自动发现结果
 * @returns {{merged:Array, addedCount:number, matchedCount:number}}
 */
export function mergeEndpoints(catalog, discovered) {
  const merged = catalog.map((ep) => ({ ...ep }));
  const index = new Map();
  merged.forEach((ep, i) => {
    index.set(`${ep.method.toUpperCase()} ${ep.path}`, i);
  });

  let addedCount = 0;
  let matchedCount = 0;

  for (const found of discovered) {
    const key = `${found.method.toUpperCase()} ${found.path}`;
    if (index.has(key)) {
      const target = merged[index.get(key)];
      target.discovered = true;
      // 自动发现带来的真实参数更有价值（若内置未定义参数）
      if (
        found.params &&
        Object.keys(found.params).length > 0 &&
        (!target.params || Object.keys(target.params).length === 0)
      ) {
        target.params = found.params;
      }
      matchedCount += 1;
      continue;
    }
    // 同名 key 冲突时追加序号
    const existingKeys = new Set(merged.map((ep) => ep.key));
    let key2 = found.key;
    let n = 2;
    while (existingKeys.has(key2)) {
      key2 = `${found.key}-${n++}`;
    }
    merged.push({ ...found, key: key2 });
    index.set(key, merged.length - 1);
    addedCount += 1;
  }

  return { merged, addedCount, matchedCount };
}
