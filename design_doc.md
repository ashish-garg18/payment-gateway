# Payment Gateway System Design Document

## 1. Introduction
This document outlines the High-Level Design (HLD) and Low-Level Design (LLD) for a robust Payment Gateway Checkout & Payment Orchestration Platform. The system is designed to handle high-throughput payment processing, offering a seamless checkout experience and intelligent payment routing.

The core objectives are to provide:
*   **Unified Checkout API**: Aggregates supported payment methods, user instruments, and applies dynamic eligibility rules.
*   **Resilient Payment Execution**: Orchestrates payments across multiple vendors (e.g., PayU, Razorpay) with intelligent routing based on availability, pricing, and performance.

## 2. Requirements

### 2.1 Functional Requirements
**Checkout Service**
1.  **Global Methods**: Retrieve all supported payment methods (Credit Card, UPI, Netbanking, etc.).
2.  **User Instruments**: Fetch registered instruments for the logged-in user via the Customer Instrument Service.
3.  **Rule Engine**: Apply dynamic eligibility logic:
    *   Merchant-specific configurations.
    *   MCC (Merchant Category Code) restrictions.
    *   Network-level restrictions (e.g., Visa/Mastercard specific rules).
    *   Transaction amount limits (e.g., UPI caps).
4.  **Downtime Management**: Filter out instruments currently experiencing downtime.
5.  **Response Aggregation**: Return a consolidated list of eligible payment methods and instruments.

**Payment Service**
1.  **Validation**: Verify the integrity of the payment request.
2.  **Smart Routing**: Select the optimal payment vendor based on:
    *   **Availability**: Real-time uptime scores.
    *   **Performance**: Error rates.
    *   **Cost**: Lowest transaction fees.
3.  **Transaction Management**:
    *   Initialize and cache transaction state in Redis.
    *   Execute the payment via the selected vendor.
    *   Persist final status to Postgres.
4.  **Failure Handling**:
    *   Update failure metrics in Redis.
    *   Trigger temporary block-listing for failing instruments or vendors.

### 2.2 Non-Functional Requirements
*   **Availability**: 99.99% for Checkout, 99.95% for Payment processing.
*   **Latency**: Checkout P90 < 120ms.
*   **Scalability**: Support 5,000 checkout req/sec and 2,000 payment req/sec.
*   **Security**: PCI-DSS compliant data handling.
*   **Reliability**: Circuit breakers and automatic vendor fallback.
*   **Consistency**: Strong consistency for transaction states.

### 2.3 Out of Scope
*   Merchant onboarding and management.
*   PCI tokenization (handled by external vault).
*   Post-payment operations (Capture, Settlement, Reconciliation).
*   Refunds and Dispute management.
*   Fraud detection systems.
*   Subscription/Recurring payment management.

## 3. System Architecture (HLD)

### 3.1 Architecture Diagram

```mermaid
graph TD
    User[User / Client] -->|HTTPS| API_GW[API Gateway]
    
    subgraph "Checkout Flow"
        API_GW --> CheckoutSvc[Checkout Service]
        CheckoutSvc --> CIS[Customer Instrument Service]
        CheckoutSvc --> Rules[Rule Engine (JEasy Rules)]
        CheckoutSvc --> Downtime[Downtime Mgmt System]
        CheckoutSvc --> Redis_Inst[(Redis: Instrument Cache)]
    end
    
    subgraph "Payment Flow"
        API_GW --> PaymentSvc[Payment Service]
        PaymentSvc --> VendorHealth[Vendor Availability Service]
        PaymentSvc --> Pricing[Pricing Service]
        PaymentSvc --> Redis_Txn[(Redis: Txn Cache)]
        PaymentSvc --> DB[(Postgres: Txn Store)]
        
        PaymentSvc --> Vendor1[PayU]
        PaymentSvc --> Vendor2[Razorpay]
        PaymentSvc --> Vendor3[Internal PG]
    end
```

