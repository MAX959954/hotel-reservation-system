# Frontend generation prompt — Folio Stays

> Single source of truth. Supersedes `PROMPT_FLYTHROUGH_SELECT.md`, whose contents are folded
> into §9 below. Copy everything under the line into your AI code generator.
>
> Every field name, enum value and error string below was read out of the Java source in
> `Hotel-system/` — not guessed. Where an earlier draft of this document was wrong, the
> correction is marked ⚠ so the generator does not reintroduce the old value.

---

Build the landing experience for a premium hotel & apartment booking marketplace named **FOLIO**.
Aesthetic: dark, cinematic, editorial — think Aman Resorts crossed with a modern DeFi dashboard.
Deep near-black canvas, champagne-gold accent, heavy use of glassmorphism, and scroll-driven
"flythrough" motion where hotel cards travel toward the viewer out of deep space **and can be
selected**.

## Stack & Dependencies

- **Vue 3.5+** with `<script setup lang="ts">` Single File Components. No Options API.
- **Vite** + **TypeScript** (strict).
- **Tailwind CSS v4** (the `@import "tailwindcss"` + `@theme` syntax, not v3 config files).
- **GSAP 3** with the **ScrollTrigger** plugin — all scroll-driven motion.
- **Lenis** (`lenis`) — smooth scrolling, initialised once and shared.
- **lucide-vue-next** — all icons.
- **Pinia** — state. **vue-router 4** — routing.

Register GSAP plugins once: `gsap.registerPlugin(ScrollTrigger)`.
Always revert GSAP contexts in `onUnmounted()` to avoid leaks on route change.

---

## 1. Global Styles (`src/style.css`)

⚠ The Google Fonts `@import` **must come before** `@import "tailwindcss"`. The Tailwind import
expands into rules, and a plain `@import` is only legal before any rule — put it second and the
bundler drops the fonts with a warning.

```css
@import url('https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Inter:wght@300;400;500;600&display=swap');

@import "tailwindcss";

@theme {
  --font-display: "Instrument Serif", ui-serif, Georgia, serif;
  --font-sans: "Inter", ui-sans-serif, system-ui, sans-serif;

  --color-ink: #0a0a0b;
  --color-ink-2: #121215;
  --color-ink-3: #1a1a1f;
  --color-champagne: #d8b778;
  --color-champagne-bright: #efd9a8;
  --color-champagne-dim: #8a7449;
  --color-bone: #f4f1ec;
  --color-bone-dim: rgba(244, 241, 236, 0.62);
  --color-hairline: rgba(244, 241, 236, 0.12);
}

:root {
  font-family: var(--font-sans);
  background-color: var(--color-ink);
  color-scheme: dark;
}

body {
  margin: 0;
  overflow-x: hidden;
  background-color: var(--color-ink);
  color: var(--color-bone);
  -webkit-font-smoothing: antialiased;
}

@keyframes kenburns {
  0%   { transform: scale(1.08) translate3d(0, 0, 0); }
  100% { transform: scale(1.22) translate3d(-1.5%, -2%, 0); }
}
.animate-kenburns { animation: kenburns 22s ease-out infinite alternate; }

:where(a, button, input, select, textarea, [tabindex]):focus-visible {
  outline: 2px solid var(--color-champagne);
  outline-offset: 2px;
  border-radius: 4px;
}

/* Native date/number controls are near-invisible on a dark ground otherwise. */
input[type="date"]::-webkit-calendar-picker-indicator {
  filter: invert(1) opacity(0.55);
  cursor: pointer;
}

@media (prefers-reduced-motion: reduce) {
  .animate-kenburns { animation: none; }
  * { scroll-behavior: auto !important; }
}
```

## 2. Smooth scroll (`src/lib/motion.ts`) and App shell

Lenis is a **shared singleton**, not a per-component instance: route changes need it too (to jump
to the top without animating the jump), and every ScrollTrigger measures against it.

```ts
lenis = new Lenis({ duration: 1.15, smoothWheel: true })
gsap.ticker.add((time) => lenis.raf(time * 1000))
gsap.ticker.lagSmoothing(0)
lenis.on('scroll', ScrollTrigger.update)
```

Export `initSmoothScroll()`, `destroySmoothScroll()`, `scrollToTop()`, and
`prefersReducedMotion()`. **Skip Lenis entirely when reduced motion is requested.**

