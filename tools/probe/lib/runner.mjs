/**
 * 探测编排核心
 *
 * 被 CLI（probe.mjs）与网页控制台（server.mjs）共用，
 * 保证两条入口的行为完全一致。
 */

import fs from 'node:fs';
import path from 'node:path';
import { HttpClient, toRelativePath } from './http.mjs';
import { analyzePortal, toReadableHtml } from './portal.mjs';
import {
  analyzeSample,
  collectNewCandidates,
  renderSampleAnalysisMarkdown,
  resolveCandidatePath
} from './analyzer.mjs';
import { toEndpointSpecs } from './harvest.mjs';
import { looksLikeLoginPage, detectErrorPage } from './dumper.mjs';
import {
  MODULES,
  filterEndpoints,
  resolvePlaceholders,
  missingContext
} from '../catalog.mjs';

/**
 * 创建带配置的 HTTP 客户端
 * @param {object} options
 * @returns {HttpClient}
 */
export function createClient(options = {}) {
  return new HttpClient({
    baseUrl: options.baseUrl,
    timeout: options.timeout ?? 20000,
    verbose: options.verbose ?? false,
    log: options.log
  });
}

/** 并发执行池（串行为默认，避免对教务系统造成压力） */
export async function runPool(items, concurrency, worker, shouldStop = () => false) {
  const limit = Math.max(1, Math.min(concurrency, items.length || 1));
  let cursor = 0;

  const runners = Array.from({ length: limit }, async () => {
    while (cursor < items.length) {
      if (shouldStop()) return;
      const index = cursor;
      cursor += 1;
      await worker(items[index], index);
    }
  });

  await Promise.all(runners);
}

/**
 * 依据过滤器与学生上下文，把完整接口清单筛成待探测列表
 *
 * @param {Array} endpoints 完整接口清单（内置 + 自动发现）
 * @param {object} options
 * @param {string[]} [options.only] 过滤器，支持前缀通配
 * @param {boolean} [options.allowMutating] 是否允许探测会改数据的接口
 * @param {object} [options.context] 学生上下文
 * @returns {{candidates:Array, skipped:Array<{ep:Object, reason:string}>}}
 */
export function buildProbePlan(endpoints, options = {}) {
  const selected = filterEndpoints(endpoints, options.only || []);
  const candidates = [];
  const skipped = [];

  for (const ep of selected) {
    if (ep.mutating && !options.allowMutating) {
      skipped.push({ ep, reason: '会改变教务数据（需开启「允许变更类接口」）' });
      continue;
    }
    const missing = missingContext(ep, options.context || {});
    if (missing.length > 0) {
      skipped.push({ ep, reason: `缺少上下文参数 ${missing.join(', ')}` });
      continue;
    }
    candidates.push(ep);
  }

  return { candidates, skipped };
}

/**
 * 探测单个接口：拼装请求 → 发送 → 落盘
 *
 * @param {HttpClient} client
 * @param {Object} endpoint
 * @param {Object} context 学生上下文
 * @param {import('./dumper.mjs').SampleStore} store
 * @returns {Promise<Object>} manifest 条目
 */
export async function probeOne(client, endpoint, context, store) {
  const params = {};
  for (const [key, value] of Object.entries(endpoint.params || {})) {
    params[key] = resolvePlaceholders(value, context);
  }

  const query = new URLSearchParams(params).toString();
  let requestPath = endpoint.path;
  let body;

  if (endpoint.method === 'POST') {
    body = params;
  } else if (query) {
    requestPath = `${endpoint.path}?${query}`;
  }

  const response = await client.request(requestPath || '', {
    method: endpoint.method,
    body,
    headers: endpoint.binary ? { Accept: 'image/*,*/*;q=0.8' } : undefined
  });

  return store.save(endpoint, response, { resolvedParams: params });
}

/**
 * 批量探测
 *
 * @param {object} options
 * @param {HttpClient} options.client
 * @param {Array} options.endpoints
 * @param {Object} options.context
 * @param {import('./dumper.mjs').SampleStore} options.store
 * @param {number} [options.concurrency]
 * @param {(info:Object)=>void} [options.onProgress] 每个接口完成时回调
 * @param {()=>boolean} [options.shouldStop] 返回 true 则中断
 * @returns {Promise<{entries:Array, aborted:boolean}>}
 */
