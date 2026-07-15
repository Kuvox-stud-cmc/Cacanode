# CacaNode Mobile MVP

This document is the implementation sequence for the Expo mobile application in `mobile/`.
Complete the phases in order. Do not begin a later phase until the current phase's exit criteria
are satisfied.

## 1. Product goal

Build a production-ready iOS and Android application for authenticated CacaNode employees. The
mobile application should prioritize the workflows that are useful away from a desktop:

1. Sign in, complete login 2FA, restore a session, and sign out.
2. View the tenant dashboard and usage summary.
3. Ask questions in the employee chat playground and inspect citations.
4. Browse, upload, inspect, download, and manage documents according to role.
5. Review customer conversations and their message history.
6. Review and update support tickets.
7. Receive clear loading, empty, offline, authorization, and error states.

The first production release does not need complete web-dashboard parity. Tenant user management,
integration token management, webhooks, widget configuration, analytics detail, and the AI Prompt
editor can follow after the core mobile experience is stable.

## 2. Current repository state

- The Expo app is under `mobile/` and uses Expo Router.
- Phase 1 provides the application foundation, placeholder route groups, shared UI states, Redux,
  RTK Query, typed environment configuration, and normalized API errors.
- Expo SDK: `~57.0.4`.
- React Native: `0.86.0`.
- TypeScript strict mode is enabled.
- Navigation uses stable Expo Router `Tabs`.
- The Spring API owns authentication, tenants, documents, dashboard data, users, and tickets.
- The FastAPI service owns employee chat, customer chat, retrieval, citations, and conversations.
- The web frontend contains working API-client examples that can be used to understand contracts,
  but mobile code must not import browser-specific cookie or DOM behavior.

### Platform and navigation decisions

- The MVP targets iOS and Android only.
- The minimum supported platforms follow Expo SDK 57: iOS 16.4 and Android API 24.
- Expo web is unsupported for MVP acceptance and release testing. The Next.js application remains
  the CacaNode web client, so Expo web regressions do not block a mobile phase or release.
- Stable Expo Router `Tabs` is the mobile navigation foundation.

## 3. MVP scope

### Included

- Login email/password form.
- Login 2FA verification and resend flow.
- Secure session restoration.
- Logout and refresh-token revocation.
- Role-aware authenticated navigation.
- Dashboard summary.
- Employee chat playground.
- Chat history for the current employee.
- Document list, filters, status, upload, download/share, visibility update, and deletion.
- Conversation list and conversation detail.
- Ticket list, ticket detail, status/priority/assignee updates, and internal notes.
- Pull to refresh, pagination where supported, and retry states.
- Light and dark themes.
- Accessibility basics.
- iOS and Android verification.

### Deferred until after MVP

- Registration and onboarding optimized for mobile.
- Email verification deep-link completion.
- Invitation acceptance deep-link completion.
- Team administration.
- Integration-token, webhook, and widget administration.
- Tenant AI Prompt administration.
- Detailed analytics charts.
- Customer-facing widget functionality inside the mobile app.
- Offline message sending.
- Background document uploads.
- Push notifications.
- Biometric app lock.
- Expo web support. Expo web is explicitly unsupported for MVP acceptance and release testing.

Deferred items may still receive foundational support, such as route placeholders or typed API
definitions, but should not block MVP delivery.

### Progress tracker

Update this table only after the phase exit criteria have passed.

| Phase | Deliverable | Depends on | Status |
| --- | --- | --- | --- |
| 0 | Requirements and contributor guidance | None | Complete |
| 1 | Expo application foundation | Phase 0 | Complete |
| 2 | Spring native authentication contract | Phase 0 | Complete |
| 3 | Mobile authentication foundation | Phases 1–2 | Complete |
| 4 | Authenticated navigation and shared UI | Phase 3 | Complete |
| 5 | Dashboard | Phase 4 | Complete |
| 6 | Employee chat playground | Phase 4 | Complete |
| 7 | Documents | Phase 4 | Complete |
| 8 | Customer conversations | Phase 4 | Complete |
| 9 | Tickets | Phases 4 and 8 | Complete |
| 10 | Resilience, accessibility, and security | Phases 5–9 | Not started |
| 11 | End-to-end tests and release | Phases 0–10 | Not started |

