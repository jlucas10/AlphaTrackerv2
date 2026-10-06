from functools import lru_cache
from zoneinfo import ZoneInfo

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Configuration, read from environment variables (and a local .env file).

    The same code runs locally and on Railway: locally the values come from
    alphatracker-assistant/.env (git-ignored), in production from the platform's
    variables. Nothing secret has a default here.
    """

    # env_ignore_empty: a line like `TIMEZONE=` in .env means "not set", so the
    # default below applies instead of an empty string breaking startup.
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_ignore_empty=True,
        extra="ignore",
    )

    # No default and not required at startup: /health and the tests work
    # without it. It is checked when the LLM client is actually built.
    gemini_api_key: str | None = None
    gemini_model: str | None = None

    # Where the Spring API lives. The assistant only ever sends GET requests
    # here, forwarding the caller's JWT.
    spring_api_base_url: str = "http://localhost:8080/api/v1"

    # Defines what "today" / "this month" mean when a period is resolved.
    # Trade dates are stored without a timezone, so one has to be chosen.
    timezone: str = "America/Los_Angeles"

    # Comma-separated extra origins (e.g. the Vercel URL in production).
    # Any http://localhost:<port> is always allowed for local development.
    cors_allowed_origins: str = ""

    @field_validator("timezone")
    @classmethod
    def _timezone_must_exist(cls, value: str) -> str:
        ZoneInfo(value)  # raises on an unknown name, so a typo fails at startup
        return value

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_allowed_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
