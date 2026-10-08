/**
 * 桌面端 preload：向前端暴露最小化的窗口控制 API
 *
 * 安全约束：contextIsolation 保持开启，仅通过 contextBridge 暴露
 * 少量白名单方法；沙箱模式下 preload 仅支持 CommonJS（.cjs）。
 * 前端经 window.desktopWindow 调用，页面无法触达 Node 能力。
 */
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopWindow', {
  minimize: () => ipcRenderer.send('win:minimize'),
  toggleMaximize: () => ipcRenderer.send('win:toggle-maximize'),
  close: () => ipcRenderer.send('win:close'),
  isMaximized: () => ipcRenderer.invoke('win:is-maximized'),
  /** 订阅最大化状态变化（true=已最大化），返回取消订阅函数 */
  onMaximizeChange: (callback) => {
    const listener = (_event, value) => callback(value);
    ipcRenderer.on('win:maximize-changed', listener);
    return () => ipcRenderer.removeListener('win:maximize-changed', listener);
  }
});
