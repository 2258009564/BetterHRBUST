#!/usr/bin/env node
/**
 * 教务在线 · 网页探测控制台（本地服务）
 *
 * 为什么需要它：
 *   登录态接口需要「验证码 + 真实账号」，而验证码是图片，只能由人来看。
 *   本服务在本地跑一个 HTTP 服务，网页里输入账号密码与验证码完成登录，
 *   会话 Cookie 由服务端持有，随后所有探测都在服务端发起，
 *   浏览器只负责展示与操作 —— 既不依赖终端，也不受 CORS 限制。
 *
 * 安全说明：
 *   · 只监听 127.0.0.1，不对局域网暴露；
 *   · 账号密码仅用于本次登录请求，不写入任何文件（会话文件只存 Cookie）；
 *   · 探测样本可能含隐私数据，请勿公开分享。
 *
 * 用法：
 *   node tools/probe/server.mjs [--port 7788] [--open] [--out output]
 */

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

import { getGbkLabel, toRelativePath, decodeBuffer } from './lib/http.mjs';
import {
  fetchCaptcha,
  checkCaptcha,
  submitLogin,
  verifySession,
  saveSession,
  loadSession,
  logout
} from './lib/auth.mjs';
import { SampleStore, cleanOutput } from './lib/dumper.mjs';
import { createRedactor } from './lib/redact.mjs';
import { CATALOG, MODULES, summarizeCatalog } from './catalog.mjs';
import {
  createClient,
  buildProbePlan,
  probeWithExpansion,
  discoverAndMerge,
  batchableEndpoints,
  capturePortal,
  analyzeStoredSamples
} from './lib/runner.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(HERE, 'public');

/* ------------------------------------------------------------------ *
 * CLI 参数
 * ------------------------------------------------------------------ */

function parseArgs(argv) {
  const options = {
    port: 7788,
    host: '127.0.0.1',
    open: false,
    outDir: path.join(HERE, 'output'),
    baseUrl: 'http://jwzx.hrbust.edu.cn/academic/',
    timeout: 20000
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const eq = arg.indexOf('=');
    const flag = eq > -1 ? arg.slice(0, eq) : arg;
    const inline = eq > -1 ? arg.slice(eq + 1) : undefined;
    const next = () => (inline !== undefined ? inline : argv[++i]);

    if (flag === '--port' || flag === '-p') options.port = Number(next()) || options.port;
    else if (flag === '--host') options.host = next() || options.host;
    else if (flag === '--out') options.outDir = path.resolve(HERE, next() || 'output');
    else if (flag === '--base') options.baseUrl = next() || options.baseUrl;
    else if (flag === '--timeout') options.timeout = Number(next()) || options.timeout;
    else if (flag === '--open') options.open = true;
    else if (flag === '--help' || flag === '-h') options.help = true;
  }

  return options;
}

const OPTIONS = parseArgs(process.argv.slice(2));

if (OPTIONS.help) {
  console.log(`
教务在线 · 网页探测控制台

用法:
  node tools/probe/server.mjs [选项]

选项:
  -p, --port <端口>   监听端口，默认 7788
      --host <地址>   监听地址，默认 127.0.0.1（仅本机可访问）
      --out <目录>    样本输出目录，默认 tools/probe/output
      --base <URL>    教务系统基址
      --timeout <ms>  单请求超时，默认 20000
      --open          启动后自动打开浏览器
  -h, --help          显示本帮助
`.trim());
  process.exit(0);
}

const UTF8 = 'utf-8';

/* ------------------------------------------------------------------ *
 * 运行状态（单用户本地工具，全局单会话即可）
 * ------------------------------------------------------------------ */

const sessionFile = path.join(OPTIONS.outDir, 'session.json');

