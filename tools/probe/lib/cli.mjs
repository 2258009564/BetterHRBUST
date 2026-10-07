/**
 * CLI 参数解析、配置文件加载与终端交互工具
 */

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { createRequire } from 'node:module';

/** 默认配置 */
export const DEFAULT_OPTIONS = {
  baseUrl: 'http://jwzx.hrbust.edu.cn/academic/',
  username: '',
  password: '',
  captcha: '',
  only: [], // 仅探测匹配的接口 key（支持前缀，如 'score.*'）
  outDir: 'output',
  timeout: 20000,
  concurrency: 1,
  skipLogin: false, // 复用 output/session.json
  portal: true, // 登录后抓取并分析门户页（index_new.jsp 等）
  discover: true, // 从菜单页自动发现新接口
  depth: 2, // 探测轮数：第 1 轮探入口，后续轮次从样本里递进挖新地址
  openCaptcha: true, // 自动打开验证码图片
  allowMutating: false, // 是否探测会改变教务数据的接口
  verbose: false,
  list: false, // 仅打印接口目录
  dryRun: false, // 只打印计划，不发请求
  analyzeOnly: false, // 不联网，仅对已落盘样本重新做结构分析
  clean: false, // 探测前清空旧的样本 / 解码副本 / 门户产物
  redact: true, // 产物脱敏（默认开启）
  assumeYes: false, // 跳过确认
  help: false
};

/** --flag value 形式的取值型参数 */
const VALUE_FLAGS = new Map([
  ['--base', 'baseUrl'],
  ['--user', 'username'],
  ['--pass', 'password'],
  ['--captcha', 'captcha'],
  ['--only', 'only'],
  ['--out', 'outDir'],
  ['--timeout', 'timeout'],
  ['--concurrency', 'concurrency'],
  ['--depth', 'depth'],
  ['--config', 'config']
]);

/** 布尔型参数 */
const BOOLEAN_FLAGS = new Map([
  ['--skip-login', ['skipLogin', true]],
  ['--no-portal', ['portal', false]],
  ['--no-discover', ['discover', false]],
  ['--no-open', ['openCaptcha', false]],
  ['--open-captcha', ['openCaptcha', true]],
  ['--allow-mutating', ['allowMutating', true]],
  ['--verbose', ['verbose', true]],
  ['--list', ['list', true]],
  ['--dry-run', ['dryRun', true]],
  ['--analyze-only', ['analyzeOnly', true]],
  ['--clean', ['clean', true]],
  ['--no-redact', ['redact', false]],
  ['--redact', ['redact', true]],
  ['--yes', ['assumeYes', true]],
  ['--help', ['help', true]],
  ['-h', ['help', true]]
]);

/**
 * 解析 process.argv
 * @param {string[]} argv 通常传 process.argv.slice(2)
 * @returns {{options:Object, configFile:string|null, unknown:string[]}}
 */
export function parseArgv(argv = []) {
  const options = { ...DEFAULT_OPTIONS };
  let configFile = null;
  const unknown = [];

  for (let i = 0; i < argv.length; i++) {
    const raw = argv[i];
    const eq = raw.indexOf('=');
    const flag = eq > -1 ? raw.slice(0, eq) : raw;
    const inlineValue = eq > -1 ? raw.slice(eq + 1) : undefined;

    if (BOOLEAN_FLAGS.has(flag)) {
      const [key, value] = BOOLEAN_FLAGS.get(flag);
      options[key] = value;
      continue;
    }

    if (VALUE_FLAGS.has(flag)) {
      const key = VALUE_FLAGS.get(flag);
      const value = inlineValue !== undefined ? inlineValue : argv[++i];
      if (value === undefined) {
        unknown.push(`${flag} (缺少取值)`);
        continue;
      }
      if (key === 'config') {
        configFile = value;
      } else if (key === 'only') {
        options.only = String(value)
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean);
      } else if (key === 'timeout' || key === 'concurrency' || key === 'depth') {
        const num = Number(value);
        options[key] = Number.isFinite(num) && num > 0 ? num : options[key];
      } else {
        options[key] = value;
      }
      continue;
    }

    unknown.push(raw);
  }

  return { options, configFile, unknown };
}

/** 读取 JSON 配置文件（不存在时返回 null） */
export function loadConfigFile(file) {
  if (!file) return null;
  const abs = path.resolve(file);
  if (!fs.existsSync(abs)) {
    console.warn(`[probe] 配置文件不存在，已忽略：${abs}`);
    return null;
  }
  try {
    return JSON.parse(fs.readFileSync(abs, 'utf8'));
  } catch (err) {
    console.warn(`[probe] 配置文件解析失败，已忽略：${err.message}`);
    return null;
  }
}

/** 合并：默认值 < 配置文件 < 命令行 */
export function mergeOptions(parsed, fileConfig) {
  const merged = { ...parsed.options };
  if (fileConfig && typeof fileConfig === 'object') {
    for (const [key, value] of Object.entries(fileConfig)) {
      if (value === undefined || value === null) continue;
      if (key === 'only' && typeof value === 'string') {
        merged.only = value.split(',').map((s) => s.trim()).filter(Boolean);
      } else if (key in merged) {
        merged[key] = value;
      }
    }
  }
  return merged;
}

/** 环境变量兜底 */
export function applyEnv(options) {
  const env = process.env;
  if (!options.username && env.HRBUST_USER) options.username = env.HRBUST_USER;
  if (!options.password && env.HRBUST_PASS) options.password = env.HRBUST_PASS;
  if (!options.captcha && env.HRBUST_CAPTCHA) options.captcha = env.HRBUST_CAPTCHA;
  if (!options.baseUrl && env.HRBUST_BASE) options.baseUrl = env.HRBUST_BASE;
  return options;
}

