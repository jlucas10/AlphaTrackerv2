from typing import Any

import httpx

# The only Spring endpoints the assistant may ever call. Everything is a
# read-only GET, and the path must match EXACTLY - so there is no way to reach
# a write endpoint, a path with an id in it, or anything outside /api/v1 by
# passing a clever string. Tool code (tools.py) is the only caller.
ALLOWED_PATHS = frozenset(
    {
        "/accounts",
        "/analytics/summary",
        "/analytics/breakdown",
        "/analytics/discipline",
        "/journal",
    }
)


class SpringAuthError(Exception):
    """The caller's JWT is missing, malformed, expired, or for an unknown user.

    Spring answers all of these with a 403 that has an EMPTY body (verified
    against a live server, including a correctly-signed expired token), so the
    empty body is the signal. Not something the model can fix: the request must
    end and the user must log in again.
    """


class SpringToolError(Exception):
    """Spring understood the request and refused it (a 4xx with a JSON message).

    Examples: asking for another trader's accountId (403 "not yours"), a bad
    `by` value (400). These go back to the model as a normal tool result so it
    can explain or retry, instead of crashing the conversation.
    """

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


class SpringUnavailable(Exception):
    """Spring could not be reached, timed out, or returned a 5xx."""


class SpringClient:
    # Deliberately no post/put/patch/delete method: read-only is enforced by
    # the shape of this class, not just by convention.

    def __init__(
        self,
        base_url: str,
        *,
        transport: httpx.BaseTransport | None = None,
        timeout: float = 10.0,
    ):
        # The JWT is NOT stored here (no default headers). It arrives with each
        # call and lives only for that request, so one client can serve many
        # users without any chance of one user's token reaching another's call.
        self._client = httpx.Client(
            base_url=base_url.rstrip("/"),
            transport=transport,
            timeout=httpx.Timeout(timeout, connect=3.0),
        )

    def get(self, path: str, token: str, params: dict[str, Any] | None = None) -> Any:
        if path not in ALLOWED_PATHS:
            raise ValueError(f"Path not allowed: {path!r}")

        # None means "not provided"; sending it would become the literal string "None".
        query = {k: v for k, v in (params or {}).items() if v is not None}

        try:
            response = self._client.get(
                path, params=query, headers={"Authorization": f"Bearer {token}"}
            )
        except httpx.RequestError as exc:
            # Only the exception TYPE is surfaced: httpx messages can echo
            # request details, and the token must never end up in an error.
            raise SpringUnavailable(f"Spring API unreachable ({type(exc).__name__}).") from None

        return self._interpret(response)

    @staticmethod
    def _interpret(response: httpx.Response) -> Any:
        status = response.status_code
        if status == 200:
            return response.json()
        if status == 401:
            raise SpringAuthError()

        body = _json_or_none(response)
        message = _extract_message(body)

        # 403 splits in two. Empty body = Spring's security layer rejected the
        # token. A JSON body = our own SecurityException handler: the token was
        # fine but the resource belongs to someone else.
        if status == 403:
            if message is None:
                raise SpringAuthError()
            raise SpringToolError(status, message)

        if 400 <= status < 500:
            raise SpringToolError(status, message or f"Request rejected (HTTP {status}).")

        raise SpringUnavailable(f"Spring API error (HTTP {status}).")


def _json_or_none(response: httpx.Response) -> Any:
    try:
        return response.json()
    except ValueError:
        return None


def _extract_message(body: Any) -> str | None:
    if isinstance(body, dict):
        value = body.get("message") or body.get("error")
        if isinstance(value, str) and value:
            return value
    return None
