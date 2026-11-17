# 贡献指南

## 开发环境

| 项 | 要求 |
|---|---|
| JDK | 17（项目锁定 Corretto 17 / Temurin 17） |
| Maven | 用自带的 `mvnw.cmd`，无需全局安装 |
| MySQL | 8.x，仅运行应用时需要；测试不需要 |
| 编辑器 | 任何支持 UTF-8 的编辑器；改 `.cmd` 时必须能保存 CRLF |

Windows 是一等公民：`start.cmd`、`build.cmd`、`mvnw.cmd` 都是批处理。其他平台可以用 Maven 命令跑构建与应用，但没有等价的一键脚本。

## 本地设置

```bat
copy config.example.cmd config.cmd
```

`config.cmd` 已被 git 排除。**永远不要提交它**，也不要把 key、数据库密码写进任何被跟踪的文件。

`cbt-agent-docs/` 和 `.workbuddy/` 同样被排除——它们是内部设计笔记与工作记录，不属于公开仓库。

## 构建与测试

```bat
build.cmd
```

等价于 `mvnw.cmd clean verify`。21 项离线测试：

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `CbtGraphAcceptanceTest` | 16 | V1–V12 流程约束 + A1–A4 授权闭环 |
| `PromptAssetTest` | 5 | SHA-256 资产锁定、LF 编码、硬约束文本、JSON 契约 |

测试用 StubLlm + `MemorySaver` + Mockito，**不启 Spring 上下文、不连 MySQL、不联网**。新增图行为时应沿用这个模式，避免让 CI 依赖外部服务。

需要跑真实 MySQL 的验证目前不在 `verify` 中，不要把它加进去。

## 代码约定

- **节点与路由用常量**：`Nodes`、`Routes`、`StateKeys` 里的常量是唯一来源，路由 Map 必须穷举并包含 `parse_error` 兜底。
- **模型输出不可信**：任何 LLM 字段都要过必填校验与枚举白名单，再经 `NodeOutputGuard` 剥离受保护 key。
- **授权只能来自用户请求**：`recordApproved` 的唯一写入口是 `CbtTurnService`，不要在节点里推断。
- **恢复不要用裸 null**：写 `graph.invoke(Map.of(), resumed)`；`invoke(null, ...)` 在 1.1.2.0 有重载歧义。
- **DTO 用 record**：请求与响应都是 Java record，字段注解显式指定 JSON 名。

## 提示词资产（禁止随意修改）

`src/main/resources/prompts/` 下 11 份文件被 `PromptAssetTest` 用 SHA-256 锁定，并要求：

- UTF-8 编码
- LF 换行（不能有 `\r`）
- 系统提示词保留安全与能力边界的原文
- 严格 JSON 输出契约与终局枚举齐全

如果确实需要调整提示词，必须同步更新测试中的哈希，并在提交信息里说明改动原因与影响范围。这是有意为之的高门槛——这些文本是安全边界的一部分。

## 批处理文件

- **必须 CRLF**。LF 会让 `cmd` 吃掉 `rem` / `setlocal` 的字符，报 `'elayedExpansion' is not recognized`。
- `echo` 行里的 `>` 必须转义为 `^->` 或 `-^>`，否则会变成重定向并在项目根创建垃圾文件。
- 改完脚本后用 `head -c 20 file.cmd | od -c` 确认存在 `\r\n`。

## 提交前检查

- [ ] `build.cmd` 全绿
- [ ] 没有提交 `config.cmd`、`.env`、任何 key 或密码
- [ ] 没有把 `cbt-agent-docs/` 的内部设计稿复制进 `docs/`
- [ ] 新增的公开文档链接有效、不指向被排除的目录
- [ ] 批处理文件换行是 CRLF
- [ ] 提示词资产未被误改

## 文档

```
README.md                    项目门面
docs/getting-started.md      环境、启动、验证
docs/configuration.md        变量、换模型、密钥
docs/architecture.md         分层、图、安全约束
docs/diagrams.md             8 张 Mermaid 图（结构/状态机/时序/ER/授权）
docs/api.md                  接口契约与示例
docs/data-and-privacy.md     表结构、授权、留存
docs/troubleshooting.md      故障排查
```

## 社区

- 行为准则见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)；参与即表示你同意遵守它。
- 安全漏洞请勿公开提 Issue，按 [SECURITY.md](SECURITY.md) 的方式私下报告。
- 提交 PR 前请对照 `.github/PULL_REQUEST_TEMPLATE.md` 的自检清单。

新增能力时同步更新相关文档；不要新建与现有文档重复的入口。