Phase 3 device verification is considered complete based on the requested assumption that deployed
iOS and Android testing passed. Phase 4 implementation, automated checks, Expo compatibility
validation, and both native exports are complete. Phase 5 dashboard implementation, tenant-cache
tests, accessibility checks, and both native exports are also complete. Phase 6 employee chat,
history ownership, citation, failure-state, cache-reset, and native export verification are complete.
Phase 7 document paging, multi-file upload, polling, native sharing, role enforcement, cache cleanup,
Spring contract tests, and both native exports are complete.
Phase 8 tenant-scoped conversation filtering, paging, detail virtualization, citations, ticket
drafts, external closing, cache refresh, FastAPI contract coverage, and both native exports are
complete.
Phase 9 full ticket filtering, stable paging, detail updates, confirmed terminal status changes,
internal notes, conversation navigation, Spring contract coverage, and both native exports are
complete.

Allowed status values are `Not started`, `In progress`, `Blocked`, and `Complete`. When a phase is
blocked, add a short blocker note directly below the table rather than skipping ahead silently.

## 4. Non-negotiable security rules

1. Never persist access or refresh tokens in AsyncStorage, SQLite, Redux persistence, logs, crash
   reports, analytics, URLs, or `EXPO_PUBLIC_*` variables.
2. Store the refresh token only in `expo-secure-store`.
3. Keep the access token in memory only.
4. Do not persist the Redux authentication slice.
5. Do not log request bodies, response bodies, tokens, document content, or chat content.
6. All production API traffic must use HTTPS.
7. Client-side JWT decoding may be used only for expiry scheduling or display. Authorization must
   always be enforced by the backend.
8. Clear secure credentials whenever refresh fails, logout succeeds, the account becomes invalid,
   or the server returns a terminal authentication error.
9. Allow only one refresh request at a time. Concurrent 401 responses must wait for the same refresh
   operation rather than rotating the refresh token multiple times.
10. Never embed Spring JWT signing keys, integration tokens, OpenAI keys, or other server secrets in
    the mobile bundle.

## 5. Authentication architecture decision

### Existing browser behavior

The backend currently returns a JWT access token in the JSON response and sends an opaque refresh
token as an HttpOnly cookie. The refresh endpoint reads that cookie and rotates the refresh token.

This is appropriate for the browser but is not the preferred contract for the native app. Native
cookie behavior differs across Expo Go, development builds, iOS, Android, and fetch
implementations. The mobile app also cannot intentionally manage the HttpOnly value.

### Required native authentication contract

Add native endpoints without removing or weakening the browser-cookie endpoints:

```text
POST /api/v1/auth/mobile/login
POST /api/v1/auth/mobile/verify-login-2fa
POST /api/v1/auth/mobile/refresh
POST /api/v1/auth/mobile/logout
```

Successful native authentication response:

```json
{
  "accessToken": "short-lived-jwt",
  "refreshToken": "opaque-random-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "userId": "uuid",
    "tenantId": "uuid",
    "email": "person@example.com",
    "fullName": "Person Name",
    "role": "TENANT_ADMIN",
    "plan": "PRO"
  }
}
```

Refresh request:

```json
{
  "refreshToken": "opaque-random-token"
}
```

Logout request:

```json
{
  "refreshToken": "opaque-random-token"
}
```

Native refresh requirements:

- Hash the submitted refresh token before repository lookup.
- Reject missing, unknown, revoked, or expired tokens.
- Verify that the user and tenant are still active.
- Delete or revoke the submitted refresh token.
- Generate and store a new refresh token.
- Return both the new refresh token and a new access token.
- Never put the refresh token in server logs or audit metadata.
- Revoke the submitted token on logout.
- Keep the existing browser refresh-cookie flow unchanged.

### Multi-device session decision

Decision: **Allow independent multi-device sessions.**

A mobile login must not invalidate a browser session or another device session. Phase 2 must change
the current token issuance behavior so each authenticated client can hold an independent refresh
token. Normal refresh rotation and logout revoke only the refresh token submitted by the current
device; they must leave other browser and device refresh tokens valid.

Account-wide security actions may revoke every refresh token for the user. These actions include
account suspension, password reset, and administrator deactivation, plus any future explicit
"sign out everywhere" operation. The existing browser cookie contract and behavior must otherwise
remain unchanged while the native token contract is added.

## 6. State and storage architecture

Use Redux Toolkit and RTK Query, but distinguish client state, server state, and secrets.

