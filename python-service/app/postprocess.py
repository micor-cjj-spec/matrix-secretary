import re

from app.schemas import ActionType, ParseResponse, ScheduleType, TaskAction, TaskSchedule, TaskTarget


VERB_HINTS = [
    "确认",
    "整理",
    "提交",
    "同步",
    "复盘",
    "说明",
    "发送",
    "回复",
    "提醒",
    "通知",
    "准备",
    "更新",
    "完成",
    "处理",
    "审批",
    "盖章",
    "检查",
    "联系",
    "电话",
    "催",
    "查看",
    "记录",
    "准备",
    "打开",
]

TARGET_SUFFIXES = [
    "群",
    "小组",
    "团队",
    "部门",
    "公司",
    "中心",
    "办公室",
    "委员会",
]

PERSON_SUFFIXES = [
    "总",
    "经理",
    "主管",
    "负责人",
    "老师",
    "同学",
    "HR",
    "hr",
]

TASK_SUFFIXES = [
    "一下",
    "一次",
    "任务",
    "进度",
    "周报",
    "日报",
    "月报",
    "流程",
    "申请",
    "记录",
    "文档",
    "材料",
    "清单",
]

PRONOUN_TARGETS = [
    "他",
    "她",
    "他们",
    "她们",
    "对方",
    "有人",
    "那个人",
    "这些人",
    "相关人员",
    "大家",
    "一次",
]

CONDITION_ACTION_NAMES = {
    "提交": "未提交",
    "回复": "未回复",
    "确认": "未确认",
    "处理": "未处理",
    "完成": "未完成",
}

META_PHRASES = [
    "帮我把下面这段",
    "拆成可执行任务",
    "自然语言拆成",
    "发送前必须让我确认",
    "发送前需要我确认",
    "都需要保留让我确认",
    "不要自动回复",
    "只提醒我人工处理",
]


def normalize_parse_response(
    response: ParseResponse,
    parser_source: str | None = None,
    parse_warning: str | None = None,
) -> ParseResponse:
    normalized_tasks = []
    for task in response.tasks:
        normalized = normalize_task(task, parser_source, parse_warning)
        if should_keep_task(normalized):
            normalized_tasks.append(normalized)
    return response.model_copy(update={"tasks": normalized_tasks})


def normalize_task(
    task: TaskAction,
    parser_source: str | None = None,
    parse_warning: str | None = None,
) -> TaskAction:
    source = task.source_sentence or ""
    action_type = normalize_action_type(task, source)
    working_task = task.model_copy(update={"action_type": action_type})
    schedule = normalize_schedule(working_task.schedule, source)
    working_task = working_task.model_copy(update={"schedule": schedule})
    target = normalize_target(working_task, source)
    content = normalize_content(working_task, source, target)
    title = normalize_title(working_task, target, content)
    analysis_note = build_analysis_note(working_task, target, parser_source, parse_warning)
    return working_task.model_copy(
        update={
            "target": target,
            "content": content,
            "title": title,
            "schedule": schedule,
            "analysis_note": analysis_note,
        }
    )


def normalize_action_type(task: TaskAction, source: str) -> ActionType:
    target_name = task.target.name or ""
    if task.action_type == ActionType.REPLY_MESSAGE and is_invalid_target_name(target_name):
        if "提醒" in source or "提醒" in task.content:
            return ActionType.REMINDER
    return task.action_type


def normalize_schedule(schedule: TaskSchedule, source: str) -> TaskSchedule:
    if schedule.schedule_type != ScheduleType.NONE or schedule.original_text:
        return schedule
    if is_conditional_trigger(source):
        return schedule.model_copy(update={"original_text": "条件触发/待确认"})
    return schedule


