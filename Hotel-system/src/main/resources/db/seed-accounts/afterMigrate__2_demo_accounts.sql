-- LOCAL TEST ACCOUNTS - one per role, all with the password  Folio2026!
--
--   guest@folio.local   guest (can book and pay)
--   owner@folio.local   owner of "Wayside Hospitality Group" (manage its hotels/bookings)
--   admin@folio.local   administrator (host applications, users)
--
-- Sign-in still needs the e-mailed code: locally it lands in Mailpit
-- (http://localhost:8025) and in the backend log.
--
-- Loaded ONLY by the "local" profile (db/seed-accounts in spring.flyway.locations).
-- These passwords are public, so never enable this location anywhere reachable from the
-- internet - config.ProductionSafetyCheck refuses to start the "prod" profile with it.
--
-- Flyway "afterMigrate" callback, re-run on every startup: every statement only inserts
-- what is missing, so it is idempotent and never overwrites changes made in the app.
-- Runs after afterMigrate__1_demo_data.sql (the owner needs its company to exist).

INSERT INTO users (first_name, last_name, password_hash, email, email_verified, enabled,
                   account_status, failed_login_attempts, date_of_birth, created_at, updated_at)
SELECT v.first_name, v.last_name, '$2a$10$wdjucp2E3ARXAdmxAEIh2uGIVizdM2sMK6jtpB.qJTx9vYn5g4b/O', v.email, true, true,
       'APPROVED', 0, DATE '1995-05-15', now(), now()
FROM (VALUES ('Gina',  'Guest', 'guest@folio.local'),
             ('Oscar', 'Owner', 'owner@folio.local'),
             ('Ada',   'Admin', 'admin@folio.local')) AS v(first_name, last_name, email)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email);

INSERT INTO user_roles (user_id, role)
SELECT u.id, r.role
FROM (VALUES ('guest@folio.local', 'GUEST'),
             ('owner@folio.local', 'GUEST'),
             ('owner@folio.local', 'HOTEL_MANAGER'),
             ('admin@folio.local', 'GUEST'),
             ('admin@folio.local', 'ADMIN')) AS r(email, role)
JOIN users u ON u.email = r.email
WHERE NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role = r.role);

INSERT INTO company_users (company_role, status, invited_at, joined_at, user_id, company_id)
SELECT 'OWNER', 'ACTIVE', now(), now(), u.id, c.id
FROM users u
JOIN companies c ON c.name = 'Wayside Hospitality Group'
WHERE u.email = 'owner@folio.local'
  AND NOT EXISTS (SELECT 1 FROM company_users cu WHERE cu.user_id = u.id AND cu.company_id = c.id);
