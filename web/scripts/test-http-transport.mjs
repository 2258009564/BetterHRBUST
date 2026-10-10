import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/services/academic/client.js', import.meta.url), 'utf8')
  .replace(/^export /gm, '');
const calls = [];
const pageFetch = async (url, options) => {
  calls.push({ url, options });
  return new Response('<html><script>const failureLabel="验证码错误";</script>authenticated course page</html>', {
    status: 200, headers: { 'content-type': 'text/html' }
  });
};
const context = vm.createContext({
  location: new URL('http://jwzx.hrbust.edu.cn/academic/student/currcourse/currcourse.jsdo'),
  unsafeWindow: { fetch: pageFetch },
  fetch: () => { throw new Error('Sandbox fetch must not be used'); },
  GM_xmlhttpRequest: () => { throw new Error('HTTP must not use extension transport'); },
  URL, URLSearchParams, FormData, TextDecoder, ArrayBuffer, Date,
  AbortController, setTimeout, clearTimeout
});
vm.runInContext(source, context);
const loginLabels = '<label>教务密码</label><label>安全验证码</label>';
assert.equal(context.parseLoginFailureReason(loginLabels + '<p>用户名或密码错误</p>'), '学号或密码错误');
assert.equal(context.parseLoginFailureReason(loginLabels + '<p>badCredentials</p>'), '学号或密码错误');
assert.equal(context.parseLoginFailureReason(loginLabels + '<p>用户不存在</p>'), '该学号不存在');
assert.equal(context.parseLoginFailureReason(loginLabels + '<p>验证码已过期</p>'), '验证码错误或已过期，请刷新重试');
assert.equal(context.parseLoginFailureReason(loginLabels + '<script>const msg="验证码错误";</script>'), '登录失败，请检查学号与密码');

await context.request('student/currcourse/currcourse.jsdo');
assert.equal(calls[0].url, 'http://jwzx.hrbust.edu.cn/academic/student/currcourse/currcourse.jsdo');
assert.equal(calls[0].options.credentials, 'include');
assert.equal((await context.postLogin('test', 'test', '0000')).success, true);
assert.equal(calls[1].options.method, 'POST');
assert.equal(calls[1].options.body.get('j_captcha'), '0000');
assert.equal(calls.length, 2);

context.unsafeWindow.fetch = async () => { throw new Error('network failure'); };
// A separate context captures the failing fetch when the module initializes.
const failure = vm.createContext({ ...context, unsafeWindow: {
  fetch: async () => { throw new Error('network failure'); }
} });
vm.runInContext(source, failure);
await assert.rejects(failure.postLogin('test', 'test', '0000'), /network failure/);
assert.equal(calls.length, 2, 'A failed login must not be replayed through another transport');

const redirectCalls = [];
let authenticated = true;
const redirected = vm.createContext({ ...context, unsafeWindow: { fetch: async (url, options) => {
  redirectCalls.push({ url, options });
  if (options.method === 'POST') return { type: 'opaqueredirect' };
  return new Response(authenticated ? '<html>course page</html>' :
    '<form action="j_acegi_security_check"><input name="j_captcha"></form>', { status: 200 });
} } });
vm.runInContext(source, redirected);
assert.equal((await redirected.postLogin('test', 'test', '0000')).success, true);
assert.equal(redirectCalls[0].options.redirect, 'manual');
assert.equal(redirectCalls[1].options.method, 'GET');
assert.equal(redirectCalls[1].url, 'http://jwzx.hrbust.edu.cn/academic/student/currcourse/currcourse.jsdo');
authenticated = false;
assert.equal((await redirected.postLogin('test', 'test', '0000')).success, false);
const evaluation = await redirected.request('eva/index/save.jsdo', { method: 'POST', body: 'rating=3', checkAuth: false });
assert.equal(evaluation.status, 202);
assert.equal(evaluation.html, '');
assert.equal(redirectCalls.filter(call => call.options.method === 'POST').length, 3);
assert.equal(redirected.isLoginPage('<script>var captcha="getCaptcha.do";</script><table>course</table>'), false);
console.log('PASS: HTTP origin, shared cookies, GET/POST, no replay, manual redirect, login validation, pending evaluation');

const { describeLoginFailure } = await import('../../tools/probe/lib/auth.mjs');
assert.equal(describeLoginFailure(loginLabels + '<p>用户名或密码错误</p>'), '学号或密码错误');
assert.equal(describeLoginFailure(loginLabels + '<p>验证码已过期</p>'), '验证码错误或已过期');
assert.equal(describeLoginFailure(loginLabels + '<script>const msg="验证码错误";</script>'), '登录失败，请检查学号与密码');

const { isLoginPage: probeIsLoginPage } = await import('../../tools/probe/lib/auth.mjs');
assert.equal(probeIsLoginPage('<script>const url="getCaptcha.do";</script><p>已登录</p>'),false);
assert.equal(probeIsLoginPage('<form action="j_acegi_security_check"><input name="j_captcha"></form>'),true);
