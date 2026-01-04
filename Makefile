# Makefile for Fraud Detection Platform
#
# Common development tasks made simple:
#   make install    - Install all prerequisites
#   make build      - Build Maven project
#   make docker     - Build Docker image
#   make local      - Run local dev environment
#   make deploy     - Deploy to Azure
#   make clean      - Clean build artifacts

.PHONY: install build test docker local deploy clean help

# Configuration
PROJECT_DIR := finance-intelligence-root
DOCKER_IMAGE := flink-fraud
DOCKER_TAG := latest
COMPOSE_FILE := docker-compose.dev.yml
ENVIRONMENT ?= dev
LOCATION ?= eastus

# Default target
help:
	@echo "Fraud Detection Platform - Build & Deploy Commands"
	@echo ""
	@echo "Development:"
	@echo "  make install     Install all prerequisites (Homebrew, Java, Maven, Docker)"
	@echo "  make build       Build all Maven modules"
	@echo "  make test        Run unit tests"
	@echo "  make local       Start local development environment (Kafka, Redis, Flink)"
	@echo "  make local-stop  Stop local development environment"
	@echo ""
	@echo "Docker:"
	@echo "  make docker      Build Docker image for Flink job"
	@echo "  make docker-push Push Docker image to ACR"
	@echo ""
	@echo "Azure:"
	@echo "  make azure-login Login to Azure CLI"
	@echo "  make deploy      Deploy infrastructure and Flink job to Azure"
	@echo ""
	@echo "Cleanup:"
	@echo "  make clean       Remove build artifacts"
	@echo ""

# =============================================================================
# Development
# =============================================================================

install:
	@echo "Installing prerequisites..."
	chmod +x scripts/setup-dev-env.sh
	./scripts/setup-dev-env.sh

build:
	@echo "Building Maven project..."
	cd $(PROJECT_DIR) && mvn clean package -DskipTests

test:
	@echo "Running tests..."
	cd $(PROJECT_DIR) && mvn test

local: docker-check
	@echo "Starting local development environment..."
	docker-compose -f $(COMPOSE_FILE) up -d
	@echo ""
	@echo "Services started:"
	@echo "  - Kafka UI:        http://localhost:8080"
	@echo "  - Flink Dashboard: http://localhost:8081"
	@echo "  - Redis Commander: http://localhost:8085"
	@echo ""
	@echo "To view logs: docker-compose -f $(COMPOSE_FILE) logs -f"

local-stop:
	@echo "Stopping local development environment..."
	docker-compose -f $(COMPOSE_FILE) down

local-logs:
	docker-compose -f $(COMPOSE_FILE) logs -f

# =============================================================================
# Docker
# =============================================================================

docker-check:
	@docker info > /dev/null 2>&1 || (echo "Docker is not running. Please start Docker Desktop." && exit 1)

docker: build docker-check
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE):$(DOCKER_TAG) -f infrastructure/docker/Dockerfile.flink ./$(PROJECT_DIR)

docker-push: docker
	@echo "Pushing Docker image to ACR..."
	@ACR=$$(az acr list --query '[0].loginServer' -o tsv); \
	if [ -z "$$ACR" ]; then echo "No ACR found. Deploy Azure infrastructure first."; exit 1; fi; \
	docker tag $(DOCKER_IMAGE):$(DOCKER_TAG) $$ACR/$(DOCKER_IMAGE):$(DOCKER_TAG); \
	az acr login --name $${ACR%%.*}; \
	docker push $$ACR/$(DOCKER_IMAGE):$(DOCKER_TAG)

# =============================================================================
# Azure
# =============================================================================

azure-login:
	@echo "Logging into Azure..."
	az login

deploy: azure-login
	@echo "Deploying to Azure..."
	chmod +x deploy-azure.sh
	./deploy-azure.sh $(ENVIRONMENT) $(LOCATION)

deploy-flink:
	@echo "Deploying Flink job to AKS..."
	kubectl apply -f infrastructure/k8s/flink-deployment.yaml

deploy-status:
	@echo "Checking deployment status..."
	kubectl get flinkdeployment -n flink-fraud
	kubectl get pods -n flink-fraud

flink-logs:
	kubectl logs -f -l app=fraud-detection-cluster -n flink-fraud

flink-ui:
	@echo "Starting Flink UI port-forward..."
	@echo "Open http://localhost:8081 in your browser"
	kubectl port-forward svc/fraud-detection-cluster-rest 8081:8081 -n flink-fraud

# =============================================================================
# Cleanup
# =============================================================================

clean:
	@echo "Cleaning build artifacts..."
	cd $(PROJECT_DIR) && mvn clean
	rm -rf $(PROJECT_DIR)/*/target

clean-docker:
	@echo "Cleaning Docker resources..."
	docker-compose -f $(COMPOSE_FILE) down -v --rmi local

clean-all: clean clean-docker
