# Payment Gateway – Checkout to Payment Flow  
High Level Design (HLD) & Low Level Design (LLD)

---

## 1. Introduction

This document defines the design for a Payment Gateway Checkout & Payment Orchestration Platform.  
The system provides:

- A **Checkout API** that returns:
  - All supported payment methods
  - User’s registered payment instruments (via Customer Instrument Service)
  - Rule-engine driven eligibility (merchant rules, MCC rules, network rules, amount caps)
  - Instrument uptime/downtime checks
  - Final list of enabled/disabled payment methods & mapped instruments

- A **Make Payment API** that:
  - Validates user-selected instrument
  - Selects a vendor based on availability + pricing
  - Initiates transaction, caches state in Redis
  - Handles failures with intelligent retry & block-list logic

---

## 2. Functional Requirements

### Checkout
1. Return global supported payment methods.  
2. Fetch user’s registered instruments from Customer Instrument Service.  
3. Apply eligibility rules:
   - Merchant-level accepted methods  
   - MCC restrictions  
   - Instrument network restrictions  
   - Transaction amount limits (e.g., UPI max ₹1,00,000)  
4. Call downtime management system to fetch instrument operational status.  
5. Merge global methods + user instruments + rule results + downtime results.

### Payment
1. Validate payment request.  
2. Perform vendor availability check (PayU / Razorpay / Internal).  
3. Select vendor based on:
   - Uptime score
   - Error rate
   - Pricing model (cheapest vendor)  
4. Cache transaction initialization in Redis.  
5. Execute vendor API call.  
6. On failure update Redis with failure metadata:
   - Instrument failures → block instrument  
   - Vendor failures → block vendor  
7. On success finalize transaction & persist.

---

## 3. Out of Scope

- Merchant onboarding  
- Full PCI tokenization (assumed external)  
- Payment capture/settlement/reconciliation  
- Refunds & disputes  
- Fraud detection  
- Card addition/tokenization flows  
- Subscription/recurring payments  

---

## 4. Non Functional Requirements

- **Availability**: Checkout 99.99%, Payment 99.95%  
- **Latency**: Checkout P90 < 120ms  
- **Scalability**: 5k checkout req/sec, 2k payment req/sec  
- **Security**: PCI-DSS compatible  
- **Reliability**: Vendor fallback, circuit breakers  
- **Consistency**: Strong consistency for transaction state  

---

## 5. High Level Design (HLD)

### Architecture Diagram (Text Form)

User → API Gateway → Checkout Service
|--> Customer Instrument Service
|--> Rule Engine (JEasy Rules)
|--> Downtime Management System
|--> Redis (Instrument Cache)
v
Checkout Response

User → API Gateway → Payment Service
|--> Vendor Availability Service
|--> Pricing Service
|--> Vendor Integrations (PayU/Razorpay/Internal PG)
|--> Redis (Transaction Cache)
|--> Postgres


### Components

#### API Gateway  
- Routing  
- Authentication  
- Rate limits  

#### Checkout Service  
- Orchestrates payment methods retrieval  
- Fetches user instruments  
- Applies rules via JEasy Rules  
- Calls downtime service  
- Returns structured method + instrument eligibility  

#### Customer Instrument Service  
- Stores user’s added payment instruments  

#### Rule Engine (JEasy Rules)  
- Merchant rules  
- MCC rules  
- Network/issuer rules  
- Amount limits  

#### Downtime Management System  
- Real-time error rate monitoring for instrument types  

#### Payment Service  
- Validates payment request  
- Vendor selection (availability + pricing)  
- Transaction caching & error tracking  
- Vendor API integration  

#### Vendor Availability Service  
- Provides vendor uptime/error rate  

#### Postgres  
- Durable transaction store  

#### Redis  
- Transaction cache  
- Block-lists (instrument/network/vendor)  

---

## 6. LLD Data Model

### **PaymentMethod**
- method_id  
- method_name  
- supported_networks  
- active  

### **PaymentInstrument**
- instrument_id  
- user_id  
- method_id  
- masked_details  
- network  
- status (active/blocked/disabled)  

### **CheckoutResponseMethod**
- method_id  
- enabled  
- reason_if_disabled  
- allow_add_new  
- user_instruments[]  

### **Transaction**
- txn_id  
- user_id  
- merchant_id  
- instrument_id  
- method_id  
- amount  
- vendor_id  
- status (initiated/success/failed)  
- failure_reason  
- created_at  

### **PricingModel**
- vendor_id  
- min_amount  
- max_amount  
- fee_percent  
- fixed_fee  

### **VendorHealth**
- vendor_id  
- uptime_score  
- error_rate  
- is_down  

---

## 7. Detailed Design

### Checkout Flow
1. Client → `/checkout`  
2. Load global payment methods (config/DB)  
3. Fetch user instruments via Customer Instrument Service  
4. Send merchantId, mcc, amount, instrument metadata to Rule Engine  
5. Rule Engine returns allow/reject per method/instrument  
6. Call Downtime Service  
7. Merge:
   - global methods  
   - rule results  
   - downtime results  
   - user instrument mapping  
8. Return final CheckoutResponse  

### Make Payment Flow
1. Client → `/payment/pay`  
2. Validate method/instrument eligibility  
3. Call Vendor Availability Service  
4. Select vendor by:
   - Highest uptime  
   - Lowest error rate  
   - Lowest fee (via Pricing Service)  
5. Cache txn_init in Redis  
6. Execute vendor API  
7. On success → persist transaction and clear Redis  
8. On failure:
   - Update Redis with error  
   - If vendor error → block vendor  
   - If instrument error → block instrument  

---

## 8. Rule Engine Flow (with pseudo algorithm)

### Inputs
- merchant_accepted_methods  
- mcc  
- instrument network  
- amount  

### Rule Algorithm
```pseudo
function evaluateInstrumentEligibility(request):
  
  if request.methodId not in merchantAcceptedMethods:
      reject("Merchant does not accept this method")

  if mcc in mccRestrictedMap AND request.methodId in mccRestrictedMap[mcc]:
      reject("MCC restricted method")

  if request.instrumentNetwork NOT IN method.supportedNetworks:
      reject("Network not supported")

  if method == UPI AND amount > 100000:
      reject("UPI amount limit exceeded")

  allow()


9. Tech Stack & Deployment
Backend
Kotlin
Spring Boot
Gradle
JEasy Rules
OpenAPI 3.1
Infra
Docker
Kubernetes
Redis (transaction + rule caching)
Postgres (transactions & pricing)
External Services
Customer Instrument Service
Vendor Availability
Downtime Management System
Payment Vendors (PayU, Razorpay, internal)