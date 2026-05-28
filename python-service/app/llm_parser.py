import json
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

import httpx
from pydantic import ValidationError

from app.schemas import ParseResponse
from app.settings import get_settings
from app.skill_client import render_skill_prompt


CN_TZ = timezone(timedelta(hours=8))

SYSTEM_PROMPT = """
你是 AI 秘书系统里的语义识别与任务拆解模块。
你的任务是把用户的一段中文自然语言拆成可执行任务计划。

只返回 JSON，不要返回 Markdown，不要解释。
JSON 必须符合以下结构：
{
  "trace_id": "string",
  "language": "zh-CN",
  "summary": "共识别 N 个可执行任务",
  "tasks": [
    {
      "action_id": "act-xxxxxxxx",
      "action_type": "必须来自可用 Skills 里的 actionType",
      "skill_name": "必须来自可用 Skills 里的 name",
      "title": "简短任务标题",
      "content": "任务正文、邮件正文、消息正文或提醒内容",
      "target": {
        "target_type": "user | group | email | dynamic | self | unknown",
        "name": "目标名称，没有则 null",
        "address": "邮箱地址，没有则 null"
      },
      "schedule": {
        "schedule_type": "none | once | recurring",
        "original_text": "原始时间表达，没有则 null",
        "run_at": "ISO-8601 时间，例如 2026-05-27T15:00:00+08:00，没有则 null",
        "cron": "Quartz cron，例如 0 0 10 ? * FRI，没有则 null",
        "timezone": "Asia/Shanghai"
      },
      "args": {},
      "priority": "low | normal | high",
      "confidence": 0.0,
      "requires_confirmation": true,
      "source_sentence": "对应的原始句子",
      "analysis_note": "一句话说明该任务的解析依据、不确定点或需要人工确认的原因"
    }
  ],
  "warnings": []
}

{skills_prompt}

关键规则：
1. 一句话里有多个动作时必须拆成多个 tasks。
2. action_type 必须选择当前可用 Skills 中最匹配的 actionType；skill_name 必须填写对应 Skill 的 name。
3. 发送、回复、对外通知类任务必须 requires_confirmation=true；如果 Skill 的 requiresConfirmation=true，也必须返回 true。
4. 没有明确时间的任务 schedule_type=none。
5. 一次性时间用 run_at，周期时间用 cron。
6. 不确定的对象 target_type=unknown，并把问题写入 warnings。
7. 当前日期是 {current_date}，用户时区是 {timezone_name}。
8. 如果 Skill 的 inputSchema 中出现额外业务参数，请放入 args，例如 location、date、file_path、subject 等。

对象和内容边界规则，必须严格遵守：
1. target.name 只能是人名、群名、邮箱名、组织名，不能包含动作或任务内容。
2. “提醒我整理本月项目进度”没有外部对象，target.target_type=unknown，content=整理本月项目进度。
3. “提醒我给李雷确认合同盖章”中，target.name=李雷，content=确认合同盖章。
4. “把总结邮件发给王总”中，target.name=王总，action_type=send_email。
5. “通知研发群提交周报”中，target.name=研发群，content=提交周报。
6. target.name 里如果出现“确认、整理、提交、同步、复盘、说明、发送、回复、处理、审批、盖章”等动作词，一定是错的，要把动作词及其后面内容放到 content。
7. “电话催一下”“检查周报”“检查某项目”“联系某人一起排查”这类短语是任务内容，不是对象；只有短语里的明确人名或群名可以作为 target。
8. 用户输入里的包装说明不是任务，例如“今天是...帮我把下面这段自然语言拆成可执行任务”不能生成 create_todo。
9. “发送前必须让我确认”“不要自动回复”“只提醒我人工处理”“涉及邮件/合同/预算都要确认”是执行策略，不要单独生成发送邮件、发送消息或待办任务。
10. “我/自己/本人”作为执行对象时使用 target_type=self，name=我；没有外部对象的个人提醒可以使用 unknown。
11. “有人/谁/对方/他/他们/未提交的人/未回复的人”这类由条件产生的对象，不能写成 user；使用 target_type=dynamic，并把 name 写成可执行的动态集合，例如“未提交周报的人”“未回复的人”“符合条件的人”。
12. 代词“他、她、他们、对方”不能单独作为 target.name；必须回到前文条件解析成 dynamic target，无法解析则 target_type=unknown。

示例：
输入：明天下午三点提醒我给李雷确认合同盖章
输出任务：skill_name=reminder, action_type=reminder, target.name=李雷, content=确认合同盖章, schedule.original_text=明天下午三点

输入：下周一上午九点提醒我整理本月项目进度
输出任务：skill_name=reminder, action_type=reminder, target.target_type=unknown, target.name=null, content=整理本月项目进度

输入：如果系统里还有状态停留在“处理中”超过三十分钟的单据，就提醒我联系李雷一起排查
输出任务：skill_name=reminder, action_type=reminder, target.name=李雷, content=联系李雷一起排查，不要直接重跑任务

输入：如果有人到下午三点还没提交周报，再单独提醒他一次
输出任务：skill_name=reminder, action_type=reminder, target.target_type=dynamic, target.name=未提交周报的人, content=如果有人到下午三点还没提交周报，再单独提醒一次
"""


