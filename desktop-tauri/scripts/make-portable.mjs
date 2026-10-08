/**
 * Windows 端发布产物整理（零 npm 依赖，仅用系统自带能力）：
 *
 *  1. 便携版：将构建好的 BetterHRBUST.exe（前端资产已嵌入二进制，单文件即可运行）
 *     压缩为 release/BetterHRBUST-<版本>-windows-portable.zip，解压即用的绿色免安装版；
 *  2. 安装版：将 NSIS 安装包（tauri 默认名 BetterHRBUST_<版本>_x64-setup.exe）
 *     复制并统一命名为 release/BetterHRBUST-<版本>-windows-setup.exe。
 *
 * 统一命名后，desktop-tauri/release/ 下的文件即为可直接上传 GitHub Release 的资源，
 * 与 README 快捷下载链接、Android 包名（BetterHRBUST-<版本>-android.apk）保持一致。
 */
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const version = JSON.parse(
  readFileSync(path.join(root, 'package.json'), 'utf8')
).version;

const exe = path.join(root, 'src-tauri', 'target', 'release', 'BetterHRBUST.exe');
if (!existsSync(exe)) {
  console.error(`未找到 ${exe}，请先执行 npm run pack 或 npm run dist`);
  process.exit(1);
}

const releaseDir = path.join(root, 'release');
mkdirSync(releaseDir, { recursive: true });

// 1) 便携版：单 exe 压缩为 zip（PowerShell Compress-Archive）
const zip = path.join(releaseDir, `BetterHRBUST-${version}-windows-portable.zip`);
execFileSync(
  'powershell.exe',
  [
    '-NoProfile',
    '-Command',
    `Compress-Archive -Path "${exe}" -DestinationPath "${zip}" -Force`
  ],
  { stdio: 'inherit' }
);
console.log(`便携版已生成: ${zip}`);

// 2) 安装版：NSIS 产物统一改名（产物名由 tauri 决定，这里只做改名，便于直接上传 Release）
const nsisDir = path.join(root, 'src-tauri', 'target', 'release', 'bundle', 'nsis');
const installer = existsSync(nsisDir)
  ? readdirSync(nsisDir).find((name) => name.endsWith('-setup.exe'))
  : undefined;

if (installer) {
  const target = path.join(releaseDir, `BetterHRBUST-${version}-windows-setup.exe`);
  copyFileSync(path.join(nsisDir, installer), target);
  console.log(`安装版已生成: ${target}`);
} else {
  // --no-bundle 构建后单独执行本脚本时没有 NSIS 产物，跳过即可，不视为失败
  console.warn(`未找到 NSIS 安装包（${nsisDir}），已跳过安装版命名`);
}
