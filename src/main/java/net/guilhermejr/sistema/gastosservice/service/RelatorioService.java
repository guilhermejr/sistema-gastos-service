package net.guilhermejr.sistema.gastosservice.service;

import lombok.RequiredArgsConstructor;
import net.guilhermejr.seguranca.jwt.AuthenticationCurrentUserService;
import net.guilhermejr.sistema.gastosservice.api.mapper.LancamentoMapper;
import net.guilhermejr.sistema.gastosservice.api.response.RelatorioMensalResponse;
import net.guilhermejr.sistema.gastosservice.api.response.TotaisResponse;
import net.guilhermejr.sistema.gastosservice.domain.entity.Lancamento;
import net.guilhermejr.sistema.gastosservice.domain.enums.TipoLancamento;
import net.guilhermejr.sistema.gastosservice.domain.repository.LancamentoRepository;
import net.guilhermejr.sistema.gastosservice.util.ConverteStringUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Entradas e saídas de um mês, pela data de cada lançamento. Compras de cartão entram
 * pela data da compra e contam como realizadas quando a fatura foi paga. Transferências,
 * depósitos, saques e o próprio pagamento da fatura não entram — não são receita nem
 * despesa, e o pagamento contaria a compra duas vezes.
 */
@RequiredArgsConstructor
@Service
public class RelatorioService {

    private final LancamentoRepository lancamentoRepository;
    private final RecorrenciaService recorrenciaService;
    private final LancamentoMapper lancamentoMapper;
    private final ConverteStringUtil converteStringUtil;
    private final AuthenticationCurrentUserService authenticationCurrentUserService;

    @Transactional
    public RelatorioMensalResponse mensal(Integer ano, Integer mes) {

        UUID usuario = authenticationCurrentUserService.getCurrentUser().getId();
        YearMonth periodo = converteStringUtil.toYearMonth(ano, mes);

        recorrenciaService.gerarAte(usuario, periodo.atEndOfMonth());
        List<Lancamento> lancamentos = lancamentoRepository
                .findAllByUsuarioAndDataBetweenOrderByDataAscIdAsc(usuario, periodo.atDay(1), periodo.atEndOfMonth());

        TotaisResponse entradas = totais(lancamentos, TipoLancamento.R);
        TotaisResponse saidas = totais(lancamentos, TipoLancamento.D);

        return RelatorioMensalResponse.builder()
                .ano(periodo.getYear())
                .mes(periodo.getMonthValue())
                .entradas(entradas)
                .saidas(saidas)
                .saldoRealizado(entradas.getRealizado().subtract(saidas.getRealizado()))
                .saldoPrevisto(entradas.getTotal().subtract(saidas.getTotal()))
                .lancamentos(lancamentoMapper.mapList(lancamentos))
                .build();

    }

    public TotaisResponse totais(List<Lancamento> lancamentos, TipoLancamento tipo) {

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