/** 校验 Node 版本（需要 18+ 才有全局 fetch / AbortSignal.timeout） */
export function assertNodeVersion(minMajor = 18) {
  const major = Number(process.versions.node.split('.')[0]);
  if (major < minMajor) {
    console.error(
      `[probe] 需要 Node ${minMajor}+，当前为 ${process.version}。请升级 Node 后重试。`
    );
    process.exit(1);
  }
  return true;
}

/** 检查可选依赖是否存在 */
export function hasDependency(name) {
  try {
    const require = createRequire(import.meta.url);
    require.resolve(name);
    return true;
  } catch {
    return false;
  }
}

/* ------------------------------------------------------------------ *
 * 终端交互
 * ------------------------------------------------------------------ */

/** 普通提问（回显） */
export async function ask(question, { defaultValue = '' } = {}) {
  if (!process.stdin.isTTY) return defaultValue;
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const answer = await rl.question(question);
    const trimmed = String(answer ?? '').trim();
    return trimmed || defaultValue;
  } finally {
    rl.close();
  }
}

/** 敏感信息提问（不回显，输入替换为 *） */
export function askSecret(question) {
  const stdin = process.stdin;
  const stdout = process.stdout;

  return new Promise((resolve) => {
    if (!stdin.isTTY || typeof stdin.setRawMode !== 'function') {
      // 非 TTY：退化为普通读取，避免脚本卡死
      const rl = readline.createInterface({ input: stdin, output: stdout });
      rl.question(question, (ans) => {
        rl.close();
        resolve(String(ans ?? '').trim());
      });
      return;
    }

    stdout.write(question);
    stdin.setRawMode(true);
    stdin.resume();
    stdin.setEncoding('utf8');

    let input = '';
    const finish = (value) => {
      stdin.removeListener('data', onData);
      if (typeof stdin.setRawMode === 'function') stdin.setRawMode(false);
      stdin.pause();
      stdout.write('\n');
      resolve(value);
    };

    const onData = (char) => {
      const text = String(char);
      switch (text) {
        case '\n':
        case '\r':
        case '\u0004': // Ctrl-D
          finish(input);
          break;
        case '\u0003': // Ctrl-C
          stdout.write('\n');
          process.exit(130);
          break;
        case '\u007f':
        case '\b':
          if (input.length > 0) {
            input = input.slice(0, -1);
            stdout.write('\b \b');
          }
          break;
        default:
          if (text >= ' ') {
            input += text;
            stdout.write('*');
          }
          break;
      }
    };

    stdin.on('data', onData);
  });
}

/** 是/否确认 */
export async function confirm(question, { defaultValue = true } = {}) {
  const hint = defaultValue ? '[Y/n]' : '[y/N]';
  const answer = await ask(`${question} ${hint} `, {
    defaultValue: defaultValue ? 'y' : 'n'
  });
  return /^y(es)?$/i.test(answer);
}

/* ------------------------------------------------------------------ *
 * 帮助
 * ------------------------------------------------------------------ */

export const HELP_TEXT = `
哈理工教务在线 · 接口探测脚本
=================================

用法:
  node probe.mjs [选项]

选项:
  --user <学号>            教务账号（也可用环境变量 HRBUST_USER）
  --pass <密码>            教务密码（也可用环境变量 HRBUST_PASS）
  --captcha <验证码>       直接提供验证码（省略则交互输入）
  --base <URL>             教务系统基址，默认 http://jwzx.hrbust.edu.cn/academic/
  --only <keys>            仅探测指定接口，逗号分隔，支持前缀通配
                           例：--only score.*,timetable.*
  --out <目录>             样本输出目录，默认 tools/probe/output
  --timeout <ms>           单请求超时，默认 20000
  --concurrency <n>        并发数，默认 1（串行，避免对教务系统造成压力）
  --depth <n>              探测轮数，默认 2。第 1 轮探入口，第 2 轮从第 1 轮样本里
                           递进挖出更深层的接口（表单 action / 按钮 onclick / 导出链接）
  --skip-login             复用 output/session.json 中的会话，跳过登录
  --no-portal              不抓取登录后门户页（默认会抓取并做结构化分析）
  --no-discover            关闭菜单自动发现，仅探测内置接口目录
  --no-open                不自动打开验证码图片
  --allow-mutating         允许探测会改变教务数据的接口（选课/退课等），默认禁止
  --verbose                打印每个请求的详细日志
  --list                   仅打印内置接口目录
  --dry-run                仅打印探测计划，不发送请求
  --analyze-only           不联网，仅对已落盘样本重新做结构分析并生成 analysis.md
  --clean                  探测前清空 samples/ decoded/ portal/，避免历史运行的文件累积
  --no-redact              关闭产物脱敏（默认开启）。关闭后产物含真实隐私数据，慎用
  --config <文件>          从 JSON 配置文件读取默认值
  -h, --help               显示本帮助

示例:
  # 全量探测（推荐）
  node tools/probe/probe.mjs --user 2104010218

  # 只探测成绩相关接口
  node tools/probe/probe.mjs --only score.*

  # 复用已登录会话重跑
  node tools/probe/probe.mjs --skip-login --only timetable.*

提示:
  · 必须在校园网 / 学校 VPN 环境下运行，否则无法访问教务系统。
  · 脚本会把原始响应保存到输出目录，便于离线分析，请勿将样本公开。
  · 账号密码仅用于本次登录，不会写入任何文件（会话文件只保存 Cookie）。
`.trim();