`App.vue` is `<main class="min-h-screen bg-ink">` with `<RouterView />` and the global
`<AuthModal />`. Home renders `<Hero />`, `<FlythroughGallery />`, `<StayTypes />`, `<SiteFooter />`.

---

## 3. Imagery — ship it, don't hotlink

⚠ Do **not** reference `images.unsplash.com` at runtime. Migration `V9__use_local_hotel_images.sql`
moved seeded hotels off remote URLs precisely because V7 had already had to patch two dead links.
The same rule applies to decorative art.

Place 12 photos at `public/images/hotels/hotel-01.jpg` … `hotel-12.jpg` and one wide hero at
`public/images/hero.jpg`. `src/lib/images.ts` exposes:

```ts
export const FALLBACK_IMAGES: readonly string[]   // the 12 local paths
export const HERO_IMAGE = '/images/hero.jpg'
export function hotelImage(imageUrl: string | null | undefined, seed: number): string
export function onImageError(event: Event, seed: number): void
```

`hotelImage` returns the hotel's own `imageUrl` when present, else a stable per-id stand-in.
`onImageError` swaps in a stand-in when a path 404s, so a missing asset never shows a broken-image box.

**Two rules about what these photos may depict:**
- **No identifiable branding.** A photo showing a real hotel's signage, attached to a different
  hotel's name, presents one business's premises as another's. Hotel *exteriors* are the trap here —
  people photograph buildings *because* of the sign. Interiors are inherently anonymous; prefer them.
- **Never presented as that specific property.** Stand-ins are decorative representative imagery,
  not a factual claim about the hotel.

**Brightness matters on a near-black UI.** The page ground is `#0a0a0b` (luminance ≈10). A photo
with mean luminance under ~45 reads as a black rectangle and looks like a failed load. Target mean
luminance roughly **60–170**. On this design the *photographs* carry the light; the UI carries the dark.

## 4. `src/components/Hero.vue`

Outer: `<div class="w-full h-screen flex items-center justify-center p-3 md:p-5 bg-ink">`
Inner: `<section class="relative w-full max-w-[1600px] h-full rounded-[1.5rem] md:rounded-[2.5rem] overflow-hidden flex flex-col items-center bg-ink-2 group">`

Background `<img :src="HERO_IMAGE" alt="">` with
`absolute inset-0 w-full h-full object-cover object-center z-0 animate-kenburns`.

Scrim — two stacked gradients so text stays legible over any photo:
`absolute inset-0 z-[1] bg-gradient-to-b from-ink/85 via-ink/40 to-ink/95`, plus
`absolute inset-0 z-[1] bg-gradient-to-r from-ink/70 to-transparent`.

Content layer `relative z-10 w-full h-full flex flex-col items-center` holds `<Navbar />`, the text
block, `<SearchBar />`, `<StatCard />`, `<CornerPanel />`.

Text block: `w-full flex flex-col items-center pt-10 md:pt-16 px-6 text-center max-w-4xl`
- `<HeroBadge />`
- `<h1 class="font-display text-5xl sm:text-6xl md:text-7xl lg:text-[88px] font-normal text-bone mb-3 tracking-[-0.02em] leading-[0.98]">` —
  "Rooms <em class="text-champagne not-italic">worth the journey</em>".
  Intro: from `{ opacity: 0, y: 28, filter: 'blur(12px)' }` → `{ opacity: 1, y: 0, filter: 'blur(0px)', duration: 1, ease: 'power3.out' }`.
- `<p class="text-sm sm:text-base md:text-lg text-bone-dim leading-relaxed max-w-xl font-light">` —
  "Independently run hotels and apartments across 12 cities. One register, no noise."
  Intro: opacity 0 → 1, `delay: 0.25, duration: 0.9`.

Guard every intro with `prefersReducedMotion()`.

## 5. `src/components/Navbar.vue`

`<nav class="flex items-center justify-between py-6 px-6 md:px-10 w-full relative z-10">`

- Wordmark left: `<span class="font-display text-2xl tracking-tight text-bone">FOLIO</span>`, links to `/`.
- Centre menu: `<ul class="hidden md:flex items-center gap-9 text-bone-dim font-light text-sm">` —
  `Stays`, `Cities` (dropdown), `Apartments`, `Journal`. Each `<li>`:
  `cursor-pointer hover:text-bone transition-colors flex items-center gap-1 group`; dropdown items
  append `<ChevronDown class="w-3.5 h-3.5 transition-transform group-hover:translate-y-0.5" />`.