def normalize_target(task: TaskAction, source: str) -> TaskTarget:
    if task.action_type == ActionType.SEND_EMAIL:
        email_target = find_email_target(source)
        if email_target:
            return TaskTarget(target_type="user", name=email_target, address=task.target.address)

    if task.action_type == ActionType.SEND_MESSAGE:
        group_target = find_group_target(source)
        if group_target:
            return TaskTarget(target_type="group", name=group_target, address=task.target.address)

    dynamic_target = find_dynamic_target(source, task.target.name)
    if dynamic_target:
        return dynamic_target

    if task.action_type in {ActionType.SEND_MESSAGE, ActionType.REPLY_MESSAGE, ActionType.REMINDER, ActionType.CREATE_TODO}:
        person = find_person_after_directive(source)
        if person and looks_like_real_target(person):
            return TaskTarget(target_type="user", name=person, address=task.target.address)

    if is_self_task(task, source):
        return TaskTarget(target_type="self", name="我", address=task.target.address)

    target_name = task.target.name or ""
    if target_name and is_invalid_target_name(target_name):
        person = find_person_after_directive(source)
        if person and person != target_name and looks_like_real_target(person):
            return TaskTarget(target_type="user", name=person, address=task.target.address)
        return TaskTarget(target_type="unknown", name=None, address=task.target.address)

    if task.target.target_type == "user" and looks_like_content(target_name):
        person = find_person_after_directive(source)
        if person and looks_like_real_target(person):
            return TaskTarget(target_type="user", name=person, address=task.target.address)
        return TaskTarget(target_type="unknown", name=None, address=task.target.address)

    return task.target


def normalize_content(task: TaskAction, source: str, target: TaskTarget) -> str:
    original_target_name = task.target.name or ""
    if not task.content and is_invalid_target_name(original_target_name):
        return cleanup_content(original_target_name)

    if task.content:
        if is_invalid_target_name(original_target_name):
            cleaned_target = cleanup_content(original_target_name)
            if cleaned_target and cleaned_target in task.content:
                return cleanup_dynamic_pronoun_content(task.content) if target.target_type == "dynamic" else task.content
            if task.content.startswith(("如果", "但如果", "除非")):
                content = cleanup_content(f"{cleaned_target}，{task.content}")
                return cleanup_dynamic_pronoun_content(content) if target.target_type == "dynamic" else content
            if is_meta_content(task.content):
                return cleaned_target
        if is_meta_content(task.content) and is_invalid_target_name(original_target_name):
            return cleanup_content(original_target_name)
        return cleanup_dynamic_pronoun_content(task.content) if target.target_type == "dynamic" else task.content

    if target.name:
        pattern = rf"(?:给|让|提醒|通知|告诉)(?:我)?{re.escape(target.name)}(.+)$"
        match = re.search(pattern, source)
        if match:
            return cleanup_content(match.group(1))

    if task.action_type == ActionType.REMINDER:
        reminder = re.search(r"(?:提醒我|提醒)(.+)$", source)
        if reminder:
            return cleanup_content(reminder.group(1))

    return task.content


def normalize_title(task: TaskAction, target: TaskTarget, content: str) -> str:
    action_name = {
        ActionType.REPLY_MESSAGE: "回复消息",
        ActionType.SEND_MESSAGE: "发送消息",
        ActionType.SEND_EMAIL: "发送邮件",
        ActionType.REMINDER: "提醒事项",
        ActionType.CREATE_TODO: "创建待办",
        ActionType.SCHEDULE_TASK: "定时任务",
    }[task.action_type]
    schedule_text = task.schedule.original_text
    target_text = target.name or target.address
    if target.target_type == "self":
        target_text = None
    if target_text:
        base = f"{action_name}: {target_text}"
    elif content:
        base = f"{action_name}: {content[:20]}"
    else:
        base = action_name
    return f"{base}（{schedule_text}）" if schedule_text else base


def is_conditional_trigger(source: str) -> bool:
    return bool(re.search(r"(如果|若|当|收到|一旦).{0,80}(就|再|则|时|后)", source))


def build_analysis_note(
    task: TaskAction,
    target: TaskTarget,
    parser_source: str | None,
    parse_warning: str | None,
) -> str | None:
    notes = []
    if task.analysis_note:
        notes.append(task.analysis_note)
    if parser_source == "fallback":
        notes.append("LLM 未产出可用结构，当前任务由规则 fallback 解析，复杂跨句语义需要人工确认。")
    elif parser_source == "llm":
        notes.append("LLM 语义解析结果，已经过字段边界规则校验。")
    if parse_warning:
        notes.append(parse_warning)
    original_target = task.target.name or task.target.address
    normalized_target = target.name or target.address
    if original_target and original_target != normalized_target:
        notes.append(f"对象已修正：原对象“{original_target}”不像明确接收对象。")
    if target.target_type == "unknown":
        notes.append("未识别到明确接收对象。")
    if target.target_type == "self":
        notes.append("识别为个人提醒，目标归为用户本人。")
    if target.target_type == "dynamic":
        notes.append("对象由条件/代词推导，执行时需按上下文筛选。")
    if task.action_type in {ActionType.SEND_EMAIL, ActionType.SEND_MESSAGE, ActionType.REPLY_MESSAGE} and target.target_type == "unknown":
        notes.append("发送/回复类任务缺少明确收件人，需要确认后执行。")
    if task.confidence < 0.75:
        notes.append("模型置信度偏低。")
    return merge_notes(notes)


