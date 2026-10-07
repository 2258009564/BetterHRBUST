/**
 * 输出脱敏
 *
 * 探测产物（样本、解码副本、门户页源码、分析报告、清单预览）会包含**真实隐私数据**：
 * 学号、内部学生 ID、真实姓名、证件号码、手机号、家庭住址，以及绑定账号的加密串。
 * 这些文件一旦被分享或误提交，等同于泄露个人信息。
 *
 * 因此本模块在**所有落盘路径**上统一做一次脱敏，规则分两类：
 *
 *   ① 已知值精确替换
 *      运行时逐步收集（登录学号、教务内部 ID、真实姓名、页面里出现的加密串），
 *      在探测结束时用完整集合做一次终局重写，保证不遗漏。
 *
 *   ② 模式规则
 *      证件号 / 手机号 / 邮箱 / 加密串参数 / 敏感标签对应的值，
 *      这类数据格式固定，可以直接用正则识别，无需事先知道具体值。
 *
 * 设计取舍：脱敏需要「解码 → 替换 → 重新编码」，而 Node 没有 GBK 编码器，
 * 所以脱敏模式下 `samples/` 与 `decoded/` **统一以 UTF-8 落盘**，
 * 原始编码仍记录在 `manifest.json` 的 `charset` 字段里，需要字节级核对时可查。
 * 通过 `--no-redact` 可关闭脱敏，恢复「原始字节 + 解码副本」的原始行为。
 */

/** 占位符：一眼能看出是脱敏产物，且保留了字段语义 */
export const PLACEHOLDER = {
  username: '[学号已脱敏]',
  studentId: '[内部ID已脱敏]',
  name: '[姓名已脱敏]',
  className: '[班级已脱敏]',
  token: '[加密串已脱敏]',
  password: '[密码已脱敏]',
  phone: '[手机号已脱敏]',
  idCard: '[证件号已脱敏]',
  email: '[邮箱已脱敏]',
  address: '[地址已脱敏]',
  other: '[已脱敏]'
};

/** 敏感标签：出现在 `<th>标签</th><td>值</td>` 结构里时，把「值」屏蔽掉 */
const SENSITIVE_LABELS = [
  '证件号码',
  '证件号',
  '身份证号',
  '身份证号码',
  '联系电话',
  '手机号码',
  '手机号',
  '手机',
  '电子邮箱',
  '邮箱',
  '通讯地址',
  '家庭住址',
  '联系地址',
  '邮政编码',
  'QQ',
  '微信',
  '考生号',
  '准考证号',
  '银行卡号'
];

/**
 * 构造「敏感标签 → 屏蔽该标签后一个单元格」的正则
 *
 * 教务系统的键值表结构稳定为 `<th>标签</th><td>值</td>`（如「我的信息」页），
 * 据此做定向屏蔽比全局模糊匹配准确得多。
 */
const LABELED_FIELD_RE = new RegExp(
  `(<th[^>]*>\\s*(?:${SENSITIVE_LABELS.join('|')})\\s*</th>\\s*<td[^>]*>)([\\s\\S]*?)(</td>)`,
  'gi'
);

/**
 * 与上下文无关、可直接识别的敏感格式
 *
 * `ph` 为替换函数，接收正则的 (匹配, 捕获组...)，返回替换结果。
 * 用函数而不是字符串，是为了只屏蔽值、保留参数名，便于对照接口定义。
 */
const PATTERN_RULES = [
  {
    type: 'idCard',
    // 18 位身份证（末位可能是 X）
    re: /\b\d{17}[\dXx]\b/g,
    ph: () => PLACEHOLDER.idCard
  },
  {
    type: 'phone',
    // 大陆手机号
    re: /(?<!\d)1[3-9]\d{9}(?!\d)/g,
    ph: () => PLACEHOLDER.phone
  },
  {
    type: 'email',
    re: /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b/g,
    ph: () => PLACEHOLDER.email
  },
  {
    type: 'token',
    // 加密串参数：userid=xxx== / studentId=xxx== / sid=...
    re: /\b((?:userid|studentId|sid|uid|token|code)=)([A-Za-z0-9+/]{10,}={0,2})/gi,
    ph: (_match, name) => `${name}${PLACEHOLDER.token}`
  }
];

