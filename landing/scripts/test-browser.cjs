const assert = require('node:assert/strict');
const { createServer } = require('node:http');
const { readFileSync, existsSync, mkdirSync } = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
const root = path.resolve(__dirname, '../dist');
const prefix = '/BetterHRBUST/';
const output = process.env.PREVIEW_OUTPUT_DIR;
const server = createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  const relative = pathname.slice(prefix.length) || 'index.html';
  const file = path.resolve(root, relative);
  if (!pathname.startsWith(prefix) || !file.startsWith(root + path.sep) || !existsSync(file)) { res.writeHead(404); res.end(); return; }
  const type = file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.png') ? 'image/png' : file.endsWith('.svg') ? 'image/svg+xml' : 'text/html';
  res.writeHead(200, { 'content-type': type }); res.end(readFileSync(file));
});
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const errors = [];
  try {
    for (const viewport of [{ width: 1440, height: 1050 }, { width: 390, height: 844 }]) {
      const page = await browser.newPage({ viewport, reducedMotion: 'reduce' });
      page.on('pageerror', error => errors.push(error.message));
      await page.route('https://api.github.com/repos/Glassous/BetterHRBUST/releases/latest', route => route.fulfill({
        contentType: 'application/json', body: JSON.stringify({ tag_name: 'v1.0.0', assets: [
          { name: 'BetterHRBUST-1.0.0-android.apk', browser_download_url: 'https://github.com/Glassous/BetterHRBUST/releases/download/v1.0.0/BetterHRBUST-1.0.0-android.apk' },
          { name: 'BetterHRBUST-1.0.0-windows-setup.exe', browser_download_url: 'https://github.com/Glassous/BetterHRBUST/releases/download/v1.0.0/BetterHRBUST-1.0.0-windows-setup.exe' }
        ] })
      }));
      await page.goto(`http://127.0.0.1:${server.address().port}${prefix}`, { waitUntil: 'networkidle' });
      assert.equal(await page.locator('h1').innerText(), 'BetterHRBUST');
      for (const image of await page.locator('img').all()) {
        if (await image.isVisible()) await image.scrollIntoViewIfNeeded();
        await image.evaluate(img => img.decode());
      }
      assert.equal(await page.locator('img').evaluateAll(images => images.every(image => image.complete && image.naturalWidth > 0)), true, '子路径下图片必须正确加载');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, '不得出现横向溢出');
      assert.match(await page.getByRole('link', { name: '下载 Android ↗' }).getAttribute('href'), /releases\/download\/v1\.0\.0\/.*\.apk$/);
      await page.getByRole('button', { name: '切换深色主题' }).click();
      assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
      assert.deepEqual(await page.locator('.client-demo').evaluateAll(elements => elements.map(element => element.dataset.view)), ['dashboard', 'timetable', 'gpa', 'resources', 'evaluation']);
      if (viewport.width > 800) {
        const copyBox = await page.locator('.overview-row .section-heading').boundingBox();
        const demoBox = await page.locator('.workspace-preview').boundingBox();
        assert.ok(demoBox.x >= copyBox.x + copyBox.width, '桌面概览必须位于文字右侧');
        assert.ok(Math.abs((demoBox.y + demoBox.height / 2) - (copyBox.y + copyBox.height / 2)) < 2);
        const positions = ['R'];
        for (const row of await page.locator('.feature-row').all()) {
          const copy = await row.locator('.feature-copy').boundingBox();
          const preview = await row.locator('.app-preview, .visual-panel').boundingBox();
          positions.push(preview.x > copy.x ? 'R' : 'L');
        }
        assert.equal(positions.join(''), 'RLRLRL', '展示的图片必须左右交替');
      }
      assert.equal(await page.locator('.client-demo button, .client-demo input, .client-demo select, .client-demo a').count(), 0, '界面展示必须保持静态');
      const dashboard = page.locator('.client-demo[data-view="dashboard"]');
      await dashboard.scrollIntoViewIfNeeded();
      const frame = page.frameLocator('.client-demo[data-view="dashboard"] iframe');
      await frame.getByText('已获得学分 / 方案总学分').waitFor();
      assert.equal(await frame.getByText('林同学，今天也从容一点。').count(), 0);
      assert.ok(await frame.getByText('林同学', { exact: true }).isVisible());
      assert.ok(await page.locator('a[href="https://github.com/Glassous/BetterHRBUST/graphs/contributors?all=1"]').isVisible());
      assert.ok(await page.locator('a[href="https://opensource.org/license/mit"]').isVisible());
      assert.equal(await page.locator('.feature-copy .tags').filter({ hasText: '每周课表' }).count(), 0);
      const gradeDemo = page.locator('.client-demo[data-view="gpa"]');
      await gradeDemo.scrollIntoViewIfNeeded();
      const scores = page.frameLocator('.client-demo[data-view="gpa"] iframe');
      await scores.getByText('总评成绩', { exact:true }).waitFor();
      const wrapping = await scores.locator('th').evaluateAll(headers => headers.some(header => getComputedStyle(header).whiteSpace !== 'nowrap'));
      assert.equal(wrapping, false, '真实成绩表头不能拆成单字行');
      const resourcesDemo = page.locator('.client-demo[data-view="resources"]');
      await resourcesDemo.scrollIntoViewIfNeeded();
      await page.frameLocator('.client-demo[data-view="resources"] iframe').getByText(/共 97 条结果/).waitFor();
      await page.locator('.client-demo[data-view="evaluation"]').scrollIntoViewIfNeeded();
      await page.frameLocator('.client-demo[data-view="evaluation"] iframe').getByText('林老师').waitFor();
      await page.getByRole('button', { name: '切换浅色主题' }).click();
      await frame.locator('html:not(.dark)').waitFor();
      await page.evaluate(() => scrollTo(0, 0));
      if (output) {
        mkdirSync(output, { recursive: true });
        await page.screenshot({ path: path.join(output, `release-${viewport.width}.png`), fullPage: true });
        if (viewport.width === 1440) await page.screenshot({ path: path.join(output, 'release-hero.png') });
      }
      await page.close();
    }
    const fallback = await browser.newPage();
    await fallback.route('https://api.github.com/**', route => route.abort());
    await fallback.goto(`http://127.0.0.1:${server.address().port}${prefix}`, { waitUntil: 'networkidle' });
    assert.equal(await fallback.getByRole('link', { name: '下载 Android ↗' }).getAttribute('href'), 'https://github.com/Glassous/BetterHRBUST/releases/latest');
    const animated = await browser.newPage({ viewport: { width: 1440, height: 1050 }, reducedMotion: 'no-preference' });
    animated.on('pageerror', error => errors.push(error.message));
    await animated.route('https://api.github.com/**', route => route.abort());
    await animated.goto(`http://127.0.0.1:${server.address().port}${prefix}`, { waitUntil: 'networkidle' });
    await animated.waitForFunction(() => [...document.querySelectorAll('.hero-letter')].every(letter => Number(getComputedStyle(letter).opacity) > .95));
    await animated.locator('.feature-row').first().scrollIntoViewIfNeeded();
    await animated.waitForFunction(() => [...document.querySelector('.feature-copy').children].every(element => Number(getComputedStyle(element).opacity) > .95));
    await animated.emulateMedia({ reducedMotion: 'reduce' });
    await animated.waitForFunction(() => !document.querySelector('.hero-letter').style.transform && !document.querySelector('.shape').style.transform);
    assert.equal(await animated.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    assert.deepEqual(errors, []);
    console.log('通过：1440/390 布局、项目子路径、概览/课表/GPA/资料/评教真实视图、真实渲染截图与算法示例、双主题、下载回退、入场与滚动动效、减少动态效果切换，无页面异常。');
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
