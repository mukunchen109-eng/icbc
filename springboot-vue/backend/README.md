# 后端

Spring Boot 3 / Java 17 的 API 骨架。数据库确认后可添加驱动、持久层和迁移脚本。

```powershell
mvn spring-boot:run
```

用户与角色数据存储在 PolarDB/MySQL 的 `db1.sys_user`、`db1.sys_role`。新建用户时密码使用 BCrypt 哈希存储，登录时使用 BCrypt 校验。