export async function probeAll(options) {
  const {
    client,
    endpoints,
    context,
    store,
    concurrency = 1,
    onProgress,
    shouldStop = () => false
  } = options;

  const total = endpoints.length;
  let done = 0;
  let sessionExpiredWarned = false;
  const entries = [];

  await runPool(
    endpoints,
    concurrency,
    async (endpoint) => {
      const entry = await probeOne(client, endpoint, context, store);
      entries.push(entry);
      done += 1;

      // 会话失效只提示一次，避免刷屏
      const sessionAlert = entry.looksLikeLogin && !endpoint.public && !sessionExpiredWarned;
      if (sessionAlert) sessionExpiredWarned = true;

      if (onProgress) {
        onProgress({
          done,
          total,
          entry,
          sessionAlert,
          moduleName: MODULES[endpoint.module]?.name || endpoint.module
        });
      }
    },
    shouldStop
  );

  return { entries, aborted: shouldStop(), sessionExpired: sessionExpiredWarned };
}

/* ------------------------------------------------------------------ *
 * 多轮递进探测
 * ------------------------------------------------------------------ */

/**
 * 多轮递进探测
 *
 * 单轮探测只能发现「入口页里直接写着的地址」，但真实系统的每个功能页
 * 自身还会再引用更深的接口（表单 action、按钮 onclick、导出链接…）。
 * 因此每轮结束后从**已落盘的样本**里再挖一轮新地址，直到不再有新发现。
 *
 * @param {Object} options
 * @param {number} [options.maxRounds=2] 最多探测几轮
 * @param {number} [options.maxNewPerRound=200] 每轮最多新增多少接口
 * @returns {Promise<{rounds:Array, entries:Array, addedTotal:number}>}
 */
export async function probeWithExpansion(options) {
  const {
    client,
    endpoints,
    context,
    store,
    concurrency = 1,
    onProgress,
    onRound,
    shouldStop = () => false,
    maxRounds = 2,
    maxNewPerRound = 200,
    log = () => {}
  } = options;

  const knownPaths = new Set(endpoints.map((ep) => ep.path.split('?')[0]));
  const rounds = [];
  let current = endpoints;
  let addedTotal = 0;

  for (let round = 1; round <= maxRounds && current.length > 0; round += 1) {
    if (shouldStop()) break;

    const before = store.entries.length;

    await probeAll({
      client,
      endpoints: current,
      context,
      store,
      concurrency,
      shouldStop,
      // 把轮次注入进度回调，便于上层区分第几轮
      onProgress: onProgress ? (info) => onProgress({ ...info, round }) : undefined
    });

    const probed = store.entries.length - before;
    rounds.push({ round, probed, total: store.entries.length });
    if (onRound) onRound({ round, probed, total: store.entries.length });

    if (round >= maxRounds || shouldStop()) break;

    // 从全部样本里挖下一轮候选（knownPaths 保证不会重复探测）
    const { candidates } = collectNewCandidates(store.outDir, store.entries, {
      knownPaths,
      maxCandidates: maxNewPerRound
    });

    // 参数收割：目录里「缺参数」的接口，真实参数就藏在别的页面链接里。
    // 用收割到的参数生成带参候选，与地址发现的候选合并进下一轮。
    // 注意去重键用「完整路径含参数」——基础路径在第一轮已登记过，
    // 按 base 去重会把带参候选全部误杀。
    const harvestedSpecs = toEndpointSpecs(store.harvested).filter(
      (ep) => !knownPaths.has(ep.path)
    );
    for (const ep of harvestedSpecs) knownPaths.add(ep.path);

    for (const candidate of candidates) knownPaths.add(candidate.path.split('?')[0]);
    addedTotal += candidates.length + harvestedSpecs.length;

    log(
      `   · 第 ${round} 轮结束，挖出新地址 ${candidates.length} 个` +
        (harvestedSpecs.length > 0 ? `、带参候选 ${harvestedSpecs.length} 个` : '')
    );

    current = [...harvestedSpecs, ...candidates];
  }

  return { rounds, entries: store.entries, addedTotal };
}

/* ------------------------------------------------------------------ *
 * 样本结构分析
 * ------------------------------------------------------------------ */

/**
 * 把已落盘的全部样本做结构化分析，输出可读报告
 *
 * @param {import('./dumper.mjs').SampleStore} store
 * @returns {{items:Array, markdownFile:string, jsonFile:string}}
 */
