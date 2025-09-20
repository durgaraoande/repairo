# 📱 Repair Shop Web Application – AI Development Prompt

## 👨‍💻 Overview

Build a **Spring Boot-based web application** for a **mobile repair shop**, where customers can contact the business via **WhatsApp**, and admins can manage customer data, reply to messages, and track repair statuses from a **Thymeleaf dashboard**.

- **Backend**: Spring Boot (Java 17+)
- **Database**: MongoDB (with field-level encrypted sensitive data)
- **Messaging**: WhatsApp Business Cloud API (Meta)
- **Frontend**: Thymeleaf
- **Security**: Spring Security, field-level encryption, HTTPS

---

## ✅ Version 1 – MVP Tasks (Ordered by Implementation Level)

### 🧱 Level 1: Project Setup & Basic Config

- Set up Spring Boot project structure (Maven or Gradle)
- Add dependencies:
  - Spring Web
  - Spring Data MongoDB
  - Spring Security
  - Thymeleaf
  - Jackson
  - JCE or Java crypto libraries for encryption
- Configure `application.yml`
- Set up MongoDB connection

---

### 🔐 Level 2: Encryption & Data Modeling

- Implement field-level encryption only for sensitive fields:
  - `phone` (customer phone number)
  - `messages[].text` (message contents)
  - `issue` (description of problem)
- Leave other fields plaintext for filtering and display:
  - `name`, `phoneModel`, `repairStatus`, `onboardingState`, `lastInteraction`
- Define MongoDB schema:
  - `Customer` model
  - `Message` sub-document
  - `RepairStatus` enum

```java
enum RepairStatus {
  PENDING, IN_PROGRESS, COMPLETED
}
```

#### MongoDB Document Example:

```json
{
  "customerId": "ObjectId",
  "name": "John Doe",
  "phone": "Encrypted String",
  "messages": [
    {
      "text": "Encrypted String",
      "from": "customer" | "admin",
      "timestamp": "ISO Date",
      "status": "pending" | "replied"
    }
  ],
  "issue": "Encrypted String",
  "phoneModel": "Samsung Galaxy S21",
  "onboardingState": "COMPLETED",
  "repairStatus": "pending",
  "lastInteraction": "ISO Date"
}
```

---

### 💬 Level 3: WhatsApp Business API Integration

Set up Meta WhatsApp Business Cloud API:

- Create Meta developer account
- Register WhatsApp Business number
- Configure webhooks
- Generate access tokens

#### Implement webhook receiver:

- Create `/webhook` endpoint to receive incoming messages
- Parse and store message in MongoDB with encryption where needed

#### Implement outgoing message sender:

- Create a service to send messages via WhatsApp API
- Reply to customers via dashboard or automated messages

---

### 📊 Level 4: Admin Dashboard (Thymeleaf)

Create login-protected admin panel (Spring Security)

#### Dashboard to:

- View list of customers
- Filter/search by name or phone
- View full conversation history
- Update repair status

#### Conversation screen:

- View full chat thread
- Reply via input box (triggers WhatsApp API)
- Update message status: pending, replied

---

### 🤖 Level 5: Auto-Reply & Auto-Onboarding Flow

- Detect greetings like "hi" from new users and start onboarding flow:
  - Ask user for name (stored plaintext)
  - Ask for issue (encrypted)
  - Ask for phone model (plaintext)
- After onboarding, create repair request with status `PENDING`
- Detect keywords like "status", "update" in messages to auto-reply with current repair status
- Track onboarding state in `onboardingState` field for each customer

---

### 🧪 Level 6: Testing & Finalization

#### Unit tests for:

- Encryption logic for selective fields
- WhatsApp webhook and sender
- Repair status auto-responder and onboarding flow

#### Integration tests:

- Webhook to message flow
- Dashboard interactions

#### Final security checks:

- Secure environment variables
- Validate WhatsApp webhook tokens
- Enforce HTTPS and CORS policies

---

## ✨ Version 2 – Optional/Planned Features (No Task Order)

- File/image upload support from WhatsApp messages
- Multi-admin roles and permission system
- Email/SMS notifications on new WhatsApp message
- Support reply from WhatsApp mobile app (if via API)
- Dashboard analytics (response time, volume, statuses)
- Repair timeline tracker with visual stages
- Predefined quick replies (WhatsApp message templates)
- Customer profile history and CRM features

---

## 📌 Notes

