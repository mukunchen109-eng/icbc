# 数据库表文档

说明：
- 当前代码通过配置项读取表名
- 默认表名来自 `src/main/resources/application.yml`
- 下列字段是代码实际读写字段，具体 SQL 类型以你们现有数据库为准

## 配置项

- `app.report.news-table = news_pool`
- `app.report.report-table = generated_report`

## `news_pool`

用途：
- 日报生成时按 `news_date` 查询资讯池

代码读字段：
- `news_date`
- `title`
- `content`

逻辑字段说明：

| 字段名 | 逻辑类型 | 说明 |
| --- | --- | --- |
| `news_date` | string / date key | 查询条件，格式为 `yyyy-MM-dd` |
| `title` | text | 资讯标题 |
| `content` | long text | 资讯正文 |

查询语句：

```sql
select news_date, title, content
from news_pool
where news_date = ?
order by title
```

## `generated_report`

用途：
- 生成日报后写入一条报表记录

代码写字段：
- `report_id`
- `report_date`
- `report_title`
- `article_count`
- `content_snapshot`
- `word_file_path`
- `pdf_file_path`
- `created_at`

逻辑字段说明：

| 字段名 | 逻辑类型 | 说明 |
| --- | --- | --- |
| `report_id` | string | 报表唯一 ID |
| `report_date` | date | 报表日期 |
| `report_title` | string | 报表标题 |
| `article_count` | int | 当次参与生成的资讯条数 |
| `content_snapshot` | long text | 生成时拼装的原始内容快照 |
| `word_file_path` | string | Word 文件本地绝对路径 |
| `pdf_file_path` | string | PDF 文件本地绝对路径 |
| `created_at` | timestamp | 生成时间 |

写入语句：

```sql
insert into generated_report
  (report_id, report_date, report_title, article_count, content_snapshot, word_file_path, pdf_file_path, created_at)
values (?, ?, ?, ?, ?, ?, ?, ?)
```

## 现状说明

- 当前报表文件路径存的是本地绝对路径
- `report` 模块的 `list/detail/download` 读的是当前 JVM 内存中的报表对象
- 如果服务重启，历史报表列表不会从数据库自动回填

如果后面要做持久化历史列表，建议补一层：
- 启动时读取 `generated_report`
- 重新构建报表索引
- 或直接把报表详情改成查库实现