@dataclass(frozen=True)
class LlmParseResult:
    response: ParseResponse | None
    warning: str | None = None


async def parse_text_with_llm(text: str, timezone_name: str, trace_id: str | None = None) -> ParseResponse | None:
    result = await parse_text_with_llm_with_diagnostics(text, timezone_name, trace_id)
    return result.response


async def parse_text_with_llm_with_diagnostics(
    text: str,
    timezone_name: str,
    trace_id: str | None = None,
) -> LlmParseResult:
    settings = get_settings()
    if not settings.openrouter_api_key:
        return LlmParseResult(None, "未配置 OpenRouter API Key，已使用规则 fallback 解析。")

    request_trace_id = trace_id or f"trace-{uuid.uuid4().hex[:12]}"
    user_prompt = {
        "trace_id": request_trace_id,
        "timezone": timezone_name,
        "text": text,
    }

    payload = {
        "model": settings.openrouter_model,
        "messages": [
            {"role": "system", "content": build_system_prompt(timezone_name)},
            {"role": "user", "content": json.dumps(user_prompt, ensure_ascii=False)},
        ],
        "temperature": 0.1,
        "response_format": {"type": "json_object"},
    }

    headers = {
        "Authorization": f"Bearer {settings.openrouter_api_key}",
        "Content-Type": "application/json",
        "HTTP-Referer": settings.app_url,
        "X-Title": settings.app_title,
    }

    try:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{settings.openrouter_base_url.rstrip('/')}/chat/completions",
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
        content = response.json()["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        parsed["trace_id"] = parsed.get("trace_id") or request_trace_id
        return LlmParseResult(ParseResponse.model_validate(parsed))
    except httpx.HTTPStatusError as ex:
        return LlmParseResult(None, f"LLM HTTP 调用失败: {ex.response.status_code}，已使用规则 fallback 解析。")
    except httpx.HTTPError as ex:
        return LlmParseResult(None, f"LLM 网络调用失败: {ex.__class__.__name__}，已使用规则 fallback 解析。")
    except (KeyError, json.JSONDecodeError) as ex:
        return LlmParseResult(None, f"LLM 响应不是有效 JSON: {ex.__class__.__name__}，已使用规则 fallback 解析。")
    except ValidationError as ex:
        first_error = ex.errors()[0] if ex.errors() else {}
        field = ".".join(str(item) for item in first_error.get("loc", []))
        return LlmParseResult(None, f"LLM 响应结构校验失败: {field or 'unknown'}，已使用规则 fallback 解析。")


def build_system_prompt(timezone_name: str) -> str:
    current_date = datetime.now(CN_TZ).date().isoformat()
    return (
        SYSTEM_PROMPT
        .replace("{current_date}", current_date)
        .replace("{timezone_name}", timezone_name)
        .replace("{skills_prompt}", render_skill_prompt())
    )