- Use only official WhatsApp Business Cloud API (no unofficial APIs).
- Encrypt only sensitive fields (`phone`, `messages.text`, `issue`) for balance of security and usability.
- Messages sent from WhatsApp mobile app cannot update system status unless sent via API.
- Use proper token storage, secure authentication, and HTTPS.

---

## 📁 Recommended Project Structure

```plaintext
repairshop-backend/
├── src/
│   ├── controller/
│   │   └── WhatsAppWebhookController.java
│   ├── service/
│   │   ├── MessageService.java
│   │   ├── WhatsAppService.java
│   ├── model/
│   │   ├── Customer.java
│   │   ├── Message.java
│   ├── repository/
│   │   └── CustomerRepository.java
│   ├── config/
│   │   └── MongoEncryptionConfig.java
│   └── templates/
│       └── dashboard.html
├── application.yml
└── README.md
```

---

## ✅ Post-MVP Enhancements Implemented (2025-09)

The following improvements have been added beyond the original MVP scope:

### Security & Hardening
- Re-enabled CSRF protection for sensitive admin endpoints (`/admin/send-message`, `/admin/update-status`, `/admin/check-new-messages`).
- Externalized admin credentials via `application.yml` properties (`app.security.admin.*`).
- Added optimistic locking to `Customer` (`@Version Long version`) to prevent concurrent update overwrites.
- Backward-compatible endpoint handling: legacy front-end form posts still receive plain text responses (`success` / `error` / `conflict`). Modern clients receive structured JSON wrapped in `ApiResponse<T>`.

### Data & Domain Layer
- Added DTOs: `SendMessageRequest`, `UpdateStatusRequest`, `CheckNewMessagesResponse`, generic `ApiResponse<T>`.
- Introduced audit entity `RepairStatusChange` with repository for status transition history.

### Pagination & Performance
- Server-side pagination for customers with `Pageable` support plus existing client-side pagination (hybrid model).
- Added server pagination controls (Thymeleaf) while retaining in-page JS pagination for large lists.

### Real-Time & Poll Optimization
- Implemented `PollUpdateService` to supply minimal diff payloads (`?diff=true`) reducing bandwidth for frequent polling.
- Added WebSocket (STOMP over SockJS) configuration with endpoints `/ws` (SockJS) and `/ws-native`.
- Simple broker destinations: `/topic/admin/new-messages`, `/topic/admin/status-updates`.
- WebSocket event publisher broadcasting new messages and status changes.
- Front-end auto-loads SockJS + STOMP scripts on demand and falls back gracefully to polling if connection fails.

### Front-End Alignment
- Updated `app.js` to: 
  - Send CSRF headers automatically.
  - Negotiate JSON or plain text responses.
  - Handle optimistic locking version (embedded as `data-version`).
  - Parse `ApiResponse` success/error structure.
  - Dynamically subscribe to WebSocket topics; show toasts on events.
- Exposed version info in `customers.html` and `repairs.html` via `data-version` attributes.
- Added CSRF meta tags to templates missing them (`dashboard.html`).

### Observability & Logging
- Added structured log statements around status updates, message sends, diff polling, and WebSocket initialization.

---

## 🔮 Future Refinements (Planned / Suggested)

| Area | Refinement | Rationale |
|------|------------|-----------|
| Security | Restrict WebSocket allowed origins to configured whitelist | Prevent broad cross-origin WS hijack |
| Security | Rate-limit status/message mutation endpoints | Mitigate abuse / accidental flooding |
| Security | Add per-admin accounts & roles (e.g., READ_ONLY, OPERATOR) | Principle of least privilege |
| Transport | Migrate simple broker to external (RabbitMQ / Redis STOMP) if load grows | Scalability & resilience |
| Polling | Server-enforced diff polling throttle (e.g., min interval) | Prevent excessive rapid polls |
| Reliability | Add retry & exponential backoff logic on WebSocket reconnect | Higher realtime availability |
| API | Introduce versioned REST API (`/api/v1/...`) decoupled from Thymeleaf admin endpoints | Cleaner separation UI vs API |
| Front-End | Switch to ETag or `If-None-Match` for large datasets | Reduce payload for unchanged resources |
| Front-End | Add push-based selective DOM patching (virtual list) for large customer sets | Performance on large data |
| Audit | Expose audit trail UI (status change history per customer) | Transparency & compliance |
| Monitoring | Add metrics (Micrometer) for poll vs WebSocket usage | Capacity planning |
| Testing | Add integration tests for dual-mode (legacy vs JSON) endpoints | Prevent regression |
| Deployment | Containerize with multi-stage Dockerfile & health probes | Standardize ops |
| Observability | Centralize structured logs (JSON) & correlation IDs | Traceability |
| Encryption | Periodic re-key / envelope encryption strategy | Long-term cryptographic hygiene |
| Build | Add dependency vulnerability scanning (OWASP, Snyk, etc.) | Supply chain security |

