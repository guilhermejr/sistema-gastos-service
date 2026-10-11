package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.mapper.ContaMapper;
import net.guilhermejr.sistema.gastosservice.api.response.SincronizacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.ContaRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ContaBancoServiceTest {

    private static PluggyClient.Transacao transacao(String id, String valor, String data, String descricao) {
        return new PluggyClient.Transacao(id, descricao, new BigDecimal(valor), OffsetDateTime.parse(data), "POSTED", "Transfers", null);
    }

    @Test
    @DisplayName("Na conta, valor negativo é saída (D) e positivo é entrada (R)")
    public void tipo_pelo_sinal() {

        TransacaoBanco saida = new TransacaoBanco();
        ContaBancoService.preencher(saida, transacao("t1", "-2395.26", "2026-09-30T12:00:00Z", "Pagamento de financiamento"));
        TransacaoBanco entrada = new TransacaoBanco();
        ContaBancoService.preencher(entrada, transacao("t2", "11301.93", "2026-10-27T12:00:00Z", "Salário ITAU UNIBANCO S.A."));

        Assertions.assertEquals(TipoLancamento.D, saida.getTipo());
        Assertions.assertEquals(new BigDecimal("2395.26"), saida.getValor());
        Assertions.assertEquals(TipoLancamento.R, entrada.getTipo());
        Assertions.assertNull(entrada.getFatura());

    }

    @Test
    @DisplayName("A busca guarda só do dia da ligação em diante, apaga a pendente que sumiu e guarda o saldo")
    @SuppressWarnings("unchecked")
    public void sincronizar() {

        PluggyClient pluggyClient = Mockito.mock(PluggyClient.class);
        ContaRepository contaRepository = Mockito.mock(ContaRepository.class);
        TransacaoBancoRepository transacaoBancoRepository = Mockito.mock(TransacaoBancoRepository.class);
        ContaBancoService service = new ContaBancoService(pluggyClient, contaRepository, transacaoBancoRepository,
                null, null, Mockito.mock(ContaMapper.class), null, null);

        Conta conta = new Conta();
        conta.setBancoContaId("cc1");
        conta.setBancoInicio(LocalDate.of(2026, 10, 11));
        TransacaoBanco sumiu = new TransacaoBanco();
        sumiu.setBancoId("velha");
        sumiu.setSituacao(SituacaoTransacaoBanco.A_REVISAR);
        Mockito.when(transacaoBancoRepository.findAllByConta(conta)).thenReturn(new ArrayList<>(List.of(sumiu)));
        Mockito.when(pluggyClient.transacoes(Mockito.any(), Mockito.eq("cc1"))).thenReturn(List.of(
                // 22h de 10/10 na Bahia: antes da ligação, fica de fora.
                transacao("antes", "-100", "2026-10-11T01:00:00Z", "Pix enviado"),
                transacao("depois", "-100", "2026-10-11T12:00:00Z", "Pix enviado"),
                transacao("zero", "0", "2026-10-12T12:00:00Z", "Tarifa estornada")));
        Mockito.when(pluggyClient.contas(Mockito.any())).thenReturn(List.of(
                new PluggyClient.Conta("cc1", "BANK", "itau", "00003152-3", new BigDecimal("1226.04"), null)));

        SincronizacaoContaResponse resposta = service.sincronizar(conta);

        ArgumentCaptor<List<TransacaoBanco>> salvas = ArgumentCaptor.forClass(List.class);
        Mockito.verify(transacaoBancoRepository).saveAll(salvas.capture());
        Assertions.assertEquals(List.of("depois"), salvas.getValue().stream().map(TransacaoBanco::getBancoId).toList());
        Assertions.assertSame(conta, salvas.getValue().get(0).getConta());
        Mockito.verify(transacaoBancoRepository).deleteAll(List.of(sumiu));
        Assertions.assertEquals(1, resposta.getNovas());
        Assertions.assertEquals(1, resposta.getRemovidas());
        Assertions.assertEquals(new BigDecimal("1226.04"), conta.getBancoSaldo());
        Assertions.assertNotNull(conta.getBancoSincronizado());
        Mockito.verify(contaRepository).save(conta);

    }

}
