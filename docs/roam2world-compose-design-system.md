# Roam2World B2B Compose Design System

This document defines the target design system and migration plan for rebuilding Roam2World B2B with a premium Android UI closer to the high-fidelity SaaS/fintech reference direction.

## Goal

Move away from incremental XML polish and establish a real Figma/Jetpack Compose design system before rebuilding the main product screens.

Target experience:

- Premium B2B SaaS dashboard
- Telecom/eSIM enterprise platform
- Admin, reseller and dealer workflows
- Stripe/Revolut Business/Cloudflare/Shopify Admin style quality
- Consistent screen system across Dashboard, Store, Wallet, eSIMs, More, Reports and OpenEUICC

## Current State

The current app is functional and has improved XML screens, but the UI is still based on mixed XML layouts and local styling.

Limitations:

- Screen polish is not centralized
- No single source of truth for spacing, radius and typography
- Hard to match high-fidelity reference visuals exactly
- UI components vary across screens
- Store falls back to local demo catalog when the backend returns an empty or unsupported package response

## Target Stack

Use Jetpack Compose for new Roam2World screens.

Recommended migration strategy:

1. Keep existing XML screens running.
2. Enable Compose in Gradle in a separate PR.
3. Add a reusable Compose design system package.
4. Rebuild one screen at a time.
5. Start with Dashboard because it defines the visual identity.
6. Move Store after backend package payload is confirmed.

## Design Tokens

### Colors

| Token | Value | Usage |
| --- | --- | --- |
| Primary | `#0A66FF` | Main CTA, active states, hero gradients |
| Primary Dark | `#0047CC` | Gradient end, strong CTA states |
| Secondary | `#1E88E5` | Supporting blue accents |
| Success | `#16A34A` | Success states, active eSIM, completed orders |
| Warning | `#F59E0B` | Pending states, expiring soon |
| Danger | `#DC2626` | Failed orders, logout, destructive actions |
| Background | `#F5F8FC` | App background |
| Card | `#FFFFFF` | Cards and surfaces |
| Border | `#E2E8F0` | Card borders and dividers |
| Text Primary | `#0F172A` | Main text |
| Text Secondary | `#64748B` | Secondary text |

### Shape

| Token | Value |
| --- | --- |
| Small radius | `12dp` |
| Medium radius | `16dp` |
| Large radius | `22dp` |
| Hero radius | `28dp` |
| Bottom sheet radius | `28dp` |

### Spacing

Use an 8dp grid.

| Token | Value |
| --- | --- |
| xxs | `4dp` |
| xs | `8dp` |
| sm | `12dp` |
| md | `16dp` |
| lg | `24dp` |
| xl | `32dp` |
| xxl | `40dp` |

### Elevation

| Token | Usage |
| --- | --- |
| 0dp | Flat backgrounds |
| 1dp | Subtle list cards |
| 3dp | Standard cards |
| 6dp | Hero cards |
| 10dp | Floating bottom nav / modal surfaces |

## Core Compose Components

### R2WTheme

Single theme wrapper for all Roam2World Compose screens.

Responsibilities:

- Material 3 color scheme
- Typography
- Shapes
- System bar color strategy
- Light mode first

### R2WHeroCard

For Dashboard and Wallet top sections.

Required properties:

- Blue gradient background
- Large rounded corners
- Main metric text
- Supporting subtitle
- Optional CTA row
- Optional top-right status badge

### R2WStatCard

For compact metrics.

Required properties:

- Title
- Value
- Optional trend label
- Optional icon
- Optional semantic color

### R2WActionCard

For quick actions and More menu.

Required properties:

- Icon
- Title
- Subtitle
- Optional badge
- Click action

### R2WPackageCard

For Store.

Required properties:

- Provider badge
- Plan name
- Data amount badge
- Validity badge
- Coverage
- Price
- Buy Now CTA

### R2WStatusBadge

For statuses.

Variants:

- Active
- Pending
- Completed
- Failed
- Expiring
- Demo

### R2WSectionHeader

For screen sections.

Required properties:

- Title
- Optional subtitle
- Optional action text

## Screen Rebuild Order

### Phase 1: Foundation

- Add Compose Gradle support
- Add design system package
- Add preview/demo screen if safe
- Keep XML screens untouched

### Phase 2: Dashboard Compose

Target:

- Premium gradient wallet card
- User/company/role header
- Notification button
- 2x2 or horizontal stat cards
- Quick action grid
- Recent activity cards
- Recent orders/activations cards

Acceptance criteria:

- Looks like a premium SaaS mobile dashboard
- No demo labels in production UI
- Uses live dashboard/wallet/order data where available
- Falls back to empty states, not fake data, unless explicitly in demo mode

### Phase 3: Store Compose

Target:

- Real API first
- Provider chips
- Search and filter chips
- Premium package cards
- No automatic demo catalog unless demo mode is explicitly enabled

Acceptance criteria:

- Backend products are shown when available
- Empty API response shows a clear empty state
- Demo packages do not appear as real inventory

### Phase 4: Wallet Compose

Target:

- Balance hero card
- Summary cards
- Wallet request CTA
- Transaction list cards
- Balance history empty/loading/error states

### Phase 5: More Compose

Target:

- Workspace/account hero
- Two-column module grid
- Role-aware modules
- Logout danger action

## Backend Catalog Requirement

Store should not depend on local demo catalog for production-like testing.

The backend endpoint `GET /api/v1/mobile/packages/` should return a real package catalog in a stable shape.

Recommended payload shape:

```json
{
  "packages": [
    {
      "id": "orange-europe-50gb-30d",
      "provider": "Orange Europe",
      "package_type": "esim",
      "name": "Orange Europe 50GB",
      "country": "Europe",
      "country_code": "EU",
      "data_amount": "50GB",
      "validity": "30 days",
      "coverage": "Europe",
      "network": "Orange",
      "base_price": "42.00",
      "reseller_price": "40.00",
      "dealer_price": "38.00",
      "description": "50GB Europe eSIM package valid for 30 days",
      "visible_to_reseller": true,
      "visible_to_dealer": true,
      "featured": true
    }
  ],
  "featured_packages": []
}
```

If the backend returns a different shape, the Android parser should be updated once and covered by sample payload tests.

## Demo Data Policy

Demo data must be explicit.

Allowed:

- Debug build demo mode
- Figma mockups
- Preview-only Compose data
- Local UI previews

Not allowed:

- Showing demo packages as real Store inventory in normal production-like app state
- Posting demo package IDs to backend order APIs
- Mixing fake and real packages without clear labeling

## First Implementation PRs

1. `chore: enable Compose foundation`
2. `feat: add Roam2World Compose design system`
3. `feat: add Compose Dashboard preview screen`
4. `feat: migrate Dashboard to Compose premium UI`
5. `fix: require real backend package catalog in Store`

## Success Definition

The app reaches the reference visual level when:

- Dashboard visually matches a premium fintech/SaaS mobile dashboard
- Store uses real backend inventory and premium cards
- Wallet and More share the same design language
- No screen looks like a raw Android form/list screen
- Empty/loading/error states are designed, not accidental
- Demo data is never confused with production data
