# AI Secretary Java + Python Demo

这个 demo 用一个更接近正式项目的方式实现“AI 秘书任务拆解与流程编排”：

```text
浏览器 / 调用方
  -> Java 主控服务: 业务入口、任务预览、确认、状态编排、执行模拟
  -> Python 能力服务: 语义识别、任务拆解、时间归一化
```

## 目录结构

```text
demo/
  java-service/      Spring Boot 主控服务
  python-service/    FastAPI 语义解析服务
```

## 职责边界

Python 负责：

- 识别意图：发邮件、发消息、回复消息、提醒、待办、周期任务
- 拆分多任务
- 抽取目标对象、内容和时间
- 输出固定 JSON Schema

Java 负责：

- 对外提供正式业务接口
- 生成 `traceId` / `planId`
- 调用 Python 语义服务
- 保存任务计划到内存仓库
- 提供用户确认机制
- 编排立即执行、一次性调度、周期调度
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
APP_URL=http://127.0.0.1:10000
APP_TITLE=AI Secretary Demo
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

也可以不改文件，启动 Java 前通过环境变量覆盖：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3306/ai_secretary_demo?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="ai_demo"
$env:MYSQL_PASSWORD="ai_demo_123"
```

如果日志出现 `Communications link failure` 或 `Connection refused`，说明 MySQL 没有启动或没有监听 `3306`。先确认：

```powershell
netstat -ano | findstr :3306
```

没有输出时，先启动 MySQL，再重启 Java 服务。

```text
com.kailei.demo.entity.TaskPlanEntity   -> ai_task_plan
com.kailei.demo.entity.TaskActionEntity -> ai_task_action
```

也可以先手动执行：

```text
D:\workspace\demo\java-service\src\main\resources\db\init-database.sql
```

```powershell
cd D:\workspace\demo\java-service
mvn spring-boot:run
```

Java 页面：

```text
http://127.0.0.1:10000
```

Java 接口：

```text
POST http://127.0.0.1:10000/api/ai-task/preview
POST http://127.0.0.1:10000/api/ai-task/{planId}/confirm
GET  http://127.0.0.1:10000/api/ai-task/{planId}
GET  http://127.0.0.1:10000/api/ai-task
```

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

## 状态流转

```text
WAITING_CONFIRM
  -> CONFIRMED
  -> EXECUTED   立即任务
  -> SCHEDULED  一次性/周期任务
  -> FAILED     执行或时间解析失败
```

当前版本使用 Java 内存仓库 `ConcurrentHashMap` 保存任务计划。一次性调度任务由 Java `@Scheduled` 每 10 秒扫描一次，到点后模拟执行。后续接入正式项目时，可替换为 MySQL + XXL-Job + MQ。

## 后续演进

- Python 规则解析器替换为 LLM structured output。
- Java 内存仓库替换为 MySQL 表。
- Java 模拟执行器替换为真实邮件、WebSocket、企业微信、飞书、钉钉等执行器。
- 一次性和周期任务调度替换为 XXL-Job。
- 增加联系人消歧、权限校验、审计日志和失败重试。
