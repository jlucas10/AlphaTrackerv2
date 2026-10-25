from typing import Any

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.chat import build_chat_router
from app.config import Settings, get_settings
from app.llm import LLM, build_llm
from app.ratelimit import RateLimiter
from app.spring_client import SpringClient


def create_app(settings: Settings | None = None, *, spring: Any = None, llm: LLM | None = None,
               limiter: RateLimiter | None = None) -> FastAPI:
    # A factory (rather than a module-level app built from global settings) so
    # tests can build an app with exactly the configuration - and fake Spring /
    # fake model - they want.
    settings = settings or get_settings()
    spring = spring or SpringClient(settings.spring_api_base_url)
    app = FastAPI(title="AlphaTracker Assistant")

    # The React app calls this service directly with the user's JWT. CORS is
    # configured here, centrally, like SecurityConfiguration does on the Spring
    # side: any localhost port for local dev, plus whatever production origins
    # CORS_ALLOWED_ORIGINS lists.
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_origin_regex=r"http://localhost:\d+",
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
        # Browsers hide every response header except a short safe list from page JavaScript unless
        # the server names it here. Without this the chat panel can see a 429 but never read how long
        # to wait; curl and the Python tests can't show the problem, only a real browser can.
        expose_headers=["Retry-After"],
    )

    # The model client is built on first use, not at startup: /health and the
    # tests work without a key, and a missing key becomes a clear 503 on /chat.
    cache: dict[str, LLM] = {}

    def get_llm() -> LLM:
        if llm is not None:
            return llm
        if "llm" not in cache:
            cache["llm"] = build_llm(settings)
        return cache["llm"]

    # One limiter for the whole process: its counts must be shared by every request thread.
    limiter = limiter or RateLimiter(settings.rate_limit_per_minute, settings.rate_limit_per_day,
                                     settings.rate_limit_global_per_day)
    app.include_router(build_chat_router(settings, spring, get_llm, limiter))

    @app.get("/health")
    def health() -> dict[str, object]:
        # Reports whether a key is configured - never the key itself - so you
        # can check your .env is being picked up without opening a chat.
        return {"status": "ok", "gemini_configured": bool(settings.gemini_api_key)}

    return app


app = create_app()