| Data | Owner | Persistent |
| --- | --- | --- |
| Access token | In-memory token manager | No |
| Refresh token | `expo-secure-store` | Yes |
| Authentication status | Redux auth slice | No |
| Current user profile | Redux auth slice | No for MVP |
| API response cache | RTK Query | No |
| Draft form input | Local component state or form library | No |
| Theme preference | AsyncStorage, if user-selectable | Yes |
| Non-secret feature preferences | AsyncStorage | Optional |

Recommended authentication states:

```ts
type AuthStatus =
  | "bootstrapping"
  | "unauthenticated"
  | "awaiting_2fa"
  | "authenticated";
```

Recommended Redux auth state:

```ts
type AuthState = {
  status: AuthStatus;
  user: AuthUser | null;
  pendingTwoFactorEmail: string | null;
};
```

The access token should live in a small module-scoped token manager rather than in persisted Redux:

```ts
let accessToken: string | null = null;

export const accessTokenStore = {
  get: () => accessToken,
  set: (value: string | null) => {
    accessToken = value;
  },
};
```

The secure refresh-token vault should expose only `get`, `set`, and `clear` operations. Feature code
must not call SecureStore directly.

## 7. Target mobile structure

```text
mobile/
  src/
    app/
      _layout.tsx
      (auth)/
        _layout.tsx
        login.tsx
        verify-login-2fa.tsx
      (app)/
        _layout.tsx
        (tabs)/
          _layout.tsx
          dashboard.tsx
          chat.tsx
          documents.tsx
          tickets.tsx
        conversations/
          index.tsx
          [conversationId].tsx
        documents/
          [documentId].tsx
          upload.tsx
        tickets/
          [ticketId].tsx
        settings/
          index.tsx
    components/
      feedback/
      forms/
      layout/
      navigation/
      ui/
    constants/
      env.ts
      theme.ts
    features/
      auth/
      chat/
      conversations/
      dashboard/
      documents/
      tickets/
    services/
      api/
        api.ts
        base-query.ts
        errors.ts
      auth/
        access-token-store.ts
        session-manager.ts
        token-vault.ts
    store/
      hooks.ts
      index.ts
    types/
      api.ts
      auth.ts
  e2e/
  app.json
  eas.json
  package.json
```

Feature directories should contain feature-specific API definitions, components, schemas, and
utilities. Route files should remain thin and compose feature components.

## 8. Recommended dependencies

Install dependencies through Expo-compatible commands where applicable.

```bash
cd mobile
npx expo install expo-secure-store
npx expo install expo-document-picker
npx expo install expo-file-system
npx expo install expo-sharing
npm install @reduxjs/toolkit react-redux
npm install react-hook-form zod @hookform/resolvers
npm install async-mutex
```

Testing dependencies should be introduced during the foundation phase rather than postponed:

```bash
npx expo install jest-expo
npm install --save-dev @testing-library/react-native @types/jest
```

Use only dependencies that are needed by an active phase. Confirm compatibility with the installed
Expo SDK before adding native packages.

### Completed Phase 1 dependency cleanup

- Replaced the erroneous `react-hook` dependency with `react-hook-form` and updated the lockfile.
- Completed ESLint configuration so the documented lint command runs successfully.
- Validated installed dependencies against Expo SDK 57 and removed incompatible, future-phase, and
  unused direct packages.

## 9. Environment configuration

The API base URL is public configuration, not a secret. Expose only URLs through Expo public
variables:

```text
EXPO_PUBLIC_API_BASE_URL=https://api.example.com/api/v1
EXPO_PUBLIC_AI_API_BASE_URL=https://ai.example.com/api/v1
```

Development URL notes:

- iOS Simulator can usually reach a host service through `localhost`.
- Android Emulator usually reaches the host through `10.0.2.2`.
- A physical device must use a LAN-reachable address or HTTPS tunnel.
- Production and preview builds must use HTTPS.
- Never put a token, password, signing secret, or private API key in Expo public variables.

Create one typed environment module that validates required values at startup. Feature code must not
read `process.env` directly.

## 10. Ordered implementation plan

## Phase 0 — Freeze requirements and establish working agreements

### Tasks

- [x] Confirm the included and deferred MVP scope.
- [x] Decide the multi-device refresh-token policy.
- [x] Decide whether Expo web is unsupported, best-effort, or a release target.
- [x] Decide whether to replace `unstable-native-tabs` with stable Expo Router tabs.
- [x] Add `mobile/AGENTS.md` with architecture rules, security rules, required commands, and done
      criteria.
