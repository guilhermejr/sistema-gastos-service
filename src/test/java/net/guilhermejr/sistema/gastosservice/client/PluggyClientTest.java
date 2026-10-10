package net.guilhermejr.sistema.gastosservice.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

public class PluggyClientTest {

    @Test
    @DisplayName("Tira o cursor do next, mantendo + / = do base64")
    public void cursor_do_next() {

        Assertions.assertEquals("MjAy+MC0x/MC==",
                PluggyClient.cursor("?accountId=562b795d-1653-429f-be86-74ead9502813&after=MjAy+MC0x/MC=="));

    }

    @Test
    @DisplayName("Decodifica o cursor que vem codificado no next")
    public void cursor_codificado() {

        Assertions.assertEquals("MjAy+MC0x/MC==", PluggyClient.cursor("?accountId=abc&after=MjAy%2BMC0x%2FMC%3D%3D"));

    }

    @Test
    @DisplayName("Sem next, acabou")
    public void sem_next() {

        Assertions.assertNull(PluggyClient.cursor(null));
        Assertions.assertNull(PluggyClient.cursor(""));
        Assertions.assertNull(PluggyClient.cursor("?accountId=abc"));

    }

    @Test
    @DisplayName("Lê o limite do cartão em creditData")
    public void limite_da_conta() {

        String json = """
                {"results":[{"id":"c1","type":"CREDIT","name":"PERSONNALITE MC BLACK","balance":14620.58,
                  "creditData":{"level":"BLACK","creditLimit":15500,"availableCreditLimit":879.42,"minimumPayment":702.01}}]}
                """;

        PluggyClient.Conta conta = JsonMapper.builder().build().readValue(json, PluggyClient.PaginaContas.class).results().getFirst();

        Assertions.assertEquals(new BigDecimal("15500"), conta.creditData().creditLimit());
        Assertions.assertEquals(new BigDecimal("879.42"), conta.creditData().availableCreditLimit());

    }

}