def merge_notes(notes: list[str]) -> str | None:
    merged = []
    for note in notes:
        if not note:
            continue
        cleaned = note.strip()
        if cleaned and cleaned not in merged:
            merged.append(cleaned)
    if not merged:
        return None
    return " ".join(merged)[:500]


def find_email_target(source: str) -> str | None:
    patterns = [
        r"发给(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,8})",
        r"给(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,8})发(?:一封)?(?:邮件|邮箱)",
        r"邮件(?:发|发送)给(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,8})",
    ]
    return first_match(source, patterns)


def find_group_target(source: str) -> str | None:
    patterns = [
        r"给(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,12}(?:群|小组|团队|部门))发(?:消息|通知)",
        r"通知(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,12}(?:群|小组|团队|部门))",
        r"(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,12}(?:群|小组|团队|部门))(?:发消息|通知|提交)",
    ]
    return first_match(source, patterns)


def find_person_after_directive(source: str) -> str | None:
    patterns = [
        r"提醒我给(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,4})(?=确认|整理|提交|同步|复盘|说明|发送|回复|处理|审批|盖章)",
        r"联系(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,4}?)(?=一起|共同|协助|排查|确认|处理)",
        r"(?:给|让|提醒|通知|告诉)(?:我)?(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,4})(?=确认|整理|提交|同步|复盘|说明|发送|回复|处理|审批|盖章)",
        r"收到(?P<name>[\u4e00-\u9fa5A-Za-z0-9_-]{2,8})的消息",
    ]
    return first_match(source, patterns)


def is_self_task(task: TaskAction, source: str) -> bool:
    if task.action_type in {ActionType.REMINDER, ActionType.CREATE_TODO, ActionType.SCHEDULE_TASK}:
        return bool(re.search(r"(提醒我|我自己|给自己|自己|本人)", source))
    return False


def find_dynamic_target(source: str, target_name: str | None = None) -> TaskTarget | None:
    if not source:
        return None
    candidate = target_name or ""
    has_dynamic_trigger = has_dynamic_reference(source)
    if candidate in {"大家", "一次"} and not has_dynamic_trigger:
        return None
    if not has_dynamic_trigger and not is_pronoun_like_target(candidate):
        return None

    action = find_condition_action(source)
    if not action:
        return TaskTarget(target_type="dynamic", name="符合条件的人") if is_pronoun_like_target(candidate) else None

    topic = find_condition_topic(source, action)
    action_name = CONDITION_ACTION_NAMES[action]
    name = f"{action_name}{topic}的人" if topic else f"{action_name}的人"
    return TaskTarget(target_type="dynamic", name=name)


def has_dynamic_reference(source: str) -> bool:
    return bool(re.search(r"(提醒|通知|告诉|回复|联系|找|给)(他|她|他们|她们|对方|这些人|相关人员)(一次|一下)?", source))


def find_condition_action(source: str) -> str | None:
    for action in CONDITION_ACTION_NAMES:
        if re.search(rf"(?:没|没有|未|还没|尚未).{{0,8}}{action}|{action}.{{0,8}}(?:没|没有|未|还没|尚未)", source):
            return action
    return None


def find_condition_topic(source: str, action: str) -> str | None:
    after_action = re.search(rf"{action}(?P<topic>[\u4e00-\u9fa5A-Za-z0-9_-]{{1,12}})?", source)
    if after_action:
        topic = cleanup_topic(after_action.group("topic") or "")
        if topic:
            return topic
    before_action = re.search(rf"(?P<topic>[\u4e00-\u9fa5A-Za-z0-9_-]{{1,12}}){action}", source)
    if before_action:
        raw_topic = before_action.group("topic") or ""
        topic = cleanup_topic(raw_topic)
        if topic and not re.search(r"(如果|有人|没人|没有人|谁|还没|尚未|没有|未|没|到.+点)", raw_topic):
            return topic
    return None