### Configuration Flags (Proposed)
```yaml
app:
  features:
    websockets: true          # Master toggle for WS
    polling-diff-default: false # Whether diff mode is default without ?diff
    strict-json: false          # When true, disable legacy plain-text responses
    audit-status: true          # Toggle status change auditing
  security:
    allowed-ws-origins: "https://admin.example.com,https://ops.example.com"
  rate-limits:
    update-status-per-minute: 120
    send-message-per-minute: 240
```

### Potential Next Sprints
- Sprint 1: Configurable feature toggles + origin restriction + basic rate limiting.
- Sprint 2: Role-based access & audit UI.
- Sprint 3: External message broker & metrics instrumentation.
- Sprint 4: API versioning + client decoupling.

---

## 🧪 Validation Summary (Current Build)
- Build: SUCCESS (Gradle `clean build -x test`).
- Compilation: All new classes (`WebSocketEventPublisher`, DTOs, audit entity, WebSocketConfig) compile.
- Backward Compatibility: Legacy form posts still receive plain text `success`.
- Real-Time: WebSocket endpoints active; fallback polling intact.
- Diff Polling: `/admin/check-new-messages?diff=true` returns minimal update payload.
- Security: CSRF tokens enforced on modified admin endpoints.

Add automated tests & configuration hardening in upcoming refinement tasks.

---

## 📎 Quick Reference (New Endpoints / Patterns)
| Endpoint | Method | Notes |
|----------|--------|-------|
| `/admin/send-message` | POST | Form or JSON, legacy/plain or `ApiResponse` |
| `/admin/update-status` | POST | Optimistic locking via `version` |
| `/admin/check-new-messages` | GET | Full payload; add `?diff=true` for minimal diff |
| `/ws` | WS/SockJS | STOMP endpoint (SockJS) |
| `/ws-native` | WS | Native WebSocket endpoint |

---

## 🛠 Migration Notes
1. Deploy backend first (legacy front-end still works due to dual-mode endpoints).
2. Clear browser cache if WebSocket scripts not loading (dynamic CDN includes).
3. If disabling WebSockets, set `app.features.websockets=false` (after adding feature toggle binding) – polling continues.
4. For strict JSON-only mode, flip `strict-json` once all clients upgraded.

---

End of extended prompt update.

---

## 🧩 Configuration Reference (Added 2025-09)

Below is a consolidated reference of all custom `app.*` configuration keys now supported. Environment variables may override any value via Spring's standard mechanisms.

### Encryption
Key: `app.encryption.key`
Purpose: Symmetric key for field-level AES encryption (dev default is placeholder; replace in prod).
Recommended: Supply via environment variable `ENCRYPTION_KEY` (32+ chars for AES-256 if JCE policy unlimited or Java 11+ default).

### Security (Admin Credentials)
Prefix: `app.security.admin.*`
- `username` / `password`: Basic in-memory admin account.
- `enabled`: Set `false` if migrating to database-backed auth in future.

### WebSocket
Prefix: `app.websocket.*`
- `enabled` (boolean): Master switch for STOMP endpoints (`/ws`, `/ws-native`).
- `allowed-origins` (comma list): Restrict origins; ALWAYS replace `*` in production.
- `heartbeat.inbound-ms` / `heartbeat.outbound-ms`: STOMP heartbeat intervals (ms). Tune >10s to reduce overhead.

Feature alias: `app.features.websockets` (currently paralleling `app.websocket.enabled`). Plan: consolidate to one of them.

### Features Toggles
Prefix: `app.features.*`
- `websockets`: (bool) Mirrors WebSocket enablement (see above).
- `diff-polling-default-enabled`: (bool) If true, frontend auto-switches to `?diff=true` after first full fetch; if false, only explicit query enables diffs.
- `strict-json`: (bool) Planned: when true, reject legacy plain-text responses (HTTP 406/415). Not yet enforced; placeholder.
- `audit-status`: (bool) Persist `RepairStatusChange` and publish WebSocket events on status updates.

### Polling Intervals
Prefix: `app.polling.*`
- `messages.interval-ms`: Base interval for conversation/message polling.
- `messages.max-interval-ms`: Upper bound when exponential/backoff logic stretches polling due to inactivity.
- `dashboard.interval-ms`: Interval for dashboard (customer list/status) refresh.
Frontend Exposure: These values are injected into meta tags and consumed by `app.js` to dynamically configure pollers.

