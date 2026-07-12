.DEFAULT_GOAL := help

PYTHON ?= python
VENV ?= .venv
APP_HOST ?= 0.0.0.0
APP_PORT ?= 8000
UVICORN_ARGS ?=
PYTEST_ARGS ?=
PROFILE ?= dev
ENV_FILE ?= .env

ifeq ($(OS),Windows_NT)
MVNW ?= ./mvnw.cmd
ENV_RUN ?=
VENV_PYTHON := $(VENV)/Scripts/python.exe
REMOVE := powershell -NoProfile -Command Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
else
MVNW ?= sh ./mvnw
ENV_RUN ?= set -a; [ ! -f $(ENV_FILE) ] || . ./$(ENV_FILE); set +a;
VENV_PYTHON := $(VENV)/bin/python
REMOVE := rm -rf
endif

.PHONY: help deps run dev run-dev run-prod test verify package clean

help:
	$(info Available targets:)
	$(info   make deps       Download Maven dependencies for offline use)
	$(info   make run        Run Spring Boot with PROFILE, default: dev)
	$(info   make dev        Run Spring Boot with the dev profile)
	$(info   make run-dev    Run Spring Boot with the dev profile)
	$(info   make run-prod   Run Spring Boot with the prod profile)
	$(info   make test       Run tests with the test profile)
	$(info   make verify     Run the full Maven verification lifecycle)
	$(info   make package    Build the production JAR)
	$(info   make clean      Remove Maven build output)
	$(info Optional variables: PROFILE=dev ENV_FILE=.env MAVEN_ARGS=...)

deps:
	$(ENV_RUN) $(MVNW) dependency:go-offline $(MAVEN_ARGS)

run:
	$(ENV_RUN) $(MVNW) -P$(PROFILE) spring-boot:run $(MAVEN_ARGS)

dev: run-dev

run-dev:
	$(ENV_RUN) $(MVNW) -Pdev spring-boot:run $(MAVEN_ARGS)

run-prod:
	$(ENV_RUN) $(MVNW) -Pprod spring-boot:run $(MAVEN_ARGS)

test:
	$(ENV_RUN) $(MVNW) -Ptest -Dspring.profiles.active=test test $(MAVEN_ARGS)

verify:
	$(ENV_RUN) $(MVNW) -Ptest -Dspring.profiles.active=test verify $(MAVEN_ARGS)

package:
	$(ENV_RUN) $(MVNW) -Pprod clean package $(MAVEN_ARGS)

clean:
	$(ENV_RUN) $(MVNW) clean $(MAVEN_ARGS)
