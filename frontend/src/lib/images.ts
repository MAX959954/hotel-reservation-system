/**
 * Imagery ships with the app rather than being hotlinked. Migration V9 moved the seeded
 * hotels off remote URLs for exactly this reason — V7 had already had to patch two dead
 * links — so the same rule applies to decorative art: no external image host is a
 * dependency of the page rendering.
 *
 * All photos are Unsplash-licensed (free, commercial use, no attribution required) and
 * were checked to contain no identifiable hotel branding, so none of them implies a
 * relationship with a real business.
 */
const LOCAL_IMAGE_COUNT = 12

export const FALLBACK_IMAGES: readonly string[] = Array.from(
  { length: LOCAL_IMAGE_COUNT },
  (_, i) => `/images/hotels/hotel-${String(i + 1).padStart(2, '0')}.jpg`,
)

export const HERO_IMAGE = '/images/hero.jpg'

/** Stable per-id pick, so a given hotel keeps the same stand-in across renders. */
export function fallbackFor(seed: number): string {
  return FALLBACK_IMAGES[Math.abs(seed) % FALLBACK_IMAGES.length]
}

export function hotelImage(imageUrl: string | null | undefined, seed: number): string {
  return imageUrl && imageUrl.trim() ? imageUrl : fallbackFor(seed)
}

/**
 * `@error` handler for hotel imagery: a hotel row may point at a path that was never
 * added, so swap in a stand-in rather than leaving a broken-image box.
 */
export function onImageError(event: Event, seed: number) {
  const img = event.target as HTMLImageElement
  const fallback = fallbackFor(seed)
  if (!img.src.endsWith(fallback)) img.src = fallback
}
