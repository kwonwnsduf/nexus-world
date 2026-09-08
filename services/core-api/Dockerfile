# syntax=docker/dockerfile:1.7
FROM gradle:8.14.3-jdk17-alpine AS build
WORKDIR /workspace
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts gradle.properties ./
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle --no-daemon dependencies
COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle --no-daemon clean test bootJar

FROM eclipse-temurin:17.0.18_8-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/build/libs/nexus-world-core-api-*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
