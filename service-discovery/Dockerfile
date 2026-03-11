# ── Stage 1: Build ────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and all POMs for multi-module project
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
RUN ./mvnw dependency:go-offline -q

# Copy source and build only this module
COPY service-discovery/src/ service-discovery/src/
RUN ./mvnw clean package -pl service-discovery -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the fat JAR from builder stage
COPY --from=builder /app/service-discovery/target/*.jar app.jar

# Set ownership
RUN chown appuser:appgroup app.jar

USER appuser

# Expose the Eureka port
EXPOSE 8761

# JVM tuning for containerized environments
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
