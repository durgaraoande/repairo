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