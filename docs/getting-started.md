# 快速开始

## 环境要求

| 项 | 要求 | 说明 |
|---|---|---|
| JDK | 17 | `start.cmd` 内置了一个 JDK 17 路径；换机器时用 `CBT_JAVA_HOME` 指向你自己的安装目录 |
| MySQL | 8.x | 服务需已运行；`mysql.exe` 通常不在 PATH，脚本会自动探测常见安装路径 |
| 模型 key | 任意 OpenAI 兼容站点 | 默认 DeepSeek，在 https://platform.deepseek.com/api_keys 申请 |
| 浏览器 | 现代浏览器 | 页面为原生 HTML/CSS/JS，无构建步骤 |
| 操作系统 | Windows（脚本路径） | 其他平台见下方「手动启动」 |

## Windows 一键启动

**第 1 步：生成配置文件**

```bat
cd C:\path\to\cbt-agent
copy config.example.cmd config.cmd
```

`config.cmd` 已被 `.gitignore` 排除，永远不会进仓库。

**第 2 步：填写两个值**

用任意文本编辑器打开 `config.cmd`，只改这两行：

```bat
set "DEEPSEEK_API_KEY=sk-你的真实key"
set "CBT_DB_PASSWORD=你的MySQL密码"
```

`CBT_DB_USERNAME=root` 与 DeepSeek 的站点/模型默认值通常不用动。

> 密码含 `%` 或 `!` 时不要写进 `config.cmd`——cmd 会在 `set` 语句里吞掉这些字符。留空，启动时交互输入即可。

**第 3 步：启动**

```bat
start.cmd
```

脚本会依次走五步，每步都有独立报错信息：

| 步骤 | 动作 | 成功标志 |
|---|---|---|
| 0 | 加载 `config.cmd` | `[0/5] config.cmd ....... loaded` |
| 1 | 定位 JDK 17 | `[1/5] JDK 17 ............ ok` |
| 2 | 解析模型配置 | `[2/5] LLM key .......... ok   sk-xxxx...` |
| 3 | 定位 `mysql.exe` | `[3/5] MySQL client ...... ok` |
| 4 | 连库并幂等灌入 `schema.sql` | `[4/5] schema ............ ok` |
| 5 | 检查 8080 端口 → 启动 → 35 秒后开浏览器 | `[5/5] starting .........` |

**第 4 步：确认真的起来了**

日志里应当依次出现：

```
LLM ready  base-url=https://api.deepseek.com  model=deepseek-chat  key=set(35 chars)
Tomcat started on port 8080
```

`LLM ready` 这一行是配置是否生效的权威凭据：它打印的是 Spring 实际绑定到的值，而不是脚本里的变量。`key=NOT SET` 表示没读到 key（页面能开，但每句问答返 401）。

## 启动参数

```bat
start.cmd                  :: 完整流程，自动开浏览器
start.cmd --no-browser     :: 不开浏览器（服务器/远程场景）
start.cmd --skip-db        :: 跳过 schema 灌入（库已初始化过）
start.cmd --help           :: 参数与环境变量清单
```

停止服务：在窗口按 `Ctrl+C`。

## 手动启动（Linux / macOS / 自定义环境）

1. 安装 JDK 17、Maven 3.9+、MySQL 8。
2. 灌入建库脚本：

   ```bash
   mysql -u root -p < src/main/resources/db/schema.sql
   ```

3. 设置环境变量：

   ```bash
   export AI_BASE_URL=https://api.deepseek.com
   export AI_MODEL=deepseek-chat
   export DEEPSEEK_API_KEY=sk-...
   export CBT_DB_USERNAME=root
   export CBT_DB_PASSWORD=...
   ```

4. 启动：

   ```bash
   mvn spring-boot:run
   ```

   `schema.sql` 自带 `CREATE DATABASE IF NOT EXISTS cbt_agent` 与 `USE cbt_agent`，不需要预先建库。

5. 打开 `http://localhost:8080/`。

## 先看看界面（不需要 key 和数据库）

直接双击 `src/main/resources/static/index.html`，或在页面地址后加 `?demo=1`。前端会探测后端；连不上就用内置的 10 轮脚本回放完整链路，并在顶部标「演示数据」。

演示模式是**纯前端路径**，不经过 Spring，也不消耗模型额度。

## 验证清单

启动后按顺序确认：

- [ ] 日志出现 `LLM ready`，`key=set(N chars)` 而非 `NOT SET`
- [ ] 日志出现 `Tomcat started on port 8080`
- [ ] 浏览器打开页面，右上角连接状态为「后端已就绪」
- [ ] 输入一句困扰，右栏出现 AI 回复
- [ ] 左栏第一格「安全评估」变为已完成（不是「未进入」）
- [ ] 连续追问两轮，`threadId` 保持不变（说明 checkpoint 生效）

任何一步失败，见 [故障排查](troubleshooting.md)。
