INSERT INTO rooms (number, type, price_per_night, capacity, floor, status, description, created_at, hotel_id)
VALUES
    -- Ribeira Riverhouse (Paris, hotel 1)
    ('101', 'DOUBLE', 189.00, 2, 1, 'AVAILABLE', 'A quiet double room overlooking the courtyard.', now(), 1),
    ('204', 'JUNIOR_SUITE', 289.00, 3, 2, 'AVAILABLE', 'A junior suite with a partial view of the Seine.', now(), 1),
    ('305', 'SUITE', 420.00, 4, 3, 'AVAILABLE', 'The riverhouse''s signature suite, with a private balcony.', now(), 1),
    ('102', 'SINGLE', 129.00, 1, 1, 'MAINTENANCE', 'Compact single room, currently being refreshed.', now(), 1),

    -- Le Marais Townhouse (Paris, hotel 5)
    ('12', 'DOUBLE', 165.00, 2, 1, 'AVAILABLE', 'A beamed-ceiling double in the original 18th-century wing.', now(), 5),
    ('27', 'TWIN', 172.00, 2, 2, 'AVAILABLE', 'Twin beds beneath the townhouse''s original skylight.', now(), 5),

    -- The Bloomsbury Residence (London, hotel 9)
    ('301', 'DELUXE', 340.00, 2, 3, 'AVAILABLE', 'A deluxe room facing the garden square.', now(), 9),
    ('402', 'PENTHOUSE', 890.00, 4, 4, 'AVAILABLE', 'The Bloomsbury''s rooftop penthouse with a private terrace.', now(), 9),
    ('115', 'SINGLE', 210.00, 1, 1, 'RESERVED', 'A compact single, currently held for an existing guest.', now(), 9),

    -- Marina Bay Residences (Dubai, hotel 12)
    ('1801', 'SUITE', 560.00, 3, 18, 'AVAILABLE', 'A marina-view suite with a private balcony.', now(), 12),
    ('2210', 'VILLA', 1250.00, 6, 22, 'AVAILABLE', 'A two-level villa residence with a private plunge pool.', now(), 12);
