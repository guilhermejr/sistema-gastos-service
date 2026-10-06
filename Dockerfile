FROM amazoncorretto:21
LABEL maintainer="Guilherme Jr. <falecom@guilhermejr.net>"
ENV TZ=America/Bahia
ARG VAULT_HOST
ARG VAULT_TOKEN
ARG CONFIG_SERVER_USER
ARG CONFIG_SERVER_PASS
ENV VAULT_HOST=${VAULT_HOST}
ENV VAULT_TOKEN=${VAULT_TOKEN}
ENV CONFIG_SERVER_USER=${CONFIG_SERVER_USER}
ENV CONFIG_SERVER_PASS=${CONFIG_SERVER_PASS}
COPY sistema-gastos-service.jar sistema-gastos-service.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:9008/gastos-service/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java","-Dspring.profiles.active=prod","-jar","/sistema-gastos-service.jar"]
EXPOSE 9008