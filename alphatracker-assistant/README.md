# AlphaTracker Assistant

A small FastAPI service that answers plain-English questions about a trader's own
trading ("what's my win rate on MNQ this month?"). It calls Gemini with tool calling;
every tool is a read-only `GET` to the Spring API, forwarding the user's JWT so the
existing ownership checks apply.

Design rules: the model never does arithmetic (every number comes from the Java API),
there are no write tools, and this service never touches the database.

## Setup

```bash
cd alphatracker-assistant
python3 -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
```

Create `alphatracker-assistant/.env` (it is git-ignored, never commit it) with at least:

```
GEMINI_API_KEY=your-key-here
```

Optional variables: `GEMINI_MODEL`, `SPRING_API_BASE_URL` (default
`http://localhost:8080/api/v1`), `TIMEZONE` (default `America/Los_Angeles`, defines what
"today" means), `CORS_ALLOWED_ORIGINS` (comma-separated extra origins; any
`http://localhost:<port>` is always allowed). Blank values count as "not set".

## Run

```bash
uvicorn app.main:app --reload --port 8000
curl localhost:8000/health   # {"status":"ok","gemini_configured":true}
```

## Test

```bash
pytest
```

## Configuration in production

On Railway there is no `.env` file: set the same names as service variables instead.
