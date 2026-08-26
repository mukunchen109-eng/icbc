# Financial Intelligence Backend

这是一个基于 Spring Boot 3 / Java 17 的后端项目。

## 当前 report 链路

`report` 模块现在按下面的方式工作：

1. 接口接收 `reportDate` 和可选的 `reportTitle`
2. 后端按 `reportDate` 去数据库表查询资讯池
3. 数据库字段固定使用：
   - `news_date`
   - `title`
   - `content`
4. 后端把查到的同日期资讯拼成 Dify Workflow 输入
5. 当前默认使用 mock Dify 调用，方便你用 Apifox 验证整条链路
6. 生成 `.docx` 和 `.pdf`
7. 将生成后的文件绝对路径写入 `report_version.word_file_path` 和 `report_version.pdf_file_path`

## 请求接口

`POST /api/reports/daily-summary`

请求体示例：

```json
{
  "reportDate": "2026-08-24",
  "reportTitle": "每日资讯摘要（2026-08-24）"
}
```

## 数据库约定

默认查询表：

- `news_pool`

默认查询字段：

- `news_date`
- `title`
- `content`

默认写入报告版本表：

- `report_version`

当前落库逻辑会新插入一条记录，并写入这些字段：

- `report_id`
- `report_date`
- `report_title`
- `report_content`
- `word_file_path`
- `pdf_file_path`
- `created_at`

如果你的实际表名不同，可以在 `application.yml` 里改：

```yaml
app:
  report:
    news-table: your_table_name
    report-version-table: your_report_version_table
```

## Dify Workflow 输入契约

当前后端向 Dify 传 3 个字段：

- `news_date`
- `title`
- `content`

其中：

- `news_date` 表示本次日报日期
- `title` 表示报告标题
- `content` 表示从数据库查出的同日期资讯长文本

当前默认开启 mock：

```yaml
app:
  report:
    dify:
      mock-enabled: true
```

这意味着即使不连真实 Dify，也能走完整个生成流程，适合用 Apifox 调试。

## Dify 配置

```yaml
app:
  report:
    output-dir: target/generated-reports
    news-table: news_pool
    report-version-table: report_version
    dify:
      base-url: http://your-dify-host
      api-key: your-api-key
      endpoint: /v1/workflows/run
      response-mode: blocking
      user: report-module
      mock-enabled: true
```

## 关键文件

- `src/main/java/com/icbc/financialinfo/modules/report/controller/ReportController.java`
- `src/main/java/com/icbc/financialinfo/modules/report/service/ReportService.java`
- `src/main/java/com/icbc/financialinfo/modules/report/service/DifyService.java`
- `src/main/java/com/icbc/financialinfo/modules/report/repository/NewsPoolRepository.java`
- `src/main/java/com/icbc/financialinfo/modules/report/repository/ReportVersionRepository.java`
- `src/main/java/com/icbc/financialinfo/modules/report/model/DifyWorkflowRequest.java`

## 启动方式

```powershell
mvn -DskipTests package
java -jar target\financial-intelligence-backend-0.0.1-SNAPSHOT.jar
```

用户与角色数据存储在 PolarDB/MySQL 的 `db1.sys_user`、`db1.sys_role`。新建用户时密码使用 BCrypt 哈希存储，登录时使用 BCrypt 校验。
