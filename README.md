# AI Secretary Java + Python Demo

这个 demo 用一个更接近正式项目的方式实现“AI 秘书任务拆解与流程编排”：

```text
浏览器 / 调用方
  -> Java 主控服务: 业务入口、任务预览、确认、状态编排、执行模拟、执行日志
  -> Python 能力服务: 语义识别、任务拆解、时间归一化、cron 表达式生成
```

## 项目文档

- [AI Secretary 架构演进与改造规则](docs/AI_SECRETARY_EVOLUTION_PLAN.md)

该文档约束后续改造方向：项目不应被改造成普通聊天机器人，而应演进为可确认、可调度、可审计、可扩展 Skill 的 AI 秘书任务执行系统。

## 目录结构

```text
java-service/      Spring Boot 主控服务
python-service/    FastAPI 语义解析服务
docs/              项目架构与演进文档
```

## 职责边界

Python 负责：

- 识别意图：发邮件、发消息、回复消息、提醒、待办、周期任务
- 拆分多任务
- 抽取目标对象、内容和时间
- 输出固定 JSON Schema
- 只要判定为调度任务，就尽量生成 cron 表达式

Java 负责：

- 对外提供正式业务接口
- 生成 `traceId` / `planId`
- 调用 Python 语义服务
- 保存任务计划到 MySQL
- 提供用户确认机制，并记录 `operatorUserId`
- 编排立即执行、一次性调度、周期调度
- 对 Skill 参数进行基础校验
- 记录执行日志到 `ai_task_execution_log`
- 模拟邮件、消息、待办、提醒执行

## 启动 Python 服务

```powershell
cd D:\workspace\demo\python-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
copy .env.example .env
notepad .env
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 10001
```

在 `.env` 里配置 OpenRouter：

```text
OPENROUTER_API_KEY=你的新 OpenRouter Key
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_MODEL=deepseek/deepseek-v4-flash:free
APP_URL=http://127.0.0.1:10002
APP_TITLE=AI Secretary Demo
SKILL_CATALOG_URL=http://127.0.0.1:10002/api/skills
```

`OPENROUTER_API_KEY` 不配置时，Python 会自动回退到本地规则解析器。不要把 API Key 写进代码或提交到仓库；如果 key 曾经发到聊天或截图里，建议在 OpenRouter 后台删除并重新生成。

如果虚拟环境已经创建过，后续只需要：

```powershell
cd D:\workspace\demo\python-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 10001
```

Python 接口：

```text
GET  http://127.0.0.1:10001/api/v1/health
POST http://127.0.0.1:10001/api/v1/semantic/parse
```

## 启动 Java 服务

Java 服务使用 MySQL + MyBatis Plus + AutoTable。默认数据库配置在：

```text
D:\workspace\demo\java-service\src\main\resources\application.yml
```

默认端口固定使用：

```text
http://127.0.0.1:10002
```

默认连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ai_secretary_demo?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ai_demo
    password: ai_demo_123
```

先用 MySQL 管理员账号执行初始化 SQL，创建 demo 专用库和用户：

```text
D:\workspace\demo\java-service\src\main\resources\db\init-database.sql
```

它会创建：

```text
database: ai_secretary_demo
user:     ai_demo
password: ai_demo_123
host:     %
```

应用启动后会通过 AutoTable 根据实体自动建表：

```text
com.kailei.demo.entity.TaskPlanEntity         -> ai_task_plan
com.kailei.demo.entity.TaskActionEntity       -> ai_task_action
com.kailei.demo.entity.TaskExecutionLogEntity -> ai_task_execution_log
```

也可以不改文件，启动 Java 前通过环境变量覆盖：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3306/ai_secretary_demo?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="ai_demo"
$env:MYSQL_PASSWORD="ai_demo_123"
$env:PYTHON_SEMANTIC_URL="http://127.0.0.1:10001/api/v1/semantic/parse"
```

如果需要启用 Java API 鉴权，再设置：

```powershell
$env:AI_SECRETARY_API_KEY="your-secret-key"
```

不设置 `AI_SECRETARY_API_KEY` 时默认不启用鉴权，便于本地开发。启用后，所有 `/api/**` 请求需要带以下任意一种请求头：

```http
X-AI-Secretary-Key: your-secret-key
```

或：

```http
Authorization: Bearer your-secret-key
```

如果日志出现 `Communications link failure` 或 `Connection refused`，说明 MySQL 没有启动或没有监听 `3306`。先确认：

```powershell
netstat -ano | findstr :3306
```

没有输出时，先启动 MySQL，再重启 Java 服务。

```powershell
cd D:\workspace\demo\java-service
mvn spring-boot:run
```

Java 页面：

```text
http://127.0.0.1:10002
```

OpenAPI 文档：

```text
http://127.0.0.1:10002/v3/api-docs
http://127.0.0.1:10002/swagger-ui/index.html
```

Java 接口：

```text
GET  http://127.0.0.1:10002/api/skills
POST http://127.0.0.1:10002/api/ai-task/preview
POST http://127.0.0.1:10002/api/ai-task/{planId}/confirm
POST http://127.0.0.1:10002/api/ai-task/{planId}/cancel
POST http://127.0.0.1:10002/api/ai-task/{planId}/actions/{actionId}/retry
GET  http://127.0.0.1:10002/api/ai-task/{planId}?userId=demo-user
GET  http://127.0.0.1:10002/api/ai-task?userId=demo-user
GET  http://127.0.0.1:10002/api/ai-task/{planId}/logs?userId=demo-user
GET  http://127.0.0.1:10002/api/ai-task/{planId}/actions/{actionId}/logs?userId=demo-user
```

## 启动 Docker 基础设施和 XXL-JOB

