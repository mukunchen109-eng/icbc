# 消息格式文档

## 1. 后端 API 通用消息格式

### 成功响应

统一使用：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 失败响应

当前项目没有统一的错误包装器，通常表现为：
- HTTP 4xx / 5xx
- Spring 默认错误体

建议前端按 `HTTP status` 先分流，再读取返回体。

## 2. 前端调用 `POST /api/reports/daily-summary`

### 请求格式

```json
{
  "reportDate": "2026-08-24",
  "reportTitle": "每日资讯摘要 2026-08-24"
}
```

### 响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "reportId": "f8c0...",
    "reportTitle": "每日资讯摘要 2026-08-24",
    "reportDate": "2026-08-24",
    "content": "Target report date: 2026-08-24...",
    "articleCount": 12,
    "generatedAt": "2026-08-25T02:11:03Z",
    "wordFile": {
      "format": "WORD",
      "fileName": "每日资讯摘要.docx",
      "absolutePath": "C:\\...\\每日资讯摘要.docx",
      "downloadUrl": "/api/reports/f8c0.../files/word"
    },
    "pdfFile": {
      "format": "PDF",
      "fileName": "每日资讯摘要.pdf",
      "absolutePath": "C:\\...\\每日资讯摘要.pdf",
      "downloadUrl": "/api/reports/f8c0.../files/pdf"
    }
  }
}
```

## 3. 报表文件下载消息格式

### 请求

```http
GET /api/reports/{reportId}/files/{format}
```

### 响应

- `word` / `docx`：`application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `pdf`：`application/pdf`

返回的是文件流，不是 JSON。

## 4. 后端到 Dify 的内部消息格式

### 请求体

`DifyService` 发送给 Dify Workflow 的格式如下：

```json
{
  "inputs": {
    "news_date": "2026-08-24",
    "title": "每日资讯摘要 2026-08-24",
    "content": "Target report date: 2026-08-24\nArticle count: 12\nSource articles:\n..."
  },
  "response_mode": "blocking",
  "user": "report-module"
}
```

### 输入字段说明

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `news_date` | string | 日报日期，格式 `yyyy-MM-dd` |
| `title` | string | 报表标题 |
| `content` | string | 从数据库拼装出的原始资讯内容 |

### 输出解析规则

后端会从 Dify 返回体中按顺序寻找这些字段：
- `data.outputs.result`
- `data.outputs.report`
- `data.outputs.content`
- `data.outputs.text`
- `data.outputs.answer`

只要其中一个字段是非空字符串，就会被当作最终日报正文。

## 5. 后端生成的日报正文结构

内部拼装的 `content` 大致是这个格式：

```text
Target report date: 2026-08-24
Article count: 12
Source articles:

[Article 1]
Title: xxx
Date: 2026-08-24
Content: ...
```

这段内容会同时喂给 Dify，以及写入 `generated_report.content_snapshot`。

## 6. 其他模块的消息格式

### `GET /api/tasks`

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "code": "daily-collection",
      "name": "每日资讯采集",
      "cron": "09:00",
      "status": "待配置"
    }
  ]
}
```

### `GET /api/users`

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "displayName": "系统管理员",
      "role": "ADMIN",
      "status": "启用"
    }
  ]
}
```

### `GET /api/preprocess/status`

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "pending": 0,
    "processed": 0,
    "deduplicated": 0
  }
}
```
