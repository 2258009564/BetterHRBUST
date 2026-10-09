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
      assert.deepEqual(await page.locator('.client-demo').evaluateAll(elements => elements.map(element => element.dataset.view)), ['dashboard', 'timetable', 'gpa', 'resources']);
      const sidebar = page.locator('.client-demo[data-view="dashboard"] .demo-sidebar');
      assert.deepEqual(await sidebar.locator('.demo-nav-label').allTextContents(), ['教务核心', '培养与资源', '信息与系统']);
      assert.equal(await sidebar.locator('.demo-nav > span').count(), 12);
      assert.match(await sidebar.innerText(), /智能课程表/);
      assert.match(await sidebar.innerText(), /成绩与GPA分析/);
      if (viewport.width > 800) {
        const copyBox = await page.locator('.overview-row .section-heading').boundingBox();
        const demoBox = await page.locator('.workspace-preview').boundingBox();
        assert.ok(demoBox.x >= copyBox.x + copyBox.width, '桌面概览必须位于文字右侧');
        assert.ok(Math.abs((demoBox.y + demoBox.height / 2) - (copyBox.y + copyBox.height / 2)) < 2);
      }
      assert.equal(await page.locator('.client-demo button, .client-demo input, .client-demo select, .client-demo a').count(), 0, '界面展示必须保持静态');
      assert.ok(await page.locator('.client-demo[data-view="dashboard"]').getByText('林同学，今天也从容一点。').isVisible());
      assert.match(await page.locator('.client-demo[data-view="gpa"]').innerText(), /4\.23/);
      assert.match(await page.locator('.client-demo[data-view="resources"]').innerText(), /97 条公开资料/);
      const darkCard = await page.locator('.demo-metrics > div').first().evaluate(element => getComputedStyle(element).backgroundColor);
      await page.reload({ waitUntil: 'networkidle' });
      assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark', '主题选择在刷新后保留');
      await page.getByRole('button', { name: '切换浅色主题' }).click();
      const lightCard = await page.locator('.demo-metrics > div').first().evaluate(element => getComputedStyle(element).backgroundColor);
      assert.notEqual(lightCard, darkCard, '复用客户端卡片必须跟随页面明暗主题');
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
    console.log('通过：1440/390 布局、项目子路径、概览/课表/GPA/资料静态组件、真实算法示例、双主题、下载回退、入场与滚动动效、减少动态效果切换，无页面异常。');
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
