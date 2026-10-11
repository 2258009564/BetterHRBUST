/**
 * 构建产物断言（无浏览器依赖，可进 CI）
 *
 * 目的：为落地页部署提供最低限度的验证门禁——
 *   · dist 存在且 index.html 完整；
 *   · 资源引用为相对路径（base: './'）——项目页部署在 /BetterHRBUST/ 子路径下，
 *     任何站内绝对路径都会 404；
 *   · index.html / CSS 引用的本地资源真实存在，防止静默丢失。
 *
 * 浏览器级布局回归（test:browser，依赖本机 playwright）仍供本地使用。
 */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'dist');
assert.ok(fs.existsSync(path.join(root, 'index.html')), '构建产物 dist/index.html 缺失：请先 npm run build');

const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');

// 站内绝对路径（/assets/...）在子路径部署下必然 404，只允许协议/相对引用
const absRefs = [...html.matchAll(/(?:src|href)="(\/[^/][^"]*)"/g)].map((m) => m[1]);
assert.deepEqual(absRefs, [], `index.html 引用了站内绝对路径（子路径部署会 404）: ${absRefs.join(', ')}`);

// index.html 引用的本地资源必须存在
const refs = [...html.matchAll(/(?:src|href)="([^"#?][^":]*?)"/g)]
  .map((m) => m[1])
  .filter((u) => !/^(https?:|data:|mailto:|#|\/\/)/.test(u));
assert.ok(refs.length > 0, 'index.html 未引用任何本地资源，产物可疑');
for (const ref of refs) {
  assert.ok(fs.existsSync(path.join(root, ref)), `index.html 引用的资源缺失: ${ref}`);
}

const js = [...html.matchAll(/src="((?:\.\/)?assets\/[^"]+\.js)"/g)].map((m) => m[1]);
// 入口 HTML 必须直接引用 JS 入口；CSS 不作此要求——全局样式已拆进各页面
// 组件 chunk 由 JS 动态加载，index.html 不再必然携带 <link rel="stylesheet">
assert.ok(js.length > 0, '缺少 JS 产物引用');

// 校验范围覆盖全部 CSS 产物（HTML 直连的 + JS chunk 携带的），
// 而非仅 HTML 引用到的——任何一份 CSS 内的字体/图片引用失效都应挡下
const cssFiles = fs.existsSync(path.join(root, 'assets'))
  ? [...new Set([
      ...[...html.matchAll(/href="((?:\.\/)?assets\/[^"]+\.css)"/g)].map((m) => m[1]),
      ...fs.readdirSync(path.join(root, 'assets'))
        .filter((f) => f.endsWith('.css'))
        .map((f) => `assets/${f}`)
    ])]
  : [];
// CSS 内引用的字体/图片（非 data:/http:）必须存在
for (const cssFile of cssFiles) {
  const text = fs.readFileSync(path.join(root, cssFile), 'utf8');
  for (const m of text.matchAll(/url\((?!['"]?data:|['"]?https?:|['"]?#)(['"]?)([^'")]+)\1\)/g)) {
    const rel = path.posix.join(path.posix.dirname(cssFile), m[2]);
    assert.ok(fs.existsSync(path.join(root, rel)), `CSS 引用的资源缺失: ${m[2]}（位于 ${cssFile}）`);
  }
}

console.log('通过：构建产物存在、相对路径引用、静态资源完整（浏览器级回归见 test:browser，本地运行）。');
