/**
 * 文本处理小工具（探测侧共用）
 */

/** 归一化空白与 &nbsp;，去掉首尾空白 */
export function normalizeText(text) {
  return String(text ?? '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/\u00a0/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** 去掉 HTML 标签，保留纯文本 */
export function stripTags(html) {
  return normalizeText(String(html || '').replace(/<[^>]*>/g, ' '));
}

/**
 * 去掉 HTML 注释
 *
 * 教务系统的页面里大量存在被注释掉的历史代码，例如
 * `<td>考试地点</td><!--<td>考试方式</td>-->`
 * 若不去掉注释，结构化分析会把注释里的列/字段也算进来，导致表头与列数错位。
 */
export function stripHtmlComments(html) {
  return String(html || '').replace(/<!--[\s\S]*?-->/g, '');
}

/** 解码常见 HTML 实体 */
export function decodeEntities(text) {
  return String(text ?? '')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/&nbsp;/gi, ' ');
}

/** 生成文件名安全的 slug */
export function slugify(text, { stripExtensions = true } = {}) {
  let value = String(text || '');
  if (stripExtensions) value = value.replace(/\.(jsdo|do|jsp|htm|html|action)$/i, '');
  return (
    value
      .replace(/[^A-Za-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .toLowerCase() || 'root'
  );
}

/** 单行预览文本 */
export function makePreview(text, maxLength = 300) {
  if (!text) return '';
  return normalizeText(text).slice(0, maxLength);
}
