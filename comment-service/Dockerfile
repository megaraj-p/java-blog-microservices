FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY service-discovery/pom.xml service-discovery/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY blog-service/pom.xml blog-service/
COPY comment-service/pom.xml comment-service/
COPY search-service/pom.xml search-service/
COPY notification-service/pom.xml notification-service/
COPY analytics-service/pom.xml analytics-service/
RUN apk add --no-cache maven && mvn dependency:go-offline -q
COPY comment-service/src comment-service/src
RUN mvn package -pl comment-service -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/comment-service/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8083
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
