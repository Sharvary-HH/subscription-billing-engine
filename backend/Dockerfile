# Build stage: compile with Maven on JDK 21. Tests run in CI, not here, so the image build stays fast.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Run stage: a JRE only, as a non-root user. The heap is capped explicitly because a small host
# (a t3.micro, say) will otherwise let the JVM size itself against the whole box and get killed.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/target/billing-*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xmx512m -XX:+UseSerialGC"
HEALTHCHECK --interval=15s --timeout=3s --retries=10 CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
