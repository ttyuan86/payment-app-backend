# payment-app-backend

A **multi-tenant backend** for invoice-driven payments.

- **Tech stack**: Spring Boot (Java 11), Spring Web, Spring Data JPA, H2/MySQL
- **Security/Validation**: API Key (`X-API-Key`), Rate Limiting,client isolation via `X-CLIENT-ID`, AES-GCM encryption, Luhn check, CORS
- **Reliability**: **Double guards duplication check** (in-memory + DB idempotency), database unique constraints
- **Documentation**:  OpenAPI 3 (`openapi.yml` in repo root)

---

## ✅ Implemented Features

### 1. Payment creation with **double guards** against duplicates
- **Guard #1: In-memory Idempotency Service**
    - Uses a composite key `X-CLIENT-ID:Idempotency-Key`
    - API: `isCompleted`, `isPending`, `tryAcquire`, `markPending`, `markCompleted`, `release`
    - Prevents duplicate work within the same JVM instance.
    - Returns:
        - Already completed → loads and returns the existing `Payment`
        - Currently processing → `202 Accepted "Processing, please retry later"`

- **Guard #2: Database IdempotencyKey**
    - Inserts `(clientId, idempotencyKey)` with status `PENDING` and a `requestHash`
    - On unique constraint violation:
        - Different `requestHash` → `409 Conflict` (same key used for different request)
        - Status `COMPLETED` → return the previously stored `Payment`
        - Otherwise → `202 Accepted`

> Together, the two guards prevent both **retry duplicates** and **concurrent double-pay issues**.

---

### 2. Business Validation
- **Luhn check** for card numbers (`CardUtils.luhn`)
- **Expiry date** must be in the future (`YearMonth`)
- **Amount** must be positive (`amountMinor > 0`)

---

### 3. Encryption & Persistence
- **AES-GCM encryption** for PAN (`AesGcmEncryptor.encryptPan`)
    - `AAD = tenantId|paymentId` → ciphertext bound to context
    - Persists: `encPan`, `iv`, `tag`, `dekKid` (all **NOT NULL**)
    - Returns only **masked PAN**; CVV is **never stored**
- `PaymentInvoice` persisted per invoice ID, with unique constraint `(clientId, invoiceId)`
    - Duplicate invoice → `409 Conflict`

---

### 4. State Management
- **IdempotencyKey.status**:
    - Initially `PENDING`
    - Updated to `COMPLETED` when payment succeeds
- **Payment.status**:
    - Currently set to `"SUCCEEDED"` in demo code
    - Should be taken from the payment gateway in production

---

### 5. Error Model (via `ResponseStatusException` + Global Handler)
- `400 Bad Request` → missing headers, invalid card, expired card, non-positive amount
- `202 Accepted` → processing in progress
- `409 Conflict` → duplicate invoices or reused idempotency key with different payload
- `500 Internal Server Error` → unexpected failure

---
### 6. Rate Limiting
Limit: 100 requests per minute per API key
- Implemented via `RateLimiterService` (using **Bucket4j**)
- Configured in `SecurityConfig` filter chain
## 🔌 HTTP API

### `POST /api/payments`

**Headers**
- `X-API-Key: <api key>`
- `X-CLIENT-ID: <client id>`
- `Idempotency-Key: <unique idempotency key>`

**Request body (`PaymentCreateRequest`)**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "cardNumber": "4111111111111111",
  "expiryMonth": 3,
  "expiryYear": 2028,
  "cvv": "123",
  "amountMinor": 1377044,
  "currency": "USD",
  "invoiceIds": ["INV-2025-007", "INV-2025-003"],
  "clientReferenceId": "web_1736567890000"
}
```

**Success (`PaymentResponse`)**
```json
{
  "paymentId": "pay_01JABCDE12345",
  "status": "SUCCEEDED",
  "amountMinor": 1377044,
  "currency": "USD",
  "maskedCard": "**** **** **** 1111",
  "brand": "VISA",
  "expiryMonth": 3,
  "expiryYear": 2028,
  "tenantId": "acme-prod",
  "idempotencyKey": "web_1736567890000"
}
```

**Conflict (invoice already paid)**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Some invoices already paid (duplicate invoice): INV-2025-007",
  "paidInvoiceIds": ["INV-2025-007"],
  "path": "/api/payments"
}
```

