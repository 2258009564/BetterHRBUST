//! 教务系统反向代理（/academic/* → http://jwzx.hrbust.edu.cn）
//!
//! 规则 1:1 平移自 web/vite.config.mjs 的 academicProxy 与 desktop/src/academic-proxy.mjs：
//!  - Host 声明为教务系统域名（等价 node http-proxy 的 changeOrigin）
//!  - Referer 注入：教务系统校验该请求头
//!  - Set-Cookie 剥离 Domain 属性，使会话 Cookie 落在 127.0.0.1
//!  - 3xx 的绝对 Location 重写为 /academic/ 相对路径
//!    （教务系统仅有 HTTP(80)，绝对地址会被现代浏览器 HTTPS 自动升级
//!     或跨域拦截，导致 ERR_CONNECTION_CLOSED）
//!  - 上游超时 20s（与 tools/probe 的探测超时对齐）
//!
//! 字节流式透传，不碰编码（GBK 解码由前端 client.js 完成）。
//!
//! 错误语义（关键）：上游连接失败/超时时返回 Err 而非 502 响应，
//! hyper 会直接断开 TCP 连接 → 前端 fetch reject → 复用 client.js
//! 既有的「网络连接失败……请确认是否处于校园网或VPN环境」提示路径。
//! （Electron 版通过 res.destroy(err) 达到同样效果）

use std::time::Duration;

use http::{header, HeaderValue, Method, Request, Response};
use http_body_util::BodyExt;
use hyper::body::Incoming;
use hyper_util::client::legacy::Client;

use crate::server::{empty_body, AppBody, GatewayError, Upstream};

/// 逐跳头：代理在两个方向都必须剥离（RFC 9110 7.6.1）
const HOP_BY_HOP: &[&str] = &[
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailer",
    "transfer-encoding",
    "upgrade",
];

/// 转发前需要剥离并重新构造的头（Host/Referer 由本代理注入，
/// Content-Length 由 hyper 按实际 body 重新分帧）
const REBUILD: &[&str] = &["host", "referer", "content-length"];

/// 上游总超时
const UPSTREAM_TIMEOUT: Duration = Duration::from_secs(20);

/// 反代转发的统一错误：任何上游故障都表现为 Err（连接被 hyper 掐断）
pub type ProxyResult = Result<Response<AppBody>, GatewayError>;

/// 转发一个 /academic 请求到教务系统
pub async fn forward(
    client: &Client<hyper_util::client::legacy::connect::HttpConnector, AppBody>,
    upstream: &Upstream,
    req: Request<Incoming>,
) -> ProxyResult {
    let fut = forward_inner(client, upstream, req);
    match tokio::time::timeout(UPSTREAM_TIMEOUT, fut).await {
        Ok(result) => result,
        Err(_) => Err(GatewayError::Timeout),
    }
}

async fn forward_inner(
    client: &Client<hyper_util::client::legacy::connect::HttpConnector, AppBody>,
    upstream: &Upstream,
    req: Request<Incoming>,
) -> ProxyResult {
    // 上游 URI = 教务系统 origin + 原样 path+query（hyper client 需要绝对形式）
    let path_and_query = req
        .uri()
        .path_and_query()
        .map(|pq| pq.as_str().to_string())
        .unwrap_or_else(|| "/academic/".to_string());
    let upstream_uri = upstream.uri_for(&path_and_query);

    let mut builder = Request::builder()
        .method(req.method().clone())
        .uri(upstream_uri)
        // changeOrigin 语义：向上游声明 Host 为教务系统域名
        .header(header::HOST, upstream.host_header())
        // 教务系统校验 Referer
        .header(header::REFERER, upstream.referer());

    // 复制其余请求头（Cookie、Content-Type、Accept 等），跳过逐跳头
    let headers = builder.headers_mut().expect("builder with headers");
    for (name, value) in req.headers() {
        let lower = name.as_str().to_ascii_lowercase();
        if HOP_BY_HOP.contains(&lower.as_str()) || REBUILD.contains(&lower.as_str()) {
            continue;
        }
        headers.append(name, value.clone());
    }

    // HEAD 无 body，直接发空体；其余流式透传原 body
    let body = req.into_body();
    let upstream_req = if matches!(builder.method_ref(), Some(&Method::HEAD)) {
        builder
            .body(empty_body())
            .map_err(GatewayError::Build)?
    } else {
        builder
            .body(body.map_err(GatewayError::Body).boxed())
            .map_err(GatewayError::Build)?
    };

    let response = client.request(upstream_req).await.map_err(GatewayError::Request)?;

    Ok(rewrite_response(response))
}

