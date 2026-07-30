CREATE TABLE hotel_amenities (
    hotel_id BIGINT NOT NULL REFERENCES hotels(id),
    amenity  VARCHAR(255)
        CHECK (amenity IN ('WIFI','BREAKFAST','AIR_CONDITIONING','PARKING','POOL','GYM','SPA','BAR',
            'RESTAURANT','ROOM_SERVICE','AIRPORT_SHUTTLE','PET_FRIENDLY','ELEVATOR','LAUNDRY','WORKSPACE',
            'TV','COFFEE_MAKER','HAIR_DRYER','LUGGAGE_STORAGE','ACCESSIBLE','EV_CHARGING','NON_SMOKING'))
);

-- 1: Ribeira Riverhouse (Paris) — five-star riverside
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (1, 'WIFI'), (1, 'BREAKFAST'), (1, 'BAR'), (1, 'ROOM_SERVICE'), (1, 'ELEVATOR'),
    (1, 'WORKSPACE'), (1, 'AIR_CONDITIONING'), (1, 'LUGGAGE_STORAGE'), (1, 'NON_SMOKING');

-- 2: Piazza Grand Hotel (Rome) — five-star
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (2, 'WIFI'), (2, 'BREAKFAST'), (2, 'BAR'), (2, 'RESTAURANT'), (2, 'ROOM_SERVICE'),
    (2, 'ELEVATOR'), (2, 'AIR_CONDITIONING'), (2, 'LUGGAGE_STORAGE');

-- 3: Sakura Tower Hotel (Tokyo) — five-star high-rise
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (3, 'WIFI'), (3, 'GYM'), (3, 'RESTAURANT'), (3, 'BAR'), (3, 'ELEVATOR'),
    (3, 'AIR_CONDITIONING'), (3, 'WORKSPACE'), (3, 'NON_SMOKING'), (3, 'ACCESSIBLE');

-- 4: Manhattan Skyline Suites (New York) — four-star, business-leaning
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (4, 'WIFI'), (4, 'AIR_CONDITIONING'), (4, 'WORKSPACE'), (4, 'TV'),
    (4, 'COFFEE_MAKER'), (4, 'ELEVATOR'), (4, 'GYM');

-- 5: Le Marais Townhouse (Paris) — restored 18th-century townhouse, no gym/pool
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (5, 'WIFI'), (5, 'BREAKFAST'), (5, 'AIR_CONDITIONING'), (5, 'NON_SMOKING'), (5, 'LUGGAGE_STORAGE');

-- 6: Trastevere Garden Hotel (Rome) — quiet courtyard, pet-friendly
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (6, 'WIFI'), (6, 'BREAKFAST'), (6, 'PET_FRIENDLY'), (6, 'AIR_CONDITIONING'), (6, 'NON_SMOKING');

-- 7: Ginza Crown Hotel (Tokyo) — five-star flagship, full-service
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (7, 'WIFI'), (7, 'RESTAURANT'), (7, 'BAR'), (7, 'SPA'), (7, 'GYM'), (7, 'ROOM_SERVICE'),
    (7, 'ELEVATOR'), (7, 'AIR_CONDITIONING'), (7, 'WORKSPACE'), (7, 'ACCESSIBLE');

-- 8: Brooklyn Heights Inn (New York) — three-star boutique, no-frills
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (8, 'WIFI'), (8, 'TV'), (8, 'COFFEE_MAKER'), (8, 'NON_SMOKING');

-- 9: The Bloomsbury Residence (London) — five-star Georgian townhouse
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (9, 'WIFI'), (9, 'BREAKFAST'), (9, 'BAR'), (9, 'WORKSPACE'), (9, 'AIR_CONDITIONING'),
    (9, 'ELEVATOR'), (9, 'PET_FRIENDLY'), (9, 'LUGGAGE_STORAGE');

-- 10: Gòtic Boutique Hotel (Barcelona) — four-star, stone-walled old-town
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (10, 'WIFI'), (10, 'BREAKFAST'), (10, 'AIR_CONDITIONING'), (10, 'NON_SMOKING'), (10, 'TV');

-- 11: Gion Machiya House (Kyoto) — restored machiya townhouse
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (11, 'WIFI'), (11, 'BREAKFAST'), (11, 'PET_FRIENDLY'), (11, 'NON_SMOKING'), (11, 'LUGGAGE_STORAGE');

-- 12: Marina Bay Residences (Dubai) — five-star waterfront resort tower
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    (12, 'WIFI'), (12, 'POOL'), (12, 'GYM'), (12, 'SPA'), (12, 'PARKING'), (12, 'EV_CHARGING'),
    (12, 'RESTAURANT'), (12, 'BAR'), (12, 'ROOM_SERVICE'), (12, 'AIR_CONDITIONING'),
    (12, 'ELEVATOR'), (12, 'WORKSPACE'), (12, 'ACCESSIBLE');
