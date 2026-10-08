/**
 * 便携版打包：将构建好的 BetterHRBUST.exe（前端资产已嵌入二进制，
 * 单文件即可运行）压缩为 release/BetterHRBUST-<版本>-win.zip，
 * 产物形态与 Electron 版（desktop/）的免安装 zip 对齐。
 *
 * 依赖：系统自带 PowerShell（Compress-Archive），零 npm 依赖。
 */
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync } from 'node:fs';
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
const zip = path.join(releaseDir, `BetterHRBUST-${version}-win.zip`);

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
