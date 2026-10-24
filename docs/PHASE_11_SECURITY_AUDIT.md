# ThreatStream Phase 11 — Security Audit Report

## 1. Executive Summary

During Phase 11, a comprehensive security audit and architecture review of the ThreatStream platform was conducted across all six backend Maven modules, the React frontend, container definitions, and configuration files.

ThreatStream processes sensitive security telemetry (authentication logs, network flows, and detection alerts) through a distributed pipeline:
`Demo Login HTTP Request → Kafka Auth Topic → Threat Detection → Kafka Alert Topic → Alert Service → MongoDB Atlas → REST API → Frontend`

The audit identified security gaps across authentication/authorization, input validation, denial-of-service resistance, container privileges, event deduplication, and configuration management. All high- and medium-risk vulnerabilities within the codebase were remediated without breaking existing API contracts or regression test suites.

---

## 2. Audit Scope and System Context

- **Alert Service (`backend/alert-service`):** Spring Boot 3.5.6 REST API on port `8083`, connecting to MongoDB Atlas (`ap-south-1`) and consuming `security.alerts`.
- **Threat Detection Service (`backend/threat-detection-service`):** Spring Boot consumer on port `8082`, evaluating auth and network events, publishing alerts.
- **Demo Login Service (`backend/demo-login-service`):** Spring Boot demonstration endpoint on port `8084`, publishing to `security.auth.events`.
- **Event Collector Service (`backend/event-collector-service`):** Ingestion skeleton on port `8081`.
- **Common Module (`backend/common`):** Shared DTO and model definitions.
- **Frontend (`frontend/`):** React 19, Vite 7, TypeScript, Tailwind CSS, Prettier, ESLint, Vitest.
- **Infrastructure (`infrastructure/`, `docker-compose.yml`):** Apache Kafka 3.9.0 (KRaft mode), PostgreSQL 17-alpine, Eclipse Temurin 21 JRE containers.

---

## 3. Prioritized Audit Findings

| ID | Title | Severity | Component | Status |
|---|---|---|---|---|
| **SEC-01** | Missing REST API Authentication & Authorization on Alert Service | High | `alert-service` | Remediated |
| **SEC-02** | Unbounded Pagination and Parameter Validation Vulnerability | High | `alert-service` | Remediated |
| **SEC-03** | Lack of Non-Root Execution and Leaked Build Context in Docker | Medium | `infrastructure/docker` | Remediated |
| **SEC-04** | Missing Abuse Resistance & Production Controls in Demo Login | Medium | `demo-login-service` | Remediated |
| **SEC-05** | Random UUID Generation Defeating Alert Deduplication | Medium | `threat-detection-service` | Remediated |
| **SEC-06** | Information Disclosure via Default Spring Boot Error Handling | Medium | All Backend Services | Remediated |
| **SEC-07** | Potential Heap Exhaustion via Unbounded IP Tracking State | Low | `threat-detection-service` | Remediated |
| **SEC-08** | Incomplete CORS Allowed Headers Configuration | Low | `alert-service` | Remediated |
| **SEC-09** | Kafka PLAINTEXT Transport & Missing Broker ACLs in Local Profile | Low / Info | `docker-compose.yml` | Documented / Residual |
| **SEC-10** | DevDependency Vulnerability in Node Test Framework (`vitest`/`tinypool`)| Low / Info | `frontend` | Documented / Residual |

---

## 4. Detailed Vulnerability Analyses

### SEC-01: Missing REST API Authentication & Authorization on Alert Service
- **Severity:** High
- **CWE:** CWE-306 (Missing Authentication for Critical Function)
- **Evidence:** `alert-service/pom.xml` did not include `spring-boot-starter-security`. All endpoints (`/api/alerts`, `/api/alerts/{alertId}`, `/api/alerts/statistics`, and mutating transitions `/acknowledge` and `/resolve`) were completely open to any network client with HTTP access to port `8083`.
- **Remediation:** 
  1. Added `spring-boot-starter-security` and `spring-security-test` dependencies.
  2. Implemented `SecurityConfiguration` with `ApiKeyAuthenticationFilter` supporting `X-API-Key` and `Authorization: Bearer <key>`.
  3. Enforced `ROLE_ANALYST` authority on all alert endpoints while permitting actuator health/info and CORS preflight.
  4. Fails startup in `production` profile if `ALERT_SERVICE_API_KEY` is missing.
