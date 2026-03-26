.PHONY: help setup start stop logs test clean build

help:
	@echo "Amen Bank — Available commands:"
	@echo "  make setup     Copy .env.example → .env"
	@echo "  make start     Start all services (docker compose up)"
	@echo "  make stop      Stop all services"
	@echo "  make logs      Tail all logs"
	@echo "  make test      Run all tests"
	@echo "  make build     Build all Docker images"
	@echo "  make clean     Stop + remove volumes"

setup:
	cp -n .env.example .env
	@echo "✅ .env created — edit it before running 'make start'"

start:
	docker compose up -d
	@echo "✅ Services started"
	@echo "   Frontend:  http://localhost:4200"
	@echo "   API:       http://localhost:8080/api/v1/swagger-ui.html"
	@echo "   Chatbot:   http://localhost:8000/docs"
	@echo "   Grafana:   http://localhost:3000"

stop:
	docker compose down

logs:
	docker compose logs -f --tail=100

test:
	@echo "▶ Backend tests..."
	cd backend && ./mvnw verify -q --no-transfer-progress
	@echo "▶ Frontend tests..."
	cd frontend && npm run test:ci
	@echo "▶ Chatbot tests..."
	cd chatbot && python -m pytest tests/ -v

build:
	docker compose build --no-cache

clean:
	docker compose down -v --remove-orphans
	rm -rf backend/target frontend/dist chatbot/.pytest_cache
