/**
 * 教务系统反向代理（/academic → http://jwzx.hrbust.edu.cn）
 *
 * 规则 1:1 平移自 web/vite.config.mjs 的 academicProxy：
 *  - changeOrigin：向上游声明 Host 为教务系统域名
 *  - autoRewrite：3xx 跳转的 host 部分改写回本地代理 host
 *  - cookieDomainRewrite: ''：剥离 Set-Cookie 的 Domain 属性，
 *    使会话 Cookie 落在 127.0.0.1（与 dev 环境的 localhost 行为一致）
 *  - Host/Referer 注入：教务系统校验这两个请求头
 *  - proxyRes：把 302 的绝对 Location 重写为 /academic/ 相对路径
 *    （教务系统仅有 HTTP(80)，绝对地址会被现代浏览器 HTTPS 自动升级
 *     或跨域拦截，导致 ERR_CONNECTION_CLOSED）
 *
 * 前端 web/src/services/academic/client.js 已在浏览器侧完成
 * GBK 解码与会话判定，本代理只做字节透传，不碰编码。
 */
import httpProxy from 'http-proxy';

const UPSTREAM = 'http://jwzx.hrbust.edu.cn';
const ACADEMIC_ORIGIN_RE = /^https?:\/\/jwzx\.hrbust\.edu\.cn(?::\d+)?\/academic\/?/i;

/** 上游超时：与 tools/probe 的 20s 探测超时对齐，超时后中断 socket 让前端给出提示 */
const PROXY_TIMEOUT_MS = 20000;

/**
 * 创建教务系统反向代理实例
 * @returns {import('http-proxy').Proxy}
 */
export function createAcademicProxy() {
  const proxy = httpProxy.createProxyServer({
    target: UPSTREAM,
    changeOrigin: true,
    autoRewrite: true,
    cookieDomainRewrite: '',
    headers: {
      Host: 'jwzx.hrbust.edu.cn',
      Referer: 'http://jwzx.hrbust.edu.cn/academic/'
    },
    proxyTimeout: PROXY_TIMEOUT_MS
  });

  proxy.on('proxyRes', (proxyRes) => {
    const location = proxyRes.headers['location'];
    if (location) {
      // 关键：教务系统 302 跳转会带上完整域名 http://jwzx.hrbust.edu.cn/academic/...
      // 统一重写为本地相对路径 /academic/...（与 vite.config.mjs 行为一致）
      proxyRes.headers['location'] = location.replace(ACADEMIC_ORIGIN_RE, '/academic/');
    }
  });

  proxy.on('error', (err, _req, res) => {
    // 不回 502 页面：直接销毁 socket，让前端 fetch reject，
    // 复用 client.js 既有的「网络连接失败……请确认是否处于校园网或VPN环境」提示路径
    if (res && typeof res.destroy === 'function') {
      res.destroy(err);
    } else if (res && res.socket) {
      res.socket.destroy();
    }
  });

  return proxy;
}

/**
 * 判断请求路径是否属于教务系统反代范围
 * @param {string} pathname URL pathname
 * @returns {boolean}
 */
export function isAcademicPath(pathname) {
  return pathname === '/academic' || pathname.startsWith('/academic/');
}
