//! BetterHRBUST 桌面端（Tauri）应用入口
//!
//! 架构：WebView2 窗口 → http://127.0.0.1:1950 本地服务（server.rs）
//!   ├─ 静态文件：web/dist（嵌入二进制）
//!   └─ /academic/* → 反向代理 → http://jwzx.hrbust.edu.cn（proxy.rs）
//!
//! 仅监听 127.0.0.1，不对局域网暴露；http://127.0.0.1 在 WebView2
//! 中属于 secure context，无明文/混合内容限制。教务会话 Cookie 由
//! WebView2 用户数据目录（按 bundle identifier 固定）持久保存。
//!
//! 窗口控制不走自定义 IPC 命令：shim.js 桥接 withGlobalTauri 的
//! window.__TAURI__，权限经 capabilities/main.json 的 remote.urls 授予。

mod proxy;
pub mod server;

use tauri::Manager;

/// 注入页面的 window.desktopWindow 兼容 shim（见 shim.js 头注释）
const SHIM: &str = include_str!("shim.js");

pub fn run() {
    tauri::Builder::default()
        // 单实例：二次启动时聚焦既有窗口
        .plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                if window.is_minimized().unwrap_or(false) {
                    let _ = window.unminimize();
                }
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        // 外链转系统浏览器
        .plugin(tauri_plugin_opener::init())
        // 页面加载完成后再显示主窗口，避免白屏闪烁
        .on_page_load(|webview, payload| {
            if payload.event() == tauri::webview::PageLoadEvent::Finished && webview.label() == "main"
            {
                if let Some(window) = webview.app_handle().get_webview_window("main") {
                    let _ = window.show();
                }
            }
        })
        .setup(|app| {
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                // 开发模式直连 Vite dev server（其自带 /academic 代理，热更新）；
                // 生产启动内置本地服务
                let url = if tauri::is_dev() {
                    "http://localhost:5173/".to_string()
                } else {
                    match server::start(server::ServerConfig::production()).await {
                        Ok(addr) => format!("http://{addr}/"),
                        Err(err) => {
                            eprintln!("[BetterHRBUST] 本地服务启动失败: {err}");
                            handle.exit(1);
                            return;
                        }
                    }
                };
                if let Err(err) = create_window(&handle, &url) {
                    eprintln!("[BetterHRBUST] 窗口创建失败: {err}");
                    handle.exit(1);
                }
            });
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("BetterHRBUST 运行失败");
}

fn create_window(
    app: &tauri::AppHandle,
    url: &str,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let parsed: tauri::Url = url.parse()?;
    // 导航守卫：仅允许停留在应用源内，其余 http(s) 转系统浏览器
    let allowed_origin = format!("{}://{}", parsed.scheme(), parsed.authority());

    tauri::WebviewWindowBuilder::new(app, "main", tauri::WebviewUrl::External(parsed))
        .title("BetterHRBUST")
        .inner_size(1280.0, 800.0)
        .min_inner_size(960.0, 600.0)
        // 无原生标题栏：顶部区域由前端 DesktopTitleBar 以 WebUI 风格接管
        .decorations(false)
        // 先隐藏，页面加载完成后再显示，避免白屏闪烁（等价 ready-to-show）
        .visible(false)
        .initialization_script(SHIM)
        .on_navigation(move |nav| {
            let target = nav.as_str();
            let same_origin = target.starts_with(&allowed_origin)
                && (target.len() == allowed_origin.len()
                    || target.as_bytes().get(allowed_origin.len()) == Some(&b'/')
                    || target.as_bytes().get(allowed_origin.len()) == Some(&b'?'));
            if same_origin {
                return true;
            }
            if nav.scheme() == "http" || nav.scheme() == "https" {
                let _ = tauri_plugin_opener::open_url(target.to_string(), None::<&str>);
            }
            false
        })
        .build()?;

    Ok(())
}