/// 响应侧改写：剥离逐跳头、Set-Cookie 去掉 Domain 属性、3xx Location 重写为相对路径
fn rewrite_response(mut res: Response<Incoming>) -> Response<AppBody> {
    let headers = res.headers_mut();

    for name in HOP_BY_HOP {
        headers.remove(*name);
    }
    // 让 hyper 按流式 body 重新分帧
    headers.remove(header::CONTENT_LENGTH);

    // Set-Cookie: 剥离 Domain 属性（等价 node http-proxy cookieDomainRewrite: ''）
    let cookies: Vec<HeaderValue> = headers
        .get_all(header::SET_COOKIE)
        .iter()
        .filter_map(|v| v.to_str().ok())
        .map(strip_cookie_domain)
        .filter_map(|s| HeaderValue::from_str(&s).ok())
        .collect();
    if !cookies.is_empty() {
        headers.remove(header::SET_COOKIE);
        for cookie in cookies {
            headers.append(header::SET_COOKIE, cookie);
        }
    }

    // 3xx Location: 绝对地址 http://jwzx.hrbust.edu.cn/academic/... → /academic/...
    if let Some(location) = headers.get(header::LOCATION).and_then(|v| v.to_str().ok()) {
        if let Some(rewritten) = rewrite_location(location) {
            if let Ok(value) = HeaderValue::from_str(&rewritten) {
                headers.insert(header::LOCATION, value);
            }
        }
    }

    let (parts, body) = res.into_parts();
    Response::from_parts(parts, body.map_err(GatewayError::Body).boxed())
}

/// 从单个 Set-Cookie 值中移除 Domain=... 属性（大小写不敏感）。
/// 例：`JSESSIONID=x; Domain=hrbust.edu.cn; Path=/academic` → `JSESSIONID=x; Path=/academic`
fn strip_cookie_domain(cookie: &str) -> String {
    cookie
        .split(';')
        .filter(|part| !part.trim_start().to_ascii_lowercase().starts_with("domain="))
        .collect::<Vec<&str>>()
        .join(";")
}

/// 判断绝对地址是否指向教务系统的 /academic 路径，是则改写为相对路径。
/// 匹配 `http(s)://jwzx.hrbust.edu.cn[:port]/academic[/...]`（域名大小写不敏感），
/// 对应 vite.config.mjs 的正则 `^https?://jwzx\.hrbust\.edu\.cn(?::\d+)?/academic/?`。
fn rewrite_location(location: &str) -> Option<String> {
    let scheme_end = location.find("://")?;
    let rest = &location[scheme_end + 3..];
    let authority_end = rest.find(['/', '?', '#']).unwrap_or(rest.len());
    let authority = &rest[..authority_end];
    // 去掉可选端口后与教务域名比对
    let host = authority.rsplit_once(':').map_or(authority, |(h, _)| h);
    if !host.eq_ignore_ascii_case("jwzx.hrbust.edu.cn") {
        return None;
    }
    // authority 之后必须是 /academic 路径（紧跟 ?/# 或为空都不改写）
    let tail = rest[authority_end..].strip_prefix('/')?;
    if tail.is_empty() {
        // `...edu.cn/` 形式：正则要求 /academic，不匹配则不改写
        return None;
    }
    if !tail.eq_ignore_ascii_case("academic") && !tail.to_ascii_lowercase().starts_with("academic/") {
        return None;
    }
    Some(format!("/{tail}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cookie_domain_stripped() {
        assert_eq!(
            strip_cookie_domain("JSESSIONID=abc; Domain=hrbust.edu.cn; Path=/academic"),
            "JSESSIONID=abc; Path=/academic"
        );
        assert_eq!(
            strip_cookie_domain("JSESSIONID=abc; domain=HRBUST.edu.cn; HttpOnly"),
            "JSESSIONID=abc; HttpOnly"
        );
        assert_eq!(strip_cookie_domain("JSESSIONID=abc"), "JSESSIONID=abc");
    }

    #[test]
    fn location_rewritten_to_relative() {
        assert_eq!(
            rewrite_location("http://jwzx.hrbust.edu.cn/academic/index.jsp").as_deref(),
            Some("/academic/index.jsp")
        );
        assert_eq!(
            rewrite_location("http://JWZX.hrbust.edu.cn:80/academic/").as_deref(),
            Some("/academic/")
        );
        assert_eq!(
            rewrite_location("https://jwzx.hrbust.edu.cn/academic/index.jsp?x=1").as_deref(),
            Some("/academic/index.jsp?x=1")
        );
    }

    #[test]
    fn location_left_alone_when_not_academic() {
        assert_eq!(rewrite_location("http://jwzx.hrbust.edu.cn/other/page"), None);
        assert_eq!(rewrite_location("http://evil.example.com/academic/x"), None);
        assert_eq!(rewrite_location("http://jwzx.hrbust.edu.cn/"), None);
        assert_eq!(rewrite_location("http://jwzx.hrbust.edu.cn"), None);
    }
}
