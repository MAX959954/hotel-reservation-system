import { useI18n } from 'vue-i18n'

export interface City {
  name: string
  country: string
}

/**
 * The real, currently-active cities in the register — read straight from the database
 * (`SELECT DISTINCT city, country FROM hotels WHERE status = 'ACTIVE'`). There is no
 * "list cities" endpoint, so this has to be maintained by hand rather than fetched, but it
 * must stay a list of cities that actually have stays: earlier drafts of this app used
 * 'Porto' and 'Lisbon' as example cities and neither one has ever had a seeded hotel, so
 * every link built from them landed on an empty "no stays yet" screen.
 */
export const ACTIVE_CITIES: City[] = [
  { name: 'Barcelona', country: 'Spain' },
  { name: 'Dubai', country: 'United Arab Emirates' },
  { name: 'Kyoto', country: 'Japan' },
  { name: 'London', country: 'United Kingdom' },
  { name: 'New York', country: 'United States' },
  { name: 'North Male Atoll', country: 'Maldives' },
  { name: 'Paris', country: 'France' },
  { name: 'Rome', country: 'Italy' },
  { name: 'Santorini', country: 'Greece' },
  { name: 'Tokyo', country: 'Japan' },
]

/** A short, visually varied subset for quick-pick chips — not every city needs a chip. */
export const QUICK_CITIES = ['Paris', 'Kyoto', 'Rome', 'Barcelona']

/**
 * Display name of a city in the current UI language. The English name is still the value
 * the API filters on and the one in the URL; a city with no translation (e.g. one a host
 * just added) shows as-is.
 */
export function useCityName() {
  const { t, te } = useI18n()
  return (name: string) => {
    const key = `city.${name.replace(/\s+/g, '_')}`
    return te(key) ? t(key) : name
  }
}
