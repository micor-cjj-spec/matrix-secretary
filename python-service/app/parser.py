import re
import uuid
from datetime import datetime, timedelta, timezone

from app.schemas import (
    ActionType,
    ParseResponse,
    ScheduleType,
    TaskAction,
    TaskSchedule,
    TaskTarget,
)


CN_TZ = timezone(timedelta(hours=8))


def split_sentences(text: str) -> list[str]:
    parts = re.split(r"[。；;！!？?\n]+|(?:另外|然后|并且|同时)", text)
    return [part.strip(" ，,") for part in parts if part.strip(" ，,")]


def parse_text(
    text: str,
    timezone_name: str,
    trace_id: str | None = None,
    allow_complex: bool = True,
) -> ParseResponse:
    if not allow_complex and is_complex_for_fallback(text):
        return ParseResponse(
            trace_id=trace_id or f"trace-{uuid.uuid4().hex[:12]}",
            summary="复杂文本需要 LLM 解析，fallback 未生成任务",
            tasks=[],
            warnings=[
                "文本包含多任务、条件或长上下文；规则 fallback 为避免误拆，未生成任务。请修复 LLM 配置后重试。",
            ],
        )

    sentences = split_sentences(text)
    tasks = []
    warnings = []

    for sentence in sentences:
        task = parse_sentence(sentence, timezone_name)
        if task:
            tasks.append(task)
        else:
            warnings.append(f"未识别出明确动作: {sentence}")

    return ParseResponse(
        trace_id=trace_id or f"trace-{uuid.uuid4().hex[:12]}",
        summary=f"共识别 {len(tasks)} 个可执行任务",
        tasks=tasks,
        warnings=warnings,
    )


def is_complex_for_fallback(text: str) -> bool:
    stripped = text.strip()
    if len(stripped) > 180:
        return True

    split_markers = re.findall(r"[。；;\n]|另外|然后|并且|同时", stripped)
    if len(split_markers) >= 2:
        return True

    action_markers = re.findall(r"发邮件|发送邮件|发消息|通知|回复|提醒|待办|每周|每天|如果|收到", stripped)
    if len(action_markers) >= 3:
        return True

    if re.search(r"(如果|若|当|收到|一旦).{0,80}(就|再|则|后|时)", stripped):
        return True

    return False


def parse_sentence(sentence: str, timezone_name: str) -> TaskAction | None:
    action_type = infer_action_type(sentence)
    if not action_type:
        return None

    schedule = infer_schedule(sentence, timezone_name)
    target = infer_target(sentence, action_type)
    content = infer_content(sentence)
    title = infer_title(action_type, target, content, schedule)

    return TaskAction(
        action_id=f"act-{uuid.uuid4().hex[:8]}",
        action_type=action_type,
        title=title,
        content=content,
        target=target,
        schedule=schedule,
        priority="high" if any(word in sentence for word in ["紧急", "尽快", "马上", "立刻"]) else "normal",
        confidence=0.82 if schedule.schedule_type != ScheduleType.NONE else 0.72,
        requires_confirmation=action_type in {ActionType.SEND_EMAIL, ActionType.SEND_MESSAGE, ActionType.REPLY_MESSAGE},
        source_sentence=sentence,
    )


def infer_action_type(sentence: str) -> ActionType | None:
    if any(word in sentence for word in ["回复", "回一下", "回消息"]):
        return ActionType.REPLY_MESSAGE
    if any(word in sentence for word in ["发邮件", "发送邮件", "邮件"]):
        return ActionType.SEND_EMAIL
    if any(word in sentence for word in ["发消息", "通知", "告诉", "转告", "群发"]):
        return ActionType.SEND_MESSAGE
    if any(word in sentence for word in ["提醒", "定时", "到点"]):
        return ActionType.REMINDER
    if any(word in sentence for word in ["待办", "任务", "跟进", "处理"]):
        return ActionType.CREATE_TODO
    if any(word in sentence for word in ["每周", "每天", "每月", "定期"]):
        return ActionType.SCHEDULE_TASK
    return None


