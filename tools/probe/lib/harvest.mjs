/**
 * 参数收割
 *
 * 目录里有一批接口登记为「无参数」或「占位参数」，直接探测只能拿到错误页；
 * 但它们在系统**其它页面的链接里带着真实参数**出现：
 *
 *   课程详情   course_detail.jsdo?cid=248686              ← 本学期课程列表里
 *   教师信息   showTeacherInfoItem.do?userid=113679       ← 同上
 *   教学报告   schoolTeachingReportIndexStudent.do?scoreid=233727590  ← 同上
 *   教学计划   scheduleJump.jsp?...&studentId=<加密串>    ← 个人教学计划页里
 *
 * 这些参数**只在运行期的响应里出现**，而且（对学生上下文）随学期变化，
 * 无法提前写死。因此探测时从响应里即时收割，再带着真实参数补测一遍。
 *
 * ⚠️ 收割必须发生在**脱敏之前**：加密串是绑定账号的敏感值，
 * 落盘时已被替换成占位符，从文件里收割只会拿到 `[加密串已脱敏]`。
 * 所以 `SampleStore.save()` 在脱敏前调用本模块的 `collectFromText()`。
 */

/**
 * 收割规则
 *
 * `re` 从响应文本里提取参数值；`build` 用提取结果构造候选接口。
 * `markers` 用于判定「拿到数据」——**必须给出**，否则错误页和跳转页
 * 也会因为「无关键字约束」被误判成有数据。
 * `limit` 限制每个规则最多生成几个候选——同一接口往往在页面上
 * 出现几十次（每门课一个链接），逐个探测没有意义。
 */
export const HARVEST_RULES = [
  {
    id: 'course.detail',
    note: '课程详情：cid 是课程实例 ID，同一门课不同学期/班级各不相同',
    re: /course_detail\.jsdo\?cid=(\d+)/gi,
    limit: 3,
    markers: ['课程', '学分'],
    build: (m) => ({
      base: 'manager/querycourse/course_detail.jsdo',
      query: `cid=${m[1]}`,
      label: `cid=${m[1]}`
    })
  },
  {
    id: 'course.teacherInfo',
    note: '教师信息：userid 是教师明文 ID（与学生内部 ID 不同源）',
    re: /showTeacherInfoItem\.do\?userid=(\d+)/gi,
    limit: 3,
    markers: ['姓名', '性别', '院系'],
    build: (m) => ({
      base: 'manager/teacherinfo/showTeacherInfoItem.do',
      query: `userid=${m[1]}`,
      label: `userid=${m[1]}`
    })
  },
  {
    id: 'eva.report',
    note: '教学报告：scoreid 是成绩记录 ID，每门课一条',
    re: /schoolTeachingReportIndexStudent\.do\?scoreid=(\d+)/gi,
    limit: 2,
    markers: ['教学', '课程'],
    build: (m) => ({
      base: 'teacher/teachingtask/schoolTeachingReportIndexStudent.do',
      query: `scoreid=${m[1]}`,
      label: `scoreid=${m[1]}`
    })
  },
  {
    id: 'timetable.scheduleJump',
    note: '教学计划跳转器：本身只是个 JS 跳转页，价值在于揭示真实目标地址',
    re: /scheduleJump\.jsp\?link=([\w.]+)&studentId=([A-Za-z0-9+/=]{10,})&classId=(\d*)/gi,
    limit: 4,
    markers: ['location.href'],
    build: (m) => ({
      base: 'manager/studyschedule/scheduleJump.jsp',
      query: `link=${m[1]}&studentId=${m[2]}&classId=${m[3]}`,
      label: `${m[1]}`
    })
  },
  {
    id: 'timetable.studentSchedule',
    note: '教学计划真实页面：由 scheduleJump 跳转目标揭示，需加密 studentId 原样回传',
    re: /location\.href="(studentSchedule\w+\.do)(\?[^"]*studentId=[A-Za-z0-9+/=]{10,}[^"]*)"/gi,
    limit: 4,
    markers: ['教学计划', '课程', '学期'],
    build: (m) => ({
      base: `manager/studyschedule/${m[1]}`,
      query: m[2].replace(/^\?/, ''),
      label: m[1]
    })
  }
];

/** 推断候选接口所属模块 */
function inferModule(base) {
  if (/querycourse|teacherinfo/.test(base)) return 'course';
  if (/studyschedule/.test(base)) return 'timetable';
  if (/teachingtask|schoolTeachingReport/.test(base)) return 'eva';
  return 'auth';
}

/**
 * 从一段响应文本里收集可收割的参数
 *
 * @param {string} text 响应正文（**必须是脱敏前的原文**）
 * @param {Set<string>} [seen] 已收集项的去重集合（跨调用共享）
 * @returns {Array<{ruleId:string, candidate:Object}>}
 */
export function collectFromText(text, seen = new Set()) {
  const src = String(text || '');
  if (!src) return [];

  /** @type {Array<{ruleId:string, candidate:Object}>} */
  const out = [];

  for (const rule of HARVEST_RULES) {
    rule.re.lastIndex = 0;
    let taken = 0;
    let m;

    while ((m = rule.re.exec(src)) !== null) {
      if (taken >= rule.limit) break;

      const built = rule.build(m);
      const full = `${built.base}?${built.query}`;
      if (seen.has(full)) continue;
      seen.add(full);

      out.push({
        ruleId: rule.id,
        candidate: {
          key: `harvest.${rule.id}.${built.label.replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '')}`,
          name: `（参数收割）${built.base}`,
          module: inferModule(built.base),
          path: full,
          method: 'GET',
          params: {},
          confidence: 'discovered',
          markers: rule.markers || [],
          note: `${rule.note}（收割自运行期响应，参数：${built.label}）`,
          harvestedFrom: rule.id
        }
      });

      taken += 1;
    }
  }

  return out;
}

/**
 * 把收割结果转成可探测的接口定义
 * @param {Array<{ruleId:string, candidate:Object}>} collected
 * @returns {Array<Object>}
 */
export function toEndpointSpecs(collected) {
  return collected.map(({ candidate }) => candidate);
}
