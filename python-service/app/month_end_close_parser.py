import re
import uuid
from datetime import datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from app.schemas import ParseResponse, TaskAction, TaskSchedule, TaskTarget


MONTH_END_TERMS = ("月结", "结账", "期末")
CHECK_TERMS = ("检查", "条件", "阻塞", "是否可以", "能否", "是否满足", "准备情况")
TODO_TERMS = ("待办", "任务", "跟进", "生成待办", "创建待办", "处理项")


def try_parse_month_end_close(
    text: str,
    timezone_name: str,
    trace_id: str | None = None,
) -> ParseResponse | None:
    normalized = text.strip()
    if not any(term in normalized for term in MONTH_END_TERMS):
        return None
    if not any(term in normalized for term in CHECK_TERMS):
        return None

    period = resolve_period(normalized, timezone_name)
    forg = resolve_forg(normalized)
    args: dict[str, object] = {"period": period}
    if forg is not None:
        args["forg"] = forg

    check_action = TaskAction(
        action_id=f"act-{uuid.uuid4().hex[:8]}",
        action_type="check_month_end_close",
        skill_name="matrix_month_end_close_check",
        title=f"检查 {period} 月结条件",
        content=f"检查 {period} 是否满足结账条件，并列出阻塞项和处理建议",
        target=TaskTarget(target_type="self", name="我"),
        schedule=TaskSchedule(schedule_type="none", timezone=timezone_name),
        args=args,
        priority="normal",
        confidence=0.98,
        requires_confirmation=False,
        source_sentence=normalized,
        analysis_note="识别为 Matrix 月结条件只读检查，不执行结账、反结账、过账或凭证写入。",
    )

    tasks = [check_action]
    if any(term in normalized for term in TODO_TERMS):
        tasks.append(
            TaskAction(
                action_id=f"act-{uuid.uuid4().hex[:8]}",
                action_type="create_todo",
                skill_name="create_todo",
                title=f"创建 {period} 月结阻塞项处理待办",
                content=f"处理 {period} 月结检查中的阻塞项和警告项",
                target=TaskTarget(target_type="self", name="我"),
                schedule=TaskSchedule(schedule_type="none", timezone=timezone_name),
                args={},
                priority="normal",
                confidence=0.95,
                requires_confirmation=True,
                source_sentence=normalized,
                analysis_note="创建站内处理待办；不会自动执行结账或修改财务数据。",
            )
        )

    summary = "已识别月结条件检查"
    if len(tasks) > 1:
        summary += "和阻塞项处理待办创建"
    warnings = []
    if forg is None:
        warnings.append("未指定业务单元，Matrix 将按现有月结工作台规则返回基础资料提示。")
    return ParseResponse(
        trace_id=trace_id or f"trace-{uuid.uuid4().hex[:12]}",
        summary=summary,
        tasks=tasks,
        warnings=warnings,
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


def resolve_forg(text: str) -> int | None:
    match = re.search(r"(?:业务单元|组织|forg)\s*[=:：]?\s*(\d+)", text, re.IGNORECASE)
    return int(match.group(1)) if match else None


def now_in_timezone(timezone_name: str) -> datetime:
    try:
        return datetime.now(ZoneInfo(timezone_name))
    except (ZoneInfoNotFoundError, ValueError):
        return datetime.now(ZoneInfo("Asia/Shanghai"))
