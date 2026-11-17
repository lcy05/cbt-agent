# 配置参考

所有配置都通过环境变量传入。`config.cmd` 只是 Windows 下设置这些变量的便捷方式，`start.cmd` 启动时会自动 `call` 它。

## 完整变量表

| 变量 | 默认值 | 读取方 | 说明 |
|---|---|---|---|
| `AI_BASE_URL` | `https://api.deepseek.com` | Spring AI | 模型站点，**只填域名**，客户端自动补 `/v1/chat/completions` |
| `AI_MODEL` | `deepseek-chat` | Spring AI | 模型名 |
| `AI_API_KEY` | 见下 | Spring AI | 优先使用的 key |
| `DEEPSEEK_API_KEY` | 空 | 回退 | `AI_API_KEY` 为空时使用 |
| `CBT_DB_URL` | `jdbc:mysql://127.0.0.1:3306/cbt_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` | Spring Boot | JDBC URL |
| `CBT_DB_USERNAME` | `root` | Spring Boot + `start.cmd` | 数据库用户 |
| `CBT_DB_PASSWORD` | 空 | Spring Boot + `start.cmd` | 数据库密码，一处配置两处复用 |
| `SERVER_PORT` | `8080` | `start.cmd` | 端口检查与浏览器地址；应用端口由 Spring 的 `server.port` 决定 |
| `CBT_JAVA_HOME` | 内置探测 | `mvnw.cmd` / `start.cmd` | JDK 17 安装目录；换机器时指向你自己的 JDK 17 |
| `CBT_MYSQL_HOME` | 自动探测 | `start.cmd` | MySQL 安装根目录（其下有 `bin\mysql.exe`） |

`application.yml` 为 key 设置了占位默认值 `sk-not-configured`，所以**缺 key 时应用仍能启动**——页面、图、checkpoint 表都正常，只有模型调用返回 401。这样设计是为了让配置问题可观察，而不是在 Bean 创建阶段直接崩溃。

## config.cmd 的优先级

`start.cmd` 先 `call config.cmd`，文件里的 `set` 会覆盖父进程已有的同名环境变量，因此：

```
config.cmd  >  系统环境变量  >  application.yml 默认值
```

`config.cmd` 不存在不算错误，脚本会提示 `copy config.example.cmd config.cmd` 后继续用环境变量。

## 换模型厂家

项目使用 OpenAI 兼容协议，换厂家不需要改代码：

| 厂家 | `AI_BASE_URL` | `AI_MODEL` |
|---|---|---|
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` / `deepseek-reasoner` |
| Kimi | `https://api.moonshot.cn` | `moonshot-v1-8k` |
| GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-plus` |
| Ollama（本地） | `http://127.0.0.1:11434/v1` | `qwen2.5:14b` |

注意：

- `base-url` 不要写 `/v1` 后缀，客户端会自己补。
- `deepseek-reasoner` 会忽略 `temperature` 参数。
- 换厂家后必须同步换 key；401 通常意味着 key 与站点不匹配。

为什么不用 `spring-ai-starter-model-deepseek`：那个 starter 的配置前缀锁死为 `spring.ai.deepseek`，换厂家要改代码。通用 OpenAI starter 让站点、模型、key 三者都可配置。

## 密码与特殊字符

`config.cmd` 是批处理文件，密码里若含 `%` 或 `!`：

- `%` 会被 cmd 当成变量引用的一部分
- `!` 在启用延迟展开时被吞掉（恰好 `start.cmd` 开启了它）

**处理方式**：把 `CBT_DB_PASSWORD` 留空，`start.cmd` 第 4 步会交互提示输入（`set /p`），该输入不经过 `set` 语句解析。

密码传递给 `mysql.exe` 时使用 `MYSQL_PWD` 环境变量，而不是 `-p` 命令行参数——避免出现在进程列表与日志里；用完立即清空，也不会泄漏给后续的 java 子进程。

## 密钥 hygiene

- `config.cmd` 与 `.env`、`*.pem` 已被 `.gitignore` 排除。
- 启动日志只打印 `key=set(N chars)` 或 `NOT SET`，不打印 key 内容。
- 不要提交真实 key，也不要把它贴进 issue、截图或聊天记录。
- 怀疑泄露时，去供应商控制台轮换 key，再更新本地 `config.cmd`。

## 端口冲突的一个隐蔽来源

某些宿主环境会向子 shell 注入 `SERVER__PORT`。Spring 的 relaxed binding 会把双下划线折叠成点号，于是 `SERVER__PORT=12345` 被读成 `server.port=12345`，**无声覆盖** `application.yml` 里的 8080。

`start.cmd` 开头执行 `set "SERVER__PORT="` 清除它。如果你是手工启动且遇到页面连不上，检查这个变量。

自定义端口：

```bat
set SERVER_PORT=8081
start.cmd
```
