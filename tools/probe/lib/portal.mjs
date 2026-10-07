/**
 * 门户页 / 框架页分析器
 *
 * 背景：教务系统登录后进入的门户页（如 index_new.jsp）里集中了大量
 * 按钮、表单、frame 与内联 JS，真实接口路径往往**只出现在这里**，
 * 而不在左侧菜单里。与其按 URP 命名规律盲猜路径，不如把门户页抓下来
 * 做结构化提取，直接拿到「表单 action / onclick 里的 URL / frame src」。
 *
 * 本模块在 Node 侧工作，不依赖 DOM，全部用正则提取。
 */

import { normalizeText, stripHtmlComments } from './text.mjs';

/**
 * 匹配疑似接口路径
 *
 * 说明：
 *  · **不含 `.action`** —— 那是 Struts2 的约定，本系统用 `.do` / `.jsdo` / `.jsp`。
 *    加进来会把 JS 里的 `fp.action`、`form1.action` 这类属性访问误当成地址。
 *  · 匹配结果还会经过「路径段里不能有点」的校验，避免把
 *    `dwr.engine._serialize.do` 这类 JS 表达式当成地址。
 */
const URL_PATTERN = /([A-Za-z0-9_@.\-/]+\.(?:jsdo|do|jsp|htm|html))(\?[^"'\s<>()\\]*)?/gi;

/** URL 扩展名白名单（用于候选筛选） */
export const BUSINESS_EXT = /\.(?:jsdo|do|jsp|htm|html)(?:\?|$)/i;

/**
 * 解析标签属性串 → 对象
 *
 * 必须同时支持 HTML **布尔属性**（`checked` / `selected` / `readonly` / `disabled`
 * 这类只有名字没有值的写法）。老式教务页面里 `<option value="2" selected>`
 * 这种写法非常常见，若只匹配 `name=value` 会漏掉选中态。
 */
export function parseAttributes(rawAttrs) {
  const attrs = {};
  const re = /([:\w-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
  let match;

  while ((match = re.exec(rawAttrs || '')) !== null) {
    const name = match[1].toLowerCase();
    const hasValue = match[2] !== undefined || match[3] !== undefined || match[4] !== undefined;

    if (!hasValue) {
      // 布尔属性：存在即为真，用空串表示
      if (!(name in attrs)) attrs[name] = '';
      continue;
    }
    attrs[name] = match[2] ?? match[3] ?? match[4] ?? '';
  }

  return attrs;
}

/** 去掉标签，取出纯文本 */
function stripTags(html) {
  return normalizeText(String(html || '').replace(/<[^>]*>/g, ' '));
}

/** 通用：提取带内容的一对标签 */
function extractPaired(html, tagName) {
  const re = new RegExp(`<${tagName}\\b([^>]*)>([\\s\\S]*?)<\\/${tagName}>`, 'gi');
  const results = [];
  let match;
  while ((match = re.exec(html)) !== null) {
    results.push({ attrs: parseAttributes(match[1]), inner: match[2], raw: match[0] });
  }
  return results;
}

/** 通用：提取自闭合标签 */
function extractVoid(html, tagName) {
  const re = new RegExp(`<${tagName}\\b([^>]*?)\\/?>`, 'gi');
  const results = [];
  let match;
  while ((match = re.exec(html)) !== null) {
    results.push({ attrs: parseAttributes(match[1]), raw: match[0] });
  }
  return results;
}

/** 提取所有 frame / iframe */
export function extractFrames(html) {
  const frames = [];
  for (const tag of ['frame', 'iframe']) {
    const re = new RegExp(`<${tag}\\b([^>]*?)\\/?>`, 'gi');
    let match;
    while ((match = re.exec(html)) !== null) {
      const attrs = parseAttributes(match[1]);
      frames.push({
        tag,
        name: attrs.name || '',
        id: attrs.id || '',
        src: attrs.src || '',
        extra: attrs.scrolling || ''
      });
    }
  }
  return frames;
}

/** 提取全部 <a> */
export function extractLinks(html) {
  const links = [];
  for (const { attrs, inner } of extractPaired(html, 'a')) {
    const text = stripTags(inner);
    const href = attrs.href || '';
    const onclick = attrs.onclick || '';
    if (!href && !onclick) continue;
    links.push({
      text,
      href,
      onclick,
      target: attrs.target || '',
      title: attrs.title || '',
      id: attrs.id || ''
    });
  }
  return links;
}

/** 提取表单控件（input / select / textarea / button） */
export function extractFormControls(innerHtml) {
  const fields = [];

  for (const { attrs } of extractVoid(innerHtml, 'input')) {
    fields.push({
      tag: 'input',
      type: attrs.type || 'text',
      name: attrs.name || '',
      id: attrs.id || '',
      value: attrs.value || '',
      placeholder: attrs.placeholder || '',
      checked: 'checked' in attrs,
      readonly: 'readonly' in attrs,
      onclick: attrs.onclick || '',
      options: []
    });
  }

  for (const { attrs, inner } of extractPaired(innerHtml, 'select')) {
    const options = [];
    const re = /<option\b([^>]*)>([\s\S]*?)<\/option>/gi;
    let match;
    while ((match = re.exec(inner)) !== null) {
      const optAttrs = parseAttributes(match[1]);
      options.push({
        value: optAttrs.value ?? stripTags(match[2]),
        label: stripTags(match[2]) || optAttrs.value || '',
        selected: 'selected' in optAttrs
      });
    }
    fields.push({
      tag: 'select',
      type: 'select',
      name: attrs.name || '',
      id: attrs.id || '',
      value: '',
      placeholder: '',
      onclick: attrs.onchange || '',
      options
    });
  }

  for (const { attrs, inner } of extractPaired(innerHtml, 'textarea')) {
    fields.push({
      tag: 'textarea',
      type: 'textarea',
      name: attrs.name || '',
      id: attrs.id || '',
      value: stripTags(inner),
      placeholder: '',
      options: []
    });
  }

  return fields;
}

/** 提取全部 <form> */
export function extractForms(html) {
  const forms = [];
  const rawForms = extractPaired(html, 'form');

  rawForms.forEach(({ attrs, inner }, index) => {
    const hidden = extractVoid(inner, 'input').filter(
      (i) => (i.attrs.type || '').toLowerCase() === 'hidden'
    );

    forms.push({
      index,
      name: attrs.name || '',
      id: attrs.id || '',
      action: attrs.action || '',
      method: (attrs.method || 'GET').toUpperCase(),
      target: attrs.target || '',
      onsubmit: attrs.onsubmit || '',
      fieldCount: extractFormControls(inner).length,
      hiddenFields: hidden.map((i) => ({
        name: i.attrs.name || '',
        value: i.attrs.value || ''
      })),
      fields: extractFormControls(inner)
    });
  });

  return forms;
}

/** 提取全部按钮（<button> 与 input[type=button|submit|image]） */
export function extractButtons(html) {
  const buttons = [];

  for (const { attrs, inner } of extractPaired(html, 'button')) {
    buttons.push({
      tag: 'button',
      type: attrs.type || 'submit',
      name: attrs.name || '',
      id: attrs.id || '',
      value: attrs.value || '',
      text: stripTags(inner),
      onclick: attrs.onclick || ''
    });
  }

  for (const { attrs } of extractVoid(html, 'input')) {
    const type = (attrs.type || '').toLowerCase();
    if (!['button', 'submit', 'reset', 'image'].includes(type)) continue;
    buttons.push({
      tag: 'input',
      type,
      name: attrs.name || '',
      id: attrs.id || '',
      value: attrs.value || '',
      text: attrs.value || attrs.alt || attrs.title || '',
      onclick: attrs.onclick || ''
    });
  }

  return buttons;
}

/** 提取脚本：外链与内联 */
export function extractScripts(html) {
  const scripts = [];
  const re = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let match;
  let index = 0;
  while ((match = re.exec(html)) !== null) {
    const attrs = parseAttributes(match[1]);
    const inline = match[2] || '';
    scripts.push({
      index: index++,
      src: attrs.src || '',
      type: attrs.type || '',
      inlineSize: inline.trim().length,
      inline: attrs.src ? '' : inline.trim()
    });
  }
  return scripts;
}

/**
 * 从整页 HTML 中扫描疑似接口地址，并带上出现位置的上下文
 * @returns {Array<{path:string, query:string, url:string, context:string}>}
 */
export function extractInlineUrls(html) {
  const found = new Map();
  URL_PATTERN.lastIndex = 0;
  let match;

  while ((match = URL_PATTERN.exec(html)) !== null) {
    const path = match[1];
    const query = match[2] || '';
    const url = `${path}${query}`;

    // 过滤明显不是业务地址的
    if (/\.(?:css|js|png|jpe?g|gif|svg|ico|woff2?)$/i.test(path)) continue;
    if (/^(?:window|document|top|parent|self|this)\./i.test(path)) continue;
    if (path.includes('.parent.')) continue;

    // 路径段里不该出现点：`dwr.engine._serialize.do` 是 JS 表达式而不是地址
    if (path.replace(/\.(?:jsdo|do|jsp|htm|html)$/i, '').includes('.')) continue;

    if (found.has(url)) continue;

    const start = Math.max(0, match.index - 80);
    const context = html
      .slice(start, match.index)
      .replace(/\s+/g, ' ')
      .trim()
      .slice(-70);

    found.set(url, { path, query, url, context });
  }

  return [...found.values()];
}

/** 提取内联 JS 中定义的函数名（用于理解按钮行为） */
export function extractFunctionNames(html) {
  const names = new Set();
  const re = /function\s+([A-Za-z_$][\w$]*)\s*\(/g;
  let match;
  while ((match = re.exec(html)) !== null) names.add(match[1]);
  return [...names];
}

/** 提取页面标题 */
export function extractTitle(html) {
  const match = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  return match ? stripTags(match[1]) : '';
}

/** 提取 meta charset 声明（用于判断页面自称的编码） */
export function extractMetaCharset(html) {
  const match =
    html.match(/<meta[^>]+charset\s*=\s*["']?\s*([\w-]+)/i) ||
    html.match(/<meta[^>]+content\s*=\s*["'][^"']*charset\s*=\s*([\w-]+)/i);
  return match ? match[1] : '';
}

/**
 * 把一段 HTML 重排为「一行一个标签」，便于人工/AI 阅读源码
 * @param {string} html
 * @param {number} [maxLength] 超出长度则截断（避免报告过大）
 */
export function toReadableHtml(html, maxLength = 400000) {
  const readable = String(html || '')
    .replace(/\r\n?/g, '\n')
    .replace(/>\s*</g, '>\n<')
    .replace(/\n{3,}/g, '\n\n');
  if (readable.length <= maxLength) return readable;
  return `${readable.slice(0, maxLength)}\n\n<!-- …已截断（原始长度 ${readable.length} 字符）… -->`;
}

/**
 * 门户页综合分析
 *
 * @param {string} html 已按正确字符集解码的 HTML
 * @param {Object} [meta] 附加信息（url / charset / path / bytes）
 * @returns {Object}
 */
export function analyzePortal(rawHtml, meta = {}) {
  // 先剥离 HTML 注释：教务系统页面里大量存在被注释掉的历史代码，
  // 不剥离会把注释里的列/字段/地址也算进来，导致结构与列数错位
  const html = stripHtmlComments(rawHtml);

  const frames = extractFrames(html);
  const links = extractLinks(html);
  const forms = extractForms(html);
  const buttons = extractButtons(html);
  const scripts = extractScripts(html);
  const inlineUrls = extractInlineUrls(html);
  const moduleIds = [...new Set(
    [...html.matchAll(/moduleId\s*[=:]\s*["']?(\d+)/gi)].map((m) => m[1])
  )];

  const analysis = {
    ...meta,
    title: extractTitle(html),
    metaCharset: extractMetaCharset(html),
    frames,
    links,
    forms,
    buttons,
    scripts: scripts.map(({ inline, ...rest }) => rest),
    inlineUrls,
    moduleIds,
    functionNames: extractFunctionNames(html),
    summary: {
      frames: frames.length,
      links: links.length,
      forms: forms.length,
      fields: forms.reduce((sum, f) => sum + f.fieldCount, 0),
      buttons: buttons.length,
      scripts: scripts.length,
      inlineUrls: inlineUrls.length,
      moduleIds: moduleIds.length,
      functionNames: extractFunctionNames(html).length
    },
    // 内联脚本全文单独存放，便于按需查看
    inlineScripts: scripts
      .filter((s) => s.inline)
      .map((s) => ({ index: s.index, code: s.inline }))
  };

  return analysis;
}

/**
 * 将分析结果渲染成便于阅读的 Markdown 报告
 * @param {Object} analysis analyzePortal 的返回值
 */
export function renderPortalMarkdown(analysis) {
  const lines = [];
  const L = (s = '') => lines.push(s);

  L(`# 门户页分析报告 · ${analysis.source || analysis.url || ''}`);
  L();
  L(`- 抓取地址：\`${analysis.url || '-'}\``);
  L(`- 页面标题：${analysis.title || '-'}`);
  L(`- 响应编码：\`${analysis.charset || '-'}\`（页面 meta 自称：\`${analysis.metaCharset || '-'}\`）`);
  L(`- 响应大小：${analysis.bytes || '-'} 字节`);
  L(`- 抓取时间：${new Date().toLocaleString('zh-CN')}`);
  L();
  L('## 概览');
  L();
  L('| 项目 | 数量 |');
  L('| --- | ---: |');
  for (const [key, value] of Object.entries(analysis.summary)) {
    L(`| ${key} | ${value} |`);
  }
  if (analysis.moduleIds.length > 0) {
    L();
    L(`**模块 ID**（${analysis.moduleIds.length} 个）：`);
    L();
    L('```');
    L(analysis.moduleIds.join(', '));
    L('```');
  }

  if (analysis.frames.length > 0) {
    L();
    L('## 框架 / 内嵌页');
    L();
    L('| 标签 | name | id | src |');
    L('| --- | --- | --- | --- |');
    for (const f of analysis.frames) {
      L(`| \`<${f.tag}>\` | ${f.name || '-'} | ${f.id || '-'} | \`${f.src || '-'}\` |`);
    }
  }

  if (analysis.forms.length > 0) {
    L();
    L('## 表单');
    for (const form of analysis.forms) {
      L();
      L(`### 表单 #${form.index}${form.name ? ` · name=${form.name}` : ''}`);
      L();
      L(`- **action**：\`${form.action || '(空)'}\``);
      L(`- **method**：\`${form.method}\``);
      if (form.target) L(`- **target**：\`${form.target}\``);
      if (form.onsubmit) L(`- **onsubmit**：\`${form.onsubmit}\``);
      L(`- 字段数：${form.fieldCount}`);
      L();
      L('| 控件 | type | name | id | 默认值 | 备注 |');
      L('| --- | --- | --- | --- | --- | --- |');
      for (const f of form.fields) {
        const note = [
          f.placeholder ? `placeholder=${f.placeholder}` : '',
          f.checked ? 'checked' : '',
          f.readonly ? 'readonly' : '',
          f.options?.length ? `${f.options.length} 个选项` : ''
        ]
          .filter(Boolean)
          .join(', ');
        L(
          `| \`<${f.tag}>\` | ${f.type} | \`${f.name || '-'}\` | \`${f.id || '-'}\` | ` +
            `${f.value ? `\`${f.value}\`` : '-'} | ${note || '-'} |`
        );
      }
    }
  }

  if (analysis.buttons.length > 0) {
    L();
    L('## 按钮');
    L();
    L('| 文本 | 类型 | name | id | onclick |');
    L('| --- | --- | --- | --- | --- |');
    for (const b of analysis.buttons) {
      L(
        `| ${b.text || '-'} | ${b.type} | \`${b.name || '-'}\` | \`${b.id || '-'}\` | ` +
          `${b.onclick ? `\`${b.onclick}\`` : '-'} |`
      );
    }
  }

  if (analysis.links.length > 0) {
    L();
    L('## 链接');
    L();
    L('| 文本 | href | onclick |');
    L('| --- | --- | --- |');
    for (const link of analysis.links) {
      L(
        `| ${link.text || '-'} | ${link.href ? `\`${link.href}\`` : '-'} | ` +
          `${link.onclick ? `\`${link.onclick}\`` : '-'} |`
      );
    }
  }

  if (analysis.inlineUrls.length > 0) {
    L();
    L('## 页面中出现的疑似接口地址');
    L();
    L('> 这些是从 frame src、表单 action、onclick 与内联 JS 里扫出来的，');
    L('> **是最有价值的线索**，可直接作为待探测接口。');
    L();
    L('| # | 地址 | 出现位置上下文 |');
    L('| ---: | --- | --- |');
    analysis.inlineUrls.forEach((item, i) => {
      L(`| ${i + 1} | \`${item.url}\` | ${item.context ? `…${item.context}` : '-'} |`);
    });
  }

  if (analysis.functionNames.length > 0) {
    L();
    L('## 内联 JS 中定义的函数');
    L();
    L('```');
    L(analysis.functionNames.join('\n'));
    L('```');
  }

  if (analysis.scripts.length > 0) {
    L();
    L('## 脚本');
    L();
    L('| # | src | 内联字节数 |');
    L('| ---: | --- | ---: |');
    for (const s of analysis.scripts) {
      L(`| ${s.index} | ${s.src ? `\`${s.src}\`` : '(内联)'} | ${s.inlineSize} |`);
    }
  }

  L();
  return lines.join('\n');
}