export function analyzeStoredSamples(store) {
  const items = [];

  for (const entry of store.entries) {
    if (!entry.decodedFile) continue;
    const abs = path.join(store.outDir, entry.decodedFile);
    if (!fs.existsSync(abs)) continue;

    const html = fs.readFileSync(abs, 'utf8');
    if (!html.trim()) continue;

    items.push({
      file: entry.file,
      decodedFile: entry.decodedFile,
      key: entry.key,
      name: entry.name,
      path: entry.path,
      status: entry.status,
      charset: entry.charset,
      bytes: entry.bytes,
      isErrorPage: entry.isErrorPage,
      analysis: analyzeSample(html, { source: entry.path })
    });
  }

  const markdownFile = store.writeArtifact(
    'analysis.md',
    renderSampleAnalysisMarkdown(items)
  );

  const jsonFile = store.writeArtifact(
    'analysis.json',
    JSON.stringify(
      items.map((item) => ({
        key: item.key,
        path: item.path,
        file: item.file,
        charset: item.charset,
        title: item.analysis.title,
        tables: item.analysis.tables,
        forms: item.analysis.forms.map((f) => ({
          action: f.action,
          method: f.method,
          fields: f.fields.map((x) => x.name).filter(Boolean)
        })),
        buttons: item.analysis.buttons
          .filter((b) => b.onclick)
          .map((b) => ({ text: b.text, onclick: b.onclick })),
        frames: item.analysis.frames,
        endpoints: item.analysis.endpoints.map((e) => e.url),
        moduleIds: item.analysis.moduleIds
      })),
      null,
      2
    )
  );

  return { items, markdownFile, jsonFile };
}

/* ------------------------------------------------------------------ *
 * 门户页捕获
 * ------------------------------------------------------------------ */

/** 登录后门户页的候选路径（按可能性排序） */
export const PORTAL_CANDIDATES = [
  'index_new.jsp',
  'main.jsp',
  'index.jsp',
  'welcome.jsp'
];

/**
 * 登录后抓取并分析门户页
 *
 * 门户页（如 index_new.jsp）里集中了大量按钮、表单、frame 与内联 JS，
 * 真实接口路径往往只出现在这里。把它抓下来做结构化提取并落盘，
 * 就能用「读源码」代替「按命名规律盲猜」。
 *
 * @param {HttpClient} client
 * @param {import('./dumper.mjs').SampleStore} store
 * @param {Object} [options]
 * @param {string} [options.landingPath] 登录跳转落点（相对路径），优先尝试
 * @param {string[]} [options.candidates] 自定义候选路径
 * @param {(msg:string)=>void} [options.log]
 * @returns {Promise<{ok:boolean, path:string, analysis:Object|null, files:Object|null, attempts:Array}>}
 */
