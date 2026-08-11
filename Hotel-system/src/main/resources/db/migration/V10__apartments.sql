-- Introduces a real property-type distinction (HOTEL vs APARTMENT). Every row seeded before
-- this migration is a hotel, hence the default; new APARTMENT rows are inserted explicitly
-- below, three per city, so the Apartments catalog (frontend GET /api/hotels/type/APARTMENT)
-- has real, distinct listings rather than reusing the hotel catalog under a different label.

ALTER TABLE hotels
    ADD COLUMN property_type VARCHAR(255) NOT NULL DEFAULT 'HOTEL';

ALTER TABLE hotels
    ADD CONSTRAINT hotels_property_type_check
        CHECK (property_type IN ('HOTEL', 'APARTMENT'));

-- Images reuse the same locally-hosted, already-vetted photo set the hotel catalog uses
-- (frontend/public/images/hotels/hotel-01.jpg .. hotel-12.jpg) rather than sourcing new
-- ones — decorative representative imagery, cycled across listings, same convention V9
-- established for hotels.

INSERT INTO hotels (name, city, country, address, star_rating, status, phone, email, description, image_url, property_type, created_at, updated_at, company_id)
VALUES
    -- Barcelona, Spain
    ('Gràcia Rooftop Apartments', 'Barcelona', 'Spain', 'Carrer de Verdi 22', 4, 'ACTIVE',
     '+34936000010', 'stay@graciarooftop-barcelona.example',
     'A one-bedroom apartment with a private rooftop terrace above Gràcia''s market squares.',
     '/images/hotels/hotel-01.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('El Born Loft Residences', 'Barcelona', 'Spain', 'Carrer Montcada 8', 3, 'ACTIVE',
     '+34936000020', 'stay@elbornloft-barcelona.example',
     'An exposed-beam loft in a converted textile warehouse, two minutes from the Picasso Museum.',
     '/images/hotels/hotel-02.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Barceloneta Beach Flats', 'Barcelona', 'Spain', 'Passeig de Joan de Borbó 45', 5, 'ACTIVE',
     '+34936000030', 'stay@barcelonetabeach-barcelona.example',
     'A top-floor two-bedroom flat with a sea-facing balcony, steps from the beach promenade.',
     '/images/hotels/hotel-03.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts')),

    -- Dubai, United Arab Emirates
    ('Marina Promenade Apartments', 'Dubai', 'United Arab Emirates', 'Marina Walk, Tower 3', 5, 'ACTIVE',
     '+97142000010', 'stay@marinapromenade-dubai.example',
     'A high-floor one-bedroom with marina and skyline views, a five-minute walk to the tram.',
     '/images/hotels/hotel-04.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Al Fahidi Heritage Flats', 'Dubai', 'United Arab Emirates', 'Al Fahidi Street 9', 4, 'ACTIVE',
     '+97142000020', 'stay@alfahidiheritage-dubai.example',
     'A courtyard apartment in Dubai''s old wind-tower quarter, across from the textile souk.',
     '/images/hotels/hotel-05.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Jumeirah Garden Residences', 'Dubai', 'United Arab Emirates', 'Jumeirah Beach Road 210', 3, 'ACTIVE',
     '+97142000030', 'stay@jumeirahgarden-dubai.example',
     'A ground-floor two-bedroom with a private garden patio, a short drive from the beach.',
     '/images/hotels/hotel-06.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    -- Kyoto, Japan
    ('Nishiki Market Apartments', 'Kyoto', 'Japan', 'Nishikikoji-dori 14', 3, 'ACTIVE',
     '+81755620010', 'stay@nishikimarket-kyoto.example',
     'A compact studio above Kyoto''s covered market street, minutes from Nijo Castle by bike.',
     '/images/hotels/hotel-07.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts')),

    ('Arashiyama Bamboo Residences', 'Kyoto', 'Japan', 'Saga-Tenryuji Susukinobabacho 3', 5, 'ACTIVE',
     '+81755620020', 'stay@arashiyamabamboo-kyoto.example',
     'A two-room house on the edge of the bamboo grove, with a private engawa overlooking the garden.',
     '/images/hotels/hotel-08.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Pontocho Riverside Flats', 'Kyoto', 'Japan', 'Pontocho Alley 27', 4, 'ACTIVE',
     '+81755620030', 'stay@pontochoriverside-kyoto.example',
     'A narrow machiya-style flat overlooking the Kamo River, one alley back from the lantern-lit lane.',
     '/images/hotels/hotel-09.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    -- London, United Kingdom
    ('Notting Hill Garden Apartments', 'London', 'United Kingdom', 'Kensington Park Road 58', 4, 'ACTIVE',
     '+442070000100', 'stay@nottinghillgarden-london.example',
     'A first-floor one-bedroom overlooking a communal garden square, near Portobello Market.',
     '/images/hotels/hotel-10.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Shoreditch Loft Residences', 'London', 'United Kingdom', 'Redchurch Street 31', 5, 'ACTIVE',
     '+442070000200', 'stay@shoreditchloft-london.example',
     'An exposed-brick warehouse loft above a print shop, in the middle of Shoreditch''s gallery strip.',
     '/images/hotels/hotel-11.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts')),

    ('Bloomsbury Mews Flats', 'London', 'United Kingdom', 'Cosmo Place 4', 3, 'ACTIVE',
     '+442070000300', 'stay@bloomsburymews-london.example',
     'A quiet mews flat behind Queen Square, an easy walk to the British Museum.',
     '/images/hotels/hotel-12.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    -- New York, United States
    ('Greenwich Village Apartments', 'New York', 'United States', 'Bedford Street 17', 5, 'ACTIVE',
     '+12125550200', 'stay@greenwichvillage-nyc.example',
     'A pre-war one-bedroom on a tree-lined West Village block, above a corner bookshop.',
     '/images/hotels/hotel-01.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Williamsburg Loft Residences', 'New York', 'United States', 'Wythe Avenue 88', 3, 'ACTIVE',
     '+17185550200', 'stay@williamsburgloft-nyc.example',
     'A converted factory loft with East River skyline views, two blocks from the waterfront park.',
     '/images/hotels/hotel-02.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Upper West Side Flats', 'New York', 'United States', 'West 78th Street 145', 4, 'ACTIVE',
     '+12125550300', 'stay@upperwestside-nyc.example',
     'A classic six two-bedroom facing a quiet courtyard, three blocks from Central Park.',
     '/images/hotels/hotel-03.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    -- North Male Atoll, Maldives
    ('Lagoon Edge Apartments', 'North Male Atoll', 'Maldives', 'Lagoon Walk 4', 4, 'ACTIVE',
     '+9606600010', 'stay@lagoonedge-maldives.example',
     'A ground-level apartment opening directly onto the lagoon shallows, with a private jetty.',
     '/images/hotels/hotel-04.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts')),

    ('Palm Villa Residences', 'North Male Atoll', 'Maldives', 'Palm Grove Lane 11', 5, 'ACTIVE',
     '+9606600020', 'stay@palmvilla-maldives.example',
     'A two-bedroom garden villa shaded by coconut palms, a short walk from the house reef.',
     '/images/hotels/hotel-05.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Reef House Flats', 'North Male Atoll', 'Maldives', 'Reef House Row 6', 4, 'ACTIVE',
     '+9606600030', 'stay@reefhouse-maldives.example',
     'A one-bedroom flat with a private sundeck over the shallows, reachable by a short boat transfer.',
     '/images/hotels/hotel-06.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    -- Paris, France
    ('Marais Canal Apartments', 'Paris', 'France', 'Rue de la Reine Blanche 6', 3, 'ACTIVE',
     '+33145000100', 'stay@maraiscanal-paris.example',
     'A one-bedroom apartment on a quiet Marais courtyard, five minutes from the Place des Vosges.',
     '/images/hotels/hotel-07.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Montmartre Artist Loft Residences', 'Paris', 'France', 'Rue Lepic 39', 4, 'ACTIVE',
     '+33145000200', 'stay@montmartreloft-paris.example',
     'A top-floor loft with a skylight studio room, on the steep lane above the Moulin de la Galette.',
     '/images/hotels/hotel-08.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    ('Saint-Germain Garden Flats', 'Paris', 'France', 'Rue de Furstemberg 5', 5, 'ACTIVE',
     '+33145000300', 'stay@saintgermaingarden-paris.example',
     'A two-bedroom flat overlooking a private courtyard garden, around the corner from Café de Flore.',
     '/images/hotels/hotel-09.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    -- Rome, Italy
    ('Trastevere Courtyard Apartments', 'Rome', 'Italy', 'Via della Scala 12', 4, 'ACTIVE',
     '+390612345700', 'stay@trastevereyard-rome.example',
     'A ground-floor apartment around a shared ivy courtyard, on one of Trastevere''s cobbled lanes.',
     '/images/hotels/hotel-10.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts')),

    ('Monti Loft Residences', 'Rome', 'Italy', 'Via del Boschetto 21', 3, 'ACTIVE',
     '+390612345800', 'stay@montiloft-rome.example',
     'A vaulted-ceiling loft in Rome''s oldest rione, a ten-minute walk from the Colosseum.',
     '/images/hotels/hotel-11.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Testaccio Market Flats', 'Rome', 'Italy', 'Via Marmorata 60', 4, 'ACTIVE',
     '+390612345900', 'stay@testacciomarket-rome.example',
     'A one-bedroom flat above Testaccio''s covered food market, in Rome''s least touristed rione.',
     '/images/hotels/hotel-12.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    -- Santorini, Greece
    ('Imerovigli Cliffside Apartments', 'Santorini', 'Greece', 'Cliff Path 3, Imerovigli', 5, 'ACTIVE',
     '+302286000100', 'stay@imerovigli-santorini.example',
     'A whitewashed one-bedroom cut into the caldera rim, with an uninterrupted sunset view.',
     '/images/hotels/hotel-01.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts')),

    ('Fira Caldera Loft Residences', 'Santorini', 'Greece', 'Marmara Steps 9, Fira', 4, 'ACTIVE',
     '+302286000200', 'stay@firacaldera-santorini.example',
     'A split-level loft built into the cliff, a short walk from Fira''s cable car station.',
     '/images/hotels/hotel-02.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Pyrgos Village Flats', 'Santorini', 'Greece', 'Kasteli Lane 5, Pyrgos', 3, 'ACTIVE',
     '+302286000300', 'stay@pyrgosvillage-santorini.example',
     'A stone-built flat in Santorini''s hilltop village, away from the caldera crowds.',
     '/images/hotels/hotel-03.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Meridian Collection')),

    -- Tokyo, Japan
    ('Yanaka Machiya Apartments', 'Tokyo', 'Japan', 'Yanaka 3-chome 8', 3, 'ACTIVE',
     '+81335500100', 'stay@yanakamachiya-tokyo.example',
     'A restored wooden machiya house in one of Tokyo''s few districts to survive the war intact.',
     '/images/hotels/hotel-04.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Wayside Hospitality Group')),

    ('Nakameguro Riverside Residences', 'Tokyo', 'Japan', 'Kamimeguro 2-chome 15', 4, 'ACTIVE',
     '+81335500200', 'stay@nakameguroriverside-tokyo.example',
     'A one-bedroom apartment on the Meguro River canal, under the cherry trees near the station.',
     '/images/hotels/hotel-05.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Casa & Co Hospitality')),

    ('Shimokitazawa Loft Flats', 'Tokyo', 'Japan', 'Kitazawa 2-chome 21', 5, 'ACTIVE',
     '+81335500300', 'stay@shimokitazawaloft-tokyo.example',
     'A two-level loft above a record shop, in Tokyo''s theatre-and-vintage-clothing neighbourhood.',
     '/images/hotels/hotel-06.jpg', 'APARTMENT', now(), now(), (SELECT id FROM companies WHERE name = 'Atoll & Tide Resorts'));

