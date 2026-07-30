INSERT INTO companies (name, legal_name, email, phone, address, city, country, website, logo_url, status, created_at, updated_at)
VALUES ('Wayside Hospitality Group', 'Wayside Hospitality Group Ltd.', 'contact@waysidehospitality.example',
        '+351210000000', 'Rua das Flores 12', 'Lisbon', 'Portugal', 'https://waysidehospitality.example',
        NULL, 'ACTIVE', now(), now());

INSERT INTO hotels (name, city, country, address, star_rating, status, phone, email, description, image_url, created_at, updated_at, company_id)
VALUES
    ('Ribeira Riverhouse', 'Paris', 'France', '18 Rue de Rivoli', 5, 'ACTIVE',
     '+33145000000', 'reservations@ribeira-paris.example',
     'A five-star riverside stay in the heart of Paris, steps from the Seine.',
     'https://images.unsplash.com/photo-1502602898536-47ad22581b52?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Piazza Grand Hotel', 'Rome', 'Italy', '4 Via del Corso', 5, 'ACTIVE',
     '+390612345678', 'reservations@piazzagrand-rome.example',
     'Classic Roman elegance a short walk from the Trevi Fountain.',
     'https://images.unsplash.com/photo-1523906834658-6e24ef2386f9?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Sakura Tower Hotel', 'Tokyo', 'Japan', '2-1 Marunouchi', 5, 'ACTIVE',
     '+81312345678', 'reservations@sakuratower-tokyo.example',
     'A modern high-rise hotel overlooking the Tokyo skyline.',
     'https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Manhattan Skyline Suites', 'New York', 'United States', '350 5th Ave', 4, 'ACTIVE',
     '+12125550100', 'reservations@manhattanskyline-nyc.example',
     'Contemporary suites in Midtown, minutes from Central Park.',
     'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=800&q=80&auto=format&fit=crop',
     now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group'));
