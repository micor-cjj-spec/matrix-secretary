from functools import lru_cache

import httpx

from app.settings import get_settings


DEFAULT_SKILLS = [
    {
        "name": "reply_message",
        "displayName": "回复消息",
        "description": "用户要求收到某人消息后回复指定内容时使用。",
        "triggerKeywords": ["回复", "回一下", "回消息", "收到消息"],
        "actionType": "reply_message",
        "riskLevel": "HIGH",
        "requiresConfirmation": True,
        "inputSchema": {},
    },
    {
        "name": "send_message",
        "displayName": "发送消息",
        "description": "用户要求向个人、群、团队发送通知或消息时使用。",
        "triggerKeywords": ["发消息", "通知", "告诉", "转告", "群发"],
        "actionType": "send_message",
        "riskLevel": "HIGH",
        "requiresConfirmation": True,
        "inputSchema": {},
    },
    {
        "name": "send_email",
        "displayName": "发送邮件",
        "description": "用户要求发送、起草、转发邮件时使用。",
        "triggerKeywords": ["发邮件", "发送邮件", "邮件发给", "起草邮件"],
        "actionType": "send_email",
        "riskLevel": "HIGH",
        "requiresConfirmation": True,
        "inputSchema": {},
    },
    {
        "name": "reminder",
        "displayName": "创建提醒",
        "description": "用户要求在某个时间提醒自己或提醒处理某件事时使用。",
        "triggerKeywords": ["提醒我", "到点提醒", "定时提醒"],
        "actionType": "reminder",
        "riskLevel": "MEDIUM",
        "requiresConfirmation": False,
        "inputSchema": {},
    },
    {
        "name": "create_todo",
        "displayName": "创建待办",
        "description": "用户要求记录待办、创建任务、后续跟进或处理事项时使用。",
        "triggerKeywords": ["待办", "任务", "跟进", "处理"],
        "actionType": "create_todo",
        "riskLevel": "MEDIUM",
        "requiresConfirmation": False,
        "inputSchema": {},
    },
    {
        "name": "schedule_task",
        "displayName": "定时任务",
        "description": "用户要求每天、每周、每月等周期性执行某个任务时使用。",
        "triggerKeywords": ["每天", "每周", "每月", "定期"],
        "actionType": "schedule_task",
        "riskLevel": "MEDIUM",
        "requiresConfirmation": False,
        "inputSchema": {},
    },
]


@lru_cache(maxsize=1)
def get_cached_skills() -> list[dict]:
    settings = get_settings()
    try:
        with httpx.Client(timeout=2) as client:
            response = client.get(settings.skill_catalog_url)
            response.raise_for_status()
            data = response.json()
            skills = data.get("skills") or []
            if isinstance(skills, list) and skills:
                return skills
    except Exception:
        pass
    return DEFAULT_SKILLS


def render_skill_prompt(skills: list[dict] | None = None) -> str:
    skills = skills or get_cached_skills()
    lines = ["当前系统可用 Skills："]
    for idx, skill in enumerate(skills, start=1):
        keywords = "、".join(skill.get("triggerKeywords") or [])
        lines.extend(
            [
                f"{idx}. {skill.get('name')}",
                f"   displayName: {skill.get('displayName')}",
                f"   actionType: {skill.get('actionType')}",
                f"   description: {skill.get('description')}",
                f"   triggerKeywords: {keywords}",
                f"   riskLevel: {skill.get('riskLevel')}",
                f"   requiresConfirmation: {str(skill.get('requiresConfirmation')).lower()}",
                f"   inputSchema: {skill.get('inputSchema')}",
            ]
        )
    return "\n".join(lines)