- [x] Add a short mobile README with setup and device networking instructions.
- [x] Add a mobile environment example file containing only public URLs.
- [x] Define the minimum supported iOS and Android versions.

### Exit criteria

- Scope decisions are recorded.
- The authentication session policy is recorded.
- Codex and human contributors have the same build, test, and security instructions.
- A new developer can start the Expo app from the README.

## Phase 1 — Reset the starter and create the application foundation

### Tasks

- [x] Remove Expo tutorial screens and components that will not be reused.
- [x] Preserve useful theme primitives only when they match the CacaNode design direction.
- [x] Create `(auth)` and `(app)` route groups.
- [x] Create a root provider composition for theme, Redux, RTK Query, safe areas, and error handling.
- [x] Configure Redux Toolkit with typed `useAppDispatch` and `useAppSelector` hooks.
- [x] Create the RTK Query base API.
- [x] Create normalized API error types for Spring and FastAPI error envelopes.
- [x] Create typed environment validation.
- [x] Create shared loading, empty, error, offline, and unauthorized components.
- [x] Create a basic color, typography, spacing, radius, and elevation system.
- [x] Add lint, TypeScript, unit-test, and test-watch scripts.
- [x] Add at least one component test and one store test to prove the test setup works.
- [x] Replace the erroneous `react-hook` dependency with `react-hook-form`.
- [x] Complete the ESLint configuration.
- [x] Validate installed dependencies against Expo SDK 57 and remove or correct incompatible or
      unused packages.

### Initial scripts

```json
{
  "typecheck": "tsc --noEmit",
  "test": "jest",
  "test:watch": "jest --watch"
}
```

### Exit criteria

- `npm run lint` passes.
- `npm run typecheck` passes.
- `npm test` passes.
- The starter content is gone.
- Authenticated and unauthenticated route groups render placeholder screens.
- There are no hard-coded API URLs outside the environment module.

## Phase 2 — Add native authentication support to Spring

### Tasks

- [x] Add native login response DTO containing access token, refresh token, expiry, and user.
- [x] Add native refresh and logout request DTOs.
- [x] Add `/auth/mobile/login` while retaining login 2FA behavior.
- [x] Add `/auth/mobile/verify-login-2fa`.
- [x] Add `/auth/mobile/refresh` with refresh-token rotation.
- [x] Add `/auth/mobile/logout` with token revocation.
- [x] Apply the selected multi-device policy.
- [x] Ensure all native endpoints use the same rate limits and account-status checks as web auth.
- [x] Ensure no refresh token is written to logs, audit metadata, or error payloads.
- [x] Ensure invalid refresh attempts return a generic 401 response.
- [x] Add tests for login, 2FA, rotation, expiry, revocation, account deactivation, tenant isolation,
      concurrent refresh behavior, and browser compatibility.

### Exit criteria

- Browser login/refresh/logout tests still pass unchanged.
- Native login and 2FA return both credentials only after successful authentication.
- A refresh token can be used once and is rotated.
- Reusing the old refresh token fails.
- Logout revokes the current mobile refresh token.
- Refresh rotation and logout leave other browser and device sessions valid.
- No mobile token appears in application logs or audit metadata.

## Phase 3 — Implement the mobile authentication foundation

### Tasks

- [x] Install and configure `expo-secure-store`.
- [x] Implement `token-vault.ts` for the refresh token.
- [x] Implement the in-memory access-token store.
- [x] Implement the auth Redux slice.
- [x] Implement native login, verify-2FA, resend-2FA, refresh, and logout API calls.
- [x] Implement a session manager that atomically updates access token, refresh token, and Redux user.
- [x] Implement cold-start bootstrapping from SecureStore.
- [x] Implement a single-flight refresh mutex for concurrent 401 responses.
- [x] Retry the original request once after successful refresh.
- [x] Prevent infinite refresh loops.
- [x] Clear all local session state after terminal refresh failure.
- [x] Implement protected Expo Router redirects.
- [x] Implement login and 2FA screens using React Hook Form and Zod.
- [x] Add password visibility, keyboard avoidance, loading state, disabled state, and error messages.
- [x] Implement logout from the account screen.
- [x] Redact authentication data from development logging.

### Required authentication tests

