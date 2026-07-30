-- The original Unsplash source photos for these two hotels were taken down upstream
-- (source URLs now 404), leaving blank cards in the folio grid. Point them at working photos.
UPDATE hotels
SET image_url = 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800&q=80&auto=format&fit=crop',
    updated_at = now()
WHERE name = 'Ribeira Riverhouse';

UPDATE hotels
SET image_url = 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800&q=80&auto=format&fit=crop',
    updated_at = now()
WHERE name = 'Ginza Crown Hotel';

-- New independent operator for the catalog's first Indian Ocean property
INSERT INTO companies (name, legal_name, email, phone, address, city, country, website, logo_url, status, created_at, updated_at)
VALUES ('Atoll & Tide Resorts', 'Atoll & Tide Resorts Pte. Ltd.', 'contact@atollandtide.example',
        '+6562000000', '1 Marina Boulevard', 'Singapore', 'Singapore', 'https://atollandtide.example',
        NULL, 'ACTIVE', now(), now());

-- Two more five-star hotels, so the five-star folio grid fills out evenly (was 7, an orphan card on the last row)
INSERT INTO hotels (name, city, country, address, star_rating, status, phone, email, description, image_url, created_at, updated_at, company_id)
VALUES
    ('Oia Caldera Suites', 'Santorini', 'Greece', '5 Marmara Steps, Oia', 5, 'ACTIVE',
     '+302286000000', 'reservations@oiacaldera-santorini.example',
     'Whitewashed suites cut into the caldera cliff, each with a private plunge pool over the Aegean.',
     'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Atoll Lagoon Resort', 'North Male Atoll', 'Maldives', 'Private Lagoon Jetty 12', 5, 'ACTIVE',
     '+9606600000', 'reservations@atolllagoon-maldives.example',
     'Overwater villas set in a private lagoon, reachable only by seaplane.',
     'https://images.unsplash.com/photo-1553603227-2358aabe821e?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts'));
