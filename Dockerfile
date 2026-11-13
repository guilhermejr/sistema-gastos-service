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

# Espera o config-server ficar pronto (no maximo 3 min) antes de iniciar, para nao
# falhar e reiniciar em loop quando todos os containers sobem juntos no boot.
ENTRYPOINT ["sh", "-c", "for i in $(seq 60); do curl -fsS -o /dev/null http://config-server:8888/actuator/health && break; echo 'Aguardando o config-server...'; sleep 3; done; exec java -Dspring.profiles.active=prod -jar /sistema-gastos-service.jar"]
EXPOSE 9008