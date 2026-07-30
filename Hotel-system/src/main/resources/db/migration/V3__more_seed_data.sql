INSERT INTO companies (name, legal_name, email, phone, address, city, country, website, logo_url, status, created_at, updated_at)
VALUES
    ('Meridian Collection', 'Meridian Collection Hospitality S.L.', 'contact@meridiancollection.example',
     '+34910000000', 'Paseo de Gracia 45', 'Barcelona', 'Spain', 'https://meridiancollection.example',
     NULL, 'ACTIVE', now(), now()),
    ('Casa & Co Hospitality', 'Casa & Co Hospitality K.K.', 'contact@casaandco.example',
     '+81352000000', '1-2 Gion', 'Kyoto', 'Japan', 'https://casaandco.example',
     NULL, 'ACTIVE', now(), now());

INSERT INTO hotels (name, city, country, address, star_rating, status, phone, email, description, image_url, created_at, updated_at, company_id)
VALUES
    -- second entries in already-seeded cities, so the search grid shows more than one result
    ('Le Marais Townhouse', 'Paris', 'France', '9 Rue des Archives', 4, 'ACTIVE',
     '+33144000000', 'reservations@lemarais-paris.example',
     'A restored 18th-century townhouse in the Marais, a few streets from the Picasso Museum.',
     'https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Trastevere Garden Hotel', 'Rome', 'Italy', '22 Via della Scala', 4, 'ACTIVE',
     '+390658000000', 'reservations@trastevere-rome.example',
     'A quiet courtyard hotel in Trastevere, five minutes from the river.',
     'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Ginza Crown Hotel', 'Tokyo', 'Japan', '5-8 Ginza', 5, 'ACTIVE',
     '+81335500000', 'reservations@ginzacrown-tokyo.example',
     'A flagship five-star address in Ginza, steps from the flagship boutiques.',
     'https://images.unsplash.com/photo-1590073242678-70ee3fc28f8e?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Brooklyn Heights Inn', 'New York', 'United States', '84 Montague St', 3, 'ACTIVE',
     '+17185550100', 'reservations@brooklynheights-nyc.example',
     'A boutique inn on a quiet brownstone street with a view of the Manhattan skyline.',
     'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    -- new cities, to widen the catalog and the quick-search variety
    ('The Bloomsbury Residence', 'London', 'United Kingdom', '14 Bedford Square', 5, 'ACTIVE',
     '+442070000000', 'reservations@bloomsbury-london.example',
     'A Georgian townhouse hotel on a garden square in literary Bloomsbury.',
     'https://images.unsplash.com/photo-1512918728675-ed5a9ecdebfd?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Gòtic Boutique Hotel', 'Barcelona', 'Spain', '3 Carrer del Bisbe', 4, 'ACTIVE',
     '+34933000000', 'reservations@gotic-barcelona.example',
     'A stone-walled boutique hotel inside the Gothic Quarter''s old city walls.',
     'https://images.unsplash.com/photo-1523906834658-6e24ef2386f9?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Gion Machiya House', 'Kyoto', 'Japan', '4 Hanamikoji-dori', 5, 'ACTIVE',
     '+81755610000', 'reservations@gion-kyoto.example',
     'A restored machiya townhouse in the Gion geisha district, with a private tea room.',
     'https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Marina Bay Residences', 'Dubai', 'United Arab Emirates', 'Marina Walk 12', 5, 'ACTIVE',
     '+97142000000', 'reservations@marinabay-dubai.example',
     'A waterfront tower with private balconies overlooking the marina skyline.',
     'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group'));
