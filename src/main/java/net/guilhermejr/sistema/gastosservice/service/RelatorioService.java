package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.mapper.MovimentacaoMapper;
import net.guilhermejr.sistema.gastosservice.api.response.CategoriasMensalResponse;
import net.guilhermejr.sistema.gastosservice.api.response.FaturaResumidaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.RelatorioMensalResponse;
import net.guilhermejr.sistema.gastosservice.api.response.TotaisResponse;
import net.guilhermejr.sistema.gastosservice.api.response.ValorCategoriaResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Categoria;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoMovimentacao;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.domain.repository.MovimentacaoRepository;
import net.guilhermejr.sistema.gastosservice.util.Ciclo;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entradas e saídas de um ciclo mensal ({@link Ciclo}: o mês do calendário, ou do dia
 * de início que o usuário configurou até a véspera dele no mês seguinte), como o
 * dinheiro passa pelas contas:
 * <ul>
 *   <li>lançamentos em conta, pela data de cada um;</li>
 *   <li>compras de cartão não aparecem uma a uma: cada cartão entra com o total da
 *   fatura que vence no ciclo, na data de vencimento. O que já foi pago dela conta como
 *   realizado e o resto como pendente. Estornos no cartão já vêm descontados do total.</li>
 * </ul>
 * Transferências, depósitos, saques e o pagamento da fatura (a movimentação) não
 * entram nos totais — não são receita nem despesa, e o pagamento contaria a fatura duas
 * vezes. As transferências do ciclo vêm à parte em {@code transferencias}, só para consulta.
 */
@RequiredArgsConstructor
@Service
public class RelatorioService {

    private final LancamentoRepository lancamentoRepository;
    private final RecorrenciaService recorrenciaService;
    private final FaturaService faturaService;
    private final ConfiguracaoService configuracaoService;
    private final LancamentoMapper lancamentoMapper;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MovimentacaoMapper movimentacaoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    /**
     * Entradas e saídas de um mês pela regra acima, com as listas que as compõem
     * ({@code noCartao}: as compras e estornos das faturas em {@code faturas}).
     */
    public record Calculo(TotaisResponse entradas, TotaisResponse saidas,
                          List<Lancamento> emConta, List<Lancamento> noCartao, List<FaturaResumidaResponse> faturas) {
    }

    @Transactional
    public RelatorioMensalResponse mensal(Integer ano, Integer mes) {

        UUID usuario = authenticationCurrentUserService.getCurrentUser().getId();
        Ciclo ciclo = configuracaoService.ciclo(usuario, converteStringUtil.toYearMonth(ano, mes));

        recorrenciaService.gerarAte(usuario, ateOndeGerar(ciclo));
        Calculo calculo = calcular(usuario, ciclo);
        TotaisResponse entradas = calculo.entradas();
        TotaisResponse saidas = calculo.saidas();

        return RelatorioMensalResponse.builder()
                .ano(ciclo.nome().getYear())
                .mes(ciclo.nome().getMonthValue())
                .inicio(ciclo.inicio())
                .fim(ciclo.fim())
                .entradas(entradas)
                .saidas(saidas)
                .saldoRealizado(entradas.getRealizado().subtract(saidas.getRealizado()))
                .saldoPrevisto(entradas.getTotal().subtract(saidas.getTotal()))
                .lancamentos(lancamentoMapper.mapList(calculo.emConta()))
                .faturas(calculo.faturas())
                .transferencias(movimentacaoMapper.mapList(movimentacaoRepository
                        .findAllByUsuarioAndTipoAndDataBetweenOrderByDataAscIdAsc(usuario, TipoMovimentacao.TRANSFERENCIA, ciclo.inicio(), ciclo.fim())))
                .build();

    }

