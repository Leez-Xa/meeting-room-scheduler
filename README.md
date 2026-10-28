<div align="center">

# 会序 · 企业会议室智能预约调度系统

**让会议室安排、人员协调与审批流转有序衔接。**

基于 Java 21、Spring Boot 3.5 与 Vue 3 的前后端分离项目，覆盖会议预约、资源推荐、周期会议、审批、通知与报表，支持 MySQL 8 和 H2 本机演示。

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Vue](https://img.shields.io/badge/Vue-3-42B883?style=flat-square&logo=vuedotjs&logoColor=white) ![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white) ![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=flat-square&logo=mysql&logoColor=white)

[功能特性](#功能特性) · [快速开始](#快速开始) · [演示账号](#演示账号) · [Windows 一键启动](#windows-一键启动) · [MySQL 部署](#mysql-部署) · [常见问题](#常见问题)

</div>

## 项目介绍

会序面向企业内部会议室管理场景，将“选择时间与参会人 → 推荐会议室 → 提交申请 → 审批 → 查看排期”连接为完整流程。项目适合 Java 课程设计、本机演示和前后端协作开发参考。

- **安排看得清**：按会议室分列展示全天排期，支持周历切换；房间固定分色，预约状态单独标注。
- **申请有依据**：根据容量、设备和楼层推荐会议室，展示评分分项与推荐理由。
- **冲突分开处理**：会议室和设备冲突阻止提交，人员时间冲突先提醒申请人再次确认。
- **权限有边界**：区分系统管理员、部门管理员和普通员工，服务端校验审批范围与详情可见性。
- **体验路径明确**：使用 H2 快速演示，使用 MySQL 8 持久化运行；Windows 可自行构建 EXE 启动入口。

## 功能特性

| 模块 | 已实现能力 |
| --- | --- |
| 工作台 | 会议室概况、个人待审批与即将开始的会议、管理入口 |
| 会议室排期 | 单日会议室分列、周历、日期和房间筛选、空闲时段发起预约 |
| 预约管理 | 保存草稿、提交、查看详情、开始前取消；提交前人员冲突确认 |
| 会议室推荐 | 先过滤不可用资源，再按容量、设备与楼层评分并说明理由 |
| 审批管理 | 部门内审批、系统管理员互审，禁止自审 |
| 周期会议 | 每周重复 2–12 次、整组冲突回滚、整组审批、单场或全部未开始场次取消 |
| 消息中心 | 提交、审批、取消、过期通知，以及会前提醒 |
| 基础数据 | 部门、账号、会议室与固定设备管理；有效预约资源的维护保护 |
| 参数与审计 | 工作日、开放时段、推荐权重、提醒参数配置与操作审计 |
| 数据导出 | 按日期、会议室、状态筛选 CSV，按管理员权限限制导出范围 |

### 排期怎么浏览

默认显示所选日期的全部开放时段，初始为 **09:00–18:00**。通过页面右侧滚动条上下浏览，左右滚动查看其他会议室；房间表头保持可见，并与会议列同步移动。时间栏底部显示配置的结束时间。

点击未来空闲时段，可将会议室、日期和时间带入预约表单。无权查看的会议仅显示占用信息。切换到“周历”可以查看整周安排。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Vue Router、Pinia、FullCalendar |
| 后端 | Java 21、Spring Boot 3.5、Spring Security、MyBatis-Plus |
| 数据库 | MySQL 8；独立 H2 demo profile |
| 接口 | `/api/v1` JSON API、OpenAPI / Swagger UI |
| 构建与启动 | Maven、npm；Windows PowerShell 与 C# EXE 启动器 |

前端与后端分别构建、启动。Vite 开发服务器将 `/api` 转发到后端；登录使用服务端会话与 CSRF 校验，密码使用 BCrypt 编码。

## 快速开始

### 1. 准备环境

| 环境 | 要求 |
| --- | --- |
| JDK | 21 |
| Node.js | 20 或更高版本 |
| Maven | 3.9+，也可使用 IntelliJ IDEA 自带 Maven |
| 数据库 | 快速演示无需单独安装；MySQL 模式需要 MySQL 8 |
| 浏览器 | 现代浏览器；Windows 启动器使用已安装的 Chrome 或 Edge |

获取源码并进入项目：

```powershell
git clone https://github.com/Leez-Xa/meeting-room-scheduler.git
cd meeting-room-scheduler
```

### 2. 构建并启动后端

在项目根目录打开终端：

```powershell
cd backend
mvn package
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

后端默认监听 `http://127.0.0.1:8080`。demo 模式首次运行会创建 H2 文件库并初始化演示数据，不需要手工建库。

Maven 不在 PATH 时，可在 IDEA 的 Maven 工具窗口执行 `package`，或使用 IDEA 自带 Maven 的完整路径。仓库没有 Maven Wrapper，不需要另找 `mvnw`。

### 3. 安装前端依赖并启动

在项目根目录另开一个终端：

```powershell
cd frontend
npm ci
npm run dev
```

打开 **http://127.0.0.1:5173**，使用下方演示账号登录。手工启动的前后端可分别通过各自终端的 `Ctrl+C` 停止。

### 4. 试用完整预约流程

1. 使用 `employee` 登录，在“会议室与排期”选择未来空闲时段，或进入“发起预约”。
2. 填写主题、时间、参会人与设备需求，查看推荐结果并选择会议室。
3. 提交申请；如有人员时间冲突，核对后再次确认。提交后的状态是“待审批”。
4. 使用 `dept-admin` 登录，在“会议审批”中处理研发员工的申请。
5. 审批通过后，员工可在“我的会议”和排期中看到“预约成功”；开始前可以取消。

## 演示账号

全新演示库的账号统一密码为 **`Admin@123`**。

| 角色 | 技术研发部 | 运营管理部 |
| --- | --- | --- |
| 系统管理员 | `admin` | `admin2` |
| 部门管理员 | `dept-admin` | `ops-admin` |
| 普通员工 | `employee`、`employee2`、`employee3`、`employee4`、`employee5` | `ops-employee`、`ops-employee2`、`ops-employee3` |

初始化数据包含 **12 个账号、8 间会议室和 18 条固定设备记录**。上述账号和密码是公开的虚构演示数据；正式使用时由系统管理员创建账号，系统不开放注册。

初始化器只处理全新空库。已有库不会自动追加演示账号、修改密码或覆盖会议记录，需要的新资源可由系统管理员在“基础数据”中添加。

## 预约与审批规则

| 规则 | 行为 |
| --- | --- |
| 时间范围 | 使用北京时间，按 `[开始,结束)` 判断重叠；不允许跨天 |
| 开放时段 | 默认周一至周五 09:00–18:00，可在系统参数中配置 |
| 可预约周 | 工作日开放本周未来时段；周末同时开放下周 |
| 资源占用 | 草稿不占资源；提交后待审批会议也占用会议室与设备 |
| 资源冲突 | 会议室和固定设备冲突由服务端重新校验，冲突时阻止提交 |
| 人员冲突 | 先列出冲突人员与时段，申请人明确确认后可继续提交 |
| 内容修改 | 仅草稿可修改；提交后需要取消重约 |
| 审批范围 | 部门管理员默认审批本部门其他人的申请；系统管理员的申请由另一名系统管理员审批 |
| 取消范围 | 申请人可取消未开始会议；管理员维护资源取消须填写原因 |
| 详情权限 | 申请人、参会人及有管理权限的人员可查看详情，其他员工只能查看占用 |

正常流转为：**草稿 → 待审批 → 预约成功 → 进行中 → 已结束**，另有已取消、已拒绝、已过期状态。待审批会议到开始时仍未通过，会进入已过期。

周期预约的首场遵守开放周，后续逐周重复；任意场次发生资源冲突时整组提交回滚。周期预约暂不支持保存草稿。默认不接入法定节假日服务，工作日以系统配置为准。

## Windows 一键启动

完成后端构建和前端依赖安装后，在项目根目录构建启动入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-launcher.ps1
```

脚本使用 Windows 本机 .NET Framework C# 编译器生成 **`会议室预约系统.exe`**。双击后自动启动前后端并打开独立项目页面；重复打开会恢复已有控制窗口。

- 点击“关闭全部”或关闭控制窗口，可停止工具管理的服务。
- 手动关闭页面后，可通过“重新打开页面”恢复。
- 没有 MySQL 连接配置时使用 H2 demo；使用 MySQL 时先按下一节完成建库和连接配置。

EXE 需保留在项目根目录，与 `scripts/`、`backend/`、`frontend/` 一起使用，可创建快捷方式。源码仓库不包含生成的 EXE；启动器仍依赖已有 JDK、Node.js、后端 JAR 和前端依赖，启动时不自动安装或构建。

构建脚本不会覆盖已有 EXE。已有入口可直接使用；需要另行构建时指定新名称：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-launcher.ps1 -OutputPath .\会议室预约系统新版.exe
```

## MySQL 部署

### 新建数据库

在项目根目录打开 MySQL 客户端：

```powershell
mysql -u root -p
```

在客户端中执行以下语句，将密码占位值替换为自己设置的密码：

```sql
CREATE DATABASE meeting_scheduler CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'meeting_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON meeting_scheduler.* TO 'meeting_app'@'localhost';
USE meeting_scheduler;
SOURCE backend/src/main/resources/schema.sql;
```

在项目根目录新开 PowerShell 终端，配置当前进程的连接信息：

```powershell
$env:MEETING_DB_URL='jdbc:mysql://127.0.0.1:3306/meeting_scheduler?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:MEETING_DB_USER='meeting_app'
$env:MEETING_DB_PASSWORD='REPLACE_WITH_STRONG_PASSWORD'
$env:MEETING_DB_INIT_MODE='never'
# 仅全新空库需要公开演示数据时设为 true。
$env:MEETING_DEMO_DATA='true'
cd backend
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

首次演示数据初始化完成后，可停止后端，将 `MEETING_DEMO_DATA` 设为 `false` 再正常启动。已有库保持 `false`。上面的连接 URL 用于本机演示；数据库连接信息通过运行环境配置。

### 升级已有 MySQL 数据库

先备份目标数据库，再按顺序在目标库执行以下增量脚本：

1. [系统参数迁移](backend/src/main/resources/migrations/V20261014__system_config.sql)
2. [周期预约迁移](backend/src/main/resources/migrations/V20261026__recurring_meeting.sql)

增量脚本不清理已有预约，可重复执行。新版查询依赖周期表和预约表的 `series_*` 字段；直接执行 `CREATE TABLE IF NOT EXISTS` 不会给旧表补列。启动器不自动升级数据库。上述脚本用于 MySQL，不适用于旧 H2 文件库。

## 构建与接口文档

```powershell
# 后端：在 backend/ 目录
mvn package

# 前端：在 frontend/ 目录
npm ci
npm run build
```

后端可运行 JAR 位于 `backend/target/`，前端生产文件位于 `frontend/dist/`。生产环境需单独提供前端静态文件服务和 `/api` 反向代理；Windows 启动器使用的是本机开发服务器。

后端完成登录后，可访问 `http://127.0.0.1:8080/swagger-ui/index.html` 查看接口文档，OpenAPI JSON 位于 `http://127.0.0.1:8080/api-docs`。前端与后端保持独立，不由后端生成网页。

## 项目结构

```text
meeting-room-scheduler/
├── backend/
│   ├── pom.xml                         # Maven 构建配置
│   └── src/main/
│       ├── java/com/company/meeting/   # 业务接口、权限与调度逻辑
│       └── resources/
│           ├── application*.yml        # H2 / MySQL 运行配置
│           ├── schema.sql              # 数据库初始化结构
│           └── migrations/             # MySQL 增量迁移
├── frontend/
│   ├── src/                            # 页面、组件、状态与接口调用
│   ├── package-lock.json               # 前端依赖锁定
│   └── vite.config.ts                  # 开发服务器与 API 代理
├── scripts/
│   ├── launcher/MeetingLauncher.cs     # EXE 入口源码
│   ├── build-launcher.ps1              # EXE 构建脚本
│   └── project-launcher.ps1            # Windows 服务启动与关闭
└── README.md
```

## 常见问题

<details>
<summary>没有单独安装 Maven，能否使用 IDEA 自带的 Maven？</summary>

可以。在 IDEA 的 Maven 工具窗口执行 `package`，或使用 IDEA 自带 Maven 的完整路径运行构建命令。构建完成后，使用本机 Java 21 启动生成的 JAR。

</details>

<details>
<summary>为什么“提交成功”后还显示待审批？</summary>

提交成功表示申请已进入审批流程，此时开始占用资源；审批通过后才显示“预约成功”。人员冲突确认只表示申请人同意继续提交，不会跳过审批。

</details>

<details>
<summary>登录正常，但工作台或会议列表提示服务器异常？</summary>

已有 MySQL 库应先检查是否完成周期预约增量迁移。缺少周期表或 `series_*` 字段时，登录接口可能正常，业务查询仍会失败。先备份、执行迁移，再检查工作台、会议列表和排期。

</details>

<details>
<summary>怎样独立体验新版本，并保留旧 H2 文件库？</summary>

在 `backend/` 目录使用新的内存库启动：

```powershell
java -jar target/meeting-room-scheduler-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo "--spring.datasource.url=jdbc:h2:mem:meeting-demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
```

原文件库会保留，内存演示数据随该后端进程退出而消失。旧 H2 库不要直接使用 MySQL 迁移脚本。

</details>

<details>
<summary>EXE 能否单独复制到另一台电脑使用？</summary>

当前 EXE 是本机启动入口，需要项目目录、运行环境、已构建后端和前端依赖一起使用。它不是包含全部运行环境的安装包。

</details>

<details>
<summary>8080 或 5173 端口被占用怎么办？</summary>

先确认是否已有一套项目服务运行。启动器不会接管不明进程；若之前是手工启动，请在原终端正常停止后再使用 EXE。不要通过删除数据库或重建项目来解决端口占用。

</details>

---

项目持续迭代中。反馈问题时，请描述操作步骤、预期结果和实际结果；附日志前先移除数据库密码、令牌等凭据。
