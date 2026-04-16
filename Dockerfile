# Stage 1: Build
FROM maven:3.9-eclipse-temurin-22 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:22-jre
WORKDIR /app

COPY --from=build /build/target/goaffpro-rechnungen-1.0-SNAPSHOT.jar /app/app.jar
COPY src/main/resources/ui/dashboard.html /app/ui/dashboard.html
COPY docs/HILFE.md /app/docs/HILFE.md
RUN mkdir -p /app/config /app/data /app/exports

ENV CONFIG_PATH=/app/config/config.properties
ENV UI_PATH=/app/ui/dashboard.html
ENV HELP_DOC_PATH=/app/docs/HILFE.md
ENV PDF_EXPORT_PATH=/app/exports

EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
