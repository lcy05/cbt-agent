# 架构

本文是文字版说明。需要图请看 [架构图表（Mermaid）](diagrams.md)：8 张图覆盖项目结构、分层、状态机、时序、数据模型、配置、授权与前端推导，全部对应源码。

## 分层

```
浏览器 (static/*)
    │  POST /api/cbt/turn
    ▼
CbtTurnController          接收 / 返回 DTO
    ▼
CbtTurnService             首轮 vs 恢复轮、写入用户授权、组装响应
    ▼
CompiledGraph              Spring AI Alibaba Graph；interruptBefore(await_user)
    ├── NodeSpec            每节点必填字段 + 枚举白名单
    ├── LlmClient           → SpringAiLlmClient → ChatModel
    ├── NodeOutputGuard     过滤模型越权写入受保护状态键
    └── MysqlSaver          checkpoint 持久化（自建 GRAPH_THREAD / GRAPH_CHECKPOINT）
    ▼
SessionRecordMapper        MyBatis-Plus，仅负责业务表 session_record
    ▼
MySQL 8
```

前端与后端不是两个服务：`static/*` 随 jar 发布，Spring Boot 在同一端口托管页面和接口，没有 npm 构建链，也没有 CORS 配置。

## 图结构

```
START ──► safety_triage ──► resume_gate ──► problem_identification
              ▲                                   │
              │                                   ▼
              │                           context_extraction
              │                                   │
              │                                   ▼
              │                           evidence_review
              │                                   │
              │                                   ▼
              │                        intervention_decision
              │                          │     │      │
              │                          ▼     ▼      ▼
              │              behavioral_experiment  practical_problem_solving
              │                          │              │
              │                          ▼              ▼
              │                       result_evaluation ◄┘
              │                                   │
              │                                   ▼
              │                            session_summary
              │                                   │
              │                                   ▼
              │                             persist_record
              │                                   │
await_user ◄──┴───────────────────────────────────┘
```

两条固定边决定了安全门的执行时机：

```java
g.addEdge(StateGraph.START, Nodes.SAFETY_TRIAGE);       // 首轮
g.addEdge(Nodes.AWAIT_USER, Nodes.SAFETY_TRIAGE);       // 每次回答后
```

所以**每一轮都以安全判断开始**，无论用户之前停在哪一步。恢复时 `resume_gate` 根据 `resume_target` 把流程送回中断前的节点。

`interruptBefore("await_user")` 保证中断点零副作用：用户回答之前不会写库、不会推进阶段。

### 逃生路径

安全路由为 `clarify_safety` / `pause_and_support` / `emergency_support` / `professional_support` 时，图直接结束或回到 `await_user`，不会进入任何 CBT 节点：

```java
public static final Set<String> CBT_NODES = Set.of(
    PROBLEM_IDENTIFICATION, CONTEXT_EXTRACTION, EVIDENCE_REVIEW,
    INTERVENTION_DECISION, BEHAVIORAL_EXPERIMENT, PRACTICAL_PROBLEM_SOLVING,
    RESULT_EVALUATION, SESSION_SUMMARY, PERSIST_RECORD);
```

这不是prompt层的"请遵守"，而是结构性不可达——提示词要求模型输出枚举值，代码再把枚举映射到目标节点。

## 模型输出的约束链

每一份节点提示词都要求严格 JSON。输出经过：

1. JSON 解析（失败重试 1 次）
2. 必填字段校验
3. 枚举白名单校验（`route`、`problem_type`、`intervention` 等）
4. `NodeOutputGuard` 剥离受保护状态键，再合并进 State
5. 仍失败 → 路由到 `parse_error`，由纯文本节点请用户重述

受保护的 key 包括用户确认/授权相关字段。模型**不能**把自己包装成"用户已同意"，也就无法触发保存。

## 保存授权闭环

```
用户请求 recordApproved=true
   └─► CbtTurnService 写入 State（唯一写入口）
          └─► persist_record 读取
                 ├─ true  → insert session_record（record_approved=1）
                 └─ false → 不写库，flow=await_approval，回到 await_user 重问
```

要点：

- 缺省值 `false`；一次授权只对当前轮有效，节点执行后无论是否保存都重置为 `false`。
- 未授权不是直接 `END`，而是回到 `await_user`——否则那条记录永久失去保存机会。
- LLM 输出永远到不了这个 key。

## 状态与检查点

| 机制 | 实现 |
|---|---|
| 会话标识 | `threadId`（首轮由服务端生成 UUID） |
| 中断 | `CompileConfig.builder().interruptBefore("await_user")` |
| 恢复 | `graph.updateState(config, update, null)` → `graph.invoke(Map.of(), resumed)` |
| 检查点 | `MysqlSaver`，`CREATE_IF_NOT_EXISTS`，表 `GRAPH_THREAD` / `GRAPH_CHECKPOINT` 自动建 |
| 序列化 | `SpringAIStateSerializer` |

`invoke(null, config)` 在 1.1.2.0 有重载歧义，恢复必须写 `invoke(Map.of(), resumed)`。

## 前端的思维链视图

左栏 10 张阶段卡片对应 `Nodes` 中的 CBT 阶段，按响应序列推导状态：已完成 / 进行中 / 等你回答 / 未进入 / 本次未走。`intervention_decision` 下方挂 6 条去向，实际走过的标 `→`。

由于接口只返回「最后停下的节点」，前端按图的两条固定边推断安全门必然已过，把它补进链路首位——这是结构性推断，不是伪造轨迹。完整轨迹和 State 明细仍拿不到，需要未实现的 `GET /trace`。

## 版本锁定

| 组件 | 版本 |
|---|---|
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 1.1.2.0 |
| MyBatis-Plus | 3.5.18 |
| Java | 17 |

这些版本相互兼容是经过验证的：Spring AI 1.1.x 对应 Boot 3.5.x；传统 XML SSM（JDK 8/11 + Spring 5）与 Spring AI Alibaba 不兼容。

## 测试策略

```
build.cmd  →  mvnw.cmd clean verify
    ├── CbtGraphAcceptanceTest  16 项：V1–V12 流程约束 + A1–A4 授权闭环
    └── PromptAssetTest          5 项：SHA-256 锁定、LF 编码、硬约束文本
```

全部离线：StubLlm + `MemorySaver` + Mockito，不启 Spring 上下文、不连 MySQL、不联网。提示词资产测试完全自包含，不依赖任何仓库外文档。

详见 [贡献指南](../CONTRIBUTING.md)。
