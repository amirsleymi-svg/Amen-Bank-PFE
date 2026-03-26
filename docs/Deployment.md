# Amen Bank — Deployment Guide

## Prerequisites
- Docker >= 24.0 & Docker Compose >= 2.0
- Domain name with DNS pointing to your server
- SSL certificate (Let's Encrypt recommended)

## Local Development

```bash
# 1. Clone
git clone https://github.com/your-org/amen-bank.git
cd amen-bank

# 2. Environment
cp .env.example .env
# Edit .env — fill ALL values (never commit .env)

# 3. Generate JWT secret
openssl rand -base64 64

# 4. Start services
docker compose up -d

# 5. Check health
docker compose ps
curl http://localhost:8080/api/v1/actuator/health
```

## Production Deployment

```bash
# 1. SSH to server
ssh user@your-server

# 2. Clone & configure
git clone https://github.com/your-org/amen-bank.git
cd amen-bank
cp .env.example .env
# Fill production values in .env

# 3. SSL certificates
mkdir -p infrastructure/nginx/ssl
# Copy amenbank.crt and amenbank.key to infrastructure/nginx/ssl/
# OR use Let's Encrypt:
certbot certonly --standalone -d amenbank.yourdomain.com

# 4. Build & start
docker compose -f docker-compose.yml up -d --build

# 5. Initial admin setup
curl -X POST https://amenbank.yourdomain.com/api/v1/admin/register \
  -H "Content-Type: application/json" \
  -d '{"username":"superadmin","email":"admin@amenbank.com",
       "password":"SecurePass@123","firstName":"Admin","lastName":"Amen",
       "adminSecretKey":"YOUR_ADMIN_SECRET_KEY"}'
```

## Service URLs
| Service    | URL                                          |
|-----------|----------------------------------------------|
| Frontend  | https://amenbank.yourdomain.com              |
| API       | https://amenbank.yourdomain.com/api/v1       |
| Swagger   | https://amenbank.yourdomain.com/api/v1/swagger-ui.html |
| Grafana   | http://your-server:3000                      |
| Prometheus| http://your-server:9090                      |

## Backup
```bash
# Database backup (cron daily)
docker exec amenbank-mysql mysqldump -u root -p$DB_ROOT_PASSWORD amenbank \
  > backups/amenbank_$(date +%Y%m%d).sql

# Restore
docker exec -i amenbank-mysql mysql -u root -p$DB_ROOT_PASSWORD amenbank \
  < backups/amenbank_20240101.sql
```

## Monitoring
- Grafana dashboards: import from `infrastructure/monitoring/grafana/dashboards/`
- Default admin: see `GRAFANA_USER` / `GRAFANA_PASSWORD` in `.env`
