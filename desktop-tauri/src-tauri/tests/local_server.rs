//! 本地服务集成测试（移植 desktop/scripts/smoke.mjs 的全部场景并增强）
//!
//! 前置条件：web/dist 已构建（rust-embed 在 debug 模式从磁盘读取）。
//! 反代测试用本地 mock 教务系统（临时端口）替代真实上游，
//! 断言 Host/Referer 注入、Cookie 透传、Set-Cookie 域剥离与 302 重写——
//! 不依赖校园网即可完整验证代理规则。

use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use http::{header, Request, Response, StatusCode};
use http_body_util::{BodyExt, Full};
use hyper::body::{Bytes, Incoming};
use hyper_util::client::legacy::Client;
use hyper_util::client::legacy::connect::HttpConnector;
use hyper_util::rt::{TokioExecutor, TokioIo};
use hyper_util::server::conn::auto;
use tokio::net::TcpListener;

use betterhrbust_desktop_lib::server::{self, service_fn, ServerConfig, Upstream};

type TestClient = Client<HttpConnector, Full<Bytes>>;

/// mock 上游记录到的关键请求头快照
#[derive(Default)]
struct SeenHeaders {
    host: Option<String>,
    referer: Option<String>,
    cookie: Option<String>,
}

fn web_dist() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../web/dist")
}

fn require_web_dist() {
    let index = web_dist().join("index.html");
    assert!(
        index.exists(),
        "缺少前端构建产物 {}：请先在 web/ 目录执行 npm install && npm run build",
        index.display()
    );
}

async fn spawn_mock_upstream() -> (String, Arc<Mutex<SeenHeaders>>) {
    let listener = TcpListener::bind(("127.0.0.1", 0)).await.unwrap();
    let addr = listener.local_addr().unwrap();
    let seen = Arc::new(Mutex::new(SeenHeaders::default()));
    let seen_task = seen.clone();

    tokio::spawn(async move {
        loop {
            let (stream, _) = match listener.accept().await {
                Ok(accepted) => accepted,
                Err(_) => continue,
            };
            let seen = seen_task.clone();
            tokio::spawn(async move {
                let service = service_fn(move |req: Request<Incoming>| {
                    let seen = seen.clone();
                    async move {
                        {
                            let mut guard = seen.lock().unwrap();
                            guard.host = req
                                .headers()
                                .get(header::HOST)
                                .and_then(|v| v.to_str().ok())
                                .map(String::from);
                            guard.referer = req
                                .headers()
                                .get(header::REFERER)
                                .and_then(|v| v.to_str().ok())
                                .map(String::from);
                            guard.cookie = req
                                .headers()
                                .get(header::COOKIE)
                                .and_then(|v| v.to_str().ok())
                                .map(String::from);
                        }
                        if req.uri().path() == "/academic/redirect" {
                            // 模拟教务系统 302：绝对 Location + 带 Domain 的会话 Cookie
                            Ok::<_, std::convert::Infallible>(
                                Response::builder()
                                    .status(StatusCode::FOUND)
                                    .header(
                                        header::LOCATION,
                                        "http://jwzx.hrbust.edu.cn/academic/index.jsp",
                                    )
                                    .header(
                                        header::SET_COOKIE,
                                        "JSESSIONID=mock123; Domain=hrbust.edu.cn; Path=/academic",
                                    )
                                    .body(Full::new(Bytes::new()))
                                    .unwrap(),
                            )
                        } else {
                            // 模拟 GBK 页面：代理必须字节透传，不得改写 Content-Type
                            Ok(Response::builder()
                                .header(header::CONTENT_TYPE, "text/html;charset=GBK")
                                .body(Full::new(Bytes::from_static(
                                    b"<html><body>GBK \xd2\xb3\xc3\xe6</body></html>",
                                )))
                                .unwrap())
                        }
                    }
                });
                let _ = auto::Builder::new(TokioExecutor::new())
                    .serve_connection(TokioIo::new(stream), service)
                    .await;
            });
        }
    });

    (format!("http://{addr}"), seen)
}

/// 以指定上游 + 随机端口启动被测服务
async fn spawn_server(upstream_origin: &str) -> String {
    let config = ServerConfig {
        upstream: Upstream::parse(upstream_origin),
        port_base: 0,
        port_tries: 1,
    };
    let addr = server::start(config).await.expect("本地服务启动失败");
    format!("http://{addr}")
}

fn client() -> TestClient {
    Client::builder(TokioExecutor::new()).build_http()
}

async fn get(
    url: &str,
    extra: &[(&str, &str)],
) -> Result<Response<Incoming>, hyper_util::client::legacy::Error> {
    let mut builder = Request::builder().uri(url);
    for (name, value) in extra {
        builder = builder.header(*name, *value);
    }
    let request = builder.body(Full::new(Bytes::new())).unwrap();
    tokio::time::timeout(Duration::from_secs(10), client().request(request))
        .await
        .expect("请求超时")
}

async fn body_text(res: Response<Incoming>) -> String {
    let bytes = res.into_body().collect().await.unwrap().to_bytes();
    String::from_utf8_lossy(&bytes).into_owned()
}

// ---------------------------------------------------------------- 静态托管

