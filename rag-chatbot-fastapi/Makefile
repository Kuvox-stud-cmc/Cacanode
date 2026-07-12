.DEFAULT_GOAL := help

PYTHON_MODE ?= venv
PYTHON ?= python
CONDA ?= conda
CONDA_ENV ?= rag-chatbot
VENV ?= .venv
APP_HOST ?= 0.0.0.0
APP_PORT ?= 8000
UVICORN_ARGS ?=
PYTEST_ARGS ?=

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

.PHONY: help env install install-dev setup run dev test check lint typecheck clean

help:
	$(info Available targets:)
	$(info   make env         Create/verify the configured Python environment)
	$(info   make setup       Install all dependencies into the Python environment)
	$(info   make install     Install runtime dependencies into the Python environment)
	$(info   make install-dev Install runtime dependencies and pytest)
	$(info   make run         Run the FastAPI application)
	$(info   make dev         Run FastAPI with auto-reload)
	$(info   make test        Run pytest)
	$(info   make lint        Run Ruff checks)
	$(info   make typecheck   Run mypy)
	$(info   make clean       Remove caches and test artifacts)
	$(info Optional variables: PYTHON_MODE=venv|conda, VENV, CONDA_ENV, APP_HOST, APP_PORT, UVICORN_ARGS, PYTEST_ARGS)

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

run: env
	$(PYTHON_RUN) -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) $(UVICORN_ARGS)

dev: env
	$(PYTHON_RUN) -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) --reload $(UVICORN_ARGS)

test: env
	$(PYTHON_RUN) -m pytest $(PYTEST_ARGS)

check: test

lint: env
	$(PYTHON_RUN) -m ruff check app tests

typecheck: env
	$(PYTHON_RUN) -m mypy app

clean:
	$(REMOVE) .pytest_cache .mypy_cache htmlcov .coverage $(VENV)