export async function capturePortal(client, store, options = {}) {
  const log = options.log || (() => {});
  const maxDepth = options.maxDepth ?? 2;
  const maxPages = options.maxPages ?? 15;

  const candidates = [
    ...new Set([
      ...(options.landingPath ? [options.landingPath] : []),
      ...(options.candidates || PORTAL_CANDIDATES)
    ])
  ];

  /* -------- ① 定位门户根页面 -------- */
  const attempts = [];
  let root = null;

  for (const candidate of candidates) {
    const res = await client.get(candidate);
    const text = res.text || '';
    const loginFallback = looksLikeLoginPage(text);
    const errorPage = detectErrorPage(res.status, text);
    const usable = res.ok && text.trim().length > 0 && !loginFallback && !errorPage;

    attempts.push({
      path: candidate,
      status: res.status,
      ok: res.ok,
      bytes: res.buffer.length,
      charset: res.charset,
      loginFallback,
      errorPage,
      usable
    });

    if (!usable) {
      const reason =
        res.error || (loginFallback ? '回落登录页' : errorPage ? '错误页' : `HTTP ${res.status}`);
      log(`   · 门户候选 ${candidate} 不可用（${reason}）`);
      continue;
    }

    root = { path: candidate, res };
    break;
  }

  if (!root) {
    log('   · 未找到可用门户页，跳过门户分析');
    return {
      ok: false,
      path: '',
      pages: [],
      analysis: null,
      files: null,
      inlineUrls: [],
      moduleIds: [],
      summary: null,
      attempts
    };
  }

  /* -------- ② 沿 frame/iframe 递归抓取 -------- */
  const pages = [];
  const visited = new Set();
  const queue = [{ path: root.path, depth: 0, res: root.res }];

  while (queue.length > 0 && pages.length < maxPages) {
    const item = queue.shift();
    const key = item.path;
    if (visited.has(key)) continue;
    visited.add(key);

    const html = item.res.text || '';
    const analysis = analyzePortal(html, {
      source: item.path,
      url: item.res.url,
      charset: item.res.charset,
      bytes: item.res.buffer.length,
      depth: item.depth,
      redirects: item.res.redirects || []
    });
    const files = store.writePortal(analysis, toReadableHtml(html));

    pages.push({ path: item.path, depth: item.depth, analysis, files });

    const indent = '   ' + '  '.repeat(item.depth + 1);
    log(
      `${indent}· ${item.path}（深度 ${item.depth}，${item.res.buffer.length} 字节，${item.res.charset}）` +
        ` 框架 ${analysis.summary.frames} · 表单 ${analysis.summary.forms} · ` +
        `按钮 ${analysis.summary.buttons} · 疑似接口 ${analysis.summary.inlineUrls} · ` +
        `moduleId ${analysis.summary.moduleIds}`
    );

    if (item.depth >= maxDepth) continue;

    for (const frame of analysis.frames) {
      if (!frame.src) continue;
      if (/^(?:javascript:|about:|data:)/i.test(frame.src)) continue;

      const res = await client.get(frame.src);
      const text = res.text || '';

      if (!res.ok || !text.trim()) {
        log(`${indent}  ↳ 框架 ${frame.src} 不可用（${res.error || `HTTP ${res.status}`}）`);
        continue;
      }
      if (looksLikeLoginPage(text)) {
        log(`${indent}  ↳ 框架 ${frame.src} 回落登录页，跳过`);
        continue;
      }
      if (detectErrorPage(res.status, text)) {
        log(`${indent}  ↳ 框架 ${frame.src} 为错误页，跳过`);
        continue;
      }

      const relative = toRelativePath(res.url, client.baseUrl) || frame.src;
      if (!visited.has(relative)) {
        queue.push({ path: relative, depth: item.depth + 1, res });
      }
    }
  }

  /* -------- ③ 聚合全部页面的线索 -------- */
  const urlMap = new Map();
  const moduleIdSet = new Set();

  for (const page of pages) {
    for (const item of page.analysis.inlineUrls) {
      if (!urlMap.has(item.url)) urlMap.set(item.url, { ...item, from: page.path });
    }
    for (const id of page.analysis.moduleIds) moduleIdSet.add(id);
  }

  // 把地址解析成「相对基址的路径」：相对写法要按各页面自身目录归一化，
  // 否则 frameset/index_new 这类子目录页面里的链接会被算错
  const candidateSet = new Set();
  for (const [url, meta] of urlMap) {
    const resolved = resolveCandidatePath(url, meta.from);
    if (resolved) candidateSet.add(resolved);
  }

  const summary = {
    pages: pages.length,
    maxDepth: Math.max(...pages.map((p) => p.depth), 0),
    frames: pages.reduce((n, p) => n + p.analysis.summary.frames, 0),
    forms: pages.reduce((n, p) => n + p.analysis.summary.forms, 0),
    buttons: pages.reduce((n, p) => n + p.analysis.summary.buttons, 0),
    links: pages.reduce((n, p) => n + p.analysis.summary.links, 0),
    inlineUrls: urlMap.size,
    moduleIds: moduleIdSet.size
  };

  log(
    `   · ✅ 门户页分析完成：抓取 ${summary.pages} 个页面（最深 ${summary.maxDepth} 层），` +
      `聚合 ${summary.inlineUrls} 个疑似接口、${summary.moduleIds} 个 moduleId`
  );

  return {
    ok: true,
    path: root.path,
    pages,
    // 根页面的分析结果（便于查看「门户本身是什么结构」）
    analysis: pages[0].analysis,
    files: pages[0].files,
    // 全部页面聚合后的线索（供发现阶段使用）
    inlineUrls: [...urlMap.values()],
    /** 已归一化为「相对基址路径」的候选地址，可直接喂给发现阶段 */
    candidatePaths: [...candidateSet],
    moduleIds: [...moduleIdSet],
    summary,
    attempts
  };
}

/**
 * 菜单自动发现并合并进接口清单
 *
 * @param {HttpClient} client
 * @param {Array} catalog
 * @param {(msg:string)=>void} [log]
 * @param {Object} [options]
 * @param {string[]} [options.extraLinks] 额外候选地址（如门户页里扫出来的）
 * @param {string[]} [options.extraModuleIds] 额外模块 ID（如门户页里提取的）
 * @param {string[]} [options.seeds] 自定义发现入口页
 * @returns {Promise<{endpoints:Array, addedCount:number, matchedCount:number, discoveredCount:number, seeds:Array}>}
 */
export async function discoverAndMerge(client, catalog, log, options = {}) {
  const { discoverEndpoints, mergeEndpoints } = await import('./discover.mjs');
  const discovery = await discoverEndpoints(client, { log, ...options });
  const { merged, addedCount, matchedCount } = mergeEndpoints(catalog, discovery.endpoints);
  return {
    endpoints: merged,
    addedCount,
    matchedCount,
    discoveredCount: discovery.endpoints.length,
    seeds: discovery.seeds,
    moduleIds: discovery.moduleIds || [],
    modules: discovery.modules || []
  };
}

/** 从完整接口清单中剔除不参与批量的接口 */
export function batchableEndpoints(catalog) {
  return catalog.filter((ep) => !ep.skipInBatch && ep.key !== 'auth.logout');
}
