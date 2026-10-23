package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.mapper.ContaMapper;
import net.guilhermejr.sistema.gastosservice.api.response.SincronizacaoContaResponse;
import net.guilhermejr.sistema.gastosservice.client.PluggyClient;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.entity.TransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.SituacaoTransacaoBanco;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.ContaRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.TransacaoBancoRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class ContaBancoServiceTest {

    private static final Clock CLOCK = Clock.fixed(LocalDate.of(2026, 10, 23).atStartOfDay(ZoneId.of("America/Bahia")).toInstant(), ZoneId.of("America/Bahia"));

    private static PluggyClient.Transacao transacao(String id, String valor, String data, String descricao) {
        return transacao(id, valor, data, descricao, "POSTED");
    }

    private static PluggyClient.Transacao transacao(String id, String valor, String data, String descricao, String status) {
        return new PluggyClient.Transacao(id, descricao, new BigDecimal(valor), OffsetDateTime.parse(data), status, "Transfers", null);
    }

    private static TransacaoBanco ligada(String bancoId, Lancamento lancamento, boolean pendente) {
        TransacaoBanco t = new TransacaoBanco();
        t.setBancoId(bancoId);
        t.setSituacao(SituacaoTransacaoBanco.CONCILIADA);
        t.setLancamento(lancamento);
        t.setPendente(pendente);
        return t;
    }

    private static Lancamento naoRealizado(long id) {
        Lancamento l = new Lancamento();
        l.setId(id);
        l.setData(LocalDate.of(2026, 10, 20));
        l.setRealizado(false);
        return l;
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
                Mockito.mock(LancamentoRepository.class), null, null, Mockito.mock(ContaMapper.class), null, CLOCK);

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

    @Test
    @DisplayName("Pendente ligada: realiza o lançamento quando o banco efetiva; enquanto pendente, não; se some, desliga")
    @SuppressWarnings("unchecked")
    public void sincronizar_pendentes_ligadas() {

        PluggyClient pluggyClient = Mockito.mock(PluggyClient.class);
        TransacaoBancoRepository transacaoBancoRepository = Mockito.mock(TransacaoBancoRepository.class);
        LancamentoRepository lancamentoRepository = Mockito.mock(LancamentoRepository.class);
        ContaBancoService service = new ContaBancoService(pluggyClient, Mockito.mock(ContaRepository.class), transacaoBancoRepository,
                lancamentoRepository, null, null, Mockito.mock(ContaMapper.class), null, CLOCK);

        Conta conta = new Conta();
        conta.setBancoContaId("cc1");
        conta.setBancoInicio(LocalDate.of(2026, 10, 11));
        Lancamento efetivado = naoRealizado(1L);
        Lancamento agendado = naoRealizado(2L);
        Lancamento sumido = naoRealizado(3L);
        TransacaoBanco sumiu = ligada("sumiu", sumido, true);
        TransacaoBanco jaRevisada = ligada("revisada", naoRealizado(4L), false);
        jaRevisada.getLancamento().setRealizado(true);
        Mockito.when(transacaoBancoRepository.findAllByConta(conta)).thenReturn(new ArrayList<>(List.of(
                ligada("efetivou", efetivado, true), ligada("agendado", agendado, true), sumiu, jaRevisada)));
        Mockito.when(pluggyClient.transacoes(Mockito.any(), Mockito.eq("cc1"))).thenReturn(List.of(
                transacao("efetivou", "-178.64", "2026-10-22T12:00:00Z", "Pagamento de seguro"),
                transacao("agendado", "-178.64", "2026-10-27T12:00:00Z", "Pagamento de seguro", "PENDING"),
                transacao("revisada", "-50", "2026-10-15T12:00:00Z", "Pix enviado")));
        Mockito.when(pluggyClient.contas(Mockito.any())).thenReturn(List.of());

        service.sincronizar(conta);

        Assertions.assertTrue(efetivado.getRealizado());
        Assertions.assertEquals(LocalDate.of(2026, 10, 22), efetivado.getData());
        Assertions.assertFalse(agendado.getRealizado());
        Assertions.assertFalse(sumido.getRealizado());
        Mockito.verify(lancamentoRepository).save(efetivado);
        Mockito.verifyNoMoreInteractions(lancamentoRepository);
        Mockito.verify(transacaoBancoRepository).deleteAll(List.of(sumiu));

    }

}