- Right: when signed out, a button
  `flex items-center bg-champagne text-ink rounded-full pl-2 pr-4 md:pr-6 py-1.5 md:py-2 gap-2 md:gap-3 hover:bg-champagne-bright hover:scale-[1.02] active:scale-[0.98] transition-all group`
  containing `<span class="bg-ink/15 p-1 md:p-1.5 rounded-full">` + `<ArrowUpRight />` and "Sign in".
  When signed in, show a "My bookings" link and a bordered "Sign out" button instead.

## 6. `src/components/HeroBadge.vue`

`flex items-center gap-2 px-4 py-2 rounded-full bg-bone/8 backdrop-blur-md border border-hairline mx-auto mb-5 w-fit`,
holding `<Sparkles class="w-3.5 h-3.5 text-champagne" />` and
`<span class="text-[13px] font-light tracking-wide text-bone-dim">12 cities · 40 independent stays</span>`.
Intro from `{ opacity: 0, y: 16 }`, 0.6s, `power2.out`.

## 7. `src/components/SearchBar.vue` — the primary conversion element

Search-to-booking is the entire product job. Make this the most tactile object on screen; the
flythrough must never outshine it.

Wrapper `w-full max-w-3xl px-6 mt-8 md:mt-10`. Bar:
`flex flex-col md:flex-row items-stretch md:items-center gap-2 md:gap-0 p-2 rounded-[1.5rem] md:rounded-full bg-bone/8 backdrop-blur-2xl border border-hairline shadow-[0_20px_60px_-20px_rgba(0,0,0,0.8)]`

Three segments split by `<div class="hidden md:block w-px h-8 bg-hairline" />`:
1. **City** — `<MapPin class="w-4 h-4 text-champagne" />` + text input, placeholder "Where to?"
2. **Dates** — `<CalendarDays />` + two `<input type="date">`
3. **Guests** — `<Users />` + `<input type="number" min="1">`

Inputs: `bg-transparent outline-none text-sm text-bone placeholder:text-bone-dim/70 w-full font-light`.
Every input needs a label — use `class="sr-only"` spans, not placeholder-as-label.

Submit: `shrink-0 flex items-center justify-center gap-2 bg-champagne text-ink rounded-full px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors`
with `<Search class="w-4 h-4" />` and "Search" →
`router.push({ name: 'hotels', query: { city, checkIn, checkOut, guests } })`.

Below, quick-city chips `Porto`, `Kyoto`, `Lisbon`, `Barcelona`:
`px-3 py-1.5 rounded-full text-xs font-light text-bone-dim bg-bone/5 border border-hairline hover:border-champagne-dim hover:text-bone transition-colors`.

## 8. `StatCard.vue` and `CornerPanel.vue`

**StatCard** — glass card, absolutely positioned:
`absolute bottom-28 right-4 left-auto md:left-6 md:right-auto md:bottom-6 lg:bottom-10 lg:left-10 p-4 lg:p-5 rounded-[1.2rem] lg:rounded-[2rem] bg-bone/8 backdrop-blur-xl border border-hairline flex flex-col gap-3 min-w-[150px] lg:min-w-[190px] w-fit`.
Contents: `4.8` in `font-display text-3xl md:text-4xl text-bone`, the label
`Average guest rating` in `text-[10px] md:text-[11px] font-light text-bone-dim uppercase tracking-[0.14em]`,
and a bone pill button "Read reviews". Intro from `{ x: -24, opacity: 0 }`, 0.8s, delay 0.2.

**CornerPanel** — faux cut-out corner:
`absolute bottom-0 right-0 p-3 pt-5 pl-8 sm:p-4 sm:pt-6 sm:pl-10 md:p-6 md:pt-8 md:pl-14 bg-ink rounded-tl-[1.5rem] sm:rounded-tl-[2rem] md:rounded-tl-[3.5rem] flex items-center gap-3 sm:gap-4 md:gap-6`.

Both inverse-radius masks are required — they paint page-background into the outer corners so the
panel reads as cut *out of* the hero rather than pasted on top:

