from functools import lru_cache
from zoneinfo import ZoneInfo

from pydantic import Field, field_validator
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
    # A fast Flash-class model for tool calling. Verified available to this
    # project's key by listing models from the API. Override with GEMINI_MODEL.
    gemini_model: str = "gemini-3.8-flash"

    # Where the Spring API lives. The assistant only ever sends GET requests
    # here, forwarding the caller's JWT.
    spring_api_base_url: str = "http://localhost:8080/api/v1"

    # Defines what "today" / "this month" mean when a period is resolved.
    # Trade dates are stored without a timezone, so one has to be chosen.
    timezone: str = "America/Los_Angeles"

    # Chat limits (see app/ratelimit.py). Every admitted request can cost 1-3 model calls.
    # per-minute stops loops; per-day is each user's budget; global-per-day bounds total spend.
    rate_limit_per_minute: int = Field(default=10, ge=1)
    rate_limit_per_day: int = Field(default=100, ge=1)
    rate_limit_global_per_day: int = Field(default=1000, ge=1)

    # Comma-separated extra origins (e.g. the Vercel URL in production).
    # Any http://localhost:<port> is always allowed for local development.
    cors_allowed_origins: str = ""

    @field_validator("gemini_api_key", "gemini_model", "spring_api_base_url", "timezone", "cors_allowed_origins",
                     mode="before")
    @classmethod
    def _tidy_pasted_value(cls, value):
        """Hosting dashboards make it easy to paste a value with stray spaces, a trailing newline, or the
        quote marks from a shell example (this already broke S3_BUCKET once). None of those is ever part of a
        real key or URL, so they are removed instead of becoming a mystery failure."""
        if isinstance(value, str):
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1].strip()
        return value

    @field_validator("timezone")
    @classmethod
    def _timezone_must_exist(cls, value: str) -> str:
        ZoneInfo(value)  # raises on an unknown name, so a typo fails at startup
        return value

    @property
    def cors_origin_list(self) -> list[str]:
        # No trailing slash: a browser's Origin header never has one, so "https://x.app/" would never match
        # and every request from that site would be blocked.
        cleaned = (o.strip().strip("\"'").strip().rstrip("/") for o in self.cors_allowed_origins.split(","))
        return [o for o in cleaned if o]


@lru_cache
def get_settings() -> Settings:
    return Settings()
