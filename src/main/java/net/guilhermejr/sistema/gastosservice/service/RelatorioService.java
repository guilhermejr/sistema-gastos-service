package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.response.FaturaResumidaResponse;
import net.guilhermejr.sistema.gastosservice.api.response.RelatorioMensalResponse;
import net.guilhermejr.sistema.gastosservice.api.response.TotaisResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Cartao;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Entradas e saídas de um mês, como o dinheiro passa pelas contas:
 * <ul>
 *   <li>lançamentos em conta, pela data de cada um;</li>
 *   <li>compras de cartão não aparecem uma a uma: cada cartão entra com o total da
 *   fatura que vence no mês, na data de vencimento. O que já foi pago dela conta como
 *   realizado e o resto como pendente. Estornos no cartão já vêm descontados do total.</li>
 * </ul>
 * Transferências, depósitos, saques e o pagamento da fatura (a movimentação) não
 * entram — não são receita nem despesa, e o pagamento contaria a fatura duas vezes.
 */
@RequiredArgsConstructor
@Service
public class RelatorioService {

    private final LancamentoRepository lancamentoRepository;
    private final RecorrenciaService recorrenciaService;
    private final FaturaService faturaService;
    private final LancamentoMapper lancamentoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    /** Entradas e saídas de um mês pela regra acima, com as listas que as compõem. */
    public record Calculo(TotaisResponse entradas, TotaisResponse saidas,
                          List<Lancamento> emConta, List<FaturaResumidaResponse> faturas) {
    }

    @Transactional
    public RelatorioMensalResponse mensal(Integer ano, Integer mes) {

        UUID usuario = authenticationCurrentUserService.getCurrentUser().getId();
        YearMonth periodo = converteStringUtil.toYearMonth(ano, mes);

        recorrenciaService.gerarAte(usuario, periodo.atEndOfMonth());
        Calculo calculo = calcular(usuario, periodo);
        TotaisResponse entradas = calculo.entradas();
        TotaisResponse saidas = calculo.saidas();

        return RelatorioMensalResponse.builder()
                .ano(periodo.getYear())
                .mes(periodo.getMonthValue())
                .entradas(entradas)
                .saidas(saidas)
                .saldoRealizado(entradas.getRealizado().subtract(saidas.getRealizado()))
                .saldoPrevisto(entradas.getTotal().subtract(saidas.getTotal()))
                .lancamentos(lancamentoMapper.mapList(calculo.emConta()))
                .faturas(calculo.faturas())
                .build();

    }

    /**
     * Calcula o mês. Usado também pelos cards de Receitas e Despesas do dashboard, para
     * que eles e o relatório nunca discordem. Quem chama gera antes as ocorrências fixas
     * até o fim do mês.
     */
    public Calculo calcular(UUID usuario, YearMonth periodo) {

        List<Lancamento> emConta = lancamentoRepository
                .findAllByUsuarioAndDataBetweenOrderByDataAscIdAsc(usuario, periodo.atDay(1), periodo.atEndOfMonth())
                .stream().filter(l -> l.getConta() != null).toList();

        Map<Cartao, List<Lancamento>> porCartao = lancamentoRepository
                .findAllByUsuarioAndCartaoIsNotNullAndFaturaBetween(usuario, periodo.atDay(1), periodo.atEndOfMonth())
                .stream().collect(Collectors.groupingBy(Lancamento::getCartao));
        List<FaturaResumidaResponse> faturas = porCartao.entrySet().stream()
                .map(e -> faturaService.resumo(e.getKey(), periodo, e.getValue()))
                .sorted(Comparator.comparing(FaturaResumidaResponse::getVencimento).thenComparing(FaturaResumidaResponse::getCartaoNome))
                .toList();

        TotaisResponse entradas = totais(emConta, TipoLancamento.R);
        TotaisResponse saidasEmConta = totais(emConta, TipoLancamento.D);
        BigDecimal faturasPendente = faturas.stream().map(FaturaResumidaResponse::getPendente).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal faturasTotal = faturas.stream().map(FaturaResumidaResponse::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        TotaisResponse saidas = TotaisResponse.de(
                saidasEmConta.getRealizado().add(faturasTotal.subtract(faturasPendente)),
                saidasEmConta.getPendente().add(faturasPendente));

        return new Calculo(entradas, saidas, emConta, faturas);

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
