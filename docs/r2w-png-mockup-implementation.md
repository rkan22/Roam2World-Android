# Roam2World PNG Mockup Implementation

The current UI implementation should use the uploaded 19-screen PNG mockup set as the source of truth.

## Source screens

1. Dashboard
2. Store
3. Package Detail
4. Customer Information
5. Purchase Confirmation
6. Purchase Success
7. eSIMs
8. eSIM Detail
9. OpenEUICC
10. OpenEUICC Install
11. OpenEUICC Profile
12. eSIM History
13. eSIM History Detail
14. Wallet
15. TGT SIM Recharge
16. Orders
17. Reports
18. More Menu
19. Vodafone Recharge

## Implementation order

1. Dashboard
2. eSIMs
3. Store
4. Package Detail
5. Purchase flow
6. OpenEUICC
7. Wallet
8. Recharge
9. Reports and More

## First pass in this branch

This branch starts by replacing the previous generic dashboard with a mockup-matched B2B dashboard:

- Roam2World + B2B header
- Welcome/admin greeting
- Admin/Reseller/Dealer role tabs
- Blue wallet balance hero
- Four metric cards
- Recent purchases card
- Quick actions panel
- Bottom navigation and floating action button

The next pass should extract common components for the eSIMs and Store screens instead of duplicating layout code.
