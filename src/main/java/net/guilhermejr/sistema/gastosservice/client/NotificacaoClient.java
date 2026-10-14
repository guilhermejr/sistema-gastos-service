package net.guilhermejr.sistema.gastosservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Envio de push pelo notificacao-service, que só é alcançável de dentro da rede do
 * Docker. Usa o endpoint genérico (título e mensagem) que o supermercado-service já usa.
 */
@Component
public class NotificacaoClient {

    private final RestClient restClient;

    public NotificacaoClient(@Value("${sistema.notificacao.host}") String host) {
        this.restClient = RestClient.builder().baseUrl("http://" + host + "/notificacao-service").build();
    }

    public void enviarPush(String titulo, String mensagem) {

        restClient.post().uri("/supermercado/enviar-notificacao-generica")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("titulo", titulo, "mensagem", mensagem))
                .retrieve().toBodilessEntity();

    }

}
