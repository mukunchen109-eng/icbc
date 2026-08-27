# 金融资讯智能化处理系统

前后端分离的项目骨架：后端 Spring Boot 3 + Java 17，前端 Vue 3 + Vite + Pinia + Vue Router。

## 启动

```powershell
cd backend; mvn spring-boot:run
cd frontend; npm install; npm run dev
```

数据库连接信息已写入后端配置；地址和账号仍可通过 `ICBC_DB_URL`、`ICBC_DB_USERNAME` 环境变量覆盖。