-- One bookable unit per apartment — the whole apartment is the room, unlike a hotel with
-- many room categories. Pricing sits below the equivalent city's boutique-hotel suites,
-- matching how a whole apartment usually undercuts a comparable hotel room per night.

INSERT INTO rooms (number, type, price_per_night, capacity, floor, status, description, created_at, hotel_id)
VALUES
    ('Whole Apartment', 'DOUBLE', 118.00, 2, 3, 'AVAILABLE', 'The entire apartment, bedroom and rooftop terrace.', now(), (SELECT id FROM hotels WHERE name = 'Gràcia Rooftop Apartments')),
    ('Whole Apartment', 'STUDIO', 96.00, 2, 2, 'AVAILABLE', 'The entire loft, open-plan with a queen bed.', now(), (SELECT id FROM hotels WHERE name = 'El Born Loft Residences')),
    ('Whole Apartment', 'FAMILY', 165.00, 4, 5, 'AVAILABLE', 'The entire two-bedroom flat with a sea-facing balcony.', now(), (SELECT id FROM hotels WHERE name = 'Barceloneta Beach Flats')),

    ('Whole Apartment', 'DOUBLE', 172.00, 2, 21, 'AVAILABLE', 'The entire apartment, marina and skyline views.', now(), (SELECT id FROM hotels WHERE name = 'Marina Promenade Apartments')),
    ('Whole Apartment', 'STUDIO', 104.00, 2, 1, 'AVAILABLE', 'The entire courtyard apartment.', now(), (SELECT id FROM hotels WHERE name = 'Al Fahidi Heritage Flats')),
    ('Whole Apartment', 'FAMILY', 138.00, 4, 1, 'AVAILABLE', 'The entire two-bedroom, with private garden patio.', now(), (SELECT id FROM hotels WHERE name = 'Jumeirah Garden Residences')),

    ('Whole Apartment', 'STUDIO', 88.00, 2, 2, 'AVAILABLE', 'The entire studio above the market street.', now(), (SELECT id FROM hotels WHERE name = 'Nishiki Market Apartments')),
    ('Whole Apartment', 'CONNECTING', 210.00, 3, 1, 'AVAILABLE', 'The entire house, two connecting tatami rooms.', now(), (SELECT id FROM hotels WHERE name = 'Arashiyama Bamboo Residences')),
    ('Whole Apartment', 'DOUBLE', 132.00, 2, 2, 'AVAILABLE', 'The entire flat, river-facing.', now(), (SELECT id FROM hotels WHERE name = 'Pontocho Riverside Flats')),

    ('Whole Apartment', 'DOUBLE', 156.00, 2, 1, 'AVAILABLE', 'The entire apartment overlooking the garden square.', now(), (SELECT id FROM hotels WHERE name = 'Notting Hill Garden Apartments')),
    ('Whole Apartment', 'STUDIO', 148.00, 2, 4, 'AVAILABLE', 'The entire warehouse loft.', now(), (SELECT id FROM hotels WHERE name = 'Shoreditch Loft Residences')),
    ('Whole Apartment', 'DOUBLE', 121.00, 2, 1, 'AVAILABLE', 'The entire mews flat behind the square.', now(), (SELECT id FROM hotels WHERE name = 'Bloomsbury Mews Flats')),

    ('Whole Apartment', 'DOUBLE', 198.00, 2, 4, 'AVAILABLE', 'The entire pre-war one-bedroom.', now(), (SELECT id FROM hotels WHERE name = 'Greenwich Village Apartments')),
    ('Whole Apartment', 'STUDIO', 142.00, 2, 5, 'AVAILABLE', 'The entire factory loft, river-view.', now(), (SELECT id FROM hotels WHERE name = 'Williamsburg Loft Residences')),
    ('Whole Apartment', 'FAMILY', 235.00, 4, 4, 'AVAILABLE', 'The entire two-bedroom classic six.', now(), (SELECT id FROM hotels WHERE name = 'Upper West Side Flats')),

    ('Whole Apartment', 'BUNGALOW', 310.00, 3, 1, 'AVAILABLE', 'The entire lagoon-edge apartment with private jetty.', now(), (SELECT id FROM hotels WHERE name = 'Lagoon Edge Apartments')),
    ('Whole Apartment', 'VILLA', 420.00, 4, 1, 'AVAILABLE', 'The entire garden villa, two bedrooms.', now(), (SELECT id FROM hotels WHERE name = 'Palm Villa Residences')),
    ('Whole Apartment', 'BUNGALOW', 285.00, 2, 1, 'AVAILABLE', 'The entire flat with private sundeck over the shallows.', now(), (SELECT id FROM hotels WHERE name = 'Reef House Flats')),

    ('Whole Apartment', 'DOUBLE', 112.00, 2, 2, 'AVAILABLE', 'The entire apartment on the courtyard.', now(), (SELECT id FROM hotels WHERE name = 'Marais Canal Apartments')),
    ('Whole Apartment', 'STUDIO', 145.00, 2, 6, 'AVAILABLE', 'The entire loft, skylight studio room.', now(), (SELECT id FROM hotels WHERE name = 'Montmartre Artist Loft Residences')),
    ('Whole Apartment', 'FAMILY', 260.00, 4, 2, 'AVAILABLE', 'The entire two-bedroom, courtyard garden view.', now(), (SELECT id FROM hotels WHERE name = 'Saint-Germain Garden Flats')),

    ('Whole Apartment', 'DOUBLE', 108.00, 2, 1, 'AVAILABLE', 'The entire apartment around the shared courtyard.', now(), (SELECT id FROM hotels WHERE name = 'Trastevere Courtyard Apartments')),
    ('Whole Apartment', 'STUDIO', 94.00, 2, 3, 'AVAILABLE', 'The entire vaulted-ceiling loft.', now(), (SELECT id FROM hotels WHERE name = 'Monti Loft Residences')),
    ('Whole Apartment', 'DOUBLE', 116.00, 2, 4, 'AVAILABLE', 'The entire flat above the covered market.', now(), (SELECT id FROM hotels WHERE name = 'Testaccio Market Flats')),

    ('Whole Apartment', 'DOUBLE', 245.00, 2, 2, 'AVAILABLE', 'The entire cliffside apartment, caldera sunset view.', now(), (SELECT id FROM hotels WHERE name = 'Imerovigli Cliffside Apartments')),
    ('Whole Apartment', 'STUDIO', 168.00, 2, 1, 'AVAILABLE', 'The entire split-level loft in the cliff.', now(), (SELECT id FROM hotels WHERE name = 'Fira Caldera Loft Residences')),
    ('Whole Apartment', 'DOUBLE', 105.00, 2, 1, 'AVAILABLE', 'The entire stone-built flat in the hilltop village.', now(), (SELECT id FROM hotels WHERE name = 'Pyrgos Village Flats')),

    ('Whole Apartment', 'STUDIO', 102.00, 2, 1, 'AVAILABLE', 'The entire restored machiya house.', now(), (SELECT id FROM hotels WHERE name = 'Yanaka Machiya Apartments')),
    ('Whole Apartment', 'DOUBLE', 138.00, 2, 3, 'AVAILABLE', 'The entire apartment on the canal.', now(), (SELECT id FROM hotels WHERE name = 'Nakameguro Riverside Residences')),
    ('Whole Apartment', 'CONNECTING', 175.00, 3, 2, 'AVAILABLE', 'The entire two-level loft above the record shop.', now(), (SELECT id FROM hotels WHERE name = 'Shimokitazawa Loft Flats'));

