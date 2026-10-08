/**
 * 桌面端本地服务冒烟测试（无需 Electron，纯 Node 运行）
 *
 * 验证项：
 *  1. 静态托管：GET / 返回 index.html
 *  2. 资源服务：/assets/*.js 返回 200 与正确 Content-Type、长缓存头
 *  3. SPA 回退：无扩展名未知路径回退 index.html
 *  4. 缺失资源：带扩展名的未知路径返回 404
 *  5. 路径穿越防护：编码后的 ../ 逃逸被拒绝
 *  6. /academic 反代：可达时校验 302 Location 重写；不可达时校验
 *     socket 销毁路径（前端 fetch reject → 友好提示）
 *
 * 用法：node scripts/smoke.mjs（需先 npm install 与 npm run build:web）
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAcademicProxy, isAcademicPath } from '../src/academic-proxy.mjs';
import { createStaticFileHandler } from '../src/local-server.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, '../../web/dist');

function startServer() {
  const academicProxy = createAcademicProxy();
  const serveStatic = createStaticFileHandler(webRoot);
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
    if (isAcademicPath(pathname)) {
      academicProxy.web(req, res);
    } else {
      serveStatic(req, res);
    }
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () =>
      resolve({ server, port: server.address().port })
    );
  });
}

function request(port, requestPath, { method = 'GET' } = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: '127.0.0.1',
        port,
        path: requestPath,
        method,
        headers: {
          Accept: 'text/html,*/*',
          'X-Requested-With': 'XMLHttpRequest'
        }
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () =>
          resolve({
            status: res.statusCode,
            headers: res.headers,
            body: Buffer.concat(chunks)
          })
        );
      }
    );
    req.on('error', (err) => reject(Object.assign(err, { requestPath })));
    req.end();
  });
}

const results = [];
function report(name, pass, detail = '') {
  results.push({ name, pass });
  console.log(`  ${pass ? '✓' : '✗'} ${name}${detail ? ` —— ${detail}` : ''}`);
}

const { server, port } = await startServer();
const base = `http://127.0.0.1:${port}`;
console.log(`本地测试服务: ${base}\n`);

try {
  // 1. 静态托管 index.html
  {
    const res = await request(port, '/');
    const html = res.body.toString('utf-8');
    const pass =
      res.status === 200 &&
      String(res.headers['content-type']).includes('text/html') &&
      /<html[\s>]/i.test(html);
    report('静态托管 GET /', pass, `status=${res.status}`);
    assert.ok(pass, 'index.html 服务异常');
  }

  // 2. assets 资源服务（取 dist/assets 里第一个 .js）
  {
    const assetsDir = path.join(webRoot, 'assets');
    const jsFile = fs
      .readdirSync(assetsDir)
      .find((f) => f.endsWith('.js') && fs.statSync(path.join(assetsDir, f)).isFile());
    const res = await request(port, `/assets/${encodeURIComponent(jsFile)}`);
    const pass =
      res.status === 200 &&
      /javascript/.test(String(res.headers['content-type'])) &&
      String(res.headers['cache-control']).includes('immutable');
    report('assets 资源与长缓存头', pass, `status=${res.status}`);
    assert.ok(pass, 'assets 服务异常');
  }

  // 3. SPA 回退
  {
    const res = await request(port, '/some/unknown/route');
    const pass =
      res.status === 200 && String(res.headers['content-type']).includes('text/html');
    report('SPA 回退（无扩展名路径 → index.html）', pass, `status=${res.status}`);
    assert.ok(pass, 'SPA 回退异常');
  }

  // 4. 缺失资源 404
  {
    const res = await request(port, '/no-such-file.png');
    report('缺失资源返回 404', res.status === 404, `status=${res.status}`);
    assert.equal(res.status, 404, '缺失资源应 404');
  }

  // 5. 路径穿越防护（URL 解析后解码仍逃逸的路径）
  {
    const res = await request(port, '/..%2f..%2fdesktop%2fpackage.json');
    report('路径穿越防护（403）', res.status === 403, `status=${res.status}`);
    assert.equal(res.status, 403, '穿越路径应 403');
  }

  // 6. /academic 反代：可达 → 校验 Location 重写；不可达 → 校验 socket 销毁
  {
    try {
      const res = await request(port, '/academic/getCaptcha.do');
      const location = res.headers['location'];
      const rewriteOk =
        !location || !/^https?:\/\/jwzx\.hrbust\.edu\.cn/i.test(location);
      const type = res.headers['content-type'] || '';
      report(
        '/academic 反代响应',
        rewriteOk,
        `status=${res.status}, type=${type}, bytes=${res.body.length}` +
          (location ? `, location=${location}` : '')
      );
      assert.ok(rewriteOk, '302 Location 未被重写为相对路径');
    } catch (err) {
      // 上游不可达（非校园网环境）：fetch 应 reject 而非收到 502 页面
      const destroyed = /ECONNRESET|EPIPE|ECONNREFUSED|socket hang up|aborted/i.test(
        `${err.code || ''} ${err.message || ''}`
      );
      report(
        '/academic 上游不可达 → socket 销毁（前端友好提示路径）',
        destroyed,
        `err=${err.code || err.message}`
      );
      assert.ok(destroyed, '代理错误应销毁 socket 让 fetch reject');
    }
  }
} finally {
  server.close();
}

const failed = results.filter((r) => !r.pass).length;
console.log(`\n结果: ${results.length - failed}/${results.length} 通过`);
process.exit(failed ? 1 : 0);
