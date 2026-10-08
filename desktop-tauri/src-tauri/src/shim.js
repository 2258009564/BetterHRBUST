/**
 * window.desktopWindow 兼容 shim（Tauri 端）
 *
 * 由 initialization_script 在页面脚本运行前注入本 shim，桥接到 withGlobalTauri
 * 暴露的 window.__TAURI__，从而让前端 main.js / DesktopTitleBar.vue 的
 * 检测逻辑生效。
 *
 * API 契约：
 *  - minimize() / toggleMaximize() / close()        fire-and-forget
 *  - isMaximized()                                   → Promise<boolean>
 *  - onMaximizeChange(cb)                            → 取消订阅函数
 *  - startDrag()                                     Tauri 专用补充：
 *    WebView2 不认 -webkit-app-region，DesktopTitleBar 在 Tauri 环境
 *    用 mousedown 手动拖拽/双击最大化，需要该入口
 */
(function () {
  if (window.desktopWindow) return;

  function currentWindow() {
    return window.__TAURI__ && window.__TAURI__.window
      ? window.__TAURI__.window.getCurrentWindow()
      : null;
  }

  window.desktopWindow = {
    minimize: function () {
      var w = currentWindow();
      if (w) w.minimize();
    },
    toggleMaximize: function () {
      var w = currentWindow();
      if (w) w.toggleMaximize();
    },
    close: function () {
      var w = currentWindow();
      if (w) w.close();
    },
    isMaximized: function () {
      var w = currentWindow();
      return w ? w.isMaximized() : Promise.resolve(false);
    },
    startDrag: function () {
      var w = currentWindow();
      if (w) w.startDragging();
    },
    onMaximizeChange: function (callback) {
      var w = currentWindow();
      if (!w) return function () {};
      // onResized 覆盖最大化/还原（窗口尺寸变化），配合 isMaximized 去重
      var last = null;
      var unlistenPromise = w.onResized(function () {
        w.isMaximized().then(function (value) {
          if (value !== last) {
            last = value;
            callback(value);
          }
        });
      });
      w.isMaximized().then(function (value) {
        last = value;
      });
      return function () {
        unlistenPromise.then(function (unlisten) {
          if (unlisten) unlisten();
        });
      };
    }
  };
})();
