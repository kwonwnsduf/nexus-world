# syntax=docker/dockerfile:1.7
FROM python:3.12.11-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
WORKDIR /app

FROM base AS test
COPY requirements.txt requirements-dev.txt pyproject.toml ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir -r requirements-dev.txt
COPY app app
COPY tests tests
RUN ruff check app tests \
 && mypy app tests \
 && pytest

FROM base AS runtime
RUN addgroup --system app && adduser --system --ingroup app app
COPY requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir -r requirements.txt
COPY --chown=app:app app app
USER app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

