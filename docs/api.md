# API 参考

## 唯一端点

```http
POST /api/cbt/turn
Content-Type: application/json
```

页面本身由 `GET /` 提供（Spring Boot 静态资源映射）。除此之外没有公开端点——没有会话列表、导出、删除或 trace。

## 请求

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `threadId` | string | 否 | 首轮留空或省略，服务端生成 UUID 并在响应中返回；后续轮次原样传回 |
| `answer` | string | 否 | 用户本轮回答。首轮可传初始困扰 |
| `recordApproved` | boolean | 否 | 默认 `false`。**只有设为 `true` 才允许写入长期记录** |

```json
{
  "threadId": "9f1c2e6a-...",
  "answer": "组长后来还说整体讲得挺清楚",
  "recordApproved": false
}
```

缺省的 `recordApproved` 会被显式写成 `false`，因此一次授权绝不会被后续轮次复用。

## 响应

| 字段 | 类型 | 说明 |
|---|---|---|
| `threadId` | string | 会话标识，后续轮次必须传回 |
| `node` | string | **最后停下的节点** id，不是完整轨迹 |
| `status` | string | `waiting_user` / `completed` / `error` |
| `question` | string | 这一轮向你提出的问题；为空表示流程结束 |
| `userResponse` | string | 节点给出的文本回复 |

```json
{
  "threadId": "9f1c2e6a-...",
  "node": "problem_identification",
  "status": "waiting_user",
  "question": "那我们把它说得具体一点：最近一次让你觉得「自己不行」，是什么时候、在做什么事？",
  "userResponse": ""
}
```

`status` 的判定：

| 值 | 条件 |
|---|---|
| `waiting_user` | `question` 非空，等待用户回答 |
| `completed` | `question` 为空，流程走完 |
| `error` | 服务端异常，`userResponse` 是兜底提示 |

## 常见 node 值

| node | 含义 |
|---|---|
| `safety_triage` | 安全判断；可能带着澄清问题回到用户 |
| `problem_identification` | 把困扰收敛成具体问题 |
| `context_extraction` | 还原情境、自动想法、情绪与身体反应 |
| `evidence_review` | 找支持/反对证据与其他解释 |
| `intervention_decision` | 选择干预路径 |
| `behavioral_experiment` / `practical_problem_solving` | 两条互斥的执行路径 |
| `result_evaluation` | 对照预测与实际结果 |
| `session_summary` | 整理结论与遗留问题 |
| `persist_record` | 询问或执行保存 |
| `parse_error` | 模型输出不合契约，请用户重述 |

安全门直接拦停时也可能返回 `emergency_support`、`pause_and_support` 或 `professional_support`。

## 调用示例

**首轮**（不传 `threadId`）：

```bash
curl -X POST http://localhost:8080/api/cbt/turn \
  -H "Content-Type: application/json" \
  -d '{"answer":"最近总是觉得自己做什么都不行"}'
```

**恢复轮**（传回 `threadId`）：

```bash
curl -X POST http://localhost:8080/api/cbt/turn \
  -H "Content-Type: application/json" \
  -d '{"threadId":"上一步返回的threadId","answer":"上周小组汇报，我把一个数据讲错了"}'
```

**授权保存**：

```bash
curl -X POST http://localhost:8080/api/cbt/turn \
  -H "Content-Type: application/json" \
  -d '{"threadId":"...","answer":"确认保存","recordApproved":true}'
```

## 语义约束

- **单问题原则**：提示词要求每轮原则上只提一个问题，前端按此设计交互。
- **threadId 是唯一凭据**：丢失后无法续接会话；服务端没有会话列表接口。
- **响应只有终点，没有轨迹**：`node` 是最后执行的节点。安全门因为图结构必然经过，前端按固定边补显；其余中间节点是否跳过，接口无法表达。
- **无认证**：端点对本机开放即可用。不要直接暴露到公网。

## 尚未实现

| 需求 | 状态 |
|---|---|
| `GET /api/cbt/threads/{id}/trace` — State 快照 | 未实现 |
| 会话列表 / 删除 / 数据导出 | 未实现 |
| 流式输出（SSE） | 未实现 |
| 认证与速率限制 | 未实现 |

详见 [架构](architecture.md) 与 [数据与隐私](data-and-privacy.md)。
