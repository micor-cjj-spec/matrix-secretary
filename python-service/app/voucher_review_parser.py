import re
import uuid
from datetime import datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from app.schemas import ParseResponse, TaskAction, TaskSchedule, TaskTarget


VOUCHER_REVIEW_TERMS = ("未审核", "待审核", "凭证审核", "审核待办")
TODO_TERMS = ("待办", "任务", "跟进", "生成待办", "创建待办")


def try_parse_voucher_review(
    text: str,
    timezone_name: str,
    trace_id: str | None = None,
) -> ParseResponse | None:
    normalized = text.strip()
    if "凭证" not in normalized or not any(term in normalized for term in VOUCHER_REVIEW_TERMS):
        return None

    period = resolve_period(normalized, timezone_name)
    query_action = TaskAction(
        action_id=f"act-{uuid.uuid4().hex[:8]}",
        action_type="query_pending_vouchers",
        skill_name="matrix_voucher_pending_review",
        title=f"查询 {period} 待审核凭证",
        content=f"查询 {period} 状态为 SUBMITTED 的待审核凭证",
        target=TaskTarget(target_type="self", name="我"),
        schedule=TaskSchedule(schedule_type="none", timezone=timezone_name),
        args={
            "period": period,
            "page": 1,
            "size": 20,
        },
        priority="normal",
        confidence=0.98,
        requires_confirmation=False,
        source_sentence=normalized,
        analysis_note="识别为 Matrix 凭证待审核查询，只执行只读查询，不执行审核或过账。",
    )

    tasks = [query_action]
    if any(term in normalized for term in TODO_TERMS):
        todo_content = f"审核 {period} 未审核凭证"
        tasks.append(
            TaskAction(
                action_id=f"act-{uuid.uuid4().hex[:8]}",
                action_type="create_todo",
                skill_name="create_todo",
                title=f"创建 {period} 凭证审核待办",
                content=todo_content,
                target=TaskTarget(target_type="self", name="我"),
                schedule=TaskSchedule(schedule_type="none", timezone=timezone_name),
                args={},
                priority="normal",
                confidence=0.96,
                requires_confirmation=True,
                source_sentence=normalized,
                analysis_note="查询完成后创建站内待办；本期不会自动审核、过账或修改凭证。",
            )
        )

    summary = "已识别待审核凭证查询"
    if len(tasks) > 1:
        summary += "和审核待办创建"
    return ParseResponse(
        trace_id=trace_id or f"trace-{uuid.uuid4().hex[:12]}",
        summary=summary,
        tasks=tasks,
        warnings=[],
    )


def resolve_period(text: str, timezone_name: str) -> str:
    explicit = re.search(r"(?P<year>20\d{2})\s*[-/年]\s*(?P<month>\d{1,2})(?:\s*月)?", text)
    if explicit:
        year = int(explicit.group("year"))
        month = int(explicit.group("month"))
        if 1 <= month <= 12:
            return f"{year:04d}-{month:02d}"

    now = now_in_timezone(timezone_name)
    if "上月" in text or "上个月" in text:
        year = now.year if now.month > 1 else now.year - 1
        month = now.month - 1 if now.month > 1 else 12
        return f"{year:04d}-{month:02d}"
    return f"{now.year:04d}-{now.month:02d}"


def now_in_timezone(timezone_name: str) -> datetime:
    try:
        return datetime.now(ZoneInfo(timezone_name))
    except (ZoneInfoNotFoundError, ValueError):
        return datetime.now(ZoneInfo("Asia/Shanghai"))
