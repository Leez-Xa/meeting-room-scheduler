# 企业会议室智能预约调度系统

前后端分离的会议室预约系统，支持三角色账号、会议室排期、预约与审批、资源冲突校验、会议室推荐、站内消息和会前提醒。

- 后端：Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus。
- 前端：Vue 3、TypeScript、Vite、Element Plus、FullCalendar。
- 数据库：提供可独立运行的 H2 演示模式，以及 MySQL 8 配置。

## 环境准备

安装 JDK 21、Maven 3.9 和 Node.js 22（包含 npm），确保 `java`、`mvn`、`node`、`npm` 可在终端执行。下载或克隆代码后，在项目根目录操作。首次构建需要联网获取依赖；代码包不包含依赖目录和已编译产物。

## 启动 H2 演示

无需安装 MySQL。打开第一个终端，构建并启动后端：

```shell
cd backend
mvn package
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

后端默认监听 8080。首次启动自动建表并生成演示数据；H2 数据保存在后端工作目录的 `data/meeting-demo`，重启后保留。

在项目根目录打开第二个终端，安装前端依赖并启动：

```shell
cd frontend
npm ci
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`。前端开发服务器将 `/api` 请求转发到 `http://127.0.0.1:8080`。运行时保持两个终端打开，使用 Ctrl+C 停止服务。

## 演示账号

以下账号为初始化程序生成的演示数据，统一初始密码为 `Admin@123`：

| 账号 | 角色 |
| --- | --- |
| admin、admin2 | 系统管理员 |
| dept-admin、ops-admin | 部门管理员 |
| employee、ops-employee | 普通员工 |

系统不开放注册。系统管理员创建账号；管理员不能审批自己的申请，两个系统管理员可互相审批。演示初始化仅用于全新数据库，不会重置已有账号。

## 构建前端

在 `frontend` 目录执行：

```shell
npm run build
```

静态产物位于 `frontend/dist`。部署时由 Web 服务器提供前端页面，将 `/api` 反向代理到后端，并为前端路由配置入口页回退。开发时的 Vite 代理不会随静态产物部署。

## MySQL 8 模式

H2 用于快速演示。MySQL 8 的连接配置已提供，真实 MySQL 8 运行验收尚未完成。

准备一个名为 `meeting_scheduler` 的全新数据库及可访问该数据库的专用账号。连接信息使用环境变量配置，不需要把真实密码写入源码。

以下是在 Windows PowerShell 中启动的示例，在 `backend` 目录执行：

```powershell
$env:MEETING_DB_URL = 'jdbc:mysql://127.0.0.1:3306/meeting_scheduler?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false'
$env:MEETING_DB_USER = 'meeting_app'
$dbPassword = Read-Host '数据库密码' -AsSecureString
$env:MEETING_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password
# 仅在全新数据库首次初始化时启用以下两项。
$env:MEETING_DB_INIT_MODE = 'always'
$env:MEETING_DEMO_DATA = 'true'
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

首次初始化使用 `src/main/resources/schema.sql` 建表，并生成上述演示账号。后续启动设置 `MEETING_DB_INIT_MODE=never`、`MEETING_DEMO_DATA=false`。其他操作系统设置同名环境变量即可。

`application-mysql.yml.example` 中的 `change_me` 是占位值。正式使用请修改演示账号密码，并自行配置数据库连接和部署环境。

## 使用规则与接口

- 时间按北京时间处理。默认工作日为周一至周五 09:00–18:00；工作日预约本周未来时段，周末可预约下周；会议不能跨天。
- 草稿不占资源，提交后占用。服务端校验会议室及设备冲突；人员时间冲突产生警告。
- 仅草稿可修改。申请人可取消未开始的会议；审批受角色、部门和禁止自审规则约束。
- 接口前缀为 `/api/v1`。认证使用会话 Cookie，写请求需要 CSRF Token，BIGINT 标识通过 JSON 字符串传输。
- 使用同一主机名登录前端后，可查看 `http://127.0.0.1:8080/swagger-ui.html`；OpenAPI JSON 位于 `/api-docs`。
