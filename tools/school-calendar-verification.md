# 校历核验

2026-10-10 直接访问教务公开入口 `http://jwzx.hrbust.edu.cn/homepage/index.do`，找到「校历及节假日安排」链接：`http://jwzx.hrbust.edu.cn/homepage/infoSingleArticle.do?articleId=416`。文章发布日期仍显示 2024-03-02，但内嵌图片实际标题是「哈尔滨理工大学2026–2027学年校历」，不能按文章旧日期判断图片学年。

图中明确写明：秋季本科生、研究生上课时间为2026年8月31日，教学于2027年1月10日结束，共19教学周（包括考试）；春季上课时间为2027年3月1日，教学于2027年7月11日结束，共19教学周（包括考试）。校历表每学期列至26周，但19周以后没有教学，属于假期范围。

事实提取为 `shared/school-calendar.json`，网页直接读取；运行 `node tools/generate-school-calendar.mjs` 生成 Android 的 `SchoolCalendar.kt`，不独立维护另一套日期。未收录的新学年不沿用这份校历起点，保留明确同步/确认提示。
