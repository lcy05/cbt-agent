# cbt-agent

[![CI](https://github.com/lcy05/cbt-agent/actions/workflows/ci.yml/badge.svg)](https://github.com/lcy05/cbt-agent/actions/workflows/ci.yml) [![Release](https://img.shields.io/github/v/release/lcy05/cbt-agent)](https://github.com/lcy05/cbt-agent/releases) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE) [![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)

一个基于 **Spring AI Alibaba Graph** 的「最小介入」认知行为自助练习闭环。后端用有向图驱动 10 个阶段，把所有模型输出约束在严格 JSON 与枚举白名单内；前端把走过的每一步画成可展开的思维链轨道。

> 当前版本：**0.1.0**。面向本地运行与开发，未提供用户认证、多租户或云端部署。

---

## 它解决什么

通用聊天机器人做 CBT 练习有三个老问题：会跳步骤、会越过安全边界、会擅自替用户下结论。这个项目把流程本身编码成图：

- **安全门是硬入口**：首轮和每次用户回答后，都必须先经过 `safety_triage`，才允许进入任何 CBT 节点。
- **模型只提建议**：LLM 输出严格 JSON，经枚举白名单校验后才由代码决定路由；`parse_error` 是每条条件边的兜底。
- **保存必须显式授权**：`recordApproved` 只能由用户请求写入，模型永远改不了这个状态；没有授权就不写库。
- **可回看**：前端按轮次展示走过的节点、每个节点问过什么、你答了什么。

## 特性

| 面向 | 能力 |
|---|---|
| 流程 | 10 阶段图 + 统一安全门 + 固定边保证每轮重判安全 |
| 安全 | 安全路由激活时，所有 CBT 节点结构性不可达 |
| 可靠 | 严格 JSON + 枚举白名单 + 1 次重试 + `parse_error` 兜底 |
| 持久化 | `MysqlSaver` 自动管理 checkpoint；业务记录独立表 |
| 前端 | 思维链轨道、分支标记、保存决策条、离线演示模式 |
| 模型 | OpenAI 兼容协议，默认 DeepSeek，改配置即可换厂家 |
| 构建 | Maven Wrapper + JDK 17，21 项离线测试 |

## 快速开始

**Windows（推荐）**：

```bat
copy config.example.cmd config.cmd
rem 编辑 config.cmd，填 DEEPSEEK_API_KEY 与 CBT_DB_PASSWORD
start.cmd
```

浏览器会自动打开 `http://localhost:8080/`。详见 [快速开始](docs/getting-started.md)。

**其他平台**：准备 JDK 17 + MySQL 8 + Maven，手工灌入 `src/main/resources/db/schema.sql`，然后设置环境变量并运行 `mvn spring-boot:run`。

## 配置速览

| 变量 | 默认值 | 作用 |
|---|---|---|
| `AI_BASE_URL` | `https://api.deepseek.com` | 模型站点，只填域名 |
| `AI_MODEL` | `deepseek-chat` | 模型名 |
| `DEEPSEEK_API_KEY` | 空 | API key；也接受 `AI_API_KEY` |
| `CBT_DB_USERNAME` | `root` | 数据库用户 |
| `CBT_DB_PASSWORD` | 空 | 数据库密码 |
| `CBT_DB_URL` | `127.0.0.1:3306/cbt_agent` | JDBC URL |

完整清单与换厂家示例见 [配置参考](docs/configuration.md)。

## 接口

只有一个端点：

```http
POST /api/cbt/turn
```

```json
// 请求
{ "threadId": "可选，首轮留空", "answer": "最近总是觉得自己做什么都不行", "recordApproved": false }

// 响应
{ "threadId": "...", "node": "problem_identification", "status": "waiting_user",
  "question": "最近一次让你觉得「自己不行」是什么时候？", "userResponse": "" }
```

`threadId` 是续接会话的唯一凭据。详见 [API 参考](docs/api.md)。

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 运行时 | Eclipse Temurin / Corretto JDK | 17 |
| 框架 | Spring Boot（Web MVC） | 3.5.16 |
| 图引擎 | Spring AI Alibaba Graph | 1.1.2.0 |
| 模型 | Spring AI OpenAI starter（指向 DeepSeek） | 1.1.2 |
| 持久层 | MyBatis-Plus | 3.5.18 |
| 数据库 | MySQL | 8.x |
| 前端 | 原生 HTML / CSS / JS，随 jar 发布 | 无构建链 |

没有 npm、Vite、独立前端服务或跨域配置——Spring Boot 在同一个端口同时托管页面与接口。

## 目录结构

```
.
├── config.example.cmd          # 公开配置模板（复制为 config.cmd 后使用）
├── start.cmd                   # Windows 一键启动：JDK → 配置 → MySQL → schema → 运行
├── build.cmd                   # clean verify，跑全部离线测试
├── mvnw.cmd                    # Maven Wrapper（固定 JDK 17）
├── docs/                       # 公开文档（你正在读的）
├── src/main/java/com/luoch/cbt/
│   ├── config/                 # 图与 LLM Bean
│   ├── controller/             # 唯一 REST 端点
│   ├── dto/                    # 请求 / 响应契约
│   ├── entity/ / mapper/       # 业务记录持久化
│   ├── graph/                  # 节点、路由、状态键、输出守卫
│   └── service/                # 每轮编排
└── src/main/resources/
    ├── prompts/                # 11 份提示词资产（受哈希锁定）
    ├── db/schema.sql           # 业务表；checkpoint 表由图引擎自建
    └── static/                 # 前端三文件
```

## 架构图表

项目结构、分层架构、完整图状态机、对话时序、数据模型、配置优先级、授权状态机与前端推导，共 8 张 Mermaid 图，见 [架构图表（Mermaid）](docs/diagrams.md)。GitHub / GitLab 直接渲染，无需构建。

## 测试

```bat
build.cmd
```

21 项测试全部离线运行：16 项图行为验收（V1–V12 + A1–A4）、5 项提示词资产校验。不需要 key、不需要 MySQL、不联网。

## 已知边界

- 接口无认证、无速率限制，**只适合本机或可信内网**。
- 没有会话列表、导出或删除接口；数据保留策略需自行实现。
- 前端只能看到每轮「最后停在哪个节点」，拿不到完整轨迹与 State 明细；`GET /trace` 尚未实现。
- `emergency_support` 等逃生节点当前没有独立卡片；安全门直接拦停时左栏不会高亮。
- Windows 脚本（`start.cmd` / `mvnw.cmd`）是一等公民，其他平台需手工执行等价步骤。

详见 [故障排查](docs/troubleshooting.md)。

## 许可

[MIT](LICENSE) © 2026 luoch

## 免责声明

本项目是**自助练习工具原型，不是医疗器械，也不提供心理诊断或治疗**。模型提示词明确要求不诊断精神疾病、不做强迫性行为实验，并在识别到安全风险时中止常规流程。如果你或他人正处于危机中，请立即联系当地紧急服务或具备资质的专业人员——本软件不能替代他们。
