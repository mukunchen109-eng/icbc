# Dify Workflow 集成说明

## 当前联调目标

当前阶段不是先接真实 Dify，而是先打通下面这条链路：

数据库查询 -> 后端拼装 content -> mock Dify 返回 -> 生成 Word/PDF -> 写入 report_version -> Apifox 调试

所以现在的 Dify 调用默认是 mock 模式。

## 数据库查询逻辑

后端按请求中的 `reportDate` 查询资讯池。

默认表名：

- `news_pool`

查询字段固定为：

- `news_date`
- `title`
- `content`

对应代码：

- `src/main/java/com/icbc/financialinfo/modules/report/repository/NewsPoolRepository.java`

SQL 逻辑是：

```sql
select news_date, title, content
from news_pool
where news_date = ?
order by title
```

如果实际表名不是 `news_pool`，可以通过配置项修改：

```yaml
app:
  report:
    news-table: your_table_name
```

## report_version 写入逻辑

生成完 Word/PDF 后，后端会向 `report_version` 新插入一条记录。

默认表名：

- `report_version`

写入字段：

- `report_id`
- `report_date`
- `report_title`
- `report_content`
- `word_file_path`
- `pdf_file_path`
- `created_at`

对应代码：

- `src/main/java/com/icbc/financialinfo/modules/report/repository/ReportVersionRepository.java`

如果你的实际表名不同，可以改配置：

```yaml
app:
  report:
    report-version-table: your_report_version_table
```

## 接口请求结构

当前生成日报接口：

- `POST /api/reports/daily-summary`

请求体：

```json
{
  "reportDate": "2026-08-24",
  "reportTitle": "每日资讯摘要（2026-08-24）"
}
```

说明：

- `reportDate` 必填
- `reportTitle` 可选，不传时后端自动生成默认标题

## 后端传给 Dify 的 3 个输入字段

后端当前固定传这 3 个字段：

- `news_date`
- `title`
- `content`

示例：

```json
{
  "inputs": {
    "news_date": "2026-08-24",
    "title": "每日资讯摘要（2026-08-24）",
    "content": "目标报告日期：2026-08-24\n同日期资讯数量：19\n说明：以下为数据库中该日期下的资讯池原始内容，请仅基于这些内容生成日报。\n\n[资讯1]\n标题：...\n日期：2026-08-24\n正文：..."
  },
  "response_mode": "blocking",
  "user": "report-module"
}
```

## mock Dify 逻辑

当前默认开启：

```yaml
app:
  report:
    dify:
      mock-enabled: true
```

对应代码：

- `src/main/java/com/icbc/financialinfo/modules/report/service/DifyService.java`

mock 模式下不会发出真实 HTTP 请求，而是：

1. 从 `content` 中提取标题
2. 自动拼一份固定格式的模拟日报
3. 返回给 `ReportService`

这样你用 Apifox 时，不需要先把 Dify 搭好，也能验证：

- 数据库是否查到了数据
- 接口是否通了
- 报告是否生成了
- Word/PDF 是否能下载
- `report_version` 是否写入了文件路径