/**
 * 判断某个「已知值」是否值得作为脱敏目标
 *
 * 太短或过于通用的值会造成大面积误替换（例如值为 `1` 或 `abc`），
 * 因此设最低长度：ASCII 值至少 4 位，含中文的值至少 2 个字符。
 */
function isRedactable(value) {
  const v = String(value ?? '').trim();
  if (!v) return false;
  const hasCjk = /[\u4e00-\u9fa5]/.test(v);
  return hasCjk ? v.length >= 2 : v.length >= 4;
}

/**
 * 脱敏器
 *
 * 用法：
 *   const redactor = new Redactor({ enabled: true });
 *   redactor.addKnown('studentId', '100001');
 *   redactor.redact(html);                  // 单次替换
 *   redactor.redactAll(storedText);         // 终局重写（用完整已知值集合）
 */
export class Redactor {
  /**
   * @param {Object} [options]
   * @param {boolean} [options.enabled=true] 是否启用脱敏
   */
  constructor({ enabled = true } = {}) {
    this.enabled = Boolean(enabled);
    /** @type {Map<string, {type:string, placeholder:string, hits:number}>} */
    this.known = new Map();
    /** 模式规则命中的次数（按类型） */
    this.patternHits = {};
  }

  /** 是否处于生效状态 */
  get active() {
    return this.enabled;
  }

