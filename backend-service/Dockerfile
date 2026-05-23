# JARs are built on the host via `./mvnw -DskipTests package` and packaged here.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 --shell /usr/sbin/nologin app && chown app:app /app
USER app

ARG JAR_FILE=backend-service/target/backend-service-*.jar
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8081
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
