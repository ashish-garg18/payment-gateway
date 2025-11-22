# Payment Gateway - Project Brief

## Project Overview

A comprehensive payment gateway system that orchestrates payment processing across multiple payment service providers (PSPs) with intelligent routing, merchant configuration management, and robust retry mechanisms.

## Core Objectives

1. **Multi-PSP Orchestration**: Support multiple payment vendors (PayU, Razorpay, etc.) with intelligent routing
2. **Smart Routing**: Route transactions to optimal vendors based on health scores, error rates, and fees
3. **Merchant Configuration**: Flexible per-merchant payment method configuration
4. **Checkout Experience**: Provide eligible payment methods and user instruments with rule-based filtering
5. **Retry Handling**: Comprehensive retry mechanism for vendor failures and instrument declines
6. **Idempotency**: Ensure safe retries without duplicate payments
7. **OpenAPI Integration**: Auto-generate API interfaces and models from OpenAPI specification

## Key Features

### 1. Checkout API
- Fetch available payment methods for a merchant
- Apply merchant-specific rules (amount limits, MCC restrictions)
- Return user's saved instruments with eligibility status
- Support instrument downtime detection
- Idempotent checkout sessions

### 2. Payment Processing
- Smart vendor selection based on health metrics
- Automatic vendor failover on errors
- Instrument decline handling
- Transaction state management
- Comprehensive logging and metrics

### 3. Merchant Configuration
- Per-merchant payment method enablement
- Custom routing rules and limits
- MCC-based restrictions
- Priority and fee configuration

### 4. Retry Strategy
- Vendor failure tracking with Redis
- Declined instrument exclusion
- Automatic retry with different vendors
- User-driven retry with different instruments

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Database**: H2 (in-memory for development)
- **Caching**: Redis (for retry state management)
- **API Specification**: OpenAPI 3.0
- **Code Generation**: OpenAPI Generator
- **Metrics**: Micrometer
- **Build Tool**: Gradle

## Architecture Highlights

### API Layer
- Controllers implement OpenAPI-generated interfaces
- Automatic request/response validation
- Header-based user identification
- Idempotency key support

### Service Layer
- `CheckoutService`: Payment method orchestration and eligibility
- `PaymentService`: Transaction processing and vendor routing
- `RuleEngineService`: Business rule evaluation
- `VendorAvailabilityService`: Vendor health monitoring
- `DowntimeService`: Instrument downtime detection

### Data Models
- All DTOs generated from OpenAPI specification
- Entity models for database persistence
- Separate domain models for internal logic

## API Endpoints

### Checkout
```
POST /api/v1/checkout
Headers: X-User-Id, X-Idempotency-Key
Request: CheckoutRequest (checkoutId, merchant, payment)
Response: CheckoutResponse (paymentId, paymentMethods)
```

### Payment
```
POST /api/v1/payment/pay
Headers: X-User-Id
Request: PaymentRequest (paymentId, merchant, payment, instrument)
Response: PaymentResponse (paymentId, txnId, status)
```

### Merchant Configuration
```
POST /api/v1/payments/onboard
GET /api/v1/payments/config
GET /api/v1/payments/config/{merchantId}/{methodId}
```

## Design Principles

1. **Idempotency First**: All operations designed for safe retries
2. **State Management**: Redis for distributed state across retries
3. **Fail Fast**: Early validation and clear error messages
4. **Observability**: Comprehensive logging with correlation IDs
5. **Type Safety**: OpenAPI-generated models ensure contract compliance
6. **Separation of Concerns**: Clear boundaries between layers

## Future Enhancements

- [ ] 3DS authentication support
- [ ] Webhook handling for async payment updates
- [ ] Advanced fraud detection
- [ ] Multi-currency support
- [ ] Payment analytics dashboard
- [ ] Circuit breaker pattern for vendor calls
- [ ] Rate limiting per merchant
- [ ] Tokenization service integration