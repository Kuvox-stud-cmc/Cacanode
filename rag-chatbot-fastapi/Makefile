.DEFAULT_GOAL := help

PYTHON_MODE ?= venv
PYTHON ?= python
CONDA ?= conda
CONDA_ENV ?= rag-chatbot
VENV ?= .venv
APP_HOST ?= 0.0.0.0
APP_PORT ?= 8000
GRAPH_HOST ?= 0.0.0.0
GRAPH_PORT ?= 8010
UVICORN_ARGS ?=
PYTEST_ARGS ?=
TARGET_COLLECTION ?= knowledge_units_v2
REINDEX_BATCH_SIZE ?= 50
REINDEX_ARGS ?=
EVALUATION_DATASET ?= tests/data/retrieval_vi_v1.json
EVALUATION_RESULTS ?= artifacts/full-pipeline.json
EVALUATION_LABEL ?= full-pipeline
DOCKER_COMPOSE ?= docker compose
DEV_COMPOSE_FILE ?= ../docker-compose.yml
DEV_INFRA_SERVICES ?= postgres redis rabbitmq qdrant ollama seaweedfs-master \
	seaweedfs-volume seaweedfs-filer seaweedfs-s3 graph-service
DEV_GRAPH_URL ?= http://localhost:8010
DEV_QDRANT_COLLECTION ?= knowledge_units_v2
DEV_RERANKER_ENABLED ?= false
DEV_RERANKER_URL ?= http://localhost:8082
DEV_RERANKER_MODEL_ID ?= cross-encoder/mmarco-mMiniLMv2-L12-H384-v1

ifeq ($(OS),Windows_NT)
VENV_PYTHON := $(VENV)/Scripts/python.exe
REMOVE := powershell -NoProfile -Command Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
else
VENV_PYTHON := $(VENV)/bin/python
REMOVE := rm -rf
endif

ifeq ($(PYTHON_MODE),conda)
PYTHON_RUN := $(CONDA) run --no-capture-output -n $(CONDA_ENV) python
else
PYTHON_RUN := $(VENV_PYTHON)
endif

.PHONY: help env install install-dev setup run run-ai dev dev-ai run-graph dev-graph \
	dev-infra dev-down dev-reranker dev-reranker-down reindex reindex-dry-run \
	evaluate-retrieval test check lint typecheck clean

help:
	$(info Available targets:)
	$(info   make env         Create/verify the configured Python environment)
	$(info   make setup       Install all dependencies into the Python environment)
	$(info   make install     Install runtime dependencies into the Python environment)
	$(info   make install-dev Install runtime dependencies and pytest)
	$(info   make run                  Run the AI/chat API; alias for run-ai)
	$(info   make run-ai               Run app.main:app on APP_HOST:APP_PORT)
	$(info   make dev                  Run the local AI/chat API with reload; containers are untouched)
	$(info   make dev-ai               Run only the AI/chat API with reload)
	$(info   make run-graph            Run app.graph_main:app on GRAPH_HOST:GRAPH_PORT)
	$(info   make dev-graph            Run the graph API with reload)
	$(info   make dev-infra            Start local storage, Ollama, and graph containers)
	$(info   make dev-down             Stop the local infrastructure started for development)
	$(info   make dev-reranker         Explicitly start TEI reranking; not recommended on Apple Silicon)
	$(info   make dev-reranker-down    Stop the optional TEI reranker)
	$(info   make reindex              Reindex completed documents into TARGET_COLLECTION)
	$(info   make reindex-dry-run      Preview the same reindex operation)
	$(info   make evaluate-retrieval   Score recorded retrieval rankings)
	$(info   make test                 Run pytest)
	$(info   make lint                 Run Ruff checks)
	$(info   make typecheck            Run mypy)
	$(info   make check                Run lint, type checks, and tests)
	$(info   make clean                Remove caches and test artifacts)
	$(info Common variables: APP_PORT, DEV_GRAPH_URL, DEV_QDRANT_COLLECTION, DEV_RERANKER_URL, TARGET_COLLECTION)

env:
ifeq ($(PYTHON_MODE),conda)
	$(PYTHON_RUN) --version
else
	$(PYTHON) -m venv $(VENV)
	$(PYTHON_RUN) --version
endif

install: env
	$(PYTHON_RUN) -m pip install -e .

install-dev: install
	$(PYTHON_RUN) -m pip install -e .[dev]

setup: install-dev

run: run-ai

run-ai: env
	$(PYTHON_RUN) -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) $(UVICORN_ARGS)

dev: env
	GRAPH_SERVICE_URL=$(DEV_GRAPH_URL) \
	QDRANT_COLLECTION=$(DEV_QDRANT_COLLECTION) \
	RERANKER_ENABLED=$(DEV_RERANKER_ENABLED) \
	RERANKER_URL=$(DEV_RERANKER_URL) \
	RERANKER_MODEL_ID=$(DEV_RERANKER_MODEL_ID) \
	$(PYTHON_RUN) -m uvicorn app.main:app \
		--host $(APP_HOST) --port $(APP_PORT) --reload $(UVICORN_ARGS)

dev-ai: env
	$(PYTHON_RUN) -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) --reload $(UVICORN_ARGS)

run-graph: env
	$(PYTHON_RUN) -m uvicorn app.graph_main:app --host $(GRAPH_HOST) --port $(GRAPH_PORT) $(UVICORN_ARGS)

dev-graph: env
	$(PYTHON_RUN) -m uvicorn app.graph_main:app --host $(GRAPH_HOST) --port $(GRAPH_PORT) --reload $(UVICORN_ARGS)

dev-infra:
	$(DOCKER_COMPOSE) -f $(DEV_COMPOSE_FILE) up -d --wait --build $(DEV_INFRA_SERVICES)

dev-down:
	$(DOCKER_COMPOSE) -f $(DEV_COMPOSE_FILE) stop $(DEV_INFRA_SERVICES)

dev-reranker:
	LOCAL_RERANKER_MODEL_ID=$(DEV_RERANKER_MODEL_ID) \
	$(DOCKER_COMPOSE) -f $(DEV_COMPOSE_FILE) --profile reranker up -d reranker-service

dev-reranker-down:
	$(DOCKER_COMPOSE) -f $(DEV_COMPOSE_FILE) stop reranker-service

reindex: env
	$(PYTHON_RUN) -m app.maintenance.reindex_documents \
		--target-collection $(TARGET_COLLECTION) \
		--batch-size $(REINDEX_BATCH_SIZE) $(REINDEX_ARGS)

reindex-dry-run: env
	$(PYTHON_RUN) -m app.maintenance.reindex_documents \
		--target-collection $(TARGET_COLLECTION) \
		--batch-size $(REINDEX_BATCH_SIZE) \
		--dry-run $(REINDEX_ARGS)

evaluate-retrieval: env
	$(PYTHON_RUN) -m app.maintenance.evaluate_retrieval \
		--dataset $(EVALUATION_DATASET) \
		--results $(EVALUATION_RESULTS) \
		--label $(EVALUATION_LABEL)

test: env
	$(PYTHON_RUN) -m pytest $(PYTEST_ARGS)

check: lint typecheck test

lint: env
	$(PYTHON_RUN) -m ruff check app tests

typecheck: env
	$(PYTHON_RUN) -m mypy app

clean:
	$(REMOVE) .pytest_cache .mypy_cache htmlcov .coverage $(VENV)
