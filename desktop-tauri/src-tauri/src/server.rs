//! 本地 HTTP 服务（仅监听 127.0.0.1）
//!
//! 架构与 desktop/src/main.mjs（Electron 版）1:1 对应：
//!  - 静态文件：web/dist 构建产物（release 经 rust-embed 嵌入二进制，
//!    debug 模式直接读磁盘），SPA 回退、/assets 长缓存、路径穿越防护
//!  - /academic/* → 反向代理 → http://jwzx.hrbust.edu.cn（见 proxy.rs）
//!  - 端口策略：固定 1950 起（建校年份），被占用向后顺延至多 10 次。
//!    固定端口保证页面源（http://127.0.0.1:1950）跨冷启动稳定，
//!    localStorage 与教务会话 Cookie 因此持久——随机端口会使用户数据"消失"。
//!
//! 依赖刻意保持最小（hyper/tokio/rust-embed，不引 axum/tower 全家桶），
//! 以满足体积与内存目标：安装包 <= 10MB、主进程 RSS <= 25MB。

use std::io;
use std::net::SocketAddr;
use std::path::Path;

use http::{header, Method, Request, Response, StatusCode, Uri};
use http_body_util::combinators::BoxBody;
use http_body_util::{BodyExt, Full};
use hyper::body::{Bytes, Incoming};
use hyper_util::client::legacy::Client;
use hyper_util::rt::{TokioExecutor, TokioIo};
use hyper_util::server::conn::auto;
use rust_embed::RustEmbed;
use tokio::net::TcpListener;

pub use hyper::service::service_fn;

/// 教务系统 origin（仅 HTTP，无 443）
pub const UPSTREAM_ORIGIN: &str = "http://jwzx.hrbust.edu.cn";

/// 本地服务端口基值（建校年份），被占用时向后顺延
pub const PORT_BASE: u16 = 1950;
pub const PORT_TRIES: u16 = 10;

/// 统一响应体：静态用 Full，代理用流式，错误类型统一为 GatewayError
pub type AppBody = BoxBody<Bytes, GatewayError>;

pub fn full_body(bytes: Bytes) -> AppBody {
    Full::new(bytes).map_err(|infallible| match infallible {}).boxed()
}

pub fn empty_body() -> AppBody {
    Full::new(Bytes::new()).map_err(|infallible| match infallible {}).boxed()
}

/// /academic 转发失败的错误：从 service 冒泡给 hyper 后连接被直接断开
/// （不回 502 页面），让前端 fetch reject 走既有网络错误提示路径。
#[derive(Debug)]
pub enum GatewayError {
    Timeout,
    /// 发送上游请求失败（连接拒绝 / DNS / 池错误）
    Request(hyper_util::client::legacy::Error),
    /// 上游响应体读取中断
    Body(hyper::Error),
    Build(http::Error),
}

impl std::fmt::Display for GatewayError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            GatewayError::Timeout => write!(f, "upstream timeout"),
            GatewayError::Request(err) => write!(f, "upstream request error: {err}"),
            GatewayError::Body(err) => write!(f, "upstream body error: {err}"),
            GatewayError::Build(err) => write!(f, "request build error: {err}"),
        }
    }
}

impl std::error::Error for GatewayError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            GatewayError::Timeout => None,
            GatewayError::Request(err) => Some(err),
            GatewayError::Body(err) => Some(err),
            GatewayError::Build(err) => Some(err),
        }
    }
}

/// 上游教务系统描述：scheme + host + 可选端口
#[derive(Clone, Debug)]
pub struct Upstream {
    scheme: String,
    host: String,
    port: Option<u16>,
}

impl Upstream {
    /// 解析形如 "http://jwzx.hrbust.edu.cn" 的 origin
    pub fn parse(origin: &str) -> Self {
        let (scheme, rest) = origin.split_once("://").unwrap_or(("http", origin));
        let authority = rest.split(['/', '?', '#']).next().unwrap_or(rest);
        let (host, port) = match authority.rsplit_once(':') {
            Some((host, port)) => (host.to_string(), port.parse().ok()),
            None => (authority.to_string(), None),
        };
        Self { scheme: scheme.to_string(), host, port }
    }

    /// 发给上游的 Host 头（默认端口不显式携带，与 changeOrigin 行为一致）
    pub fn host_header(&self) -> String {
        match self.port {
            Some(port) => format!("{}:{}", self.host, port),
            None => self.host.clone(),
        }
    }

    /// 注入的 Referer（教务系统校验该头，规则来自 vite.config.mjs）
    pub fn referer(&self) -> String {
        format!("{}://{}/academic/", self.scheme, self.host_header())
    }

    /// 拼上游绝对 URI：origin + 原样 path+query
    pub fn uri_for(&self, path_and_query: &str) -> Uri {
        Uri::builder()
            .scheme(self.scheme.as_str())
            .authority(self.host_header().as_str())
            .path_and_query(path_and_query.to_string())
            .build()
            .expect("upstream uri 由固定 origin 与已校验 path 拼接，不会非法")
    }
}

/// 服务配置（测试用自定义 upstream / 端口）
#[derive(Clone, Debug)]
pub struct ServerConfig {
    pub upstream: Upstream,
    pub port_base: u16,
    pub port_tries: u16,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self::production()
    }
}

impl ServerConfig {
    pub fn production() -> Self {
        Self {
            upstream: Upstream::parse(UPSTREAM_ORIGIN),
            port_base: PORT_BASE,
            port_tries: PORT_TRIES,
        }
    }
}

/// 前端构建产物：release 嵌入二进制（单 exe 便携版的来源），
/// debug 模式 rust-embed 自动改为运行时读磁盘，方便前端重建后即时生效
#[derive(RustEmbed)]
#[folder = "../../web/dist"]
struct WebAssets;

