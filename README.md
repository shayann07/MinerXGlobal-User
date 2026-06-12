# MinerXGlobal-User

Kotlin Android user-side client for the **Miner X Global** investment / multi-level-referral platform. Users sign up with email + password (verified by email), are auto-activated when their cumulative plan investment crosses a threshold, buy investment plans that pay daily ROI plus a direct-referral bonus to their upline, see their team levels, lucky-draw rewards, salary/rank, transactions, and chat with admins. Firebase Remote Config drives a sideloaded-APK self-update flow.

## ⚠ Security Notice — Read First

**This repository contains committed secrets and patterns that must not be used as-is.** Read this section before cloning, running, or forking.

- `app/src/main/java/com/minerxgloble/minerxgloble/fcm/AccessToken.kt` ships a **full Firebase service-account JSON in source**, including the PEM private key for `REDACTED_CLIENT_EMAIL` (project `minerxgloble`, `private_key_id` `REDACTED_KEY_ID`). The class loads it via `GoogleCredentials.fromStream` and mints OAuth2 access tokens for the FCM HTTP v1 endpoint. The key has admin-level access to the Firebase project — treat it as compromised, **rotate at IAM, and remove it from git history**.
- `fcm/Fcm.kt` calls `https://fcm.googleapis.com/v1/projects/minerxgloble/messages:send` directly from the client using that service-account token. Server-key push sending must move to a Cloud Function (or other backend); clients must never ship admin credentials.
- `repos/AuthRepository.kt` writes the user's **plaintext password** into `users/{authUid}.password` on registration and re-`update`s it on every successful login (commented `// remove / hash in production`). The same password is cached in `SharedPreferences` under the key `password`. Stop persisting passwords; rely on Firebase Auth alone.
- `MainActivity.onCreate` installs `DebugAppCheckProviderFactory` unconditionally. Release builds should use `PlayIntegrityAppCheckProviderFactory` (the `firebase-appcheck-playintegrity` dependency is already declared).

`google-services.json` and signing keystores are gitignored, so the Firebase config and signing material are not in the repo, but the Google Services Gradle plugin is still applied — the build needs `app/google-services.json` placed locally.

## Status

Functional production build of v10 (`versionCode 10`, `versionName "10.0"`). 27 Navigation Component fragments across 122 Kotlin source files. Working tree clean on `master`. The previous repo had no `README.md`; this file was generated from a code audit.

## How It Works

### App startup and navigation
- `App.kt` is a tiny `Application` that forces `AppCompatDelegate.MODE_NIGHT_YES`. There is no Hilt / no DI graph — repositories and view-model factories are constructor-wired manually inside `MainActivity.onCreate`.
- `ui/MainActivity` is the only activity. It hosts a `NavHostFragment` (graph: `R.navigation.nav_graph`), a Material `BottomNavigationView` (`Home / Wallet / Plans / Team / Profile`), a `FAB` routing to `stackFragment`, and a `DrawerLayout` with a custom row for each menu id (Home, Profile, Rank, Investment Wallet, Earnings Wallet, Team, Salary, Transactions, Lucky Draw, Support, FAQs, Logout).
- `App Check` is initialized with the **debug** provider factory (`DebugAppCheckProviderFactory`). A 1.5 s "auth grace" window after `onStart` lets Firebase restore the session before any "session expired" guard fires.
- A Firestore `addSnapshotListener` on `users/{authUid}` (`attachUserDocGuard`) signs the user out if the document disappears or gets `isBlocked = true`. `onResume` runs `hardVerifyAuth()` which `reload()`s `currentUser` and signs out on `ERROR_USER_NOT_FOUND` / `ERROR_USER_DISABLED`.
- Deep link: `http(s)://minerxglobal.com/` with `android:autoVerify="true"`. A `?ref=…` query parameter is captured into `PrefService.saveReferralFromLink`.
- Drawer rows for **WhatsApp** (`https://whatsapp.com/channel/0029VbB0BZI3mFY6qBqSmw12`) and **Telegram** (`https://t.me/minerxglobalofficial`) try the native app first and fall back to a browser intent.