项目提供了一个本地 `docker-compose.yml`，用于启动 MySQL 和 XXL-JOB Admin：

```powershell
cd D:\workspace\demo
docker compose up -d mysql xxl-job-admin
```

服务地址：

```text
MySQL:        127.0.0.1:3307
XXL-JOB UI:  http://127.0.0.1:8080/xxl-job-admin
账号:        admin
密码:        123456
```

Docker 首次启动 MySQL 时会自动初始化：

- `ai_secretary_demo`：Java demo 业务库。
- `xxl_job`：XXL-JOB Admin 元数据库。
- `ai-secretary-demo-executor` 执行器分组。
- `aiSecretaryDispatchDueTasks` 调度任务，默认每 10 秒触发一次。

如果已经存在旧的 MySQL volume，初始化 SQL 不会重复执行；需要重建时可以先确认数据可丢弃，再执行：

```powershell
docker compose down -v
docker compose up -d mysql xxl-job-admin
```

如果 Java 也要连接 Docker 里的 MySQL，启动 Java 前把数据库地址指到 `3307`：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3307/ai_secretary_demo?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="ai_demo"
$env:MYSQL_PASSWORD="ai_demo_123"
```

### 使用 XXL-JOB 触发调度

Java 默认仍启用本地 `@Scheduled` 扫描器，方便不启动 XXL-JOB 时也能跑通 demo。要切换为 XXL-JOB 触发，启动 Java 前设置：

```powershell
$env:AI_SECRETARY_LOCAL_SCHEDULER_ENABLED="false"
$env:XXL_JOB_ENABLED="true"
$env:XXL_JOB_ADMIN_ADDRESSES="http://127.0.0.1:8080/xxl-job-admin"
$env:XXL_JOB_ACCESS_TOKEN="default_token"
$env:XXL_JOB_EXECUTOR_APPNAME="ai-secretary-demo-executor"
$env:XXL_JOB_EXECUTOR_ADDRESS="http://host.docker.internal:10003"
$env:XXL_JOB_EXECUTOR_PORT="10003"

cd D:\workspace\demo\java-service
mvn spring-boot:run
```

说明：

- `XXL_JOB_EXECUTOR_ADDRESS` 是 XXL-JOB Admin 回调 Java 执行器的地址。Admin 跑在 Docker 容器里、Java 跑在 Windows 主机上时，推荐使用 `http://host.docker.internal:10003`。
- 如果 XXL-JOB Admin 和 Java 都在宿主机直接运行，可以改成 `http://127.0.0.1:10003`。
- XXL-JOB JobHandler 名称为 `aiSecretaryDispatchDueTasks`，内部复用 Java 现有 `dispatchDueOnceTasks()` 逻辑。
- 本地 `@Scheduled` 和 XXL-JOB 不建议同时开启，否则可能重复扫描到期任务。

## 示例请求

```http
POST /api/ai-task/preview
Content-Type: application/json
```

```json
{
  "text": "明天下午三点提醒我给王总发邮件，内容是项目方案已经更新；另外每周五上午十点通知团队群提交周报。收到李雷的消息后回复他：我下午会确认合同。",
  "timezone": "Asia/Shanghai",
  "userId": "demo-user"
}
```

预览结果中，只要 `schedule.schedule_type` 不是 `none`，就应体现 `cron` 字段：

```json
{
  "schedule": {
    "schedule_type": "recurring",
    "original_text": "每周五上午十点",
    "run_at": null,
    "cron": "0 0 10 ? * FRI",
    "timezone": "Asia/Shanghai",
    "next_run_at": "2026-05-29T10:00+08:00",
    "last_run_at": null,
    "trigger_count": 0
  }
}
```

预览后再确认：

```http
POST /api/ai-task/{planId}/confirm
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user"
}
```

取消未执行任务：

```http
POST /api/ai-task/{planId}/cancel
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user",
  "reason": "用户主动取消"
}
```

重试失败动作：

```http
POST /api/ai-task/{planId}/actions/{actionId}/retry
Content-Type: application/json
```

```json
{
  "operatorUserId": "demo-user"
}
```

## 状态流转

```text
WAITING_CONFIRM
  -> CONFIRMED
  -> EXECUTED   立即任务 / 到点后的一次性任务
  -> SCHEDULED  一次性/周期任务等待触发
  -> FAILED     执行、参数校验或时间解析失败
  -> CANCELLED  用户取消未执行任务
```

调度说明：

- 一次性任务：`schedule_type=once`，同时保留 `run_at`、`cron`、`next_run_at`。
- 周期任务：`schedule_type=recurring`，必须保留 `cron`，Java 服务会计算并维护 `next_run_at`、`last_run_at`、`trigger_count`。
- Java 本地 `@Scheduled` 默认每 10 秒扫描一次已确认的调度任务；也可以关闭本地扫描，改由 XXL-JOB 每 10 秒触发 `aiSecretaryDispatchDueTasks`。
- 周期任务执行成功后重新进入 `SCHEDULED` 并推进下一次触发时间。
- 当前执行器仍是模拟执行，正式项目可替换为真实邮件、WebSocket、企业微信、飞书、钉钉等执行器。

## 后续演进

详细规则见 [AI Secretary 架构演进与改造规则](docs/AI_SECRETARY_EVOLUTION_PLAN.md)。

近期重点：

- 增加任务编辑、取消、重试能力。
- 增加 API Key / JWT 鉴权和 userId 数据隔离。
- 将模拟执行器替换为真实邮件、提醒、待办、消息执行器。
- 增强调度幂等、失败重试、超时恢复和 HTTP Skill 安全策略。
- 后续按需引入 Spring AI、Dify、n8n、MCP 等辅助能力，但核心任务状态机继续沉淀在 Java 服务。