def cleanup_topic(value: str) -> str:
    value = re.split(r"(再|就|时|的时候|，|,|。|；|;|提醒|通知|告诉|回复)", value)[0]
    value = re.sub(r"^(他|她|他们|她们|对方|有人|谁|还没|没有|没|未|尚未)", "", value)
    value = re.sub(r"(的人|一次|一下)$", "", value)
    return value.strip(" ，,。；;：:")


def first_match(source: str, patterns: list[str]) -> str | None:
    for pattern in patterns:
        match = re.search(pattern, source)
        if match:
            return match.group("name")
    return None


def looks_like_content(value: str) -> bool:
    return looks_like_action_phrase(value)


def is_invalid_target_name(value: str) -> bool:
    if not value:
        return False
    if is_pronoun_like_target(value):
        return True
    if looks_like_real_target(value):
        return False
    if looks_like_action_phrase(value):
        return True
    if any(value.endswith(suffix) for suffix in TASK_SUFFIXES):
        return True
    return False


def looks_like_real_target(value: str) -> bool:
    normalized = value.strip()
    if not normalized:
        return False
    if re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", normalized):
        return True
    if re.fullmatch(r"1[3-9]\d{9}", normalized):
        return True
    if re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{1,31}", normalized):
        return True
    if has_sentence_punctuation(normalized):
        return False
    if is_pronoun_like_target(normalized):
        return False
    if looks_like_action_phrase(normalized):
        return False
    if any(normalized.endswith(suffix) for suffix in TARGET_SUFFIXES):
        return 2 <= len(normalized) <= 12
    if any(normalized.endswith(suffix) for suffix in PERSON_SUFFIXES):
        return 2 <= len(normalized) <= 8
    if re.fullmatch(r"[\u4e00-\u9fa5]{2,3}", normalized):
        return True
    return False


def looks_like_action_phrase(value: str) -> bool:
    normalized = value.strip()
    if not normalized:
        return False
    if any(normalized.startswith(hint) for hint in VERB_HINTS):
        return True
    if any(hint in normalized for hint in VERB_HINTS):
        return len(normalized) > 3 or any(normalized.endswith(suffix) for suffix in TASK_SUFFIXES)
    return False


def has_sentence_punctuation(value: str) -> bool:
    return bool(re.search(r"[，,。；;：:、“”\"'（）()]", value))


def is_pronoun_like_target(value: str) -> bool:
    normalized = (value or "").strip()
    if not normalized:
        return False
    return any(normalized == word or normalized.startswith(word) for word in PRONOUN_TARGETS)


def cleanup_dynamic_pronoun_content(value: str) -> str:
    text = value
    text = re.sub(r"(提醒|通知|告诉)(他|她|他们|她们|对方|这些人)(一次|一下)?", r"\1\3", text)
    text = re.sub(r"(单独提醒)(他|她|他们|她们|对方|这些人)(一次|一下)?", r"\1\3", text)
    return text.strip(" ，,。；;：:")


def is_meta_content(value: str) -> bool:
    return any(phrase in value for phrase in META_PHRASES)


def should_keep_task(task: TaskAction) -> bool:
    source = task.source_sentence or ""
    content = task.content or ""
    title = task.title or ""
    if task.action_type == ActionType.CREATE_TODO and any(phrase in content + source for phrase in META_PHRASES[:3]):
        return False
    if task.action_type in {ActionType.SEND_EMAIL, ActionType.SEND_MESSAGE, ActionType.REPLY_MESSAGE}:
        if (task.target.name or "") == "确认" and "确认" in content + source:
            return False
        if not task.target.name and is_meta_content(content + source):
            return False
    if any(phrase in title + content for phrase in ["发送邮件: 确认", "发送消息: 确认"]):
        return False
    return True


def cleanup_content(value: str) -> str:
    value = value.strip(" ，,。；;：:")
    value = re.sub(r"^(发邮件|发消息|回复|通知|提醒|让|给)", "", value)
    return value.strip(" ，,。；;：:")