/// 每请求共享的客户端状态（hyper 连接池可克隆复用）
#[derive(Clone)]
struct AppState {
    client: Client<hyper_util::client::legacy::connect::HttpConnector, AppBody>,
    upstream: Upstream,
}

/// 启动本地服务：绑定端口（含顺延逻辑）并进入 accept 循环。
/// 返回实际绑定的地址，供窗口加载。
pub async fn start(config: ServerConfig) -> io::Result<SocketAddr> {
    let listener = bind_listener(config.port_base, config.port_tries).await?;
    let addr = listener.local_addr()?;
    let state = AppState {
        client: Client::builder(TokioExecutor::new()).build_http::<AppBody>(),
        upstream: config.upstream,
    };

    tokio::spawn(async move {
        loop {
            let (stream, _peer) = match listener.accept().await {
                Ok(accepted) => accepted,
                Err(_) => continue,
            };
            let state = state.clone();
            tokio::spawn(async move {
                let service = service_fn(move |req| handle(state.clone(), req));
                // service 返回 Err 时 hyper 直接断开连接（不回 502），
                // 这正是反代上游故障时需要的行为
                let _ = auto::Builder::new(TokioExecutor::new())
                    .serve_connection(TokioIo::new(stream), service)
                    .await;
            });
        }
    });

    Ok(addr)
}

/// 端口绑定：port_base == 0 时直接用系统分配的临时端口（测试用）；
/// 否则从 port_base 起逐个尝试，仅 AddrInUse 顺延，其他错误直接抛出
async fn bind_listener(port_base: u16, port_tries: u16) -> io::Result<TcpListener> {
    if port_base == 0 {
        return TcpListener::bind(("127.0.0.1", 0)).await;
    }
    let mut last_err: Option<io::Error> = None;
    for offset in 0..port_tries {
        match TcpListener::bind(("127.0.0.1", port_base + offset)).await {
            Ok(listener) => return Ok(listener),
            Err(err) if err.kind() == io::ErrorKind::AddrInUse => {
                last_err = Some(err);
            }
            Err(err) => return Err(err),
        }
    }
    Err(last_err.unwrap_or_else(|| {
        io::Error::new(io::ErrorKind::AddrInUse, "无可用本地端口")
    }))
}

/// 总入口：/academic 走反代（错误冒泡断连），其余走静态托管（错误转 4xx/5xx 响应）
async fn handle(state: AppState, req: Request<Incoming>) -> Result<Response<AppBody>, GatewayError> {
    let path = req.uri().path();
    if path == "/academic" || path.starts_with("/academic/") {
        crate::proxy::forward(&state.client, &state.upstream, req).await
    } else {
        Ok(static_response(&req))
    }
}

/// 静态托管：MIME、缓存策略、SPA 回退、路径穿越防护
/// （行为对齐 desktop/src/local-server.mjs）
fn static_response(req: &Request<Incoming>) -> Response<AppBody> {
    if !matches!(*req.method(), Method::GET | Method::HEAD) {
        return plain(StatusCode::METHOD_NOT_ALLOWED, "Method Not Allowed");
    }

    let raw_path = req.uri().path();
    let decoded = match percent_encoding::percent_decode_str(raw_path).decode_utf8() {
        Ok(decoded) => decoded,
        Err(_) => return plain(StatusCode::BAD_REQUEST, "Bad Request"),
    };
    // 穿越防护：出现 .. 段一律拒绝（release 下资产嵌入二进制本就无法逃逸，
    // 该检查同时覆盖 debug 模式的磁盘读取）
    if decoded.split('/').any(|segment| segment == "..") {
        return plain(StatusCode::FORBIDDEN, "Forbidden");
    }

    let lookup = decoded.trim_start_matches('/');
    let lookup = if lookup.is_empty() { "index.html" } else { lookup };
    let head_only = *req.method() == Method::HEAD;

    if let Some(asset) = WebAssets::get(lookup) {
        return asset_response(lookup, &asset, head_only);
    }

    // SPA 回退：无扩展名路径回退 index.html；带扩展名的资源缺失则 404
    if Path::new(lookup).extension().is_some() {
        return plain(StatusCode::NOT_FOUND, "Not Found");
    }
    match WebAssets::get("index.html") {
        Some(asset) => asset_response("index.html", &asset, head_only),
        None => plain(StatusCode::NOT_FOUND, "Frontend build not found: web/dist"),
    }
}

fn asset_response(name: &str, asset: &rust_embed::EmbeddedFile, head_only: bool) -> Response<AppBody> {
    let mime = mime_guess::from_path(name).first_or_octet_stream();
    let mut builder = Response::builder()
        .header(header::CONTENT_TYPE, mime.as_ref())
        .header(header::CONTENT_LENGTH, asset.data.len())
        .header("x-content-type-options", "nosniff");
    if name == "index.html" {
        // 入口不缓存，保证发版即生效
        builder = builder.header(header::CACHE_CONTROL, "no-cache");
    } else if name.starts_with("assets/") {
        // Vite 构建的 assets 带内容哈希，可长缓存
        builder = builder.header(header::CACHE_CONTROL, "public, max-age=31536000, immutable");
    }
    let body = if head_only {
        empty_body()
    } else {
        full_body(Bytes::from(asset.data.clone().into_owned()))
    };
    builder.body(body).expect("静态响应构造不会失败")
}

fn plain(status: StatusCode, text: &str) -> Response<AppBody> {
    Response::builder()
        .status(status)
        .header(header::CONTENT_TYPE, "text/plain; charset=utf-8")
        .header("x-content-type-options", "nosniff")
        .body(full_body(Bytes::copy_from_slice(text.as_bytes())))
        .expect("纯文本响应构造不会失败")
}