const state = {
  client: createClient({
    baseUrl: OPTIONS.baseUrl,
    timeout: OPTIONS.timeout,
    log: (msg) => log(`[http] ${msg}`)
  }),
  identity: { loggedIn: false, studentId: '', year: '', term: '' },
  landingPath: '',
  /** 登录学号，用于产物脱敏 */
  username: '',
  /** 当前生效的脱敏器 */
  redactor: null,
  portal: null,
  store: null,
  running: false,
  stopRequested: false,
  lastRun: null
};

function log(message) {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  console.log(`  ${time}  ${message}`);
}

/** 获取当前脱敏器（默认启用） */
function ensureRedactor() {
  if (!state.redactor) {
    state.redactor = createRedactor({ enabled: true, username: state.username });
  }
  return state.redactor;
}

/** 确保样本仓库存在 */
function ensureStore() {
  if (!state.store) {
    state.store = new SampleStore({ outDir: OPTIONS.outDir, redactor: ensureRedactor() });
  }
  return state.store;
}

/* 启动时尝试复用上次会话，省去重复登录 */
(function restoreSession() {
  const restored = loadSession(state.client, sessionFile);
  if (restored) {
    log(`已加载上次会话（保存于 ${restored.savedAt || '未知时间'}），可尝试直接探测`);
    state.identity = {
      loggedIn: Boolean(restored.studentId),
      studentId: restored.studentId || '',
      year: '',
      term: ''
    };
    state.landingPath = restored.landingPath || '';
  }
})();

/* ------------------------------------------------------------------ *
 * HTTP 工具
 * ------------------------------------------------------------------ */

function sendJson(res, status, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function sendText(res, status, text, contentType = 'text/plain; charset=utf-8') {
  const body = Buffer.from(text, UTF8);
  res.writeHead(status, {
    'Content-Type': contentType,
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function readJsonBody(req, limit = 1024 * 64) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error('请求体过大'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error('请求体不是合法 JSON'));
      }
    });
    req.on('error', reject);
  });
}

/** 校验并解析输出目录内的文件路径，防目录穿越 */
function safeResolve(relPath) {
  const target = path.resolve(OPTIONS.outDir, String(relPath || ''));
  const root = path.resolve(OPTIONS.outDir);
  if (target !== root && !target.startsWith(root + path.sep)) return null;
  return target;
}

/* ------------------------------------------------------------------ *
 * SSE
 * ------------------------------------------------------------------ */

function sseOpen(res) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no'
  });
  res.write(': connected\n\n');
}

function sseSend(res, payload) {
  if (res.writableEnded) return;
  res.write(`data: ${JSON.stringify(payload)}\n\n`);
}

/* ------------------------------------------------------------------ *
 * 路由
 * ------------------------------------------------------------------ */

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const route = url.pathname;

  try {
    if (route === '/' || route === '/index.html') return serveStatic(res, 'index.html');
    if (route.startsWith('/api/')) return await handleApi(req, res, route, url);
    return sendText(res, 404, 'Not Found');
  } catch (err) {
    log(`请求处理异常 ${route}: ${err?.message || err}`);
    if (!res.headersSent) sendJson(res, 500, { success: false, message: err?.message || String(err) });
    else res.end();
  }
});

