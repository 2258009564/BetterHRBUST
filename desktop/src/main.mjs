/**
 * BetterHRBUST Windows 桌面端主进程
 *
 * 架构：BrowserWindow → http://127.0.0.1:<随机端口> 本地服务
 *   ├─ 静态文件：web/dist（打包后为 resources/webapp）
 *   └─ /academic/* → 反向代理 → http://jwzx.hrbust.edu.cn
 *
 * 仅监听 127.0.0.1，不对局域网暴露；http://127.0.0.1 在 Chromium
 * 中属于 secure context，无明文/混合内容限制。
 * 教务会话 Cookie 由 Electron 默认持久会话（userData 目录）保存，
 * 重启应用后仍有效，直至教务侧过期。
 */
import { app, BrowserWindow, Menu, dialog, ipcMain, shell } from 'electron';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAcademicProxy, isAcademicPath } from './academic-proxy.mjs';
import { createStaticFileHandler } from './local-server.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP_MODEL_ID = 'edu.hrbust.betterhrbust';

/**
 * 开发直连：设置 ELECTRON_START_URL 后跳过内置静态服务与反代，
 * 直接加载 Vite 开发服务器（其自身带 /academic 代理），支持热更新。
 * 例（PowerShell）：$env:ELECTRON_START_URL='http://localhost:5173'; npm start
 */
const devUrl = process.env.ELECTRON_START_URL || '';

if (!app.requestSingleInstanceLock()) {
  // 已有实例在运行：静默退出，由原实例聚焦窗口
  app.quit();
} else {
  let mainWindow = null;
  let server = null;

  app.setAppUserModelId(APP_MODEL_ID);
  // 桌面应用无需应用菜单栏
  Menu.setApplicationMenu(null);

  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.show();
      mainWindow.focus();
    }
  });

  // 前端自定义标题栏的窗口控制通道
  ipcMain.on('win:minimize', () => {
    if (mainWindow) mainWindow.minimize();
  });
  ipcMain.on('win:toggle-maximize', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.on('win:close', () => {
    if (mainWindow) mainWindow.close();
  });
  ipcMain.handle('win:is-maximized', () => (mainWindow ? mainWindow.isMaximized() : false));

  /** 解析前端构建产物目录：打包后取资源目录，开发期取 ../web/dist */
  function resolveWebRoot() {
    return app.isPackaged
      ? path.join(process.resourcesPath, 'webapp')
      : path.resolve(__dirname, '../../web/dist');
  }

  /**
   * 本地服务端口策略：固定优先（1950 起，建校年份），被占用时向后顺延。
   * 固定端口保证页面源（http://127.0.0.1:1950）跨冷启动稳定，
   * localStorage 与教务会话 Cookie 因此持久 —— 随机端口会使用户数据"消失"。
   */
  const PORT_BASE = 1950;
  const PORT_TRIES = 10;

  /** 启动本地 HTTP 服务（仅监听 127.0.0.1） */
  function startLocalServer() {
    const webRoot = resolveWebRoot();
    if (!fs.existsSync(path.join(webRoot, 'index.html'))) {
      dialog.showMessageBoxSync({
        type: 'error',
        title: 'BetterHRBUST',
        message: '未找到前端构建产物',
        detail: `缺少 ${webRoot}\\index.html。\n请在 web/ 目录执行 npm install && npm run build 后重试，\n或设置 ELECTRON_START_URL 直连 Vite 开发服务器。`
      });
      app.exit(1);
      return null;
    }

    const academicProxy = createAcademicProxy();
    const serveStatic = createStaticFileHandler(webRoot);

    const requestHandler = (req, res) => {
      let pathname;
      try {
        pathname = new URL(req.url, 'http://127.0.0.1').pathname;
      } catch {
        res.statusCode = 400;
        return res.end('Bad Request');
      }
      if (isAcademicPath(pathname)) {
        academicProxy.web(req, res);
      } else {
        serveStatic(req, res);
      }
    };

    return (async () => {
      let lastErr = null;
      for (let i = 0; i < PORT_TRIES; i++) {
        const candidate = http.createServer(requestHandler);
        candidate.on('clientError', (_err, socket) => socket.destroy());
        try {
          await new Promise((resolve, reject) => {
            candidate.once('error', reject);
            candidate.listen(PORT_BASE + i, '127.0.0.1', resolve);
          });
          server = candidate;
          return server;
        } catch (err) {
          if (err && err.code !== 'EADDRINUSE') throw err;
          lastErr = err;
        }
      }
      throw lastErr || new Error('无可用本地端口');
    })();
  }

  function createWindow(startUrl) {
    mainWindow = new BrowserWindow({
      width: 1280,
      height: 800,
      minWidth: 960,
      minHeight: 600,
      show: false,
      autoHideMenuBar: true,
      backgroundColor: '#f4f4f5',
      title: 'BetterHRBUST',
      icon: path.resolve(__dirname, '../build/icon.ico'),
      // 无原生标题栏：顶部区域由前端 DesktopTitleBar 以 WebUI 风格接管
      titleBarStyle: 'hidden',
      webPreferences: {
        preload: path.join(__dirname, 'preload.cjs'),
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        spellcheck: false
      }
    });

    mainWindow.once('ready-to-show', () => mainWindow.show());

    // 阻止窗口导航离开应用源；外链（如引导回教务系统原生界面）交给系统浏览器
    mainWindow.webContents.on('will-navigate', (event, url) => {
      const current = new URL(mainWindow.webContents.getURL() || startUrl);
      let target;
      try {
        target = new URL(url);
      } catch {
        return event.preventDefault();
      }
      if (target.origin !== current.origin) {
        event.preventDefault();
        if (target.protocol === 'http:' || target.protocol === 'https:') {
          shell.openExternal(url);
        }
      }
    });
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
      if (url.startsWith('http:') || url.startsWith('https:')) {
        shell.openExternal(url);
      }
      return { action: 'deny' };
    });

    // 最大化状态变化同步给前端标题栏（切换最大/还原图标）
    const sendMaximizeState = () => {
      if (mainWindow) {
        mainWindow.webContents.send('win:maximize-changed', mainWindow.isMaximized());
      }
    };
    mainWindow.on('maximize', sendMaximizeState);
    mainWindow.on('unmaximize', sendMaximizeState);

    mainWindow.on('closed', () => {
      mainWindow = null;
    });

    mainWindow.loadURL(startUrl).catch((err) => {
      console.error('[BetterHRBUST] 页面加载失败:', err.message);
    });
  }

  app.whenReady().then(async () => {
    try {
      let startUrl = devUrl;
      if (!startUrl) {
        const srv = await startLocalServer();
        if (!srv) return; // 已弹出错误对话框并退出
        const { port } = srv.address();
        startUrl = `http://127.0.0.1:${port}/`;
      }
      console.log(`[BetterHRBUST] 加载 ${startUrl}`);
      createWindow(startUrl);
    } catch (err) {
      console.error('[BetterHRBUST] 启动失败:', err);
      dialog.showMessageBoxSync({
        type: 'error',
        title: 'BetterHRBUST',
        message: '应用启动失败',
        detail: String((err && err.message) || err)
      });
      app.exit(1);
    }
  });

  app.on('window-all-closed', () => {
    // Windows 惯例：关闭窗口即退出
    if (server) server.close();
    app.quit();
  });
}