#[tokio::test]
async fn serves_index_html() {
    require_web_dist();
    let origin = spawn_server("http://127.0.0.1:1").await;
    let res = get(&format!("{origin}/"), &[]).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    assert!(res.headers()[header::CONTENT_TYPE].to_str().unwrap().contains("text/html"));
    assert!(body_text(res).await.contains("<html"));
}

#[tokio::test]
async fn assets_are_long_cached() {
    require_web_dist();
    let assets_dir = web_dist().join("assets");
    let first_js = std::fs::read_dir(&assets_dir)
        .expect("web/dist/assets 不存在")
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .find(|name| name.ends_with(".js"))
        .expect("assets 下无 js 产物");
    let origin = spawn_server("http://127.0.0.1:1").await;
    let res = get(&format!("{origin}/assets/{first_js}"), &[]).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    assert!(res.headers()[header::CONTENT_TYPE].to_str().unwrap().contains("javascript"));
    assert!(res.headers()[header::CACHE_CONTROL]
        .to_str()
        .unwrap()
        .contains("immutable"));
}

#[tokio::test]
async fn spa_fallback_for_extensionless_routes() {
    require_web_dist();
    let origin = spawn_server("http://127.0.0.1:1").await;
    let res = get(&format!("{origin}/some/unknown/route"), &[]).await.unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    assert!(res.headers()[header::CONTENT_TYPE].to_str().unwrap().contains("text/html"));
}

#[tokio::test]
async fn missing_file_with_extension_is_404() {
    require_web_dist();
    let origin = spawn_server("http://127.0.0.1:1").await;
    let res = get(&format!("{origin}/no-such-file.png"), &[]).await.unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn path_traversal_is_forbidden() {
    require_web_dist();
    let origin = spawn_server("http://127.0.0.1:1").await;
    let res = get(&format!("{origin}/..%2f..%2fdesktop-tauri%2fpackage.json"), &[])
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::FORBIDDEN);
}

// ---------------------------------------------------------------- 反向代理

#[tokio::test]
async fn academic_proxy_injects_headers_and_streams_body() {
    require_web_dist();
    let (upstream, seen) = spawn_mock_upstream().await;
    let origin = spawn_server(&upstream).await;

    let res = get(
        &format!("{origin}/academic/index.jsp"),
        &[("cookie", "JSESSIONID=prev-session")],
    )
    .await
    .unwrap();

    assert_eq!(res.status(), StatusCode::OK);
    // 字节透传：Content-Type 原样保留（GBK 由前端解码）
    assert!(res.headers()[header::CONTENT_TYPE].to_str().unwrap().contains("GBK"));
    assert!(body_text(res).await.contains("<html>"));

    let guard = seen.lock().unwrap();
    // changeOrigin：上游收到的 Host 是上游自己的地址（生产环境即教务域名）
    let expected_authority = upstream.trim_start_matches("http://");
    assert_eq!(guard.host.as_deref(), Some(expected_authority));
    // Referer 注入
    let expected_referer = format!("{upstream}/academic/");
    assert_eq!(guard.referer.as_deref(), Some(expected_referer.as_str()));
    // 会话 Cookie 透传
    assert_eq!(guard.cookie.as_deref(), Some("JSESSIONID=prev-session"));
}

#[tokio::test]
async fn academic_redirect_location_and_cookie_domain_rewritten() {
    require_web_dist();
    let (upstream, _seen) = spawn_mock_upstream().await;
    let origin = spawn_server(&upstream).await;

    // 不跟随重定向（legacy client 默认行为），直接检查 302 响应
    let res = get(&format!("{origin}/academic/redirect"), &[]).await.unwrap();
    assert_eq!(res.status(), StatusCode::FOUND);
    assert_eq!(
        res.headers()[header::LOCATION].to_str().unwrap(),
        "/academic/index.jsp"
    );
    // Domain 剥离 + 会话 Cookie 注入 Max-Age（跨冷启动持久化的关键）
    assert_eq!(
        res.headers()[header::SET_COOKIE].to_str().unwrap(),
        "JSESSIONID=mock123; Path=/academic; Max-Age=604800"
    );
}

#[tokio::test]
async fn unreachable_upstream_aborts_connection() {
    require_web_dist();
    // 端口 1 几乎必然无监听：模拟非校园网环境教务系统不可达
    let origin = spawn_server("http://127.0.0.1:1").await;
    let result = get(&format!("{origin}/academic/anything"), &[]).await;
    // 关键语义：连接被掐断（fetch reject），而非收到 502 响应
    assert!(result.is_err(), "上游不可达时必须断开连接，实际得到: {:?}", result.map(|r| r.status()));
}

// ---------------------------------------------------------------- 端口策略

#[tokio::test]
async fn port_falls_back_when_occupied() {
    // 占住 1950，服务应顺延绑定 1951
    let occupant = std::net::TcpListener::bind(("127.0.0.1", server::PORT_BASE)).unwrap();
    let config = ServerConfig {
        upstream: Upstream::parse("http://127.0.0.1:1"),
        port_base: server::PORT_BASE,
        port_tries: server::PORT_TRIES,
    };
    let addr = server::start(config).await.expect("端口顺延后应绑定成功");
    assert_eq!(addr.port(), server::PORT_BASE + 1);
    drop(occupant);
}
