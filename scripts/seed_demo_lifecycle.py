"""Load a demo trader into a LOCAL Spring server so the account lifecycle can be looked at by hand.

Every account is built to land in a different state: near its target, target hit, floor breached,
breached-then-past-the-target, and passed with a funded successor. The money is chosen so each figure is
checkable: all trades are MNQ (a point is worth $2), so profit = points x contracts x 2.

Idempotent: logs in (registering the first time), reuses accounts by name, reopens them, deletes the user's
trades and recreates them. Only ever talks to localhost.

    python3 scripts/seed_demo_lifecycle.py [--spring-url http://localhost:8080/api/v1]

Uses only the standard library, so it runs without the assistant's virtualenv.
"""

import argparse
import json
import urllib.error
import urllib.request
from urllib.parse import urlparse

EMAIL = "demo-lifecycle@example.test"
PASSWORD = "Demo-lifecycle-1!"  # not a secret: this user only exists on a developer's local database
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}

# (day in Oct 2026, contracts, points) -> profit = contracts * points * 2. Negative points = a losing LONG.
ACCOUNTS = [
    {
        "name": "Apex 50k - Near Target", "firm": "Apex", "type": "EVALUATION", "balance": 50000, "dd": 2500, "target": 3000,
        "trades": [(5, 20, 20), (6, 10, 30), (7, 13, -10), (8, 10, 50)],  # +800 +600 -260 +1000 = 2140
    },
    {
        "name": "Apex 50k - Target Hit", "firm": "Apex", "type": "EVALUATION", "balance": 50000, "dd": 2500, "target": 3000,
        "trades": [(5, 20, 30), (6, 15, 30), (7, 10, 50)],  # +1200 +900 +1000 = 3100, reached Oct 7
    },
    {
        "name": "Topstep 50k - Blown", "firm": "Topstep", "type": "EVALUATION", "balance": 50000, "dd": 2000, "target": 3000,
        "trades": [(5, 10, 20), (6, 10, -50), (7, 15, -50)],  # +400 -1000 -1500: 47,900, floor was 48,400, breach Oct 7
    },
    {
        "name": "Apex 50k - Recovered Too Late", "firm": "Apex", "type": "EVALUATION", "balance": 50000, "dd": 2000, "target": 3000,
        "trades": [(5, 10, -50), (6, 11, -50), (7, 40, 50), (8, 15, 50)],  # breach Oct 6, then +4000 +1500 = 3400
    },
    {
        "name": "Apex 100k - Passed", "firm": "Apex", "type": "EVALUATION", "balance": 100000, "dd": 3000, "target": 6000,
        "trades": [(5, 25, 50), (6, 20, 50), (7, 20, 50)],  # +2500 +2000 +2000 = 6500, then marked passed below
        "pass": True,
    },
]
FUNDED = {
    "name": "Apex 100k - Passed (Funded)", "firm": "Apex", "type": "FUNDED", "balance": 100000, "dd": 3000,
    "trades": [(12, 15, 20), (13, 10, -10)],  # +600 -200
}


class SeedError(RuntimeError):
    pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed the lifecycle demo trader into a local Spring server.")
    parser.add_argument("--spring-url", default="http://localhost:8080/api/v1")
    base = parser.parse_args().spring_url.rstrip("/")
    if urlparse(base).hostname not in LOCAL_HOSTS:  # equality, not "contains": localhost.evil.com is rejected
        raise SeedError(f"Refusing to seed {base!r}: only a localhost server is allowed.")

    def call(method: str, path: str, body: dict | None = None, token: str | None = None):
        request = urllib.request.Request(
            base + path, method=method, data=None if body is None else json.dumps(body).encode(),
            headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})},
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:  # status and path only: never the Authorization header
            raise SeedError(f"{method} {path} -> HTTP {e.code}") from None

    try:
        token = call("POST", "/auth/authenticate", {"email": EMAIL, "password": PASSWORD})["token"]
    except SeedError:  # first run: the demo user doesn't exist yet
        token = call("POST", "/auth/register", {"email": EMAIL, "password": PASSWORD,
                                                "firstName": "Demo", "lastName": "Trader"})["token"]

    for trade in call("GET", "/trades", token=token):
        call("DELETE", f"/trades/{trade['id']}", token=token)
    existing = {a["name"]: a for a in call("GET", "/accounts", token=token)}
    for a in existing.values():  # undo a previous run's pass/fail so every run starts from the same place
        if a.get("status", "ACTIVE") != "ACTIVE":
            call("PATCH", f"/accounts/{a['id']}/status", {"status": "ACTIVE"}, token)

    def ensure(spec: dict, promoted_from: int | None = None) -> int:
        if spec["name"] in existing:
            return existing[spec["name"]]["id"]
        body = {"name": spec["name"], "firm": spec["firm"], "accountType": spec["type"],
                "startingBalance": spec["balance"], "maxDrawdown": spec["dd"], "drawdownMode": "END_OF_DAY"}
        if spec.get("target"):
            body["profitTarget"] = spec["target"]
        if promoted_from:
            body["promotedFromAccountId"] = promoted_from
        return call("POST", "/accounts", body, token)["id"]

    def log_trades(account_id: int, trades: list) -> None:
        for day, contracts, points in trades:
            entry = 20000.0
            call("POST", "/trades", {
                "ticker": "MNQ", "direction": "LONG", "entryPrice": entry, "exitPrice": entry + points,
                "contracts": contracts, "followedPlan": points > 0, "accountId": account_id,
                "tradeDate": f"2026-10-{day:02d}T10:30:00",
            }, token)

    passed_id = None
    for spec in ACCOUNTS:
        account_id = ensure(spec)
        log_trades(account_id, spec["trades"])
        if spec.get("pass"):
            passed_id = account_id
    funded_id = ensure(FUNDED, promoted_from=passed_id)
    log_trades(funded_id, FUNDED["trades"])
    call("PATCH", f"/accounts/{passed_id}/status", {"status": "PASSED"}, token)  # after its trades, like a real pass
    call("PATCH", f"/accounts/{funded_id}/primary", token=token)

    print(f"Seeded {EMAIL} / {PASSWORD} at {base}\n")
    for a in call("GET", "/accounts", token=token):
        target = f"{a['targetProgress'] * 100:.0f}%" if a.get("targetProgress") is not None else "-"
        left = a.get("targetRemaining")  # absent on a backend that predates the field
        left = "n/a" if "targetRemaining" not in a else "-" if left is None else f"{left:.2f}"
        print(f"  {a['name']:<32} {a['accountType']:<10} status={a['status']:<7} profit={a['profit']:>8.2f} "
              f"target={target:>5} left={left:>8} suggested={a['suggestedStatus']}")


if __name__ == "__main__":
    main()
