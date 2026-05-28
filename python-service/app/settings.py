import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from dotenv import dotenv_values, load_dotenv


BASE_DIR = Path(__file__).resolve().parents[1]
ENV_FILE = BASE_DIR / ".env"
load_dotenv(ENV_FILE)


@dataclass(frozen=True)
class Settings:
    openrouter_api_key: str | None = None
    openrouter_base_url: str = "https://openrouter.ai/api/v1"
    openrouter_model: str = "deepseek/deepseek-v4-flash:free"
    app_url: str = "http://127.0.0.1:10002"
    app_title: str = "AI Secretary Demo"


@lru_cache
def get_settings() -> Settings:
    env_file_values = dotenv_values(ENV_FILE)
    api_key = first_valid_api_key(
        os.getenv("OPENROUTER_API_KEY"),
        env_file_values.get("OPENROUTER_API_KEY"),
    )
    return Settings(
        openrouter_api_key=api_key,
        openrouter_base_url=os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
        openrouter_model=os.getenv("OPENROUTER_MODEL", "deepseek/deepseek-v4-flash:free"),
        app_url=os.getenv("APP_URL", "http://127.0.0.1:10002"),
        app_title=os.getenv("APP_TITLE", "AI Secretary Demo"),
    )


def first_valid_api_key(*values: str | None) -> str | None:
    for value in values:
        normalized = normalize_api_key(value)
        if normalized:
            return normalized
    return None


def normalize_api_key(value: str | None) -> str | None:
    if not value:
        return None
    stripped = value.strip()
    if not stripped or stripped.startswith("replace-with-") or stripped in {"your-key", "你的新 OpenRouter Key"}:
        return None
    return stripped
