package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.ContaMapper;
import net.guilhermejr.sistema.gastosservice.api.response.*;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Conta;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.CartaoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.ContaRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
public class DashboardService {

    private static final int ITENS_AGENDA = 5;

    private final LancamentoRepository lancamentoRepository;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final RecorrenciaService recorrenciaService;
    private final SaldoService saldoService;
    private final FaturaService faturaService;
    private final RelatorioService relatorioService;
    private final ContaMapper contaMapper;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;
    private final Clock clock;

    /**
     * Tudo o que a tela inicial mostra, numa chamada. Gera antes as despesas e receitas
     * fixas até o fim do mês que vem, para que "a pagar" e "a receber" já enxerguem as
     * próximas ocorrências.
     */
    @Transactional
    public DashboardResponse carregar() {

        UUID usuario = authenticationCurrentUserService.getCurrentUser().getId();
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mes = YearMonth.from(hoje);

        recorrenciaService.gerarAte(usuario, mes.plusMonths(1).atEndOfMonth());

        // Mesma regra do relatório: cartão entra pelo total da fatura que vence no mês.
        RelatorioService.Calculo doMes = relatorioService.calcular(usuario, mes);

        List<Conta> contas = contaRepository.findAllByUsuarioAndAtivoTrueOrderByNomeAsc(usuario);
        Map<Long, BigDecimal> saldos = saldoService.saldos(usuario, contas);
        BigDecimal saldoTotal = contas.stream().map(c -> saldos.get(c.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Conta> contasSaldoGeral = contas.stream().filter(Conta::getSomaSaldoGeral).toList();
        BigDecimal saldoGeral = contasSaldoGeral.stream().map(c -> saldos.get(c.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FaturaResumidaResponse> faturas = cartaoRepository.findAllByUsuarioAndAtivoTrueOrderByNomeAsc(usuario)
                .stream().map(faturaService::resumoAtual).toList();

        return DashboardResponse.builder()
                .ano(mes.getYear())
                .mes(mes.getMonthValue())
                .receitas(doMes.entradas())
                .despesas(doMes.saidas())
                .saldoGeral(saldoGeral)
                .saldoTotal(saldoTotal)
                .faturas(faturas.stream().map(FaturaResumidaResponse::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .contas(contasSaldoGeral.stream().map(c -> contaMapper.mapObject(c, saldos.get(c.getId()))).toList())
                .cartoes(faturas)
                .proximosPagar(proximosPagar(usuario, hoje))
                .proximosReceber(pendentesEmConta(usuario, TipoLancamento.R, hoje).limit(ITENS_AGENDA).toList())
                .build();

    }

    /**
     * Despesas pendentes em conta e faturas com saldo a pagar, juntas e por data — os
     * atrasados primeiro, porque continuam devidos. Uma compra de cartão entra pela
     * fatura dela, que é como o dinheiro sai da conta.
     */
    private List<ItemAgendaResponse> proximosPagar(UUID usuario, LocalDate hoje) {

        Stream<ItemAgendaResponse> despesas = pendentesEmConta(usuario, TipoLancamento.D, hoje);

        Map<Cartao, Map<YearMonth, List<Lancamento>>> porFatura = lancamentoRepository.findPendentesEmCartao(usuario).stream()
                .collect(Collectors.groupingBy(Lancamento::getCartao,
                        Collectors.groupingBy(l -> YearMonth.from(l.getFatura()))));

        Stream<ItemAgendaResponse> faturas = porFatura.entrySet().stream().flatMap(porCartao ->
                porCartao.getValue().entrySet().stream().map(fatura -> {
                    Cartao cartao = porCartao.getKey();
                    LocalDate vencimento = CalendarioUtil.vencimentoNoMes(fatura.getKey(), cartao.getDiaVencimento());
                    return ItemAgendaResponse.builder()
                            .origem(ItemAgendaResponse.FATURA)
                            .cartaoId(cartao.getId())
                            .descricao("Fatura " + cartao.getNome())
                            .data(vencimento)
                            .valor(FaturaService.liquido(fatura.getValue()))
                            .atrasado(vencimento.isBefore(hoje))
                            .build();
                })).filter(item -> item.getValor().signum() > 0);

        return Stream.concat(despesas, faturas)
                .sorted(Comparator.comparing(ItemAgendaResponse::getData))
                .limit(ITENS_AGENDA)
                .toList();

    }

    private Stream<ItemAgendaResponse> pendentesEmConta(UUID usuario, TipoLancamento tipo, LocalDate hoje) {

        return lancamentoRepository.findPendentesEmConta(usuario, tipo, PageRequest.of(0, ITENS_AGENDA)).stream()
                .map(l -> ItemAgendaResponse.builder()
                        .origem(ItemAgendaResponse.LANCAMENTO)
                        .lancamentoId(l.getId())
                        .descricao(l.getParcela() == null ? l.getDescricao()
                                : l.getDescricao() + " (" + l.getParcela() + "/" + l.getTotalParcelas() + ")")
                        .data(l.getData())
                        .valor(l.getValor())
                        .atrasado(l.getData().isBefore(hoje))
                        .build());

    }

}
