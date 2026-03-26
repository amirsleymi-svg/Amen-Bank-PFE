# 🔒 Amen Bank — Security Documentation

## Threat Model

### Assets à protéger
1. Données personnelles (CIN, adresse, date de naissance)
2. Données financières (soldes, transactions, IBAN)
3. Credentials utilisateurs (mot de passe, TOTP secret)
4. Tokens JWT (access + refresh)
5. Données de sessions

### Vecteurs de menace

| Menace | Impact | Probabilité | Mitigation |
|--------|--------|-------------|------------|
| Credential stuffing | Élevé | Élevé | Rate limiting + lockout + 2FA |
| Brute force TOTP | Élevé | Moyen | Fenêtre ±1 + rate limiting |
| Token hijacking | Élevé | Moyen | HTTPS + HttpOnly cookies + rotation |
| SQL Injection | Critique | Faible | JPA/Hibernate paramétré |
| XSS | Élevé | Moyen | CSP strict + sanitisation Angular |
| CSRF | Élevé | Faible | SameSite=Strict + JWT stateless |
| Replay attack | Moyen | Faible | JTI unique + rotation refresh tokens |
| Privilege escalation | Critique | Faible | RBAC strict + @PreAuthorize |
| File upload malveillant | Moyen | Moyen | Validation MIME + taille + sandbox |
| Data breach DB | Critique | Faible | Chiffrement au repos + least privilege |

---

## OWASP Top 10 — Couverture

### A01 — Broken Access Control ✅
- RBAC avec permissions granulaires (26 permissions)
- Vérification d'ownership sur chaque ressource (ex: `findActiveAccountByIdAndUserId`)
- `@PreAuthorize` sur chaque endpoint sensible
- Soft delete (pas de suppression physique des données)

### A02 — Cryptographic Failures ✅
- Mots de passe : BCrypt strength 12
- Codes de secours : BCrypt hashés
- Tokens : SHA-256 avant stockage BDD
- TOTP secrets : stockés chiffrés (AES-256 recommandé en production)
- TLS 1.2/1.3 obligatoire
- HSTS activé (max-age=31536000)

### A03 — Injection ✅
- Requêtes préparées via JPA/Hibernate
- `@Valid` + Bean Validation sur tous les DTOs
- Sanitisation des inputs chatbot (strip HTML/scripts)
- Pas de requêtes SQL dynamiques

### A04 — Insecure Design ✅
- Séparation User / Admin (tables et services distincts)
- Principe du moindre privilège
- Tous les flux sensibles requièrent TOTP
- Audit logging complet et immuable

### A05 — Security Misconfiguration ✅
- `server.error.include-stacktrace=never`
- Headers de sécurité HTTP via Spring Security + Nginx
- Swagger UI désactivé en production
- Actuator exposé uniquement au réseau interne

### A06 — Vulnerable Components ✅
- OWASP Dependency Check intégré dans CI/CD
- Trivy scan des images Docker
- Dépendances verrouillées (pom.xml + package-lock.json)

### A07 — Identification and Authentication Failures ✅
- 2FA obligatoire pour toutes les opérations sensibles
- Lockout après 5 tentatives (15 min)
- Refresh token rotation avec détection de réutilisation
- Sessions invalidées à la déconnexion

### A08 — Software and Data Integrity Failures ✅
- Signatures JWT vérifiées (HS256)
- CI/CD avec vérification d'intégrité
- Images Docker signées (production)

### A09 — Security Logging and Monitoring Failures ✅
- Audit log sur toutes les opérations (créer, modifier, supprimer)
- Logs IP + User-Agent + Request-ID
- Prometheus + Grafana pour alertes temps réel
- Sentry pour tracking erreurs

### A10 — Server-Side Request Forgery ✅
- Chatbot : validation des URLs backend (allowlist)
- Pas d'exposition directe d'URLs internes
- Nginx comme point d'entrée unique

---

## Checklist de durcissement

### Backend Spring Boot
- [x] `spring.jpa.show-sql=false` en production
- [x] `server.error.include-message=never`
- [x] Actuator endpoints restreints au réseau interne
- [x] CORS configuré avec allowedOriginPatterns
- [x] Compression GZIP activée
- [x] `HttpOnly` + `Secure` + `SameSite=Strict` sur les cookies
- [x] Rate limiting applicatif + Nginx

### Base de données
- [x] Utilisateur DB avec permissions minimales (pas de DROP/CREATE)
- [x] Connexions SSL à MySQL
- [x] Flyway pour les migrations (pas de DDL ad hoc)
- [x] Backup automatique quotidien
- [x] Soft delete (audit trail conservé)

### Frontend Angular
- [x] `HttpOnly` pour les tokens (localStorage en fallback sécurisé)
- [x] `DomSanitizer` pour les inputs dynamiques
- [x] Lazy loading des modules
- [x] Content Security Policy via Nginx
- [x] Pas de secrets en dur (variables d'environnement Angular)

### Infrastructure
- [x] TLS 1.2+ uniquement
- [x] Ciphers modernes (ECDHE)
- [x] HSTS avec preload
- [x] Rate limiting multi-couche (Nginx + Spring)
- [x] Images Docker non-root
- [x] Secrets via Docker secrets / .env (jamais dans les images)
- [x] Réseaux Docker isolés

---

## Procédure de réponse aux incidents

1. **Détection** : Alerte Grafana / Sentry / audit log
2. **Confinement** : Désactivation du compte ou révocation des tokens
3. **Investigation** : Consultation des audit logs
4. **Éradication** : Patch + redéploiement
5. **Notification** : Informer l'utilisateur affecté sous 72h (RGPD)
6. **Post-mortem** : Rapport et amélioration des contrôles

---

## Contacts sécurité

- Email : security@amenbank.com
- Téléphone urgences : +216 71 XXX XXX (24h/24)
