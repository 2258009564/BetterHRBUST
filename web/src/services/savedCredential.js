/**
 * 本地保存的教务账号密码（"记住密码"功能的数据层）
 *
 * 持久化统一走 storage.js 门面，三个构建目标行为一致：
 * 油猴 → GM 存储；桌面端 → Tauri Rust 后端；Web → localStorage。
 *
 * 密码仅做 XOR + Base64 混淆，避免在存储中以明文裸露（减小被直接窥屏/误读的风险）。
 * 这不是加密：混淆密钥内置在代码里，任何能读取本机存储的人都能还原，
 * 因此只适合个人设备使用；取消登录页的"记住密码"勾选即可立即清除。
 */

import { storageGetItem, storageSetItem, storageRemoveItem } from '@/services/storage.js';

/** 存储键（better_hrbust 前缀，桌面端一次性迁移逻辑可覆盖） */
const CREDENTIAL_KEY = 'better_hrbust_saved_credential';

/** 混淆密钥（仅用于避免明文落盘，不承担真实加密职责） */
const MASK = 'better-hrbust-local-credential-v1';

/** 对称异或：加密与解密同一实现 */
function xorBytes(bytes) {
  const out = new Uint8Array(bytes.length);
  for (let i = 0; i < bytes.length; i += 1) {
    out[i] = bytes[i] ^ MASK.charCodeAt(i % MASK.length);
  }
  return out;
}

function obfuscate(text) {
  const masked = xorBytes(new TextEncoder().encode(text));
  let binary = '';
  for (let i = 0; i < masked.length; i += 1) binary += String.fromCharCode(masked[i]);
  return btoa(binary);
}

function deobfuscate(cipher) {
  const binary = atob(cipher);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return new TextDecoder().decode(xorBytes(bytes));
}

/**
 * 读取已保存的账号密码（供登录表单自动填入）
 * @returns {{ username: string, password: string } | null} 无记录或数据损坏时返回 null
 */
export function readSavedCredential() {
  const raw = storageGetItem(CREDENTIAL_KEY);
  if (!raw) return null;
  try {
    const data = JSON.parse(raw);
    const username = String(data?.username || '').trim();
    const password = typeof data?.password === 'string' ? deobfuscate(data.password) : '';
    if (!username || !password) return null;
    return { username, password };
  } catch {
    return null;
  }
}

/**
 * 保存账号密码（登录成功后调用）
 * 存储不可用或入参为空时静默忽略，不影响登录流程
 */
export function saveCredential(username, password) {
  const name = String(username || '').trim();
  if (!name || typeof password !== 'string' || !password) return;
  try {
    storageSetItem(CREDENTIAL_KEY, JSON.stringify({ username: name, password: obfuscate(password) }));
  } catch {
    // 忽略：下次登录会重新尝试保存
  }
}

/** 清除已保存的密码 */
export function clearCredential() {
  storageRemoveItem(CREDENTIAL_KEY);
}
