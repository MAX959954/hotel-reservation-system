import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import Lenis from 'lenis'

gsap.registerPlugin(ScrollTrigger)

/**
 * Every scroll-driven effect in the app is measured against the same smooth-scroll
 * instance, so it lives here as a single shared handle rather than being created
 * per-component. Route changes need it too (to jump to the top without Lenis
 * animating the jump), which is why it is exported rather than kept inside App.vue.
 */
let lenis: Lenis | null = null

export function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function initSmoothScroll(): Lenis | null {
  if (prefersReducedMotion()) return null
  if (lenis) return lenis

  lenis = new Lenis({ duration: 1.15, smoothWheel: true })

  gsap.ticker.add(tickerCallback)
  gsap.ticker.lagSmoothing(0)
  lenis.on('scroll', ScrollTrigger.update)

  return lenis
}

function tickerCallback(time: number) {
  lenis?.raf(time * 1000)
}

export function destroySmoothScroll() {
  if (!lenis) return
  gsap.ticker.remove(tickerCallback)
  lenis.destroy()
  lenis = null
}

export function scrollToY(y: number) {
  if (lenis) {
    lenis.scrollTo(y)
  } else {
    window.scrollTo({ top: y, behavior: 'smooth' })
  }
}

export function scrollToElement(el: HTMLElement) {
  if (lenis) {
    const y = el.getBoundingClientRect().top + window.scrollY
    lenis.scrollTo(y, { immediate: true })
  } else {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

export function scrollToTop() {
  if (lenis) {
    lenis.scrollTo(0, { immediate: true })
  } else {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }
}

if (import.meta.env.DEV) {
  Object.assign(window as unknown as Record<string, unknown>, { gsap, ScrollTrigger })
}

export { gsap, ScrollTrigger }
