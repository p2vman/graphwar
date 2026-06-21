FROM eclipse-temurin:8-jdk AS builder
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle

COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew clean assemble globalServerShadow --no-daemon || true

ARG JAR_NAME=build/libs/globalServerJar.jar
FROM eclipse-temurin:8-jre
WORKDIR /app

COPY --from=builder /app/${JAR_NAME} /app/globalServerJar.jar

ENTRYPOINT ["java","-jar","/app/globalServerJar.jar"]
