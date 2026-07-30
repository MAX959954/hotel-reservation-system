---
name: Folio
description: A guest register for an independently-run hotel marketplace.
colors:
  ink: "#1c1310"
  ink-raised: "#241a15"
  ink-cover: "#2c1b17"
  ink-cover-2: "#3a1f1c"
  paper: "#f2e8d3"
  paper-2: "#e9dcbf"
  paper-line: "#c9b78f"
  paper-ink: "#241a12"
  paper-ink-soft: "#5a4a35"
  brass: "#c99a4b"
  brass-bright: "#e3b96b"
  brass-dim: "#8a6a37"
  stamp: "#8c2f39"
  stamp-bright: "#b23f4c"
  text: "#d8c9ae"
  text-dim: "#a9967a"
  text-h: "#f4ead2"
  border: "#4a362d"
typography:
  display:
    fontFamily: "Spectral, Iowan Old Style, Palatino Linotype, serif"
    fontSize: "clamp(1.8rem, 3.6vw, 2.5rem)"
    fontWeight: 600
    lineHeight: 1.15
  body:
    fontFamily: "Spectral, Iowan Old Style, Palatino Linotype, serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Courier Prime, Courier New, ui-monospace, monospace"
    fontSize: "0.78rem"
    letterSpacing: "0.06em"
rounded:
  sm: "2px"
  md: "3px"
  lg: "6px"
spacing:
  sm: "0.5rem"
  md: "1rem"
  lg: "1.75rem"
  xl: "3rem"
components:
  button-primary:
    backgroundColor: "{colors.brass}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "0.55rem 1rem"
  button-primary-hover:
    backgroundColor: "{colors.brass-bright}"
  stamp-badge:
    backgroundColor: "transparent"
    textColor: "{colors.stamp-bright}"
    rounded: "{rounded.md}"
    padding: "0.4rem 0.75rem"
---

# Design System: Folio

## Overview

**Creative North Star: "The Guest Register"**

Folio's world is a hotel front desk at evening: a leather-bound ledger open under lamp light, brass fittings, ink stamps recording what just happened. The system exists to make a marketplace of independently-run hotels feel like one trusted, hand-kept record rather than a template of cards over a stock photo. Density is moderate and deliberate — ruled lines, folio numbers, and tabbed indices carry structure that would otherwise need borders and boxes.

The ground is dark by design (leather and wood, not paper) — the scene is evening, not a bright morning desk — with ivory ledger-paper appearing only inside bounded "page" panels, never as the page background. Brass is the one committed accent, carrying primary actions and structure. Oxblood ink-stamp red is confirmed and deliberately rare: reserved for status and confirmation, never for ordinary buttons or links, so its appearance always means something happened (booked, searched, confirmed).

Confirmed rejections: no glossy full-bleed hero photography as the organizing device; no cream/parchment page-wide background (paper is a bounded material, not the ground); no icon-tile or gradient-card scaffolding.

**Key Characteristics:**
- Dark leather-and-wood ground, never a light theme
- Brass is the single committed accent; oxblood is reserved for status only
- Ruled "ledger-line" texture stands in for card borders and dividers
- Spectral (serif) carries voice; Courier Prime (mono) carries data, labels, and controls

## Colors

A dark, warm, low-saturation ground with exactly one bright committed accent and one rare status color.

### Primary
- **Warm Brass** (`#c99a4b`, hover `#e3b96b`, dim `#8a6a37`): the committed accent. Carries every primary action (search submit, register, view bookings), nav CTA, ledger clasps, rating stars, and section rules. Owns roughly a third of any given screen's visual weight through borders, rules, and filled buttons — never washed over full backgrounds.

### Neutral
- **Oxblood Leather** (`#1c1310` ink, `#241a15` raised, `#2c1b17`/`#3a1f1c` cover): the page background and header/footer cover tones. This is the "desk," not a card surface — nothing sits directly on `ink` without at least the `ink-raised` step between it and content.
- **Ledger Paper** (`#f2e8d3` paper, `#e9dcbf` paper-2, `#c9b78f` ruled line): used only inside bounded "page" panels (the hero ledger spread, blank-image fallbacks). Never the page-level background.
- **Warm Parchment Text** (`#d8c9ae` body, `#a9967a` dim, `#f4ead2` headings): body copy and headings on the dark ground.
- **Umber Border** (`#4a362d`): hairline dividers and card borders on the dark ground.

### Named Rules
**The Rare Ink Rule.** Oxblood (`#8c2f39` / `#b23f4c`) only ever labels a status or confirmation (the three "Searched / Reserved / Confirmed" stamps). It never colors a primary button, a link, or decoration — if oxblood appears, something was confirmed.

## Typography

**Display/Body Font:** Spectral (with Iowan Old Style, Palatino Linotype, serif fallback)
**Label/Mono Font:** Courier Prime (with Courier New, ui-monospace fallback)

