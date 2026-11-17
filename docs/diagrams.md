# 架构图表（Mermaid）

本文件的每张图都直接对应当前源码，不画不存在的能力。节点名取自 `Nodes.java`，路由值取自 `Routes.java`，字段取自 `schema.sql`，配置键取自 `application.yml` / `config.example.cmd`。改代码时按文末「维护对照表」同步。

> 渲染：GitHub、GitLab、VS Code（Markdown Preview Mermaid Support）原生支持；无需构建步骤。

---

## 1. 项目结构

```mermaid
mindmap
  root((cbt-agent))
    src/main/java/com/luoch/cbt
      CbtApplication
      config
        GraphConfig
        SpringAiLlmClient
      controller
        CbtTurnController
      dto
        TurnRequest
        TurnResponse
      entity
        SessionRecord
      mapper
        SessionRecordMapper
      service
        CbtTurnService
      graph
        CbtGraphFactory
        NodeSpec
        Nodes
        Routes
        StateKeys
        KeyStrategies
        NodeOutputGuard
        LlmNodeSupport
        LlmClient
    src/main/resources
      static
        index.html
        app.css
        app.js
      prompts
        system + 11 份节点提示词
      db/schema.sql
      application.yml
    src/test/java/com/luoch/cbt
      CbtGraphAcceptanceTest
      PromptAssetTest
    脚本与文档
      start.cmd
      build.cmd
      config.example.cmd
      README.md
      docs/
```

要点：

- 后端是标准的 Controller → Service → Graph → Mapper 分层；`graph/` 包是系统的核心，其余包都是它的输入/输出外壳。
- 前端只有 3 个静态文件，随 jar 一起发布，**不是独立前端工程**，没有 npm/CORS。
- `prompts/` 下 11 份提示词由 SHA-256 锁定，测试保证没被改写。

---

## 2. 系统分层架构

```mermaid
flowchart TD
    subgraph browser["浏览器"]
        UI["index.html + app.js<br/>对话栏 / 10 阶段思维链轨道"]
    end

    subgraph app["Spring Boot 3.5.16 · 单端口 8080"]
        CTRL["CbtTurnController<br/>POST /api/cbt/turn"]
        SVC["CbtTurnService<br/>首轮 vs 恢复轮<br/>写入用户授权"]
        GRAPH["CompiledGraph<br/>interruptBefore(await_user)"]

        subgraph graphcore["graph/ 核心"]
            NODE["NodeSpec<br/>必填字段 + 枚举白名单"]
            GUARD["NodeOutputGuard<br/>剥离受保护状态键"]
            ROUTE["Routes<br/>条件边全映射"]
            STATE["StateKeys / KeyStrategies<br/>状态契约与合并策略"]
        end

        LLM["SpringAiLlmClient<br/>OpenAI 兼容协议"]
        MAPPER["SessionRecordMapper<br/>MyBatis-Plus"]
        SAVER["MysqlSaver<br/>checkpoint 持久化"]
    end

    subgraph external["外部"]
        API["DeepSeek / 其他兼容模型<br/>默认 deepseek-chat"]
        DB[("MySQL 8<br/>session_record<br/>GRAPH_THREAD / GRAPH_CHECKPOINT")]
    end

    UI -->|JSON 请求| CTRL
    CTRL --> SVC
    SVC --> GRAPH
    GRAPH --- NODE
    GRAPH --- GUARD
    GRAPH --- ROUTE
    GRAPH --- STATE
    GRAPH --> LLM
    LLM -->|HTTPS| API
    GRAPH --> SAVER
    GRAPH --> MAPPER
    SAVER --> DB
    MAPPER --> DB
    SVC -->|TurnResponse| UI
```

读图要点：

| 组件 | 职责边界 |
|---|---|
| `CbtTurnService` | 唯一写入 `record_approved` 的地方；决定 `invoke` 还是 `updateState + invoke` |
| `NodeOutputGuard` | 模型输出里的受保护键一律剥离，模型无法把自己包装成「用户已同意」 |
| `MysqlSaver` | 只管 checkpoint 两张表；业务表 `session_record` 由 Mapper 独占 |
| `SpringAiLlmClient` | 只依赖 OpenAI 兼容协议，换厂家是三个环境变量的事 |

