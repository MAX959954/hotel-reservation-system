# Production deployment on your own server (VPS)

This folder runs Folio in production on a single Linux server with Docker:

```
Internet ──HTTPS──▶ Caddy (80/443, automatic Let's Encrypt)
                      ├── /api/*, /company_users*, /uploads/avatars/*, /actuator/health ──▶ Spring Boot API (profile "prod")
                      └── everything else ─────────────────────────────────────────────────▶ Vue app (nginx)
                    API ──▶ PostgreSQL 16, Redis 7 (internal network only, not exposed)
```

| File | Purpose |
|---|---|
| `docker-compose.prod.yml` | the production stack |
| `docker-compose.home.yml` | add-on for hosting from your own PC via Tailscale Funnel |
| `Caddyfile` | HTTPS + routing |
| `.env.example` | every production setting and secret; copy to `.env` on the server |
| `setup-server.sh` | one-time server preparation (Docker, firewall, auto-updates, swap) |
| `deploy.sh` | build and (re)start, wait until healthy. Run it for every update |
| `backup.sh` | daily database backup (keeps 14) |
| `../.github/workflows/deploy.yml` | optional automatic deploy after CI passes on `main` |

The local development setup (`docker compose up` in the repository root) is separate and
unaffected. Production uses the same Dockerfiles, but with the `prod` Spring profile and
real secrets. The app refuses to start if a local-development setting leaks into
production (see `config.ProductionSafetyCheck`).

---

## Try the production build on your own PC first

Before renting a server, you can run the **same production stack** on your own computer:
the `prod` Spring profile, HTTPS through Caddy, real e-mail, no test accounts. This checks
that the production configuration works and is handy for a demo. It runs wherever Docker
runs: Docker Desktop on Windows or macOS, WSL, or Linux. Commands run from the repository root.

**1. Create the settings file**

```bash
cp deploy/.env.example deploy/.env                 # PowerShell: Copy-Item deploy\.env.example deploy\.env
```

**2. Fill in `deploy/.env`**

