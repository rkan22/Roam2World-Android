# Roam2World B2B Android App

A fully-featured B2B telecom management application built on top of [OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC), providing a complete dashboard for Admins, Resellers and Dealers to manage eSIM orders, wallet balances, TGT SIM recharges, and OpenEUICC eSIM profile installation.

---

## Features

| Module | Description | Roles |
|:---|:---|:---|
| **Dashboard** | Balance overview, active eSIM count, recent orders, quick actions | All |
| **eSIM Store** | Browse & purchase eSIM packages by country, filter & search | All |
| **Wallet** | Balance, transaction history, wallet requests (top-up) | All |
| **My eSIMs** | List of purchased eSIMs, detail view, QR code, install via OpenEUICC | All |
| **Purchase History** | Full order history with status tracking | All |
| **Reports** | Sales analytics and reporting | Admin, Reseller |
| **My Dealers** | Dealer management, balance allocation, suspend/activate | Reseller |
| **TGT SIM Recharge** | Top up physical TGT SIM cards | All |
| **OpenEUICC Integration** | Direct eSIM profile installation via OpenEUICC | All |
| **Profile** | Account info, role, permissions | All |
| **Settings** | API URL configuration, app preferences | All |
| **Support** | Helpdesk & FAQ | All |

---

## Architecture

The app follows a standard Android MVC/MVVM hybrid pattern:

```
app-common/
  src/main/java/im/angry/openeuicc/
    auth/           # API client, data models, token store, JWT utilities
    ui/             # All Activity classes (Dashboard, Wallet, Packages, eSIMs, etc.)
    util/           # Inset helpers, status chip utilities
  src/main/res/
    layout/         # XML layouts for all screens
    drawable/       # Vector icons and gradient backgrounds
    menu/           # Navigation menus
    values/         # Colors, strings, themes
```

### Key Classes

| Class | Purpose |
|:---|:---|
| `Roam2WorldAuthApi` | REST API client for all B2B endpoints |
| `AuthTokenStore` | Encrypted session persistence (Android Keystore) |
| `AuthSession` | Authenticated user session model |
| `JwtUtils` | JWT expiry checking |
| `DashboardActivity` | Main hub with bottom navigation |
| `PackagesActivity` | eSIM store with search/filter |
| `WalletActivity` | Balance and transaction management |
| `MobileEsimsActivity` | eSIM list and management |
| `MobileEsimDetailActivity` | eSIM detail, QR code, install |
| `MyDealersActivity` | Dealer management (Reseller only) |
| `ReportsActivity` | Analytics and reporting |
| `TgtSimRechargeActivity` | TGT SIM recharge flow |
| `OpenEuiccIntegrationActivity` | OpenEUICC profile installation |

---

## API Endpoints

The app communicates with the Roam2World B2B backend:

| Endpoint | Description |
|:---|:---|
| `POST api/v1/mobile/auth/login/` | Login |
| `POST api/v1/auth/refresh/` | Token refresh |
| `POST api/v1/auth/logout/` | Logout |
| `GET api/v1/mobile/dashboard/` | Dashboard stats |
| `GET api/v1/mobile/wallet/` | Wallet balance |
| `GET api/v1/mobile/transactions/` | Transaction history |
| `GET/POST api/v1/mobile/wallet/requests/` | Wallet top-up requests |
| `GET api/v1/mobile/packages/` | eSIM package catalog |
| `GET api/v1/mobile/packages/featured/` | Featured packages |
| `GET api/v1/mobile/orders/` | Order history |
| `GET api/v1/mobile/esims/` | Purchased eSIMs |
| `GET api/v1/mobile/dealers/` | Dealer list (Reseller) |
| `POST api/v1/mobile/dealers/{id}/allocate-balance/` | Allocate dealer balance |
| `POST api/v1/mobile/dealers/{id}/suspend/` | Suspend dealer |
| `POST api/v1/mobile/dealers/{id}/activate/` | Activate dealer |

The default API base URL is `https://roam2world-panels-backend.onrender.com`. This can be changed in **Settings → API URL**.

---

## Building

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 34+

### Setup

```shell
git clone https://github.com/rkan22/Roam2World-Android.git
cd Roam2World-Android
git submodule update --init
```

Create `keystore.properties` in the root directory:

```ini
storePassword=my-store-password
keyPassword=my-password
keyAlias=my-key
unprivKeyPassword=my-unpriv-password
unprivKeyAlias=my-unpriv-key
storeFile=/path/to/android/keystore
```

### Build

**Privileged (OpenEUICC) variant:**
```shell
./gradlew :app:assembleDebug
```

**Unprivileged (EasyEUICC) variant:**
```shell
./gradlew :app-unpriv:assembleDebug
```

---

## User Roles

| Role | Access |
|:---|:---|
| **Admin** | All modules including dealer management, reports, wallet |
| **Reseller** | Dealer management, wallet, reports, eSIM store, TGT recharge |
| **Dealer** | eSIM store, wallet, orders, TGT recharge |

Role-based visibility is enforced both in the UI and at the API level.

---

## Security

- Session tokens are stored encrypted using **Android Keystore** (AES-256-GCM)
- JWT expiry is checked before every API call; expired tokens trigger automatic refresh
- All API communication uses HTTPS

---

## License

This project is based on [OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC), licensed under **GNU GPL v3**.

The Roam2World B2B extensions are also released under **GNU GPL v3**.
