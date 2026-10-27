# syntax=docker/dockerfile:1
# ---- Stage 1: build the jar (uses the Maven wrapper, so no Maven needs to be installed) ----
FROM eclipse-temurin:24-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# The cache mount keeps downloaded dependencies between builds (a re-build after a code change does not
# download everything again). The retry settings make a flaky network a slow build, not a failed one.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -B -DskipTests \
        -Dmaven.wagon.http.retryHandler.count=6 \
        -Dmaven.wagon.httpconnectionManager.ttlSeconds=30 \
        package \
    && cp target/worksphere-backend-*.jar app.jar

# ---- Stage 2: a small runtime image: only a JRE, the jar, and a non-root user ----
FROM eclipse-temurin:24-jre
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin worksphere
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER 10001
EXPOSE 8080
# Size the heap from the container's memory limit instead of the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
