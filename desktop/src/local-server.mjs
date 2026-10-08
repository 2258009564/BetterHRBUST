/**
 * 零依赖本地静态文件服务（托管 web/dist 构建产物）
 *
 * 特性：
 *  - mime 类型映射（覆盖 Vite 构建产物的全部资源类型）
 *  - 路径穿越防护（relative + 前缀校验，拒绝逃逸出托管根目录）
 *  - SPA 回退：无扩展名的未知路径回退 index.html，带扩展名则 404
 *  - 缓存策略：/assets/*（内容哈希文件名）长缓存，index.html 不缓存
 *  - 支持 GET / HEAD
 */
import fs from 'node:fs';
import path from 'node:path';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.map': 'application/json',
  '.json': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.xml': 'application/xml; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.avif': 'image/avif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.eot': 'application/vnd.ms-fontobject',
  '.wasm': 'application/wasm',
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.pdf': 'application/pdf'
};

/**
 * 创建静态文件处理器
 * @param {string} root 托管根目录（web/dist）
 * @returns {(req: import('http').IncomingMessage, res: import('http').ServerResponse) => void}
 */
export function createStaticFileHandler(root) {
  const rootAbs = path.resolve(root);
  const indexHtml = path.join(rootAbs, 'index.html');

  function send(res, status, body) {
    res.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end(body);
  }

  return function handle(req, res) {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      return send(res, 405, 'Method Not Allowed');
    }

    let urlPath;
    try {
      urlPath = decodeURIComponent(new URL(req.url, 'http://127.0.0.1').pathname);
    } catch {
      return send(res, 400, 'Bad Request');
    }

    // 去掉开头斜杠交给 path.resolve 拼接，随后做包含校验防穿越
    const resolved = path.resolve(rootAbs, urlPath.replace(/^\/+/, ''));
    const rel = path.relative(rootAbs, resolved);
    if (rel.startsWith('..') || path.isAbsolute(rel)) {
      return send(res, 403, 'Forbidden');
    }

    let filePath = resolved;
    try {
      let stat = fs.existsSync(filePath) ? fs.statSync(filePath) : null;
      if (stat && stat.isDirectory()) {
        filePath = path.join(filePath, 'index.html');
        stat = fs.existsSync(filePath) ? fs.statSync(filePath) : null;
      }

      if (!stat) {
        // SPA 回退：无扩展名路径回退 index.html；带扩展名的资源缺失则 404
        if (path.extname(urlPath)) {
          return send(res, 404, 'Not Found');
        }
        if (!fs.existsSync(indexHtml)) {
          return send(res, 404, 'Frontend build not found: web/dist');
        }
        filePath = indexHtml;
        stat = fs.statSync(filePath);
      }

      const ext = path.extname(filePath).toLowerCase();
      const headers = {
        'Content-Type': MIME[ext] || 'application/octet-stream',
        'Content-Length': stat.size,
        'X-Content-Type-Options': 'nosniff'
      };
      if (filePath === indexHtml) {
        headers['Cache-Control'] = 'no-cache';
      } else if (rel.startsWith('assets' + path.sep)) {
        // Vite 构建的 assets 带内容哈希，可长缓存
        headers['Cache-Control'] = 'public, max-age=31536000, immutable';
      }

      res.writeHead(200, headers);
      if (req.method === 'HEAD') return res.end();
      fs.createReadStream(filePath).on('error', () => res.destroy()).pipe(res);
    } catch {
      send(res, 500, 'Internal Server Error');
    }
  };
}