### 3.2 Component Descriptions

| Component | Responsibility |
| :--- | :--- |
| **API Gateway** | Entry point for all requests. Handles routing, authentication, and rate limiting. |
| **Checkout Service** | Orchestrator for the checkout experience. Aggregates data from CIS, Rule Engine, and Downtime systems to present valid payment options. |
| **Payment Service** | Core transaction processor. Handles validation, vendor selection (smart routing), execution, and state management. |
| **Customer Instrument Service** | Manages user's saved payment instruments (cards, VPAs). |
| **Rule Engine** | Executes business logic for payment eligibility (Merchant rules, MCC, Limits). Uses **JEasy Rules**. |
| **Downtime Management** | Monitors and reports real-time outages for banks and networks. |
| **Vendor Availability Service** | Provides health metrics (uptime, error rates) for payment gateways. |
| **Redis** | **Cache**: Stores temporary transaction state, instrument eligibility, and block-lists. <br> **Locking**: Distributed locks for idempotency. |
| **Postgres** | **System of Record**: Stores all transaction data, pricing models, and audit logs. |

## 4. Data Model (LLD)

### 4.1 Database Schema

**PaymentMethod**
*Defines global supported payment methods.*
```sql
CREATE TABLE payment_methods (
    method_id VARCHAR(50) PRIMARY KEY, -- e.g., 'UPI', 'CREDIT_CARD'
    method_name VARCHAR(100),
    supported_networks TEXT[], -- Array of supported networks e.g., ['VISA', 'MASTERCARD']
    active BOOLEAN DEFAULT TRUE
);
```

**PaymentInstrument**
*Stores user-specific payment details (tokenized).*
```sql
CREATE TABLE payment_instruments (
    instrument_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    method_id VARCHAR(50) REFERENCES payment_methods(method_id),
    masked_details VARCHAR(50), -- e.g., '**** 1234'
    network VARCHAR(50),
    status VARCHAR(20) -- 'ACTIVE', 'BLOCKED', 'DISABLED'
);
```

**Transaction**
*Records the state of every payment attempt.*
```sql
CREATE TABLE transactions (
    txn_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    instrument_id UUID,
    method_id VARCHAR(50),
    amount DECIMAL(15, 2),
    currency VARCHAR(3) DEFAULT 'INR',
    vendor_id VARCHAR(50),
    status VARCHAR(20), -- 'INITIATED', 'SUCCESS', 'FAILED'
    failure_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**PricingModel**
*Configuration for vendor routing logic.*
```sql
CREATE TABLE pricing_models (
    vendor_id VARCHAR(50),
    min_amount DECIMAL(15, 2),
    max_amount DECIMAL(15, 2),
    fee_percent DECIMAL(5, 2),
    fixed_fee DECIMAL(15, 2),
    PRIMARY KEY (vendor_id, min_amount, max_amount)
);
```

**VendorHealth**
*Real-time health metrics (likely updated via background jobs).*
```sql
CREATE TABLE vendor_health (
    vendor_id VARCHAR(50) PRIMARY KEY,
    uptime_score DECIMAL(5, 2), -- 0.00 to 100.00
    error_rate DECIMAL(5, 2),
    is_down BOOLEAN DEFAULT FALSE,
    last_updated TIMESTAMP
);
```

**MerchantPaymentConfig**
*Merchant-specific payment acceptance configuration. Populated during merchant onboarding by external team.*
```sql
CREATE TABLE merchant_payment_config (
    config_id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    method_id VARCHAR(50) REFERENCES payment_methods(method_id),
    supported_networks TEXT[], -- e.g., ['VISA', 'MASTERCARD', 'RUPAY']
    min_amount DECIMAL(15, 2),
    max_amount DECIMAL(15, 2),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(merchant_id, method_id)
);

