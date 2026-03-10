# Enterprise Blog Platform — Java Microservices Architecture

> Industrial-grade enterprise blog platform built with Java 17, Spring Boot 3, Spring Cloud, Kafka, PostgreSQL, Redis, Elasticsearch, and Kubernetes — reflecting real-world engineering standards used at companies like HPE, Netflix, and Amazon.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Service Responsibilities](#service-responsibilities)
3. [Technology Stack](#technology-stack)
4. [System Design Decisions](#system-design-decisions)
5. [Event-Driven Communication](#event-driven-communication)
6. [API Contracts](#api-contracts)
7. [Security Model](#security-model)
8. [Failure Handling & Resilience](#failure-handling--resilience)
9. [Scaling Strategy](#scaling-strategy)
10. [Observability](#observability)
11. [Containerization Strategy](#containerization-strategy)
12. [Deployment Pipeline](#deployment-pipeline)
13. [Getting Started](#getting-started)
14. [Engineering Considerations](#engineering-considerations)

---

## Architecture Overview

```
                          ┌────────────────────────────────────────────────┐
                          │              Client (Browser / Mobile)          │
                          └────────────────────────┬───────────────────────┘
                                                   │ HTTPS
                          ┌────────────────────────▼───────────────────────┐
                          │              Load Balancer (Nginx/AWS ALB)      │
                          └────────────────────────┬───────────────────────┘
                                                   │
                          ┌────────────────────────▼───────────────────────┐
                          │          API Gateway (Spring Cloud Gateway)      │
                          │   • JWT Validation   • Rate Limiting            │
                          │   • Request Routing  • CORS                     │
                          └──┬──────┬──────┬──────┬──────┬──────┬──────────┘
                             │      │      │      │      │      │
           ┌─────────────────▼──┐ ┌─▼────┐ ┌────▼──┐ ┌─▼────┐ ┌▼──────────────────┐
           │   User Service     │ │ Blog │ │Comment│ │Search│ │ Analytics Service  │
           │ JWT • RBAC • Roles │ │Service│ │Service│ │Service│ │ Metrics • Reports │
           └────────┬───────────┘ └──┬───┘ └───┬───┘ └──┬───┘ └─────────┬──────────┘
                    │                │          │         │               │
           ┌────────▼──────┐  ┌──────▼──┐  ┌───▼──┐  ┌──▼──┐  ┌────────▼────────┐
           │  PostgreSQL   │  │PostgreSQL│  │Postgr│  │Elast│  │   PostgreSQL     │
           │  (users DB)   │  │(blogs DB)│  │(cmts)│  │search│  │  (analytics DB) │
           └───────────────┘  └─────────┘  └──────┘  └─────┘  └─────────────────┘
                                                   │
                    ┌──────────────────────────────▼────────────────────────────┐
                    │                     Apache Kafka                           │
                    │  blog.published │ blog.viewed │ comment.created │          │
                    │  user.registered │ post.deleted                           │
                    └──────────────────────────────┬────────────────────────────┘
                                                   │
                          ┌────────────────────────▼───────────────────────┐
                          │          Notification Service                    │
                          │    Email Alerts • Push Notifications             │
                          └────────────────────────────────────────────────┘

                    ┌─────────────────────────────────────────────────────────┐
                    │             Service Discovery (Eureka)                   │
                    │       All services register here for load balancing      │
                    └─────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────────────────────────────────┐
                    │           Observability Stack                            │
                    │  Prometheus → Grafana   |   ELK Stack (Logs)            │
                    │  Zipkin (Distributed Tracing)                           │
                    └─────────────────────────────────────────────────────────┘
```

---

## Service Responsibilities

| Service | Port | Responsibility |
|---|---|---|
| **Service Discovery** | 8761 | Eureka server — all services register here |
| **API Gateway** | 8080 | Routing, JWT validation, rate limiting, CORS |
| **User Service** | 8081 | Registration, login, JWT issuance, RBAC |
| **Blog Service** | 8082 | CRUD for blog posts, tags, categories, publish workflow |
| **Comment Service** | 8083 | Comments, nested replies, moderation |
| **Search Service** | 8084 | Elasticsearch full-text search and filtering |
| **Notification Service** | 8085 | Email alerts driven by Kafka events |
| **Analytics Service** | 8086 | View tracking, popular posts, engagement metrics |

---

## Technology Stack

### Backend
| Layer | Technology |
|---|---|
| Language | Java 17 (LTS) |
| Framework | Spring Boot 3.2 |
| Cloud | Spring Cloud 2023.0 |
| Security | Spring Security 6 + JJWT 0.12 |
| Data Access | Spring Data JPA + Hibernate 6 |
| Search | Spring Data Elasticsearch 5 |
| Messaging | Apache Kafka (Spring Kafka) |
| Caching | Redis (Spring Data Redis) |
| Resilience | Resilience4j (Circuit Breaker) |
| Tracing | Micrometer + Zipkin |
| Metrics | Micrometer + Prometheus |

### Infrastructure
| Layer | Technology |
|---|---|
| Database | PostgreSQL 15 (per-service) |
| Cache | Redis 7 |
| Search | Elasticsearch 8 + Kibana |
| Message Broker | Apache Kafka 3.6 + Zookeeper |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Container Runtime | Docker + Docker Compose |
| Orchestration | Kubernetes (k8s) |
| Ingress | Nginx Ingress Controller |
| Monitoring | Prometheus + Grafana |
| Log Aggregation | ELK Stack (Elasticsearch + Logstash + Kibana) |
| CI/CD | GitHub Actions |

---

## System Design Decisions

### 1. Database per Service Pattern
Each service owns its database schema. No service queries another service's DB directly.
- **User Service** → `users_db` (PostgreSQL)
- **Blog Service** → `blogs_db` (PostgreSQL)
- **Comment Service** → `comments_db` (PostgreSQL)
- **Notification Service** → `notifications_db` (PostgreSQL)
- **Analytics Service** → `analytics_db` (PostgreSQL)
- **Search Service** → Elasticsearch index only

This eliminates tight coupling at the data layer, enables independent scaling, and allows different database technologies per service.

### 2. API Gateway as Perimeter Defense
The gateway is the single entry point. It validates JWTs *before* forwarding requests, so internal services don't need to re-validate tokens. Internal service-to-service calls use service accounts or propagated headers.

### 3. Async-First for Cross-Service Side Effects
Publishing a blog post triggers email notifications, search indexing, and analytics updates. These are async Kafka events — the core write operation succeeds immediately without waiting for these side effects. This improves write latency and decouples services.

### 4. CQRS-Lite at the Service Level
Blog Service handles writes (PostgreSQL). Search Service handles complex reads (Elasticsearch). They stay in sync via Kafka events. This allows each to be optimized independently.

### 5. JWT with Short Expiry + Refresh Tokens
Access tokens expire in 15 minutes. Refresh tokens (stored in Redis with TTL) are used to issue new access tokens. This limits blast radius if an access token is stolen.

---

## Event-Driven Communication

### Kafka Topics

| Topic | Producer | Consumers | Payload |
|---|---|---|---|
| `user.registered` | User Service | Notification Service | `{userId, email, username}` |
| `blog.published` | Blog Service | Search Service, Notification Service, Analytics | `{blogId, authorId, title, tags, content}` |
| `blog.deleted` | Blog Service | Search Service, Analytics Service | `{blogId}` |
| `blog.viewed` | Analytics Service | (self-consume after API hit) | `{blogId, userId, ipAddress, timestamp}` |
| `comment.created` | Comment Service | Notification Service, Analytics Service | `{commentId, blogId, authorId, content}` |

### Event Flow Example — Blog Published

```
Author POSTs /blogs/{id}/publish
    → Blog Service validates ownership
    → Blog Service updates status = PUBLISHED
    → Blog Service publishes to Kafka topic: blog.published
         ├── Search Service consumes: indexes document in Elasticsearch
         ├── Notification Service consumes: emails all subscribers
         └── Analytics Service consumes: creates analytics record
```

---

## API Contracts

### User Service

```
POST   /auth/register          Register new user
POST   /auth/login             Login, returns JWT + refresh token
POST   /auth/refresh           Refresh access token
POST   /auth/logout            Invalidate refresh token
GET    /users/me               Get current user profile
PUT    /users/me               Update current user profile
GET    /users/{id}             Get user by ID (Admin only)
GET    /users                  List all users (Admin only)
PUT    /users/{id}/roles       Update user roles (Admin only)
```

### Blog Service

```
GET    /blogs                  List published blogs (paginated, filterable)
POST   /blogs                  Create new blog post (Author/Admin)
GET    /blogs/{id}             Get single blog post
PUT    /blogs/{id}             Update blog post (owner/Admin)
DELETE /blogs/{id}             Delete blog post (owner/Admin)
POST   /blogs/{id}/publish     Publish a draft post
POST   /blogs/{id}/archive     Archive a post
GET    /blogs/my               Get current user's posts
GET    /categories             List all categories
POST   /categories             Create category (Admin)
GET    /tags                   List all tags
```

### Comment Service

```
GET    /comments?blogId={id}   Get comments for a blog post
POST   /comments               Create a comment
PUT    /comments/{id}          Update a comment (owner/Admin)
DELETE /comments/{id}          Delete a comment (owner/Admin)
POST   /comments/{id}/approve  Approve a comment (Admin)
POST   /comments/{id}/reject   Reject a comment (Admin)
GET    /comments/{id}/replies  Get nested replies
```

### Search Service

```
GET    /search?q={query}&tags={tags}&category={cat}&page={p}&size={s}
GET    /search/suggest?q={query}  Autocomplete suggestions
```

### Analytics Service

```
GET    /analytics/popular          Top 10 most viewed posts
GET    /analytics/posts/{id}/views View count for a specific post
GET    /analytics/trending         Trending posts (last 24h)
GET    /analytics/author/{id}      Author engagement metrics (Admin/Author)
```

---

## Security Model

### Authentication Flow

```
Client → POST /auth/login → User Service
User Service validates credentials
User Service issues: access_token (15min) + refresh_token (7 days)
Client stores tokens securely (HttpOnly cookies in web)

Client → GET /blogs → API Gateway
API Gateway extracts JWT from Authorization header
API Gateway validates JWT signature + expiry
API Gateway forwards X-User-Id + X-User-Roles headers to upstream service
Upstream service trusts these headers (internal network only)
```

### Role-Based Access Control (RBAC)

| Role | Permissions |
|---|---|
| `READER` | Read published blogs, read comments, search |
| `AUTHOR` | All READER + create/edit/delete own blogs, comment |
| `ADMIN` | All AUTHOR + manage users, moderate comments, view analytics |

### Security Hardening
- Passwords hashed with BCrypt (strength 12)
- JWT signed with RS256 (asymmetric) in production
- Rate limiting: 100 req/min per IP, 1000 req/min per authenticated user
- CORS configured per environment
- SQL injection prevention via JPA parameterized queries
- Input validation with `@Valid` + Bean Validation
- Security headers: CSP, X-Frame-Options, HSTS via API Gateway
- Secrets managed via Kubernetes Secrets (base64 encoded, backed by Vault in prod)

---

## Failure Handling & Resilience

### Circuit Breaker Pattern (Resilience4j)

When Blog Service calls User Service to validate an author, a circuit breaker prevents cascade failure:

```
Blog Service → User Service
   ↳ Circuit CLOSED: normal call
   ↳ Circuit OPEN (after 5 failures): returns cached fallback immediately
   ↳ Circuit HALF-OPEN (after 30s): tests with probe requests
```

### Retry Policy
- 3 retries with exponential backoff (1s, 2s, 4s)
- Applied to inter-service REST calls only
- NOT applied to client-facing reads (fail fast)

### Bulkhead Pattern
- Separate thread pools per downstream dependency
- Prevents slow User Service calls from exhausting Blog Service threads

### Kafka Failure Handling
- Dead Letter Queue (DLQ) for messages that fail processing after 3 retries
- Idempotent consumers (check event ID before processing)
- Kafka consumer groups for independent scaling

### Database Connection Pooling
- HikariCP (Spring Boot default) — max 20 connections per service instance
- Connection validation on borrow

---

## Scaling Strategy

### Horizontal Scaling
Every microservice is stateless. Scale by increasing replica count:
```bash
kubectl scale deployment blog-service --replicas=10
```

### Database Scaling
- **Read replicas**: Route read queries to replicas via Spring Data routing
- **Connection pooling**: PgBouncer in front of PostgreSQL
- **Sharding**: Analytics service partitioned by date range

### Kafka Scaling
- Increase partition count per topic to allow more consumers
- Consumer group auto-rebalancing via Kafka coordinator

### Caching Strategy
- **L1**: In-process cache (Caffeine) for hot data (< 1ms)
- **L2**: Redis distributed cache (1-3ms) for shared data
- Cache invalidation on write via pub/sub

### CDN for Static Assets
Blog content served through CloudFront/Fastly. Markdown compiled to HTML cached at edge.

### Auto-Scaling (Kubernetes HPA)
```yaml
# Scales when CPU > 70% or custom Kafka consumer lag metric > 1000
minReplicas: 2
maxReplicas: 20
metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

---

## Observability

### Metrics (Micrometer → Prometheus → Grafana)
Key metrics per service:
- `blog_posts_created_total` — counter
- `blog_requests_duration_seconds` — histogram
- `kafka_consumer_lag` — gauge
- `jvm_memory_used_bytes` — JVM metrics
- `db_connection_pool_active` — HikariCP metrics

### Distributed Tracing (Zipkin / Jaeger)
Every request gets a `traceId`. The API Gateway injects the trace header, and each service propagates it. You can follow a request from gateway → blog-service → user-service in the Zipkin UI.

### Structured Logging (ELK Stack)
Services log in JSON format via Logback with `logstash-logback-encoder`:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "service": "blog-service",
  "traceId": "abc123",
  "userId": "user-456",
  "message": "Blog post published",
  "blogId": "post-789"
}
```
Logs flow: Application → Logstash → Elasticsearch → Kibana

### Health Checks
Spring Actuator exposes `/actuator/health`, `/actuator/metrics`, `/actuator/info`.
Kubernetes liveness and readiness probes use these endpoints.

---

## Containerization Strategy

### Multi-Stage Docker Builds
```
Stage 1: Builder (eclipse-temurin:17-jdk) — compiles the JAR
Stage 2: Runtime (eclipse-temurin:17-jre-alpine) — runs the JAR
Result: ~180MB image vs ~650MB if built in one stage
```

### Image Tagging
- `latest` — most recent commit to main
- `v1.2.3` — semantic version tags for production deployments
- `sha-abc1234` — immutable SHA-based tags for reproducibility

### Container Security
- Non-root user inside containers
- Read-only filesystem where possible
- No secrets baked into images — injected via Kubernetes Secrets

---

## Deployment Pipeline

### Environments
```
Developer PR → Feature Branch
  ↓ GitHub Actions: lint + unit tests + build
Merge to main → dev environment (auto-deploy)
  ↓ Integration tests + contract tests
Tag release → staging environment
  ↓ E2E tests + performance tests
Manual approval → production environment (blue/green deploy)
```

### GitHub Actions Pipeline
1. **Build**: `mvn clean package -DskipTests`
2. **Test**: `mvn test` with JaCoCo coverage gate (>80%)
3. **Security Scan**: OWASP dependency check + Trivy image scan
4. **Docker Build**: Build and push to GitHub Container Registry (GHCR)
5. **Deploy**: `kubectl apply` via ArgoCD or `kubectl set image`
6. **Smoke Test**: Health check endpoints post-deploy
7. **Notify**: Slack notification on success/failure

### Blue/Green Deployment
```
Current: blog-service-v1 (blue, 100% traffic)
Deploy:  blog-service-v2 (green, deployed, 0% traffic)
Switch:  Update Service selector to green, run smoke tests
Verify:  Monitor error rates for 5 minutes
Rollback: Switch selector back to blue if error rate spikes
```

---

## Getting Started

### Prerequisites
- Docker Desktop with Kubernetes enabled
- Java 17+, Maven 3.9+
- kubectl

### Local Development (Docker Compose)
```bash
# Start all infrastructure
docker-compose up -d

# Build all services
mvn clean package -DskipTests

# The services start automatically via docker-compose
# API Gateway: http://localhost:8080
# Eureka:      http://localhost:8761
# Kibana:      http://localhost:5601
# Grafana:     http://localhost:3000 (admin/admin)
# Zipkin:      http://localhost:9411
```

### Kubernetes Deployment
```bash
# Create namespace
kubectl apply -f k8s/namespace.yml

# Deploy infrastructure
kubectl apply -f k8s/infrastructure/

# Deploy services
kubectl apply -f k8s/service-discovery/
kubectl apply -f k8s/api-gateway/
kubectl apply -f k8s/user-service/
kubectl apply -f k8s/blog-service/
kubectl apply -f k8s/comment-service/
kubectl apply -f k8s/search-service/
kubectl apply -f k8s/notification-service/
kubectl apply -f k8s/analytics-service/

# Deploy monitoring
kubectl apply -f k8s/monitoring/
```

### Test the Platform
```bash
# Register a user
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"securePass123!"}'

# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"securePass123!"}'

# Create a blog post (use JWT from login response)
curl -X POST http://localhost:8080/blogs \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"My First Post","content":"Hello World","categoryId":1,"tags":["java","spring"]}'
```

---

## Engineering Considerations

### Millions of Users
- Stateless services scale horizontally to hundreds of pods
- Kafka handles millions of events/second with proper partition strategy
- CDN absorbs static asset traffic, reducing origin load
- Redis cluster handles millions of cache operations/second
- Read replicas for PostgreSQL separate read/write load
- Elasticsearch scales horizontally for search traffic

### Fault Tolerance
- Resilience4j circuit breakers prevent cascading failures
- Kafka DLQs ensure no events are lost
- Database connection pooling prevents thread exhaustion
- Kubernetes restarts failed pods within seconds
- Multi-AZ deployment ensures zone-level failures don't cause outages

### Real Enterprise Patterns Used
- **Saga Pattern**: Long-running transactions (user deletion cascades via events)
- **Outbox Pattern**: Guaranteed event publishing via transactional outbox table
- **CQRS**: Separate read (Elasticsearch) and write (PostgreSQL) models for blog data
- **API Gateway Pattern**: Single entry point with cross-cutting concerns
- **Sidecar Pattern**: Envoy proxy sidecar for mTLS and traffic management
- **Database per Service**: No shared databases, each service owns its data

---

## Project Structure

```
java-blog-microservices/
├── pom.xml                          # Parent multi-module POM
├── docker-compose.yml               # Full local development environment
├── .gitignore
├── README.md
├── service-discovery/               # Eureka Server
├── api-gateway/                     # Spring Cloud Gateway
├── user-service/                    # Auth, JWT, Users
├── blog-service/                    # Blog CRUD, Tags, Categories
├── comment-service/                 # Comments, Replies, Moderation
├── search-service/                  # Elasticsearch full-text search
├── notification-service/            # Email notifications via Kafka
├── analytics-service/               # View tracking, metrics
├── k8s/                             # Kubernetes manifests
│   ├── namespace.yml
│   ├── infrastructure/              # Postgres, Redis, Kafka, ES
│   ├── service-discovery/
│   ├── api-gateway/
│   ├── user-service/
│   ├── blog-service/
│   ├── comment-service/
│   ├── search-service/
│   ├── notification-service/
│   ├── analytics-service/
│   └── monitoring/                  # Prometheus, Grafana
└── .github/
    └── workflows/
        └── ci-cd.yml                # Full CI/CD pipeline
```
