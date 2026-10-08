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
import net.guilhermejr.sistema.gastosservice.domain.repository.RecorrenciaRepository;
import net.guilhermejr.sistema.gastosservice.exception.ExceptionDefault;
import net.guilhermejr.sistema.gastosservice.util.CalendarioUtil;
import net.guilhermejr.sistema.gastosservice.util.Ciclo;
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
    private static final int MAXIMO_AGENDA = 100;

    private final LancamentoRepository lancamentoRepository;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final RecorrenciaService recorrenciaService;
    private final RecorrenciaRepository recorrenciaRepository;
    private final SaldoService saldoService;
    private final FaturaService faturaService;
    private final RelatorioService relatorioService;
    private final ConfiguracaoService configuracaoService;
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
        Ciclo ciclo = Ciclo.daData(hoje, configuracaoService.diaInicioCiclo(usuario));

        // Cobre também RelatorioService.ateOndeGerar: o ciclo de hoje começa neste mês ou no anterior.
        LocalDate geradoAte = YearMonth.from(hoje).plusMonths(1).atEndOfMonth();
        recorrenciaService.gerarAte(usuario, geradoAte);

        // Mesma regra do relatório: cartão entra pelo total da fatura que vence no ciclo.
        RelatorioService.Calculo doMes = relatorioService.calcular(usuario, ciclo);

        List<Conta> contas = contaRepository.findAllByUsuarioAndAtivoTrueOrderByNomeAsc(usuario);
        Map<Long, BigDecimal> saldos = saldoService.saldos(usuario, contas);
        BigDecimal saldoTotal = contas.stream().map(c -> saldos.get(c.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Conta> contasSaldoGeral = contas.stream().filter(Conta::getSomaSaldoGeral).toList();
        BigDecimal saldoGeral = contasSaldoGeral.stream().map(c -> saldos.get(c.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FaturaResumidaResponse> faturas = cartaoRepository.findAllByUsuarioAndAtivoTrueOrderByNomeAsc(usuario)
                .stream().map(faturaService::resumoAtual).toList();

        return DashboardResponse.builder()
                .ano(ciclo.nome().getYear())
                .mes(ciclo.nome().getMonthValue())
                .inicio(ciclo.inicio())
                .fim(ciclo.fim())
                .receitas(doMes.entradas())
                .despesas(doMes.saidas())
                .saldoGeral(saldoGeral)
                .saldoTotal(saldoTotal)
                .faturas(faturas.stream().map(FaturaResumidaResponse::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .contas(contasSaldoGeral.stream().map(c -> contaMapper.mapObject(c, saldos.get(c.getId()))).toList())
                .cartoes(faturas)
                .proximosPagar(agenda(usuario, TipoLancamento.D, ITENS_AGENDA, hoje, geradoAte))
                .proximosReceber(agenda(usuario, TipoLancamento.R, ITENS_AGENDA, hoje, geradoAte))
                .build();

    }

    /**
     * Os primeiros itens de "a pagar" (D) ou "a receber" (R) — o card mostra 5 e pede
     * mais 5 de cada vez, sempre a lista desde o início.
     */
    @Transactional
    public AgendaResponse agenda(TipoLancamento tipo, int quantidade) {

        if (quantidade < 1 || quantidade > MAXIMO_AGENDA) {
            throw new ExceptionDefault("A quantidade deve estar entre 1 e " + MAXIMO_AGENDA + ".");
        }

        UUID usuario = authenticationCurrentUserService.getCurrentUser().getId();
        LocalDate hoje = LocalDate.now(clock);
        LocalDate geradoAte = YearMonth.from(hoje).plusMonths(1).atEndOfMonth();
        recorrenciaService.gerarAte(usuario, geradoAte);

        return agenda(usuario, tipo, quantidade, hoje, geradoAte);

    }

    /**
     * As ocorrências fixas só existem até onde foram geradas, então a lista só está
     * completa até essa data. Busca um item a mais, para saber se há outros; se ele cai
     * depois do que já foi gerado, gera até a data dele e busca de novo — o que surgir
     * cai antes dele, e a segunda busca já fica dentro do gerado. Se faltam itens e há
     * série ativa, gera meses à frente (uma série mensal dá um item por mês).
     */
    private AgendaResponse agenda(UUID usuario, TipoLancamento tipo, int quantidade, LocalDate hoje, LocalDate geradoAte) {

        List<ItemAgendaResponse> itens = itensAgenda(usuario, tipo, quantidade + 1, hoje);

        if (itens.size() <= quantidade && recorrenciaRepository.existsByUsuarioAndAtivoTrue(usuario)) {
            geradoAte = geradoAte.plusMonths(quantidade + 1L);
            recorrenciaService.gerarAte(usuario, geradoAte);
            itens = itensAgenda(usuario, tipo, quantidade + 1, hoje);
        }

        if (itens.size() > quantidade && itens.get(quantidade).getData().isAfter(geradoAte)) {
            recorrenciaService.gerarAte(usuario, itens.get(quantidade).getData());
            itens = itensAgenda(usuario, tipo, quantidade + 1, hoje);
        }

        boolean temMais = itens.size() > quantidade;
        return AgendaResponse.builder()
                .itens(temMais ? itens.subList(0, quantidade) : itens)
                .temMais(temMais)
                .build();

    }

    private List<ItemAgendaResponse> itensAgenda(UUID usuario, TipoLancamento tipo, int quantidade, LocalDate hoje) {

        return tipo == TipoLancamento.D
                ? proximosPagar(usuario, hoje, quantidade)
                : pendentesEmConta(usuario, TipoLancamento.R, hoje, quantidade).toList();

    }

    /**
     * Despesas pendentes em conta e faturas com saldo a pagar, juntas e por data — os
     * atrasados primeiro, porque continuam devidos. Uma compra de cartão entra pela
     * fatura dela, que é como o dinheiro sai da conta.
     */
    private List<ItemAgendaResponse> proximosPagar(UUID usuario, LocalDate hoje, int quantidade) {

        Stream<ItemAgendaResponse> despesas = pendentesEmConta(usuario, TipoLancamento.D, hoje, quantidade);

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
                .limit(quantidade)
                .toList();

    }

    private Stream<ItemAgendaResponse> pendentesEmConta(UUID usuario, TipoLancamento tipo, LocalDate hoje, int quantidade) {

        return lancamentoRepository.findPendentesEmConta(usuario, tipo, PageRequest.of(0, quantidade)).stream()
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
