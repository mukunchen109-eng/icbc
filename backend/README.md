# 后端

Spring Boot 3 / Java 17 的 API 骨架。数据库确认后可添加驱动、持久层和迁移脚本。

```powershell
mvn spring-boot:run
```

用户与角色数据存储在 PolarDB/MySQL 的 `db1.sys_user`、`db1.sys_role`。新建用户时密码使用 BCrypt 哈希存储，登录时使用 BCrypt 校验。

## 邮件发送配置

部室负责人发送终审报告前，需要配置 SMTP。以 SSL 邮箱为例：

```bash
export MAIL_ENABLED=true
export MAIL_HOST=smtp.example.com
export MAIL_PORT=465
export MAIL_USERNAME=sender@example.com
export MAIL_PASSWORD=邮箱SMTP授权码
export MAIL_FROM=sender@example.com
mvn spring-boot:run
```

邮件附带最终报告的 PDF 和 Word 文件。报告、原始资讯 CSV、审核日志、邮件日志及归档 ZIP 默认保存在 `backend/data/archives`，可用 `ARCHIVE_ROOT` 修改目录。非 macOS 环境还需用 `REPORT_FONT_PATH` 指定支持中文的 `.ttf` 字体。
