/**
 * 探测结果落盘与 manifest 清单生成
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { getGbkLabel } from './http.mjs';
import { MODULES } from '../catalog.mjs';
import { slugify, makePreview } from './text.mjs';
import { renderPortalMarkdown } from './portal.mjs';
import { Redactor } from './redact.mjs';
import { collectFromText } from './harvest.mjs';

export { slugify };

/** 仓库根目录（用于校验输出目录的合法性） */
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

/**
 * 输出目录安全校验
 *
 * 探测产物是**运行期数据**，接口文档是**手工维护的成果**，两者必须隔离。
 * 这里硬性拦截指向 `docs/` 的输出目录，避免误操作覆盖接口文档
 * （`cleanOutput()` 会删除目录内容，一旦指错后果不可逆）。
 *
 * @param {string} outDir 用户指定的输出目录
 * @returns {string} 解析后的绝对路径
 */
export function assertSafeOutDir(outDir) {
  const resolved = path.resolve(outDir);
  const docsDir = path.join(REPO_ROOT, 'docs');

  if (isSameOrUnder(resolved, docsDir)) {
    throw new Error(
      `输出目录不能指向接口文档目录：${resolved}\n` +
        '探测产物请放在 tools/probe/output 或其他仓库外目录。'
    );
  }

  return resolved;
}

/**
 * 路径等同或位于其下
 *
 * Windows 的路径不区分大小写（`D:\` 与 `d:\` 是同一处），
 * 但字符串比较区分——实测中 `fileURLToPath` 产出大写盘符、
 * 用户输入小写盘符，直接 `startsWith` 会漏判，因此按平台决定是否归一化大小写。
 */
function isSameOrUnder(child, parent) {
  const normalize = (p) => (process.platform === 'win32' ? p.toLowerCase() : p);
  const c = normalize(child);
  const p = normalize(parent);

  return c === p || c.startsWith(p + path.sep);
}

/** 登录页特征（用于识别会话失效） */
const LOGIN_MARKERS = ['j_acegi_security_check', 'getCaptcha.do'];

/** 网关 / 容器级错误页特征 */
const GATEWAY_ERROR_PATTERNS = [
  /HTTP\s*(?:状态|Status)\s*\d{3}/i,
  /Status Report/i,
  /The requested resource is not available/i
];

/** 权限 / 会话类文案 */
const PERMISSION_TEXT = /您无权|无权限|没有权限|非法访问|会话超时|参数错误/;

/** URP「提示信息」错误页的标题（非常稳定且独特） */
const URP_ERROR_TITLE = /<title>\s*提示信息\s*<\/title>/i;

/** 判定为「小页面」的阈值：错误页通常很小，正常功能页往往很大 */
const SMALL_PAGE_BYTES = 6000;

/**
 * 判定响应是否为错误页
 *
 * 注意：URP 的错误页会以 **HTTP 200** 返回，因此不能只看状态码；
 * 但也不能仅凭 `class="error"` 判断 —— 教务系统的**正常表单页**
 * 会用 `<em class="error"></em>` 作为必填校验提示的占位元素，
 * 早期版本据此判定会把学籍信息这类大表单页误判成错误页。
 *
 * 现在的判定依据（按可靠性排序）：
 *   1. HTTP 状态码 >= 400
 *   2. 标题为「提示信息」（URP 错误页的固定标题）
 *   3. 网关/Tomcat 错误页特征
 *   4. 页面很小 且 含权限类文案
 */
export function detectErrorPage(status, text) {
  if (status >= 400) return true;

  const html = String(text || '');
  if (!html) return false;

  if (URP_ERROR_TITLE.test(html)) return true;
  if (GATEWAY_ERROR_PATTERNS.some((re) => re.test(html))) return true;

  // 权限文案只有出现在「小页面」上才说明是错误页
  if (html.length < SMALL_PAGE_BYTES && PERMISSION_TEXT.test(html)) return true;

  return false;
}

/** 响应体是否已回落到登录页（即会话失效） */
export function looksLikeLoginPage(text) {
  return LOGIN_MARKERS.some((marker) => String(text || '').includes(marker));
}

