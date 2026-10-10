package net.guilhermejr.sistema.gastosservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Leitura dos dados do Itaú pelo Meu Pluggy (Open Finance). Só lê: autentica, lista as
 * contas da conexão, as faturas e as transações de uma conta.
 *
 * <p>As credenciais vêm do Vault ({@code secret/gastos-service}). Sem elas o serviço sobe
 * normalmente e só a integração responde que não está configurada.
 */
@Log4j2
@Component
public class PluggyClient {

    private final RestClient restClient = RestClient.builder().baseUrl("https://api.pluggy.ai").build();
    private final String clientId;
    private final String clientSecret;
    private final String itemId;
    private final String usuario;

    public PluggyClient(@Value("${pluggyClientID:}") String clientId, @Value("${pluggySecretID:}") String clientSecret,
                        @Value("${pluggyItemId:}") String itemId, @Value("${pluggyUsuario:}") String usuario) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.itemId = itemId;
        this.usuario = usuario;
    }

    /**
     * As credenciais do Meu Pluggy são de uma pessoa: só o usuário em {@code pluggyUsuario}
     * pode ligar cartões ao banco e ver as transações.
     */
    public boolean liberadoPara(UUID usuarioLogado) {
        return configurado() && usuario.equalsIgnoreCase(usuarioLogado.toString());
    }

    public boolean configurado() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !itemId.isBlank() && !usuario.isBlank();
    }

    /** Gera a chave de acesso (vale 2 horas); cada sincronização pede uma. */
    public String autenticar() {

        if (!configurado()) {
            throw new ExceptionDefault("A integração com o banco não está configurada.");
        }
        Autenticacao autenticacao = chamar(() -> restClient.post().uri("/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
                .retrieve().body(Autenticacao.class));
        return autenticacao.apiKey();

    }

    public List<Conta> contas(String chave) {

        return chamar(() -> restClient.get().uri("/accounts?itemId={item}", itemId)
                .header("X-API-KEY", chave).retrieve().body(PaginaContas.class)).results();

    }

    public List<Fatura> faturas(String chave, String contaId) {

        return chamar(() -> restClient.get().uri("/bills?accountId={conta}", contaId)
                .header("X-API-KEY", chave).retrieve().body(PaginaFaturas.class)).results();

    }

    /**
     * Todas as transações da conta, sem filtro de data: as parcelas futuras vêm com a data
     * em que serão cobradas, e um filtro até hoje as deixaria de fora. Páginas de 500,
     * por cursor: {@code next} traz o {@code after} da próxima página.
     */
    public List<Transacao> transacoes(String chave, String contaId) {

        List<Transacao> transacoes = new ArrayList<>();
        String after = null;
        do {
            Map<String, String> variaveis = after == null ? Map.of("conta", contaId) : Map.of("conta", contaId, "after", after);
            PaginaTransacoes pagina = chamar(() -> restClient.get()
                    .uri(uri -> {
                        uri.path("/v2/transactions").queryParam("accountId", "{conta}");
                        if (variaveis.containsKey("after")) {
                            uri.queryParam("after", "{after}");
                        }
                        return uri.build(variaveis);
                    })
                    .header("X-API-KEY", chave).retrieve().body(PaginaTransacoes.class));
            transacoes.addAll(pagina.results());
            after = cursor(pagina.next());
        } while (after != null);
        return transacoes;

    }

    /**
     * O cursor é base64 e pode ter {@code + / =}: sai decodificado do {@code next} (só os
     * %XX — o "+" literal continua "+") e volta como variável da URI, que o codifica por
     * inteiro. Colado cru na URL, o "+" chegaria à Pluggy como espaço.
     */
    static String cursor(String next) {

        if (next == null || next.isBlank()) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(next).build().getQueryParams().getOrDefault("after", List.of()).stream()
                .findFirst().map(valor -> UriUtils.decode(valor, StandardCharsets.UTF_8)).orElse(null);

    }

    private <T> T chamar(Supplier<T> chamada) {

        try {
            return chamada.get();
        } catch (RestClientException e) {
            log.error("Erro ao consultar a Pluggy: {}", e.getMessage());
            throw new ExceptionDefault("Não foi possível consultar o banco agora. Tente de novo mais tarde.");
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Autenticacao(String apiKey) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaContas(List<Conta> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaFaturas(List<Fatura> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaTransacoes(List<Transacao> results, String next) {}

    /** {@code type}: BANK ou CREDIT. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Conta(String id, String type, String name, String number) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fatura(String id, OffsetDateTime dueDate) {}

    /** No cartão, {@code amount} é positivo na compra e negativo no estorno e no pagamento. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transacao(String id, String description, BigDecimal amount, OffsetDateTime date, String status,
                            String category, DadosCartao creditCardMetadata) {}

    /** {@code billForecastDate} é o mês da fatura, "aaaa-mm". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DadosCartao(Integer installmentNumber, Integer totalInstallments, OffsetDateTime purchaseDate,
                              String billForecastDate, String billId, String cardNumber) {}

}
