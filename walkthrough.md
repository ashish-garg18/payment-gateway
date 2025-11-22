# Payment Gateway Implementation Walkthrough

## Overview
We have implemented a robust Payment Gateway system with two main APIs:
1.  **Checkout API**: `/api/v1/checkout` - Aggregates payment methods and applies rules.
2.  **Payment API**: `/api/v1/payment/pay` - Orchestrates payments with smart routing.

## Project Structure
-   `src/main/java/com/paymentgateway/`
    -   `controller/`: REST Controllers (`CheckoutController`, `PaymentController`)
    -   `service/`: Business Logic (`CheckoutService`, `PaymentService`, `RuleEngineService`, etc.)
    -   `repository/`: Data Access (`PaymentMethodRepository`, `TransactionRepository`, etc.)
    -   `model/`: JPA Entities (`PaymentMethod`, `Transaction`, `VendorHealth`, etc.)
    -   `dto/`: Data Transfer Objects (`CheckoutResponse`, `PaymentRequest`, etc.)

## Key Features Implemented
-   **Rule Engine**: `SimpleRuleEngineService` implements logic for Merchant, MCC, Network, and Amount checks.
-   **Smart Routing**: `PaymentService` selects vendors based on Uptime, Error Rate, and Cost.
-   **Downtime Handling**: `MockDowntimeService` simulates bank downtime checks.
-   **Pricing Logic**: `PricingServiceImpl` calculates fees based on transaction amount.

## How to Run
1.  **Prerequisites**:
    -   Java 17+
    -   PostgreSQL (running on localhost:5432)
    -   Redis (running on localhost:6379)
    -   Kafka (running on localhost:9092)

2.  **Build**:
    ```bash
    ./gradlew clean build
    ```

3.  **Run**:
    ```bash
    ./gradlew bootRun
    ```

## Verification Steps
### 1. Checkout API
**Request**:
```http
GET /api/v1/checkout?merchantId=...&userId=...&amount=100.00&mcc=6011
```
**Expected Result**: JSON response with list of payment methods. If MCC is 6011, Credit Cards might be disabled based on the rule.

### 2. Payment API
**Request**:
```http
POST /api/v1/payment/pay
{
  "merchantId": "...",
  "userId": "...",
  "amount": 500.00,
  "methodId": "UPI",
  "instrumentId": "..."
}
```
**Expected Result**: JSON response with `txnId` and status `SUCCESS` (if mock vendor succeeds).

## Next Steps
-   Configure real database connections in `application.yml`.
-   Replace Mock services (`MockCustomerInstrumentService`, `MockDowntimeService`) with real integrations.
-   Implement actual Vendor API calls in `PaymentService`.