**Processing**
```json
{
  "status": 202,
  "message": "Processing, please retry later"
}
```

---

## 📦 Data Model

### `entity.Payment`
- `id`, `paymentId`, `clientId` (from `X-CLIENT-ID`)
- `amountMinor`, `currency`, `maskedCard`, `brand`
- `expiryMonth`, `expiryYear`, `clientReferenceId`
- `idempotencyKey`, `requestHash`
- `encPan`, `iv`, `tag`, `dekKid`
- `status`, `createdAt`, `updatedAt`

### `entity.PaymentInvoice`
- `id`, `clientId`, `invoiceId`, `payment` (FK → Payment)
- Unique constraint: `(clientId, invoiceId)`

### `entity.IdempotencyKey`
- `id`, `clientId`, `idempotencyKey`, `requestHash`
- `status` (`PENDING` | `COMPLETED`)
- `expiresAt` (TTL)
- Unique constraint: `(clientId, idempotencyKey)`

### `dto.PaymentCreateRequest`
- `firstName`, `lastName`, `cardNumber`, `expiryMonth`, `expiryYear`, `cvv`
- `amountMinor`, `currency`, `invoiceIds[]`, `clientReferenceId`

### `dto.PaymentResponse`
- `paymentId`, `status`, `amountMinor`, `currency`
- `maskedCard`, `brand`, `expiryMonth`, `expiryYear`
- `tenantId` (from `X-CLIENT-ID`), `idempotencyKey`

---

## 📂 Project Structure

```
src/main/java/com/tonyyuan/paymentappbackend
├─ config/        # RateLimiterService, SecurityConfig
├─ controller/    # PaymentController
├─ dto/           # ErrorResponse, PaymentCreateRequest, PaymentResponse
├─ entity/        # Payment, PaymentInvoice, IdempotencyKey
├─ exception/     # AlreadyPaidException, GlobalExceptionHandler
├─ repository/    # PaymentRepository, PaymentInvoiceRepository, IdempotencyKeyRepository
├─ security/      # ApiKeyFilter
├─ service/       # PaymentService, IdempotencyService, InMemoryIdempotencyService
├─ util/          # AesGcmEncryptor, CardUtils, RequestHashUtils
└─ PaymentAppBackendApplication.java

src/main/resources
├─ application.yml
└─ application.properties

openapi.yml   # root-level spec
pom.xml
README.md
```

---

## 🔒 Security Notes
- **Authentication**: API Key (`X-API-Key`)
- **Tenant isolation**: `X-CLIENT-ID` header
- **Encryption**: AES-GCM for PAN (`encPan`, `iv`, `tag`, `dekKid`), CVV never stored
- **CORS**: configured via `SecurityConfig`
- **Rate limiting**: default 100 requests/minute per API key (can be moved to API Gateway)

---

## 🐳 Running & Testing
```bash
# Run dev mode with H2
mvn spring-boot:run

# Run tests
mvn clean test
```



---

## 🚀 Potential Enhancements
- External idempotency store (Redis) for cross-instance duplicate protection
- OAuth2/OIDC (Keycloak/Auth0) instead of API Key
- Rate limiting at API Gateway (Kong, NGINX, AWS API Gateway)
- PSP integration (Adyen, Stripe) with **Resilience4j** (timeouts, retries, circuit breakers)
- Kafka + **Transactional Outbox Pattern** for async event publishing and consistency
- Vault/KMS for key management & DEK rotation
- PCI DSS scope reduction (tokenization via PSP vaulting)
- Observability: structured logs (PAN masked), Prometheus metrics, distributed tracing

---

## 📘 OpenAPI
- Spec file → [`openapi.yml`](./openapi.yml)