-- Amenities skew toward what a whole-apartment stay actually offers (a kitchen, laundry, a
-- desk) rather than hotel-style services the listing does not have (no room service, no
-- front desk, no restaurant on site).

INSERT INTO hotel_amenities (hotel_id, amenity)
SELECT h.id, a.amenity
FROM hotels h
CROSS JOIN (VALUES
    ('WIFI'), ('AIR_CONDITIONING'), ('WORKSPACE'), ('LAUNDRY'), ('NON_SMOKING')
) AS a(amenity)
WHERE h.property_type = 'APARTMENT';

-- A few apartments get one extra amenity each, for variety rather than every listing
-- reading identically.
INSERT INTO hotel_amenities (hotel_id, amenity) VALUES
    ((SELECT id FROM hotels WHERE name = 'Barceloneta Beach Flats'), 'ELEVATOR'),
    ((SELECT id FROM hotels WHERE name = 'Marina Promenade Apartments'), 'ELEVATOR'),
    ((SELECT id FROM hotels WHERE name = 'Jumeirah Garden Residences'), 'PET_FRIENDLY'),
    ((SELECT id FROM hotels WHERE name = 'Upper West Side Flats'), 'ELEVATOR'),
    ((SELECT id FROM hotels WHERE name = 'Palm Villa Residences'), 'PET_FRIENDLY'),
    ((SELECT id FROM hotels WHERE name = 'Saint-Germain Garden Flats'), 'ELEVATOR'),
    ((SELECT id FROM hotels WHERE name = 'Shoreditch Loft Residences'), 'ELEVATOR'),
    ((SELECT id FROM hotels WHERE name = 'Greenwich Village Apartments'), 'LUGGAGE_STORAGE');
