package net.guilhermejr.sistema.gastosservice.service;

import net.guilhermejr.sistema.gastosservice.api.response.FaturaResumidaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ValorCategoriaResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.util.Ciclo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class RelatorioServiceTest {

    @Mock
    private LancamentoRepository lancamentoRepository;

    @Mock
    private FaturaService faturaService;

    @InjectMocks
    private RelatorioService relatorioService;

    private static Conta conta(long id, boolean somaSaldoGeral) {

        Conta conta = new Conta();
        conta.setId(id);
        conta.setSomaSaldoGeral(somaSaldoGeral);
        return conta;

    }

    private static Cartao cartao(long id, Conta conta) {

        Cartao cartao = new Cartao();
        cartao.setId(id);
        cartao.setDiaVencimento(10);
        cartao.setConta(conta);
        return cartao;

    }

    private static Lancamento emConta(TipoLancamento tipo, Conta conta, String valor) {

        Lancamento lancamento = lancamento(tipo, null, valor);
        lancamento.setConta(conta);
        lancamento.setRealizado(true);
        return lancamento;

    }

    private static Lancamento noCartao(Cartao cartao, String valor) {

        Lancamento lancamento = lancamento(TipoLancamento.D, null, valor);
        lancamento.setCartao(cartao);
        lancamento.setFatura(LocalDate.of(2026, 10, 10));
        return lancamento;

    }

    private static FaturaResumidaResponse fatura(Cartao cartao, String total) {

        FaturaResumidaResponse fatura = new FaturaResumidaResponse();
        fatura.setCartaoId(cartao.getId());
        fatura.setCartaoNome("Cartão " + cartao.getId());
        fatura.setVencimento(LocalDate.of(2026, 10, 10));
        fatura.setTotal(new BigDecimal(total));
        fatura.setPendente(new BigDecimal(total));
        return fatura;

    }

    @Test
    @DisplayName("Com filtro de conta, só entram os lançamentos e as faturas dos cartões das contas aceitas")
    public void calcular_so_contas_aceitas() {

        Conta corrente = conta(1, true);
        Conta investimento = conta(2, false);
        Cartao pagoPelaCorrente = cartao(1, corrente);
        Cartao pagoPeloInvestimento = cartao(2, investimento);
        Cartao semConta = cartao(3, null);
        Ciclo ciclo = Ciclo.chamado(YearMonth.of(2026, 10), 1);

        Mockito.when(lancamentoRepository.findAllByUsuarioAndDataBetweenOrderByDataAscIdAsc(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(
                        emConta(TipoLancamento.R, corrente, "1000.00"),
                        emConta(TipoLancamento.R, investimento, "300.00"),
                        emConta(TipoLancamento.D, corrente, "100.00"),
                        emConta(TipoLancamento.D, investimento, "50.00")));
        Mockito.when(lancamentoRepository.findAllByUsuarioAndCartaoIsNotNullAndFaturaBetween(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(noCartao(pagoPelaCorrente, "200.00"), noCartao(pagoPeloInvestimento, "70.00"), noCartao(semConta, "20.00")));
        Mockito.when(faturaService.resumo(ArgumentMatchers.eq(pagoPelaCorrente), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(fatura(pagoPelaCorrente, "200.00"));
        Mockito.when(faturaService.resumo(ArgumentMatchers.eq(semConta), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(fatura(semConta, "20.00"));

        RelatorioService.Calculo calculo = relatorioService.calcular(UUID.randomUUID(), ciclo, Conta::getSomaSaldoGeral);

        Assertions.assertEquals(new BigDecimal("1000.00"), calculo.entradas().getTotal());
        Assertions.assertEquals(new BigDecimal("100.00"), calculo.saidas().getRealizado());
        Assertions.assertEquals(new BigDecimal("220.00"), calculo.saidas().getPendente());
        Assertions.assertEquals(2, calculo.faturas().size());
        Mockito.verify(faturaService, Mockito.never()).resumo(ArgumentMatchers.eq(pagoPeloInvestimento), ArgumentMatchers.any(), ArgumentMatchers.any());

    }

    private static Categoria categoria(long id, String descricao) {

        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setDescricao(descricao);
        return categoria;

    }

    private static Lancamento lancamento(TipoLancamento tipo, Categoria categoria, String valor) {

        Lancamento lancamento = new Lancamento();
        lancamento.setTipo(tipo);
        lancamento.setCategoria(categoria);
        lancamento.setValor(new BigDecimal(valor));
        return lancamento;

    }

    @Test
    @DisplayName("Soma por categoria só o tipo pedido, da maior para a menor")
    public void soma_por_categoria() {

        Categoria mercado = categoria(1, "Mercado");
        Categoria lazer = categoria(2, "Lazer");
        Categoria salario = categoria(3, "Salário");
        List<Lancamento> lancamentos = List.of(
                lancamento(TipoLancamento.D, lazer, "50.00"),
                lancamento(TipoLancamento.D, mercado, "30.00"),
                lancamento(TipoLancamento.R, salario, "1000.00"),
                lancamento(TipoLancamento.D, categoria(1, "Mercado"), "40.00"));

        List<ValorCategoriaResponse> despesas = RelatorioService.porCategoria(lancamentos, TipoLancamento.D);

        Assertions.assertEquals(List.of("Mercado", "Lazer"), despesas.stream().map(ValorCategoriaResponse::getDescricao).toList());
        Assertions.assertEquals(List.of(new BigDecimal("70.00"), new BigDecimal("50.00")), despesas.stream().map(ValorCategoriaResponse::getValor).toList());
        Assertions.assertEquals(List.of(3L), RelatorioService.porCategoria(lancamentos, TipoLancamento.R).stream().map(ValorCategoriaResponse::getCategoriaId).toList());

    }

}
