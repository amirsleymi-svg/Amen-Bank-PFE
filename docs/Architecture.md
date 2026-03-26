# Amen Bank — Architecture

## System Overview

```
Browser/Mobile
      │
      ▼
   NGINX (443)
   Rate Limiting + SSL + CSP Headers
      │
   ┌──┴──────────────┬──────────────────┐
   ▼                 ▼                  ▼
Angular SPA     Spring Boot API    FastAPI Chatbot
(port 4200)      (port 8080)        (port 8000)
   │              │    │               │
   │           MySQL   Redis         Redis
   │           (3306) (6379)         (6379)
   │
Prometheus + Grafana (3000/9090)
```

## Backend Package Structure

```
com.amenbank/
├── config/          # AppConfig, SecurityConfig
├── controller/      # REST endpoints (Auth, Account, Transfer, Credit, Admin, StandingOrder)
├── dto/
│   ├── request/     # Input validation DTOs
│   └── response/    # API response DTOs
├── entity/          # JPA entities (15 entities)
├── enums/           # All domain enums (16 enums)
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── repository/      # Spring Data JPA repos (14 repos)
├── security/
│   ├── jwt/         # JwtUtils, JwtAuthFilter, JwtAuthEntryPoint
│   └── totp/        # TotpService (Google Authenticator)
└── service/
    └── impl/        # Business logic (Auth, Account, Transfer, Credit, Admin, Email, Audit)
```

## Frontend Module Structure

```
src/app/
├── core/
│   ├── guards/        # authGuard, guestGuard, adminGuard
│   ├── interceptors/  # authInterceptor (JWT + refresh)
│   ├── models/        # TypeScript interfaces
│   └── services/      # AuthService, AccountService, TransferService, CreditService
├── features/
│   ├── auth/          # Login, Register, ForgotPassword, ResetPassword, Forbidden
│   ├── dashboard/     # Main dashboard with Chart.js
│   ├── transfers/     # Simple (2FA modal), Batch CSV, History
│   ├── credits/       # Simulation, Apply, Applications
│   ├── profile/       # 2FA setup (QR), Password change, Sessions
│   ├── admin/         # KYC, Users, Credits, Audit logs
│   └── chatbot/       # Chat widget
└── shared/
    └── components/
        └── totp-modal/ # Reusable Google Authenticator modal
```

## Security Architecture

### Authentication Flow
1. `POST /auth/login` → validates credentials → returns `tempToken` if 2FA enabled
2. `POST /auth/totp/verify` → validates TOTP code → returns `accessToken` + `refreshToken`
3. Every API call: `Authorization: Bearer <accessToken>`
4. On 401: interceptor calls `POST /auth/refresh` → rotates tokens → retries

### Sensitive Operations (require TOTP)
- Wire transfers (`POST /transfers`)
- Password change (`POST /auth/password/change`)
- 2FA enable (`POST /auth/totp/enable`)

### RBAC Matrix
| Permission         | USER | AUDITOR | ADMIN | SUPER_ADMIN |
|-------------------|------|---------|-------|-------------|
| ACCOUNT_READ      | ✓    | ✓       | ✓     | ✓           |
| TRANSFER_CREATE   | ✓    |         | ✓     | ✓           |
| KYC_APPROVE       |      |         | ✓     | ✓           |
| AUDIT_READ        |      | ✓       | ✓     | ✓           |
| ADMIN_CREATE      |      |         |       | ✓           |
