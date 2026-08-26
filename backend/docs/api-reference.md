# 接口文档

## 通用响应格式

所有 JSON 接口成功时统一返回 `ApiResponse`：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

说明：
- `code = 0` 表示成功
- `message` 为提示信息
- `data` 为业务数据

当前项目没有统一异常包装器，业务异常通常通过 HTTP 状态码 + Spring 默认错误体返回。

## 认证模块

### `POST /api/auth/login`

请求体：

```json
{
  "username": "admin",
  "password": "123456"
}
```

字段说明：
- `username` 必填
- `password` 必填

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "dev-token",
    "user": {
      "username": "admin",
      "role": "ADMIN"
    }
  }
}
```

## 报表模块

### `GET /api/reports`

返回当前 JVM 内已生成报表列表。

响应数据：

```json
[
  {
    "reportId": "f8c0...",
    "reportTitle": "每日资讯摘要 2026-08-24",
    "reportDate": "2026-08-24",
    "articleCount": 12,
    "generatedAt": "2026-08-25T02:11:03Z",
    "wordFileName": "每日资讯摘要.docx",
    "pdfFileName": "每日资讯摘要.pdf"
  }
]
```

### `GET /api/reports/{reportId}`

返回某次报表的详情。

响应数据：

```json
{
  "reportId": "f8c0...",
  "reportTitle": "每日资讯摘要 2026-08-24",
  "reportDate": "2026-08-24",
  "content": "Target report date: ...",
  "articleCount": 12,
  "generatedAt": "2026-08-25T02:11:03Z",
  "wordFile": {
    "format": "WORD",
    "fileName": "每日资讯摘要.docx",
    "absolutePath": "C:\\...\\target\\generated-reports\\2026-08-24\\每日资讯摘要.docx",
    "downloadUrl": "/api/reports/f8c0.../files/word"
  },
  "pdfFile": {
    "format": "PDF",
    "fileName": "每日资讯摘要.pdf",
    "absolutePath": "C:\\...\\target\\generated-reports\\2026-08-24\\每日资讯摘要.pdf",
    "downloadUrl": "/api/reports/f8c0.../files/pdf"
  }
}
```

### `POST /api/reports/daily-summary`

生成日报。

请求体：

```json
{
  "reportDate": "2026-08-24",
  "reportTitle": "每日资讯摘要 2026-08-24"
}
```

字段说明：
- `reportDate` 必填，格式 `yyyy-MM-dd`
- `reportTitle` 选填，不传则后端默认生成

响应数据与 `GET /api/reports/{reportId}` 一致。

### `GET /api/reports/{reportId}/files/{format}`

下载报表文件。

参数：
- `reportId` 报表 ID
- `format` 支持 `word`、`docx`、`pdf`

返回：
- `word/docx` 返回 Word 文件流
- `pdf` 返回 PDF 文件流

响应头：
- `Content-Type`
- `Content-Disposition: attachment`

## 采集模块

### `GET /api/collections/status`

返回采集状态。

响应数据：

```json
{
  "latestRun": "待配置",
  "total": 0,
  "source": "IPA / Excel"
}
```

### `POST /api/collections/run`

提交采集任务。

响应数据：

```json
{
  "message": "已提交采集任务"
}
```

## 预处理模块

### `GET /api/preprocess/status`

响应数据：

```json
{
  "pending": 0,
  "processed": 0,
  "deduplicated": 0
}
```

## 审核模块

### `GET /api/reviews/pending`

响应数据：

```json
{
  "initial": 0,
  "final": 0
}
```

## 任务模块

### `GET /api/tasks`

响应数据：

```json
[
  {
    "code": "daily-collection",
    "name": "每日资讯采集",
    "cron": "09:00",
    "status": "待配置"
  }
]
```

## 用户模块

### `GET /api/users`

响应数据：

```json
[
  {
    "id": 1,
    "username": "admin",
    "displayName": "系统管理员",
    "role": "ADMIN",
    "status": "启用"
  }
]
```

## 归档模块

### `GET /api/archives/summary`

响应数据：

```json
{
  "reports": 0,
  "auditLogs": 0,
  "mailLogs": 0
}
```

## 分发模块

### `POST /api/distributions/send`

响应数据：

```json
{
  "message": "邮件分发任务已提交"
}
```

## 联调注意点

- `report` 模块当前使用内存保存已生成报表，服务重启后不会保留历史 `reportId`
- 报表文件当前落地到本地目录 `target/generated-reports/{newsDate}/`
- 前端拿到 `downloadUrl` 后可直接请求下载
- 业务错误目前没有统一的业务码表，按 HTTP 状态码和默认错误体处理
