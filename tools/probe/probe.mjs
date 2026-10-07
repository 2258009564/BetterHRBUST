#!/usr/bin/env node
/**
 * 哈理工教务在线 · 接口探测脚本
 *
 * 流程：
 *   1. 建立会话 → 拉取验证码 → 交互式登录
 *   2. 校验会话并提取学生上下文（学号 / 学年 / 学期）
 *   3. 从菜单页自动发现接口 + 叠加内置接口目录
 *   4. 批量请求并把原始响应落盘，生成 manifest.json / manifest.md
 *
 * 运行环境：Node 18+，必须处于校园网或学校 VPN。
 *
 * 用法：
 *   node tools/probe/probe.mjs --user 2104010218
 *   node tools/probe/probe.mjs --only score.* --skip-login
 *   node tools/probe/probe.mjs --list
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { getGbkLabel, toRelativePath } from './lib/http.mjs';
import {
  fetchCaptcha,
  checkCaptcha,
  submitLogin,
  verifySession,
  saveSession,
  loadSession,
  openWithSystemViewer,
  isLoginPage
} from './lib/auth.mjs';
import { SampleStore, cleanOutput } from './lib/dumper.mjs';
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
import {
  assertNodeVersion,
  parseArgv,
  loadConfigFile,
  mergeOptions,
  applyEnv,
  ask,
  askSecret,
  confirm,
  hasDependency,
  HELP_TEXT,
  DEFAULT_OPTIONS
} from './lib/cli.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_OUT_DIR = path.join(HERE, 'output');

/* ------------------------------------------------------------------ *
 * 输出工具
 * ------------------------------------------------------------------ */

const color = {
  reset: '\u001b[0m',
  dim: '\u001b[2m',
  red: '\u001b[31m',
  green: '\u001b[32m',
  yellow: '\u001b[33m',
  blue: '\u001b[34m',
  cyan: '\u001b[36m'
};

function banner(text) {
  console.log(`\n${color.cyan}${text}${color.reset}`);
}

function step(text) {
  console.log(`${color.blue}▸${color.reset} ${text}`);
}

function info(text) {
  console.log(`  ${text}`);
}

function warn(text) {
  console.log(`  ${color.yellow}!${color.reset} ${text}`);
}

function fail(text) {
  console.log(`  ${color.red}✗${color.reset} ${text}`);
}

