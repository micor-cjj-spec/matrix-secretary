from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field


class ActionType(str, Enum):
    REPLY_MESSAGE = "reply_message"
    SEND_MESSAGE = "send_message"
    SEND_EMAIL = "send_email"
    REMINDER = "reminder"
    CREATE_TODO = "create_todo"
    SCHEDULE_TASK = "schedule_task"


class ScheduleType(str, Enum):
    NONE = "none"
    ONCE = "once"
    RECURRING = "recurring"


class TaskTarget(BaseModel):
    target_type: Literal["user", "group", "email", "dynamic", "self", "unknown"] = "unknown"
    name: str | None = None
    address: str | None = None


class TaskSchedule(BaseModel):
    schedule_type: ScheduleType = ScheduleType.NONE
    original_text: str | None = None
    run_at: str | None = None
    cron: str | None = None
    timezone: str = "Asia/Shanghai"


class TaskAction(BaseModel):
    action_id: str
    action_type: ActionType
    title: str
    content: str
    target: TaskTarget = Field(default_factory=TaskTarget)
    schedule: TaskSchedule = Field(default_factory=TaskSchedule)
    priority: Literal["low", "normal", "high"] = "normal"
    confidence: float = Field(ge=0, le=1)
    requires_confirmation: bool = True
    source_sentence: str
    analysis_note: str | None = None


class ParseRequest(BaseModel):
    text: str = Field(min_length=1)
    timezone: str = "Asia/Shanghai"
    user_id: str | None = None
    trace_id: str | None = None


class ParseResponse(BaseModel):
    trace_id: str
    language: str = "zh-CN"
    summary: str
    tasks: list[TaskAction]
    warnings: list[str] = Field(default_factory=list)
