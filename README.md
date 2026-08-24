# 金融资讯智能化处理系统

前后端分离的项目骨架：后端 Spring Boot 3 + Java 17，前端 Vue 3 + Vite + Pinia + Vue Router。

## 启动

```powershell
cd backend; mvn spring-boot:run
cd frontend; npm install; npm run dev
```

数据库方案待确认后，将在 `backend/src/main/resources/application.yml` 中补充连接配置并接入持久化实体。
