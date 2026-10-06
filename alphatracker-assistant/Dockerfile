FROM python:3.13-slim

WORKDIR /app

# Dependencies first, so the layer is cached until pyproject.toml changes.
COPY pyproject.toml ./
RUN mkdir app && touch app/__init__.py && pip install --no-cache-dir . && rm -rf app

COPY app ./app

# Railway (and most platforms) inject PORT; 8000 is the local default.
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
