# 管理端 API Key 保护说明

本次增强 `ApiKeyAuthFilter`，为全局调度管理接口增加独立管理端 API Key 能力。

## 保护对象

当前管理端接口包括：

```http
GET /api/ai-task/dispatch/metrics/summary
GET /api/ai-task/dispatch-records
```

这些接口会暴露全局调度积压、失败记录、重试状态和任务计划 ID，不应直接裸露给普通调用方。

## 配置项

```yaml
ai-secretary:
  api-key: ${AI_SECRETARY_API_KEY:}
  admin-api-key: ${AI_SECRETARY_ADMIN_API_KEY:}
```

| 配置 | 说明 |
|---|---|
| `ai-secretary.api-key` | 普通 API Key；配置后保护 `/api/**` |
| `ai-secretary.admin-api-key` | 管理端 API Key；配置后优先用于管理端接口 |

## 鉴权规则

### 1. 非 `/api/**` 请求

直接放行。

### 2. `OPTIONS` 请求

直接放行，避免影响跨域预检。

### 3. 普通 `/api/**` 请求

如果 `ai-secretary.api-key` 为空，则放行，兼容本地开发。

如果 `ai-secretary.api-key` 不为空，则必须提供有效普通 API Key。

### 4. 管理端接口

如果 `ai-secretary.admin-api-key` 不为空，管理端接口必须使用 admin key。

如果 `ai-secretary.admin-api-key` 为空，则回退使用 `ai-secretary.api-key`。

如果两者都为空，则放行，兼容本地开发环境。

## 请求头

普通 API Key：

```http
X-AI-Secretary-Key: <api-key>
```

管理端 API Key：

```http
X-AI-Secretary-Admin-Key: <admin-api-key>
```

也支持 Bearer：

```http
Authorization: Bearer <key>
```

当访问管理端接口且配置了 `admin-api-key` 时，Bearer token 应传 admin key。

## 示例

### 调用调度指标摘要

```bash
curl \
  -H "X-AI-Secretary-Admin-Key: ${AI_SECRETARY_ADMIN_API_KEY}" \
  http://127.0.0.1:10002/api/ai-task/dispatch/metrics/summary
```

### 查询全局 FAILED dispatch records

```bash
curl \
  -H "X-AI-Secretary-Admin-Key: ${AI_SECRETARY_ADMIN_API_KEY}" \
  "http://127.0.0.1:10002/api/ai-task/dispatch-records?status=FAILED&page=1&size=20"
```

### 普通 API 调用

```bash
curl \
  -H "X-AI-Secretary-Key: ${AI_SECRETARY_API_KEY}" \
  http://127.0.0.1:10002/api/ai-task?userId=user-001&page=1&size=20
```

## 失败响应

无效或缺失 API Key 时返回：

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json
```

响应体：

```json
{
  "code": "UNAUTHORIZED",
  "message": "API Key 无效或缺失，请通过 X-AI-Secretary-Key、X-AI-Secretary-Admin-Key 或 Authorization: Bearer <key> 传入有效凭证。",
  "path": "/api/ai-task/dispatch-records",
  "traceId": "..."
}
```

## 当前边界

1. 当前仍是 API Key 级别保护，不是完整 RBAC。
2. `admin-api-key` 配置后，普通 API Key 不能访问管理端接口，除非两者配置成同一个值。
3. 管理端保护路径当前是显式白名单：
   - `/api/ai-task/dispatch/metrics/summary`
   - `/api/ai-task/dispatch-records`
4. 本地开发如果不配置任何 key，会继续放行 `/api/**`。

## 生产建议

1. 生产环境必须配置 `AI_SECRETARY_API_KEY`。
2. 生产环境建议单独配置 `AI_SECRETARY_ADMIN_API_KEY`，并与普通 API Key 区分。
3. 管理端 key 应只注入管理端前端或运维工具，不应暴露给普通用户端。
4. 后续接入 Spring Security 后，应将这些接口迁移到 `ROLE_ADMIN` 或 `SCOPE_dispatch:read` 权限模型。
