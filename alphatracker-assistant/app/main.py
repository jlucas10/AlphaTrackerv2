from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import Settings, get_settings


def create_app(settings: Settings | None = None) -> FastAPI:
    # A factory (rather than a module-level app built from global settings) so
    # tests can build an app with exactly the configuration they want.
    settings = settings or get_settings()
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
    )

    @app.get("/health")
    def health() -> dict[str, object]:
        # Reports whether a key is configured - never the key itself - so you
        # can check your .env is being picked up without opening a chat.
        return {"status": "ok", "gemini_configured": bool(settings.gemini_api_key)}

    return app


app = create_app()
