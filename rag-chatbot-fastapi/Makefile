.DEFAULT_GOAL := help

PYTHON ?= python
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

.PHONY: help venv install install-dev setup run dev test check clean clean-all

help:
	$(info Available targets:)
	$(info   make setup       Create the virtual environment and install dependencies)
	$(info   make install     Install runtime dependencies)
	$(info   make install-dev Install runtime dependencies and pytest)
	$(info   make run         Run the FastAPI application)
	$(info   make dev         Run FastAPI with auto-reload)
	$(info   make test        Run pytest)
	$(info   make clean       Remove caches and test artifacts)
	$(info   make clean-all   Also remove the virtual environment)
	$(info Optional variables: APP_HOST, APP_PORT, UVICORN_ARGS, PYTEST_ARGS)

$(VENV_PYTHON):
	$(PYTHON) -m venv $(VENV)

venv: $(VENV_PYTHON)

install: venv
	$(VENV_PYTHON) -m pip install --upgrade pip
	$(VENV_PYTHON) -m pip install -r requirements.txt

install-dev: install
	$(VENV_PYTHON) -m pip install pytest

setup: install-dev

run: venv
	$(VENV_PYTHON) -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) $(UVICORN_ARGS)

dev: venv
	$(VENV_PYTHON) -m uvicorn app.main:app --host $(APP_HOST) --port $(APP_PORT) --reload $(UVICORN_ARGS)

test: venv
	$(VENV_PYTHON) -m pytest $(PYTEST_ARGS)

check: test

clean:
	$(REMOVE) .pytest_cache .mypy_cache htmlcov .coverage

clean-all: clean
	$(REMOVE) $(VENV)