### Authentication and accounts (`repos/AuthRepository.kt`)
- Custom user id format: `MXG-` + 6 random characters from the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. Uniqueness is enforced via a `uidReservations` Firestore collection inside a transaction (creates a doc with the candidate id; conflicts retry).
- **Register:** duplicate-email guard on `users.email` → `auth.createUserWithEmailAndPassword` → atomic `runTransaction` that writes `users/{authUid}` and a fresh `accounts/{accountId}` (with embedded `InvestmentModel` and `EarningsModel`) → `sendEmailVerification` → cache essentials to `PrefService` → best-effort `users.deviceToken` write from `FirebaseMessaging.getInstance().token`. Any failure rolls back the Auth user and both Firestore docs.
- **Login:** returns `LoginResult.SUCCESS / UNVERIFIED_EMAIL / FAILURE`. Unverified accounts are sent a fresh verification email and signed back out. Successful login caches the user document into prefs, downloads `profile_pics/$mxgUid.jpg` from Firebase Storage, and refreshes the FCM device token.
- `fetchProfileSmart` resolves user docs in three fallbacks: `users/{authUid}` (cache, then server) → legacy `users/{MXG-…}` doc id → `whereEqualTo("uid", MXG)` query.
- Announcement images come from a Firestore `announcement_images` collection, each doc holding an `imageUrl` field.

### Plan purchases and referral payout (`repos/BuyPlanRepo.buyPlan`)
- Reads `plans/*` (fields: `minAmount`, `maxAmount`, `dailyPercentage`, `directProfit`, `totalPayout`, `planName`), filters to plans whose range brackets the buyer's amount, picks the one with the highest `minAmount` as the auto-selected tier.
- In a Firestore transaction:
  - Validates `accounts.investment.currentBalance` and `remainingBalance` ≥ amount; deducts both and increments `investment.totalInvestedInPlans`.
  - First plan ever (when `prevTotalInPlans == 0.0`) → adds **50 MXGN tokens** to `accounts.earnings.tokens`.
  - Activates the user (`users.status = "active"`) when cumulative plan investment crosses **`>= 10.0`** (note: a code comment says "≥ 50" — code wins).
  - Direct-referral bonus = `amount * directPct / 100`, paid to the upline's `earnings.referralProfit / totalEarned / totalEarnedToDate`, **only if** the buyer's `users.directProfitBlock != true` AND the upline is `status == "active"`.
  - Either creates a new `userPlans/{id}` doc or upserts the existing active one for the same `userId`/`planName` (`buyDate`, `roiAmount`, `totalPayoutAmount`, `referrerId`, `referralReceivedDirectProfit`, `principal` etc.).
  - Writes audit rows to `transactions` (`Plan Purchase` for the buyer, `Direct Profit` for the upline) and `userPlanChangeLogs` (`NEW` / `TOP_UP` with before/after maps and an 8-char trace id).
- Post-transaction the repo fetches an FCM access token via `AccessToken.getAccessTokenAsync` (the embedded service account) and sends two push notifications: `"Congratulations! You received 50 free MXGN tokens…"` to the buyer on first plan, and `"Referral bonus received — You earned …"` to the upline.

### Chat with admin (`repos/chat/ChatRepository`)
- Admin list comes from a top-level `Admin` Firestore collection (id, name).
- Messages live in a flat `chats` collection: `senderId`, `receiverId`, `message`, `status`, `sender` (`"1"` = user→admin), `createdAt`. `getChats` attaches **two** snapshot listeners (user→admin and admin→user) and merges by document id.
- `ChatPreview` groups every chat doc by the "other party" id and joins admin display name from the `Admin` collection.

### In-app self-update (`utils/RemoteUpdateManager`)
- Reads Firebase Remote Config keys: `latest_version_code`, `apk_download_url`, `apk_sha256`, `update_message`. If `apk_sha256` is blank the update is aborted.
- If installed `versionCode < latest_version_code`, shows a non-cancellable `MaterialAlertDialog` (`DialogUpdateBinding`, `XLogoLoadingView`).
- "Update Now" enqueues an Android `DownloadManager` request for `update.apk` into `Environment.DIRECTORY_DOWNLOADS`, polls progress every 400 ms, and drives the X-logo progress indicator.
- After **20 cancelled installs** (`MAX_RETRIES`) the app blocks itself behind an "Exit" dialog. `utils/UpdateDownloadReceiver` (registered for `DOWNLOAD_COMPLETE` in the manifest) and `utils/UpdateInstaller` finish the install handoff.

## Project Structure

