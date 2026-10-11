/**
 * ICS (iCalendar, RFC 5545) 导出工具
 *
 * 供课表/考试导出共用：
 *  - 文本按 §3.3.11 转义（反斜杠、分号、逗号、换行）
 *  - 内容行超过 75 字节按 §3.2.2 折叠（续行以空格开头，按 UTF-8 字节数计）
 *  - 行尾统一 CRLF；VEVENT 携带必需的 UID / DTSTAMP / DTSTART / DTEND
 *  - 时间为本地浮动时间（课表与考试按学校本地时间理解，无需时区限定）
 */

/** RFC 5545 §3.3.11：TEXT 值中的特殊字符转义 */
export function escapeIcsText(text) {
  return String(text ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r\n|\r|\n/g, '\\n');
}

/** Date → 本地浮动时间值 YYYYMMDDTHHMMSS */
export function formatIcsDateTime(date) {
  const pad = n => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}` +
    `T${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
  );
}

/** 单个字符的 UTF-8 字节长度 */
function charUtf8Bytes(ch) {
  const code = ch.codePointAt(0);
  if (code <= 0x7f) return 1;
  if (code <= 0x7ff) return 2;
  if (code <= 0xffff) return 3;
  return 4;
}

/** 超长内容行折叠为多行（续行以空格开头） */
function foldLine(line) {
  const chunks = [];
  let chunk = '';
  let chunkBytes = 0;
  for (const ch of line) {
    const bytes = charUtf8Bytes(ch);
    if (chunkBytes + bytes > 75 && chunk.length > 0) {
      chunks.push(chunk);
      chunk = ` ${ch}`;
      chunkBytes = 1 + bytes;
    } else {
      chunk += ch;
      chunkBytes += bytes;
    }
  }
  chunks.push(chunk);
  return chunks;
}

/** 简单字符串哈希（仅用于生成稳定 UID，无加密需求） */
function hashSeed(seed) {
  let hash = 5381;
  for (let i = 0; i < seed.length; i += 1) {
    hash = ((hash << 5) + hash + seed.charCodeAt(i)) >>> 0;
  }
  return hash.toString(36);
}

/**
 * 组装 VCALENDAR 文本
 * @param {Array<{uid?:string, summary:string, location?:string, description?:string,
 *   start:Date, end:Date, alarmMinutes?:number}>} events
 * @param {Object} [options]
 * @param {string} [options.prodId]
 */
export function buildIcsCalendar(events, { prodId = '-//BetterHRBUST//CN' } = {}) {
  const stamp = formatIcsDateTime(new Date());
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', `PRODID:${prodId}`, 'CALSCALE:GREGORIAN'];
  for (const ev of events) {
    if (!(ev.start instanceof Date) || !(ev.end instanceof Date)) continue;
    const uid = ev.uid || `${hashSeed(`${ev.summary}|${ev.start.getTime()}`)}@betterhrbust`;
    lines.push('BEGIN:VEVENT', `UID:${uid}`, `DTSTAMP:${stamp}`);
    if (ev.alarmMinutes) {
      lines.push('BEGIN:VALARM', 'ACTION:DISPLAY', `TRIGGER:-PT${ev.alarmMinutes}M`, `DESCRIPTION:${escapeIcsText(ev.summary)}`, 'END:VALARM');
    }
    lines.push(
      `DTSTART:${formatIcsDateTime(ev.start)}`,
      `DTEND:${formatIcsDateTime(ev.end)}`,
      `SUMMARY:${escapeIcsText(ev.summary)}`,
      `LOCATION:${escapeIcsText(ev.location || '')}`,
      `DESCRIPTION:${escapeIcsText(ev.description || '')}`,
      'END:VEVENT'
    );
  }
  lines.push('END:VCALENDAR');
  return lines.flatMap(foldLine).join('\r\n') + '\r\n';
}

/**
 * 导出 .ics 文件。
 *
 * 优先走 window.showSaveFilePicker（Chromium / WebView2 的 File System Access API）：
 * 弹原生"另存为"对话框、用户自选保存位置，写入结果真实可知。
 * 不支持或调用受限（无痕等环境抛错）时回退到锚点下载：
 * 文件静默落入浏览器默认下载目录，JS 无法获知最终结果，调用方须如实提示。
 *
 * @returns {Promise<'saved'|'cancelled'|'fallback'>}
 *   saved=用户自选位置写入成功；cancelled=用户在对话框点了取消；fallback=已触发默认下载
 */
export async function downloadIcs(filename, ics) {
  if (typeof window !== 'undefined' && typeof window.showSaveFilePicker === 'function') {
    try {
      const handle = await window.showSaveFilePicker({
        suggestedName: filename,
        types: [{ description: 'iCalendar 日历文件', accept: { 'text/calendar': ['.ics'] } }]
      });
      const writable = await handle.createWritable();
      await writable.write(new Blob([ics], { type: 'text/calendar;charset=utf-8' }));
      await writable.close();
      return 'saved';
    } catch (err) {
      if (err && err.name === 'AbortError') return 'cancelled';
      // 其余异常（无痕等受限环境）：回退到锚点下载，不让导出死在 picker 上
    }
  }
  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
  return 'fallback';
}
