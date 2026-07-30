-- Demo guest identities for seeded reviews. These accounts never log in; the hash is a
-- placeholder, not a credential meant to be used.
INSERT INTO users (first_name, last_name, password_hash, email, phone, email_verified, enabled, account_status, created_at, updated_at)
VALUES
    ('Amara', 'Okafor', '$2a$10$demoSeedHashNotARealCredential000000000000000000000000', 'amara.okafor@example.com', '+447700900001', true, true, 'APPROVED', now(), now()),
    ('Lukas', 'Weber', '$2a$10$demoSeedHashNotARealCredential000000000000000000000001', 'lukas.weber@example.com', '+491700900002', true, true, 'APPROVED', now(), now()),
    ('Priya', 'Nair', '$2a$10$demoSeedHashNotARealCredential000000000000000000000002', 'priya.nair@example.com', '+919810900003', true, true, 'APPROVED', now(), now()),
    ('Diego', 'Fernandez', '$2a$10$demoSeedHashNotARealCredential000000000000000000000003', 'diego.fernandez@example.com', '+34690900004', true, true, 'APPROVED', now(), now()),
    ('Hana', 'Kobayashi', '$2a$10$demoSeedHashNotARealCredential000000000000000000000004', 'hana.kobayashi@example.com', '+818090900005', true, true, 'APPROVED', now(), now());

-- Completed stays backing the reviews below (reviews require a COMPLETED booking).
INSERT INTO bookings (check_in, check_out, guest_count, booking_status, total_price, created_at, user_id, room_id)
VALUES
    ('2026-05-12 14:00:00', '2026-05-15 11:00:00', 2, 'COMPLETED', 567.00, '2026-05-15 11:00:00', (SELECT id FROM users WHERE email = 'amara.okafor@example.com'), 1),
    ('2026-04-02 14:00:00', '2026-04-05 11:00:00', 2, 'COMPLETED', 867.00, '2026-04-05 11:00:00', (SELECT id FROM users WHERE email = 'lukas.weber@example.com'), 2),
    ('2026-03-18 14:00:00', '2026-03-20 11:00:00', 4, 'COMPLETED', 840.00, '2026-03-20 11:00:00', (SELECT id FROM users WHERE email = 'priya.nair@example.com'), 3),
    ('2026-06-01 14:00:00', '2026-06-03 11:00:00', 2, 'COMPLETED', 378.00, '2026-06-03 11:00:00', (SELECT id FROM users WHERE email = 'diego.fernandez@example.com'), 1),
    ('2026-02-20 14:00:00', '2026-02-24 11:00:00', 2, 'COMPLETED', 1360.00, '2026-02-24 11:00:00', (SELECT id FROM users WHERE email = 'hana.kobayashi@example.com'), 7);

INSERT INTO reviews (rating, comment, created_at, is_approved, booking_id, user_id, room_id)
VALUES
    (5, 'The room over the courtyard was quiet and spotless, and the front desk held our luggage after checkout without any fuss.',
     '2026-05-16 09:00:00', true,
     (SELECT id FROM bookings WHERE user_id = (SELECT id FROM users WHERE email = 'amara.okafor@example.com') AND room_id = 1),
     (SELECT id FROM users WHERE email = 'amara.okafor@example.com'), 1),

    (4, 'Junior suite was bigger than expected and the partial river view was a nice surprise. Breakfast options were a bit repetitive by day three.',
     '2026-04-06 10:30:00', true,
     (SELECT id FROM bookings WHERE user_id = (SELECT id FROM users WHERE email = 'lukas.weber@example.com') AND room_id = 2),
     (SELECT id FROM users WHERE email = 'lukas.weber@example.com'), 2),

    (5, 'Booked the suite for a family trip — the private balcony made mornings with coffee genuinely memorable. Would stay again.',
     '2026-03-21 08:15:00', true,
     (SELECT id FROM bookings WHERE user_id = (SELECT id FROM users WHERE email = 'priya.nair@example.com') AND room_id = 3),
     (SELECT id FROM users WHERE email = 'priya.nair@example.com'), 3),

    (3, 'Room was clean but noise from the street was noticeable at night with the window cracked. Staff were helpful when we asked to switch floors.',
     '2026-06-04 12:00:00', true,
     (SELECT id FROM bookings WHERE user_id = (SELECT id FROM users WHERE email = 'diego.fernandez@example.com') AND room_id = 1),
     (SELECT id FROM users WHERE email = 'diego.fernandez@example.com'), 1),

    (5, 'The deluxe room at the Bloomsbury exceeded expectations — the garden square view and the quiet street made it feel like a real escape in central London.',
     '2026-02-25 09:45:00', true,
     (SELECT id FROM bookings WHERE user_id = (SELECT id FROM users WHERE email = 'hana.kobayashi@example.com') AND room_id = 7),
     (SELECT id FROM users WHERE email = 'hana.kobayashi@example.com'), 7);
