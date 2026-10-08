/**
 * 教务在线 HTML 响应解析器
 * 基于 DOMParser 与精确字段抽取，实现现代化前端结构化数据输出
 */

/**
 * 将 HTML 文本解析为 DOM 树
 * @param {string} html
 * @returns {Document}
 */
export function createDom(html) {
  const parser = new DOMParser();
  return parser.parseFromString(html, 'text/html');
}

/**
 * 剥离 HTML 注释，防止注释内的单元格导致表格解析列错位
 * @param {string} html
 * @returns {string}
 */
export function stripHtmlComments(html) {
  return (html || '').replace(/<!--[\s\S]*?-->/g, '');
}

/**
 * 解析学生上下文（/academic/student/currcourse/currcourse.jsdo）
 */
export function parseStudentContext(html) {
  const doc = createDom(html);

  // 1. 内部学生 ID、学年
  let studentId = '';
  let year = '';
  const ctrtMatch = html.match(/studentid=["']?(\d+)["']?[\s\S]*?year=["']?(\d+)["']?/i);
  if (ctrtMatch) {
    studentId = ctrtMatch[1];
    year = ctrtMatch[2];
  } else {
    const sidM = html.match(/studentid\s*=\s*["']?(\d+)["']?/i);
    const yrM = html.match(/year\s*=\s*["']?(\d+)["']?/i);
    if (sidM) studentId = sidM[1];
    if (yrM) year = yrM[1];
  }

  // 2. 学期
  let term = '2';
  const termSelect = doc.querySelector('select[name="term"]');
  if (termSelect) {
    const selectedOption = termSelect.querySelector('option[selected]') || termSelect.selectedOptions?.[0];
    if (selectedOption) {
      term = selectedOption.value;
    }
  }

  // 3. 本学期课程列表
  const courses = [];
  const table = doc.querySelector('table.infolist_tab');
  if (table) {
    const rows = table.querySelectorAll('tr.infolist_common');
    rows.forEach(tr => {
      const tds = tr.querySelectorAll('td');
      if (tds.length >= 6) {
        courses.push({
          courseId: tds[0]?.textContent.trim() || '',
          courseSeq: tds[1]?.textContent.trim() || '',
          courseName: tds[2]?.textContent.trim() || '',
          teacher: tds[3]?.textContent.trim() || '',
          credit: parseFloat(tds[4]?.textContent.trim()) || 0,
          property: tds[5]?.textContent.trim() || '',
          examWay: tds[6]?.textContent.trim() || '',
          examType: tds[7]?.textContent.trim() || '',
          timeAndPlace: tds[9]?.textContent.trim() || ''
        });
      }
    });
  }

  return {
    studentId,
    year,
    term,
    courses
  };
}

/**
 * 解析个人信息与学籍异动（/academic/showPersonalInfo.do）
 */
export function parsePersonalInfo(html) {
  const doc = createDom(html);
  const info = {
    studentNumber: '',
    realName: '',
    college: '',
    major: '',
    direction: '',
    studentType: '',
    grade: '',
    className: '',
    idCard: '',
    email: '',
    phone: '',
    address: '',
    postalCode: '',
    status: '在籍（注册）',
    photoUrl: '',
    changes: []
  };

  // 1. 头像图片
  const photoImg = doc.querySelector('img[src*="loadphoto_added.jsdo"]') || doc.querySelector('img[src*="showStudentImage.jsp"]');
  if (photoImg) {
    info.photoUrl = photoImg.getAttribute('src') || '';
  }

  // 2. 基本信息键值表
  const formTable = doc.querySelector('table.form');
  if (formTable) {
    const rows = formTable.querySelectorAll('tr');
    rows.forEach(row => {
      const ths = row.querySelectorAll('th');
      const tds = row.querySelectorAll('td');
      for (let i = 0; i < ths.length; i++) {
        const key = ths[i]?.textContent.trim();
        const val = tds[i]?.textContent.replace(/&nbsp;/g, '').trim();
        if (!key || val === undefined) continue;

        if (key.includes('用户名')) info.studentNumber = val;
        else if (key.includes('真实姓名')) info.realName = val;
        else if (key.includes('所在院系')) info.college = val;
        else if (key.includes('专业') && !key.includes('方向')) info.major = val;
        else if (key.includes('方向')) info.direction = val;
        else if (key.includes('学生类别')) info.studentType = val;
        else if (key.includes('年级')) info.grade = val;
        else if (key.includes('班级')) info.className = val;
        else if (key.includes('证件号码')) info.idCard = val;
        else if (key.includes('电子邮箱')) info.email = val;
        else if (key.includes('联系电话')) info.phone = val;
        else if (key.includes('通讯地址')) info.address = val;
        else if (key.includes('邮政编码')) info.postalCode = val;
      }
    });
  }

  // 3. 学籍异动表
  const changesTable = doc.querySelector('table.datalist');
  if (changesTable) {
    const rows = changesTable.querySelectorAll('tr.infolist_hr_common, tr.infolist_common');
    rows.forEach(tr => {
      const tds = tr.querySelectorAll('td');
      if (tds.length >= 4) {
        info.changes.push({
          type: tds[0]?.textContent.replace(/&nbsp;/g, '').trim() || '',
          date: tds[1]?.textContent.replace(/&nbsp;/g, '').trim() || '',
          reason: tds[2]?.textContent.replace(/&nbsp;/g, '').trim() || '',
          remark: tds[3]?.textContent.replace(/&nbsp;/g, '').trim() || ''
        });
      }
    });
  }

  return info;
}

/**
 * 解析成绩查询列表（/academic/manager/score/studentOwnScore.do）
 */
export function parseScores(html) {
  const doc = createDom(html);

  // 1. 可用学年与学期
  const yearOptions = [];
  const yearSelect = doc.querySelector('select[name="year"]');
  if (yearSelect) {
    yearSelect.querySelectorAll('option').forEach(opt => {
      const val = opt.value.trim();
      const txt = opt.textContent.trim();
      if (val) yearOptions.push({ value: val, label: txt });
    });
  }

  // 2. 成绩数据表
  const table = doc.querySelector('table.datalist');
  const scores = [];

  if (table) {
    const trList = table.querySelectorAll('tr');
    if (trList.length > 1) {
      // 解析表头
      const headerThs = trList[0].querySelectorAll('th');
      const headers = Array.from(headerThs).map(th => th.textContent.trim());

      const colYear = headers.findIndex(h => h === '学年');
      const colTerm = headers.findIndex(h => h === '学期');
      const colCode = headers.findIndex(h => h === '课程号');
      const colName = headers.findIndex(h => h === '课程名');
      const colSeq = headers.findIndex(h => h === '课序号');
      const colGroup = headers.findIndex(h => h === '课组');
      const colTotal = headers.findIndex(h => h === '总评');
      const colCredit = headers.findIndex(h => h === '学分');
      const colHours = headers.findIndex(h => h === '学时');
      const colProp = headers.findIndex(h => h === '选课属性');
      const colRemark = headers.findIndex(h => h === '备注');
      const colExamType = headers.findIndex(h => h === '考试性质');
      const colPassMark = headers.findIndex(h => h === '及格标志');

      for (let i = 1; i < trList.length; i++) {
        const tds = trList[i].querySelectorAll('td');
        if (tds.length === 0) continue;

        const year = tds[colYear]?.textContent.trim() || '';
        const term = tds[colTerm]?.textContent.trim() || '';
        const courseId = tds[colCode]?.textContent.trim() || '';
        const courseName = tds[colName]?.textContent.trim() || '';
        const courseSeq = tds[colSeq]?.textContent.trim() || '';
        const courseGroup = tds[colGroup]?.textContent.trim() || '';
        const rawScore = tds[colTotal]?.textContent.trim() || '';
        const credit = parseFloat(tds[colCredit]?.textContent.trim()) || 0;
        const hours = parseInt(tds[colHours]?.textContent.trim(), 10) || 0;
        const property = tds[colProp]?.textContent.trim() || '';
        const remark = tds[colRemark]?.textContent.trim() || '';
        const examType = tds[colExamType]?.textContent.trim() || '';
        const passMark = tds[colPassMark]?.textContent.trim() || '';

        // 判断是否及格
        const numScore = parseFloat(rawScore);
        let passed = true;
        if (!isNaN(numScore)) {
          passed = numScore >= 60;
        } else if (passMark) {
          passed = !passMark.includes('不') && passMark !== '不及格';
        } else if (rawScore) {
          passed = rawScore !== '不及格' && rawScore !== '不合格';
        }

        scores.push({
          year,
          term,
          courseId,
          courseName,
          courseSeq,
          courseGroup,
          score: rawScore,
          credit,
          hours,
          property,
          remark,
          examType,
          passMark,
          passed
        });
      }
    }
  }

  return {
    scores,
    yearOptions
  };
}

/**
 * 解析课程表（/academic/manager/coursearrange/showTimetable.do）
 */
export function parseTimetable(html) {
  const doc = createDom(html);
  const table = doc.querySelector('table#timetable');
  const cells = [];
  const unarranged = [];

  if (table) {
    const rows = table.querySelectorAll('tr.infolist_hr_common');
    rows.forEach((tr, rowIndex) => {
      const th = tr.querySelector('th');
      const sectionLabel = th ? th.textContent.replace(/<br>/g, '').trim() : `第${rowIndex + 1}大节`;

      const tds = tr.querySelectorAll('td');
      tds.forEach((td, dayIndex) => {
        const id = td.getAttribute('id') || '';
        const rawHtml = td.innerHTML.trim();

        if (!rawHtml || rawHtml === '&nbsp;' || td.textContent.trim() === '') {
          return;
        }

        // 以 <br> 分割课程详情
        const lines = rawHtml.split(/<br\s*\/?>/i).map(l => l.replace(/<[^>]+>/g, '').trim()).filter(Boolean);
        if (lines.length === 0) return;

        // 第一行通常是 <<课程名>>;课序号
        let courseName = lines[0] || '';
        let courseSeq = '';
        const nameMatch = courseName.match(/<<?(.*?)(?:>>|;)?(?:\s*;\s*(\d+))?$/);
        if (nameMatch) {
          courseName = nameMatch[1] || courseName;
          courseSeq = nameMatch[2] || '';
        }
        courseName = courseName.replace(/^[<《]+|[>》]+$/g, '').trim();

        const location = lines[1] || '';
        const teacher = lines[2] || '';
        const weeks = lines[3] || '';
        const hoursType = lines[4] || '';

        // id 格式形如 "1-4367" 或 "1-1"，前缀为星期几 (1~7)
        let day = dayIndex + 1;
        if (id) {
          const parts = id.split('-');
          const parsedDay = parseInt(parts[0], 10);
          if (parsedDay >= 1 && parsedDay <= 7) day = parsedDay;
        }

        cells.push({
          id,
          day, // 1~7 (周一到周日)
          sectionIndex: rowIndex + 1, // 1~6 大节
          sectionLabel,
          courseName,
          courseSeq,
          location,
          teacher,
          weeks,
          hoursType,
          rawLines: lines
        });
      });
    });
  }

  // 没有具体上课时间或地点的课程
  const noArrTable = doc.querySelector('table#noArrangement');
  if (noArrTable) {
    const rows = noArrTable.querySelectorAll('tr');
    for (let i = 1; i < rows.length; i++) {
      const tds = rows[i].querySelectorAll('td');
      if (tds.length >= 8) {
        unarranged.push({
          courseId: tds[0]?.textContent.trim() || '',
          courseName: tds[1]?.textContent.trim() || '',
          courseSeq: tds[2]?.textContent.trim() || '',
          teacher: tds[3]?.textContent.trim() || '',
          mergeClass: tds[4]?.textContent.trim() || '',
          weeks: tds[5]?.textContent.trim() || '',
          day: tds[6]?.textContent.trim() || '',
          location: tds[7]?.textContent.trim() || ''
        });
      }
    }
  }

  return {
    cells,
    unarranged
  };
}

/**
 * 解析考试安排（/academic/manager/examstu/studentQueryAllExam.do 或 student/exam/index.jsdo）
 */
export function parseExams(html) {
  // 先剥离 HTML 注释，防止注释导致的表头与单元格错位
  const cleanHtml = stripHtmlComments(html);
  const doc = createDom(cleanHtml);

  const exams = [];
  const table = doc.querySelector('table.datalist') || doc.querySelector('table.infolist_tab');

  if (table) {
    const trList = table.querySelectorAll('tr');
    if (trList.length > 1) {
      const ths = trList[0].querySelectorAll('th');
      const headers = Array.from(ths).map(th => th.textContent.trim());

      const colCode = headers.findIndex(h => h.includes('课程号'));
      const colName = headers.findIndex(h => h.includes('课程名称'));
      const colTime = headers.findIndex(h => h.includes('考试时间'));
      const colLoc = headers.findIndex(h => h.includes('考试地点'));
      const colProp = headers.findIndex(h => h.includes('考试性质'));

      for (let i = 1; i < trList.length; i++) {
        const tds = trList[i].querySelectorAll('td');
        if (tds.length < 4) continue;

        const courseId = tds[colCode >= 0 ? colCode : 0]?.textContent.trim() || '';
        const courseName = tds[colName >= 0 ? colName : 1]?.textContent.trim() || '';
        const time = tds[colTime >= 0 ? colTime : 2]?.textContent.trim() || '';
        const location = tds[colLoc >= 0 ? colLoc : 3]?.textContent.trim() || '';
        const property = tds[colProp >= 0 ? colProp : 4]?.textContent.trim() || '正常考试';

        if (!courseName && !courseId) continue;

        exams.push({
          courseId,
          courseName,
          time,
          location,
          property
        });
      }
    }
  }

  return exams;
}

/**
 * 解析教学周与校历信息（/academic/calendarinfo/viewCalendarInfo.do）
 */
export function parseCalendarInfo(html) {
  const doc = createDom(html);

  // 1. 当前周次
  let currentWeek = 1;
  const curTd = doc.querySelector('.week td.cur span') || doc.querySelector('.curweek strong');
  if (curTd) {
    const num = parseInt(curTd.textContent.trim(), 10);
    if (!isNaN(num)) currentWeek = num;
  }

  // 2. 学期名称
  let semesterName = '';
  const curWeekDiv = doc.querySelector('.curweek');
  if (curWeekDiv) {
    semesterName = curWeekDiv.textContent.replace(/\s+/g, ' ').replace(/第.*周/, '').trim();
  }

  // 3. 公告内容
  const notices = [];
  const textWrapper = doc.querySelector('#textwrapper');
  if (textWrapper) {
    const items = textWrapper.querySelectorAll('p, div, li');
    items.forEach(el => {
      const text = el.textContent.trim();
      if (text) {
        notices.push({
          title: text.slice(0, 50),
          content: text,
          date: new Date().toLocaleDateString('zh-CN')
        });
      }
    });
  }

  return {
    currentWeek,
    semesterName,
    notices
  };
}

/**
 * 解析培养方案与学分完成度（/academic/manager/studyschedule/studentScheduleShowByTerm.do）
 */
export function parseCurriculumPlan(html) {
  const doc = createDom(html);
  const groups = [];

  // 从下拉选项或表格中提取课组学分要求
  const select = doc.querySelector('select#syt12') || doc.querySelector('select[name="syt"]');
  if (select) {
    select.querySelectorAll('option').forEach(opt => {
      const text = opt.textContent.trim();
      // 匹配形如："学科基础课程（2023） 选课属性：必修 学分要求=26.5 门数要求=8"
      const m = text.match(/^(.*?)\s+选课属性：(.*?)?\s*学分要求=([\d.]+)\s*门数要求=(\d+)/);
      if (m) {
        groups.push({
          id: opt.value,
          name: m[1].trim(),
          property: m[2]?.trim() || '必修',
          requiredCredits: parseFloat(m[3]) || 0,
          requiredCourses: parseInt(m[4], 10) || 0,
          earnedCredits: 0,
          passedCourses: 0,
          courses: []
        });
      }
    });
  }

  // 提取课程明细
  const detailTable = doc.querySelector('table#output_ctx') || doc.querySelector('table.output_ctx');
  if (detailTable) {
    const rows = detailTable.querySelectorAll('tr');
    let currentGroupName = '';

    rows.forEach(tr => {
      const th = tr.querySelector('th');
      if (th && th.textContent.includes('课组')) {
        currentGroupName = th.textContent.replace(/.*课组[：:]\s*/, '').trim();
      }

      const tds = tr.querySelectorAll('td');
      if (tds.length >= 7) {
        const cCode = tds[1]?.textContent.trim();
        const cName = tds[2]?.textContent.trim();
        const cCredit = parseFloat(tds[3]?.textContent.trim()) || 0;
        const cHours = parseInt(tds[4]?.textContent.trim(), 10) || 0;
        const cProp = tds[5]?.textContent.trim() || '';

        if (cCode && cName && !isNaN(cCredit)) {
          const group = groups.find(g => g.name === currentGroupName) || groups[0];
          if (group) {
            group.courses.push({
              code: cCode,
              name: cName,
              credit: cCredit,
              hours: cHours,
              property: cProp
            });
          }
        }
      }
    });
  }

  return {
    groups
  };
}

/**
 * 解析教室时间占用（/academic/teacher/teachresource/roomschedulequery.jsdo）
 */
export function parseClassroomQueryOptions(html) {
  const doc = createDom(html);
  const areas = [];
  const buildings = [];
  const rooms = [];

  const aidSelect = doc.querySelector('select[name="aid"]');
  if (aidSelect) {
    aidSelect.querySelectorAll('option').forEach(opt => {
      if (opt.value) areas.push({ id: opt.value, name: opt.textContent.trim() });
    });
  }

  const bSelect = doc.querySelector('select[name="buildingid"]');
  if (bSelect) {
    bSelect.querySelectorAll('option').forEach(opt => {
      if (opt.value) buildings.push({ id: opt.value, name: opt.textContent.trim() });
    });
  }

  const rSelect = doc.querySelector('select[name="room"]');
  if (rSelect) {
    rSelect.querySelectorAll('option').forEach(opt => {
      if (opt.value) rooms.push({ id: opt.value, name: opt.textContent.trim() });
    });
  }

  return {
    areas,
    buildings,
    rooms
  };
}
