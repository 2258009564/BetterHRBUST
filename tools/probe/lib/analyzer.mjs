/**
 * 样本批量分析器
 *
 * 目的：一次探测只能发现「入口页里直接写着的地址」，但真实系统里
 * 每个功能页自身还会再引用更深的接口（表单 action、按钮 onclick、
 * ajax url、导出链接……）。把这些页面全部做结构化分析，
 * 就能把「一轮探测」变成「多轮递进发现」，而不是靠人肉逐个读 HTML。
 *
 * 本模块在 Node 侧工作，不依赖 DOM。
 */

import fs from 'node:fs';
import path from 'node:path';
import { analyzePortal, extractInlineUrls, BUSINESS_EXT } from './portal.mjs';
import { inferModule } from './discover.mjs';
import { stripTags, stripHtmlComments } from './text.mjs';

/**
 * 提取页面中所有数据表格的表头
 *
 * URP 的表格类名有稳定规律：
 *   infolist_tab    表头 + 数据（成绩、课表、考试…）
 *   datalist        成绩查询页的表格
 *   infolist_common 数据行（普通列表）
 *   infolist_hr_common 数据行（含跨行合并的列表，如课表）
 *   button_tab / explain_tab 按钮区与说明区
 *
 * @param {string} html
 * @returns {Array<{headers:string[], tableClass:string, rowClasses:string[]}>}
 */
export function extractTableHeaders(rawHtml) {
  const html = stripHtmlComments(rawHtml);
  const tables = [];
  const tableRe = /<table\b([^>]*)>([\s\S]*?)<\/table>/gi;
  let match;

  while ((match = tableRe.exec(html)) !== null) {
    const tableAttrs = match[1];
    const body = match[2];

    const headers = [];
    const thRe = /<th\b[^>]*>([\s\S]*?)<\/th>/gi;
    let thMatch;
    while ((thMatch = thRe.exec(body)) !== null) {
      const text = stripTags(thMatch[1]).replace(/\s+/g, '');
      if (text) headers.push(text);
    }
    if (headers.length === 0) continue;

    const rowClasses = [
      ...new Set(
        [...body.matchAll(/<t[rd]\b[^>]*class\s*=\s*["']([^"']+)["']/gi)].map((m) => m[1])
      )
    ];

    tables.push({
      headers,
      tableClass: (tableAttrs.match(/class\s*=\s*["']([^"']+)["']/i) || [])[1] || '',
      rowClasses
    });
  }

  return tables;
}

/**
 * 综合分析单个样本页面
 *
 * @param {string} html 已解码的 HTML
 * @param {Object} [meta] 附加信息（file / path / charset…）
 */
export function analyzeSample(html, meta = {}) {
  const base = analyzePortal(html, meta);
  const tables = extractTableHeaders(html);

  return {
    ...base,
    tables,
    // 只保留「业务地址」，过滤掉样式与脚本
    endpoints: base.inlineUrls.filter(
      (item) => !/\/styles\/|\.css|jquery|\.js$/i.test(item.url)
    )
  };
}

/**
 * 判断页面是否「明显是登录页 / 错误页」，这类页面不参与线索提取
 */
function isNoisePage(html) {
  const text = String(html || '');
  if (text.includes('j_acegi_security_check')) return true;
  if (/HTTP\s*(?:状态|Status)\s*\d{3}/i.test(text)) return true;
  if (/您无权|无权限|没有权限|非法访问/.test(text)) return true;
  return false;
}

/**
 * 把页面里出现的地址解析成「相对基址的路径」
 *
 * 关键点：教务系统页面里的链接写法五花八门，必须区分处理：
 *   1. 以 `/academic/` 开头 → 直接去掉前缀
 *   2. 以 `/` 开头 → 相对站点根
 *   3. 其它 → **相对当前页面所在目录**解析（`../` 需要真正地向上跳）
 *
 * 早期版本把所有相对地址都当成「相对基址」，导致
 * `calendar/calendarViewList.do` 里的 `findSchoolCalendarDetailByDate.do`
 * 被错误解析成 `/academic/findSchoolCalendarDetailByDate.do` 而 404。
 *
 * @param {string} rawUrl 页面中出现的地址
 * @param {string} [pagePath] 该页面自身的路径
 * @returns {string|null} 相对基址的路径（含 query），无法解析时返回 null
 */
