package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Map;

public class BancoServiceTest {

    private static final LocalDate FATURA = LocalDate.of(2026, 11, 6);

    private static PluggyClient.Transacao transacao(String valor, String data, String status, PluggyClient.DadosCartao dados) {
        return new PluggyClient.Transacao("t1", " AMAZON BRSAO PAULO05/11 ", new BigDecimal(valor), OffsetDateTime.parse(data),
                status, "Shopping", dados);
    }

    private static PluggyClient.DadosCartao parcela(int numero, int total) {
        return new PluggyClient.DadosCartao(numero, total, OffsetDateTime.parse("2026-06-21T18:00:01Z"), "2026-11", null, "5943");
    }

    @Test
    @DisplayName("O mês da fatura vem do previsto pelo banco")
    public void mes_da_fatura_pelo_previsto() {

        PluggyClient.Transacao t = transacao("10", "2026-10-01T12:00:00Z", "PENDING",
                new PluggyClient.DadosCartao(null, null, null, "2026-11", "f1", null));

        Assertions.assertEquals(YearMonth.of(2026, 11), BancoService.mesDaFatura(t, Map.of("f1", YearMonth.of(2026, 10))));

    }

    @Test
    @DisplayName("Sem o previsto, o mês da fatura vem do vencimento da fatura a que pertence")
    public void mes_da_fatura_pela_fatura() {

        PluggyClient.Transacao t = transacao("-9233.15", "2025-11-05T12:00:00Z", "POSTED",
                new PluggyClient.DadosCartao(null, null, null, null, "f1", null));

        Assertions.assertEquals(YearMonth.of(2025, 11), BancoService.mesDaFatura(t, Map.of("f1", YearMonth.of(2025, 11))));
        Assertions.assertNull(BancoService.mesDaFatura(t, Map.of()));

    }

    @Test
    @DisplayName("A data é a do fuso local: compra às 22h37 de 09/10 chega como 01h37 UTC de 10/10")
    public void data_no_fuso_local() {

        TransacaoBanco guardada = new TransacaoBanco();
        BancoService.preencher(guardada, transacao("68.40", "2026-10-10T01:37:18.000Z", "PENDING", null), FATURA);

        Assertions.assertEquals(LocalDate.of(2026, 10, 9), guardada.getData());

    }

    @Test
    @DisplayName("Compra é despesa e valor negativo é estorno, sempre com valor positivo")
    public void tipo_pelo_sinal() {

        TransacaoBanco compra = new TransacaoBanco();
        BancoService.preencher(compra, transacao("82.77", "2026-10-08T16:28:13Z", "PENDING", null), FATURA);
        TransacaoBanco estorno = new TransacaoBanco();
        BancoService.preencher(estorno, transacao("-30.67", "2026-10-08T16:28:13Z", "POSTED", null), FATURA);

        Assertions.assertEquals(TipoLancamento.D, compra.getTipo());
        Assertions.assertEquals(new BigDecimal("82.77"), compra.getValor());
        Assertions.assertTrue(compra.getPendente());
        Assertions.assertEquals(TipoLancamento.R, estorno.getTipo());
        Assertions.assertEquals(new BigDecimal("30.67"), estorno.getValor());
        Assertions.assertFalse(estorno.getPendente());

    }

    @Test
    @DisplayName("Parcela guarda número, total, data da compra e descrição sem espaços nas pontas")
    public void parcela_com_dados_da_compra() {

        TransacaoBanco guardada = new TransacaoBanco();
        BancoService.preencher(guardada, transacao("999.90", "2026-10-27T03:00:00Z", "PENDING", parcela(5, 11)), FATURA);

        Assertions.assertEquals(5, guardada.getParcela());
        Assertions.assertEquals(11, guardada.getTotalParcelas());
        Assertions.assertEquals(LocalDate.of(2026, 6, 21), guardada.getDataCompra());
        Assertions.assertEquals("AMAZON BRSAO PAULO05/11", guardada.getDescricao());
        Assertions.assertEquals("5943", guardada.getFinalCartao());
        Assertions.assertEquals(FATURA, guardada.getFatura());

    }

    @Test
    @DisplayName("Compra à vista não tem parcela, mesmo que o banco mande 1 de 1")
    public void a_vista_sem_parcela() {

        TransacaoBanco guardada = new TransacaoBanco();
        BancoService.preencher(guardada, transacao("50", "2026-10-01T12:00:00Z", "POSTED", parcela(1, 1)), FATURA);

        Assertions.assertNull(guardada.getParcela());
        Assertions.assertNull(guardada.getTotalParcelas());

    }

    @Test
    @DisplayName("Diz se algo mudou: a mesma transação de novo não muda; a pendente que fechou, sim")
    public void detecta_mudanca() {

        TransacaoBanco guardada = new TransacaoBanco();
        Assertions.assertTrue(BancoService.preencher(guardada, transacao("82.77", "2026-10-08T16:28:13Z", "PENDING", null), FATURA));
        Assertions.assertFalse(BancoService.preencher(guardada, transacao("82.77", "2026-10-08T16:28:13Z", "PENDING", null), FATURA));
        Assertions.assertTrue(BancoService.preencher(guardada, transacao("82.77", "2026-10-08T16:28:13Z", "POSTED", null), FATURA));

    }

}
