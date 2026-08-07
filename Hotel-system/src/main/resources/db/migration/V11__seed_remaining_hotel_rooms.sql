-- Ten HOTEL-type properties (2,3,4,6,7,8,10,11,13,14) were left with zero rooms after
-- V4__seed_rooms.sql only covered 1, 5, 9 and 12 — "Available rooms" was empty for the
-- rest. Most get the same Double/Junior suite/Suite/Single lineup as Ribeira Riverhouse
-- (hotel 1), priced by star rating and city; three get a different mix that matches their
-- branding instead of a generic copy-paste.
INSERT INTO rooms (number, type, price_per_night, capacity, floor, status, description, created_at, hotel_id)
VALUES
    -- Piazza Grand Hotel (Rome, hotel 2, 5*)
    ('101', 'SINGLE', 145.00, 1, 1, 'AVAILABLE', 'A compact single room off the piazza-facing corridor.', now(), 2),
    ('110', 'DOUBLE', 205.00, 2, 1, 'AVAILABLE', 'A double room with tall shuttered windows.', now(), 2),
    ('204', 'JUNIOR_SUITE', 310.00, 3, 2, 'AVAILABLE', 'A junior suite overlooking the piazza fountain.', now(), 2),
    ('305', 'SUITE', 460.00, 4, 3, 'AVAILABLE', 'The hotel''s signature suite, with a frescoed ceiling.', now(), 2),

    -- Sakura Tower Hotel (Tokyo, hotel 3, 5*)
    ('101', 'SINGLE', 155.00, 1, 1, 'AVAILABLE', 'A single room with a city-facing window.', now(), 3),
    ('112', 'DOUBLE', 220.00, 2, 1, 'AVAILABLE', 'A double room on the tower''s lower floors.', now(), 3),
    ('218', 'JUNIOR_SUITE', 330.00, 3, 2, 'AVAILABLE', 'A junior suite with a skyline view.', now(), 3),
    ('340', 'SUITE', 490.00, 4, 3, 'MAINTENANCE', 'A high-floor suite, currently being refreshed.', now(), 3),

    -- Manhattan Skyline Suites (New York, hotel 4, 4*)
    ('101', 'SINGLE', 165.00, 1, 1, 'AVAILABLE', 'A single room facing the inner courtyard.', now(), 4),
    ('115', 'DOUBLE', 230.00, 2, 1, 'AVAILABLE', 'A double room with skyline glimpses.', now(), 4),
    ('221', 'JUNIOR_SUITE', 340.00, 3, 2, 'AVAILABLE', 'A junior suite with floor-to-ceiling windows.', now(), 4),
    ('330', 'SUITE', 495.00, 4, 3, 'AVAILABLE', 'A corner suite with panoramic skyline views.', now(), 4),

    -- Trastevere Garden Hotel (Rome, hotel 6, 4*)
    ('101', 'SINGLE', 120.00, 1, 1, 'AVAILABLE', 'A single room facing the garden path.', now(), 6),
    ('108', 'DOUBLE', 175.00, 2, 1, 'AVAILABLE', 'A double room opening onto the garden terrace.', now(), 6),
    ('212', 'JUNIOR_SUITE', 260.00, 3, 2, 'AVAILABLE', 'A junior suite above the garden courtyard.', now(), 6),
    ('301', 'SUITE', 380.00, 4, 3, 'RESERVED', 'The garden hotel''s top-floor suite, currently held for an existing guest.', now(), 6),

    -- Ginza Crown Hotel (Tokyo, hotel 7, 5*)
    ('101', 'SINGLE', 175.00, 1, 1, 'AVAILABLE', 'A single room just off the Ginza-facing lobby.', now(), 7),
    ('117', 'DOUBLE', 245.00, 2, 1, 'AVAILABLE', 'A double room with a Ginza street view.', now(), 7),
    ('229', 'JUNIOR_SUITE', 365.00, 3, 2, 'AVAILABLE', 'A junior suite with a private tea corner.', now(), 7),
    ('350', 'SUITE', 540.00, 4, 3, 'AVAILABLE', 'The Crown''s flagship suite, with a soaking tub.', now(), 7),

    -- Gòtic Boutique Hotel (Barcelona, hotel 10, 4*)
    ('101', 'SINGLE', 118.00, 1, 1, 'AVAILABLE', 'A snug single room in the old Gothic quarter.', now(), 10),
    ('109', 'DOUBLE', 170.00, 2, 1, 'AVAILABLE', 'A double room with exposed stone walls.', now(), 10),
    ('206', 'JUNIOR_SUITE', 255.00, 3, 2, 'AVAILABLE', 'A junior suite with a small reading nook.', now(), 10),
    ('303', 'SUITE', 375.00, 4, 3, 'AVAILABLE', 'The boutique hotel''s suite, with a private terrace.', now(), 10),

    -- Gion Machiya House (Kyoto, hotel 11, 5*)
    ('101', 'SINGLE', 175.00, 1, 1, 'AVAILABLE', 'A tatami-floored single room off the machiya''s inner corridor.', now(), 11),
    ('105', 'DOUBLE', 240.00, 2, 1, 'AVAILABLE', 'A double room overlooking the machiya''s tsuboniwa garden.', now(), 11),
    ('201', 'JUNIOR_SUITE', 350.00, 3, 2, 'AVAILABLE', 'A junior suite with a private engawa veranda.', now(), 11),
    ('301', 'SUITE', 520.00, 4, 3, 'AVAILABLE', 'The house''s principal suite, once reserved for guests of honour.', now(), 11),

    -- Brooklyn Heights Inn (New York, hotel 8, 3*) — budget mix, no suites
    ('101', 'SINGLE', 99.00, 1, 1, 'AVAILABLE', 'A no-frills single room, ideal for solo travellers.', now(), 8),
    ('104', 'DOUBLE', 139.00, 2, 1, 'AVAILABLE', 'A double room with a view of the brownstones.', now(), 8),
    ('207', 'TWIN', 145.00, 2, 2, 'AVAILABLE', 'A twin room with two full-size beds.', now(), 8),
    ('210', 'TRIPLE', 179.00, 3, 2, 'AVAILABLE', 'A triple room, popular with small groups.', now(), 8),

    -- Oia Caldera Suites (Santorini, hotel 13, 5*) — all-suite luxury, no basic singles
    ('201', 'JUNIOR_SUITE', 380.00, 2, 2, 'AVAILABLE', 'A junior suite carved into the caldera cliffside.', now(), 13),
    ('305', 'SUITE', 560.00, 3, 3, 'AVAILABLE', 'A suite with a private plunge pool facing the caldera.', now(), 13),
    ('320', 'DELUXE', 480.00, 2, 3, 'AVAILABLE', 'A deluxe cave room with a sunset-facing terrace.', now(), 13),
    ('401', 'PENTHOUSE', 950.00, 4, 4, 'AVAILABLE', 'The property''s cliff-top penthouse, with a 270-degree caldera view.', now(), 13),

    -- Atoll Lagoon Resort (North Male Atoll, hotel 14, 5*) — overwater resort mix
    ('112', 'SUITE', 520.00, 3, 1, 'AVAILABLE', 'A beach suite steps from the lagoon.', now(), 14),
    ('118', 'FAMILY', 640.00, 5, 1, 'AVAILABLE', 'A family room with direct beach access.', now(), 14),
    ('B04', 'BUNGALOW', 780.00, 2, 1, 'AVAILABLE', 'An overwater bungalow with a glass floor panel and private deck.', now(), 14),
    ('V02', 'VILLA', 1400.00, 6, 1, 'OUT_OF_ORDER', 'A private lagoon-front villa, currently closed for storm-damage repairs.', now(), 14);
