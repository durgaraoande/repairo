# Repairo – Repair Shop Messaging & Service Dashboard

A Spring Boot + MongoDB application for managing mobile device repair workflows and real‑time WhatsApp customer messaging.

## Key Features
- WhatsApp inbound webhook + outbound message sending (API-ready scaffold)
- Encrypted sensitive fields (customer phone, issue description, message text)
- Admin dashboard (Thymeleaf) for customers, conversations, and repair status updates
- Incremental chat updates: WebSocket (STOMP) with automatic polling fallback & diff polling optimization
- Optimistic locking on customer updates (`@Version`) to prevent lost updates
- Rate limiting (token bucket) for polling and mutation endpoints
- Feature toggles & configurable polling intervals via `application.yml`
- Audit trail (optional) for status changes
- CSRF protection, configurable WebSocket origins, conditional WebSocket enablement

## Tech Stack
- Java 17+, Spring Boot, Spring Data MongoDB, Spring Security, Thymeleaf
- STOMP over WebSocket (SockJS fallback)
- Jackson for JSON, field-level encryption (AES)

## Quick Start
```bash
# 1. Provide environment secrets (dev defaults exist but replace for real use)
set ENCRYPTION_KEY=replaceWithLongRandomSecret
set WHATSAPP_ACCESS_TOKEN=...
set WHATSAPP_PHONE_NUMBER_ID=...
set WHATSAPP_WEBHOOK_VERIFY_TOKEN=...

# 2. Run (Windows PowerShell)
./gradlew.bat bootRun

# 3. Access dashboard
http://localhost:8080/login  (default creds: admin / admin – change in config)
```

## Configuration Overview (`application.yml`)
```yaml
app:
  encryption:
    key: ${ENCRYPTION_KEY}
  security:
    admin:
      username: admin
      password: admin
  websocket:
    enabled: true
    allowed-origins: "*"          # Change in production
  features:
    websockets: true
    diff-polling-default-enabled: true
    strict-json: false             # Planned enforcement toggle
    audit-status: true
  polling:
    messages:
      interval-ms: 4000
      max-interval-ms: 30000
    dashboard:
      interval-ms: 10000
  rate-limit:
    enabled: true
    policies:
      check-messages:
        capacity: 60
        diff-capacity: 120
        periodMs: 60000
        perIp: true
        paths: [/admin/check-new-messages]
      send-message:
        capacity: 120
        periodMs: 60000
        perIp: true
        paths: [/admin/send-message]
      update-status:
        capacity: 90
        periodMs: 60000
        perIp: true
        paths: [/admin/update-status]
```

### Notable Toggles
- `app.features.websockets` / `app.websocket.enabled`: Disable to force polling-only.
- `app.features.diff-polling-default-enabled`: Auto-switch to lightweight diff payloads after first full load.
- `app.features.audit-status`: Toggle persistence + WebSocket broadcast of status changes.
- `app.features.strict-json`: Placeholder for future hard enforcement of JSON-only API usage.

## Core Endpoints
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/webhook` | POST | WhatsApp inbound webhook (extend with signature/verification) |
| `/admin/check-new-messages` | GET | Full or diff conversation metadata (`?diff=true`) |
| `/admin/send-message` | POST | Send a message to customer (JSON) |
| `/admin/update-status` | POST | Update repair status (optimistic lock) |
| `/ws` | WS/SockJS | STOMP broker endpoint |

## Security Notes
- Replace default admin credentials immediately (or integrate proper user store).
- Set a strong `ENCRYPTION_KEY` (32+ chars) and do not commit it.
- Restrict `app.websocket.allowed-origins` to trusted domains.
- Consider setting `perIp=false` for rate limit policies if behind an auth proxy with single admin IP.

## Development Tips
- Profiles: `dev` (default) vs `prod` (enable SSL & caching)
- To disable WebSockets temporarily: set `app.websocket.enabled=false` (polling continues)
- Logs: adjust via `logging.level.com.repairo`

## Roadmap (Selected)
- Strict JSON enforcement interceptor
- Consolidate WebSocket enable flags into a single property
- Role-based multi-admin security model
- Distributed rate limiting & metrics (Micrometer)
- OpenAPI documentation for admin JSON endpoints
- External message broker for scale (RabbitMQ / Redis)

## Contributing
1. Fork & create a feature branch
2. Follow existing code style & naming
3. Provide focused commits + brief PR description

## License
Currently unspecified – add a license file (e.g., MIT/Apache-2.0) before public distribution.

---
For the full architectural notes and extended design rationale, see `prompt.md`.
