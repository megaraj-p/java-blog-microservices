# ── Stage 1: Build ────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY service-discovery/pom.xml service-discovery/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY blog-service/pom.xml blog-service/
COPY comment-service/pom.xml comment-service/
COPY search-service/pom.xml search-service/
COPY notification-service/pom.xml notification-service/
COPY analytics-service/pom.xml analytics-service/
RUN ./mvnw dependency:go-offline -pl api-gateway -q
COPY api-gateway/src/ api-gateway/src/
RUN ./mvnw clean package -pl api-gateway -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/api-gateway/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
