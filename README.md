# 企业会议室智能预约调度系统

前后端分离的会议室预约系统，支持三角色账号、会议室排期、预约与审批、资源冲突校验、会议室推荐、站内消息、会前提醒和系统参数管理。

- 后端：Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus。
- 前端：Vue 3、TypeScript、Vite、Element Plus、FullCalendar。
- 数据库：提供可独立运行的 H2 演示模式，以及 MySQL 8 配置。

## 环境准备

安装 JDK 21、Maven 3.9 和 Node.js 22（包含 npm），确保 `java`、`mvn`、`node`、`npm` 可在终端执行。下载或克隆代码后，在项目根目录操作。首次构建需要联网获取依赖；代码包不包含依赖目录和已编译产物。

## Windows 快捷启动与关闭

先按下方说明完成后端构建和前端依赖安装，然后双击项目根目录的 `启动工具.cmd`。点击“启动并打开页面”即可等待前后端就绪并打开独立项目窗口；点击“关闭全部”关闭工具管理的页面和服务。手动关闭页面后可点击“重新打开页面”，关闭工具窗口也会停止其管理的服务。

工具使用 Windows PowerShell 和现有 JDK、Node.js、Chrome/Edge，不需要额外依赖，不自动安装或构建。没有 MySQL 配置时使用 H2 demo；已有 MySQL 连接可通过当前进程的 `MEETING_DB_*` 环境变量配置，需先按下方说明建好表。工具不会自动初始化 MySQL 数据库。

工具只管理它启动且记录的进程，端口已被其他启动方式占用时显示提示，不接管或关闭其他程序。独立项目窗口使用单独的浏览器资料目录，不影响原浏览器标签页。日志与运行记录位于 `work/launcher/`，用于本机排错，不属于源码交付内容。

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

H2 用于快速演示。2026-10-13 已在 MySQL 8.4.9 上完成26项后端测试、员工提交/部门审批/员工取消的三角色页面流程、排期与消息检查，以及后端和数据库重启后的数据保留验证。2026-10-14 已实现系统参数中心，在 MySQL 独立库配置下完成43项测试（含3项旧库兼容单元测试）及三角色页面回归；参数保存后经后端重启仍保留。周期预约和CSV报表尚未实现。

准备一个名为 `meeting_scheduler` 的全新数据库及可访问该数据库的专用账号。连接信息使用环境变量配置，不需要把真实密码写入源码。

以下是在 Windows PowerShell 中启动的示例，在 `backend` 目录执行：

```powershell
$env:MEETING_DB_URL = 'jdbc:mysql://127.0.0.1:3306/meeting_scheduler?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
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

## 系统参数与已有数据库升级

系统管理员登录后，在“系统参数”页维护开放工作日、每日开放时间、推荐权重、楼层扣分、提醒提前量和草稿保留天数。默认权重为50%/30%/20%，提前15分钟提醒，草稿7天未改后清理；保存后即时生效，重启后保留。修改开放规则如影响已有有效预约，会阻止保存；其他管理员已经修改参数时，需要重新加载并核对。

全新数据库由 `src/main/resources/schema.sql` 创建配置表。已有 MySQL 数据库应先执行增量升级脚本。在项目根目录使用已有 MySQL 客户端执行下列命令，交互输入数据库密码：

```shell
mysql --host=127.0.0.1 --user=meeting_app --password --database=meeting_scheduler --execute="source backend/src/main/resources/migrations/V20261014__system_config.sql"
```

该账号需具备建表和新增初始记录权限，权限不足时由数据库管理员执行，并为应用账号授予新表读写权限。脚本只新增配置表和初始行，重复执行不会覆盖已保存参数。已有库后续启动仍设置 `MEETING_DB_INIT_MODE=never`、`MEETING_DEMO_DATA=false`，不重复初始化演示数据。

未执行升级时，原有预约流程继续使用应用初始规则，参数页显示升级提示并禁止保存；应用不会自动修改旧库表结构。参数接口为 `/api/v1/admin/settings` 的 GET/PUT，仅系统管理员可访问，写请求需要CSRF，版本号通过JSON字符串传输。

## 使用规则与接口

- 时间按北京时间处理。默认工作日为周一至周五 09:00–18:00；工作日预约本周未来时段，周末可预约下周；会议不能跨天。
- 草稿不占资源，提交后占用。服务端校验会议室及设备冲突；人员时间冲突产生警告。
- 仅草稿可修改。申请人可取消未开始的会议；审批受角色、部门和禁止自审规则约束。
- 接口前缀为 `/api/v1`。认证使用会话 Cookie，写请求需要 CSRF Token，BIGINT 标识通过 JSON 字符串传输。
- 使用同一主机名登录前端后，可查看 `http://127.0.0.1:8080/swagger-ui.html`；OpenAPI JSON 位于 `/api-docs`。
