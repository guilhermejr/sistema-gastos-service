FROM amazoncorretto:21
LABEL maintainer="Guilherme Jr. <falecom@guilhermejr.net>"
ENV TZ=America/Bahia
ARG VAULT_HOST
ENV VAULT_HOST=${VAULT_HOST}
COPY sistema-gastos-service.jar sistema-gastos-service.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:9008/gastos-service/actuator/health | grep -q '"status":"UP"' || exit 1

# Roda sem root. HOME em /tmp porque o usuario 10001 nao existe no /etc/passwd da imagem.
ENV HOME=/tmp
USER 10001:10001

ENTRYPOINT ["java","-Dspring.profiles.active=prod","-jar","/sistema-gastos-service.jar"]
EXPOSE 9008