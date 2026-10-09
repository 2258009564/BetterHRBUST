// 仅在内存中保留状态和结构统计，不记录账号、参数、Cookie、密码或响应正文。
const entries = [];

function safePath(value) {
  try {
    const url = new URL(value || '/', 'http://jwzx.hrbust.edu.cn');
    return url.pathname.replace(/;jsessionid=[^/?#]*/gi, '');
  } catch {
    return '[路径不可解析]';
  }
}

export function recordAcademicDiagnostic({ path, finalUrl, method = 'GET', status, encoding,
  bytes, outcome, noticeCode, structure, counts }) {
  entries.push({ path: safePath(path), method, status, encoding, bytes, outcome,
    finalPath: finalUrl ? safePath(finalUrl) : undefined,
    finalProtocol: finalUrl ? new URL(finalUrl, 'http://jwzx.hrbust.edu.cn').protocol : undefined,
    noticeCode, structure, counts });
  if (entries.length > 80) entries.shift();
}

export function getAcademicDiagnosticsReport() {
  return JSON.stringify({ format: 'BetterHRBUST-诊断-v2', requests: entries }, null, 2);
}
