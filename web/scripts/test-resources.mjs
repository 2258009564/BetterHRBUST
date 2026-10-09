import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../../shared/resources.json', import.meta.url));
const android = readFileSync(new URL('../../android/app/src/main/assets/resources.json', import.meta.url));
assert.deepEqual(source, android, '网页与 Android 必须使用同一份目录');
const catalog = JSON.parse(source);
assert.equal(catalog.categories.length, 9);
assert.equal(catalog.items.length, catalog.categories.reduce((count, category) => count + category.count, 0));
assert.equal(new Set(catalog.items.map(item => item.category + '|' + item.id)).size, catalog.items.length);
for (const item of catalog.items) {
  assert.ok(item.title && item.category && /^\d{4}-\d{2}-\d{2}$/.test(item.date));
  for (const url of [item.url, ...item.attachments.map(file => file.url)]) {
    const parsed = new URL(url);
    assert.ok(['http:', 'https:'].includes(parsed.protocol));
    assert.ok(parsed.hostname.endsWith('.hrbust.edu.cn'));
    assert.ok(!parsed.username && !parsed.password && !/jsessionid/i.test(url));
  }
}
assert.ok(catalog.items.some(item => item.title.includes('补办学生证')));
assert.ok(catalog.items.some(item => item.title.includes('四六级')));
const wrapper = readFileSync(new URL('./wrap-userscript.mjs', import.meta.url), 'utf8');
assert.ok(wrapper.includes('// @match        *://jwzx.hrbust.edu.cn/academic/*'), '油猴不得接管教务处公开原文与附件页面');
console.log(`通过：${catalog.categories.length} 类、${catalog.items.length} 条资料，网页/Android 一致，来源和附件链接完整。`);
