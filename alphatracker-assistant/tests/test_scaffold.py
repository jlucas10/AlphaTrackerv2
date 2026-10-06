import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def test_health_reports_key_status_without_leaking_it():
    # _env_file=None: tests never read a real .env, so they pass the same on any machine.
    configured = TestClient(create_app(Settings(_env_file=None, gemini_api_key="super-secret")))
    missing = TestClient(create_app(Settings(_env_file=None, gemini_api_key=None)))

    body = configured.get("/health")
    assert body.json() == {"status": "ok", "gemini_configured": True}
    assert "super-secret" not in body.text
    assert missing.get("/health").json()["gemini_configured"] is False


def test_blank_env_values_fall_back_to_defaults(monkeypatch):
    # `TIMEZONE=` left empty in .env must mean "unset", not break startup.
    monkeypatch.setenv("TIMEZONE", "")
    monkeypatch.setenv("SPRING_API_BASE_URL", "")

    settings = Settings(_env_file=None)

    assert settings.timezone == "America/Los_Angeles"
    assert settings.spring_api_base_url == "http://localhost:8080/api/v1"


def test_unknown_timezone_fails_at_startup():
    with pytest.raises(Exception):
        Settings(_env_file=None, timezone="Not/AZone")
