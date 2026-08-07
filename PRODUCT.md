# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Travelers searching for and booking a hotel stay. They arrive wanting to find a room in a specific city, compare a handful of properties (star rating, price, location), and complete a booking with check-in/check-out dates and guest count. Some are unauthenticated first-time visitors; returning users log in to book and manage their reservations.

## Product Purpose

A hotel booking marketplace: search hotels by city/country, view a hotel's detail page with its available rooms, and book a room for a date range. Authenticated users can view and cancel their own bookings. Success is a completed booking with minimal friction from search to confirmation.

## Positioning

A multi-company marketplace, not a single hotel chain's site: hotels in the backend belong to different companies (`companyId`/`companyName` on `HotelResponse`), so the product's value is aggregating and letting travelers choose across many independent hotels/chains — the same shape as an OTA (e.g. Booking.com/Expedia), not a single-brand booking page.

## Operating Context

- Backend is a separate Spring Boot service (`Hotel-system/`) exposing REST endpoints consumed via `frontend/src/api/*` (axios).
- Search today is by city (`GET /api/hotels/city/:city`); also available by country, company, and rating.
- Hotel detail shows available rooms for that hotel (`GET /api/rooms/available/:hotelId` equivalent) and lets a logged-in user open a booking form (check-in, check-out, guest count) per room.
- Auth is JWT-based (Pinia `auth` store) via a single global `AuthModal` popup, not dedicated `/login`/`/register` pages — email or phone entry, a 6-digit code, then (for new accounts) name/DOB/password with a strength meter; Google sign-in is also wired. There is no password-based login anymore — the code is the only way in, every time. A `requiresAuth` route guard on `/bookings` opens the modal instead of redirecting to a page.
- `AuthResponse` (and the persisted `auth` store) now carries the current user's numeric `userId` alongside `email`/`roles`, set at login/register/Google time — `BookingsView` and the booking-submit flow in `HotelDetailView` consume it directly.

## Capabilities and Constraints

- Hotel fields available for display: name, city, country, address, star rating (`startRating`, 1–5), phone, email, description, `imageUrl`, status (`ACTIVE`, `INACTIVE`, `UNDER_RENOVATION`, `COMING_SOON`, `CLOSED`, `SUSPENDED`), owning company.
- Room fields: type (`SINGLE`, `DOUBLE`, `TWIN`, `TRIPLE`, `SUITE`, `JUNIOR_SUITE`, `DELUXE`, `PENTHOUSE`, `FAMILY`, `CONNECTING`, `DORMITORY`, `STUDIO`, `VILLA`, `BUNGALOW`, `ACCESSIBLE`), price per night, capacity, floor, status.
- Booking fields: check-in/out, guest count, status (`PENDING`, `CONFIRMED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, `NO_SHOW`, `PAYMENT_FAILED`), total price, special request.
- No amenities, photo galleries (beyond a single `imageUrl`), or review/rating-count fields exist on the hotel model today — do not invent them as real data.
- Only a "search by city" flow exists as a real, wired search route today; other filters (country/company/rating) exist as API methods but no UI calls them yet.

## Brand Commitments

No confirmed brand. "Hotel Reservations" in the current nav is a placeholder, not a committed name. The user has approved inventing a plausible brand name/wordmark as part of this design work rather than keeping the literal placeholder.

## Evidence on Hand

No real per-hotel photography, copy, testimonials, or press exists. `HotelResponse.imageUrl` is a real field, populated per-property when a hotel operator supplies one; entries without it fall back to a small pool of verified, generic Unsplash interior/exterior photos (not a photo of that specific property — decorative representative imagery, not a factual claim). Do not fabricate specific hotel names, testimonials, or customer counts as if real.

## Product Principles

- Search-to-booking is the entire job; the main page's only real success metric is getting a traveler into a city search with confidence, not top-of-funnel storytelling.
- Aggregator honesty: never imply the main page's design that this is one hotel's own site — breadth/choice across independently operated hotels is the actual mechanism.
- Don't design UI around fields the API doesn't return (amenities, galleries, review counts) — treat those as absent rather than assumed.
- Keep parity with the existing product's plain, utilitarian tone (current UI is unstyled-functional) while raising visual craft — this is a redesign of tone, not a change of scope.
