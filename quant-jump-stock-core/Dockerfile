FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy Spring Boot fat JAR (not the -plain.jar)
COPY build/libs/quant-jump-stock-core-0.0.1-SNAPSHOT.jar app.jar

ENV SERVER_PORT=10010
ENV TZ=Asia/Seoul
EXPOSE 10010

ENTRYPOINT ["java", \
  "-XX:+UseG1GC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Duser.timezone=Asia/Seoul", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