function ok(text) {
  console.log(`  ${color.green}✓${color.reset} ${text}`);
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)}MB`;
}

/* ------------------------------------------------------------------ *
 * 主流程
 * ------------------------------------------------------------------ */

async function main() {
  assertNodeVersion(18);

  const { options: parsedOptions, configFile, unknown } = parseArgv(process.argv.slice(2));
  const fileConfig = loadConfigFile(configFile);
  const options = applyEnv(mergeOptions({ options: parsedOptions }, fileConfig));

  if (unknown.length > 0) {
    warn(`未识别的参数已忽略：${unknown.join(', ')}`);
  }

  if (options.help) {
    console.log(HELP_TEXT);
    return;
  }

  if (options.list) {
    printCatalogList();
    return;
  }

  /* ---- 仅分析模式：不联网，复用已有样本重新做结构分析 ---- */
  if (options.analyzeOnly) {
    const analyzeOutDir = path.isAbsolute(options.outDir)
      ? options.outDir
      : path.resolve(HERE, options.outDir || DEFAULT_OPTIONS.outDir);
    const manifestPath = path.join(analyzeOutDir, 'manifest.json');

    banner('样本结构分析（--analyze-only）');

    if (!fs.existsSync(manifestPath)) {
      fail(`找不到 ${manifestPath}，请先执行一次完整探测`);
      process.exitCode = 1;
      return;
    }

    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    const store = new SampleStore({ outDir: analyzeOutDir });
    store.entries = manifest.entries || [];

    info(`已加载 ${store.entries.length} 条探测记录（来自 ${manifest.generatedAt || '未知时间'}）`);

    const result = analyzeStoredSamples(store);
    ok(`分析报告   ${result.markdownFile}`);
    ok(`结构化数据 ${result.jsonFile}`);
    info(`共分析 ${result.items.length} 个样本页面`);

    const withTables = result.items.filter((i) => i.analysis.tables.length > 0);
    if (withTables.length > 0) {
      console.log('');
      info(`提取到数据表格的页面（${withTables.length} 个）：`);
      for (const item of withTables.slice(0, 20)) {
        const t = item.analysis.tables[0];
        info(`  · ${item.key}  →  ${t.headers.slice(0, 6).join(' | ')}${t.headers.length > 6 ? ' …' : ''}`);
      }
    }
    return;
  }

  const outDir = path.isAbsolute(options.outDir)
    ? options.outDir
    : path.resolve(HERE, options.outDir || DEFAULT_OPTIONS.outDir);

  banner('哈理工教务在线 · 接口探测');
  info(`基址        ${options.baseUrl}`);
  info(`输出目录    ${outDir}`);
  info(`GBK 解码器  ${getGbkLabel()}`);
  info(`Node 版本   ${process.version}`);

  if (!hasDependency('iconv-lite')) {
    info('提示        未安装 iconv-lite（可选）。脚本使用 Node 内置 GBK 解码，功能不受影响。');
  }

  const client = createClient({
    baseUrl: options.baseUrl,
    timeout: options.timeout,
    verbose: options.verbose,
    log: (msg) => console.log(`${color.dim}${msg}${color.reset}`)
  });

  /* -------- 1. 连通性检测 -------- */
  banner('① 连通性检测');
  const rootRes = await client.get('');
  if (!rootRes.ok) {
    fail(`无法访问教务系统：${rootRes.error || `HTTP ${rootRes.status}`}`);
    info('请确认：');
    info('  · 已连接校园网或学校 VPN');
    info(`  · 基址正确（当前：${options.baseUrl}）`);
    info('  · 浏览器能正常打开该地址');
    process.exitCode = 1;
    return;
  }
  if (!isLoginPage(rootRes.text)) {
    ok('教务系统可达（当前会话似乎已登录，将继续验证）');
  } else {
    ok(`教务系统可达（HTTP ${rootRes.status}，${formatBytes(rootRes.buffer.length)}）`);
  }

  if (options.clean) {
    const removed = cleanOutput(outDir);
    if (removed.length > 0) {
      info(`已清空旧产物（保留会话文件）：${removed.join('、')}`);
    }
  }

  const store = new SampleStore({ outDir });
  store.save(
    { key: 'auth.root', name: '登录页 / 站点入口', module: 'auth', path: '', method: 'GET', public: true, markers: ['j_acegi_security_check'] },
    rootRes
  );

  /* -------- 2. 登录 -------- */
  banner('② 登录教务在线');

  const sessionFile = path.join(outDir, 'session.json');
  let session = null;

  if (options.skipLogin) {
    session = loadSession(client, sessionFile);
    if (!session) {
      warn(`未找到会话文件 ${sessionFile}，将走完整登录流程`);
    } else {
      info(`已加载会话（保存于 ${session.savedAt}）`);
    }
  }

  let identity = { loggedIn: false, studentId: '', year: '', term: '' };

  if (client.jar.size > 0 && !session) {
    identity = await verifySession(client);
  }

  if (session) {
    identity = await verifySession(client);
    // 复用会话时沿用上次记录的登录落点
    if (session.landingPath) identity.landingPath = session.landingPath;
    if (identity.loggedIn) {
      ok(`会话有效，学号 ${identity.studentId || '(未知)'}`);
      if (session.landingPath) info(`记忆的登录落点  ${session.landingPath}`);
    } else {
      warn('会话已失效，需要重新登录');
    }
  }

  if (!identity.loggedIn) {
    identity = await interactiveLogin(client, options, outDir, sessionFile);
    if (!identity.loggedIn) {
      fail('登录失败，探测终止');
      process.exitCode = 1;
      return;
    }
  }

  const context = {
    studentId: identity.studentId,
    year: identity.year,
    term: identity.term
  };
  info(`学生上下文  studentId=${context.studentId || '?'}  year=${context.year || '?'}  term=${context.term || '?'}`);
  if (!context.studentId || !context.year || !context.term) {
    warn('未能完整解析学生上下文，依赖这些参数的接口将被跳过');
  }

  /* -------- 3. 门户页捕获与分析 -------- */
  banner('③ 门户页捕获与分析');
  let portal = { ok: false, analysis: null, files: null };

  if (options.portal === false) {
    info('已按 --no-portal 跳过门户页捕获');
  } else {
    step('抓取登录后门户页并做结构化分析...');
    portal = await capturePortal(client, store, {
      landingPath: identity.landingPath,
      log: (msg) => console.log(`${color.dim}${msg}${color.reset}`)
    });

    if (portal.ok) {
      const s = portal.summary;
      ok(
        `共抓取 ${s.pages} 个门户页面（最深 ${s.maxDepth} 层），` +
          `聚合 表单 ${s.forms} / 按钮 ${s.buttons} / 链接 ${s.links} / ` +
          `疑似接口 ${s.inlineUrls} / moduleId ${s.moduleIds}`
      );
      info('产物（每个页面一组）：');
      for (const page of portal.pages) {
        info(`  · [深度 ${page.depth}] ${page.path}`);
        info(`      ${page.files.html}`);
      }
      if (portal.inlineUrls.length > 0) {
        info('聚合得到的疑似接口地址（前 15 条）：');
        for (const item of portal.inlineUrls.slice(0, 15)) {
          info(`  · ${item.url}`);
        }
      }
      if (portal.moduleIds.length > 0) {
        info(`moduleId（${portal.moduleIds.length} 个）：${portal.moduleIds.join(', ')}`);
      }
    }
  }

  /* -------- 4. 接口发现 -------- */
  banner('④ 接口发现');
  let endpoints = batchableEndpoints(CATALOG);

  if (options.discover) {
    step('扫描菜单页提取链接...');
    const found = await discoverAndMerge(
      client,
      CATALOG,
      (msg) => console.log(`${color.dim}${msg}${color.reset}`),
      portal.ok
        ? {
            extraLinks: portal.candidatePaths,
            extraModuleIds: portal.moduleIds
          }
        : {}
    );
    endpoints = batchableEndpoints(found.endpoints);
    ok(
      `自动发现 ${found.discoveredCount} 个链接` +
        `（新增 ${found.addedCount} 个，匹配内置 ${found.matchedCount} 个）`
    );
  } else {
    info('已按 --no-discover 跳过菜单自动发现');
  }

  const { candidates, skipped } = buildProbePlan(endpoints, {
    only: options.only,
    allowMutating: options.allowMutating,
    context
  });

  const stats = summarizeCatalog();
  info(`内置目录 ${stats.total} 个接口（${Object.entries(stats.byConfidence).map(([k, v]) => `${k}:${v}`).join('  ')}）`);
  info(`本次待探测 ${candidates.length} 个，跳过 ${skipped.length} 个`);

  if (skipped.length > 0 && options.verbose) {
    for (const { ep, reason } of skipped) {
      info(`  · 跳过 ${ep.key}：${reason}`);
    }
  }

  if (options.dryRun) {
    banner('⑤ 探测计划（--dry-run，不发送请求）');
    for (const ep of candidates) {
      console.log(`  · [${MODULES[ep.module]?.name || ep.module}] ${ep.method} ${ep.path}  (${ep.key})`);
    }
    for (const { ep, reason } of skipped) {
      console.log(`  ${color.dim}· [跳过] ${ep.key}：${reason}${color.reset}`);
    }
    return;
  }

  if (candidates.length === 0) {
    warn('没有可探测的接口，请检查 --only 过滤条件');
    return;
  }

  /* -------- 5. 批量探测（多轮递进） -------- */
  banner('⑤ 批量探测');

  let expandedCount = 0;
  const roundResults = await probeWithExpansion({
    client,
    endpoints: candidates,
    context,
    store,
    concurrency: options.concurrency,
    maxRounds: options.depth,
    log: (msg) => console.log(`${color.dim}${msg}${color.reset}`),
    onRound: ({ round, probed, total }) => {
      if (round > 1) {
        ok(`第 ${round} 轮完成：本轮探测 ${probed} 个，累计 ${total} 个`);
      }
    },
    onProgress: ({ round, done, total: count, entry, sessionAlert }) => {
      const s = entry;
      const seq = `${String(done).padStart(String(count).length, ' ')}/${count}`;
      const statusText = s.ok
        ? `${color.green}${s.status}${color.reset}`
        : `${color.red}${s.error ? 'ERR' : s.status}${color.reset}`;
      const flag =
        s.looksLikeLogin && !s.publicAccess
          ? `${color.yellow}[会话失效]${color.reset}`
          : s.hitMarkers
            ? `${color.green}[有数据]${color.reset}`
            : `${color.dim}[无特征]${color.reset}`;

      console.log(
        `  [R${round ?? 1} ${seq}] ${statusText} ${formatBytes(s.bytes).padStart(8, ' ')} ` +
          `${String(s.elapsedMs).padStart(6, ' ')}ms  ${s.key.padEnd(30, ' ')} ${flag}`
      );

      if (sessionAlert) {
        warn('会话可能已过期，后续接口结果可能不可信。可重新运行并重新登录。');
      }
    }
  });
  expandedCount = roundResults.addedTotal;

  /* -------- 6. 样本结构分析 -------- */
  banner('⑥ 样本结构分析');
  const analysis = analyzeStoredSamples(store);
  ok(`分析报告   ${analysis.markdownFile}`);
  ok(`结构化数据 ${analysis.jsonFile}`);
  info(`共分析 ${analysis.items.length} 个样本页面`);

  /* -------- 7. 落盘与汇总 -------- */
  banner('⑦ 结果落盘');
  const meta = {
    baseUrl: options.baseUrl,
    studentContext: context,
    discovered: options.discover,
    concurrency: options.concurrency,
    skipped: skipped.map(({ ep, reason }) => ({ key: ep.key, reason }))
  };
  const { file: manifestFile, summary } = store.writeManifest(meta);
  const { file: mdFile } = store.writeMarkdown(meta);

  ok(`原始样本   ${store.sampleDir}`);
  ok(`接口清单   ${manifestFile}`);
  ok(`汇总报告   ${mdFile}`);

  console.log('');
  console.log(`  接口总数    ${summary.total}`);
  console.log(`  HTTP 成功   ${color.green}${summary.ok}${color.reset}`);
  console.log(`  请求失败    ${summary.failed > 0 ? color.red : color.dim}${summary.failed}${color.reset}`);
  console.log(`  拿到数据    ${color.green}${summary.withData}${color.reset}`);
  console.log(`  路径不存在  ${summary.notFound > 0 ? color.yellow : color.dim}${summary.notFound}${color.reset}`);
  console.log(`  错误页      ${summary.errorPages > 0 ? color.yellow : color.dim}${summary.errorPages}${color.reset}`);
  console.log('');
  for (const bucket of Object.values(summary.byModule)) {
    console.log(
      `    ${bucket.moduleName.padEnd(16, ' ')} 探测 ${String(bucket.total).padStart(3, ' ')} · ` +
        `成功 ${String(bucket.ok).padStart(3, ' ')} · 有数据 ${String(bucket.data).padStart(3, ' ')}` +
        ` · 404 ${String(bucket.notFound).padStart(3, ' ')}`
    );
  }

  if (summary.notFoundKeys.length > 0) {
    console.log('');
    warn(`以下路径不存在，建议从 catalog.mjs 移除：${summary.notFoundKeys.join('、')}`);
  }

  if (summary.loginExpired) {
    warn(
      `疑似会话失效的接口：${summary.sessionSuspects.join('、')}，建议重新运行并重新登录后再生成结论。`
    );
  }

  console.log('');
  info('下一步：把 manifest.json 与 samples/ 目录交给 AI，即可据此编写接口文档与 SDK。');
}

/* ------------------------------------------------------------------ *
 * 登录交互
 * ------------------------------------------------------------------ */

async function interactiveLogin(client, options, outDir, sessionFile) {
  step('获取图形验证码...');

  const captchaFile = path.join(outDir, 'captcha.png');
  const captcha = await fetchCaptcha(client, captchaFile);
  if (!captcha.ok) {
    fail(`验证码获取失败：${captcha.error || `HTTP ${captcha.status}`}`);
    return { loggedIn: false, studentId: '', year: '', term: '' };
  }
  ok(`验证码已保存：${captcha.file}（${formatBytes(captcha.size)}）`);

  if (options.openCaptcha) {
    const opened = openWithSystemViewer(captcha.file);
    if (opened) info('已尝试用系统默认看图工具打开，请查看后回到终端输入。');
    else info('未能自动打开图片，请手动打开上面路径查看。');
  }

  let username = options.username;
  if (!username) {
    username = await ask('  学号：');
  } else {
    info(`学号        ${username}`);
  }

  let password = options.password;
  if (!password) {
    password = await askSecret('  密码（输入时不回显）：');
  } else {
    info('密码        已从参数/环境变量读取');
  }

  if (!username || !password) {
    fail('缺少学号或密码');
    return { loggedIn: false, studentId: '', year: '', term: '' };
  }

  let captchaCode = options.captcha;
  if (!captchaCode) {
    captchaCode = await ask('  验证码（图片中的 4 位字符）：');
  }

  // 与教务站点自身逻辑保持一致：先调用 checkCaptcha.do 校验验证码，再提交 Acegi 表单
  step('校验验证码...');
  const captchaCheck = await checkCaptcha(client, String(captchaCode).trim());
  if (captchaCheck.valid === false) {
    fail('验证码不正确或已过期，请重新运行并输入新的验证码');
    return { loggedIn: false, studentId: '', year: '', term: '' };
  }
  if (captchaCheck.valid === true) {
    ok('验证码校验通过');
  } else {
    warn('checkCaptcha.do 未返回明确结果，交由登录接口最终判定');
  }

  step('提交登录...');
  const result = await submitLogin(client, { username, password, captcha: captchaCode });

  if (!result.success) {
    fail(result.message);
    const retry = options.assumeYes ? false : await confirm('  是否重试登录（换一张验证码）？', { defaultValue: true });
    if (retry) {
      return interactiveLogin(client, { ...options, captcha: '' }, outDir, sessionFile);
    }
    return { loggedIn: false, studentId: '', year: '', term: '' };
  }

  const identity = await verifySession(client);
  if (!identity.loggedIn) {
    fail('登录返回成功但会话校验未通过，可能是教务系统改版或需要额外验证');
    return identity;
  }

  // 登录成功后的跳转落点，通常就是门户页（如 index_new.jsp）
  const landingPath = toRelativePath(result.finalUrl, client.baseUrl);

  ok(`登录成功（学号 ${identity.studentId || username}）`);
  if (landingPath) info(`登录落点  ${landingPath}`);
  saveSession(client, sessionFile, { studentId: identity.studentId, landingPath });
  info(`会话已保存到 ${sessionFile}，下次可用 --skip-login 复用`);

  return { ...identity, landingPath };
}

/* ------------------------------------------------------------------ *
 * 目录打印
 * ------------------------------------------------------------------ */

function printCatalogList() {
  const stats = summarizeCatalog();
  banner(`内置接口目录（共 ${stats.total} 个）`);
  let currentModule = '';
  for (const ep of CATALOG) {
    if (ep.module !== currentModule) {
      currentModule = ep.module;
      console.log('');
      console.log(`${color.cyan}${MODULES[ep.module]?.name || ep.module}${color.reset}`);
    }
    const flags = [
      ep.confidence.padEnd(9, ' '),
      ep.mutating ? 'mutating' : '        ',
      ep.skipInBatch ? 'skip-batch' : '          '
    ].join(' ');
    console.log(`  ${color.dim}${flags}${color.reset} ${ep.method.padEnd(4)} ${ep.key.padEnd(26)} ${ep.path}`);
  }
  console.log('');
  console.log(`可靠度分布：${Object.entries(stats.byConfidence).map(([k, v]) => `${k}=${v}`).join('  ')}`);
  console.log('');
  console.log('提示：自动发现功能会在登录后扫描菜单页，补充内置目录未覆盖的接口。');
}

main().catch((err) => {
  console.error(`\n${color.red}探测脚本异常终止：${err?.stack || err}${color.reset}`);
  process.exitCode = 1;
});
