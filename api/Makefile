.DEFAULT_GOAL := help

PYTHON ?= python
VENV ?= .venv
APP_HOST ?= 0.0.0.0
APP_PORT ?= 8000
UVICORN_ARGS ?=
PYTEST_ARGS ?=
PROFILE ?= dev

ifeq ($(OS),Windows_NT)
MVNW ?= ./mvnw.cmd
VENV_PYTHON := $(VENV)/Scripts/python.exe
REMOVE := powershell -NoProfile -Command Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
else
MVNW ?= sh ./mvnw
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
	$(info Optional variables: PROFILE=dev MAVEN_ARGS=...)

deps:
	$(MVNW) dependency:go-offline $(MAVEN_ARGS)

run:
	$(MVNW) -P$(PROFILE) spring-boot:run $(MAVEN_ARGS)

dev: run-dev

run-dev:
	$(MVNW) -Pdev spring-boot:run $(MAVEN_ARGS)

run-prod:
	$(MVNW) -Pprod spring-boot:run $(MAVEN_ARGS)

test:
	$(MVNW) -Ptest -Dspring.profiles.active=test test $(MAVEN_ARGS)

verify:
	$(MVNW) -Ptest -Dspring.profiles.active=test verify $(MAVEN_ARGS)

package:
	$(MVNW) -Pprod clean package $(MAVEN_ARGS)

clean:
	$(MVNW) clean $(MAVEN_ARGS)
