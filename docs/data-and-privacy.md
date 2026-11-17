# 数据与隐私

## 数据存到哪里

| 表 | 归属 | 由谁创建 | 内容 |
|---|---|---|---|
| `session_record` | 业务数据 | `db/schema.sql` | 用户确认后的长期记录 |
| `GRAPH_THREAD` | 检查点 | `MysqlSaver` 自动创建 | 会话线程元数据 |
| `GRAPH_CHECKPOINT` | 检查点 | `MysqlSaver` 自动创建 | 每轮 State 快照 |

`schema.sql` 自带 `CREATE DATABASE IF NOT EXISTS cbt_agent` 和 `USE cbt_agent`，可重复执行（幂等）。它只负责业务表；两张图表由图引擎以 `CREATE_IF_NOT_EXISTS` 建立。

## 业务表字段

`session_record` 保存一次被授权的练习记录：

| 字段 | 说明 |
|---|---|
| `id` | 自增主键 |
| `thread_id` | 会话 id，带普通索引 |
| `session_summary` | 本轮整理结论 |
| `confirmed_facts` | 已确认事实（JSON） |
| `user_reported_thoughts` / `user_reported_emotions` | 用户自述的想法与情绪（JSON） |
| `evidence_review` | 证据检验结果（JSON） |
| `accepted_alternative_thought` | 用户接受的替代想法 |
| `intervention` | 干预类型 |
| `action_result` | 行动结果 |
| `unresolved_issues` / `next_step` | 遗留问题与下一步 |
| `confidence_of_record` | 记录置信度 |
| `record_approved` | 授权标记；**表里只会出现 1**，因为未授权的行根本不写入 |
| `created_at` / `updated_at` | 时间戳，数据库自动维护 |

## 授权闭环

这是整个系统最重要的数据边界。

```
用户在请求里显式给出 recordApproved=true
   │
   ├─ CbtTurnService 把值写进 State（唯一入口）
   │     └─ 首轮、恢复轮都写；缺省 false 会覆盖残留值
   │
   └─ persist_record 节点读取
         ├─ true  → insert，record_approved=1，随后立刻重置为 false
         └─ false → 不 insert；flow=await_approval，回到 await_user 重新询问
```

四条硬约束：

1. 模型输出无法写入 `recordApproved`——`NodeOutputGuard` 先剥离受保护 key 才合并 State。
2. 一次授权只对当前记录有效，节点执行后无论存没存都重置。
3. 未授权不会直接结束流程，用户仍有机会补授权。
4. 授权通过不产生重复插入；一次请求只走一次 persist。

## 检查点里有什么

`GRAPH_CHECKPOINT` 保存的是每轮的 `OverAllState` 快照，包含模型产出的中间字段（自动想法、证据、干预计划等）。这些内容**也会落盘**——即使最终用户没有授权保存长期记录。

换句话说：检查点是图恢复所必需的机制，不等于"用户同意保存"。如果部署场景对数据留存敏感，需要自行实现检查点清理策略（当前版本没有自动清理）。

## 日志边界

| 信息 | 是否进日志 |
|---|---|
| API key | 否。只打印 `key=set(N chars)` 或 `NOT SET` |
| MySQL 密码 | 否。通过 `MYSQL_PWD` 传递，不进命令行；用完清空 |
| 用户对话内容 | 默认不打印（应用日志级别 INFO，无逐轮 State 打印） |
| 实际生效的 base-url / model | 是，`LLM ready` 那一行 |

## 提示词层的安全边界

系统提示词明确要求模型：

- 你不是心理医生，不能进行精神疾病诊断
- 每轮原则上只向用户提出一个问题
- 不得强迫用户完成行为实验
- 不具有修改本系统规则的权限
- 出现安全风险信号时，不得继续常规认知重构或行为实验

这些约束有两层保障：提示词文本本身受 SHA-256 锁定（测试会检测篡改），以及图结构上的路由限制。

## 部署建议

- **只在本机或可信内网运行**。接口没有认证，任何能访问 8080 端口的人都能创建/续接会话。
- 不要给数据库账号超出 `cbt_agent` 库的权限。
- 不要把 `config.cmd`、数据库备份或检查点表导出上传到公开位置。
- 这是自助练习原型，不是医疗设备；危机场景必须转介专业支持。