def infer_schedule(sentence: str, timezone_name: str) -> TaskSchedule:
    recurring = infer_recurring_cron(sentence)
    if recurring:
        return TaskSchedule(
            schedule_type=ScheduleType.RECURRING,
            original_text=recurring["original_text"],
            cron=recurring["cron"],
            timezone=timezone_name,
        )

    run_at, original_text = infer_once_time(sentence)
    if run_at:
        return TaskSchedule(
            schedule_type=ScheduleType.ONCE,
            original_text=original_text,
            run_at=run_at,
            timezone=timezone_name,
        )

    return TaskSchedule(schedule_type=ScheduleType.NONE, timezone=timezone_name)


def infer_recurring_cron(sentence: str) -> dict[str, str] | None:
    weekday_map = {
        "一": "MON",
        "二": "TUE",
        "三": "WED",
        "四": "THU",
        "五": "FRI",
        "六": "SAT",
        "日": "SUN",
        "天": "SUN",
    }
    match = re.search(r"每周([一二三四五六日天]).*?([上中下晚早]午)?([一二两三四五六七八九十\d]{1,3})点", sentence)
    if match:
        weekday = weekday_map[match.group(1)]
        hour = cn_number_to_int(match.group(3))
        period = match.group(2) or ""
        if period in ["下午", "晚上"] and hour < 12:
            hour += 12
        return {"original_text": match.group(0), "cron": f"0 0 {hour} ? * {weekday}"}

    match = re.search(r"每天.*?([上中下晚早]午)?([一二两三四五六七八九十\d]{1,3})点", sentence)
    if match:
        hour = cn_number_to_int(match.group(2))
        period = match.group(1) or ""
        if period in ["下午", "晚上"] and hour < 12:
            hour += 12
        return {"original_text": match.group(0), "cron": f"0 0 {hour} * * ?"}

    return None


def infer_once_time(sentence: str) -> tuple[str | None, str | None]:
    now = datetime.now(CN_TZ).replace(second=0, microsecond=0)
    day_offset = None
    day_text = None
    if "今天" in sentence:
        day_offset, day_text = 0, "今天"
    elif "明天" in sentence:
        day_offset, day_text = 1, "明天"
    elif "后天" in sentence:
        day_offset, day_text = 2, "后天"

    relative = re.search(r"(\d+|[一二两三四五六七八九十]+)\s*(分钟|小时|天)后", sentence)
    if relative:
        amount = cn_number_to_int(relative.group(1))
        unit = relative.group(2)
        delta = {"分钟": timedelta(minutes=amount), "小时": timedelta(hours=amount), "天": timedelta(days=amount)}[unit]
        return (now + delta).isoformat(), relative.group(0)

    if day_offset is None:
        return None, None

    time_match = re.search(r"([上中下晚早]午)?([一二两三四五六七八九十\d]{1,3})点(?:半|([一二两三四五六七八九十\d]{1,3})分)?", sentence)
    target = now + timedelta(days=day_offset)
    if time_match:
        period = time_match.group(1) or ""
        hour = cn_number_to_int(time_match.group(2))
        minute = 30 if "点半" in time_match.group(0) else cn_number_to_int(time_match.group(3) or "0")
        if period in ["下午", "晚上"] and hour < 12:
            hour += 12
        if period == "中午" and hour < 11:
            hour += 12
        target = target.replace(hour=hour, minute=minute)
        return target.isoformat(), f"{day_text}{time_match.group(0)}"

    return target.replace(hour=9, minute=0).isoformat(), day_text


