package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public class ConexaoBancoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-11T13:00:00Z");

    private static PluggyClient.Item item(String status, String ultima, String desligada, String consentimento) {
        return new PluggyClient.Item(status, "SUCCESS", OffsetDateTime.parse(ultima), OffsetDateTime.parse(ultima).plusDays(1),
                desligada == null ? null : OffsetDateTime.parse(desligada), OffsetDateTime.parse(consentimento));
    }

    @Test
    @DisplayName("Atualizada ontem, sem erro e com o consentimento longe: nada a avisar")
    public void tudo_certo() {

        Assertions.assertEquals(List.of(), ConexaoBancoService.alertas(
                item("UPDATED", "2026-10-10T15:02:02Z", null, "2027-10-10T14:36:40Z"), AGORA));

    }

    @Test
    @DisplayName("Avisa erro de login, atualização desligada, dados de mais de 26h e consentimento a menos de 30 dias")
    public void avisos() {

        List<String> alertas = ConexaoBancoService.alertas(
                item("LOGIN_ERROR", "2026-10-09T15:02:02Z", "2026-10-10T00:00:00Z", "2026-10-30T14:36:40Z"), AGORA);

        Assertions.assertEquals(4, alertas.size());
        Assertions.assertTrue(alertas.get(0).contains("LOGIN_ERROR"));
        Assertions.assertTrue(alertas.get(2).contains("09/10/2026 às 12:02"), alertas.get(2));
        Assertions.assertTrue(alertas.get(3).contains("30/10/2026"));

    }

}
