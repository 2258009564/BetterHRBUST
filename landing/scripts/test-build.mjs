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
const css = [...html.matchAll(/href="((?:\.\/)?assets\/[^"]+\.css)"/g)].map((m) => m[1]);
assert.ok(js.length > 0 && css.length > 0, '缺少 JS/CSS 产物引用');

// CSS 内引用的字体/图片（非 data:/http:）必须存在
for (const cssFile of css) {
  const text = fs.readFileSync(path.join(root, cssFile), 'utf8');
  for (const m of text.matchAll(/url\((?!['"]?data:|['"]?https?:|['"]?#)(['"]?)([^'")]+)\1\)/g)) {
    const rel = path.posix.join(path.posix.dirname(cssFile), m[2]);
    assert.ok(fs.existsSync(path.join(root, rel)), `CSS 引用的资源缺失: ${m[2]}（位于 ${cssFile}）`);
  }
}

console.log('通过：构建产物存在、相对路径引用、静态资源完整（浏览器级回归见 test:browser，本地运行）。');
