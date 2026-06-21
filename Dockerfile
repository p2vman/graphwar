FROM openjdk:8-jdk AS builder
WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle

COPY . .

RUN chmod +x ./gradlew \
    && ./gradlew clean assemble globalServerShadow --no-daemon || true

ARG JAR_NAME=build/libs/globalServerJar.jar
FROM openjdk:8-jre-slim
WORKDIR /app

COPY --from=builder /app/${JAR_NAME} /app/globalServerJar.jar

ENTRYPOINT ["java","-jar","/app/globalServerJar.jar"]
