/**
 * 平台存储门面（localStorage 同语义子集：字符串键值）
 *
 * 三个构建目标共用同一份业务代码，持久化位置按运行环境自动选择：
 *
 * 1. 油猴（GM_getValue/GM_setValue/GM_deleteValue 可用）→ 扩展存储。
 *    与页面源解耦：用户清理教务站点数据、站点自身换 localStorage 键空间
 *    都不会丢缓存；GM API 是同步的，行为与 localStorage 完全一致。
 *
 * 2. 桌面端（Tauri initialization_script 注入 window.desktopWindow）
 *    → 内存 Map + Rust 后端文件持久化（本地服务 /__app/storage 端点，
 *    落盘在 Tauri app_data_dir）。启动时 initStorage() 一次性水合内存，
 *    随后读写走内存（同步），写入异步镜像到后端。
 *    数据归 Rust 进程所有：本地端口漂移（1950 被占顺延）或 WebView2
 *    用户数据目录被清理时，教务数据缓存与会话元数据不丢。
 *
 * 3. Web（默认）→ localStorage 直通，与既有行为完全一致。
 *
 * 注意：桌面端业务模块顶层（useSession/useTheme 等）就会读存储，
 * 因此 main.js 必须 await initStorage() 之后再动态加载应用主体
 * （见 main.js → boot.js 的拆分；油猴入口为同步 GM 存储，无需等待）。
 */

// ---------------- 环境探测（模块求值时一次性确定） ----------------

const hasGM =
  typeof GM_getValue === 'function' &&
  typeof GM_setValue === 'function' &&
  typeof GM_deleteValue === 'function';

const isDesktop = typeof window !== 'undefined' && !!window.desktopWindow;

const MODE = hasGM ? 'gm' : isDesktop ? 'desktop' : 'local';

/** 桌面端：异步镜像写请求的串行队列（保证同键写删除顺序与业务侧一致） */
let mirrorQueue = Promise.resolve();

/** 桌面端：后端是否已就绪（水合成功）。dev 模式（Vite 5173，无 Rust 服务）下保持 false */
let backendReady = false;

/** 桌面端：内存键值镜像（水合后作为同步读写的唯一来源） */
const memoryStore = new Map();

// ---------------- 油猴 / Web 直通实现 ----------------

function gmGet(key) {
  const value = GM_getValue(key, null);
  return typeof value === 'string' ? value : null;
}

function gmSet(key, value) {
  GM_setValue(key, value);
}

function gmRemove(key) {
  GM_deleteValue(key);
}

// ---------------- 桌面端实现 ----------------

/** 首次运行迁移：桌面版此前直接使用页面 localStorage，升级后导入 Rust 存储（仅后端为空时） */
function migrateLocalStorageOnce() {
  let migrated = 0;
  for (let i = 0; i < localStorage.length; i += 1) {
    const key = localStorage.key(i);
    if (!key || (!key.startsWith('better_hrbust') && key !== 'saved_student_number')) continue;
    if (!memoryStore.has(key)) {
      memoryStore.set(key, localStorage.getItem(key));
      migrated += 1;
    }
  }
  return migrated;
}

/**
 * 桌面端启动水合：拉取 Rust 后端全量键值填充内存镜像。
 * 失败（后端未就绪 / dev 模式无本地服务）时整体回落 localStorage，
 * 保证应用照常可用，仅本次运行无 Rust 持久化。
 */
async function hydrateFromBackend() {
  try {
    const res = await fetch('/__app/storage', { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`storage backend ${res.status}`);
    const map = await res.json();
    Object.entries(map || {}).forEach(([key, value]) => {
      if (typeof key === 'string' && typeof value === 'string') memoryStore.set(key, value);
    });
    if (memoryStore.size === 0 && migrateLocalStorageOnce() > 0) {
      // 老版本 localStorage 数据导入后端，完成一次性别迁移
      for (const [key, value] of memoryStore) {
        mirrorQueue = mirrorQueue.then(() =>
          fetch('/__app/storage', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key, value })
          }).catch(() => {})
        );
      }
    }
    backendReady = true;
  } catch {
    backendReady = false;
  }
}

function mirrorPut(key, value) {
  mirrorQueue = mirrorQueue.then(() =>
    fetch('/__app/storage', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value })
    }).catch(() => {})
  );
}

function mirrorDelete(key) {
  mirrorQueue = mirrorQueue.then(() =>
    fetch(`/__app/storage?key=${encodeURIComponent(key)}`, { method: 'DELETE' }).catch(() => {})
  );
}

// ---------------- 对外 API ----------------

/**
 * 启动初始化（仅在桌面端有实际动作），必须在业务模块求值前完成。
 * Web / 油猴环境立即返回。
 */
export function initStorage() {
  if (MODE !== 'desktop') return Promise.resolve();
  return hydrateFromBackend();
}

/** 读取字符串值，不存在返回 null（与 localStorage.getItem 一致） */
export function storageGetItem(key) {
  if (MODE === 'gm') return gmGet(key);
  if (MODE === 'desktop' && backendReady) {
    return memoryStore.has(key) ? memoryStore.get(key) : null;
  }
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

/** 写入字符串值（非字符串统一转字符串，与 localStorage.setItem 一致） */
export function storageSetItem(key, value) {
  const text = typeof value === 'string' ? value : String(value);
  if (MODE === 'gm') {
    gmSet(key, text);
    return;
  }
  if (MODE === 'desktop' && backendReady) {
    memoryStore.set(key, text);
    mirrorPut(key, text);
    return;
  }
  try {
    localStorage.setItem(key, text);
  } catch {
    // 存储配额不足时静默忽略，不影响内存态
  }
}

/** 删除键（不存在时为无操作，与 localStorage.removeItem 一致） */
export function storageRemoveItem(key) {
  if (MODE === 'gm') {
    gmRemove(key);
    return;
  }
  if (MODE === 'desktop' && backendReady) {
    memoryStore.delete(key);
    mirrorDelete(key);
    return;
  }
  try {
    localStorage.removeItem(key);
  } catch {
    // 忽略
  }
}
