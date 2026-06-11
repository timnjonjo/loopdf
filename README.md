# RDAS — Reference Data Aggregation Service

A Spring Boot 3 microservice that acts as a single source of truth for country, currency, language, and geographical reference data. RDAS wraps a third-party SOAP service behind clean REST/JSON APIs, adding caching, pagination, filtering, sorting, resilience, and auditability.

---

## Table of Contents

1. [Background](#background)
2. [Architecture](#architecture)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Getting Started](#getting-started)
6. [Configuration](#configuration)
7. [API Reference](#api-reference)
8. [Caching Strategy](#caching-strategy)
9. [Resilience](#resilience)
10. [Running Tests](#running-tests)
11. [Kubernetes Deployment](#kubernetes-deployment)
12. [Kubernetes Troubleshooting](#kubernetes-troubleshooting)
13. [Engineering Discussion](#engineering-discussion)

---

## Background

Multiple consumer channels (Mobile Apps, Web Apps, Partner APIs, Internal Portals) previously consumed a third-party SOAP service directly. This caused:

- Inconsistent responses across channels
- Poor performance due to repeated SOAP calls per request
- No pagination or filtering support
- SOAP credentials exposed across multiple applications
- No centralized caching or auditability

RDAS solves this by becoming the **single integration point** — consuming SOAP internally, caching aggressively, and exposing modern REST/JSON APIs to all consumers.

**Upstream SOAP service:**
```
http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL
```

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Consumers                             │
│     Mobile App │ Web App │ Partner API │ Ops Portal          │
└─────────────────────────┬────────────────────────────────────┘
                          │  REST / JSON
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                  RDAS  (Spring Boot 3)                       │
│                                                              │
│  ┌─────────────────┐     ┌──────────────────────────────┐   │
│  │  REST Controllers│     │  GlobalExceptionHandler      │   │
│  │  /countries      │     │  RequestLoggingFilter        │   │
│  │  /continents     │     │  Input Validation            │   │
│  │  /currencies     │     └──────────────────────────────┘   │
│  │  /languages      │                                        │
│  └────────┬─────────┘                                        │
│           │                                                  │
│  ┌────────▼─────────┐     ┌──────────────────────────────┐   │
│  │  CountryService  │     │  ReferenceDataBootstrap      │   │
│  │  (filter/sort/   │     │  (startup + nightly refresh) │   │
│  │   paginate)      │     └──────────────┬───────────────┘   │
│  └────────┬─────────┘                    │                   │
│           │                              │                   │
│  ┌────────▼──────────────────────────────▼───────────────┐   │
│  │              ReferenceDataStore (in-memory)           │   │
│  │              ConcurrentHashMap — thread-safe          │   │
│  └───────────────────────────┬───────────────────────────┘   │
│                              │  (bootstrap only)             │
│  ┌───────────────────────────▼───────────────────────────┐   │
│  │          CountryInfoSoapGateway (interface)           │   │
│  │          CountryInfoSoapGatewayImpl                   │   │
│  │          CircuitBreaker + Retry (Resilience4j)        │   │
│  └───────────────────────────┬───────────────────────────┘   │
└──────────────────────────────┼───────────────────────────────┘
                               │  SOAP / XML
                               ▼
                  CountryInfo SOAP Service (3rd party)
```

**Key design decisions:**

- **Anti-corruption layer** — SOAP is fully hidden behind `CountryInfoSoapGateway`. Controllers and services never touch generated stubs.
- **Bootstrap pattern** — All SOAP calls happen once at startup (and nightly). Zero SOAP calls per REST request.
- **Concurrent bootstrap** — Country detail fetching runs across a bounded thread pool with a `Semaphore` to respect the 100 req/min SOAP rate limit.
- **Fail-fast on startup** — If bootstrap fails, the app throws rather than starting in a broken state.
- **Readiness probe** — Kubernetes will not route traffic until the in-memory store is populated.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Maven |
| SOAP client | JAX-WS RI 4 (stubs generated via `jaxws-maven-plugin`) |
| Cache | Caffeine (in-process) |
| Resilience | Resilience4j (CircuitBreaker, Retry) |
| API docs | SpringDoc OpenAPI 2 (Swagger UI) |
| Observability | Spring Actuator|
| Container | Docker |
| Orchestration | Kubernetes |

---

## Project Structure

```
rdas/
├── src/
│   ├── main/
│   │   ├── java/com/loopdfs/rdas/
│   │   │   ├── RdasApplication.java
│   │   │   ├── config/
│   │   │   │   ├── CacheConfig.java          # Caffeine cache beans with per-type TTLs
│   │   │   │   └── SoapConfig.java           # JAX-WS port with timeout config
│   │   │   ├── controllers/
│   │   │   │   ├── CountryController.java    # GET /api/v1/countries
│   │   │   │   ├── ContinentController.java  # GET /api/v1/continents
│   │   │   │   ├── CurrencyController.java   # GET /api/v1/currencies
│   │   │   │   └── LanguageController.java   # GET /api/v1/languages
│   │   │   ├── services/
│   │   │   │   ├── CountryService.java              # filter / sort / paginate
│   │   │   │   ├── ReferenceDataBootstrapService.java # startup + scheduled refresh
│   │   │   │   └── ReferenceDataStore.java          # in-memory ConcurrentHashMap store
│   │   │   ├── gateway/
│   │   │   │   ├── CountryInfoSoapGateway.java      # interface (anti-corruption layer)
│   │   │   │   └── CountryInfoSoapGatewayImpl.java  # JAX-WS adapter + resilience
│   │   │   ├── domain/
│   │   │   │   ├── Country.java
│   │   │   │   ├── Continent.java
│   │   │   │   ├── Currency.java
│   │   │   │   └── Language.java
│   │   │   ├── dtos/
│   │   │   │   ├── CountryResponse.java
│   │   │   │   ├── CountryDetailResponse.java
│   │   │   │   ├── PagedResponse.java
│   │   │   │   └── ErrorResponse.java
│   │   │   ├── exceptions/
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   └── SoapGatewayException.java
│   │   │   ├── filter/
│   │   │   │   └── RequestLoggingFilter.java
│   │   │   └── health/
│   │   │       └── ReferenceDataHealthIndicator.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── wsdl/
│   │           └── CountryInfoService.wsdl   # local WSDL copy for offline builds
│   └── test/
│       └── java/com/loopdfs/rdas/
│           └── services/
│               └── CountryServiceTest.java
└── deployment-files/
    ├── namespace.yaml
    ├── configmap.yaml
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    └── hpa.yaml
```

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| Docker | 24+ |
| kubectl | 1.28+ |

### 1. Clone the repository

```bash
git clone https://github.com/timnjonjo/loopdf.git
cd loopdf
```

### 2. Generate SOAP stubs

The SOAP client stubs are generated from the WSDL at build time. Run once before opening in your IDE:

```bash
mvn clean generate-sources
```

Generated classes appear in `target/generated-sources/jaxws/com/loopdfs/rdas/soap/generated/`.

> **IntelliJ:** Right-click `target/generated-sources/jaxws` → Mark Directory As → Generated Sources Root

### 3. Build

```bash
mvn clean package
```

### 4. Run locally

```bash
java -jar target/rdas-1.0.0.jar
```

The application starts on `http://localhost:8080`.

On startup, RDAS bootstraps all reference data from the SOAP service. Watch for:

```
INFO  ReferenceDataBootstrapService - Reference data bootstrap complete. countries=250, currencies=175, languages=407
```

### 5. Explore the API

Swagger UI is available at:
```
http://localhost:8080/swagger-ui.html
```

Health check:
```
http://localhost:8080/actuator/health
```

---

## Configuration

All configuration lives in `src/main/resources/application.yml`.

| Property | Default | Description |
|---|---|---|
| `rdas.soap.connect-timeout-ms` | `10000` | SOAP connection timeout |
| `rdas.soap.read-timeout-ms` | `60000` | SOAP read timeout |
| `rdas.cache.country-ttl-hours` | `24` | Country cache TTL |
| `rdas.cache.currency-ttl-hours` | `24` | Currency cache TTL |
| `rdas.cache.language-ttl-hours` | `24` | Language cache TTL |
| `rdas.cache.continent-ttl-hours` | `72` | Continent cache TTL |
| `rdas.bootstrap.refresh-cron` | `0 0 2 * * *` | Nightly refresh schedule (02:00) |

Override any property at runtime:

```bash
java -jar target/rdas-1.0.0.jar \
  --rdas.soap.read-timeout-ms=30000 \
  --rdas.bootstrap.refresh-cron="0 0 3 * * *"
```

---

## API Reference

Full interactive documentation: `http://localhost:8080/swagger-ui.html`

### Search Countries

```
GET /api/v1/countries
```

| Parameter | Type | Required | Description |
|---|---|---|---|
| `search` | string | No | Partial country name match (case-insensitive) |
| `continent` | string | No | ISO continent code e.g. `AF`, `EU`, `AS` |
| `currency` | string | No | ISO 4217 currency code e.g. `KES`, `USD` |
| `language` | string | No | ISO 639-1 language code e.g. `EN`, `SW` |
| `sortBy` | string | No | `name` (default) \| `isoCode` \| `continent` |
| `sortDir` | string | No | `asc` (default) \| `desc` |
| `page` | int | No | Zero-based page index (default `0`) |
| `size` | int | No | Page size 1–100 (default `20`) |

**Example:**
```bash
curl "http://localhost:8080/api/v1/countries?continent=AF&currency=KES&page=0&size=10"
```

**Response:**
```json
{
  "content": [
    {
      "isoCode": "KE",
      "name": "Kenya",
      "continentCode": "AF",
      "currencyIsoCode": "KES",
      "currencyName": "Kenyan Shilling",
      "languageName": "",
      "flagUrl": "http://www.oorsprong.org/websamples.countryinfo/Flags/Kenya.jpg"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

---

### Get Country Detail

```
GET /api/v1/countries/{isoCode}
```

```bash
curl "http://localhost:8080/api/v1/countries/KE"
```

**Response:**
```json
{
  "isoCode": "KE",
  "name": "Kenya",
  "continentCode": "AF",
  "capitalCity": "Nairobi",
  "currencyIsoCode": "KES",
  "currencyName": "Kenyan Shilling",
  "internationalPhoneCode": "254",
  "languageIsoCode": "",
  "languageName": "",
  "flagUrl": "http://www.oorsprong.org/websamples.countryinfo/Flags/Kenya.jpg"
}
```

---

### Countries Sharing a Currency

```
GET /api/v1/countries/{isoCode}/currency-siblings?page=0&size=20
```

```bash
curl "http://localhost:8080/api/v1/countries/KE/currency-siblings"
```

---

### List Continents

```
GET /api/v1/continents
```

---

### List Currencies

```
GET /api/v1/currencies
```

---

### List Languages

```
GET /api/v1/languages
```

---

### Error Responses

All errors follow a consistent envelope:

```json
{
  "timestamp": "2026-06-11T13:44:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Country not found: ZZ",
  "path": "/api/v1/countries/ZZ"
}
```

| Status | Scenario |
|---|---|
| `400` | Invalid query parameter or path variable |
| `404` | Country ISO code not found |
| `503` | SOAP service unavailable / store not yet populated |

---

## Caching Strategy

RDAS bootstraps all reference data from SOAP **once at startup**, then serves every REST request entirely from an in-memory store. No SOAP call is made per REST request.

| Data | TTL | Rationale |
|---|---|---|
| Countries | 24 hours | Changes only on geopolitical events |
| Currencies | 24 hours | ISO 4217 changes rarely |
| Languages | 24 hours | ISO 639 changes rarely |
| Continents | 72 hours | Effectively immutable |

**Refresh strategy:** A scheduled job runs at 02:00 daily and replaces the store in the background. Stale data continues to be served during the refresh window — zero downtime.

**SOAP traffic:** One full bootstrap = ~6 bulk SOAP calls + ~5 calls per country. After bootstrap, SOAP traffic drops to zero until the next scheduled refresh.

---

## Resilience

### Circuit Breaker (Resilience4j)

Wraps every SOAP gateway call. Configuration in `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      soapGateway:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

States:
- **CLOSED** — normal operation
- **OPEN** — SOAP calls short-circuit immediately; fallback returns empty/stale data
- **HALF-OPEN** — probe calls allowed; transitions back to CLOSED on success

### When the SOAP service is down for 6 hours

1. Circuit breaker opens after 5 consecutive failures
2. All REST requests continue to be served from the in-memory store (stale-if-error)
3. If the store is cold (first boot during outage): `503 Service Unavailable` with `Retry-After: 3600`
4. `GET /actuator/health` exposes `referenceData: DOWN` → triggers monitoring alert
5. Scheduled refresh retries at 02:00; if SOAP recovers, store is repopulated automatically

### Health & Monitoring

```bash
# Overall health
GET /actuator/health

# Reference data store status
GET /actuator/health/referenceData

# Circuit breaker state
GET /actuator/circuitbreakers
```

---

## Running Tests

```bash
mvn test
```

Tests cover:
- Search by name, continent, currency
- Pagination correctness (page index, size, totalPages)
- Sort ascending and descending
- `ResourceNotFoundException` on unknown ISO code
- Currency sibling lookup

---

## Kubernetes Deployment

### Prerequisites

- A running Kubernetes cluster (local: `minikube` or `kind`; cloud: EKS, GKE, AKS)
- `kubectl` configured against the target cluster
- Docker image pushed to a registry accessible by the cluster

### 1. Build and push the Docker image

```bash
# Build
docker build -t your-registry/rdas:1.0.0 .

# Push
docker push your-registry/rdas:1.0.0
```

Update the `image` field in `k8s/deployment.yaml` to match your registry path.

### 2. Deploy

```bash
# Apply all manifests in order
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/hpa.yaml
```

Or apply the whole directory at once:

```bash
kubectl apply -f k8s/
```

### 3. Verify the rollout

```bash
# Watch rollout
kubectl rollout status deployment/rdas -n rdas

# List pods
kubectl get pods -n rdas

# Expected output
NAME                    READY   STATUS    RESTARTS   AGE
rdas-6d9f8b7c4-abc12   1/1     Running   0          2m
rdas-6d9f8b7c4-xyz34   1/1     Running   0          2m
```

### 4. Verify the service is healthy

```bash
# Port-forward to test locally
kubectl port-forward svc/rdas 8080:80 -n rdas

# Check health
curl http://localhost:8080/actuator/health

# Test the API
curl "http://localhost:8080/api/v1/countries?continent=AF"
```

### 5. Scaling

Manual scale:

```bash
kubectl scale deployment/rdas -n rdas --replicas=4
```

The HPA scales automatically between 2 and 10 replicas based on CPU utilisation (threshold: 70%).

Check HPA status:

```bash
kubectl get hpa -n rdas
```

---

## Kubernetes Troubleshooting

### Pod is not starting

```bash
# Describe the pod for events (OOMKilled, ImagePullBackOff, etc.)
kubectl describe pod -n rdas <pod-name>

# Check logs
kubectl logs -n rdas <pod-name> --previous   # if pod crashed
kubectl logs -n rdas <pod-name>              # current run
```

### Bootstrap is taking too long / readiness probe failing

The readiness probe hits `/actuator/health/readiness`. RDAS won't pass readiness until the in-memory store is populated. Bootstrap can take 1–3 minutes on first run due to SOAP calls.

```bash
# Watch readiness in real time
kubectl logs -n rdas -l app=rdas -f | grep -E "bootstrap|ERROR|WARN"

# Check readiness probe result
kubectl describe pod -n rdas <pod-name> | grep -A5 Readiness
```

If bootstrap consistently fails, the SOAP service may be unreachable from the cluster. Verify egress:

```bash
kubectl exec -n rdas <pod-name> -- \
  curl -s "http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL" \
  | head -5
```

### Circuit breaker is OPEN

```bash
kubectl exec -n rdas <pod-name> -- \
  curl -s localhost:8080/actuator/circuitbreakers | jq .
```

If `state: OPEN`, the SOAP service is unreachable. The store will continue serving cached data. The circuit breaker will attempt recovery automatically after `waitDurationInOpenState` (30 seconds by default).

### High memory usage

RDAS holds all reference data in-memory (~250 countries). Each pod is allocated 512Mi request / 1Gi limit. If OOMKilled:

```bash
# Check current memory
kubectl top pods -n rdas

# Increase limits in k8s/deployment.yaml
resources:
  limits:
    memory: "2Gi"
```

### Rolling back a bad deployment

```bash
kubectl rollout undo deployment/rdas -n rdas

# Verify rollback
kubectl rollout status deployment/rdas -n rdas
```

### Checking ingress

```bash
kubectl get ingress -n rdas
kubectl describe ingress rdas-ingress -n rdas
```

---

## Engineering Discussion

### Q1 — What if the SOAP limit dropped to 10 req/min?

Bootstrap would take significantly longer (~2 hours sequentially for 250 countries × 6 calls). Mitigations:
- Bundle a static JSON snapshot of reference data in the JAR as a fallback seed
- Persist the last successful bootstrap to a database so restarts don't re-bootstrap from SOAP
- Increase cache TTL to 72 hours (refresh every 3 days instead of daily)
- Use a `Semaphore(1)` with deliberate 6-second delays between SOAP calls

### Q2 — Scale to 20 million requests/day (~232 req/sec)

Since all reads are served from in-memory after bootstrap, a single pod can handle this comfortably. For HA at that scale:
- 3–5 RDAS pods behind a load balancer (already configured via HPA)
- Replace Caffeine with a shared Redis cluster so all pods share one warm store
- Bootstrap becomes a leader-elected CronJob (ShedLock) — only one pod calls SOAP
- Prometheus + Grafana dashboards with autoscaling alerts

### Q3 — Given another week

- **Persistent store** — persist bootstrapped data to PostgreSQL so restarts are instant with no SOAP dependency
- **Admin refresh endpoint** — `POST /admin/cache/refresh` secured by API key for on-demand refresh
- **OpenTelemetry** — distributed tracing (OTLP → Jaeger) for SOAP latency visibility across bootstrap and refresh cycles
- **Audit log** — persist every API call (IP, path, params, timestamp, response time) to a database table for compliance reporting