```html
<div class="absolute -top-[1.5rem] sm:-top-[2rem] md:-top-[3.5rem] right-0 w-[1.5rem] sm:w-[2rem] md:w-[3.5rem] h-[1.5rem] sm:h-[2rem] md:h-[3.5rem] pointer-events-none" aria-hidden="true">
  <svg width="100%" height="100%" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M56 56V0C56 30.9279 30.9279 56 0 56H56Z" fill="#0a0a0b"/>
  </svg>
</div>
<div class="absolute bottom-0 -left-[1.5rem] sm:-left-[2rem] md:-left-[3.5rem] w-[1.5rem] sm:w-[2rem] md:w-[3.5rem] h-[1.5rem] sm:h-[2rem] md:h-[3.5rem] pointer-events-none" aria-hidden="true">
  <svg width="100%" height="100%" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M56 56H0C30.9279 56 56 30.9279 56 0V56Z" fill="#0a0a0b"/>
  </svg>
</div>
```

Content: a `bg-bone/5 w-10 h-10 md:w-14 md:h-14 rounded-full border border-hairline` circle holding
`<Play class="text-champagne" />`, then "Take the tour" (`font-display`) over a "2 min film" row
with `<ChevronRight />`.

---

## 9. `src/components/FlythroughGallery.vue` — THE signature element

Stay cards fly out of deep space toward the camera and past it, driven by scroll — **and the stay
currently in front of the camera can be opened.** This is the centrepiece; invest the most effort here.

### 9.1 Structure

```html
<section ref="root" class="relative h-[400vh] bg-ink">
  <div class="sticky top-0 h-screen w-full overflow-hidden flex items-center justify-center"
       style="perspective: 1200px; perspective-origin: 50% 50%; transform-style: preserve-3d">
    <h2 class="absolute font-display text-[13vw] leading-none text-bone/8 select-none pointer-events-none z-0">STAYS</h2>
    <!-- six cards -->
  </div>
</section>
```

Cards: `absolute w-[280px] md:w-[380px] aspect-[3/4] rounded-[1.5rem] overflow-hidden border border-hairline bg-ink-2 will-change-transform opacity-0 z-10`
with `style="backface-visibility: hidden"`. Each holds a cover `<img class="w-full h-full object-cover">`,
a bottom scrim `absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-ink to-transparent`, and a caption.

### 9.2 The cards must carry real hotels

A card cannot route anywhere until it holds a real `HotelResponse.id`. Do not hard-code stay names.

⚠ **The API has no "list all hotels" endpoint.** Only by-id, by-city, by-country, by-company and
by-rating exist. Pick one strategy and say which in a comment — do not invent an endpoint:

- **Preferred:** `GET /api/hotels/rating/5`, presented honestly as "Five-star stays". One request,
  and the section has a truthful reason to exist.
- **Alternative:** fetch 3–4 cities with `Promise.allSettled` and merge, tolerating partial failure.

Take the first six. If fewer than three come back, render the static grid (§9.7) instead.

**States:**
- **Loading** — hold the section at full height showing only the `STAYS` wordmark. Never build the
  ScrollTrigger before data resolves, or the pin measures against the wrong height.
- **Error / empty** — the grid fallback with a short line of copy. Never a dead 400vh pinned void.
- **Loaded** — build the timeline, then call `ScrollTrigger.refresh()` once. Images changing layout
  after pin creation is the classic cause of a mis-measured pin.

### 9.3 The motion

One timeline bound to one ScrollTrigger:

```ts
ScrollTrigger.create({ trigger: root.value, start: 'top top', end: 'bottom bottom', scrub: 1.1, animation: tl })
```

Each card `i` of `n` owns the slice starting at `i / n * 0.8`, duration 1 — so slices overlap and
the stream never empties. Per card:

- **Position tween (linear, one tween):** from
  `{ z: -2600, scale: 0.42, rotateY: gsap.utils.random(-16, 16), x: gsap.utils.random(-260, 260), y: gsap.utils.random(-120, 120) }`
  to `{ z: 850, scale: 1.25, rotateY: 0, x: 0, y: 0, ease: 'none' }`.

- **Opacity, on its own tweens** so it peaks mid-flight: `0 → 1` over the first 25% of the slice,
  `1 → 0` over the last 20%. Cards materialise out of darkness and dissolve as they pass.

- ⚠ **Blur, also on its own tweens, reaching zero.** An earlier draft ran blur from `14px` to `6px`
  in a single tween — so the minimum blur was 6px, reached only once opacity had already faded to 0,
  and at peak visibility the card sat at roughly **10px of blur**. It was never in focus and its
  caption was unreadable. That is fatal now that the card is a control: **nobody can choose a hotel
  they cannot read.** Instead: `blur(14px) → blur(0px)` over the first ~30% of the slice, hold sharp
  through the legible window, then `blur(0px) → blur(10px)` over the last ~20%.

