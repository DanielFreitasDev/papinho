# syntax=docker/dockerfile:1
#
# Imagem do Papinho: o papinho.jar (SPA + API + WebSocket) num JRE 25, sem privilégios, com os dados em /data.
#
# Do código-fonte (o estágio "build" roda ./mvnw; atrás de proxy, passe o settings.xml do Maven e a rede do host):
#   docker build -t papinho .
#   docker build --network host --secret id=maven-settings,src=$HOME/.m2/settings.xml -t papinho .
# Com um JAR pronto (o CI usa o JAR já testado, o mesmo da release, e o estágio "build" nem roda):
#   docker buildx build --build-context jar=<pasta com o papinho.jar> -t papinho .

ARG JRE_IMAGE=eclipse-temurin:25-jre-alpine

# O JAR não depende da arquitetura: compila uma vez, na máquina que constrói, para todas as plataformas.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=secret,id=maven-settings,target=/root/.m2/settings.xml \
    ./mvnw -B -DskipTests install \
    && cp backend/target/papinho.jar /papinho.jar

# Trocável por --build-context jar=<pasta>: a imagem leva exatamente aquele papinho.jar.
FROM scratch AS jar
COPY --from=build /papinho.jar /papinho.jar

FROM ${JRE_IMAGE}
ARG VERSION=dev
ARG REVISION=desconhecida
LABEL org.opencontainers.image.title="Papinho" \
      org.opencontainers.image.description="Chat web auto-hospedado, inspirado no WhatsApp Web, com privacidade nativa" \
      org.opencontainers.image.source="https://github.com/DanielFreitasDev/papinho" \
      org.opencontainers.image.url="https://github.com/DanielFreitasDev/papinho" \
      org.opencontainers.image.documentation="https://github.com/DanielFreitasDev/papinho#rodar-com-docker" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
RUN addgroup -S -g 10001 papinho \
    && adduser -S -D -H -u 10001 -G papinho -h /data -s /sbin/nologin papinho \
    && mkdir -p /data /app \
    && chown papinho:papinho /data
COPY --from=jar /papinho.jar /app/papinho.jar
COPY LICENSE NOTICE /app/
# Arquivos enviados, uploads em andamento e a chave de cifra (secret.key): guarde o volume no backup.
ENV PAPINHO_DATA_DIR=/data
VOLUME ["/data"]
EXPOSE 8080
USER papinho
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PAPINHO_PORT:-8080}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/papinho.jar"]