- [x] No stored token starts at the login screen.
- [x] Valid refresh token restores the authenticated route.
- [x] Invalid refresh token is deleted and returns to login.
- [x] Login requiring 2FA navigates to verification.
- [x] Successful 2FA stores the refresh token and enters the app.
- [x] Multiple simultaneous 401 responses trigger exactly one refresh request.
- [x] Successful refresh retries queued requests.
- [x] Failed refresh rejects queued requests and clears the session.
- [x] Logout calls the server, clears SecureStore, clears memory, and returns to login.
- [x] Refresh token is absent from Redux state and persisted storage other than SecureStore.

### Exit criteria

- Authentication works after a force-close and relaunch.
- Access tokens are never persisted.
- Refresh tokens exist only in SecureStore and the backend database hash.
- Route protection has no visible flash of protected content.
- The full auth test matrix passes.
- Login, 2FA, refresh, and logout are verified on both iOS and Android.

## Phase 4 — Create authenticated navigation and reusable mobile UI

### Recommended MVP navigation

Primary tabs:

1. Dashboard
2. Chat
3. Documents
4. Tickets

Secondary stack routes:

- Conversations
- Conversation detail
- Document detail
- Document upload
- Ticket detail
- Account/settings

### Tasks

- [x] Replace the starter tab implementation.
- [x] Add role-aware tab and stack configuration.
- [x] Add a consistent screen header.
- [x] Add an account/avatar menu with logout.
- [x] Add reusable buttons, inputs, cards, badges, list rows, separators, dialogs, and sheets.
- [x] Add skeletons and retry panels.
- [x] Add safe-area, status-bar, keyboard, and large-text support.
- [x] Ensure interactive controls have accessibility labels and adequate touch targets.
- [x] Ensure dark mode has sufficient contrast.

### Exit criteria

- Navigation works with Android back gestures and iOS swipe-back behavior.
- Deep stack routes can be opened directly without breaking auth redirects.
- Non-admin users do not see admin-only actions.
- Shared UI primitives cover the upcoming feature screens without copy-pasted styles.

## Phase 5 — Dashboard vertical slice

### API

```text
GET /api/v1/dashboard/summary
```

### Tasks

- [x] Add dashboard summary types and RTK Query endpoint.
- [x] Render usage, documents, conversations/messages, tickets, and plan information available in
      the current response.
- [x] Add pull to refresh.
- [x] Add loading skeleton, error retry, and empty states.
- [x] Add navigation shortcuts to chat, upload, conversations, and tickets.
- [x] Hide admin-only shortcuts for regular users.
- [x] Test response mapping and role-aware actions.

### Exit criteria

- Dashboard data matches the authenticated tenant.
- Refresh updates visible metrics.
- No stale tenant data remains after logout/login as another tenant.
- Dashboard is verified at small and large font sizes.

## Phase 6 — Employee chat playground vertical slice

### APIs

Use the existing employee playground endpoints in FastAPI for:

- Creating a chat session.
- Submitting a message.
- Listing message history.
- Listing employee playground sessions.
- Hiding/deleting a playground session.

### Tasks

- [x] Add typed chat session, message, citation, and history models.
- [x] Load the tenant workspace to obtain chatbot and knowledge-base identifiers.
- [x] Create or resume an employee playground session.
- [x] Build the chat transcript with user and assistant messages.
- [x] Build message composer validation and disabled/loading behavior.
- [x] Display citations with document name, snippet, page, section, sheet, or cell metadata when
      available.
- [x] Add a citation detail sheet.
- [x] Add conversation history and new-chat actions.
- [x] Handle no-information responses, quota errors, model timeouts, model provider failures, and
      unavailable session storage.
- [x] Keep message drafts local; do not persist them by default.
- [x] Ensure chat text is never logged.
- [x] Test tenant/user scoping and history ownership.

### Exit criteria

- An employee can start a chat, send multiple messages, and inspect citations.
- Existing employee sessions can be reopened.
- A session belonging to another user cannot be accessed.
- Loading and failure states do not duplicate submitted messages.
- Chat is usable with the keyboard open on both platforms.

## Phase 7 — Documents vertical slice

### APIs

```text
GET    /api/v1/documents
POST   /api/v1/documents
GET    /api/v1/documents/{documentId}
GET    /api/v1/documents/{documentId}/download
PATCH  /api/v1/documents/{documentId}/visibility
DELETE /api/v1/documents/{documentId}
```

### Tasks

