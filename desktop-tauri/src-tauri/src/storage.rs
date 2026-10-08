//! 桌面端键值持久化存储（本地 JSON 文件）
//!
//! 供前端存储门面（web/src/services/storage.js）经本地服务的
//! `/__app/storage` 端点读写，语义对齐 localStorage（字符串键值）：
//!
//! | 请求 | 含义 |
//! | --- | --- |
//! | `GET /__app/storage` | 全量键值（JSON 对象，`no-store`） |
//! | `PUT /__app/storage` | `{"key":"...","value":"..."}` 写入单键 |
//! | `DELETE /__app/storage?key=k` | 删除单键；不带 key 删除全部 |
//!
//! 存储文件为 `{app_data_dir}/storage.json`，数据归 Rust 进程所有：
//! 页面源端口漂移（1950 被占顺延）或 WebView2 用户数据目录被清理时，
//! 教务数据缓存与会话元数据不丢。写入采用「临时文件 + 原子改名」，
//! 崩溃不会留下半写文件；意外损坏时把原文件改名 `.corrupt` 保留现场。

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use http::{header, Method, Request, Response, StatusCode};
use http_body_util::BodyExt;
use hyper::body::{Bytes, Incoming};

use crate::server::{full_body, plain_response, AppBody};

/// PUT 请求体大小上限：全量数据（成绩/考试/公告等解析结果）通常 < 1MB，
/// 16MB 纯防御性兜底（仅 127.0.0.1 可达，无外部攻击面）
pub const MAX_BODY_BYTES: usize = 16 * 1024 * 1024;

/// 键名长度上限（应用侧键均为 `better_hrbust_*` / `saved_student_number` 短标识符）
const MAX_KEY_LEN: usize = 256;

/// 键值存储：内存镜像 + 磁盘 JSON 文件，多请求共享（Arc 进 AppState）
pub struct Storage {
    path: PathBuf,
    data: Mutex<BTreeMap<String, String>>,
}

impl Storage {
    /// 打开（或初始化）指定目录下的存储文件
    pub fn open(dir: &Path) -> io::Result<Self> {
        fs::create_dir_all(dir)?;
        let path = dir.join("storage.json");
        let data = match fs::read(&path) {
            Ok(bytes) => match serde_json::from_slice::<BTreeMap<String, String>>(&bytes) {
                Ok(map) => map,
                Err(_) => {
                    // 损坏文件保留现场后从空开始，而不是让应用永远起不来
                    let _ = fs::rename(&path, dir.join("storage.json.corrupt"));
                    BTreeMap::new()
                }
            },
            Err(err) if err.kind() == io::ErrorKind::NotFound => BTreeMap::new(),
            Err(err) => return Err(err),
        };
        Ok(Self { path, data: Mutex::new(data) })
    }

    /// 全量快照（GET 用）
    pub fn snapshot(&self) -> BTreeMap<String, String> {
        self.data.lock().expect("storage 锁中毒").clone()
    }

    /// 写入单键并落盘
    pub fn set(&self, key: String, value: String) -> io::Result<()> {
        let snapshot = {
            let mut guard = self.data.lock().expect("storage 锁中毒");
            guard.insert(key, value);
            guard.clone()
        };
        self.save(&snapshot)
    }

    /// 删除单键并落盘；键不存在则无操作。返回键是否存在过
    pub fn remove(&self, key: &str) -> io::Result<bool> {
        let (removed, snapshot) = {
            let mut guard = self.data.lock().expect("storage 锁中毒");
            let removed = guard.remove(key).is_some();
            (removed, guard.clone())
        };
        if removed {
            self.save(&snapshot)?;
        }
        Ok(removed)
    }

    /// 清空全部键并落盘。返回清除的键数
    pub fn clear(&self) -> io::Result<usize> {
        let (cleared, snapshot) = {
            let mut guard = self.data.lock().expect("storage 锁中毒");
            let cleared = guard.len();
            guard.clear();
            (cleared, guard.clone())
        };
        if cleared > 0 {
            self.save(&snapshot)?;
        }
        Ok(cleared)
    }

    /// 原子落盘：先写临时文件再改名（Windows 下 std::fs::rename 覆盖目标）
    fn save(&self, map: &BTreeMap<String, String>) -> io::Result<()> {
        let mut json = serde_json::to_vec(map)
            .map_err(|err| io::Error::new(io::ErrorKind::InvalidData, err))?;
        json.push(b'\n');
        let tmp = self.path.with_extension("json.tmp");
        fs::write(&tmp, &json)?;
        fs::rename(&tmp, &self.path)?;
        Ok(())
    }
}

/// `/__app/storage` 请求处理。storage 未启用时统一 503，
/// 让前端门面走回落路径（dev 模式等场景）
pub async fn handle(storage: &Option<Arc<Storage>>, req: Request<Incoming>) -> Response<AppBody> {
    let Some(storage) = storage else {
        return plain_response(StatusCode::SERVICE_UNAVAILABLE, "storage not enabled");
    };

    match *req.method() {
        Method::GET => {
            let body = serde_json::to_vec(&storage.snapshot())
                .unwrap_or_else(|_| b"{}".to_vec());
            Response::builder()
                .status(StatusCode::OK)
                .header(header::CONTENT_TYPE, "application/json")
                .header(header::CACHE_CONTROL, "no-store")
                .header("x-content-type-options", "nosniff")
                .body(full_body(Bytes::from(body)))
                .expect("存储 GET 响应构造不会失败")
        }
        Method::PUT => {
            let bytes = match req.into_body().collect().await {
                Ok(collected) => collected.to_bytes(),
                Err(_) => return plain_response(StatusCode::BAD_REQUEST, "bad body"),
            };
            if bytes.len() > MAX_BODY_BYTES {
                return plain_response(StatusCode::PAYLOAD_TOO_LARGE, "payload too large");
            }
            let payload: serde_json::Value = match serde_json::from_slice(&bytes) {
                Ok(value) => value,
                Err(_) => return plain_response(StatusCode::BAD_REQUEST, "invalid json"),
            };
            let key = payload.get("key").and_then(|v| v.as_str()).unwrap_or("");
            let value = payload.get("value").and_then(|v| v.as_str()).unwrap_or("");
            if key.is_empty() || key.len() > MAX_KEY_LEN {
                return plain_response(StatusCode::BAD_REQUEST, "invalid key");
            }
            match storage.set(key.to_string(), value.to_string()) {
                Ok(()) => plain_response(StatusCode::OK, "stored"),
                Err(_) => plain_response(StatusCode::INTERNAL_SERVER_ERROR, "write failed"),
            }
        }
        Method::DELETE => {
            // ?key= 缺省 → 清空全部（退出登录场景）
            let key = req
                .uri()
                .query()
                .and_then(|query| query.split('&').find_map(|pair| pair.strip_prefix("key=")));
            let result = match key {
                Some(key) if !key.is_empty() => storage.remove(key).map(|_| ()),
                _ => storage.clear().map(|_| ()),
            };
            match result {
                Ok(()) => plain_response(StatusCode::OK, "deleted"),
                Err(_) => plain_response(StatusCode::INTERNAL_SERVER_ERROR, "write failed"),
            }
        }
        _ => plain_response(StatusCode::METHOD_NOT_ALLOWED, "Method Not Allowed"),
    }
}