/** 静态文件 */
function serveStatic(res, name) {
  const file = path.join(PUBLIC_DIR, name);
  if (!fs.existsSync(file)) return sendText(res, 404, '控制台页面缺失：' + name);

  const ext = path.extname(file).toLowerCase();
  const types = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.svg': 'image/svg+xml'
  };
  const body = fs.readFileSync(file);
  res.writeHead(200, {
    'Content-Type': types[ext] || 'application/octet-stream',
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

async function handleApi(req, res, route, url) {
  switch (route) {
    case '/api/status':
      return sendJson(res, 200, buildStatus());

    case '/api/catalog':
      return sendJson(res, 200, buildCatalog());

    case '/api/captcha':
      return await handleCaptcha(res);

    case '/api/login':
      return await handleLogin(req, res);

    case '/api/logout':
      return await handleLogout(res);

    case '/api/probe/stream':
      return await handleProbeStream(req, res, url);

    case '/api/manifest':
      return handleManifest(res);

    case '/api/report':
      return handleReport(res);

    case '/api/sample':
      return handleSample(res, url);

    case '/api/portal':
      return handlePortal(res, url);

    default:
      return sendJson(res, 404, { success: false, message: '未知接口' });
  }
}

/* ------------------------------------------------------------------ *
 * API 实现
 * ------------------------------------------------------------------ */

function buildStatus() {
  return {
    baseUrl: OPTIONS.baseUrl,
    outDir: OPTIONS.outDir,
    gbkDecoder: getGbkLabel(),
    nodeVersion: process.version,
    cookies: state.client.jar.size,
    landingPath: state.landingPath,
    running: state.running,
    identity: state.identity,
    portal: state.portal,
    catalogSummary: summarizeCatalog(),
    lastRun: state.lastRun
  };
}

function buildCatalog() {
  return {
    modules: MODULES,
    endpoints: CATALOG.map((ep) => ({
      key: ep.key,
      name: ep.name,
      module: ep.module,
      moduleName: MODULES[ep.module]?.name || ep.module,
      path: ep.path,
      method: ep.method,
      confidence: ep.confidence,
      mutating: Boolean(ep.mutating),
      skipInBatch: Boolean(ep.skipInBatch),
      note: ep.note || ''
    }))
  };
}

/** 拉取验证码并以图片形式返回（会话 Cookie 留在服务端） */
async function handleCaptcha(res) {
  const captcha = await fetchCaptcha(state.client, path.join(OPTIONS.outDir, 'captcha.png'));

  if (!captcha.ok) {
    return sendJson(res, 502, {
      success: false,
      message: `验证码获取失败：${captcha.error || `HTTP ${captcha.status}`}`
    });
  }

  const buffer = fs.readFileSync(captcha.file);
  res.writeHead(200, {
    'Content-Type': captcha.contentType || 'image/png',
    'Content-Length': buffer.length,
    'Cache-Control': 'no-store, no-cache, must-revalidate'
  });
  res.end(buffer);
}

async function handleLogin(req, res) {
  if (req.method !== 'POST') return sendJson(res, 405, { success: false, message: '请使用 POST' });

  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    return sendJson(res, 400, { success: false, message: err.message });
  }

  const username = String(body.username || '').trim();
  const password = String(body.password || '').trim();
  const captcha = String(body.captcha || '').trim();

  if (!username || !password) {
    return sendJson(res, 400, { success: false, message: '请填写学号与密码' });
  }
  if (!/^\d{4}$/.test(captcha)) {
    return sendJson(res, 400, { success: false, message: '请输入 4 位验证码' });
  }

  log(`尝试登录，学号 ${username}`);

  // 与教务站点自身逻辑一致：先校验验证码
  const captchaCheck = await checkCaptcha(state.client, captcha);
  if (captchaCheck.valid === false) {
    log('验证码不正确或已过期');
    return sendJson(res, 200, {
      success: false,
      stage: 'captcha',
      message: '验证码不正确或已过期，请点击图片刷新后重试'
    });
  }

  const result = await submitLogin(state.client, { username, password, captcha });
  if (!result.success) {
    log(`登录失败：${result.message}`);
    return sendJson(res, 200, { success: false, stage: 'login', message: result.message });
  }

  const identity = await verifySession(state.client);
  state.identity = {
    loggedIn: identity.loggedIn,
    studentId: identity.studentId || username,
    year: identity.year,
    term: identity.term
  };

  // 登录跳转落点通常就是门户页（如 index_new.jsp），记录下来供门户捕获优先使用
  state.landingPath = toRelativePath(result.finalUrl, state.client.baseUrl);

  // 记录登录学号，并同步到脱敏器：学号会出现在多处响应里，必须屏蔽
  state.username = username;
  ensureRedactor().addKnown('username', username);

  saveSession(state.client, sessionFile, {
    studentId: state.identity.studentId,
    landingPath: state.landingPath
  });

  log(
    `登录成功：学号 ${state.identity.studentId}，` +
      `学年 ${state.identity.year || '?'} 学期 ${state.identity.term || '?'}`
  );

  return sendJson(res, 200, {
    success: true,
    message: '登录成功',
    identity: state.identity
  });
}

async function handleLogout(res) {
  await logout(state.client);
  state.identity = { loggedIn: false, studentId: '', year: '', term: '' };
  log('已退出登录');
  return sendJson(res, 200, { success: true });
}

function handleManifest(res) {
  const file = path.join(OPTIONS.outDir, 'manifest.json');
  if (!fs.existsSync(file)) {
    return sendJson(res, 404, { success: false, message: '还没有探测结果，请先执行一次探测' });
  }
  const body = fs.readFileSync(file);
  res.writeHead(200, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

function handleReport(res) {
  const file = path.join(OPTIONS.outDir, 'manifest.md');
  if (!fs.existsSync(file)) return sendText(res, 404, '还没有探测结果，请先执行一次探测');
  return sendText(res, 200, fs.readFileSync(file, 'utf8'), 'text/markdown; charset=utf-8');
}

function handleSample(res, url) {
  const file = safeResolve(url.searchParams.get('file'));
  if (!file || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    return sendJson(res, 404, { success: false, message: '样本文件不存在' });
  }

  const ext = path.extname(file).toLowerCase();
  const isImage = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.bin'].includes(ext);
  const body = fs.readFileSync(file);

  if (isImage) {
    res.writeHead(200, {
      'Content-Type': ext === '.png' ? 'image/png' : `image/${ext.slice(1)}`,
      'Content-Length': body.length,
      'Cache-Control': 'no-store'
    });
    return res.end(body);
  }

  // 原始样本保持服务器字节原貌，这里按同样规则判定编码后再输出为 UTF-8 文本
  const { text } = decodeBuffer(body, '');
  return sendText(res, 200, text, 'text/plain; charset=utf-8');
}

/**
 * 读取分析产物（门户页 / 样本结构分析）
 * 统一以纯文本返回，避免在控制台源上执行页面脚本
 */
function handlePortal(res, url) {
  const relPath = url.searchParams.get('file');

  // 只允许读取 portal/ 目录下的文件，以及根目录的 analysis.md / analysis.json
  const file = safeResolve(relPath);
  const portalRoot = path.join(OPTIONS.outDir, 'portal');
  const analysisFiles = new Set([
    path.join(OPTIONS.outDir, 'analysis.md'),
    path.join(OPTIONS.outDir, 'analysis.json')
  ]);
  const allowed =
    file && (file.startsWith(portalRoot + path.sep) || analysisFiles.has(file));

  if (!allowed || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    return sendJson(res, 404, {
      success: false,
      message: '分析产物不存在，请先执行一次探测'
    });
  }

  const body = fs.readFileSync(file);
  res.writeHead(200, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Content-Length': body.length,
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

/** 探测主流程（SSE 实时推送进度） */
async function handleProbeStream(req, res, url) {
  if (state.running) {
    sseOpen(res);
    sseSend(res, { type: 'error', message: '已有探测任务在运行中，请等待完成或先停止' });
    return res.end();
  }

  sseOpen(res);

  const params = url.searchParams;
  const only = (params.get('only') || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const concurrency = Math.max(1, Math.min(4, Number(params.get('concurrency')) || 1));
  const depth = Math.max(1, Math.min(4, Number(params.get('depth')) || 2));
  const discover = params.get('discover') !== 'false';
  const portalEnabled = params.get('portal') !== 'false';
  const allowMutating = params.get('allowMutating') === 'true';
  const cleanFirst = params.get('clean') === 'true';
  const redactEnabled = params.get('redact') !== 'false';

  let aborted = false;
  req.on('close', () => {
    if (state.running) {
      aborted = true;
      state.stopRequested = true;
    }
  });

  state.running = true;
  state.stopRequested = false;

  try {
    /* 1. 会话检查 */
    if (!state.identity.loggedIn) {
      sseSend(res, { type: 'stage', stage: 'session', message: '正在校验会话…' });
      const identity = await verifySession(state.client).catch(() => ({
        loggedIn: false,
        studentId: '',
        year: '',
        term: ''
      }));
      state.identity = { ...identity };
    }

    if (!state.identity.loggedIn) {
      sseSend(res, { type: 'error', message: '尚未登录或会话已失效，请先在左侧完成登录' });
      return res.end();
    }

    const context = {
      studentId: state.identity.studentId,
      year: state.identity.year,
      term: state.identity.term
    };

    sseSend(res, { type: 'stage', stage: 'session', message: '会话有效', identity: state.identity });

    if (!context.studentId || !context.year || !context.term) {
      sseSend(res, {
        type: 'warn',
        message: '学生上下文不完整，依赖学年/学期的接口将被跳过'
      });
    }

    /* 2. 连通性 + 站点入口样本 */
    if (cleanFirst) {
      const removed = cleanOutput(OPTIONS.outDir);
      state.store = null;
      if (removed.length > 0) {
        sseSend(res, {
          type: 'log',
          message: `已清空旧产物（保留会话文件）：${removed.join('、')}`
        });
      }
    }

    // 每次探测按请求参数重建脱敏器（登记当前登录学号），
    // 并让样本仓库随之重建，避免沿用上一次的脱敏配置
    state.redactor = createRedactor({
      enabled: redactEnabled,
      username: state.username
    });
    // ⚠️ 内部学生 ID 必须在重建**之后**登记：重建会丢弃之前登记的值。
    //    实测教训：登记放在重建之前，导致内部 ID 明文写进了 40 处产物。
    state.redactor.addKnown('studentId', context.studentId);
    state.store = null;

    if (!redactEnabled) {
      sseSend(res, {
        type: 'warn',
        message: '已关闭脱敏：产物将包含真实隐私数据，请勿分享或提交'
      });
    }

    const store = ensureStore();
    const rootRes = await state.client.get('');
    if (!rootRes.ok) {
      sseSend(res, {
        type: 'error',
        message: `无法访问教务系统：${rootRes.error || `HTTP ${rootRes.status}`}。请确认已连接校园网或学校 VPN。`
      });
      return res.end();
    }
    store.save(
      {
        key: 'auth.root',
        name: '登录页 / 站点入口',
        module: 'auth',
        path: '',
        method: 'GET',
        public: true,
        markers: ['j_acegi_security_check']
      },
      rootRes
    );

    /* 3. 门户页捕获与分析 */
    let portal = { ok: false, analysis: null, files: null };

    if (portalEnabled) {
      sseSend(res, {
        type: 'stage',
        stage: 'portal',
        message: '正在抓取登录后门户页并做结构化分析…'
      });
      try {
        portal = await capturePortal(state.client, store, {
          landingPath: state.landingPath,
          log: (msg) => sseSend(res, { type: 'log', message: msg.replace(/^\s+/, '') })
        });
        state.portal = portal.ok
          ? { path: portal.path, summary: portal.summary, pages: portal.pages.map((p) => p.path) }
          : null;

        if (portal.ok) {
          sseSend(res, {
            type: 'portal',
            path: portal.path,
            summary: portal.summary,
            files: portal.files,
            title: portal.analysis.title,
            chars: portal.analysis.charset,
            pages: portal.pages.map((p) => ({
              path: p.path,
              depth: p.depth,
              title: p.analysis.title,
              summary: p.analysis.summary,
              files: p.files
            })),
            inlineUrls: portal.inlineUrls.slice(0, 100),
            moduleIds: portal.moduleIds.slice(0, 200),
            attempts: portal.attempts
          });
        } else {
          sseSend(res, { type: 'warn', message: '未找到可用门户页，跳过门户分析' });
        }
      } catch (err) {
        sseSend(res, { type: 'warn', message: `门户页分析失败（已跳过）：${err.message}` });
      }
    }

    /* 4. 接口发现 */
    let endpoints = batchableEndpoints(CATALOG);

    if (discover) {
      sseSend(res, { type: 'stage', stage: 'discover', message: '正在扫描菜单页，自动发现接口…' });
      try {
        const found = await discoverAndMerge(
          state.client,
          CATALOG,
          (msg) => sseSend(res, { type: 'log', message: msg.replace(/^\s+/, '') }),
          portal.ok
            ? {
                extraLinks: portal.candidatePaths,
                extraModuleIds: portal.moduleIds
              }
            : {}
        );
        endpoints = batchableEndpoints(found.endpoints);
        sseSend(res, {
          type: 'discovered',
          discoveredCount: found.discoveredCount,
          addedCount: found.addedCount,
          matchedCount: found.matchedCount,
          moduleIds: found.moduleIds
        });
      } catch (err) {
        sseSend(res, { type: 'warn', message: `自动发现失败（已跳过）：${err.message}` });
      }
    }

    /* 4. 生成探测计划 */
    const { candidates, skipped } = buildProbePlan(endpoints, {
      only,
      allowMutating,
      context
    });

    sseSend(res, {
      type: 'plan',
      total: candidates.length,
      skipped: skipped.map(({ ep, reason }) => ({ key: ep.key, name: ep.name, reason })),
      endpoints: candidates.map((ep) => ({
        key: ep.key,
        name: ep.name,
        module: ep.module,
        moduleName: MODULES[ep.module]?.name || ep.module,
        method: ep.method,
        path: ep.path,
        confidence: ep.confidence
      }))
    });

    if (candidates.length === 0) {
      sseSend(res, { type: 'error', message: '没有可探测的接口，请检查筛选条件' });
      return res.end();
    }

    /* 5. 批量探测（多轮递进） */
    sseSend(res, {
      type: 'stage',
      stage: 'probe',
      message: `开始探测 ${candidates.length} 个接口（最多 ${depth} 轮，逐轮递进发现）…`
    });

    const started = Date.now();
    const result = await probeWithExpansion({
      client: state.client,
      endpoints: candidates,
      context,
      store,
      concurrency,
      maxRounds: depth,
      shouldStop: () => aborted || state.stopRequested,
      log: (msg) => sseSend(res, { type: 'log', message: msg.replace(/^\s+/, '') }),
      onRound: ({ round, probed, total }) =>
        sseSend(res, { type: 'round', round, probed, total }),
      onProgress: ({ round, done, total, entry, sessionAlert, moduleName }) => {
        sseSend(res, {
          type: 'progress',
          round,
          done,
          total,
          moduleName,
          sessionAlert,
          entry: {
            index: entry.index,
            key: entry.key,
            name: entry.name,
            module: entry.module,
            method: entry.method,
            path: entry.path,
            status: entry.status,
            ok: entry.ok,
            bytes: entry.bytes,
            elapsedMs: entry.elapsedMs,
            contentType: entry.contentType,
            charset: entry.charset,
            markersHit: entry.markersHit,
            hitMarkers: entry.hitMarkers,
            looksLikeLogin: entry.looksLikeLogin,
            isErrorPage: entry.isErrorPage,
            publicAccess: entry.publicAccess,
            confidence: entry.confidence,
            file: entry.file,
            decodedFile: entry.decodedFile,
            preview: entry.preview,
            error: entry.error,
            note: entry.note
          }
        });
      }
    });

    /* 6. 样本结构分析 */
    sseSend(res, { type: 'stage', stage: 'analyze', message: '正在对全部样本做结构化分析…' });
    let analysis = { items: [], markdownFile: '', jsonFile: '' };
    try {
      analysis = analyzeStoredSamples(store);
      sseSend(res, {
        type: 'analysis',
        count: analysis.items.length,
        files: { markdown: analysis.markdownFile, json: analysis.jsonFile },
        items: analysis.items.map((item) => ({
          key: item.key,
          path: item.path,
          title: item.analysis.title,
          tables: item.analysis.tables,
          forms: item.analysis.forms.map((f) => ({ action: f.action, method: f.method })),
          endpoints: item.analysis.endpoints.map((e) => e.url)
        }))
      });
    } catch (err) {
      sseSend(res, { type: 'warn', message: `样本分析失败（已跳过）：${err.message}` });
    }

    /* 7. 落盘与汇总 */
    const meta = {
      baseUrl: OPTIONS.baseUrl,
      studentContext: context,
      discovered: discover,
      concurrency,
      trigger: 'web-console',
      skipped: skipped.map(({ ep, reason }) => ({ key: ep.key, reason }))
    };

    // 终局重写：姓名、加密串等值可能在探测中途才发现，用完整集合再过一遍
    const finalPass = store.finalizeRedaction();
    if (store.redacting && finalPass.files > 0) {
      sseSend(res, {
        type: 'log',
        message: `脱敏回填 ${finalPass.files} 个文件（补上了中途才发现的敏感值）`
      });
    }

    // 同 probe.mjs：manifest 最后写，让其中的脱敏统计反映最终值
    const { file: reportFile } = store.writeMarkdown(meta);
    const { file: manifestFile, summary } = store.writeManifest(meta);
    const redaction = store.redactor.describe();

    state.lastRun = {
      at: new Date().toISOString(),
      durationMs: Date.now() - started,
      summary,
      manifestFile,
      reportFile,
      aborted: result.aborted
    };

    sseSend(res, {
      type: 'done',
      aborted: result.aborted,
      summary,
      redaction,
      durationMs: Date.now() - started,
      manifestFile,
      reportFile
    });
    res.end();
  } catch (err) {
    log(`探测异常：${err?.stack || err}`);
    sseSend(res, { type: 'error', message: `探测异常：${err?.message || err}` });
    res.end();
  } finally {
    state.running = false;
    state.stopRequested = false;
  }
}

/* ------------------------------------------------------------------ *
 * 启动
 * ------------------------------------------------------------------ */

fs.mkdirSync(OPTIONS.outDir, { recursive: true });

server.listen(OPTIONS.port, OPTIONS.host, () => {
  const url = `http://${OPTIONS.host}:${OPTIONS.port}/`;
  console.log('');
  console.log('  哈理工教务在线 · 网页探测控制台');
  console.log('  ─────────────────────────────────────────');
  console.log(`  控制台地址   ${url}`);
  console.log(`  教务基址     ${OPTIONS.baseUrl}`);
  console.log(`  样本输出     ${OPTIONS.outDir}`);
  console.log(`  GBK 解码器   ${getGbkLabel()}`);
  console.log('');
  console.log('  请在浏览器中打开上述地址，输入学号与密码完成登录后开始探测。');
  console.log('  按 Ctrl+C 停止服务。');
  console.log('');

  if (OPTIONS.open) {
    const cmd = process.platform === 'win32' ? 'cmd' : process.platform === 'darwin' ? 'open' : 'xdg-open';
    const args = process.platform === 'win32' ? ['/c', 'start', '', url] : [url];
    const child = spawn(cmd, args, { detached: true, stdio: 'ignore', windowsHide: true });
    child.on('error', () => {});
    child.unref();
  }
});

server.on('error', (err) => {
  if (err.code === 'EADDRINUSE') {
    console.error(`\n  端口 ${OPTIONS.port} 已被占用，请用 --port 指定其他端口。\n`);
  } else {
    console.error(`\n  服务启动失败：${err.message}\n`);
  }
  process.exit(1);
});