export function resolveCandidatePath(rawUrl, pagePath = '') {
  const url = String(rawUrl || '').trim();
  if (!url) return null;

  // 协议地址、锚点、JS 伪协议一律跳过
  if (/^(?:[a-z][a-z0-9+.-]*:|\/\/|#)/i.test(url)) return null;

  const qIndex = url.indexOf('?');
  let pathPart = qIndex === -1 ? url : url.slice(0, qIndex);
  const query = qIndex === -1 ? '' : url.slice(qIndex);
  if (!pathPart) return null;

  // 1. 以 /academic/ 开头 → 去掉应用前缀
  if (/^\/academic\//i.test(pathPart)) {
    return `${pathPart.replace(/^\/academic\//i, '')}${query}`;
  }

  // 2. 其它绝对路径 → 相对站点根
  if (pathPart.startsWith('/')) {
    return `${pathPart.replace(/^\/+/, '')}${query}`;
  }

  // 3. 相对路径 → 相对页面所在目录做真正的路径归一化
  const dir = pagePath.includes('/') ? pagePath.slice(0, pagePath.lastIndexOf('/') + 1) : '';
  const stack = [];
  for (const segment of `${dir}${pathPart}`.split('/')) {
    if (segment === '' || segment === '.') continue;
    if (segment === '..') {
      stack.pop();
      continue;
    }
    stack.push(segment);
  }

  if (stack.length === 0) return null;
  return `${stack.join('/')}${query}`;
}

/**
 * 从已落盘的样本中提取「还没探测过」的候选地址
 *
 * @param {string} outDir 输出目录（用于读取解码副本）
 * @param {Array} entries SampleStore 的 manifest 条目
 * @param {Object} [options]
 * @param {Set<string>} [options.knownPaths] 已经探测过的路径集合
 * @param {number} [options.maxCandidates=200] 最多返回多少条
 * @param {boolean} [options.skipMutating=true] 是否跳过疑似会改数据的地址
 * @returns {{candidates:Array, sources:Map<string,string>}}
 */
export function collectNewCandidates(outDir, entries, options = {}) {
  const knownPaths = options.knownPaths || new Set();
  const maxCandidates = options.maxCandidates ?? 200;
  const skipMutating = options.skipMutating !== false;

  /** 疑似会改变数据的方法名 */
  const MUTATING_HINTS = /\b(save|modify|update|delete|del|add|insert|submit|commit|drop)\b/i;

  const sources = new Map();
  const candidates = [];

  for (const entry of entries) {
    if (!entry.decodedFile || entry.isErrorPage || entry.looksLikeLogin) continue;

    // 纯脚本文件（.js）里全是 JS 代码，从中提取「地址」会产生大量噪音，
    // 例如 DWR 的 engine.js 会扫出 `dwr/MSXML2.DO`、`dwr/dom.do` 这类伪地址。
    // 真正的功能页是 .do / .jsdo / .jsp / .htm，不受影响。
    if (/\.js$/i.test(entry.path || '')) continue;

    const abs = path.join(outDir, entry.decodedFile);
    if (!fs.existsSync(abs)) continue;

    const fileText = fs.readFileSync(abs, 'utf8');
    if (isNoisePage(fileText)) continue;

    // 注释里的地址通常对应已被废弃的功能，探测它们只会产生 404 噪音
    const html = stripHtmlComments(fileText);

    for (const item of extractInlineUrls(html)) {
      // 关键：相对地址要相对「页面自身所在目录」解析，而不是一律相对基址。
      // 例如页面 calendar/calendarViewList.do 里的 `findSchoolCalendarDetailByDate.do`
      // 真实地址是 calendar/findSchoolCalendarDetailByDate.do，直接当成根路径会 404。
      const resolved = resolveCandidatePath(item.url, entry.path);
      if (!resolved) continue;

      // 只保留看起来像业务请求的地址
      if (!BUSINESS_EXT.test(resolved)) continue;
      if (/(^|\/)styles\//i.test(resolved)) continue;

      const pathOnly = resolved.split('?')[0];
      if (knownPaths.has(pathOnly)) continue;
      if (sources.has(pathOnly)) continue;

      if (skipMutating && MUTATING_HINTS.test(pathOnly)) {
        sources.set(pathOnly, `${entry.key}（疑似变更类，已跳过）`);
        continue;
      }

      sources.set(pathOnly, entry.key);
      candidates.push({
        key: `expanded.${pathOnly.replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '').toLowerCase()}`,
        name: `（深度发现）${pathOnly}`,
        module: inferModule(pathOnly),
        path: resolved,
        method: 'GET',
        confidence: 'discovered',
        discoveredFrom: entry.key,
        note: `从样本 ${entry.file} 中解析得到（原写法 \`${item.url}\`）`
      });

      if (candidates.length >= maxCandidates) break;
    }
    if (candidates.length >= maxCandidates) break;
  }

  return { candidates, sources };
}

/**
 * 把全部样本的分析结果渲染成 Markdown 报告
 *
 * @param {Array} items [{ file, key, name, status, charset, analysis }]
 * @param {Object} [meta]
 */
export function renderSampleAnalysisMarkdown(items, meta = {}) {
  const lines = [];
  const L = (s = '') => lines.push(s);

  L('# 样本结构分析报告');
  L();
  L(`- 生成时间：${new Date().toLocaleString('zh-CN')}`);
  L(`- 分析样本数：${items.length}`);
  L(`- 说明：本报告把每个接口返回的 HTML 做了结构化拆解（表头 / 表单 / 按钮 / 嵌套地址），`);
  L('  用于从真实响应反推接口语义，而不是按命名规律猜测。');
  L();

  /* ---- 概览表 ---- */
  L('## 概览');
  L();
  L('| # | 接口 | 页面标题 | 表格 | 表单 | 按钮 | 嵌套地址 |');
  L('| ---: | --- | --- | ---: | ---: | ---: | ---: |');
  items.forEach((item, i) => {
    const a = item.analysis;
    L(
      `| ${i + 1} | \`${item.key}\` | ${a.title || '—'} | ${a.tables.length} | ` +
        `${a.forms.length} | ${a.buttons.length} | ${a.endpoints.length} |`
    );
  });

  /* ---- 逐条明细 ---- */
  L();
  L('## 明细');
  for (const item of items) {
    const a = item.analysis;
    L();
    L(`### ${item.key}`);
    L();
    L(`- 路径：\`${item.path || '-'}\``);
    L(`- 样本：\`${item.file || '-'}\`（${item.bytes || '-'} 字节，编码 ${item.charset || '-'}）`);
    L(`- 标题：${a.title || '—'}`);

    if (a.tables.length > 0) {
      L();
      L('**表格结构**：');
      L();
      for (const t of a.tables) {
        L(`- \`${t.tableClass || '(无class)'}\` → ${t.headers.join(' | ')}`);
        if (t.rowClasses.length) L(`  - 行 class：\`${t.rowClasses.join('`, `')}\``);
      }
    }

    if (a.forms.length > 0) {
      L();
      L('**表单**：');
      L();
      for (const f of a.forms) {
        L(`- \`${f.method} ${f.action || '(空 action)'}\``);
        const names = f.fields.map((x) => x.name || x.id).filter(Boolean);
        if (names.length) L(`  - 字段：${names.join(', ')}`);
        const opts = f.fields.filter((x) => x.options?.length);
        for (const o of opts) {
          L(`  - \`${o.name}\` 选项：${o.options.map((v) => v.label).join(' / ')}`);
        }
      }
    }

    const withOnclick = a.buttons.filter((b) => b.onclick);
    if (withOnclick.length > 0) {
      L();
      L('**按钮行为**：');
      L();
      for (const b of withOnclick) {
        L(`- [${b.text || b.name || b.id || b.type}] \`${b.onclick}\``);
      }
    }

    if (a.frames.length > 0) {
      L();
      L('**框架**：');
      L();
      for (const f of a.frames) L(`- \`<${f.tag} name="${f.name}">\` → \`${f.src}\``);
    }

    if (a.endpoints.length > 0) {
      L();
      L('**页面中出现的地址**：');
      L();
      for (const e of a.endpoints) L(`- \`${e.url}\``);
    }
  }

  L();
  return lines.join('\n');
}
