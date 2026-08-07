-- Swap seeded hotels off hotlinked Unsplash URLs (V7 already had to patch two dead links)
-- onto the frontend's own bundled illustration set, served locally from
-- frontend/public/images/hotels/. Removes the external-dependency failure mode entirely.
UPDATE hotels SET image_url = '/images/hotels/hotel-01.jpg', updated_at = now() WHERE name = 'Ribeira Riverhouse';
UPDATE hotels SET image_url = '/images/hotels/hotel-02.jpg', updated_at = now() WHERE name = 'Piazza Grand Hotel';
UPDATE hotels SET image_url = '/images/hotels/hotel-03.jpg', updated_at = now() WHERE name = 'Sakura Tower Hotel';
UPDATE hotels SET image_url = '/images/hotels/hotel-04.jpg', updated_at = now() WHERE name = 'Manhattan Skyline Suites';
UPDATE hotels SET image_url = '/images/hotels/hotel-05.jpg', updated_at = now() WHERE name = 'Le Marais Townhouse';
UPDATE hotels SET image_url = '/images/hotels/hotel-06.jpg', updated_at = now() WHERE name = 'Trastevere Garden Hotel';
UPDATE hotels SET image_url = '/images/hotels/hotel-07.jpg', updated_at = now() WHERE name = 'Ginza Crown Hotel';
UPDATE hotels SET image_url = '/images/hotels/hotel-08.jpg', updated_at = now() WHERE name = 'Brooklyn Heights Inn';
UPDATE hotels SET image_url = '/images/hotels/hotel-09.jpg', updated_at = now() WHERE name = 'The Bloomsbury Residence';
UPDATE hotels SET image_url = '/images/hotels/hotel-10.jpg', updated_at = now() WHERE name = 'Gòtic Boutique Hotel';
UPDATE hotels SET image_url = '/images/hotels/hotel-11.jpg', updated_at = now() WHERE name = 'Gion Machiya House';
UPDATE hotels SET image_url = '/images/hotels/hotel-12.jpg', updated_at = now() WHERE name = 'Marina Bay Residences';
UPDATE hotels SET image_url = '/images/hotels/hotel-01.jpg', updated_at = now() WHERE name = 'Oia Caldera Suites';
UPDATE hotels SET image_url = '/images/hotels/hotel-02.jpg', updated_at = now() WHERE name = 'Atoll Lagoon Resort';
