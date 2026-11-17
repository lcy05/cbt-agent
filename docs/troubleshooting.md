# 故障排查

按日志第一处异常匹配即可，不必通读堆栈。

## 启动阶段

| 症状 | 原因 | 处理 |
|---|---|---|
| `[2/5] LLM key .......... MISSING` | `config.cmd` 未填 key | 填 `DEEPSEEK_API_KEY`；页面能开但问答 401 |
| `LLM ready ... key=NOT SET` | 应用没读到 key | 检查启动日志上一行 `[0/5] config.cmd ... loaded`，以及是否在别的 shell 里启动 |
| `Authentication Fails` / `401 Unauthorized` | key 与站点不匹配，或 key 无效 | 核对 `AI_BASE_URL` 与 key 来源；换模型厂家必须同步换 key |
| `Access denied for user 'root'@'localhost' (using password: NO)` | 数据库密码没传进去 | 在 `config.cmd` 填 `CBT_DB_PASSWORD`，或让它交互提示 |
| `Unknown database 'cbt_agent'` / `Unable to create tables` | 未执行建库脚本 | 跑 `start.cmd`（不带 `--skip-db`），或手工执行 `src/main/resources/db/schema.sql` |
| `[3/5] MySQL client ...... NOT FOUND` | `mysql.exe` 不在常见路径 | 设置 `CBT_MYSQL_HOME` 指向 MySQL 安装根目录 |
| `[5/5] port 8080 ......... IN USE` | 端口被占用 | 停掉占用进程，或 `set SERVER_PORT=8081 && start.cmd` |
| 日志说 `Tomcat initialized with port <非 8080 的随机端口>` | 宿主注入的 `SERVER__PORT` 被 Spring 折叠成 `server.port` | `start.cmd` 已自动清除；手工启动时执行 `unset SERVER_PORT` / `set SERVER__PORT=` |

## 批处理脚本

| 症状 | 原因 | 处理 |
|---|---|---|
| `'elayedExpansion' is not recognized` | `.cmd` 被保存成 LF 换行 | 转回 CRLF。用 `head -c 20 x.cmd \| od -c` 检查是否有 `\r\n` |
| 项目根冒出几个无扩展名垃圾文件 | `echo` 行里的 `>` 被当成重定向 | 写成 `^->` 或 `-^>`；注意 `rem` 行不受影响 |
| 改了 `config.cmd` 但不生效 | 编辑的不是 `start.cmd` 同目录的那份 | `start.cmd` 只加载 `%~dp0config.cmd`（自身所在目录） |
| 密码被吞字符 | 密码含 `%` 或 `!` | 留空，走交互输入 |

## 运行阶段

| 症状 | 原因 | 处理 |
|---|---|---|
| 页面一直显示「离线演示」 | 前端探测 `/api/cbt/turn` 失败 | 确认后端在 8080；GET 该路径返回 405 也算在线 |
| 每轮都回 `parse_error` | 模型没产出严格 JSON，或换了不兼容的模型 | 换回 `deepseek-chat` 试；查看模型返回原文确认是否被 ` ```json ` 围栏包裹 |
| 保存后仍反复询问 | 请求没带 `recordApproved: true` | 前端点「保存」按钮会带上；直接调 API 需手工传 |
| 左栏「安全评估」显示未进入 | 前端旧版本未补安全门 | 刷新页面（Ctrl+F5 清缓存）；这是已修复的展示问题 |
| 安全门拦停后左栏全是灰的 | `emergency_support` 等逃生节点没有卡片 | 已知缺口，见 README「已知边界」 |
| 刷新页面后会话丢失 | `threadId` 只存在前端内存 | 当前版本没有会话持久化到浏览器，刷新即新会话 |

## 测试阶段

| 症状 | 原因 | 处理 |
|---|---|---|
| `build.cmd` 编译报编码错误 | 源文件被改成非 UTF-8 | 用 UTF-8 保存；提示词资源必须 UTF-8 + LF |
| `PromptAssetTest` 哈希不匹配 | 提示词被改动 | 这些资产是锁定的，不要编辑；恢复原始内容 |
| 提示词测试找不到源文档 | 旧版本测试依赖已排除的内部文档 | 已解耦；若仍遇到，更新到当前版本 |

## 如何拿到更多信息

- 启动日志的 `LLM ready` 行：确认实际生效的站点、模型和 key 状态。
- `start.cmd` 失败时会打印一个关键字对照表，按第一句错误匹配即可。
- 前端右上角连接状态：`后端已就绪` / `已连接` / `离线演示` / `请求失败`。
- 后端异常统一返回 `status=error`，不向前端暴露堆栈；细节在服务端控制台。

仍然解决不了时，请提供：操作系统与 JDK 版本、启动日志前 30 行、`[2/5]` 与 `LLM ready` 的输出（key 请打码）、以及完整报错的第一行。