- [x] Add document list, detail, status, visibility, and upload response types.
- [x] Add document RTK Query endpoints.
- [x] Build a paginated/filterable document list.
- [x] Display processing, completed, and failed status clearly.
- [x] Implement document selection using `expo-document-picker`.
- [x] Validate type and size before upload using the same rules as the backend.
- [x] Upload multipart content with progress or an explicit indeterminate state.
- [x] Poll or refresh processing status without excessive requests.
- [x] Download files to a temporary application location.
- [x] Open or share downloads through native platform APIs.
- [x] Allow tenant admins to change customer visibility.
- [x] Allow tenant admins to delete documents with confirmation.
- [x] Preserve list filters after opening document detail.
- [x] Invalidate dashboard and document queries after mutations.

### Exit criteria

- A supported file can be selected, uploaded, and observed through processing.
- Completed files can be downloaded/shared.
- Failed uploads display the safe backend error.
- Regular users cannot see or trigger admin document mutations.
- Upload and download behavior is verified on iOS and Android devices or development builds.

## Phase 8 — Customer conversations vertical slice

### Tasks

- [x] Add typed conversation list, conversation detail, message, citation, customer, and metadata
      models.
- [x] Add conversation list and detail endpoints from the existing FastAPI contract.
- [x] Build status and channel filters.
- [x] Add pagination or incremental loading.
- [x] Show customer identity safely, including missing-name and missing-email states.
- [x] Render message history and citations.
- [x] Render ticket-draft actions when present without treating a draft as a created ticket.
- [x] Add pull to refresh and retry.
- [x] Ensure message and document text are never logged.

### Exit criteria

- Users can browse Widget and Custom API conversations for their tenant.
- Conversation detail displays the unchanged public message contract.
- Another tenant's conversation cannot be loaded through route manipulation.
- Large transcripts remain responsive.

## Phase 9 — Tickets vertical slice

### APIs

```text
GET   /api/v1/tenants/me/tickets
GET   /api/v1/tenants/me/tickets/assignees
GET   /api/v1/tenants/me/tickets/{ticketId}
PATCH /api/v1/tenants/me/tickets/{ticketId}
POST  /api/v1/tenants/me/tickets/{ticketId}/notes
```

### Tasks

- [x] Add ticket, status, priority, assignee, and note types.
- [x] Build ticket list filters and pagination.
- [x] Build ticket detail with customer and conversation context.
- [x] Allow supported status, priority, and assignee updates.
- [x] Add internal notes with validation and optimistic or confirmed update behavior.
- [x] Confirm destructive/terminal state changes where appropriate.
- [x] Invalidate ticket list, ticket detail, and dashboard data after mutations.
- [x] Add conflict/error recovery without losing note drafts.

### Exit criteria

- Ticket list and detail remain tenant-scoped.
- Updates are reflected after navigation and app restart.
- Failed note submissions preserve the user's text.
- Assignee and status behavior matches the web application contract.

## Phase 10 — Resilience, accessibility, and security hardening

### Tasks

- [ ] Detect offline state and distinguish it from server errors.
- [ ] Provide retry behavior for idempotent reads.
- [ ] Avoid automatically retrying unsafe mutations unless idempotency is guaranteed.
- [ ] Clear RTK Query cache on logout and tenant changes.
- [ ] Verify no sensitive data appears in Redux DevTools or device logs.
- [ ] Verify screenshots and app switcher previews do not expose highly sensitive screens; add a
      privacy overlay if required.
- [ ] Review dependency security and Expo compatibility.
- [ ] Add accessibility labels, roles, hints, focus order, and large-text checks.
- [ ] Test screen-reader behavior for forms, chat messages, citations, uploads, and tickets.
- [ ] Test slow network, expired access token, expired refresh token, revoked token, 403, 404, 429,
      500, 502, 503, and 504 behavior.
- [ ] Ensure all error messages shown to users are safe and actionable.

### Exit criteria

- No token appears in logs, Redux persistence, AsyncStorage, or error reports.
- Offline and server-error states are distinguishable.
- Critical flows work with VoiceOver/TalkBack and large text.
- Authentication recovery is deterministic under concurrent failures.

## Phase 11 — End-to-end tests and release readiness

### Required end-to-end flows

