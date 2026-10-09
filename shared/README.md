# 教务处公开资料目录

`resources.json` 收录教务处「资料下载」栏目下的全部分页标题、日期、原文及附件链接。网页构建时内联目录，Android 从 `assets/resources.json` 加载，搜索不需要连接教务。

从仓库根目录运行 `python tools/crawl-resources.py` 更新目录；脚本使用 Python 标准库，不需要额外依赖。每个分类的解析条数必须与学校分页统计一致，且取得全部栏目后才保存。逐门读取公开原文并解析附件地址，间隔 150ms，不登录、不下载附件；外部学院页面只保留学校目录给出的原文地址。

生成文件同步写入 `shared/resources.json` 与 `android/app/src/main/assets/resources.json`。运行 `node web/scripts/test-resources.mjs` 核验两端一致性、分类条数、唯一标识与学校域名链接。索引日期是抓取时间；办理流程、最新通知及附件版本以学校原文为准。
