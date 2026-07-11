.DEFAULT_GOAL := help

CONDA ?= conda
CONDA_ENV ?= rag-chatbot
CONDA_RUN := $(CONDA) run --no-capture-output -n $(CONDA_ENV)
APP_HOST ?= 0.0.0.0
APP_PORT ?= 8000
UVICORN_ARGS ?=
PYTEST_ARGS ?=

ifeq ($(OS),Windows_NT)
REMOVE := powershell -NoProfile -Command Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
else
REMOVE := rm -rf
endif

.PHONY: help env install install-dev setup run dev test check lint typecheck clean

help:
	$(info Available targets:)
	$(info   make env         Verify the configured Conda environment)
	$(info   make setup       Install all dependencies into the Conda environment)
	$(info   make install     Install runtime dependencies into the Conda environment)
	$(info   make install-dev Install runtime dependencies and pytest)
	$(info   make run         Run the FastAPI application)
	$(info   make dev         Run FastAPI with auto-reload)
	$(info   make test        Run pytest)
	$(info   make lint        Run Ruff checks)
	$(info   make typecheck   Run mypy)
	$(info   make clean       Remove caches and test artifacts)
	$(info Optional variables: CONDA_ENV, APP_HOST, APP_PORT, UVICORN_ARGS, PYTEST_ARGS)

env:
	$(CONDA_RUN) python --version

install: env
	$(CONDA_RUN) python -m pip install -e .

install-dev: install
	$(CONDA_RUN) python -m pip install -e .[dev]

setup: install-dev

run: env
	$(CONDA_RUN) python -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) $(UVICORN_ARGS)

dev: env
	$(CONDA_RUN) python -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) --reload $(UVICORN_ARGS)

test: env
	$(CONDA_RUN) python -m pytest $(PYTEST_ARGS)

check: test

lint: env
	$(CONDA_RUN) python -m ruff check app tests

typecheck: env
	$(CONDA_RUN) python -m mypy app

clean:
	$(REMOVE) .pytest_cache .mypy_cache htmlcov .coverage
