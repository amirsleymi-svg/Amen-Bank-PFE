# Contributing to Amen Bank

## Branches
- `main` — production-ready, protected
- `develop` — integration branch
- `feature/*` — new features
- `fix/*` — bug fixes
- `hotfix/*` — urgent production fixes

## Commit Convention (Conventional Commits)
```
feat(auth): add TOTP backup code recovery
fix(transfer): correct daily limit calculation
docs(api): update OpenAPI specs
test(credit): add amortization edge cases
chore(deps): bump Spring Boot to 3.2.5
```

## Pull Request Checklist
- [ ] Tests pass (`mvn verify` / `npm run test:ci` / `pytest`)
- [ ] No new linting errors
- [ ] `.env.example` updated if new variables added
- [ ] OpenAPI annotations updated on new endpoints
- [ ] CHANGELOG.md updated

## Local Setup
```bash
cp .env.example .env
# fill in .env values
docker compose up -d mysql redis
cd backend && mvn spring-boot:run
cd frontend && npm install && ng serve
cd chatbot && pip install -r requirements.txt && uvicorn app.main:app --reload
```
