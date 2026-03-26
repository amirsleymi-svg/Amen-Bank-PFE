# 🏦 Amen Bank — Application Bancaire Full-Stack

[![CI/CD](https://github.com/amenbank/amenbank/actions/workflows/ci.yml/badge.svg)](https://github.com/amenbank/amenbank/actions)
![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Angular](https://img.shields.io/badge/Angular-17-red) ![Python](https://img.shields.io/badge/Python-3.12-blue)

Plateforme bancaire complète, sécurisée et production-ready.

---

## 📋 Table des matières

- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Installation rapide](#installation-rapide)
- [Modules fonctionnels](#modules-fonctionnels)
- [API Documentation](#api-documentation)
- [Sécurité](#sécurité)
- [Tests](#tests)

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        NGINX (443)                          │
│              Reverse proxy + SSL + Rate Limiting             │
└──────┬─────────────────┬───────────────────┬────────────────┘
       │                 │                   │
   Angular SPA      Spring Boot         FastAPI
   (port 4200)       (port 8080)        (port 8000)
       │                 │                   │
       └────────┬────────┘         Redis Cache
                │                 (sessions + tokens)
              MySQL 8
          (port 3306)
```

**Stack technique :**
| Couche | Technologie |
|--------|------------|
| Frontend | Angular 17, Angular Material, Tailwind CSS |
| Backend | Spring Boot 3.2, Java 21 |
| Base de données | MySQL 8, Flyway |
| Chatbot | Python 3.12, FastAPI |
| Auth | JWT + TOTP (Google Authenticator) |
| Cache | Redis 7 |
| Proxy | Nginx |
| CI/CD | GitHub Actions |
| Monitoring | Prometheus + Grafana |
| Conteneurs | Docker, Docker Compose |

---

## ⚙️ Prérequis

- **Docker** ≥ 24.0 et **Docker Compose** ≥ 2.0
- **Java 21** (pour développement local backend)
- **Node.js 20** (pour développement local frontend)
- **Python 3.12** (pour développement local chatbot)

---

## 🚀 Installation rapide

```bash
# 1. Cloner le dépôt
git clone https://github.com/amenbank/amenbank.git
cd amenbank

# 2. Configurer les variables d'environnement
cp .env.example .env
# ⚠️ Modifier les valeurs dans .env avant de continuer

# 3. Générer un JWT_SECRET fort
openssl rand -base64 64

# 4. Lancer tous les services
docker compose up -d

# 5. Vérifier l'état des services
docker compose ps
```

L'application sera disponible sur :
- **Frontend** : http://localhost:4200
- **API** : http://localhost:8080/api/v1
- **Swagger UI** : http://localhost:8080/api/v1/swagger-ui.html
- **Chatbot** : http://localhost:8000/docs
- **Grafana** : http://localhost:3000

---

## 🧩 Modules fonctionnels

### 👤 Inscription & KYC
1. L'utilisateur remplit le formulaire avec son numéro CIN
2. Le système vérifie dans la table `identity_verification`
3. Une demande KYC est créée avec statut `PENDING`
4. Un admin valide ou rejette la demande
5. En cas d'approbation : compte activé + compte courant créé automatiquement

### 🔐 Authentification
- Email ou username + mot de passe hashé bcrypt
- **2FA obligatoire** : TOTP via Google Authenticator
- Codes de secours (8 codes à usage unique, hashés)
- JWT access token (15 min) + refresh token avec rotation (7 jours)
- Verrouillage après 5 tentatives échouées (15 min)

### 💸 Virements
- Virement simple avec confirmation TOTP
- Import CSV avec validation ligne par ligne et aperçu
- Virements planifiés et ordres permanents
- Export CSV des transactions

### 🏦 Crédits
- Simulation avec tableau d'amortissement complet
- Taux selon le type (personnel, immobilier, auto, entreprise, étudiant)
- Demande en ligne avec upload de documents
- Suivi du statut en temps réel

### 🤖 Chatbot
- FAQ bancaire (frais, cartes, virements, sécurité)
- Consultation du solde (authentifié)
- Transactions récentes
- Mémoire de session via Redis
- Rate limiting : 20 messages/minute

---

## 📚 API Documentation

La documentation OpenAPI est disponible via Swagger UI :
```
http://localhost:8080/api/v1/swagger-ui.html
```

### Endpoints principaux

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/auth/register` | Inscription |
| POST | `/auth/login` | Connexion |
| POST | `/auth/totp/verify` | Vérification 2FA |
| POST | `/auth/refresh` | Renouveler le token |
| GET | `/accounts` | Liste des comptes |
| GET | `/accounts/{id}/transactions` | Transactions paginées |
| POST | `/transfers` | Effectuer un virement |
| POST | `/credits/simulate` | Simuler un crédit |
| POST | `/credits/apply` | Demande de crédit |
| GET | `/admin/kyc` | Demandes KYC (admin) |

---

## 🔒 Sécurité

- **HTTPS** obligatoire avec HSTS (31536000s)
- **CSP** strict configuré
- **CSRF** protection via tokens
- **XSS** protection via CSP + sanitisation
- Mots de passe hashés avec **bcrypt** (strength: 12)
- Tokens stockés hashés en SHA-256
- Rate limiting nginx + Spring
- Audit logging complet de toutes les opérations
- Voir [Security.md](docs/security/Security.md) pour le détail

---

## 🧪 Tests

```bash
# Backend
cd backend && mvn test

# Frontend
cd frontend && npm run test:ci

# Chatbot
cd chatbot && pytest tests/ -v

# E2E
cd frontend && npx cypress run
```

---

## 📊 Monitoring

- **Prometheus** : http://localhost:9090
- **Grafana** : http://localhost:3000 (admin / voir .env)
- **Health check** : http://localhost:8080/api/v1/actuator/health

---

## 📄 Licence

Propriétaire — Amen Bank © 2024. Tous droits réservés.
