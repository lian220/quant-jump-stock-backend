FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install Python 3 and pip for running Vertex AI scripts
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies for Vertex AI
RUN pip3 install --no-cache-dir google-cloud-aiplatform google-cloud-storage pymongo

# Copy Spring Boot fat JAR (not the -plain.jar)
COPY build/libs/quant-jump-stock-core-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 10010

ENTRYPOINT ["java", "-jar", "app.jar"]