**Acceptance:** at the timeline position where a card's opacity is 1, its computed `filter` is
`blur(0px)` and its caption is legible at 100% zoom.

### 9.4 Breakpoint tuning — use `gsap.matchMedia`, not a one-shot read

⚠ Reading the viewport once in `onMounted` freezes the tuning at whatever size the component
happened to mount at, and it stays wrong through every later resize or rotation. Use
`gsap.matchMedia()`, which re-runs setup on breakpoint change and reverts the previous build:

```ts
mm.add({ isMobile: '(max-width: 767px)', isDesktop: '(min-width: 768px)' }, (ctx) => { … })
```

On mobile halve the `z` travel (`-1300 → 420`) and soften the entry blur to `8px` — deep blur over
long 3D travel is the expensive part on mobile GPUs. Keep all six cards; do not change the DOM per
breakpoint.

Revert the matchMedia in `onUnmounted`.

### 9.5 The active card

A card flying past under `scrub` is a hostile click target: moving, overlapped by neighbours. Do
**not** put `@click` on all six.

Exactly one card is **active** — the one whose slice is in its legible window. Derive it in the
ScrollTrigger's `onUpdate` by mapping `self.progress` to the nearest slice midpoint, and store it in
a `ref<number | null>`. **Update the ref only when the index actually changes**, or the list
re-renders 60 times a second.

Active card treatment:
- border becomes `--color-champagne` instead of `--color-hairline`
- `box-shadow: 0 0 0 1px rgba(216,183,120,0.35), 0 30px 80px -20px rgba(0,0,0,0.9)`
- caption gains the hotel name, `{startRating} ★ · {city}, {country}`, and a pill reading
  **View stay →** in `bg-champagne text-ink rounded-full px-3 py-1.5 text-xs font-medium`
- `cursor: pointer` on the active card only
- ⚠ `pointer-events: none` on every **inactive** card — otherwise a click lands on whichever
  neighbour happens to sit under the cursor, and the user opens a hotel they did not pick

Inactive cards keep their current look; they are already fading through opacity.

### 9.6 Interaction

**Pointer.** Clicking anywhere on the active card opens it — the pill is an affordance, not the
only hit area.

**Hover pauses the motion.** `tl.pause()` on `pointerenter`, `tl.resume()` on `pointerleave`. This
is the single most important usability fix here: a target moving at scrub speed is a Fitts's-law trap.

