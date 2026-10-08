// 应用入口：先完成平台存储初始化，再加载应用主体。
//
// 桌面端的持久化数据在 Rust 后端（本地服务 /__app/storage），水合是异步的；
// 而 useSession / useTheme 等业务模块在模块顶层就读取存储，因此必须等
// initStorage() 完成后再动态加载 boot.js（连带整个应用模块图）。
// Web / 油猴环境的存储是同步的（localStorage / GM 存储），initStorage()
// 立即返回，不改变原有加载路径。
import { initStorage } from './services/storage.js';

initStorage()
  .catch(() => {}) // 水合失败由 storage.js 内部回落 localStorage，此处不再上抛
  .then(() => import('./boot.js'));