def infer_target(sentence: str, action_type: ActionType) -> TaskTarget:
    email = re.search(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+", sentence)
    if email:
        return TaskTarget(target_type="email", address=email.group(0))

    clean_sentence = strip_time_words(sentence)

    reply_source = re.search(r"收到([\u4e00-\u9fa5A-Za-z0-9_-]{2,8})的消息", clean_sentence)
    if action_type == ActionType.REPLY_MESSAGE and reply_source:
        return TaskTarget(target_type="user", name=reply_source.group(1))

    target_sentence = re.sub(r"^(通知|提醒|告诉|转告|给)", "", clean_sentence)
    group = re.search(r"([\u4e00-\u9fa5A-Za-z0-9_-]{2,12}群)", target_sentence)
    if group:
        return TaskTarget(target_type="group", name=group.group(1))

    mail_target = re.search(r"给(?:我)?([\u4e00-\u9fa5A-Za-z0-9_-]{2,8})发邮件", clean_sentence)
    if mail_target:
        return TaskTarget(target_type="user", name=mail_target.group(1))

    person = re.search(r"(?:让|通知|提醒|告诉|回复|转告)(?:我)?([\u4e00-\u9fa5A-Za-z0-9_-]{2,8})", clean_sentence)
    if person:
        return TaskTarget(target_type="user", name=person.group(1).strip("发邮件消息：:，,"))

    if action_type == ActionType.SEND_EMAIL:
        return TaskTarget(target_type="email")
    return TaskTarget()


def infer_content(sentence: str) -> str:
    explicit = re.search(r"(?:内容是|内容为)(.+)$", sentence)
    if explicit:
        return explicit.group(1).strip(" ，,。")

    subject = re.search(r"主题(?:写|为|是)[“\"']?(?P<subject>.+?)[”\"']?[，,]", sentence)
    body = re.search(r"正文(?:说明|写|为|是)(?P<body>.+)$", sentence)
    if subject or body:
        parts = []
        if subject:
            parts.append(f"主题：{subject.group('subject').strip(' ，,。')}")
        if body:
            parts.append(f"正文：{body.group('body').strip(' ，,。')}")
        return "；".join(parts)

    colon = re.search(r"[:：](.+)$", sentence)
    if colon:
        return colon.group(1).strip(" ，,。")

    clean_sentence = strip_time_words(sentence)
    group_content = re.search(r"[\u4e00-\u9fa5A-Za-z0-9_-]{2,12}群(.+)$", clean_sentence)
    if group_content:
        return group_content.group(1).strip(" ，,。")

    content_match = re.search(r"(?:说|告诉.*?)(.+)$", sentence)
    if content_match:
        return content_match.group(1).strip(" ，,。")
    content = strip_time_words(sentence)
    content = re.sub(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+", "", content)
    content = re.sub(r"^(给|让|通知|提醒|告诉|回复|转告)(我)?[\u4e00-\u9fa5A-Za-z0-9_-]{0,12}", "", content)
    return content.strip(" ，,。")


def infer_title(action_type: ActionType, target: TaskTarget, content: str, schedule: TaskSchedule) -> str:
    action_name = {
        ActionType.REPLY_MESSAGE: "回复消息",
        ActionType.SEND_MESSAGE: "发送消息",
        ActionType.SEND_EMAIL: "发送邮件",
        ActionType.REMINDER: "提醒事项",
        ActionType.CREATE_TODO: "创建待办",
        ActionType.SCHEDULE_TASK: "定时任务",
    }[action_type]
    target_name = target.name or target.address or "未指定对象"
    suffix = f"（{schedule.original_text}）" if schedule.original_text else ""
    return f"{action_name}: {target_name}{suffix}" if content else f"{action_name}{suffix}"


def cn_number_to_int(value: str) -> int:
    if value.isdigit():
        return int(value)
    value = value.replace("两", "二")
    digits = {"零": 0, "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
    if value == "十":
        return 10
    if "十" in value:
        left, _, right = value.partition("十")
        return (digits.get(left, 1) * 10) + digits.get(right, 0)
    return digits.get(value, 0)


def strip_time_words(sentence: str) -> str:
    text = re.sub(r"(今天|明天|后天)", "", sentence)
    text = re.sub(r"每周[一二三四五六日天]", "", text)
    text = re.sub(r"每天", "", text)
    text = re.sub(r"([上中下晚早]午)?[一二两三四五六七八九十\d]{1,3}点(?:半|[一二两三四五六七八九十\d]{1,3}分)?", "", text)
    text = re.sub(r"(\d+|[一二两三四五六七八九十]+)\s*(分钟|小时|天)后", "", text)
    return text.strip(" ，,。")