| Variable | Value for the local run |
|---|---|
| `DOMAIN` | `localhost` |
| `ACME_EMAIL` | can stay as is (not used for `localhost`) |
| `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET` | three **different** random values, see below |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | Gmail address + app password ([section 5](#e-mail-via-gmail-required-for-sign-in-and-sign-up)). **Required**: without test accounts, sign-in only works with e-mailed codes |
| `STRIPE_*`, `GOOGLE_CLIENT_ID` | optional, see below |

A random secret, on any OS with Docker (run it once per value):

```bash
docker run --rm alpine sh -c "head -c 32 /dev/urandom | base64"
```

Optional services:
- **Google sign-in:** in the OAuth client, add `https://localhost` to *Authorized
  JavaScript origins*.
- **Stripe:** the test keys work as they are. Stripe can't reach `localhost`, so webhooks,
  which move a paid booking to *confirmed*, need the [Stripe CLI](https://docs.stripe.com/stripe-cli):
  `stripe listen --forward-to https://localhost/api/payments/webhook --skip-verify`. Put
  the `whsec_...` it prints into `STRIPE_WEBHOOK_SECRET` and restart the stack (step 3).

**3. Build and start** (the first build takes 5–10 minutes)

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env ps
```

Wait until `app`, `postgres` and `redis` show `(healthy)`. Ports 80 and 443 must be free.
The local development stack (`docker compose up` in the root) uses other ports, so both
can run side by side.

**4. Open https://localhost**

For `localhost`, Caddy uses its own local certificate instead of Let's Encrypt, so the
browser shows a warning: choose **Advanced → Proceed to localhost** (Chrome/Edge: *Proceed
to localhost (unsafe)*). This happens only locally; on a server with a real domain the
certificate is trusted. Then check:

- https://localhost/actuator/health → `{"status":"UP"}`;
- the home page lists the demo hotels;
- register with your real e-mail address, and the code arrives by e-mail (check spam).

To become an administrator, use the SQL command in [section 6](#6-deploy).

**5. Stop**

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env down      # keeps the data
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env down -v   # also deletes the database
```

If something fails, see [Troubleshooting](#9-troubleshooting). The API log is
`docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env logs app`.

---

## Host it from your own PC (Tailscale Funnel)

No server? The production stack can run on your own computer and be public on the
internet **while the computer is on**, through [Tailscale Funnel](https://tailscale.com/docs/features/tailscale-funnel):

- free, with a fixed address `https://<machine>.<tailnet>.ts.net` and a real, trusted
  certificate;
- no router port forwarding: Caddy listens only on `127.0.0.1:8088`, and Tailscale
  forwards public HTTPS traffic to it.

This is fine for a demo or a thesis defence. It is not a real production setup: the site is
down whenever the PC sleeps or is off, it runs over your home connection, and Funnel
traffic has bandwidth limits.

**1. Install Tailscale and turn on Funnel** (once)

1. Install Tailscale (Windows: `winget install tailscale.tailscale`, or https://tailscale.com/download)
   and sign in, e.g. with Google or GitHub.
2. In the admin console (https://login.tailscale.com/admin), open **Machines** →
   your PC → **Edit machine name** and choose a short name, e.g. `folio`.
3. Open **DNS**, check that **MagicDNS** is on, and enable **HTTPS Certificates**.
4. Find the address: `tailscale status --json` shows it as `"DNSName"`, e.g.
   `folio.tail1a2b3c.ts.net.`. Use it without the trailing dot.

**2. Configure `deploy/.env`**

Set up the file as in [the local run above](#try-the-production-build-on-your-own-pc-first), with:

```
DOMAIN=folio.tail1a2b3c.ts.net
```

Use your own address, without `https://`. Google sign-in: add `https://<that address>` to
*Authorized JavaScript origins*. Stripe: the webhook URL `https://<that address>/api/payments/webhook`
works as on a server.

**3. Start the stack and publish it**

```bash
docker compose -f deploy/docker-compose.prod.yml -f deploy/docker-compose.home.yml --env-file deploy/.env up -d --build
tailscale funnel --bg 8088
```

The first time, `tailscale funnel` prints a link to approve Funnel for your tailnet: open
it, approve, and run the command again. `--bg` keeps the Funnel on, including after a
reboot. `tailscale funnel status` shows the public URL. Open it from a phone on mobile
data to check it is really public. A new address can take a few minutes to become
reachable.

On Windows, two scripts do steps 3 and the shutdown for you, from any folder:
`scripts\public-start.ps1` (starts Docker Desktop if needed, builds, waits until healthy,
turns on Funnel; add `-NoBuild` to skip rebuilding) and `scripts\public-stop.ps1` (Funnel off,
containers stopped, data kept; `-DeleteData` also wipes the database).

**Keep it running**
- Windows: *Settings → System → Power* → set sleep to **Never** while plugged in.
- Docker Desktop: *Settings → General* → **Start Docker Desktop when you sign in**. The
  containers restart automatically (`restart: unless-stopped`).

**Stop publishing:** `tailscale funnel reset`. The stack
itself stops with the `down` command from the section above, adding
`-f deploy/docker-compose.home.yml` after the first `-f`.

---

## 1. Get a server

Any VPS with **Ubuntu 22.04 or 24.04**, **≥ 2 vCPU, ≥ 4 GB RAM** (the backend and
frontend are built on the server) and a public IPv4 address works. For example:

- **Hetzner Cloud**, e.g. a CX22 / CAX11 (~€4–5/month, EU data centres, simplest option);
- **Oracle Cloud Always Free**, an Ampere A1 VM (ARM, free; sign-up needs a card, and free
  capacity is sometimes unavailable). All images used here support ARM.

Prices and plans change, so check the provider's site.

When creating the server, **add your SSH public key** (`ssh-keygen -t ed25519` on your
PC if you don't have one; the public key is `~/.ssh/id_ed25519.pub`). Note the server's IP.

## 2. Get a domain and point it at the server

HTTPS needs a domain name. Options:

- **Free:** a subdomain from [DuckDNS](https://www.duckdns.org): sign in, create e.g.
  `folio-demo` → `folio-demo.duckdns.org`, set its IP to your server's IP.
- **Your own domain** (~€1–10/year from any registrar): create a DNS **A record**
  `folio.yourdomain.com → <server IP>` (or `@` for the bare domain).

Check it resolves before continuing: `nslookup folio-demo.duckdns.org` should print your
server's IP. Caddy can only get a certificate once DNS points at the server.

## 3. Prepare the server

```bash
ssh root@<server-ip>            # Oracle: ssh ubuntu@<server-ip>

git clone https://github.com/<owner>/<repo>.git ~/folio
cd ~/folio
sudo bash deploy/setup-server.sh
```

`setup-server.sh` installs Docker, enables automatic security updates, opens only ports
22/80/443 in the firewall and adds 2 GB swap. Oracle Cloud additionally needs ports 80
and 443 opened in the VCN **Security List / Network Security Group** in the web console.

## 4. Configure

```bash
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
nano deploy/.env
```

Fill in (generate each random secret separately with `openssl rand -base64 32`):

| Variable | Value |
|---|---|
| `DOMAIN` | e.g. `folio-demo.duckdns.org` (no `https://`) |
| `ACME_EMAIL` | your e-mail (Let's Encrypt expiry notices) |
| `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET` | random secrets |
| e-mail (Gmail SMTP), Stripe, Google | see section 5 |
| `FLYWAY_LOCATIONS` | keep the default to get the demo hotel catalogue |

The external services can be added later: without them the site runs, but e-mail codes
(and therefore sign-up/sign-in), card payments or Google sign-in won't work. Re-run
`deploy.sh` after changing `.env`.

## 5. External services

### E-mail via Gmail (required for sign-in and sign-up)
Sign-in and sign-up codes are e-mailed, so without this nobody can log in. The default
is plain SMTP through a Gmail account: free, about 500 messages/day.

1. Use a Gmail account (a separate one for the project is a good idea) and turn on
   **2-Step Verification**: https://myaccount.google.com/security.
2. Create an **app password**: https://myaccount.google.com/apppasswords → name it
   "Folio" → Google shows a 16-character password. Your normal Gmail password does
   **not** work here.
3. In `.env`:
   ```
   MAIL_PROVIDER=smtp
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=your.address@gmail.com
   SMTP_PASSWORD=the16charapppassword
   MAIL_FROM=Folio <your.address@gmail.com>
   ```
   With Gmail, `MAIL_FROM` must use the same address as `SMTP_USERNAME`.

Other providers work the same way with their SMTP host, port and credentials (e.g.
Brevo, Mailjet). SendGrid's HTTP API is still supported (`MAIL_PROVIDER=sendgrid`,
`SENDGRID_API_KEY`), but SendGrid no longer has a free plan.

### Stripe (card payments, **test mode**)
1. https://dashboard.stripe.com → switch on **Test mode**.
2. **Developers → API keys**: copy the *Publishable key* (`pk_test_...`) →
   `STRIPE_PUBLISHABLE_KEY`, and the *Secret key* (`sk_test_...`) → `STRIPE_SECRET_KEY`.
3. **Developers → Webhooks → Add endpoint**:
   - URL: `https://<DOMAIN>/api/payments/webhook`
   - events: `payment_intent.succeeded`, `payment_intent.payment_failed`,
     `charge.refunded`, `charge.dispute.created`
   - copy the *Signing secret* (`whsec_...`) → `STRIPE_WEBHOOK_SECRET`.
4. Test card: **4242 4242 4242 4242**, any future expiry, any CVC.

Keep Stripe in test mode for a portfolio or thesis demo: no real money moves.

### Google sign-in
1. https://console.cloud.google.com → create a project.
2. **APIs & Services → OAuth consent screen**: external, fill in the app name and your
   e-mail, add yourself (and anyone who will test) as test users.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**, type *Web
   application*. Under **Authorized JavaScript origins** add `https://<DOMAIN>`.
4. Copy the client ID (`....apps.googleusercontent.com`) → `GOOGLE_CLIENT_ID`. The same
   value is used by both the backend and the frontend.

The "Continue with Google" button appears in the sign-in and sign-up dialogs once
`GOOGLE_CLIENT_ID` is set.

## 6. Deploy

```bash
bash deploy/deploy.sh
```

The first run builds both images on the server (5–10 minutes). The script waits until
the API reports healthy, then prints the URL. Open `https://<DOMAIN>`: the certificate
is issued automatically on the first request (a few seconds).

**Check it:**
- `https://<DOMAIN>/actuator/health` → `{"status":"UP"}`
- the home page lists the demo hotels;
- register with a real e-mail address, and the code arrives by e-mail.

The first admin: register normally, then on the server:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec postgres \
  psql -U hotel_user -d hotel_db -c "INSERT INTO user_roles (user_id, role) SELECT id, 'ADMIN' FROM users WHERE email = 'you@example.com';"
```

Sign out and back in.

## 7. Operations

| Task | Command (in `~/folio`) |
|---|---|
| Update to the latest `main` | `bash deploy/deploy.sh` |
| Logs | `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env logs -f app` |
| Status | `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env ps` |
| Restart the API | `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env restart app` |
| Manual backup | `bash deploy/backup.sh` |

**Daily backups** (03:30, 14 kept in `/var/backups/folio`):

```bash
(crontab -l 2>/dev/null; echo "30 3 * * * bash $HOME/folio/deploy/backup.sh >> /var/log/folio-backup.log 2>&1") | crontab -
```

Backups on the same server don't survive losing the server. For anything important,
also copy them elsewhere, or use the provider's snapshot/backup feature.

## 8. Automatic deploys (optional)

`.github/workflows/deploy.yml` redeploys after the CI workflow passes on `main`.

1. On your PC, create a key used only for deploys: `ssh-keygen -t ed25519 -f folio_deploy -N ""`.
2. On the server, append `folio_deploy.pub` to `~/.ssh/authorized_keys` of the user that
   owns `~/folio`. That user must be able to run `docker` (in the `docker` group, or root).
3. GitHub → repository → **Settings → Secrets and variables → Actions**:
   - secrets: `DEPLOY_HOST` (IP or domain), `DEPLOY_USER`, `DEPLOY_SSH_KEY` (content of the
     private key file `folio_deploy`);
   - variables: `DEPLOY_ENABLED` = `true`, optionally `DEPLOY_PATH` (default `~/folio`).
4. Push to `main`: CI runs the tests, then the Deploy workflow runs `deploy.sh` on the
   server. You can also start it by hand under **Actions → Deploy → Run workflow**.

If the repository is private, the server needs read access for `git pull`: add a GitHub
*deploy key* (read-only) on the server, or clone via HTTPS with a token.

## 9. Troubleshooting

**Site not reachable / certificate errors**
- DNS must point at the server: `nslookup <DOMAIN>`.
- Ports 80 and 443 must be open (`ufw status`; on Oracle also the Security List).
- Caddy's log: `docker compose ... logs caddy`. Let's Encrypt rate-limits repeated
  failures, so fix DNS/ports first, then restart Caddy.

**`Unsafe production configuration: ...` and the API stops**
`config.ProductionSafetyCheck` found a development setting: a missing, short or dev
`JWT_SECRET`, `MAIL_DEV_LOG_OTP` on, or `db/seed-accounts` in `FLYWAY_LOCATIONS`. Fix
`deploy/.env` and redeploy.

**No e-mails**
`docker compose ... logs app | grep -i "mail"`. `535 Authentication failed` means
`SMTP_USERNAME` / `SMTP_PASSWORD` is wrong: use a Gmail **app password**, not the account
password. Also check the recipient's spam folder. Some VPS providers block outgoing mail
ports for new accounts; port 587 is normally open, and if not, ask their support.

**Build killed / out of memory on the server**
Use a server with ≥ 4 GB RAM; `setup-server.sh` also adds swap.

**Google button missing**
`GOOGLE_CLIENT_ID` must be set **before** building (it's compiled into the frontend).
Set it and run `deploy.sh` again. `origin_mismatch` from Google means the authorized
JavaScript origin isn't exactly `https://<DOMAIN>`.