**Character:** Spectral is a registry/document-grade serif — it carries the voice of someone writing in a ledger, not a marketing display face. Courier Prime is a real typewriter face, used wherever the interface is "entering data": room numbers, dates, prices, nav labels, buttons.

### Hierarchy
- **Display** (600, `clamp(1.8rem, 3.6vw, 2.5rem)`, 1.15 line-height): hero and section headlines, Spectral, occasionally italic for the ledger-page voice.
- **Body** (400, 17px, 1.55 line-height): running copy, Spectral, measure capped near 40–55ch inside page panels.
- **Label** (400, 0.7–0.85rem, 0.06–0.16em tracking, uppercase): nav tabs, folio numbers, buttons, badges — always Courier Prime, always uppercase, always tracked.

### Named Rules
**The Two-Voice Rule.** Only two families exist in the whole system. If a new element needs emphasis, reach for weight, italics, or the Spectral/Courier Prime switch — never a third font.

## Layout

Content is capped at 1100px (page shell) with the hero ledger capped narrower at 980px so it reads as one physical spread. Sections stack with generous vertical rhythm (`clamp(2.5rem, 5vw, 3.5rem)` top padding) separated by a single hairline (`--border`), never a card shell. The hero collapses from a two-page side-by-side spread to a single stacked page under 720px, dropping the center spine/clasps entirely rather than shrinking them. Grids (the five-star folio list) use `auto-fill, minmax(220px, 1fr)` so density adapts without breakpoints.

## Elevation & Depth

Mostly flat. Depth comes from material change (paper vs. leather) and one soft drop shadow under the hero ledger itself (`0 30px 60px -30px rgba(0,0,0,0.65)`) to sell it as a physical object resting on the page — not from card shadows or hover-elevation tricks.

### Shadow Vocabulary
- **hero-object** (`0 30px 60px -30px rgba(0,0,0,0.65), 0 2px 0 rgba(0,0,0,0.25)`): the one shadow in the system, used only on the hero ledger spread.

### Named Rules
**The One Shadow Rule.** Shadow exists to make the ledger itself feel like an object on a desk. Nothing else in the system casts one; folio cards and buttons signal state through border-color and background shifts instead.

## Shapes

Corners are small and consistent: `2px` on inputs/badges, `3px` on buttons and folio cards, `6px` on the hero ledger and its two pages (asymmetric — only the outer edges round; the spine stays square). No pill shapes except the quick-city tabs, which are the one deliberately soft, tactile shape in the system (a paper chip, not a button).

## Components

Every control reads as a desk object being used — stamped, ruled, or brass-fitted — never a flat, generic web control.

### Buttons
- **Shape:** 3px radius, no shadow.
- **Primary (`brass-btn`):** filled Warm Brass background, ink-dark text, Courier Prime uppercase label, no border. Used for every functional call to action (search, register, view bookings).
- **Status stamp (`ink-stamp`):** transparent background, 2px oxblood border, oxblood text, slight rotation (−3° to 2°, varied per instance) to read as a hand-applied stamp. Used only for the three process states, never as a clickable control.
- **Hover/Focus:** primary buttons brighten to `brass-bright`; text inputs get a 2px brass outline on `:focus-visible`.

### Chips
- **Quick-city tabs:** paper-colored pill, 1px `paper-line` border, sits inside the ledger's right page; hover shifts border to `brass-dim` and background to full `paper`.

### Cards / Containers
- **Folio card (five-star listing):** `ink-raised` background, 1px `border` (umber), 6px radius, image or ledger-line "blank" fallback with a brass-toned initial when no photo exists. Hover lifts 2px and brightens the border to `brass-dim` — no shadow.

### Inputs / Fields
- **Ledger search line:** borderless input sitting on a single bottom rule (`paper-ink-soft`), Spectral italic, no visible box — the ruled ledger line is the input chrome.

### Navigation
- **Style:** dark cover-tone bar, brass wordmark in italic Spectral, tabs in uppercase Courier Prime with a brass underline on hover/active. The register/CTA link is the one nav item filled solid brass.

### Ink Stamp (signature component)
A rotated, bordered oxblood label reserved exclusively for confirmed status (search executed, room reserved, booking confirmed). Its rarity is what makes it legible as "something happened" rather than decoration.

## Do's and Don'ts

### Do:
- **Do** keep the page background dark (leather/wood tones); ledger paper is a bounded material inside "page" panels only.
- **Do** use Warm Brass for every functional primary action.
- **Do** use Courier Prime, uppercase and tracked, for every label, nav item, and button.
- **Do** keep the hero ledger's asymmetric outer-corner rounding (6px) with a square center spine.

### Don't:
- **Don't** use oxblood/stamp red for a clickable control, link, or decoration — it is reserved for the three named status stamps.
- **Don't** let ledger paper (`--paper`) become a full-page or full-section background; it belongs only inside a bounded page panel.
- **Don't** add a card shadow anywhere except the single hero-object shadow.
- **Don't** introduce a third font family; every new UI need is solved inside Spectral/Courier Prime.