**Keyboard — mandatory.** The stream must not be the only way in.
- Only the active card is in the tab order: `:tabindex="isActive ? 0 : -1"`.
- `Enter` and `Space` open it; prevent `Space` from also scrolling.
- `ArrowLeft` / `ArrowRight` step between stays by scrolling to that card's timeline position
  (compute from the trigger's `start`/`end` and the slice midpoint).
- Tab must not be trapped by the pin.

**Touch.** Hover does not exist. Under `(hover: none)`, the first tap on the active card stops the
flythrough there and the second opens it — otherwise a scroll gesture ending on a card fires a
navigation nobody asked for.

### 9.7 Reduced motion, and the fallback grid

Under `prefers-reduced-motion: reduce`, skip the timeline entirely: no pinning, no 3D, no scroll
hijack. Render the six stays as a plain responsive grid.

That grid is also the fallback for too-few results. **Every card in it must be a real
`<RouterLink :to="{ name: 'hotel', params: { id } }">`** carrying the same caption content —
including star rating and the "View stay →" affordance. The grid must reach the same destination
with no motion involved at all.

### 9.8 Opening a stay, and coming back

`router.push({ name: 'hotel', params: { id }, query: { from: 'flythrough' } })`.

⚠ **Restore scroll on back.** With `scrollBehavior: () => ({ top: 0 })` plus a `scrollToTop()` in
`afterEach`, returning from a stay dumps the user at the top and they lose their place in a 400vh
section. Instead honour `savedPosition` — `(to, from, savedPosition) => savedPosition ?? { top: 0 }` —
skip the scroll reset on `popstate`, and call `ScrollTrigger.refresh()` after restoring.

**Prefetch on intent:** when a card becomes active, prefetch `GET /api/hotels/{id}` into a Pinia
cache keyed by id, and have `HotelDetailView` read that cache before firing its own request. Do
**not** prefetch rooms — that call needs `checkIn`/`checkOut`, which the flythrough does not know,
and guessing dates would show availability for a stay nobody asked about.

### 9.9 Performance and accessibility

- Animate only `transform`, `opacity`, `filter`. Never `top` / `left` / `width`.
- Stage gets `role="list"`, each card `role="listitem"`.
- Announce the active stay in a visually hidden live region:
  `<p class="sr-only" aria-live="polite">{{ activeStay?.name }}, {{ activeStay?.city }}</p>` —
  updated on index change only, never per frame.
- Images keep meaningful `alt`: `"{name} in {city}"`.

## 10. `src/components/StayTypes.vue`

Two split panels — **Hotels** and **Apartments** — side by side on desktop, stacked on mobile.
Each: `relative h-[70vh] rounded-[2rem] overflow-hidden group cursor-pointer border border-hairline`,
cover image scaling `group-hover:scale-105 transition-transform duration-[1.2s] ease-[cubic-bezier(0.16,1,0.3,1)]`,
a scrim, and a caption in `font-display text-4xl text-bone` with an `<ArrowUpRight />` translating
`group-hover:translate-x-1 group-hover:-translate-y-1`. Each panel routes to `/hotels?city=…`.
Reveal with `ScrollTrigger.batch`, from `{ y: 60, opacity: 0 }`, stagger 0.12, `start: 'top 80%'`, `once: true`.

---

## 11. Backend contract — DO NOT invent fields

Type everything in `src/types/`, call it from `src/api/` with axios. JWT lives in a Pinia `auth`
store, attached by a request interceptor as `Authorization: Bearer <token>`; on `401`, clear the store.
Base URL from `import.meta.env.VITE_API_BASE_URL`, fallback `http://localhost:8080`.

**Endpoints that exist:**
```
GET   /api/hotels/{id}
GET   /api/hotels/city/{city}
GET   /api/hotels/country/{country}
GET   /api/hotels/company/{companyId}
GET   /api/hotels/rating/{rating}
GET   /api/rooms/hotels/{hotelId}
GET   /api/rooms/hotels/{hotelId}/available?checkIn=&checkOut=&guestCount=

POST  /api/bookings
GET   /api/bookings/user/{userId}          (isSelf-guarded; any other id → 403)
PATCH /api/bookings/{id}/cancel

POST  /api/auth/otp/request               { identifier }            -> 202, empty body
POST  /api/auth/otp/verify                { identifier, code }      -> OtpVerifyResponse
POST  /api/auth/complete-registration     { verificationTicket, … } -> AuthResponse (201)
POST  /api/auth/google                    { idToken }               -> AuthResponse
```
`checkIn` / `checkOut` are **LocalDateTime** strings — `2026-08-14T15:00:00`, no `Z`, no offset.

**`HotelResponse`:** `id, name, city, country, address, startRating (1–5), phone, email,
description, imageUrl, status, companyId, companyName, amenities`.
⚠ The field really is spelled `startRating`. Keep the typo; it is the wire format.

**`RoomResponse`:** `id, number, type, pricePerNight, capacity, floor, status, description,
createdAt, hotelId, hotelName`.

**`AuthResponse`:** `token, tokenType ("Bearer"), userId, email, roles`.

**`ApiError`** ⚠ — exactly `{ status: number, message: string, timestamp: string, errors?: string[] }`.
There is no `error` field and no `path` field. Surface `message` (or joined `errors`) inline;
never replace it with a generic "Something went wrong" — the server's sentence is the only text
that tells the user what to do.

**Enums — exact values, invent none:**
- `RoomType`: `SINGLE, DOUBLE, TWIN, TRIPLE, SUITE, JUNIOR_SUITE, DELUXE, PENTHOUSE, FAMILY, CONNECTING, DORMITORY, STUDIO, VILLA, BUNGALOW, ACCESSIBLE`
- `RoomStatus` ⚠: `AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE, OUT_OF_ORDER` — the last one is
  `OUT_OF_ORDER`, not `OUT_OF_SERVICE`
- `BookingStatus`: `PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW, PAYMENT_FAILED`
- `Hotel_Status`: `ACTIVE, INACTIVE, UNDER_RENOVATION, COMING_SOON, CLOSED, SUSPENDED`
- `Roles` ⚠: `GUEST, COMPANY_CLIENT, RECEPTIONIST, HOTEL_MANAGER, ADMIN, SUPPORT` — there is no
  `USER`, no `MANAGER`, no `OWNER`
- `Amenity` ⚠ — a **closed** enum of 22: `WIFI, BREAKFAST, AIR_CONDITIONING, PARKING, POOL, GYM,
  SPA, BAR, RESTAURANT, ROOM_SERVICE, AIRPORT_SHUTTLE, PET_FRIENDLY, ELEVATOR, LAUNDRY, WORKSPACE,
  TV, COFFEE_MAKER, HAIR_DRYER, LUGGAGE_STORAGE, ACCESSIBLE, EV_CHARGING, NON_SMOKING`

**Hard rule:** there are **no** review counts and no photo galleries. Do not design UI around data
the API does not return — treat anything not listed above as absent.

## 12. Routing (`src/router/index.ts`)

```
/                  HomeView         — Hero + FlythroughGallery + StayTypes + Footer
/hotels            HotelsView       — reads ?city&checkIn&checkOut&guests
/hotels/:id        HotelDetailView  — one hotel + its available rooms
/bookings          BookingsView     — meta: { requiresAuth: true }
/:pathMatch(.*)*   NotFoundView
```

`createWebHistory`. `scrollBehavior: (to, from, savedPosition) => savedPosition ?? { top: 0 }` (§9.8).

**Auth guard:** a `requiresAuth` route must **not** redirect to a login page — there isn't one.
Open the global `AuthModal` over the current view and resolve the navigation only after a
successful sign-in, keeping the intended route in the auth-modal store.

## 13. `src/views/HotelsView.vue`

Reads `route.query.city`, calls `GET /api/hotels/city/{city}`. Sticky filter bar
(`sticky top-0 z-30 bg-ink/80 backdrop-blur-xl border-b border-hairline`) carrying city, dates and
guests as chips, then `grid gap-6 sm:grid-cols-2 lg:grid-cols-3`.

Card: `group relative rounded-[1.25rem] overflow-hidden bg-ink-2 border border-hairline hover:border-champagne-dim transition-colors duration-500`,
4:3 cover with `group-hover:scale-[1.04] transition-transform duration-[1.2s] ease-[cubic-bezier(0.16,1,0.3,1)]`,
a rating badge `absolute top-3 right-3 px-2.5 py-1 rounded-full bg-ink/70 backdrop-blur-md border border-hairline text-[11px] font-medium text-champagne`
rendering `startRating` as "N ★" — **not** five icons; the API gives an integer, nothing more.

**Build all four states:** skeleton cards (`animate-pulse bg-bone/5`, never a bare spinner); empty
("No stays in {city} yet" + quick-city chips as recovery); error (the `ApiError` message + Retry);
loaded. Reveal with `ScrollTrigger.batch`, from `{ y: 40, opacity: 0 }`, stagger 0.08, `start: 'top 88%'`, `once: true`.

## 14. `src/views/HotelDetailView.vue`

`GET /api/hotels/{id}` and `GET /api/rooms/hotels/{id}/available?…`. Read the flythrough prefetch
cache first (§9.8).

Header: full-bleed `h-[60vh]` cover with ken-burns and a scrim; hotel name in
`font-display text-5xl md:text-7xl text-bone`; meta line with `MapPin` + address/city/country,
`Phone`, `Mail`. Show `status` as a pill **only when it is not `ACTIVE`**, in
`text-amber-300/90 bg-amber-300/10 border-amber-300/20`.

Amenities render as plain text chips (humanise `AIR_CONDITIONING` → "Air conditioning").

Rooms: one row each, `flex items-center justify-between gap-4 p-5 rounded-[1.25rem] bg-ink-2 border border-hairline hover:border-champagne-dim transition-colors`.
Left: humanised `type` (`JUNIOR_SUITE` → "Junior suite"), `number`, `floor`, `Sleeps {capacity}`.
Right: `pricePerNight` in `font-display text-2xl text-bone` with a `/ night` suffix, and **Reserve**.
If not authenticated, Reserve opens `AuthModal` first, then `BookingModal`.

## 15. `src/components/BookingModal.vue`

Glass panel `w-full max-w-lg rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)]`
over a `fixed inset-0 bg-ink/70 backdrop-blur-sm` overlay. Trap focus; close on `Esc` and overlay click.
Enter from `{ opacity: 0, y: 24, scale: 0.97 }`, 0.35s `power3.out`.

**`POST /api/bookings` body — exactly these keys:**
```ts
{ roomId: number, checkIn: string, checkOut: string, guestCount: number, specialRequest?: string }
```
⚠ **Never send `userId`.** The server takes the booking's owner from the JWT — `BookingServiceImpl`
comments this explicitly as the defence against booking on someone else's account.

Serialise as `YYYY-MM-DDTHH:mm:ss`; send hotel-standard times (check-in `T15:00:00`, check-out `T11:00:00`).

**Mirror the server's validation client-side:**
- both dates **in the future** (server has `@Future` — today is rejected, so default to tomorrow);
- check-out strictly after check-in;
- `guestCount >= 1` and `<= room.capacity`;
- ⚠ nights are a **calendar-date** span: Mon 15:00 → Thu 11:00 is **3** nights. Counting elapsed
  hours floors it to 2 and puts your price out of step with the server's.

Show `nights × pricePerNight` live above the submit button.

On success show a confirmation inside the same modal carrying the returned `bookingStatus` — it
comes back **`PENDING`**; ⚠ do not label it "Confirmed", the property has not confirmed anything yet.

## 16. `src/components/AuthModal.vue` — passwordless, code-first

There is **no password login**; a one-time code is the only way in, every time.

**Step 1 — identifier.** ⚠ The server validates this with `@Email`, so it is **email only**. Do not
offer a phone option — it 400s. `POST /api/auth/otp/request` → `202`, empty body. Show a **30 s**
resend cooldown and note the code expires in **10 minutes**; both are real server limits.

**Step 2 — 6-digit code.** Six inputs, paste-to-fill, auto-advance.
`POST /api/auth/otp/verify` → `{ newAccount, verificationTicket?, auth? }`.
`newAccount === false` → `auth` present, store and close. `true` → keep the ticket, go to step 3.

**Step 3 — new accounts only.** `firstName`, `lastName`, `dateOfBirth` (`YYYY-MM-DD`), `password`
(min 8, with a strength meter) → `POST /api/auth/complete-registration` → `AuthResponse` (201).
⚠ The server enforces **18+** and returns "You must be at least 18 years old to book with us." —
validate client-side too.

**Google:** `POST /api/auth/google` with `{ idToken }`.

Surface `ApiError.message` inline under the relevant field, never as a bare toast. Real cases to
handle: "Please wait before requesting another code.", "Too many codes requested. Please try again
later.", "Incorrect code.", "This code has expired. Request a new one.", and
"Verification expired — start again." — the last one must send the user back to step 1, because the
ticket is gone and step 2 can no longer succeed.

## 17. `src/views/BookingsView.vue`

`GET /api/bookings/user/{userId}` with `userId` from the auth store.

One card per booking: hotel name, room number, date range, guests, `totalPrice`, status pill.
Colours: `PENDING` amber · `CONFIRMED` champagne · `CHECKED_IN` sky · `COMPLETED` bone-dim ·
`CANCELLED` / `NO_SHOW` / `PAYMENT_FAILED` rose.

Cancel is `PATCH /api/bookings/{id}/cancel`. ⚠ Show the button **only** when status is not
`COMPLETED` and not `CANCELLED` — those are the exact two the server refuses. Confirm first, then
**refetch from the server** rather than mutating local state: cancelling also frees the room
server-side, so the authoritative status is whatever the server now says.

## 18. State (`src/stores/`)

- **`auth`** — `{ token, tokenType, userId, email, roles }`, persisted to `localStorage` under
  `folio-auth`, hydrated on load. Getters `isAuthenticated`, `hasRole`; actions `setSession`, `logout`.
- **`authModal`** — `{ open, step, identifier, verificationTicket, intendedRoute }`, plus a
  `prompt()` action returning a `Promise<boolean>` that resolves once the user is signed in or
  dismisses, so the router guard and the Reserve button can both await a sign-in.
- **`hotels`** — prefetch cache keyed by id (§9.8).
- **`currency`** — display currency + `Intl.NumberFormat`. Prices arrive as bare numbers with no
  currency field, so there is nothing to convert *from*: pick one display currency and label it
  honestly rather than implying conversion.

## 19. Quality bar

- Responsive at 375px, 768px, 1280px, 1920px. No horizontal scroll at any width.
- Every interactive element has a visible `:focus-visible` ring in `--color-champagne`.
- Meaningful `alt` on content images; `alt=""` on decorative ones.
- Colour, radii and type scale come **only** from `@theme` tokens — no ad-hoc hex in components.
- `npm run build` (`vue-tsc -b && vite build`) passes with zero type errors **and zero warnings**.
