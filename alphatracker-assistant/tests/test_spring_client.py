import logging

import httpx
import pytest

from app.spring_client import (
    SpringAuthError,
    SpringClient,
    SpringToolError,
    SpringUnavailable,
)

TOKEN = "header.payload.SECRET-SIGNATURE"


def client_for(handler) -> SpringClient:
    return SpringClient("http://spring.test/api/v1", transport=httpx.MockTransport(handler))


def test_forwards_jwt_on_a_get_and_drops_unset_params():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["method"] = request.method
        seen["url"] = str(request.url)
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={"ok": True})

    result = client_for(handler).get(
        "/analytics/summary", TOKEN, {"accountId": 5, "from": "2026-09-01", "to": None}
    )

    assert result == {"ok": True}
    assert seen["method"] == "GET"
    assert seen["auth"] == f"Bearer {TOKEN}"
    assert seen["url"].startswith("http://spring.test/api/v1/analytics/summary?")
    assert "accountId=5" in seen["url"] and "from=2026-09-01" in seen["url"]
    assert "to=" not in seen["url"]  # None must not become the string "None"


@pytest.mark.parametrize(
    "path",
    [
        "/trades/5",             # a real endpoint, just not one the assistant may read
        "/auth/authenticate",    # credentials endpoint
        "/analytics/../trades",  # path traversal back out of an allowed prefix
        "http://evil.test/x",    # an absolute URL must not redirect the call elsewhere
    ],
)
def test_paths_outside_the_allowlist_never_reach_the_network(path):
    def handler(request: httpx.Request) -> httpx.Response:
        raise AssertionError("a disallowed path must not make a request")

    with pytest.raises(ValueError):
        client_for(handler).get(path, TOKEN)


def test_error_responses_map_to_three_distinct_outcomes():
    def respond(status, **kwargs):
        return client_for(lambda r: httpx.Response(status, **kwargs)).get("/accounts", TOKEN)

    # Empty-body 403 (and 401): the token itself is unusable -> end the request.
    with pytest.raises(SpringAuthError):
        respond(403)
    with pytest.raises(SpringAuthError):
        respond(401)

    # JSON-body 403 / 400: Spring understood and refused -> goes back to the model.
    with pytest.raises(SpringToolError) as forbidden:
        respond(403, json={"message": "Unauthorized access: account not found or not yours."})
    assert forbidden.value.status == 403
    assert "not yours" in forbidden.value.message
    with pytest.raises(SpringToolError) as bad:
        respond(400, json={"message": "'by' must be one of: instrument, setup, rating."})
    assert bad.value.status == 400

    # 5xx: Spring is the problem, not the request.
    with pytest.raises(SpringUnavailable):
        respond(500, text="boom")


def test_token_never_appears_in_errors_or_logs(caplog):
    def exploding(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    errors = []
    with caplog.at_level(logging.DEBUG):
        for handler in (exploding, lambda r: httpx.Response(500, text="boom"), lambda r: httpx.Response(403)):
            try:
                client_for(handler).get("/accounts", TOKEN)
            except Exception as exc:  # noqa: BLE001 - collecting every kind on purpose
                errors.append(exc)

    assert len(errors) == 3
    for exc in errors:
        assert "SECRET-SIGNATURE" not in f"{exc} {exc!r}"
    assert "SECRET-SIGNATURE" not in caplog.text
