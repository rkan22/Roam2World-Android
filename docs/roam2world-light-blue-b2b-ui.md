# Roam2World Light Blue B2B UI

Figma source: https://www.figma.com/design/O6b2FSjsOSbsHLwcATYkIF

This design pass converts the product direction from a generic B2C travel eSIM style into a light, blue, B2B telecom administration interface.

## Design tokens

| Token | Hex | Usage |
|---|---:|---|
| Primary | `#006BFF` | Primary actions, selected nav, hero cards |
| Primary Dark | `#0047B3` | Blue gradients and emphasis |
| Sky | `#EAF4FF` | Quick action tiles and soft fills |
| Background | `#F6FAFF` | App background |
| Text | `#07152F` | Main text |
| Muted | `#667085` | Secondary text |
| Border | `#D9E6F7` | Card/input outlines |
| Success | `#12B76A` | Positive status and credit transactions |
| Danger | `#F04438` | Failed status and debit transactions |

## Target screens

1. Splash
2. Login
3. Dashboard
4. Wallet
5. Recharge
6. Check GB
7. eSIM Store
8. Package Detail
9. Purchase Success
10. My eSIMs
11. eSIM Detail
12. OpenEUICC QR Scan
13. Manual LPA Code
14. eUICC Profiles
15. eUICC Profile Detail
16. Dealers / Customers
17. Reports
18. Transactions
19. Settings

## Implementation notes

The Compose layer should not copy one-off colors inside each Activity. New screens should use:

- `R2WColors`
- `R2WCard`
- `R2WPrimaryButton`
- `R2WSecondaryButton`
- `R2WWalletBalanceCard`
- `R2WQuickActionGrid`
- `R2WProgressRow`
- `R2WStatusBadge`

This makes the Figma system and Android implementation stay aligned.
