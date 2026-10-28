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
mvn package
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

后端默认监听 `http://127.0.0.1:8080`，首次运行会创建 H2 文件库并初始化演示数据。Maven 不在 PATH 时，可在 IDEA 的 Maven 工具窗口执行 `package`，或使用 IDEA 自带 Maven 的完整路径运行命令，无需另装 Maven。

演示账号为 `admin`、`admin2`（两名系统管理员）、`dept-admin`、`ops-admin`（部门管理员），以及 `employee`、`employee2`、`employee3`、`employee4`、`employee5`、`ops-employee`、`ops-employee2`、`ops-employee3`（普通员工）。全新演示库的统一密码为 `Admin@123`。这些是公开演示数据，只用于本机体验；已有账号密码不会自动重置。系统不开放注册，正式账号由系统管理员创建。

已有旧 H2 文件库不应直接使用 MySQL 迁移。需要保留旧文件并独立体验时，可使用内存库，退出后数据消失：

```powershell
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo "--spring.datasource.url=jdbc:h2:mem:meeting-demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
```

## 启动前端

在 `frontend/` 目录执行：

```powershell
npm ci
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`，开发服务器会把 `/api` 请求转发到后端。

## 构建

```powershell
# backend/
mvn package

# frontend/
npm ci
npm run build
```

## Windows 本机 EXE 启动入口

先按上述步骤准备 JDK、Node.js、后端 JAR 和前端依赖。在项目根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-launcher.ps1
```

构建脚本使用 Windows 自带 .NET Framework 的 C# 编译器，不下载安装；未找到编译器会提示。生成 `会议室预约系统.exe` 后双击，即可自动启动前后端并打开独立浏览器页面。重复打开会回到已有控制窗口；点击“关闭全部”或关闭控制窗口会停止工具管理的服务。也可直接运行 `powershell -NoProfile -STA -ExecutionPolicy Bypass -File scripts/project-launcher.ps1` 打开控制窗口。

EXE 需留在项目根目录，与 `scripts/`、`backend/`、`frontend/` 一起使用，可创建快捷方式。它是本机启动入口，仍需已有运行环境；源码仓库不包含生成的二进制文件。构建脚本拒绝覆盖已有目标，重新构建可指定 `-OutputPath .\会议室预约系统新版.exe`。

无现成 MySQL 配置时使用 H2 demo；使用 MySQL 时先完成下述建表、迁移，并在启动进程环境中设置数据库连接参数。本机私有运行目录和数据库凭据不随源码发布。端口被其他启动方式占用时会提示，工具不会接管不明进程。

## 数据库升级

H2 全新库在启动时由 `backend/src/main/resources/schema.sql` 初始化。使用 MySQL 时先完成下述建库与初始化。已有 MySQL 数据库应先备份，再依次在目标数据库中执行：

- `backend/src/main/resources/migrations/V20261014__system_config.sql`
- `backend/src/main/resources/migrations/V20261026__recurring_meeting.sql`

周期迁移使用元数据检查和增量 DDL，可重复执行，不清理原有预约。新版查询依赖周期表及 `series_*` 列，已有库必须在启动新版前执行迁移；`CREATE TABLE IF NOT EXISTS` 本身不会给旧表增加列。此迁移用于 MySQL，不适用于旧 H2 文件库。

如果旧库漏执行周期迁移，登录或服务启动检查可能通过，但工作台和会议列表会因缺少 `series_id` 等字段返回服务器异常。应先备份目标库、执行上述迁移，再实际登录检查工作台、会议列表及周排期；仅检查登录页或安全令牌接口不能代表业务可用。

### 全新 MySQL 8 配置

在仓库根目录打开 MySQL 客户端：

```powershell
mysql -u root -p
```

在客户端执行以下语句，将密码占位值替换为自己设置的密码：

```sql
CREATE DATABASE meeting_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'meeting_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON meeting_scheduler.* TO 'meeting_app'@'localhost';
USE meeting_scheduler;
SOURCE backend/src/main/resources/schema.sql;
```

然后在 PowerShell 的当前终端配置连接并启动，下面的密码仅为占位值：

```powershell
$env:MEETING_DB_URL='jdbc:mysql://127.0.0.1:3306/meeting_scheduler?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:MEETING_DB_USER='meeting_app'
$env:MEETING_DB_PASSWORD='REPLACE_WITH_STRONG_PASSWORD'
$env:MEETING_DB_INIT_MODE='never'
# 仅对全新空库初始化公开演示账号与资源；已有库保持 false。
$env:MEETING_DEMO_DATA='true'
cd backend
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

首次初始化完成后停止后端，将 `MEETING_DEMO_DATA` 设为 `false` 再正常启动。演示数据初始化器只支持全新空库，已有用户或基础数据不会被自动覆盖。真实数据库凭据只配置在运行环境中，不应提交到仓库。