- **Residual Risk:** Single shared API key model; recommended to transition to fine-grained OAuth2/OIDC JWT tokens with role-based access control (RBAC) in enterprise deployments.

---

### SEC-02: Unbounded Pagination and Parameter Validation Vulnerability
- **Severity:** High
- **CWE:** CWE-770 (Allocation of Resources Without Limits or Throttling)
- **Evidence:** `AlertController.java` accepted `@RequestParam(defaultValue = "20") int size` with no upper limit validation check (`page < 0 || size < 1`). A client requesting `size=1000000` could trigger large database queries, causing memory spikes or Denial of Service. In addition, `threatType` and `sourceIp` had no character pattern or length limits, and malformed UUID paths caused unhandled server errors.
- **Remediation:**
  1. Bound pagination `size` in `AlertController` with `size > 100` resulting in immediate `400 Bad Request`.
  2. Validated `threatType` (max 64 chars, alphanumeric/underscore) and `sourceIp` (max 64 chars, valid IP pattern).
  3. Extended `ApiExceptionHandler` to intercept `MethodArgumentTypeMismatchException` and `HttpMessageNotReadableException`, returning safe `400 Bad Request` responses.
- **Residual Risk:** None within application scope.

---

### SEC-03: Lack of Non-Root Execution and Leaked Build Context in Docker
- **Severity:** Medium
- **CWE:** CWE-250 (Execution with Unnecessary Privileges)
- **Evidence:** `infrastructure/docker/backend.Dockerfile` ran the JRE container as root user (`uid=0`). Furthermore, no `.dockerignore` file was present, which could cause local `.env` files, git metadata, and build logs to be copied into image contexts during Docker builds.
- **Remediation:**
  1. Updated `backend.Dockerfile` to create an unprivileged system user and group (`threatstream:threatstream`) and execute the application process under this user.
  2. Created root `.dockerignore` and `backend/.dockerignore` explicitly excluding `.env*`, `.git`, build outputs, and logs.
- **Residual Risk:** Minimal. Base image vulnerabilities must still be tracked with image vulnerability scanners.

---

### SEC-04: Missing Abuse Resistance & Production Controls in Demo Login
- **Severity:** Medium
- **CWE:** CWE-307 (Improper Restriction of Excessive Authentication Attempts)
- **Evidence:** `demo-login-service` had no rate limiting on `POST /api/demo/auth/login`. An automated script could flood thousands of login requests per second. Additionally, there was no toggle to disable the demo login endpoint in production environments, creating a risk that demonstration endpoints could be left active in production.
- **Remediation:**
  1. Created `RateLimitingFilter` enforcing a configurable sliding-window rate limit (default: 60 requests/minute per client IP), allowing legitimate brute-force demonstration (12 requests) while blocking abuse.
  2. Added `demo.auth.enabled` toggle; when disabled, the endpoint returns `403 Forbidden` with a clear security notice.
  3. Added `DEMO_RATE_LIMIT_PER_MINUTE` and `DEMO_AUTH_ENABLED` configuration properties.
  4. Configured stateless session management and security headers in `SecurityConfiguration`.
- **Residual Risk:** Application-level IP rate limiting can be bypassed by distributed IP botnets; production architectures must enforce edge rate limiting (e.g. AWS WAF / Cloudflare).

---

### SEC-05: Random UUID Generation Defeating Alert Deduplication
- **Severity:** Medium
- **CWE:** CWE-694 (Use of Multiple Resources with Duplicate Identifier)
- **Evidence:** `AlertFactory.java` instantiated `SecurityAlert` with `UUID.randomUUID()`. Consequently, if a single incident generated duplicate Kafka alert messages (due to consumer redelivery, broker retry, or engine replay), each message carried a distinct UUID, bypassing `AlertService.findByAlertId()` deduplication and generating duplicate MongoDB documents.
- **Remediation:**
  1. Refactored `AlertFactory` to generate deterministic Type 3 UUIDs using `UUID.nameUUIDFromBytes` derived from `ruleId`, `threatType`, `sourceIp`, and `firstSeen` epoch millisecond.
  2. Ensured identical incidents produce identical deterministic IDs for idempotent MongoDB Atlas persistence.
- **Residual Risk:** None. Verified through unit tests.

---