-- Example data:
-- Merchant A accepts Credit Cards (Visa, Mastercard only), UPI, and Netbanking
-- Merchant B accepts only UPI and Wallets (Paytm, PhonePe)
```

### 4.2 Merchant Onboarding Integration

**External Team Responsibility:**
The merchant onboarding process is handled by a separate team. During onboarding, they collect:
- Merchant business details (name, MCC, address, etc.)
- Payment acceptance preferences (which payment methods to enable)
- Network restrictions (e.g., only Visa/Mastercard, no Amex)
- Transaction limits per payment method

**Integration Point:**
The onboarding team sends merchant payment configuration data to our system via:
1. **Async Event** (Kafka topic: `merchant.onboarded`)
2. **Webhook** to our internal API: `POST /internal/merchant-config`
3. **Batch Upload** (CSV/JSON file processing)

**Our Responsibility:**
- Consume merchant configuration data
- Store in `merchant_payment_config` table
- Use this config in Rule Engine for checkout eligibility

**Mock Service:**
For development/testing, we implement `MockMerchantConfigService` that returns hardcoded configurations for test merchant IDs.

## 5. Detailed Design & Algorithms

### 5.1 Checkout Flow
**Endpoint**: `GET /checkout`

1.  **Load Methods**: Retrieve active global payment methods from Config/DB.
2.  **Fetch Instruments**: Query *Customer Instrument Service* for `user_id`.
3.  **Apply Rules**: Send context (`merchantId`, `mcc`, `amount`, `instruments`) to *Rule Engine*.
    *   *Algorithm*: See Section 5.3.
4.  **Check Downtime**: Query *Downtime Service* for any global or bank-specific outages.
5.  **Aggregation**:
    *   Filter out instruments rejected by Rules or Downtime.
    *   Map remaining instruments to their respective Payment Methods.
    *   Construct `CheckoutResponse`.

### 5.2 Make Payment Flow
**Endpoint**: `POST /payment/pay`

1.  **Validation**: Ensure `method_id` and `instrument_id` are valid and belong to the user.
2.  **Smart Routing (Vendor Selection)**:
    *   Fetch candidates from *Vendor Availability Service*.
    *   **Filter**: Remove vendors with `is_down = true`.
    *   **Sort**:
        1.  Primary: `uptime_score` (DESC)
        2.  Secondary: `error_rate` (ASC)
        3.  Tertiary: `fee` (ASC) calculated via *Pricing Service*.
    *   **Select**: Top candidate.
3.  **Initialization**:
    *   Generate `txn_id`.
    *   Store initial state in Redis: `SET txn:{txn_id} {status: 'INITIATED', ...} TTL 15min`.
4.  **Execution**: Call selected Vendor's API.
5.  **Completion**:
    *   **Success**:
        *   Update Redis status to `SUCCESS`.
        *   Async write to Postgres `transactions` table.
    *   **Failure**:
        *   Update Redis status to `FAILED` with `failure_reason`.
        *   **Feedback Loop**: Increment error count for Vendor/Instrument in Redis block-list.
        *   If Instrument error threshold breached -> Block Instrument.
        *   If Vendor error threshold breached -> Block Vendor.

### 5.3 Rule Engine Algorithm
**Pseudo-code for `evaluateInstrumentEligibility`**

```java
function evaluateInstrumentEligibility(Request request, Instrument instrument) {
    
    // 1. Fetch Merchant-Specific Configuration
    MerchantPaymentConfig merchantConfig = 
        merchantConfigService.getConfig(request.merchantId, request.methodId);
    
    if (merchantConfig == null || !merchantConfig.isEnabled()) {
        return Reject("Payment method not enabled for this merchant");
    }

    // 2. MCC (Merchant Category Code) Restrictions
    // e.g., Gambling MCCs (6011) might block Credit Cards
    if ("6011".equals(request.mcc) && "CREDIT_CARD".equals(request.methodId)) {
        return Reject("Credit Cards not allowed for this Merchant Category");
    }

    // 3. Network Compatibility (using MERCHANT config, not global method)
    if (instrument != null && merchantConfig.getSupportedNetworks() != null) {
        if (!merchantConfig.getSupportedNetworks().contains(instrument.getNetwork())) {
            return Reject("Network " + instrument.network + " not supported by merchant");
        }
    }

    // 4. Amount Limits (merchant-specific limits)
    if (request.amount < merchantConfig.getMinAmount() || 
        request.amount > merchantConfig.getMaxAmount()) {
        return Reject("Amount outside merchant's accepted range");
    }
    
    // 5. Global Method Limits (e.g., UPI regulatory limits)
    if ("UPI".equals(request.methodId) && request.amount > 100000) {
        return Reject("Amount exceeds UPI regulatory limit of 1,00,000");
    }

    return Allow();
}
```

## 6. Technology Stack

*   **Language**: Java (JDK 17+)
*   **Framework**: Spring Boot 3.x
*   **Build Tool**: Gradle
*   **Rule Engine**: JEasy Rules
*   **API Spec**: OpenAPI 3.1
*   **Database**: PostgreSQL (Primary), Redis (Cache/KV)
*   **Message Broker**: Kafka (for async events like analytics, notifications)
*   **Containerization**: Docker

---

## 10. Test Cases

### 10.1 Checkout API Test Cases

#### Valid Scenario Tests
1. **Should return all global payment methods**
    - Input: valid user, merchant, amount
    - Expected: methods list populated

2. **Should merge user instruments with global methods**
    - Input: user with CC + UPI saved
    - Expected: CC/UPI sections include user instruments

3. **Should apply merchant rules correctly**
    - Merchant disallows UPI → UPI must appear with disabled=true & reason

4. **Should reject method if MCC disallows a payment type**

5. **Should enforce amount rules**
    - UPI amount > ₹100000 → mark disabled

6. **Should disable an instrument when downtime system marks it degraded**

7. **Should allow additional new instrument if method allows new additions**

#### Negative Scenarios
1. Missing mandatory fields → validation error
2. Invalid merchant Id → reject
3. Instrument service failure → fallback to empty list
4. Downtime management failure → fail open (do not disable method)

---

### 10.2 Vendor Availability Test Cases

1. **Should return full vendor health info**
2. **Should correctly mark vendor isDown=true**
3. **Should handle partial failures (one vendor failing)**
4. **Should fail gracefully on dependency timeout**

---

### 10.3 Payment API Test Cases

#### Valid Scenarios
1. Should select vendor with highest uptime
2. Should pick cheapest vendor when uptime is equal
3. Should cache transaction before vendor call
4. Should persist successful transaction and clear redis
5. Should retry with alternate vendor if selected vendor fails
6. Should block instrument when error is instrument-specific
7. Should block vendor when vendor-specific error

#### Negative Scenarios
1. Instrument not allowed by rules → reject
2. Vendor availability service timeout → use cached vendor if exists
3. Pricing service failure → fallback to vendor with best uptime
4. Invalid instrument ID → error
5. Redis failure → return graceful degraded error (non-blocking path)

---

### 10.4 End-to-End Flow Tests

1. Full checkout → user selects method → payment success
2. Checkout → payment vendor down → vendor fallback success
3. Checkout → user selects blocked instrument → error
4. Multi-attempt payment:
    - Attempt 1 fails (instrument error) → block instrument
    - Attempt 2 with same instrument → immediately reject
4. Downtime=true in checkout → instrument disabled in UI

---

### 10.5 Load/Test Cases
1. Checkout P90 under 120ms at 5k RPS
2. Payment P95 < 200ms vendor selection
3. Redis latency < 5ms
4. Vendor health service < 20ms per call

---

### 10.6 Chaos/Failure Tests
1. Vendor API down
2. Downtime system down
3. Redis unavailable
4. Postgres read-replica lag
5. Pricing DB corrupted
6. Duplicate payment attempts

---