### Rate Limiting
Prefix: `app.rate-limit.*`
- `enabled`: Master switch.
- `policies`: Map of named policies. Each policy supports:
  - `capacity`: Token bucket capacity (max operations per window).
  - `diff-capacity`: Optional override capacity when the request includes `?diff=true` (used for high-frequency lightweight polls).
  - `periodMs`: Refill window (ms) for full bucket replenishment (linear accrual).
  - `perIp`: If true, key includes client IP; if false, global shared bucket.
  - `paths`: Ant-style path patterns this policy applies to.

Example Policies (current defaults):
```
app.rate-limit.policies.check-messages:
  capacity: 60
  diff-capacity: 120
  periodMs: 60000
  perIp: true
  paths: [/admin/check-new-messages]

app.rate-limit.policies.send-message:
  capacity: 120
  periodMs: 60000
  perIp: true
  paths: [/admin/send-message]

app.rate-limit.policies.update-status:
  capacity: 90
  periodMs: 60000
  perIp: true
  paths: [/admin/update-status]
```

Operational Guidance:
- Lower `diff-capacity` if abusive high-frequency polling emerges.
- For internal/VPN-only dashboards you may set `perIp=false` to simplify, but keep per-IP for multi-user fairness.
- If deploying multiple instances, current in-memory limiter must be replaced with a distributed store (Redis, etc.).

### Logging Levels
Defined per profile; adjust via `logging.level.<package>` keys. Production defaults minimize noise: security=WARN, root=WARN.

### WhatsApp Integration
Prefix: `whatsapp.*` (unchanged from earlier description) – ensure tokens not checked into source control.

---

## 🔐 Production Hardening Checklist (Config-Focused)
1. Set a strong `ENCRYPTION_KEY` (32+ chars) via environment variable.
2. Replace `app.websocket.allowed-origins: "*"` with explicit domains.
3. Consider disabling WebSockets (`app.websocket.enabled=false`) if infrastructure (proxy, SSL termination) not ready; polling still works.
4. Tune rate limits based on expected concurrent admin load (e.g., reduce send/update capacities if single-user admin scenario).
5. Enable `strict-json` once all legacy/non-JSON clients removed (after implementing enforcement logic).
6. Move admin credentials to a secrets manager or migrate to a user store; set `app.security.admin.enabled=false` afterward.
7. Add a reverse proxy (Nginx / Traefik) with gzip and caching headers for static assets.

---

## 🔄 Planned Strict JSON Enforcement (Design Sketch)
When `app.features.strict-json=true`:
- Intercept admin API endpoints (`/admin/send-message`, `/admin/update-status`, `/admin/check-new-messages`).
- Reject requests without `Accept: application/json` (HTTP 406) OR lacking `Content-Type: application/json` where body is required (HTTP 415).
- Remove plain-text fallbacks in controller responses.
- Log rejections at INFO level to aid cutover monitoring.

---

## 🧪 Suggested Future Tests for Config
| Concern | Test |
|---------|------|
| Rate limit exhaustion | Rapidly invoke `/admin/check-new-messages` until 429, assert retry message present |
| Diff vs full polling | First full poll then diff poll returns only changed subset |
| Audit toggle off | Disable `audit-status` and verify no `RepairStatusChange` persisted |
| WebSocket disabled fallback | `app.websocket.enabled=false` → frontend establishes polling only |
| Poll interval injection | Meta tags reflect YAML values; JS poller uses them |

---

## 📝 Changelog (Recent Config-Related Additions)
- Added `FeatureProperties` & `PollingProperties` @ConfigurationProperties classes.
- Added in-memory token bucket `RateLimitInterceptor` with configurable per-path policies.
- Introduced WebSocket conditional enablement + allowed origins + heartbeats.
- Injected polling intervals & feature toggles into templates (consumed by `app.js`).
- Optimistic locking via `@Version` on `Customer` entity.
- Audit toggle controlling status change persistence + WebSocket publishing.

---

## 📌 Next Potential Improvements
- Consolidate `app.features.websockets` and `app.websocket.enabled` into a single canonical property.
- Implement strict JSON enforcement interceptor.
- Externalize rate limiting to distributed cache for horizontal scaling.
- Add Micrometer metrics for rate limit hits/misses and polling vs WebSocket usage ratio.
- Provide OpenAPI (springdoc) for `/admin` JSON endpoints.

---

End of configuration reference update.