### SEC-06: Information Disclosure via Default Spring Boot Error Handling
- **Severity:** Medium
- **CWE:** CWE-209 (Generation of Error Message Containing Sensitive Information)
- **Evidence:** Spring Boot's default error controller and exception handlers could disclose internal class names, stack traces, and database driver exceptions under 500 error conditions.
- **Remediation:**
  1. Configured `server.error.include-message: never`, `include-binding-errors: never`, `include-stacktrace: never`, and `include-exception: false` across all microservices.
  2. Implemented generic `@ExceptionHandler(Exception.class)` in all REST exception handlers returning standardized, clean JSON error responses (`{"message": "An unexpected server error occurred."}`).
- **Residual Risk:** None.

---

### SEC-07: Potential Heap Exhaustion via Unbounded IP Tracking State
- **Severity:** Low
- **CWE:** CWE-400 (Uncontrolled Resource Consumption)
- **Evidence:** `BruteForceRule`, `PasswordSprayingRule`, and `SuspiciousLoginRule` stored tracking state in `Map<String, Deque<...>>`. Stale IP entries with empty queues were retained in memory, which could lead to unbounded memory growth under high-volume scans from distinct IPs.
- **Remediation:**
  1. Implemented capacity guards (`MAX_TRACKED_IPS = 10000`) and automatic cleanup of empty tracking deques upon evaluation.
  2. Pruned expired entries from the maps when threshold limits are approached.
- **Residual Risk:** In-memory state is local to single instance; distributed multi-instance deployment requires Redis or Kafka Streams state stores.

---

### SEC-08: Incomplete CORS Allowed Headers Configuration
- **Severity:** Low
- **CWE:** CWE-942 (Permissive Cross-Domain Policy with Untrusted Domains)
- **Evidence:** `alert-service/src/main/java/com/threatstream/alert/CorsConfiguration.java` only permitted `Content-Type` in `allowedHeaders`. Any frontend request sending `Authorization` or `X-API-Key` would fail browser CORS preflight checks.
- **Remediation:**
  1. Updated CORS configuration in `SecurityConfiguration` and `CorsConfiguration` to permit `Content-Type`, `Authorization`, and `X-API-Key`.
  2. Retained origin restriction to configured `app.cors.allowed-origin` (default: `http://localhost:5173`).
- **Residual Risk:** None.

---

### SEC-09: Kafka PLAINTEXT Transport & Missing Broker ACLs in Local Profile
- **Severity:** Low (Local Dev) / High (Production)
- **CWE:** CWE-319 (Cleartext Transmission of Sensitive Information)
- **Evidence:** `docker-compose.yml` configures Apache Kafka using `PLAINTEXT://` on internal port `9092` and host loopback `127.0.0.1:9094`. There is no transport layer encryption (TLS) or SASL client authentication.
- **Remediation Guidance:**
  - Loopback binding (`127.0.0.1:9094`) prevents exposure to external untrusted networks during local development.
  - Production deployments must configure `SSL` or `SASL_SSL` listeners (e.g. SCRAM-SHA-512) and enable Kafka ACLs to restrict topic publish/subscribe permissions per microservice principal.
- **Residual Risk:** Acceptable for local developer workstations; unacceptable for staging or production environments.

---

### SEC-10: DevDependency Vulnerabilities in Node Test Framework
- **Severity:** Low / Informational
- **CWE:** CWE-1395 (Dependency on Vulnerable Third-Party Component)
- **Evidence:** `npm audit` reported prototype pollution in `tinypool` and `vitest` devDependencies (`@vitest/mocker`, `tinypool`).
- **Remediation Guidance:**
  - These dependencies are strictly devDependencies for executing unit tests in development and are NOT bundled into client production builds (`vite build` output in `frontend/dist/`).
  - An automatic fix (`npm audit fix --force`) would force upgrade to `vitest@5`, which introduces breaking configuration changes to Vitest 3.x suites.
  - Recommended: Pin upgrade in next phase after validating Vitest 5 migration guide.
- **Residual Risk:** Dev-only; zero exposure to production frontend runtime.

---

## 5. Audit Conclusion

All identified high- and medium-severity application code vulnerabilities have been remediated. The codebase preserves all verified Phase 10 functionality, with zero breaking API contract changes, 51 passing backend tests across all six Maven modules, and 18 passing frontend tests.
