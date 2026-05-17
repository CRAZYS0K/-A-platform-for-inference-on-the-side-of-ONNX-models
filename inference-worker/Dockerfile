################ Build stage ################
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared-dto/pom.xml shared-dto/
COPY gateway-service/pom.xml gateway-service/
COPY backend-service/pom.xml backend-service/
COPY inference-worker/pom.xml inference-worker/
COPY notification-service/pom.xml notification-service/
RUN mvn -B -pl inference-worker -am dependency:go-offline -DskipTests

COPY shared-dto/src shared-dto/src
COPY inference-worker/src inference-worker/src
RUN mvn -B -pl inference-worker -am package -DskipTests

################ Runtime stage ################
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 --shell /usr/sbin/nologin app && chown app:app /app
USER app

COPY --from=build /workspace/inference-worker/target/inference-worker-*.jar /app/app.jar

EXPOSE 8082
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
