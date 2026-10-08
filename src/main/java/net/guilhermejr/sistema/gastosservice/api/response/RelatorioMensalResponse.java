package net.guilhermejr.sistema.gastosservice.api.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RelatorioMensalResponse {

    private Integer ano;
    private Integer mes;
    /** Primeiro e último dia do ciclo chamado por ano/mes (o mês inteiro, se o ciclo começa no dia 1). */
    private LocalDate inicio;
    private LocalDate fim;
    private TotaisResponse entradas;
    private TotaisResponse saidas;
    /** Entradas menos saídas, só do que foi realizado. */
    private BigDecimal saldoRealizado;
    /** Entradas menos saídas, contando o que ainda está pendente. */
    private BigDecimal saldoPrevisto;
    /** Só os lançamentos em conta; compras de cartão entram pelas faturas. */
    private List<LancamentoResponse> lancamentos;
    /** Faturas com vencimento no ciclo, uma por cartão, com o total de cada uma. */
    private List<FaturaResumidaResponse> faturas;

}
