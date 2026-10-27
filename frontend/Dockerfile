# syntax=docker/dockerfile:1
# Build context: the REPOSITORY ROOT (so the nginx config in infra/nginx can be copied in).
#   docker build -f frontend/Dockerfile -t worksphere-frontend .

# ---- Stage 1: build the static site ----
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# Empty = the browser calls the same address that served the page, and nginx proxies /api and /ws.
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- Stage 2: nginx serves it (the "unprivileged" image runs as a non-root user on port 8080) ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
