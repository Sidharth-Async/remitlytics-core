# ---------------------------------------------------
# Stage 1: Build & Extract Layers
# ---------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Maven wrapper / build descriptors first for caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -B

# Copy source code and package executable JAR
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Extract Spring Boot layers
WORKDIR /workspace/target
RUN java -Djarmode=layertools -jar core-engine-0.0.1-SNAPSHOT.jar extract

# ---------------------------------------------------
# Stage 2: Hardened Runtime Container
# ---------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root application user for container security
RUN groupadd -r remitlytics && useradd -r -g remitlytics remitlytics
USER remitlytics:remitlytics

# Copy extracted layers from builder
COPY --from=builder /workspace/target/dependencies/ ./
COPY --from=builder /workspace/target/spring-boot-loader/ ./
COPY --from=builder /workspace/target/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/application/ ./

# Container environment & performance flags
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]