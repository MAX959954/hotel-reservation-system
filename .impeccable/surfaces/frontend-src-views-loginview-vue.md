---
version: 1
slug: "frontend-src-views-loginview-vue"
primary_target: "frontend/src/views/LoginView.vue"
related_targets: ["frontend/src/views/RegisterView.vue"]
---

## Scope & Mode

Login and register pages. **Operate** — the visitor's job is to complete a short task (authenticate / create an account) as fast as possible; expression must never slow that down.

## Audience, job, action, proof, constraints

Returning or new travelers who need to sign in or create an account before booking. Action: submit email/password (login) or name/email/password/phone (register). No proof/content needed beyond the form itself — this is a pure utility step, not a persuasion moment. Constraint: must stay inside the existing Folio "Guest Register" world (dark leather ground, brass accent, oxblood reserved for status/error, Spectral + Courier Prime only) — confirmed with the user rather than introducing a separate light/minimal aesthetic.

## Chosen direction & memorable moment

Both pages had zero visual implementation (bare browser-default forms) before this pass. Rather than the generic centered-card auth pattern (the category default), the surface uses an asymmetric composition: a slim vertical brass "spine" (literal book-spine motif, `writing-mode: vertical-rl`) replaces the boxed card, and the form itself is bare ruled-lines directly on the dark ground — reusing the exact `.lookup-line` input pattern the homepage hero already established, rather than inventing a new input chrome. This reads as "minimal" (no box, generous whitespace, one accent color) while staying legibly Folio.

Folio numbering carries through: hero pages are "Folio I/II," login is "Folio III," register is "Folio IV" — the whole site is one continuously paginated register.

Register's five fields pair first/last name on one row to avoid vertical sprawl; everything else matches login's grammar exactly (same spine, same bare ruled-line fields, same `brass-btn` submit, same oxblood error text).

Mobile (<720px): the spine collapses from a vertical rule to a horizontal one at the top, wordmark un-rotated — matching the hero's existing pattern of dropping structural elements rather than shrinking them.

Concept-seed: `--scope surface --mode operate`, key `402529f2`, assigned index 6 (own grounded list, not a challenger). The dealt staging challenger ("rehearsed command preview") was rejected on product-truth grounds — auth is a binary server-side check with no local state to preview, so a fake live-preview would be theater, not function.

## Unresolved decisions

None outstanding. Backend `/api/auth/me` gap (noted in PRODUCT.md) is orthogonal to this surface's visual work.
