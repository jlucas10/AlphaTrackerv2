FROM python:3.13-slim

# No .pyc files in the image; flush logs immediately so Railway shows them live.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_ROOT_USER_ACTION=ignore

WORKDIR /app

# Dependencies first, so this slow layer is cached until requirements.txt changes. Exact tested versions.
COPY requirements.txt ./
RUN pip install -r requirements.txt

COPY app ./app

# Run as an unprivileged user: if the app were ever compromised, the attacker would not be root.
RUN useradd --system --uid 10001 --no-create-home appuser
USER appuser

# Railway (and most platforms) inject PORT; 8000 is the local default.
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]
