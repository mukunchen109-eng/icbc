# Dify Workflow 集成说明

## 当前联调目标

当前阶段不是先接真实 Dify，而是先打通下面这条链路：

数据库查询 -> 后端拼装 content -> mock Dify 返回 -> 生成 Word/PDF -> Apifox 调试

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

其中：

- `news_date`：本次日报目标日期
- `title`：报告标题
- `content`：由后端把数据库内容拼装成的长文本

## 为什么不再单独传 `article_count`

因为这个值完全可以在后端代码里确定，没必要再暴露成 Workflow 独立变量。

现在后端会：

1. 查询该日期下的资讯
2. 统计条数
3. 把“同日期资讯数量”写入 `content` 头部
4. 再传给 mock Dify 或真实 Dify

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

## Apifox 建议测试顺序

### 1. 确认数据库里有测试数据

确保目标表里至少有 19 条同一天的数据，例如：

- `news_date = 2026-08-24`

### 2. 调生成接口

请求：

```http
POST /api/reports/daily-summary
Content-Type: application/json
```

请求体：

```json
{
  "reportDate": "2026-08-24",
  "reportTitle": "每日资讯摘要（2026-08-24）"
}
```

### 3. 看返回结果

成功时你会拿到：

- `reportId`
- `content`
- `wordFile`
- `pdfFile`

### 4. 再测下载接口

- `GET /api/reports/{reportId}/files/word`
- `GET /api/reports/{reportId}/files/pdf`

## 当前限制

### 1. 现在是 mock 生成，不是真实大模型

所以当前日报内容只是测试用的模拟文本，不代表真实分析能力。

### 2. 数据库记录不足 19 条会直接报错

这是按你的日报规则做的硬校验。

### 3. 当前只按单表、三个字段查库

如果你后面要扩成多字段、多表联合查询，这一层可以继续扩展。
