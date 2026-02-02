.PHONY: help local prod up down logs clean

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

local: ## Start services with .env.local (default)
	@echo "Starting services with .env.local..."
	ENV_FILE=.env.local docker compose up -d

prod: ## Start services with .env.prod
	@echo "Starting services with .env.prod..."
	ENV_FILE=.env.prod docker compose up -d

up: local ## Alias for 'make local'

down: ## Stop all services
	docker compose down

logs: ## Show logs from all services
	docker compose logs -f

clean: ## Stop and remove all containers, volumes, and networks
	docker compose down -v --remove-orphans

restart-local: down local ## Restart with .env.local

restart-prod: down prod ## Restart with .env.prod