/**
 * 是否为「纯 JS 跳转页」
 *
 * 教务系统里有一类页面本身没有任何数据，只有一句
 * `location.href="真正目标.do?...&studentId=<加密串>"`，
 * 作用是把浏览器带去真正的功能页（如教学计划的 scheduleJump.jsp）。
 *
 * 这类页面有价值——它**揭示了带真实参数的目标地址**——但不能算「拿到数据」，
 * 否则会把跳转页误报成业务数据。判定特征：
 *   · 体积很小（跳转页通常只有几百字节）
 *   · 含 location.href / location.replace
 *   · 没有任何表格结构
 *
 * @param {string} text 响应正文
 * @returns {boolean}
 */
export function isRedirectStub(text) {
  const t = String(text || '');
  if (!t || t.length > 1500) return false;
  if (/<table[\s>]|<th[\s>]|<td[\s>]/i.test(t)) return false;
  return /location\.href\s*=|location\.replace\s*\(|window\.location/i.test(t);
}

/** 依据响应推断文件扩展名 */
function pickExtension(endpoint, response) {
  const contentType = (response.headers.get('content-type') || '').toLowerCase();

  if (endpoint.binary || contentType.startsWith('image/')) {
    if (contentType.includes('png')) return '.png';
    if (contentType.includes('jpeg') || contentType.includes('jpg')) return '.jpg';
    if (contentType.includes('gif')) return '.gif';
    if (contentType.includes('svg')) return '.svg';
    return '.bin';
  }
  if (contentType.includes('json')) return '.json';
  if (contentType.includes('javascript')) return '.js';
  if (contentType.includes('html')) return '.html';

  const cleanPath = endpoint.path.replace(/[?#].*$/, '').toLowerCase();
  if (cleanPath.endsWith('.jsdo')) return '.js';
  if (cleanPath.endsWith('.do') || cleanPath.endsWith('.jsp')) return '.html';
  return '.txt';
}

/**
 * 清空探测产物
 *
 * 多次运行会在 samples/ 里累积历史文件（每次运行的序号会重置，
 * 但上一轮生成的、本轮不再探测的接口样本会残留），
 * 导致 samples/ 与 decoded/ 数量对不上、清单与实际不符。
 * 该函数用于在探测前把产物恢复到干净状态。
 *
 * **不会删除 `session.json`**，以便继续复用已登录会话。
 *
 * @param {string} outDir 输出目录
 * @returns {string[]} 被清理的条目名
 */
export function cleanOutput(outDir) {
  // 会删除目录内容，因此必须先在安全校验里拦掉指向 docs/ 的情况
  const safeDir = assertSafeOutDir(outDir);
  const removed = [];

  for (const name of ['samples', 'decoded', 'portal']) {
    const dir = path.join(safeDir, name);
    if (fs.existsSync(dir)) {
      fs.rmSync(dir, { recursive: true, force: true });
      removed.push(`${name}/`);
    }
  }

  for (const name of ['manifest.json', 'manifest.md', 'analysis.json', 'analysis.md']) {
    const file = path.join(safeDir, name);
    if (fs.existsSync(file)) {
      fs.rmSync(file, { force: true });
      removed.push(name);
    }
  }

  return removed;
}

/** 探测样本仓库 */
export class SampleStore {
  /**
   * @param {Object} options
   * @param {string} options.outDir 输出根目录
   * @param {string} [options.runId] 本次运行标识（用于 manifest 记录）
   * @param {Redactor} [options.redactor] 脱敏器，默认启用脱敏
   */
  constructor({ outDir, runId, redactor }) {
    this.outDir = assertSafeOutDir(outDir);
    this.sampleDir = path.join(this.outDir, 'samples');
    /** 按正确字符集解码后的 UTF-8 副本，便于直接阅读/分析 */
    this.decodedDir = path.join(this.outDir, 'decoded');
    /** 门户页分析产物 */
    this.portalDir = path.join(this.outDir, 'portal');
    this.runId = runId || new Date().toISOString().replace(/[:.]/g, '-');
    this.counter = 0;
    this.entries = [];
    this.artifacts = [];
    /**
     * 参数收割结果：从响应中提取的「接口 + 真实参数」组合。
     * 在脱敏前收集，供多轮探测的第二轮带参补测使用。
     * @type {Array<{ruleId:string, candidate:Object}>}
     */
    this.harvested = [];
    /** 收割去重（跨调用共享，避免同一参数组合被重复收集） */
    this.harvestSeen = new Set();
    /** 脱敏器：所有落盘内容统一经过它 */
    this.redactor = redactor || new Redactor({ enabled: true });
    fs.mkdirSync(this.sampleDir, { recursive: true });
    fs.mkdirSync(this.decodedDir, { recursive: true });
  }

  /** 是否处于脱敏模式 */
  get redacting() {
    return this.redactor.active;
  }

  /** 落盘前的统一脱敏出口 */
  clean(content) {
    return this.redactor.redact(content);
  }

  /** 分配下一个序号 */
  nextIndex() {
    this.counter += 1;
    return this.counter;
  }

  /**
   * 写入一个产物文件（相对 outDir），并记录到 artifacts 列表
   * @param {string} relPath 相对输出目录的路径
   * @param {string|Buffer} content
   * @returns {string} 归一化后的相对路径
   */
  writeArtifact(relPath, content) {
    const abs = path.join(this.outDir, relPath);
    fs.mkdirSync(path.dirname(abs), { recursive: true });

    // 文本类产物统一脱敏；Buffer 原样写入（调用方已确认可安全落盘）
    const payload = Buffer.isBuffer(content) ? content : this.clean(String(content));
    fs.writeFileSync(abs, payload);

    const normalized = relPath.split(path.sep).join('/');
    this.artifacts.push(normalized);
    return normalized;
  }

  /**
   * 写出门户页分析产物（可读 HTML + 结构化 JSON + Markdown 报告 + 内联脚本）
   *
   * 产物目的：把「登录后门户页里隐藏的按钮 / 表单 / 地址」摊开成可读文件，
   * 避免继续按命名规律盲测接口。
   *
   * @param {Object} analysis analyzePortal() 的返回值
   * @param {string} readableHtml 一行一标签重排后的 HTML
   */
  writePortal(analysis, readableHtml) {
    const base = slugify(analysis.source || analysis.url || 'portal');
    const result = {
      html: this.writeArtifact(`portal/${base}.utf8.html`, readableHtml),
      analysisJson: this.writeArtifact(
        `portal/${base}.analysis.json`,
        JSON.stringify(analysis, null, 2)
      ),
      analysisMarkdown: this.writeArtifact(
        `portal/${base}.analysis.md`,
        renderPortalMarkdown(analysis)
      ),
      inlineScripts: null
    };

    if (analysis.inlineScripts?.length) {
      const code = analysis.inlineScripts
        .map((s) => `/* ===== 内联脚本 #${s.index} ===== */\n${s.code}`)
        .join('\n\n');
      result.inlineScripts = this.writeArtifact(`portal/${base}.inline-scripts.js`, code);
    }

    return result;
  }

  /**
   * 保存一条探测结果
   *
   * @param {Object} endpoint 接口定义
   * @param {Object} response HttpClient 返回对象
   * @param {Object} [extra]  附加字段（如 context、error）
   * @returns {Object} manifest 条目
   */
  save(endpoint, response, extra = {}) {
    const index = this.nextIndex();
    const seq = String(index).padStart(4, '0');
    const ext = pickExtension(endpoint, response);
    const fileName = `${seq}-${endpoint.key.replace(/^discovered\./, 'd-')}${ext}`
      .replace(/[^A-Za-z0-9._-]/g, '-');
    const relFile = path.posix.join('samples', fileName);
    const absFile = path.join(this.outDir, relFile);

    const text = response.text || '';
    const isBinary =
      Boolean(endpoint.binary) || /^image\//i.test(response.contentType || '');

    if (!isBinary && text) {
      // ① 参数收割：必须在脱敏**之前**——加密串是敏感值，落盘后只剩占位符，
      //    只有在这里才能拿到真实参数供第二轮带参补测
      this.harvested.push(...collectFromText(text, this.harvestSeen));

      // ② 敏感值收集（加密串、学号、真实姓名），后续所有落盘内容都会屏蔽它们。
      //    姓名的来源是「我的信息」页，通常在中途才被探测到，
      //    因此结束时会用 `finalizeRedaction()` 把先落盘的文件重写一遍。
      this.redactor.harvest(text);
    }

    /** 二进制内容是否被跳过落盘 */
    let binarySkipped = false;
    /** 实际写入的样本文件与解码副本（二进制会被替换为说明文件） */
    let relFileForEntry = relFile;
    let relDecodedForEntry = null;

    if (isBinary) {
      // 图片等二进制响应**不保存副本**：
      // 学生照片、验证码这类内容本身就是个人数据，且无法在不破坏文件的前提下脱敏。
      // 改为写入一份文本说明，保留尺寸与哈希以供核对。
      const digest = crypto
        .createHash('sha256')
        .update(response.buffer)
        .digest('hex')
        .slice(0, 16);
      const stubName = `${seq}-${endpoint.key.replace(/^discovered\./, 'd-')}.txt`
        .replace(/[^A-Za-z0-9._-]/g, '-');
      const relStub = path.posix.join('samples', stubName);
      const stub = [
        '[二进制响应，未落盘]',
        `content-type: ${response.contentType || response.headers.get('content-type') || '-'}`,
        `bytes: ${response.buffer.length}`,
        `sha256[:16]: ${digest}`,
        '',
        '为保护隐私，图片等二进制内容不保存副本。'
      ].join('\n');

      fs.writeFileSync(path.join(this.outDir, relStub), stub, 'utf8');
      binarySkipped = true;
      relFileForEntry = relStub;
      relDecodedForEntry = null;
    } else if (text) {
      // 脱敏需要「解码 → 替换 → 重新编码」，而 Node 没有 GBK 编码器，
      // 因此脱敏模式下两份产物统一写成 UTF-8；原始编码记录在 manifest.charset。
      const cleaned = this.clean(text);
      fs.writeFileSync(absFile, cleaned, 'utf8');

      relDecodedForEntry = path.posix.join('decoded', fileName);
      fs.writeFileSync(path.join(this.outDir, relDecodedForEntry), cleaned, 'utf8');
    } else {
      fs.writeFileSync(absFile, response.buffer);
    }

    const haystack = text.toLowerCase();
    const markersHit = (endpoint.markers || []).filter((m) =>
      haystack.includes(String(m).toLowerCase())
    );
    const looksLikeLogin = LOGIN_MARKERS.some((m) => text.includes(m));
    const isErrorPage = detectErrorPage(response.status, text);
    const redirectStub = isRedirectStub(text);

    // 只有「请求成功 + 有响应体 + 不是错误页 + 不是登录页 + 不是跳转页 +
    // （命中关键字 或 该接口本就无关键字约束）」才算真正拿到了业务数据。
    // 排除项都来自实测教训：
    //   · 404 页面上会偶然出现关键字 → 误报「有数据」
    //   · 0 字节的 200 响应（如需要参数的接口）→ 误报「有数据」
    //   · 纯 JS 跳转页（如 scheduleJump.jsp）→ 误报「有数据」
    const hasBody = response.buffer.length > 0;
    const hitMarkers =
      Boolean(response.ok) &&
      hasBody &&
      !isErrorPage &&
      !looksLikeLogin &&
      !redirectStub &&
      (markersHit.length > 0 || (endpoint.markers || []).length === 0);

    /** @type {Object} */
    const entry = {
      index,
      key: endpoint.key,
      name: endpoint.name,
      module: endpoint.module,
      moduleName: MODULES[endpoint.module]?.name || endpoint.module,
      path: endpoint.path,
      method: endpoint.method,
      params: endpoint.params || {},
      confidence: endpoint.confidence || 'unknown',
      publicAccess: Boolean(endpoint.public),
      mutating: Boolean(endpoint.mutating),
      parser: endpoint.parser || null,
      url: response.url,
      requestedUrl: response.requestedUrl,
      status: response.status,
      statusText: response.statusText,
      ok: Boolean(response.ok),
      contentType: response.contentType || response.headers.get('content-type') || '',
      charset: response.charset || '',
      bytes: response.buffer.length,
      elapsedMs: response.elapsedMs,
      redirects: response.redirects || [],
      markers: endpoint.markers || [],
      markersHit,
      hitMarkers,
      looksLikeLogin,
      isErrorPage,
      redirectStub,
      file: relFileForEntry,
      decodedFile: relDecodedForEntry,
      binarySkipped,
      preview: isBinary ? '(二进制内容，未落盘)' : this.clean(makePreview(text)),
      error: response.error || null,
      note: this.clean(endpoint.note || '') || null,
      discoveredFrom: endpoint.discoveredFrom || null,
      ...extra
    };

    this.entries.push(entry);
    return entry;
  }

  /** 汇总统计 */
  summarize() {
    const byModule = {};
    const notFoundKeys = [];
    const errorPageKeys = [];
    const sessionSuspects = [];

    for (const entry of this.entries) {
      const bucket = (byModule[entry.module || 'unknown'] ||= {
        moduleName: entry.moduleName || entry.module || '未分类',
        total: 0,
        ok: 0,
        data: 0,
        notFound: 0
      });
      bucket.total += 1;
      if (entry.ok) bucket.ok += 1;
      if (entry.hitMarkers) bucket.data += 1;

      if (entry.status === 404) {
        bucket.notFound += 1;
        notFoundKeys.push(entry.key);
      } else if (entry.isErrorPage) {
        errorPageKeys.push(entry.key);
      }

      if (entry.looksLikeLogin && !entry.publicAccess) sessionSuspects.push(entry.key);
    }

    const ok = this.entries.filter((e) => e.ok).length;
    const protectedEntries = this.entries.filter((e) => !e.publicAccess);

    // 单条接口回落到登录页往往只是该路径无权限，只有成规模回落才算会话真的失效
    const loginExpired =
      sessionSuspects.length >= 2 &&
      sessionSuspects.length >= Math.max(2, protectedEntries.length * 0.25);

    return {
      total: this.entries.length,
      ok,
      failed: this.entries.length - ok,
      withData: this.entries.filter((e) => e.hitMarkers).length,
      notFound: notFoundKeys.length,
      notFoundKeys,
      errorPages: errorPageKeys.length,
      errorPageKeys,
      loginExpired,
      sessionSuspects,
      byModule
    };
  }

  /** 统计各接口实际使用的字符集 */
  charsetStats() {
    const stats = {};
    for (const entry of this.entries) {
      if (!entry.charset) continue;
      stats[entry.charset] = (stats[entry.charset] || 0) + 1;
    }
    return stats;
  }

  /**
   * 终局脱敏重写
   *
   * 真实姓名、内部 ID 等值是在探测**中途**才从页面里发现的，
   * 在它们之前落盘的样本无法在写入时就替换掉。因此探测结束后，
   * 用完整的已知值集合把全部文本产物重写一遍，确保没有遗漏。
   *
   * @returns {{files:number, replacements:number}}
   */
  finalizeRedaction() {
    if (!this.redactor.active) return { files: 0, replacements: 0 };

    const dirs = ['samples', 'decoded', 'portal'];
    let files = 0;

    for (const dir of dirs) {
      const abs = path.join(this.outDir, dir);
      if (!fs.existsSync(abs)) continue;

      for (const name of fs.readdirSync(abs)) {
        const file = path.join(abs, name);
        if (!fs.statSync(file).isFile()) continue;
        if (!/\.(html?|js|json|txt|md)$/i.test(name)) continue;

        const original = fs.readFileSync(file, 'utf8');
        const cleaned = this.redactor.redactAll(original);
        if (cleaned !== original) {
          fs.writeFileSync(file, cleaned, 'utf8');
          files += 1;
        }
      }
    }

    // 返回本次实际被改写的文件数；累计替换次数由 describe() 提供
    return { files, replacements: this.redactor.stats().total };
  }

  /** 写出 manifest.json */
  writeManifest(meta = {}) {
    const summary = this.summarize();
    const manifest = {
      generator: 'BetterHRBUST probe',
      runId: this.runId,
      generatedAt: new Date().toISOString(),
      gbkFallback: getGbkLabel(),
      charsets: this.charsetStats(),
      nodeVersion: process.version,
      redaction: this.redactor.describe(),
      ...meta,
      artifacts: this.artifacts,
      summary,
      entries: this.entries
    };

    const file = path.join(this.outDir, 'manifest.json');
    const json = JSON.stringify(manifest, null, 2);

    // 整体过一遍脱敏：条目里的 url / preview / note 都可能带上真实值。
    // 占位符不含引号与反斜杠，因此替换后仍是合法 JSON。
    const cleaned = this.clean(json);
    try {
      JSON.parse(cleaned);
    } catch {
      throw new Error('脱敏后 manifest.json 不是合法 JSON，请检查已知值是否含引号');
    }

    fs.writeFileSync(file, cleaned, 'utf8');
    return { file, manifest, summary };
  }

  /** 写出人类可读的汇总 Markdown */
  writeMarkdown(meta = {}) {
    const summary = this.summarize();
    const charsets = this.charsetStats();
    const lines = [
      '# 探测结果汇总',
      '',
      `- 运行时间：${new Date().toLocaleString('zh-CN')}`,
      `- 教务基址：${meta.baseUrl || '-'}`,
      `- GBK 兜底解码器：${getGbkLabel()}`,
      `- 实际编码分布：${Object.entries(charsets).map(([k, v]) => `${k}=${v}`).join('  ') || '-'}`,
      `- 接口总数：${summary.total}（成功 ${summary.ok} / 失败 ${summary.failed}）`,
      `- 拿到业务数据：${summary.withData}`,
      `- 路径不存在（404）：${summary.notFound}`,
      `- 返回错误页：${summary.errorPages}`,
      this.redactor.active
        ? '- 脱敏：已启用（产物统一为 UTF-8，图片不落盘）'
        : '- 脱敏：**已关闭**，产物含真实隐私数据，请勿分享',
      summary.loginExpired
        ? `- ⚠️ 疑似会话失效的接口：${summary.sessionSuspects.join(', ')}`
        : '',
      '',
      '## 按模块统计',
      '',
      '| 模块 | 探测数 | HTTP 成功 | 有数据 | 404 |',
      '| --- | ---: | ---: | ---: | ---: |',
      ...Object.values(summary.byModule).map(
        (b) => `| ${b.moduleName} | ${b.total} | ${b.ok} | ${b.data} | ${b.notFound} |`
      ),
      '',
      '## 明细',
      '',
      '| # | 接口 key | 方法 | 路径 | 状态 | 编码 | 大小 | 结论 | 命中关键字 | 样本文件 |',
      '| ---: | --- | --- | --- | ---: | --- | ---: | --- | --- | --- |'
    ];

    for (const e of this.entries) {
      const status = e.error ? 'ERR' : String(e.status);
      let verdict;
      if (e.error) verdict = '网络错误';
      else if (e.status === 404) verdict = '路径不存在';
      else if (e.isErrorPage) verdict = '错误页/无权限';
      else if (e.looksLikeLogin) verdict = '回落到登录页';
      else if (e.hitMarkers) verdict = '✅ 有数据';
      else verdict = '无特征';

      lines.push(
        `| ${e.index} | \`${e.key}\` | ${e.method} | \`${e.path}\` | ${status} | ` +
          `${e.charset || '-'} | ${e.bytes} | ${verdict} | ` +
          `${e.hitMarkers ? e.markersHit.join(' / ') || '—' : '—'} | \`${e.file}\` |`
      );
    }

    if (summary.notFoundKeys.length > 0) {
      lines.push('', '## 路径不存在（404）的接口', '');
      lines.push('以下路径在当前教务系统版本中不存在，建议从目录中移除或改为自动发现：', '');
      lines.push(
        summary.notFoundKeys.map((k) => `\`${k}\``).join('、')
      );
    }

    lines.push('');
    const file = path.join(this.outDir, 'manifest.md');
    fs.writeFileSync(file, this.clean(lines.filter(Boolean).join('\n')), 'utf8');
    return { file, summary };
  }
}