上述 MySQL URL 用于本机演示；远程部署应使用相应的 TLS 和账户配置。后端和前端仍独立构建、启动，Vite 开发服务器负责转发 API 请求。接口文档可在后端登录后通过 `/swagger-ui/index.html` 或 `/api-docs` 查看。

## 预约提交确认

点击“提交申请”后，系统先检查申请人和全部参会人的时间安排。存在人员冲突时，弹框列出人员及北京时间，选择“确认继续提交”后才会提交审批；选择“返回修改”、关闭窗口、点击遮罩或按 Esc 都会保留表单，本次申请不会提交。周期预约检查全部场次，无人员冲突则直接提交。

正式提交时服务端仍复查人员和资源；发现新的人员冲突会显示“人员安排已变化，请重新确认”，核对后才能继续，会议室或固定设备冲突则阻止提交。人员冲突可协调后继续提交，提交成功后的状态为“待审批”，审批通过才是“预约成功”。人员预检不会创建预约、占用资源或发送通知。本次功能无需数据库结构升级。

## 主要功能

- 三角色登录、预约、审批、取消、按会议室分列的单日排期及周历切换
- 会议室容量、固定设备和时间冲突的服务端事务校验
- 每周 2–12 次周期预约、整组冲突回滚、整组审批和单场/全部未开始场次取消
- 系统管理员全量、部门管理员本部门 CSV 报表导出
- 系统参数、站内消息、审计和启动状态恢复

周期首场遵守当前开放周，后续按周重复；推荐仅检查首场，提交时检查全部场次。周期预约暂不支持保存草稿。取消、过期场次保留原状态，剩余未来待审批场次整组处理；单场审批接口不能拆分审批周期组。取消对话框须明确选择本场或全部未开始场次并确认，关闭窗口不会取消会议。

CSV 支持全部业务状态（含草稿），按会议时间与筛选日期的重叠范围导出。部门管理员仅导出本部门，不含子部门；普通员工无导出权限。每次导出记录审计，并对公式开头文本做转义；CSV 使用 UTF-8 BOM，适合用 Excel 打开。预约时间均为北京时间，采用 `[开始,结束)` 区间。

本仓库发布运行所需源码和配置；开发测试、浏览器工具及内部验收记录不随源码发布。2026-10-27 在独立 H2 和 MySQL 8.4 测试库各通过58项后端回归；2026-10-28 前端类型检查及生产构建通过，并在独立 MySQL 测试库验证人员冲突确认、跨周周期预约、审批、取消及周排期。通过原启动工具连接既有 MySQL 演示库的登录、工作台、预约详情、审批页、周排期和提交前预检也已验证；预检失败、重复点击和确认期间冲突变化均已检查。完整交付材料和验收演示流程仍在后续阶段完善。


## 会议室排期与演示资源

“会议室与排期”默认按所选日期将每间会议室分为一列，也可切换周历。每间会议室使用固定颜色，卡片文字标签显示预约状态。全天时段随页面完整展开，使用页面右侧滚动条上下浏览；房间列较多时在排期内左右滚动，房间表头保持可见并与会议列同步，时间栏固定在左侧；可筛选单间会议室。点击开放范围内的未来空闲时段，会将日期、起止时间和会议室带入预约表单。无权查看的会议仅显示占用；待审批会议也占用资源。加载失败会提示重试，避免把未知占用显示为空闲。页面适配宽屏与手机，无新增前后端依赖。

全新空库的演示数据共12个账号、8间会议室、18条固定设备记录，包括以下新增房间：

| 会议室 | 楼层 | 容量 | 固定设备 |
| --- | --- | --- | --- |
| 云杉厅 | 2层 | 8人 | 显示器1、麦克风2 |
| 听雨厅 | 3层 | 10人 | 视频会议设备1、显示器1 |
| 映月厅 | 4层 | 16人 | 投影仪1、麦克风4 |
| 多功能厅 | 5层 | 30人 | 投影仪2、麦克风6、视频会议设备1 |

初始化器仅处理全新空库，已有库不会自动追加账号、房间或设备。已有环境可由系统管理员在“基础数据”中按需创建，保留既有预约和密码；本次界面及种子扩展不需要新的数据库结构迁移，阶段三旧库仍需先完成上文的周期迁移。

2026-10-28本版本前端类型检查及生产构建通过，独立H2后端60项测试和独立MySQL 8.4测试库58项测试通过；另完成14项排期算法检查、30项实际本机演示环境浏览器检查，以及12项独立MySQL预约、人员冲突确认、审批、取消与排期释放检查。H2新增种子测试不计入MySQL测试范围。


2026-10-28排期滚动修正通过前端类型检查、生产构建及59项浏览器检查，覆盖7种窗口尺寸、原生上下/左右滚动条拖动、全天时段到达、表头对齐、内容放大和周历切换。本轮仅调整前端展示，不涉及后端或数据库变更。

时间栏底部显示系统配置的开放结束时间，默认18:00，结束刻度完整显示在网格内。