---

## 3. 图状态机（核心逻辑）

```mermaid
flowchart TD
    START(["START"]) --> ST["safety_triage<br/>统一安全门"]

    ST -->|continue| RG["resume_gate<br/>零副作用"]
    ST -->|clarify_safety| AU["await_user<br/>中断点"]
    ST -->|pause_and_support| PAUSE["pause_and_support<br/>静态文本"]
    ST -->|emergency_support| EMERG["emergency_support<br/>LLM 自由文本"]
    ST -->|parse_error| PE["parse_error<br/>静态文本"]

    PAUSE --> END1(["END"])
    EMERG --> END2(["END"])

    RG -->|resume_target| PI["problem_identification"]
    RG -->|其余 resume_target| OTHER["context / evidence / intervention<br/>behavioral / practical / result<br/>summary / persist"]
    RG -->|END| END3(["END"])

    PI -->|next| CE["context_extraction"]
    CE -->|next| ER["evidence_review"]
    ER -->|next| ID["intervention_decision"]

    ID -->|cognitive_review| ER
    ID -->|behavioral_experiment<br/>small_behavioral_step| BE["behavioral_experiment · 6A"]
    ID -->|practical_problem_solving| PP["practical_problem_solving · 6B"]
    ID -->|observation_only| SS["session_summary"]
    ID -->|professional_support| PRO["professional_support"]
    ID -->|parse_error| PE

    BE -->|always await| AU
    PP -->|always await| AU
    BE -.->|"next → 等待行动结果"| RE["result_evaluation"]
    PP -.->|"next → 等待行动结果"| RE

    AU -->|下一轮回答| ST
    RE -->|next / 六种 next_step| SS
    SS -->|next| PR["persist_record<br/>无 LLM"]
    SS -->|await| AU

    PR -->|saved · 已授权| END4(["END"])
    PR -->|await_approval · 未授权| AU
    PE --> AU
    PRO --> END5(["END"])

    classDef safety fill:#ffe6e6,stroke:#c00,color:#000
    classDef control fill:#e8f0fe,stroke:#36c,color:#000
    classDef terminal fill:#eee,stroke:#888,color:#000
    class ST,PAUSE,EMERG,PRO safety
    class RG,AU control
    class END1,END2,END3,END4,END5 terminal
```

关键结构事实：

1. **两条固定边决定安全门的执行时机** — `START → safety_triage` 和 `await_user → safety_triage`。任何一轮都以安全判断开始，无论上一轮停在哪。
2. **`resume_gate` 是恢复分发器** — 10 个 `resume_target` 值（9 个节点 + `END`）全部有映射，穷举由测试 V9 保证。
3. **6A / 6B 永远等待** — `collectAlwaysAwait()` 让 `next` 也指向 `await_user`，因为「计划批准」和「行动结果」都必须由用户带回。
4. **逃生路径结构性不可达** — `pause_and_support` / `emergency_support` / `professional_support` 直接到 `END`，不经过任何 CBT 节点。
5. **未授权保存不是 `END`** — `persist_record` 未授权时回到 `await_user`，让那条待保存记录下一轮仍有机会被确认。

---

