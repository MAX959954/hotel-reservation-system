# Folio

A booking platform for independently run hotels and apartments. Guests search and book
stays across a shared catalog, and each property's staff manage their bookings, rooms
and team through a separate extranet.

**Stack:** Spring Boot 3 (Java 17) · PostgreSQL 16 · Redis 7 · Vue 3 + TypeScript + Vite ·
Docker Compose

![Folio home page](docs/screenshots/01-home.png)

---

## Contents

- [Screenshots](#screenshots)
- [How reservations and payments work](#how-reservations-and-payments-work)
1. [Quick start](#1-quick-start)
2. [What's running](#2-whats-running)
3. [Testing the app](#3-testing-the-app)
4. [Optional: e-mail, card payments, Google sign-in](#4-optional-e-mail-card-payments-google-sign-in)
5. [Everyday commands](#5-everyday-commands)
6. [Development without rebuilding (hot reload)](#6-development-without-rebuilding-hot-reload)
7. [Running the automated tests](#7-running-the-automated-tests)
8. [Troubleshooting](#8-troubleshooting)
9. [Project structure](#9-project-structure)
10. [Sharing your local build (Cloudflare Tunnel)](#10-sharing-your-local-build-cloudflare-tunnel)
11. [Production deployment](#11-production-deployment)

---

## Screenshots

All screenshots come from the local Docker build with the seeded demo data
(`docker compose up --build`, see [Quick start](#1-quick-start)).

### Guest side

| | |
|---|---|
| **Search by city and dates**<br>Pick a city, dates and number of guests. Only stays in that city are listed. | ![Search results](docs/screenshots/02-search-results.png) |
| **Hotel page**<br>Description, amenities and the rooms with their nightly price. | ![Hotel rooms](docs/screenshots/05-hotel-rooms.png) |
| **Reserve a room**<br>The total is calculated live: nights × nightly price. | ![Reserve dialog](docs/screenshots/06-reserve-dialog.png) |
| **Sign in with a one-time code**<br>Password first, then a 6-digit code sent by e-mail. | ![Sign in](docs/screenshots/03-sign-in.png) ![Verification code](docs/screenshots/04-verification-code.png) |

### Booking and payment, step by step

| Step | Screen |
|---|---|
| **1. Booking created: Pending**<br>The guest has reserved. The room is held, but the hotel still has to accept it. | ![Pending booking](docs/screenshots/07-booking-pending.png) |
| **2. Hotel confirms** (extranet, owner/staff)<br>Staff see new requests under **Pending** and click **Confirm**. | ![Extranet confirm](docs/screenshots/08-extranet-confirm.png) |
| **3. Guest can pay**<br>A confirmed booking shows **Pay now**. | ![Confirmed, pay now](docs/screenshots/09-booking-confirmed-pay-now.png) |
| **4. Choose a payment method**<br>Card / Google Pay go through Stripe. Bank transfer, cash and crypto are settled offline. | ![Payment methods](docs/screenshots/10-payment-methods.png) |
| **5. Offline method saved**<br>Nothing is charged yet. The booking waits for the hotel to confirm the money arrived. | ![Payment awaiting](docs/screenshots/11-payment-awaiting.png) |
| **6. Hotel marks it as paid**<br>The extranet shows *Awaiting payment · Bank transfer* and a **Mark as paid** button. | ![Mark as paid](docs/screenshots/12-extranet-mark-as-paid.png) |
| **7. Paid, ready for check-in**<br>Only a paid booking can be checked in. | ![Paid, check in](docs/screenshots/13-extranet-paid-check-in.png) |
| **E-mail notifications**<br>Every step sends an e-mail. Locally they all land in Mailpit (http://localhost:8025). | ![Mailpit](docs/screenshots/14-mailpit-emails.png) |

---

## How reservations and payments work

### Booking lifecycle

```
             hotel confirms          guest pays          hotel checks in        hotel completes
  PENDING ─────────────────▶ CONFIRMED ─────────▶ (paid) ─────────────▶ CHECKED_IN ─────────▶ COMPLETED
     │                          │   │
     │ cancelled by guest/hotel │   └── guest didn't arrive (6 h after check-in time) ──▶ NO_SHOW
     │ or 48 h with no answer   │
     ▼                          ▼
 CANCELLED ◀────────────────────┘  cancelled by guest/hotel, or not paid within 24 h
```

**Creating a booking** (`POST /api/bookings`). The backend checks that:
- check-out is after check-in and the stay is at least one night;
- the hotel and its company are active and the room isn't in maintenance or out of order;
- the room isn't already booked for any overlapping date;
- the number of guests fits the room's capacity.

The price is `nightly price × nights`, calculated with `BigDecimal` (exact cents) and
stored on the booking. Later price changes don't affect existing bookings.

**Who does what:**

| Action | Who | Allowed when |
|---|---|---|
| Reserve | guest | room free for the dates |
| Confirm | hotel staff | booking is `PENDING` |
| Pay | guest | booking is `CONFIRMED` |
| Check in | hotel staff | booking is `CONFIRMED` **and paid** |
| Complete | hotel staff | booking is `CHECKED_IN` |
| Cancel | guest or hotel staff | booking is `PENDING` or `CONFIRMED` (not after check-in) |

**Automatic jobs** (`BookingLifecycleScheduler`, run every 15 minutes) keep stale
bookings from blocking rooms:

| Job | Default | Setting |
|---|---|---|
| Cancel a `PENDING` booking the hotel never answered | after 48 h | `BOOKING_PENDING_HOLD_HOURS` |
| Cancel a `CONFIRMED` booking that was never paid | after 24 h | `BOOKING_PAYMENT_DEADLINE_HOURS` |
| Mark a confirmed booking as `NO_SHOW` when the guest didn't check in | 6 h after check-in time | `BOOKING_NO_SHOW_GRACE_HOURS` |
| Expire a card payment that was started but never finished | after 24 h | `PAYMENT_PENDING_EXPIRY_HOURS` |

### Payments

Payment opens only after the hotel has **confirmed** the booking, so a guest never
pays for a stay the hotel hasn't accepted.

| Method | How it is processed | Who marks it as paid |
|---|---|---|
| Credit / debit card, Google Pay | Stripe PaymentIntent. The browser confirms the card with Stripe.js (including 3-D Secure), then the backend asks Stripe whether the payment really succeeded and that it matches this booking | Stripe (confirm call or webhook), never a person |
| Bank transfer, cash on arrival, crypto | Recorded as *Awaiting payment*. Nothing is charged online | Hotel staff with **Mark as paid** once the money has arrived |

Some details worth knowing:
- **One payment per booking.** A failed or abandoned card attempt can be retried.
  Before a retry, the backend checks with Stripe that the earlier attempt wasn't actually
  charged, so a guest can't be charged twice.
- **Stripe webhooks** (`/api/payments/webhook`, signature verified) reconcile
  everything that happens outside the app: a payment that succeeded after the browser
  was closed, a failed payment, or a refund issued from the Stripe Dashboard.
- **Amounts** are `numeric(12,2)` in the database and converted to cents for Stripe.

### Cancellation and refunds

| Who cancels | When | Refund |
|---|---|---|
| Guest | more than **48 h** before check-in (`BOOKING_FREE_CANCELLATION_HOURS`) | full refund |
| Guest | later than that | refund minus a late-cancellation fee of **1 night** (`BOOKING_LATE_CANCELLATION_FEE_NIGHTS`) |
| Hotel, admin, or an automatic job | any time before check-in | full refund |
| Guest doesn't show up (`NO_SHOW`) | — | nothing is refunded |

The cancellation and the refund happen in one database transaction: if Stripe refuses
the refund, the booking stays as it was, so the guest never ends up with a cancelled
stay and no money back. Card refunds are sent to Stripe with an idempotency key, so a
retry can't refund twice. Hotel owners and managers can also issue a full or partial
refund through the API (`PATCH /api/payments/{id}/refund`). A **full** refund of a
confirmed booking cancels it and frees the room.

---

## 1. Quick start

### Prerequisites

| Tool | Why | Get it |
|---|---|---|
| **Git** | clone the repository | https://git-scm.com/downloads |
| **Docker Desktop** | runs the whole stack: database, cache, API and website | https://www.docker.com/products/docker-desktop/ |

That's all. You **don't** need Java, Node.js, PostgreSQL or Redis on your machine, and
you **don't** need any `.env` file. Every setting has a working local default.

> **Windows:** Docker Desktop needs WSL 2. If the installer asks, let it enable WSL
> and restart. If Docker later complains about WSL, run `wsl --update` in PowerShell.
> Give Docker at least 4 GB of RAM (Settings → Resources).

### Run it

```bash
git clone https://github.com/<owner>/<repo>.git
cd <repo>                      # the folder that contains docker-compose.yml
docker compose up --build
```

Make sure **Docker Desktop is running** first (the whale icon in the tray or menu bar is
steady, not animating).

The **first** start takes about 5–10 minutes: Docker downloads the base images, builds the
backend with Gradle and the frontend with Vite. Later starts take seconds.

The backend is ready when its log shows:

```
hotel_app  | ... Started HotelSystemApplication in 12.3 seconds
```

Then open:

| What | URL |
|---|---|
| **Website** | **http://localhost:5173** |
| **Mail inbox** (all e-mails the app sends) | **http://localhost:8025** |
| API | http://localhost:8081 |
| API health check | http://localhost:8081/actuator/health (should show `{"status":"UP"}`) |

To stop, press `Ctrl+C` in that terminal (or run `docker compose down`, see
[Everyday commands](#5-everyday-commands)).

---

## 2. What's running

`docker compose up` starts five containers:

| Container | What it is | Host port |
|---|---|---|
| `hotel_frontend` | Vue app, built and served by nginx | `5173` |
| `hotel_app` | Spring Boot REST API (Spring profile `local`) | `8081` |
| `hotel_db` | PostgreSQL 16 | `5432` |
| `hotel_redis` | Redis 7 (caching, token blacklist) | `6379` |
| `hotel_mailpit` | [Mailpit](https://mailpit.axllent.org/): fake SMTP server + web inbox | `8025` (web), `1025` (SMTP) |

**Configuration profiles.** The same code and Docker image run everywhere; only the
Spring profile and environment variables differ:

| Profile | Used by | What it sets |
|---|---|---|
| `local` (`application-local.yml`) | `docker compose`, IDE runs | e-mail via SMTP to Mailpit, a public dev JWT key, sign-in codes also printed to the log |
| `prod` (`application-prod.yml`) | production server (`deploy/`) | real secrets from environment variables, real e-mail over SMTP (e.g. Gmail); **refuses to start** if a dev setting (dev JWT key, codes in the log) is present |
| `test` (`src/test/resources/application-test.yml`) | `./gradlew test` | throwaway PostgreSQL container (Testcontainers) |

**Database: schema and demo data are separate.** On start the backend runs Flyway
(`Hotel-system/src/main/resources/db/`):

| Folder | Contents | Loaded in |
|---|---|---|
| `db/migration` | the schema only (`V1__baseline_schema.sql`, future `V2__...`) | everywhere: local, tests, production |
| `db/seed` | demo catalogue: 4 hotel companies, 44 hotels and apartments in 10 cities (Paris, Rome, Kyoto, Barcelona, Dubai, London, New York, Tokyo, Santorini, Maldives), rooms, amenities, translations, reviews | `local` profile (and optionally a demo deployment) |
| `db/seed-accounts` | the three test accounts below | `local` profile **only** |

The seed files are Flyway `afterMigrate` callbacks that only insert into an empty database,
so they never overwrite anything you create while using the app. The data lives in a
Docker volume and survives restarts; `docker compose down -v` resets it to the seed state.

### Test accounts

| E-mail | Password | Role |
|---|---|---|
| `guest@folio.local` | `Folio2026!` | guest: book and pay |
| `owner@folio.local` | `Folio2026!` | owner of *Wayside Hospitality Group*: manage its hotels, rooms, bookings, staff |
| `admin@folio.local` | `Folio2026!` | administrator: host applications, users |

Signing in asks for a 6-digit code after the password. Locally it arrives in **Mailpit**
(http://localhost:8025); see [3.2](#32-sign-in-and-create-an-account).

---

## 3. Testing the app

### 3.1 Browse as a visitor (no account)

Open http://localhost:5173 and browse: pick a city, open a hotel, switch the language (EN
menu, top right). Hotel descriptions are translated into EN, SK, RU, ES, FR, IT and JA.

### 3.2 Sign in and create an account

Sign-in and sign-up are confirmed with a **6-digit code sent by e-mail**. Locally every
e-mail lands in **Mailpit** instead of a real mailbox.

**Sign in with a test account:** click **Sign in**, enter e.g. `guest@folio.local` /
`Folio2026!`, then open **http://localhost:8025**. The newest message, "Your Folio
verification code", contains the code (valid for 10 minutes). Enter it and you're in.

**Create your own account:** click **Create account**, fill in the form (you must be 18+,
password at least 8 characters), take the code from Mailpit the same way.

> Any e-mail address works locally, even a made-up one, because nothing is actually sent.
> Booking and payment notifications show up in Mailpit too.
>
> Fallback: the code is also printed to the backend log:
> `docker logs hotel_app 2>&1 | Select-String "Verification code"` (Windows) or
> `docker logs hotel_app 2>&1 | grep "Verification code"` (macOS/Linux).

### 3.3 Book a stay (as a guest)

1. Open a hotel, choose dates (at least one night) and guests, then **Reserve**.
2. The booking appears under **My bookings** with status **Pending**. Hotel staff must
   confirm it first (next step).

### 3.4 Act as hotel staff

Sign in as **`owner@folio.local`** (owner of *Wayside Hospitality Group*). The account
menu has **Manage bookings** and **Manage hotels**, where you can:

- **Confirm**, **Check in**, **Complete** or **Cancel** bookings (confirm the one you
  made in 3.3). Wayside Hospitality Group owns the hotels in Paris, Rome, Tokyo, New
  York and more.
- edit hotels, add rooms, change room status;
- invite staff by e-mail (they get a role in this company only).

Other seeded companies (no owner account): `Atoll & Tide Resorts`, `Meridian Collection`,
`Casa & Co Hospitality`. To attach your own account to one of them:

```bash
docker exec hotel_db psql -U hotel_user -d hotel_db -c "INSERT INTO company_users (company_role, status, invited_at, joined_at, user_id, company_id) SELECT 'OWNER', 'ACTIVE', now(), now(), u.id, c.id FROM users u, companies c WHERE u.email = 'you@example.com' AND c.name = 'Meridian Collection';"
```
then sign out and back in.

### 3.5 Pay for a booking

Once a booking is **Confirmed**, the guest sees **Pay now** in **My bookings**.

- **Cash on arrival / Bank transfer / Crypto** work without any setup. The payment is
  recorded as **Awaiting payment**. Staff then click **Mark as paid** in **Manage
  bookings** once the money has actually arrived.
- **Credit / debit card and Google Pay** need Stripe test keys, see
  [section 4](#4-optional-e-mail-card-payments-google-sign-in).

### 3.6 Act as an administrator

Sign in as **`admin@folio.local`**. The account menu includes the admin pages:

- **Admin** (`/admin/applications`): approve or reject host applications and open the
  uploaded documents;
- **Users** (`/admin/users`): search users, change account status, grant or revoke roles.

**Full host onboarding flow:** as `guest@folio.local` (or your own account) open
**Become a host** and submit the application with a document. Then approve it as
`admin@folio.local`. The applicant
becomes the **owner** of the new company and can add hotels in **Manage hotels**.

> Tip: use two different browsers (or a private window) to be signed in as guest and
> staff/admin at the same time.

---

## 4. Optional: e-mail, card payments, Google sign-in

Everything above works without any keys. To enable the external integrations, create
**`Hotel-system/.env`** from the example and fill in only what you need:

```bash
cp Hotel-system/.env.example Hotel-system/.env          # macOS / Linux
Copy-Item Hotel-system\.env.example Hotel-system\.env   # Windows
```

| Feature | Variables in `Hotel-system/.env` | Where to get them |
|---|---|---|
| Real e-mails instead of Mailpit | `MAIL_PROVIDER=sendgrid`, `SENDGRID_API_KEY` (starts with `SG.`), `MAIL_FROM` (a sender verified in SendGrid) | https://app.sendgrid.com/settings/api_keys |
| Card / Google Pay payments | `STRIPE_SECRET_KEY` (`sk_test_...`) | https://dashboard.stripe.com → Test mode → Developers → API keys |
| Stripe webhooks (optional) | `STRIPE_WEBHOOK_SECRET` (`whsec_...`) | `stripe listen --forward-to localhost:8081/api/payments/webhook` |
| Google sign-in | `GOOGLE_CLIENT_ID` | Google Cloud Console → Credentials → OAuth client ID |

The frontend needs the **public** keys at build time. Put them in a **`.env` file in the
repository root** (next to `docker-compose.yml`):

```
VITE_STRIPE_PUBLISHABLE_KEY=pk_test_...
VITE_GOOGLE_CLIENT_ID=....apps.googleusercontent.com
```

Apply the changes with `docker compose up --build`. With Stripe in test mode, pay with
card **4242 4242 4242 4242**, any future expiry date and any CVC.

> `.env` files are git-ignored. Never commit real keys.

---

## 5. Everyday commands

Run these from the repository root.

| Task | Command |
|---|---|
| Start everything (foreground, logs in the terminal) | `docker compose up --build` |
| Start in the background | `docker compose up --build -d` |
| Stop (keeps the database) | `docker compose down` |
| **Reset the database** to fresh seed data | `docker compose down -v` then `docker compose up --build` |
| Follow backend logs | `docker logs -f hotel_app` |
| Status of all containers | `docker compose ps` |
| Rebuild after pulling new code | `docker compose up --build` |
| Open a SQL shell | `docker exec -it hotel_db psql -U hotel_user -d hotel_db` |

---

## 6. Development without rebuilding (hot reload)

For frontend work it's faster to run only the backend in Docker and the Vite dev server
directly. This needs **Node.js 20+**.

```bash
docker compose up --build -d postgres redis app
docker compose stop frontend          # frees port 5173 if the Docker frontend was running

cd frontend
cp .env.example .env                  # VITE_API_BASE_URL=http://localhost:8081
npm install
npm run dev                           # http://localhost:5173, reloads on save
```

For backend changes, rebuild the API container: `docker compose up --build -d app`.

To run the backend from **IntelliJ IDEA** instead (JDK 17): stop the API container
(`docker compose stop app`) and start the dependencies with
`docker compose up -d postgres redis mailpit`. Then run `HotelSystemApplication` with the
environment variables `SPRING_PROFILES_ACTIVE=local` and `PORT=8081`. The `local`
profile supplies everything else (database password, dev JWT key, Mailpit).

---

## 7. Running the automated tests

**Backend** (JUnit 5, about 350 tests). Needs **JDK 17** and **Docker running**:
- integration tests start their own throwaway **PostgreSQL 16** container
  (Testcontainers) and run the real Flyway migrations, so they test the same database
  engine as production;
- a few tests need Redis on `localhost:6379`.

```bash
docker compose up -d redis
cd Hotel-system
./gradlew test          # Windows: .\gradlew test
```

The HTML report is in `Hotel-system/build/reports/tests/test/index.html`.

**Frontend** (type-check + production build):

```bash
cd frontend
npm ci
npm run build
```

CI runs both on every push and pull request to `main` (`.github/workflows/ci.yml`).

---

## 8. Troubleshooting

**"Cannot reach the server. Is the backend running?" on the website**
- Check http://localhost:8081/actuator/health. If it doesn't load, the backend is still
  starting or crashed: `docker logs hotel_app --tail 80`.
- If it shows `UP`, it's usually CORS. `Hotel-system/.env` contains a stale
  `CORS_ALLOWED_ORIGINS` (e.g. an old `trycloudflare.com` URL). Make that line
  `CORS_ALLOWED_ORIGINS=`, then run `docker compose up -d app` and hard-refresh the
  page (`Ctrl+F5`).

**`Validate failed: Migrations have failed validation` / `checksum mismatch` / `Detected applied migration not resolved locally`**
Your local database was created with the old migrations (before schema and demo data
were split into `db/migration` + `db/seed`). Reset it once: `docker compose down -v`,
then `docker compose up --build`.

**`password authentication failed for user "hotel_user"` in the logs**
The database volume was created earlier with a different password. Reset it:
`docker compose down -v`, then `docker compose up --build`. This deletes local data;
the seed data is recreated.

**`Bind for 0.0.0.0:5432 failed: port is already allocated`** (or 5173 / 8081 / 6379)
Something else uses that port, often a locally installed PostgreSQL or Redis, or a
running `npm run dev`. Stop it, or change the left-hand side of the port mapping in
`docker-compose.yml` / `Hotel-system/docker-compose.yml` (e.g. `"5433:5432"`).

**`error during connect ... docker daemon is not running`**
Start Docker Desktop and wait until it's fully up, then retry.

**No verification code arrives**
Look in Mailpit (http://localhost:8025, newest message first) or in the backend log (see
3.2). If `MAIL_PROVIDER=sendgrid` is set in `Hotel-system/.env`, codes go to the real
mailbox instead. Too many requests in a row are rate-limited (30 s between codes, 5 per
hour).

**Tests fail with `Could not find a valid Docker environment`**
Testcontainers needs Docker. Start Docker Desktop and run the tests again.

**Account locked after wrong passwords**
5 failed attempts lock the account for 15 minutes (brute-force protection). Wait, or
reset the database.

**Changes in the code don't show up**
Always start with `--build`. Without it Docker reuses the previously built image.

---

## 9. Project structure

```
.
├── docker-compose.yml      One-command local stack (includes Hotel-system/docker-compose.yml + frontend)
├── Hotel-system/           Backend: Spring Boot API
│   ├── src/main/java/      Packages by feature: booking, payment, hotels, room, reviews, user, companies, ...
│   ├── src/main/resources/ application*.yml (base / local / prod), db/migration (schema),
│   │                       db/seed (demo catalogue), db/seed-accounts (local test accounts)
│   ├── src/test/           Unit, controller and integration tests
│   ├── docker-compose.yml  Postgres + Redis + Mailpit + API (local defaults for every setting)
│   ├── Dockerfile
│   └── .env.example        Optional overrides / API keys
├── frontend/               Frontend: Vue 3 SPA (Vite, Pinia, vue-i18n, Tailwind)
│   ├── src/                api/, components/, views/, stores/, locales/, router/
│   ├── Dockerfile          Builds the app and serves it with nginx
│   └── .env.example
├── scripts/                Cloudflare Tunnel helpers (see below)
└── .github/workflows/      CI
```

---

## 10. Sharing your local build (Cloudflare Tunnel)

To show a locally running build to someone else, or test it from a phone, `scripts/`
exposes both the frontend and backend over a free
[Cloudflare quick tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/).
No account or domain is needed. Windows only (PowerShell). Requires
[`cloudflared`](https://github.com/cloudflare/cloudflared):
`winget install Cloudflare.cloudflared`, then open a new terminal.

```powershell
# from the repository root
powershell -ExecutionPolicy Bypass -File scripts\start-tunnels.ps1
```

The script:
1. starts Docker Desktop if needed;
2. builds and starts Postgres, Redis and the API, and waits until the API is healthy;
3. starts the Vite dev server and opens a tunnel for each side;
4. writes the generated URLs into both `.env` files;
5. prints the two public links.

When you're done:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\stop-tunnels.ps1
```

This stops the tunnels and the dev server and **resets both `.env` files to local URLs**,
so `docker compose up` works normally again. Quick-tunnel URLs are random and change on
every start, with no uptime guarantee. Fine for a demo, not for a permanent link.

---

## 11. Production deployment

Production runs on a single Linux server (VPS) with Docker: Caddy (automatic HTTPS),
the frontend, the API with the `prod` Spring profile, PostgreSQL and Redis, all on one
domain. **Step-by-step guide: [`deploy/README.md`](deploy/README.md)**: server, domain,
Gmail SMTP / Stripe / Google setup, deploy, backups, automatic deploys from GitHub Actions.

**No server yet?** The production stack also runs on your own PC with `DOMAIN=localhost`
(HTTPS, `prod` profile, real e-mail), see
[Try the production build on your own PC first](deploy/README.md#try-the-production-build-on-your-own-pc-first).

In short, on the server:

```bash
git clone https://github.com/<owner>/<repo>.git ~/folio && cd ~/folio
sudo bash deploy/setup-server.sh          # once: Docker, firewall, auto-updates
cp deploy/.env.example deploy/.env        # fill in domain + secrets
bash deploy/deploy.sh                     # build, start, wait until healthy
```

**How production differs from local:**

| | Local (`docker compose up`) | Production (`deploy/`) |
|---|---|---|
| Spring profile | `local` | `prod` |
| E-mail | Mailpit (fake inbox) | real SMTP, e.g. Gmail (or SendGrid) |
| Secrets | public dev defaults | `deploy/.env` on the server only |
| Data | schema + demo catalogue + test accounts | schema + demo catalogue (configurable), **no** test accounts |
| HTTPS | no | Caddy + Let's Encrypt |
| Exposed ports | all services, for debugging | only 80/443 |

With `prod` active, `config.ProductionSafetyCheck` stops the application at startup if
`JWT_SECRET` is missing, too short or equal to the public dev key, if `MAIL_DEV_LOG_OTP`
is on, or if the local test accounts (`db/seed-accounts`) are enabled. Missing e-mail
or Stripe credentials only log a warning.

### Known limitations

- **No general-purpose API rate limiting.** Login and OTP requests are throttled
  (`app.login.*` in `application.yml`, `OtpService.MAX_REQUESTS_PER_HOUR`), but the rest
  of the API has no per-IP/per-user limit. A production system with real traffic would
  add this at the reverse proxy or in-app (e.g. Bucket4j).
