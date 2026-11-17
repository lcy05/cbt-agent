# 更新日志

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。当前处于 `0.x`，表示原型阶段，接口与数据结构可能在Minor版本中变化。

## 0.1.0 — 2026-11-17

首个公开版本。从零构建的完整闭环：图引擎、业务持久化、前端思维链、一键启动脚本与文档。

### 新增

- **图流程**：10 个 CBT 阶段 + 统一安全门 + `resume_gate` 分流；`START → safety_triage` 与 `await_user → safety_triage` 两条固定边保证每轮先做安全判断。
- **安全约束**：安全路由激活时 CBT 节点结构性不可达；`NodeOutputGuard` 阻止模型写入用户确认/授权状态。
- **可靠性**：严格 JSON + 必填字段与枚举白名单 + 一次重试 + `parse_error` 兜底；11 份提示词资产受 SHA-256 锁定。
- **持久化**：`MysqlSaver` 自动建 checkpoint 表；`session_record` 业务表；MyBatis-Plus 映射。
- **授权闭环**：`recordApproved` 仅由用户请求写入；未授权不写库并返回 `await_user` 重新询问；一次授权只生效一次。
- **前端**：思维链轨道（阶段状态、问/答展开、分支标记）、保存决策条、离线演示模式；安全门按图结构补显为链路首节点。
- **启动脚本**：`start.cmd` 五步流水（JDK → 配置 → MySQL → schema → 运行），支持 `--no-browser` / `--skip-db` / `--help`；`config.cmd` 存放本地密钥且被 git 排除。
- **模型**：OpenAI 兼容协议，默认 DeepSeek（`deepseek-chat`）；改环境变量即可换 Kimi / GLM / Ollama。
- **测试**：21 项离线测试，`build.cmd` 一键 `clean verify`。
- **文档**：README + 6 份专题文档 + 贡献指南 + 安全政策。

### 变更

- 早期 Python / LangGraph 原型已完全移除；技术路线固定为 Spring Boot 3.5.16 + MVC + MyBatis-Plus + Spring AI Alibaba Graph 1.1.2.0。
- 模型接入从 DashScope starter 切换为 OpenAI 兼容 starter，使站点、模型、key 全部可配置。
- 提示词资产测试改为自包含，不再依赖仓库外的内部文档，保证任何人 clone 后都能直接构建。

### 已知限制

- 无认证、无速率限制，仅适合本机或可信内网。
- 没有会话列表、删除、导出或 `GET /trace` 端点。
- 前端拿不到完整执行轨迹与 State 明细。
- `emergency_support` 等逃生节点尚无独立卡片。
- 检查点数据没有自动清理策略。