## 4. 一轮对话的时序

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant B as 浏览器 app.js
    participant C as CbtTurnController
    participant S as CbtTurnService
    participant G as CompiledGraph
    participant L as LLM (DeepSeek)
    participant D as MySQL

    rect rgb(232, 240, 254)
    note over U,D: 首轮：threadId 为空
    U->>B: 输入第一句话
    B->>C: POST /api/cbt/turn {threadId:null, answer,...}
    C->>S: turn(request)
    S->>S: 生成 UUID 作为 threadId
    S->>S: 写入 user_answer / resume_target / record_approved
    S->>G: invoke(initial, config)
    G->>L: safety_triage（严格 JSON）
    L-->>G: safety_status / route / question
    G->>L: problem_identification（严格 JSON）
    L-->>G: ready_for_next / question
    G->>G: 在 await_user 前中断（零副作用）
    G->>D: MysqlSaver 写 checkpoint
    G-->>S: OverAllState
    S-->>C: TurnResponse(threadId, node, status, question, userResponse)
    C-->>B: JSON
    B->>B: applyResponse → 补安全门 → 渲染思维链
    B-->>U: 显示问题
    end

    rect rgb(232, 255, 232)
    note over U,D: 恢复轮：带 threadId
    U->>B: 回答 + 可选「确认保存」
    B->>C: POST {threadId, answer, recordApproved}
    C->>S: turn(request)
    S->>G: updateState(config, update, null)
    S->>G: invoke(Map.of(), resumed)
    G->>D: 读取 checkpoint
    G->>L: 再次 safety_triage（每轮必经）
    G->>G: resume_gate 按 resume_target 分发
    G->>L: 继续 CBT 节点…
    alt 已授权且进入 persist_record
        G->>D: insert session_record (record_approved=1)
    else 未授权
        G->>G: flow=await_approval
        G->>D: 仅写 checkpoint，不写业务表
    end
    G-->>S: OverAllState
    S-->>C: TurnResponse
    C-->>B: JSON
    B-->>U: 下一问或保存提示
    end