    /**
     * Despesas e receitas do ciclo por categoria, para os gráficos. Segue a mesma regra
     * do relatório, mas abre as faturas: as despesas são as em conta mais as compras das
     * faturas que vencem no ciclo, cada uma na sua categoria, e as receitas são as em
     * conta. Conta realizado e pendente. Estornos no cartão vêm à parte em
     * {@code estornosCartao}.
     */
    @Transactional
    public CategoriasMensalResponse categorias(Integer ano, Integer mes) {

        UUID usuario = authenticationCurrentUserService.getCurrentUser().getId();
        Ciclo ciclo = configuracaoService.ciclo(usuario, converteStringUtil.toYearMonth(ano, mes));

        recorrenciaService.gerarAte(usuario, ateOndeGerar(ciclo));
        Calculo calculo = calcular(usuario, ciclo);
        List<Lancamento> despesas = Stream.concat(calculo.emConta().stream(), calculo.noCartao().stream()).toList();

        return CategoriasMensalResponse.builder()
                .ano(ciclo.nome().getYear())
                .mes(ciclo.nome().getMonthValue())
                .inicio(ciclo.inicio())
                .fim(ciclo.fim())
                .despesas(porCategoria(despesas, TipoLancamento.D))
                .receitas(porCategoria(calculo.emConta(), TipoLancamento.R))
                .estornosCartao(calculo.noCartao().stream()
                        .filter(l -> l.getTipo() == TipoLancamento.R)
                        .map(Lancamento::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();

    }

    /** Soma os lançamentos do tipo por categoria, da maior para a menor (empate pelo nome). */
    public static List<ValorCategoriaResponse> porCategoria(List<Lancamento> lancamentos, TipoLancamento tipo) {

        Map<Categoria, BigDecimal> somas = lancamentos.stream()
                .filter(l -> l.getTipo() == tipo)
                .collect(Collectors.groupingBy(Lancamento::getCategoria, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Lancamento::getValor, BigDecimal::add)));
        return somas.entrySet().stream()
                .map(e -> new ValorCategoriaResponse(e.getKey().getId(), e.getKey().getDescricao(), e.getValue()))
                .sorted(Comparator.comparing(ValorCategoriaResponse::getValor).reversed()
                        .thenComparing(ValorCategoriaResponse::getDescricao))
                .toList();

    }

    /**
     * Até onde gerar as ocorrências fixas antes de {@link #calcular}: o fim do ciclo e o
     * das faturas que vencem nele (a do mês seguinte ao início junta compras até o fim
     * daquele mês).
     */
    public static LocalDate ateOndeGerar(Ciclo ciclo) {
        return ciclo.mesInicio().plusMonths(1).atEndOfMonth();
    }

    /**
     * Calcula o ciclo. Usado também pelos cards de Receitas e Despesas do dashboard, para
     * que eles e o relatório nunca discordem. Quem chama gera antes as ocorrências fixas
     * até {@link #ateOndeGerar}.
     *
     * <p>A fatura de cada cartão é a do mês em que o vencimento atual dele cai dentro do
     * ciclo ({@link Ciclo#mesDaFatura}), buscada pelo mês inteiro como em todo o resto —
     * compras já pagas guardam o vencimento antigo se o dia do cartão mudou.
     */
    public Calculo calcular(UUID usuario, Ciclo ciclo) {

        List<Lancamento> emConta = lancamentoRepository
                .findAllByUsuarioAndDataBetweenOrderByDataAscIdAsc(usuario, ciclo.inicio(), ciclo.fim())
                .stream().filter(l -> l.getConta() != null).toList();

        YearMonth primeiro = ciclo.mesInicio();
        Map<Cartao, List<Lancamento>> porCartao = lancamentoRepository
                .findAllByUsuarioAndCartaoIsNotNullAndFaturaBetween(usuario, primeiro.atDay(1), primeiro.plusMonths(1).atEndOfMonth())
                .stream()
                .filter(l -> YearMonth.from(l.getFatura()).equals(ciclo.mesDaFatura(l.getCartao().getDiaVencimento())))
                .collect(Collectors.groupingBy(Lancamento::getCartao));
        List<FaturaResumidaResponse> faturas = porCartao.entrySet().stream()
                .map(e -> faturaService.resumo(e.getKey(), ciclo.mesDaFatura(e.getKey().getDiaVencimento()), e.getValue()))
                .sorted(Comparator.comparing(FaturaResumidaResponse::getVencimento).thenComparing(FaturaResumidaResponse::getCartaoNome))
                .toList();

        TotaisResponse entradas = totais(emConta, TipoLancamento.R);
        TotaisResponse saidasEmConta = totais(emConta, TipoLancamento.D);
        BigDecimal faturasPendente = faturas.stream().map(FaturaResumidaResponse::getPendente).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal faturasTotal = faturas.stream().map(FaturaResumidaResponse::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        TotaisResponse saidas = TotaisResponse.de(
                saidasEmConta.getRealizado().add(faturasTotal.subtract(faturasPendente)),
                saidasEmConta.getPendente().add(faturasPendente));

        List<Lancamento> noCartao = porCartao.values().stream().flatMap(List::stream).toList();
        return new Calculo(entradas, saidas, emConta, noCartao, faturas);

    }

    private TotaisResponse totais(List<Lancamento> lancamentos, TipoLancamento tipo) {

        BigDecimal realizado = BigDecimal.ZERO;
        BigDecimal pendente = BigDecimal.ZERO;
        for (Lancamento l : lancamentos) {
            if (l.getTipo() != tipo) {
                continue;
            }
            if (l.getRealizado()) {
                realizado = realizado.add(l.getValor());
            } else {
                pendente = pendente.add(l.getValor());
            }
        }
        return TotaisResponse.de(realizado, pendente);

    }

}
