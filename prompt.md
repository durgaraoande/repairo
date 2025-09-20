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