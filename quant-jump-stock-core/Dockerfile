FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install Python 3 and pip for running Vertex AI scripts
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies for Vertex AI
RUN pip3 install --no-cache-dir google-cloud-aiplatform google-cloud-storage pymongo

# Copy pre-built JAR
COPY build/libs/quantiq-core-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