```
MinerXGlobal-User/
├── app/
│   ├── build.gradle.kts                 # compileSdk 35, minSdk 24, applicationId com.minerxgloble.minerxgloble
│   └── src/main/
│       ├── AndroidManifest.xml          # MainActivity launcher + minerxglobal.com deep link
│       ├── java/com/minerxgloble/minerxgloble/
│       │   ├── App.kt                   # Application: forces dark mode
│       │   ├── adapters/                # RecyclerView adapters (incl. adapters/chat/)
│       │   ├── fcm/
│       │   │   ├── AccessToken.kt       # ⚠ embeds Firebase service-account JSON
│       │   │   ├── Fcm.kt               # POSTs to fcm.googleapis.com/v1/projects/minerxgloble/messages:send
│       │   │   └── NotificationService.kt
│       │   ├── models/                  # User, Account, UserPlan, EarningsModel, InvestmentModel, …
│       │   │   └── chat/                # ChatPreview (other chat models live in trustledger.* package)
│       │   ├── repos/                   # Auth, BuyPlan, Wallet, Transaction, Salary, Rank, TeamLevel, NetworkStats, LuckyDraw
│       │   │   └── chat/                # ChatRepository, ChatViewModelFactory
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── animation/           # X-logo loaders, gradient cards, intro overlays
│       │   │   └── fragments/           # 22 fragments + chat/ subpackage
│       │   ├── utils/                   # PrefService, RemoteUpdateManager, UpdateDownloadReceiver, UpdateInstaller, dialog helpers
│       │   └── viewModels/              # 12 ViewModels + viewModels/factory/
│       └── res/navigation/nav_graph.xml # single Nav graph
└── build.gradle.kts                     # root Gradle
```

## Tech Stack

- **Language / build:** Kotlin, Android Gradle Plugin via `libs.versions.toml`, JVM 11.
- **App config:** `applicationId = com.minerxgloble.minerxgloble`, `compileSdk = 35`, `minSdk = 24`, `targetSdk = 35`, `versionCode = 10`, `versionName = "10.0"`. View Binding + `buildConfig` enabled.
- **UI:** Single activity, Navigation Component (`androidx.navigation.fragment.ktx`, `ui.ktx`, `compose`, `dynamic-features-fragment`) + safeargs Gradle plugin, Material 3, ConstraintLayout / GridLayout / CoordinatorLayout, Lottie raws (`confetti`, `money_rain`, `sent_animation`, `wheel_border`).
- **Firebase:** BoM, Firestore (+ ktx), Auth, Storage, Functions ktx, Messaging ktx, Remote Config ktx, App Check (debug + Play Integrity).
- **Networking / serialization:** OkHttp, gRPC OkHttp, Picasso, Glide, Volley, Moshi + Kotlin codegen, Gson, `google-auth-library-oauth2-http` (used by `AccessToken`).
- **Concurrency / state:** `kotlinx-coroutines-android` with `tasks.await()`, AndroidX `lifecycle-viewmodel-ktx` + `livedata-ktx`, manual DI.
- **UI extras:** CircleImageView, UltraPullToRefresh, TapTargetView, MaterialShowcaseView, uCrop (`com.yalantis.ucrop.UCropActivity` declared in manifest), PhotoView, Shimmer, `luckywheel-android` (powers `LuckyDrawFragment`).
- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `REQUEST_INSTALL_PACKAGES`, `WRITE_EXTERNAL_STORAGE` (≤28), `READ_EXTERNAL_STORAGE` (≤32), `POST_NOTIFICATIONS`, `com.google.android.c2dm.permission.RECEIVE`.

## Build / Run

1. Clone, then place a real `app/google-services.json` for the Firebase project the build is targeting (the file is gitignored).
2. Open in Android Studio (Hedgehog or later) and let Gradle sync.
3. `./gradlew :app:assembleDebug` (or run from Android Studio).

The app expects a populated Firebase backend (Auth users, `plans`, `users`, `accounts`, `userPlans`, `transactions`, `userPlanChangeLogs`, `Admin`, `chats`, `announcement_images`, `uidReservations` collections) and Remote Config keys `latest_version_code`, `apk_download_url`, `apk_sha256`, `update_message`. None of that backend code is in this repository.

## Honest Limitations

- **No backend code in repo.** All business logic that runs server-side (daily ROI scheduling, withdrawal approval, salary/rank evaluation, FCM auth) lives in a separate MinerX backend. Some of that work is simulated by the client doing it directly — see the `BuyPlanRepo` transaction.
- **Sending FCM pushes from the client.** The current design requires the embedded service-account key precisely because pushes are sent client-side. Until that moves to a Cloud Function, the key cannot be safely shipped.
- **`DebugAppCheckProviderFactory` is wired unconditionally.** Production-grade abuse protection is effectively disabled until this is swapped per-build-type.
- **Cross-package leftovers.** `repos/chat/ChatRepository.kt` imports `com.trustledger.aitrustledger.models.chat.Admin` and `Message` — the chat model classes live under a `trustledger.aitrustledger` package inside this app's source set, a remnant of the AI Trust Ledger project.
- **Activation threshold mismatch.** `BuyPlanRepo.buyPlan` uses `>= 10.0` to flip `users.status = "active"`, but a code comment says "≥ 50". Pick one source of truth.
- **No `LICENSE` file.** Treat the source as **all rights reserved by the author** until a license is added.
- **No tests** beyond the default JUnit / Espresso instrumentation harness in `build.gradle.kts`.
