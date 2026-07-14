# CacaNode mobile

The CacaNode mobile application is an Expo/React Native client for authenticated employee workflows
on iOS and Android. The MVP covers authentication, dashboard summaries, employee chat, documents,
customer conversations, and tickets. The Next.js application in `frontend/` remains the web client;
Expo web is not an MVP target.

Phases 1–3 provide the Expo SDK 57 application foundation and native authentication: CacaNode
theming, stable Router Tabs, Redux Toolkit/RTK Query, typed public environment configuration,
normalized API errors, SecureStore-backed refresh tokens, protected routes, email-code 2FA, and
local-first logout. Follow the ordered roadmap in
[`MOBILE_MVP.md`](../MOBILE_MVP.md), and read [`AGENTS.md`](./AGENTS.md) before contributing.

## Prerequisites

- macOS with Xcode for the iOS Simulator, or Android Studio for the Android Emulator.
- A React Native-supported Node.js release. Use Node 20.19.4 or newer as the project baseline and
  stay on a supported LTS line for Expo SDK 57/React Native 0.86.
- npm, included with Node.js.
- Expo Go on a physical device for starter development, or a development build once a feature
  requires native configuration that Expo Go does not include.

The minimum application targets are iOS 16.4 and Android API 24.

## Repository layout

```text
api/                    Spring API: auth, tenants, documents, dashboard, users, tickets
frontend/               Next.js web client
mobile/                 Expo iOS and Android client
rag-chatbot-fastapi/    FastAPI AI/chat and retrieval service
```

Mobile routes live in `src/app`, feature behavior in `src/features`, shared UI in `src/components`,
API infrastructure in `src/services`, and Redux setup in `src/store`.

## Install and configure

From the repository root:

```bash
cd mobile
npm install
cp .env.example .env.local
```

`.env.local` is ignored by Git. The example contains public service URLs only. Change each hostname
for the simulator, emulator, or physical device you are running; never put credentials or tokens in
an `EXPO_PUBLIC_*` variable.

## Start Expo

Start the development server from `mobile/`:

```bash
npm start
```

The Expo terminal UI can open a simulator/emulator or display a QR code. You can also start a target
directly:

```bash
npm run ios
npm run android
```

For iOS, install Xcode and create or boot an iOS 16.4-or-later Simulator. For Android, install
Android Studio, create an API 24-or-later Android Virtual Device, and boot it before running the
Android command.

For a physical device on the same network, install Expo Go and run:

```bash
npx expo start --lan
```

Scan the QR code and use LAN-reachable API hostnames. If LAN discovery is unavailable, start Expo
with `npx expo start --tunnel`; the API services still need their own device-reachable LAN or HTTPS
tunnel URLs.

## API addresses by target

The default [`.env.example`](./.env.example) values work when the iOS Simulator can reach services
on the development Mac through `localhost`.

| Target | API hostname to use | Example Spring URL |
| --- | --- | --- |
| iOS Simulator | `localhost` | `http://localhost:8080/api/v1` |
| Android Emulator | `10.0.2.2` | `http://10.0.2.2:8080/api/v1` |
| Physical device | Mac LAN IP or HTTPS tunnel host | `http://192.168.1.20:8080/api/v1` |

Apply the same hostname rule to the AI API on port 8000. A physical phone cannot use `localhost` to
reach services running on the development computer. Preview and production builds must use HTTPS.

## Verification

Run the current mobile checks from `mobile/`:

```bash
npm run lint
npm run typecheck
npm test
npx expo install --check
npx expo config --type public
```

Native bundle checks require public URL values. After copying `.env.example` to `.env.local`, run:

```bash
npx expo export --platform ios
npx expo export --platform android
```

## Native authentication notes

- Mobile login sends a six-digit confirmation code by email. The code expires after 10 minutes and
  is replaced by a new login or resend request.
- Browser login continues to use the existing emailed verification link.
- The refresh token is stored only in Expo SecureStore. The access token remains in memory and is
  lost when the process closes; app bootstrap rotates the stored refresh token to restore it.
- If bootstrap cannot reach the API, the app keeps the refresh token and presents a retry action.
- Logout clears the local session before making a best-effort server revocation request, so it works
  even while offline.