- [ ] Login requiring 2FA.
- [ ] Cold-start session restoration.
- [ ] Access-token expiry followed by successful refresh.
- [ ] Refresh-token failure followed by login redirect.
- [ ] Dashboard load and refresh.
- [ ] Start chat, send question, and open citation.
- [ ] Upload document and observe its status.
- [ ] Open conversation detail.
- [ ] Update ticket and add note.
- [ ] Logout and verify protected routes are inaccessible.
- [ ] Verify regular-user and tenant-admin authorization differences.

### Release tasks

- [ ] Configure application name, slug, scheme, icons, and splash assets for CacaNode.
- [ ] Configure iOS bundle identifier and Android package name.
- [ ] Add `eas.json` with development, preview, and production profiles.
- [ ] Configure preview and production API URLs.
- [ ] Configure signing through EAS.
- [ ] Add privacy policy and store metadata.
- [ ] Configure crash reporting with token and content redaction before enabling it.
- [ ] Add CI for install, lint, typecheck, unit tests, and preview build validation.
- [ ] Produce an internal iOS build and Android build.
- [ ] Run the full release checklist on physical devices.

### Final MVP exit criteria

- All phase exit criteria are satisfied.
- All required end-to-end flows pass on iOS and Android.
- Spring tests, FastAPI tests, mobile lint, mobile typecheck, and mobile tests pass.
- A preview build installs and runs against the preview backend.
- Security review confirms the refresh token is stored only in SecureStore.
- The MVP has no known blocker-level defects.
- Deferred features are recorded as post-MVP work rather than partially implemented hidden flows.

## 11. Verification commands

Run checks from the relevant project directory after every implementation slice.

### Mobile

```bash
cd mobile
npm run lint
npm run typecheck
npm test
npx expo export --platform ios
npx expo export --platform android
```

Use development builds or device runs for native capabilities that Expo export cannot validate:

```bash
npm run ios
npm run android
```

### Spring API

```bash
cd api
sh mvnw test
```

### FastAPI

```bash
cd rag-chatbot-fastapi
.venv/bin/pytest
.venv/bin/ruff check app tests
.venv/bin/mypy app
```

## 12. Definition of done for every task

A task is not complete merely because its primary screen renders. Every task must satisfy all
applicable items:

- [ ] Behavior matches the documented API contract.
- [ ] Tenant and user authorization are enforced by the backend.
- [ ] TypeScript has no new errors.
- [ ] Loading, empty, success, and failure states are implemented.
- [ ] User edits are preserved after recoverable API failures.
- [ ] Sensitive content is not logged or persisted insecurely.
- [ ] Unit or component tests cover important state transitions.
- [ ] Existing tests still pass.
- [ ] The feature is manually verified on iOS and Android when native behavior is involved.
- [ ] The roadmap checkbox and relevant documentation are updated.
- [ ] Deferred behavior is explicitly documented instead of silently omitted.

## 13. Recommended Codex implementation loop

Use one phase or one vertical slice per Codex task. Avoid asking for the entire mobile application in
one implementation turn.

For each task:

1. Ask Codex to read `MOBILE_MVP.md`, `mobile/AGENTS.md`, and the relevant backend/frontend files.
2. State the exact phase and checkboxes being implemented.
3. Ask Codex to inspect the current implementation before changing files.
4. Require it to implement the complete slice, not only produce a plan.
5. Require tests and verification proportional to the change.
6. Require it to update the completed checkboxes in this document.
7. Review the diff and record recurring corrections in `mobile/AGENTS.md`.
8. Continue to the next task only after the current exit criteria pass.

Example task prompt:

```text
Implement MOBILE_MVP.md Phase 3, Mobile Authentication Foundation.

Read MOBILE_MVP.md and mobile/AGENTS.md first. Inspect the current Spring native auth endpoints and
the Expo project before editing. Implement SecureStore refresh-token storage, in-memory access-token
storage, Redux auth state, session bootstrap, single-flight refresh, protected Expo Router groups,
login, 2FA, logout, and the required tests. Never persist or log tokens. Run mobile lint, typecheck,
tests, and the relevant Spring tests. Update only the completed Phase 3 checkboxes after verification.
```

## 14. Post-MVP sequence

After the MVP release is stable, implement in this order:

1. Push notifications for ticket and document events.
2. Email verification and invitation deep links.
3. Detailed analytics.
4. Team administration.
5. Tenant AI Prompt administration.
6. Widget, integration-token, and webhook administration.
7. Biometric application lock.
8. Improved offline reading and cached drafts.
9. Tablet-specific layouts.
10. Expo web parity, only if it remains a product requirement.
