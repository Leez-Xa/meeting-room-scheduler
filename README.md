# 企业会议室智能预约调度系统

Java 21 + Spring Boot 3.5 + Vue 3/TypeScript/Vite 的企业会议室预约调度系统。后端通过 `/api/v1` 提供 JSON 接口，前端与后端独立运行。

## 环境要求

- Java 21
- Node.js 20 或更高版本
- Maven 3.9+（也可使用 IntelliJ IDEA 自带 Maven）
- MySQL 8；无 MySQL 时可使用 H2 demo profile

## 启动后端

在 `backend/` 目录执行：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

后端默认监听 `http://127.0.0.1:8080`。也可以先执行 `mvn package`，再运行生成的 Spring Boot JAR。

## 启动前端

在 `frontend/` 目录执行：

```powershell
npm install
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`，开发服务器会把 `/api` 请求转发到后端。

## 构建

```powershell
# backend/
mvn test
mvn package

# frontend/
npm install
npm run build
```

## 数据库升级

新数据库由 `backend/src/main/resources/schema.sql` 初始化。已有数据库按顺序执行：

- `backend/src/main/resources/migrations/V20261014__system_config.sql`
- `backend/src/main/resources/migrations/V20261026__recurring_meeting.sql`

MySQL 连接通过 `MEETING_DB_URL`、`MEETING_DB_USER` 和 `MEETING_DB_PASSWORD` 环境变量配置，示例配置不包含真实凭据。

## 主要功能

- 三角色登录、预约、审批、取消和会议室周排期
- 会议室容量、固定设备和时间冲突的服务端事务校验
- 每周 2–12 次周期预约、整组冲突回滚、整组审批和单场/全部未开始场次取消
- 系统管理员全量、部门管理员本部门 CSV 报表导出
- 系统参数、站内消息、审计和启动状态恢复
