# ─── Stage 1: Build Angular ───────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

ARG API_URL=/api/v1
ARG CHATBOT_URL=/chatbot

# Cache node_modules
COPY package*.json ./
RUN npm ci --prefer-offline --no-audit

COPY . .

# Replace environment at build time
RUN sed -i "s|http://localhost:8080/api/v1|${API_URL}|g" src/environments/environment.prod.ts && \
    sed -i "s|http://localhost:8000|${CHATBOT_URL}|g" src/environments/environment.prod.ts

RUN npm run build

# ─── Stage 2: Serve with Nginx ─────────────────────────────────────
FROM nginx:1.25-alpine AS runtime

# Remove default nginx site
RUN rm -rf /usr/share/nginx/html/* && rm /etc/nginx/conf.d/default.conf

# Copy built app
COPY --from=builder /app/dist/amen-bank/browser /usr/share/nginx/html

# Nginx config for Angular SPA
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
