# Folio

A booking platform for independently run hotels and apartments — guests search and book
stays across a shared catalog, while each property's own staff manage their bookings,
rooms, and team through a dedicated extranet.

**Stack:** Spring Boot 3 (Java 17) · PostgreSQL · Redis · Vue 3 + TypeScript + Vite ·
Docker Compose

## Project structure

```
Hotel-system/   Backend — Spring Boot API, Flyway migrations, Docker build
frontend/       Frontend — Vue 3 SPA (Vite)
scripts/        Local dev tooling (see "Sharing your local build" below)
```

## Local development

### 1. Backend

```bash
cd Hotel-system
cp .env.example .env   # fill in POSTGRES_PASSWORD, JWT_SECRET, etc.
docker compose up -d
```

This starts Postgres, Redis, and the API (built from `Dockerfile`) together, listening
on `http://localhost:8081`. Flyway applies all migrations automatically on startup.

Required in `.env`: `POSTGRES_PASSWORD`, `JWT_SECRET`, `STRIPE_SECRET_KEY`. Everything
else (SendGrid, Google OAuth, custom CORS origins) is optional for local use — see the
comments in `.env.example` for what each one is for.

### 2. Frontend

```bash
cd frontend
cp .env.example .env   # VITE_API_BASE_URL defaults to http://localhost:8081
npm install
npm run dev
```

Opens on `http://localhost:5173`.

## Sharing your local build (Cloudflare Tunnel)

Sometimes you want to show a locally running build to someone else, or test it from a
phone, without deploying anywhere. `scripts/` automates exposing both the frontend and
backend to the internet over a free [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
— no account or domain required.

Requires the [`cloudflared`](https://github.com/cloudflare/cloudflared) CLI installed
(`winget install Cloudflare.cloudflared` on Windows).

**Start everything:**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-tunnels.ps1
```

This starts Docker Desktop first if it isn't already running (waits up to 2 minutes for
it to come up), then brings up Postgres/Redis/the API in Docker, starts the Vite dev
server, opens a tunnel for each, and wires the generated URLs into both `.env` files
automatically (`VITE_API_BASE_URL` on the frontend, `CORS_ALLOWED_ORIGINS` on the
backend) — then prints the two public links.

**Stop everything:**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\stop-tunnels.ps1
```

Docker containers are left running; only the tunnels and dev server are stopped.

> **Note:** these are free "quick tunnels" — no uptime guarantee, and the URL is a new
> random `*.trycloudflare.com` address every time you start one. Fine for a quick demo
> or test; for anything you want a stable, permanent link for, see the production
> deployment below instead.

## Production deployment

The backend and frontend each deploy as separate services on [Railway](https://railway.app),
built from `Hotel-system/Dockerfile` and `frontend/Dockerfile` respectively (the
frontend's build serves the compiled Vite output through nginx). Both need their Root
Directory set accordingly in the service's Settings, plus a Postgres and Redis instance
provisioned in the same project and referenced via environment variables.
