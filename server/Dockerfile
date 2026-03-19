# Use official Kotlin/Java runtime
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install dependencies for build
RUN apk add --no-cache bash

# Set working directory
WORKDIR /app

# Copy entire repository for build
COPY . .

# Build the server
RUN ./gradlew :server:shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Create app directory
WORKDIR /app

# Create data directory for persistent storage
RUN mkdir -p /app/data

# Copy the built JAR from builder stage
COPY --from=builder /app/server/build/libs/*.jar app.jar

# Expose the server port
EXPOSE 8080

# Volume for persistent data
VOLUME ["/app/data"]

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the server
ENTRYPOINT ["java", "-jar", "app.jar"]
