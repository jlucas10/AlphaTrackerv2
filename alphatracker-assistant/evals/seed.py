"""Load the eval dataset into a LOCAL Spring server, through its public endpoints.

Why this lives in `evals/` and not `app/`: the production Spring client is read-only
by construction (it has no POST). Seeding has to write, so it gets its own small
writer here, and the Dockerfile only copies `app/`, so write-capable code never
ships to Railway.

Safe to run repeatedly (idempotent): log in as a fixed eval trader (registering it
the first time), reuse its accounts by name (Spring has no delete-account endpoint),
delete its trades and recreate them (deleting rolls account balances back, so the
end state is always identical), and reset its journal.

    python -m evals.seed [--spring-url http://localhost:8080/api/v1]
"""

import argparse
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlparse

import httpx

from app.config import Settings
from evals.dataset import ACCOUNTS, JOURNAL, TRADES

EVAL_EMAIL = "eval-harness@example.test"
EVAL_PASSWORD = "Eval-harness-1!"  # not a secret: this user only ever exists on a developer's local DB
LOCAL_HOSTS = frozenset({"localhost", "127.0.0.1", "::1"})
JOURNAL_RESET_FROM, JOURNAL_RESET_TO = "2026-01-01", "2026-12-31"  # 365 days, inside the 366-day API limit


class SeedError(RuntimeError):
    pass


@dataclass(frozen=True)
class SeededEval:
    base_url: str
    token: str
    account_ids: dict[str, int]  # dataset key ("A"/"B") -> Spring account id


def assert_local(base_url: str) -> None:
    """Refuse anything but a localhost server.

    The hostname must EQUAL a local one (not merely contain it), so
    'localhost.evil.com' and 'http://x@localhost.evil.com' are rejected.
    Limit: a local Spring can still be configured (DATABASE_URL) to use a remote
    database, which no URL check can see. The seeded user is a separate
    @example.test account, easy to delete.
    """
    host = urlparse(base_url).hostname
    if host not in LOCAL_HOSTS:
        raise SeedError(
            f"Refusing to seed {base_url!r}: evals only write to a local Spring server "
            f"(host must be one of {sorted(LOCAL_HOSTS)})."
        )


def seed(base_url: str) -> SeededEval:
    assert_local(base_url)
    with httpx.Client(base_url=base_url.rstrip("/"), timeout=30) as http:
        token = _login_or_register(http)
        auth = {"Authorization": f"Bearer {token}"}
        account_ids = _ensure_accounts(http, auth)
        _wipe_trades(http, auth)
        _create_trades(http, auth, account_ids)
        _reset_journal(http, auth)
    return SeededEval(base_url, token, account_ids)


# ------------------------------------------------------------------ steps
def _login_or_register(http: httpx.Client) -> str:
    creds = {"email": EVAL_EMAIL, "password": EVAL_PASSWORD}
    response = http.post("/auth/authenticate", json=creds)
    if response.status_code != 200:  # first run: the eval trader doesn't exist yet
        response = _check(http.post("/auth/register", json={**creds, "firstName": "Eval", "lastName": "Harness"}),
                          "register eval user")
    return response.json()["token"]


def _ensure_accounts(http: httpx.Client, auth: dict) -> dict[str, int]:
    existing = {a["name"]: a["id"] for a in _check(http.get("/accounts", headers=auth), "list accounts").json()}
    ids: dict[str, int] = {}
    for key, spec in ACCOUNTS.items():
        if spec.name in existing:
            ids[key] = existing[spec.name]
            continue
        created = _check(http.post("/accounts", headers=auth, json={
            "name": spec.name, "firm": spec.firm, "accountType": spec.account_type,
            "startingBalance": spec.starting_balance, "maxDrawdown": spec.max_drawdown,
        }), f"create account {spec.name}")
        ids[key] = created.json()["id"]
    return ids


def _wipe_trades(http: httpx.Client, auth: dict) -> None:
    for trade in _check(http.get("/trades", headers=auth), "list trades").json():
        _check(http.delete(f"/trades/{trade['id']}", headers=auth), "delete trade")


def _create_trades(http: httpx.Client, auth: dict, account_ids: dict[str, int]) -> None:
    for t in TRADES:
        created = _check(http.post("/trades", headers=auth, json={
            "ticker": t.ticker, "direction": t.direction, "entryPrice": t.entry, "exitPrice": t.exit,
            "contracts": t.contracts, "followedPlan": t.followed_plan,
            "tradeDate": t.when.isoformat(timespec="seconds"), "accountId": account_ids[t.account],
        }), f"create trade {t.when}")
        if t.rating is not None or t.tags:  # rating and tags are set after creation, via PATCH
            _check(http.patch(f"/trades/{created.json()['id']}", headers=auth,
                              json={"executionRating": t.rating, "setupTags": list(t.tags)}), "tag trade")


def _reset_journal(http: httpx.Client, auth: dict) -> None:
    wanted = {n.day.isoformat() for n in JOURNAL}
    stale = _check(http.get("/journal", headers=auth, params={"from": JOURNAL_RESET_FROM, "to": JOURNAL_RESET_TO}),
                   "list journal").json()
    for entry in stale:  # blank anything a previous version of the dataset left behind
        if entry["date"] not in wanted:
            _check(http.put(f"/journal/{entry['date']}", headers=auth, json={"notes": "", "htfBias": ""}), "clear journal")
    for note in JOURNAL:
        _check(http.put(f"/journal/{note.day.isoformat()}", headers=auth,
                        json={"notes": note.notes, "htfBias": note.htf_bias}), "write journal")


def _check(response: httpx.Response, what: str) -> httpx.Response:
    if response.status_code >= 400:
        # Status and path only: never the Authorization header.
        raise SeedError(f"Could not {what}: HTTP {response.status_code} from {response.request.url.path}")
    return response


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed the eval trader into a local Spring server.")
    parser.add_argument("--spring-url", default=Settings().spring_api_base_url)
    args = parser.parse_args()
    seeded = seed(args.spring_url)
    print(f"Seeded {len(TRADES)} trades and {len(JOURNAL)} journal notes for {EVAL_EMAIL} at {seeded.base_url}")
    print("Account ids:", seeded.account_ids)


if __name__ == "__main__":
    main()
