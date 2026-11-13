FROM eclipse-temurin:25-jdk-jammy@sha256:5ba254809ef542504d40d47271c7d6c4f240fe40ca1386259806d431f13b6b4f AS build
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src/main src/main
ENV MAVEN_OPTS="-Xms64m -Xmx192m -XX:ActiveProcessorCount=2"
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-jammy@sha256:a4e9fb35b48412cafbf036dabe201f22ee5e96f789e975bd6866ba0cbec45d17
RUN groupadd --gid 10001 bodego && useradd --uid 10001 --gid bodego --no-create-home bodego
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/bodego-platform-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=staging
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:ActiveProcessorCount=2", "-jar", "/app/app.jar"]