```

时序要点：

- `invoke(null, config)` 在 Spring AI Alibaba Graph 1.1.2.0 有重载歧义，恢复必须用 `invoke(Map.of(), resumed)`。
- 授权是**一次性**的：`persist_record` 执行后无论保存与否都把 `record_approved` 重置为 `false`，不会串到下一轮。
- 中断发生在 `await_user` **之前**，所以用户没回答时不会写业务表、不会推进阶段。

---

## 5. 数据模型

```mermaid
erDiagram
    SESSION_RECORD {
        BIGINT id PK "自增主键"
        VARCHAR thread_id "会话标识，索引 idx_session_thread"
        TEXT session_summary "会话总结"
        JSON confirmed_facts "确认的事实"
        JSON user_reported_thoughts "用户自述想法"
        JSON user_reported_emotions "用户自述情绪"
        JSON evidence_review "证据检验结果"
        TEXT accepted_alternative_thought "接受的替代想法"
        VARCHAR intervention "干预类型"
        TEXT action_result "行动结果"
        JSON unresolved_issues "未解决问题"
        TEXT next_step "下一步"
        VARCHAR confidence_of_record "记录完整度"
        TINYINT record_approved "授权标记，实际行恒为 1"
        DATETIME created_at "创建时间"
        DATETIME updated_at "更新时间"
    }

    GRAPH_THREAD {
        VARCHAR thread_id "由 MysqlSaver 管理"
    }

    GRAPH_CHECKPOINT {
        VARCHAR thread_id "由 MysqlSaver 管理"
    }

    SESSION_RECORD ||--o{ GRAPH_CHECKPOINT : "thread_id 逻辑关联 · 无外键"
    GRAPH_THREAD ||--o{ GRAPH_CHECKPOINT : "同 thread 的多轮快照"
```

数据边界：

| 表 | 归属 | 写入条件 |
|---|---|---|
| `session_record` | `SessionRecordMapper` | 仅当 `record_approved=true` |
| `GRAPH_THREAD` / `GRAPH_CHECKPOINT` | `MysqlSaver` | 每轮中断自动写，`CREATE_IF_NOT_EXISTS` |

`GRAPH_THREAD` 没有时间列；`MysqlSaver.release()` 只置 `is_released=TRUE`，**不删行**。因此 checkpoint 的自动清理是尚未实现的能力，不要假设它会自己过期。

---

## 6. 配置来源与优先级

```mermaid
flowchart LR
    subgraph sources["配置来源 · 后者覆盖前者"]
        YML["application.yml<br/>内置默认值"]
        ENV["环境变量<br/>AI_* / CBT_DB_*"]
        CFG["config.cmd<br/>已 git-ignore"]
    end

    YML --> ENV --> CFG

    CFG --> SPRING["Spring 绑定<br/>spring.ai.openai.*<br/>spring.datasource.*"]
    CFG --> START["start.cmd<br/>JDK 探测 / mysql.exe 探测<br/>灌 schema / 端口检查"]

    SPRING --> LLMCLIENT["LLM 客户端<br/>base-url + model + key"]
    SPRING --> DS["DataSource"]

    LLMCLIENT --> LOG["启动自述日志<br/>LLM ready base-url=… model=… key=set(N chars)"]
    DS --> MYSQL["MySQL cbt_agent"]

    CLI["命令行参数<br/>--no-browser / --skip-db / --help"] --> START
```

默认值与覆盖关系：

| 变量 | 默认 | 说明 |
|---|---|---|
| `AI_BASE_URL` | `https://api.deepseek.com` | 只填域名，客户端自动补 `/v1/chat/completions` |
| `AI_MODEL` | `deepseek-chat` | 可换 `deepseek-reasoner` 或任意兼容模型 |
| `DEEPSEEK_API_KEY` | `sk-not-configured` | 占位值让应用无 key 也能启动；此时问答 401 |
| `CBT_DB_PASSWORD` | 空 | 空则 `start.cmd` 交互式提示，不落盘 |

`config.cmd` 优先于环境变量，因为它执行的是 `set`，后赋值覆盖前者。密钥相关文件全部被 `.gitignore` 排除，仓库里只有 `config.example.cmd` 模板。

---

## 7. 保存授权状态机

```mermaid
stateDiagram-v2
    direction LR
    [*] --> 未授权 : TurnRequest.recordApproved 缺省 false
    未授权 --> 已授权 : 用户回复含确认保存<br/>Service 写入 State
    已授权 --> 待落库 : persist_record 读取到 true
    未授权 --> 重问 : persist_record 读取到 false
    重问 --> 未授权 : flow=await_approval<br/>回到 await_user
    待落库 --> 已复位 : insert session_record<br/>record_approved=1
    待落库 --> 已复位 : 节点执行即重置 State 为 false
    已复位 --> 未授权 : 下一轮缺省 false
    已复位 --> [*] : END
```

不可绕过的约束：

1. `record_approved` 在 `StateKeys.PROTECTED` 中，模型输出经 `NodeOutputGuard` 时会被剥离。
2. 唯一写入口是 `CbtTurnService`，且**每一轮都写**（缺席的授权显式重置为 `false`）。
3. `persist_record` 是无 LLM 节点，直接读 State 决定是否 `insert`。
4. 一次授权只对当前轮有效，节点执行后无条件重置。

---

## 8. 前端思维链推导

```mermaid
flowchart LR
    RESP["TurnResponse<br/>只有一个 node 字段"] --> APPLY["applyResponse()"]
    APPLY --> PUSH["visited.push(node)"]
    PUSH --> GUARD{"visited 含<br/>safety_triage?"}
    GUARD -->|否| INS["补 safety_triage 到首位"]
    GUARD -->|是| SKIP["跳过，避免重复"]
    INS --> RENDER
    SKIP --> RENDER
    RENDER["renderChain()<br/>10 张阶段卡片"] --> STATES["已完成 / 进行中 / 等你回答<br/>未进入 / 本次未走"]
    RENDER --> BRANCH["intervention_decision 下挂 6 条去向<br/>实际走过的标 →"]
    RENDER --> META["renderThread()<br/>AI 消息前置 安全评估 ✓"]
```

推导依据与边界：

- `START → safety_triage` 与 `await_user → safety_triage` 是固定边，所以「本轮落在任意 CBT 节点」⇒「安全门必然已过」。前端补首位是**结构性推断，不是伪造轨迹**。
- 接口契约只有 5 个字段（见 `TurnResponse`），没有轨迹数组，所以**中间节点是否被跳过，前端无法知晓**。
- `emergency_support` / `pause_and_support` / `professional_support` / `parse_error` 四个终止节点不在 10 张卡片中；安全门真拦停时左栏不会高亮。完整轨迹需要尚未实现的 `GET /api/cbt/threads/{id}/trace`。

---

## 维护对照表

| 代码变更 | 需同步的图 |
|---|---|
| `Nodes.java` 增删节点 | 图 1、图 3、图 8 |
| `Routes.java` 改路由值 | 图 3、图 4 |
| `schema.sql` 改字段 | 图 5 |
| `application.yml` / `config.example.cmd` 改变量 | 图 6 |
| `CbtTurnService` 改授权写入时机 | 图 4、图 7 |
| `app.js` 改阶段推导 | 图 8 |