  /**
   * 登记一个已知的敏感值
   * @param {keyof typeof PLACEHOLDER} type 类型
   * @param {string} value 真实值
   */
  addKnown(type, value) {
    if (!this.enabled || !isRedactable(value)) return;

    // 已知值会被写进 manifest.json 的整体脱敏流程，含引号或反斜杠的值
    // 替换后会破坏 JSON 结构，因此在入口处直接剔除这类字符
    const v = String(value).trim().replace(/["\\]/g, '');
    if (!isRedactable(v) || this.known.has(v)) return;
    this.known.set(v, {
      type,
      placeholder: PLACEHOLDER[type] || PLACEHOLDER.other,
      hits: 0
    });
  }

  /** 批量登记：只需要值的类型，值从文本中自动提取 */
  addKnownMany(pairs) {
    for (const [type, value] of pairs) this.addKnown(type, value);
    return this;
  }

  /**
   * 从响应文本中自动发现并登记敏感值
   *
   * 两类值必须靠「从真实响应里抽取」而不是靠正则模糊匹配：
   *
   *   ① 加密串令牌 —— 形如 `userid=xxx==`、`studentId=xxx==`，
   *      绑定账号，出现在参数位置或正文里；
   *   ② 键值对里的身份值 —— 教务系统的键值表结构稳定为
   *      `<th>标签</th><td>值</td>`，可据此精确取出学号与真实姓名。
   *
   * 收集到之后由 `redact()` 做精确替换，避免误伤正常文本。
   *
   * @param {string} text 响应正文
   * @returns {{tokens:number, identity:number}} 本次新发现的数量
   */
  harvest(text) {
    if (!this.enabled) return { tokens: 0, identity: 0 };

    const src = String(text || '');
    const found = { tokens: 0, identity: 0 };

    // ① 加密串令牌
    const tokenRe = /\b(?:userid|studentId|sid|uid)=([A-Za-z0-9+/]{10,}={0,2})/gi;
    let m;
    while ((m = tokenRe.exec(src)) !== null) {
      const size = this.known.size;
      this.addKnown('token', m[1]);
      if (this.known.size > size) found.tokens += 1;
    }

    // ② 键值对中的身份信息
    const identityLabels = [
      { type: 'username', labels: ['用户名', '学号'] },
      { type: 'name', labels: ['真实姓名', '姓名'] },
      { type: 'className', labels: ['班级'] }
    ];

    for (const { type, labels } of identityLabels) {
      const re = new RegExp(
        `<th[^>]*>\\s*(?:${labels.join('|')})\\s*</th>\\s*<td[^>]*>([\\s\\S]*?)</td>`,
        'i'
      );
      const hit = src.match(re);
      if (!hit) continue;

      // 单元格里可能夹着 &nbsp; 与标签，只取纯文本
      const value = hit[1]
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ')
        .trim();

      if (!value) continue;

      // 身份值应当是短文本。过长的匹配说明取到的是下拉框选项列表
      //（如学籍信息页的「班级」字段内嵌 <select>），登记它只会造成噪音
      if (value.length > 40) continue;

      const size = this.known.size;
      this.addKnown(type, value);
      if (this.known.size > size) found.identity += 1;
    }

    return found;
  }

  /** 兼容旧调用名 */
  harvestTokens(text) {
    return this.harvest(text).tokens;
  }

  /**
   * 登记全部已知值后，执行脱敏
   * @param {string} text
   * @returns {string}
   */
  redact(text) {
    if (!this.enabled) return text;
    let out = String(text ?? '');

    // ① 已知值：按长度降序，避免短值先替换破坏长值
    const entries = [...this.known.entries()].sort((a, b) => b[0].length - a[0].length);
    for (const [value, info] of entries) {
      if (!out.includes(value)) continue;
      const parts = out.split(value);
      info.hits += parts.length - 1;
      out = parts.join(info.placeholder);
    }

    // ② 敏感标签对应的值（键值表结构）
    out = out.replace(
      LABELED_FIELD_RE,
      (_whole, open, _value, close) => {
        this.patternHits.labels = (this.patternHits.labels || 0) + 1;
        return `${open}${PLACEHOLDER.other}${close}`;
      }
    );

    // ③ 格式规则
    for (const rule of PATTERN_RULES) {
      out = out.replace(rule.re, (...args) => {
        this.patternHits[rule.type] = (this.patternHits[rule.type] || 0) + 1;
        return rule.ph(...args);
      });
    }

    return out;
  }

  /**
   * 终局重写：用当前完整的已知值集合重新处理已落盘文本
   *
   * 为什么需要：真实姓名等值是在探测**中途**才从页面里发现的，
   * 而在它之前落盘的样本无法在写入时就替换掉。因此在探测结束时统一重跑一遍。
   *
   * @param {string} text 已落盘的文本（可能已脱敏过一次）
   * @returns {string}
   */
  redactAll(text) {
    return this.redact(text);
  }

  /** 按类型汇总屏蔽次数（含已知值替换与模式规则命中） */
  stats() {
    /** @type {Record<string, number>} */
    const byType = {};

    for (const info of this.known.values()) {
      if (info.hits === 0) continue;
      byType[info.type] = (byType[info.type] || 0) + info.hits;
    }

    for (const [type, hits] of Object.entries(this.patternHits)) {
      if (hits === 0) continue;
      // 键值表命中的是「标签后的值」，没有更细的类型信息
      const key = type === 'labels' ? 'sensitiveField' : type;
      byType[key] = (byType[key] || 0) + hits;
    }

    const total = Object.values(byType).reduce((sum, n) => sum + n, 0);
    return { total, byType, knownValues: this.known.size };
  }

  /** 重置命中统计（用于终局重写前重新计数） */
  resetStats() {
    for (const info of this.known.values()) info.hits = 0;
    this.patternHits = {};
  }

  /** 生成可写入 manifest 的说明 */
  describe() {
    if (!this.enabled) {
      return { enabled: false, note: '未脱敏：产物包含真实隐私数据，请勿分享' };
    }
    const { total, byType, knownValues } = this.stats();
    return {
      enabled: true,
      replacements: total,
      byType,
      knownValues,
      note: '产物已脱敏；脱敏需重新编码，故 samples/ 与 decoded/ 均为 UTF-8'
    };
  }
}

/**
 * 建立脱敏器并预置登录账号
 * @param {Object} [options]
 * @param {boolean} [options.enabled]
 * @param {string} [options.username] 登录学号
 */
export function createRedactor({ enabled = true, username = '' } = {}) {
  const redactor = new Redactor({ enabled });
  if (username) redactor.addKnown('username', username);
  return redactor;
}
